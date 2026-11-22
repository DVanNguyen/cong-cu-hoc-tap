<?php

namespace App\Http\Controllers\Api\StudyAi;

use App\Http\Controllers\Controller;
use App\Models\RawItem;
use App\Models\Task;
use App\Models\Course;
use App\Services\AiServiceClient;
use Illuminate\Http\Request;
use Illuminate\Support\Str;

class DeadlineController extends Controller
{
    protected AiServiceClient $aiService;

    public function __construct(AiServiceClient $aiService)
    {
        $this->aiService = $aiService;
    }

    /**
     * Trích xuất deadline từ tin nhắn văn bản (qua app hoặc API)
     */
    public function extract(Request $request)
    {
        $request->validate([
            'text' => 'required|string',
            'source' => 'nullable|string|in:telegram,email,speech,manual',
            'course_hint' => 'nullable|string',
        ]);

        $userId = $request->user()->id;
        $source = $request->input('source', 'manual');
        $text = $request->input('text');
        $receivedAt = now()->toIso8601String();
        $courseHint = $request->input('course_hint', 'Trí tuệ nhân tạo');

        // 1. Tính hash Idempotent
        $rawHash = hash('sha256', "{$source}_{$text}");

        // Kiểm tra xem đã xử lý tin này chưa (Idempotent check)
        $existingRaw = RawItem::where('hash', $rawHash)->first();
        if ($existingRaw) {
            $existingTask = Task::where('raw_item_id', $existingRaw->id)->first();
            return response()->json([
                'message' => 'Tin nhắn đã được xử lý trước đó (Idempotent)',
                'is_deadline' => $existingTask !== null,
                'raw_item' => $existingRaw,
                'task' => $existingTask,
            ]);
        }

        // 2. Gọi Python AI Service
        $aiResult = $this->aiService->extractDeadline($text, $source, $receivedAt, $courseHint);

        if (!$aiResult) {
            return response()->json(['error' => 'Dịch vụ AI không phản hồi'], 503);
        }

        // Lưu vào raw_items
        $rawItem = RawItem::create([
            'user_id' => $userId,
            'source' => $source,
            'external_id' => $request->input('external_id'),
            'received_at' => now(),
            'text_content' => $text,
            'hash' => $rawHash,
            'status' => $aiResult['is_deadline_candidate'] ? 'processed' : 'ignored',
        ]);

        // Nếu không có dấu hiệu deadline
        if (!$aiResult['is_deadline_candidate'] || empty($aiResult['task'])) {
            return response()->json([
                'message' => 'Tin nhắn không chứa deadline hoặc bài tập',
                'is_deadline' => false,
                'raw_item_id' => $rawItem->id,
            ]);
        }

        $taskData = $aiResult['task'];

        // Tìm hoặc tạo môn học
        $course = Course::firstOrCreate(
            ['user_id' => $userId, 'name' => $taskData['course']],
            ['code' => 'AI']
        );

        // 3. Tạo hoặc cập nhật Task
        $workspace = $request->user()->workspaces()->first();
        $workspaceId = $workspace ? $workspace->id : Str::uuid()->toString();

        $task = Task::create([
            'id' => (string) Str::uuid(),
            'workspace_id' => $workspaceId,
            'user_id' => $userId,
            'title' => $taskData['title'],
            'description' => $taskData['evidence_quote'] ?? '',
            'tag' => $taskData['type'],
            'due_date' => $taskData['due_iso_candidate'] ? (int) (strtotime($taskData['due_iso_candidate']) * 1000) : null,
            'raw_item_id' => $rawItem->id,
            'course_id' => $course->id,
            'confidence' => $taskData['confidence'],
            'intent' => $taskData['intent'],
            'effort_est_min' => $taskData['effort_est_min'],
            'evidence_quote' => $taskData['evidence_quote'],
            'needs_confirmation' => $taskData['needs_confirmation'] ?? false,
        ]);

        return response()->json([
            'message' => 'Trích xuất và lưu công việc thành công',
            'is_deadline' => true,
            'task' => $task,
            'needs_confirmation' => $task->needs_confirmation,
        ]);
    }

    /**
     * Webhook nhận tin nhắn từ Telegram Bot
     */
    public function telegramWebhook(Request $request)
    {
        $message = $request->input('message') ?? $request->input('channel_post');
        if (!$message || empty($message['text'])) {
            return response()->json(['status' => 'ignored']);
        }

        $text = $message['text'];
        $messageId = $message['message_id'] ?? null;
        $receivedAt = isset($message['date']) ? date('c', $message['date']) : now()->toIso8601String();

        // Giả lập user admin/mặc định nhận webhook
        $user = \App\Models\User::first();
        if (!$user) {
            return response()->json(['error' => 'No user found'], 500);
        }

        $rawHash = hash('sha256', "telegram_{$text}");
        if (RawItem::where('hash', $rawHash)->exists()) {
            return response()->json(['status' => 'duplicate_ignored']);
        }

        $aiResult = $this->aiService->extractDeadline($text, 'telegram', $receivedAt, 'Trí tuệ nhân tạo');

        $rawItem = RawItem::create([
            'user_id' => $user->id,
            'source' => 'telegram',
            'external_id' => (string) $messageId,
            'received_at' => now(),
            'text_content' => $text,
            'hash' => $rawHash,
            'status' => ($aiResult && $aiResult['is_deadline_candidate']) ? 'processed' : 'ignored',
        ]);

        if ($aiResult && $aiResult['is_deadline_candidate'] && !empty($aiResult['task'])) {
            $taskData = $aiResult['task'];
            $course = Course::firstOrCreate(
                ['user_id' => $user->id, 'name' => $taskData['course']],
                ['code' => 'AI']
            );

            $workspace = $user->workspaces()->first();
            $workspaceId = $workspace ? $workspace->id : Str::uuid()->toString();

            Task::create([
                'id' => (string) Str::uuid(),
                'workspace_id' => $workspaceId,
                'user_id' => $user->id,
                'title' => $taskData['title'],
                'description' => $taskData['evidence_quote'] ?? '',
                'tag' => $taskData['type'],
                'due_date' => $taskData['due_iso_candidate'] ? (int) (strtotime($taskData['due_iso_candidate']) * 1000) : null,
                'raw_item_id' => $rawItem->id,
                'course_id' => $course->id,
                'confidence' => $taskData['confidence'],
                'intent' => $taskData['intent'],
                'effort_est_min' => $taskData['effort_est_min'],
                'evidence_quote' => $taskData['evidence_quote'],
                'needs_confirmation' => $taskData['needs_confirmation'] ?? false,
            ]);
        }

        return response()->json(['status' => 'ok', 'raw_item_id' => $rawItem->id]);
    }

    /**
     * Hàng chờ xác nhận (Confirmation Queue) cho các deadline độ tin cậy thấp
     */
    public function confirmationQueue(Request $request)
    {
        $userId = $request->user()->id;
        $pendingTasks = Task::where('user_id', $userId)
            ->where('needs_confirmation', true)
            ->with(['course', 'rawItem'])
            ->orderBy('created_at', 'desc')
            ->get();

        return response()->json($pendingTasks);
    }

    /**
     * Người dùng duyệt hoặc chỉnh sửa deadline trong Hộp xác nhận
     */
    public function confirmTask(Request $request, $id)
    {
        $task = Task::where('user_id', $request->user()->id)->findOrFail($id);

        $task->update([
            'title' => $request->input('title', $task->title),
            'due_date' => $request->input('due_date', $task->due_date),
            'effort_est_min' => $request->input('effort_est_min', $task->effort_est_min),
            'needs_confirmation' => false,
            'confidence' => 1.0, // Đã được người dùng xác nhận
        ]);

        return response()->json([
            'message' => 'Đã duyệt công việc vào danh sách chính thức',
            'task' => $task,
        ]);
    }

    /**
     * Lấy danh sách việc đã sắp xếp theo Earliest Deadline First (EDF) và mức cấp bách
     */
    public function rankedTasks(Request $request)
    {
        $userId = $request->user()->id;
        $tasks = Task::where('user_id', $userId)
            ->where('completed', false)
            ->whereNotNull('due_date')
            ->get();

        $payload = $tasks->map(function ($t) {
            $isoDate = is_numeric($t->due_date) ? date('c', (int) ($t->due_date / 1000)) : date('c', strtotime($t->due_date));
            return [
                'id' => $t->id,
                'title' => $t->title,
                'due_at' => $isoDate,
                'effort_est_min' => $t->effort_est_min ?? 120,
                'progress' => $t->completed ? 1.0 : 0.0,
                'type' => $t->tag ?? 'assignment',
                'weight' => $t->important ? 3.0 : 1.0,
            ];
        })->toArray();

        $ranked = $this->aiService->rankTasks($payload);

        return response()->json([
            'tasks' => $ranked,
            'count' => count($ranked),
        ]);
    }
}
