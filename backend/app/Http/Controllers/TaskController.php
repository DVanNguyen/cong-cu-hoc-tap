<?php

namespace App\Http\Controllers;

use App\Models\Task;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\DB;

/**
 * TaskController - Offline-First Architecture v4.0
 */
class TaskController extends Controller
{
    /**
     * Get tasks — if workspace_id given, filter by it; otherwise return ALL user tasks
     */
    public function index(Request $request)
    {
        $workspaceId = $request->query('workspace_id');
        $filter = $request->query('filter', 'all');
        
        $userId = $request->user()->id;
        $groupIds = \App\Models\GroupMember::where('user_id', $userId)->pluck('group_id');

        $query = Task::notDeleted()
            ->where(function ($q) use ($userId, $groupIds) {
                $q->where('user_id', $userId)
                  ->orWhere('assignee_id', $userId)
                  ->orWhere('assigner_id', $userId)
                  ->orWhere(function ($gQ) use ($groupIds) {
                      $gQ->whereIn('group_id', $groupIds)
                         ->where('is_public', true);
                  });
            })
            ->notDeleted();
        
        if ($workspaceId) {
            $query->where(function ($wQ) use ($workspaceId) {
                $wQ->where('workspace_id', $workspaceId)
                   ->orWhereNotNull('group_id');
            });
        }
        
        // Apply filter
        if ($filter === 'today') {
            $todayStart = today()->startOfDay()->getTimestamp() * 1000;
            $todayEnd = today()->endOfDay()->getTimestamp() * 1000;
            $query->whereBetween('due_date', [$todayStart, $todayEnd]);
        } elseif ($filter === 'week') {
            $todayStart = today()->startOfDay()->getTimestamp() * 1000;
            $weekEnd = today()->addDays(7)->endOfDay()->getTimestamp() * 1000;
            $query->whereBetween('due_date', [$todayStart, $weekEnd]);
        } elseif ($filter === 'completed') {
            $query->where('completed', true);
        } elseif ($filter === 'pending') {
            $query->where('completed', false);
        }
        
        $tasks = $query->with(['assignee:id,name', 'assigner:id,name'])->latest('updated_at_ms')->get();
        $tasks->each(function ($task) {
            $task->assignee_name = $task->assignee?->name;
            $task->assigner_name = $task->assigner?->name;
            unset($task->assignee, $task->assigner);
        });
        return $this->successResponse($tasks, 'Tasks retrieved successfully');
    }

    /**
     * Create a new task
     */
    public function store(Request $request)
    {
        $validated = $request->validate([
            'id' => 'sometimes|uuid',
            'workspace_id' => 'nullable|exists:workspaces,id',
            'title' => 'required|string|max:255',
            'description' => 'nullable|string',
            'tag' => 'nullable|string',
            'due_date' => 'nullable|integer',
            'palette_idx' => 'sometimes|integer|between:0,4',
            'important' => 'sometimes|boolean',
            'task_type' => 'sometimes|string',
            'repeat_mode' => 'sometimes|string',
            'total_weeks' => 'sometimes|integer',
            'completed_weeks' => 'sometimes|integer',
            'missed_weeks' => 'sometimes|integer',
            'weekly_commitment' => 'sometimes|string',
            'last_completed_date' => 'sometimes|nullable|integer',
            'completed_at' => 'sometimes|nullable|integer',
            'streak_count' => 'sometimes|integer',
            'snooze_count' => 'sometimes|integer',
            'snoozed_until' => 'sometimes|nullable|integer',
            'scheduled_start_time' => 'sometimes|nullable|integer',
            'scheduled_end_time' => 'sometimes|nullable|integer',
            'updated_at_ms' => 'sometimes|integer',
            'created_at_ms' => 'sometimes|integer',
            'group_id' => 'sometimes|nullable|uuid',
            'assigner_id' => 'sometimes|nullable|uuid',
            'assignee_id' => 'sometimes|nullable|uuid',
            'parent_task_id' => 'sometimes|nullable|uuid',
            'is_public' => 'sometimes|boolean',
            'status' => 'sometimes|string',
        ]);

        if (!empty($validated['due_date']) && $validated['due_date'] < (now()->getTimestampMs() - 60000)) {
            return $this->errorResponse('Thời hạn hoàn thành không thể ở trong quá khứ', null, 422);
        }

        $taskData = [
            'workspace_id' => $validated['workspace_id'] ?? null,
            'user_id' => $request->user()->id,
            'title' => $validated['title'],
            'description' => $validated['description'] ?? '',
            'tag' => $validated['tag'] ?? '',
            'due_date' => $validated['due_date'] ?? null,
            'palette_idx' => $validated['palette_idx'] ?? 0,
            'important' => $validated['important'] ?? false,
            'task_type' => $validated['task_type'] ?? 'SMALL',
            'repeat_mode' => $validated['repeat_mode'] ?? 'NONE',
            'total_weeks' => $validated['total_weeks'] ?? 0,
            'completed_weeks' => $validated['completed_weeks'] ?? 0,
            'missed_weeks' => $validated['missed_weeks'] ?? 0,
            'weekly_commitment' => $validated['weekly_commitment'] ?? '',
            'last_completed_date' => $validated['last_completed_date'] ?? null,
            'completed_at' => $validated['completed_at'] ?? null,
            'streak_count' => $validated['streak_count'] ?? 0,
            'snooze_count' => $validated['snooze_count'] ?? 0,
            'snoozed_until' => $validated['snoozed_until'] ?? null,
            'scheduled_start_time' => $validated['scheduled_start_time'] ?? null,
            'scheduled_end_time' => $validated['scheduled_end_time'] ?? null,
            'group_id' => $validated['group_id'] ?? null,
            'assigner_id' => $validated['assigner_id'] ?? $request->user()->id,
            'assignee_id' => $validated['assignee_id'] ?? null,
            'parent_task_id' => $validated['parent_task_id'] ?? null,
            'is_public' => $validated['is_public'] ?? true,
            'status' => $validated['status'] ?? 'PENDING',
            'updated_at_ms' => $validated['updated_at_ms'] ?? now()->getTimestampMs(),
            'created_at_ms' => $validated['created_at_ms'] ?? now()->getTimestampMs(),
        ];
        
        DB::beginTransaction();
        try {
            if (isset($validated['id'])) {
                $taskData['id'] = $validated['id'];
            }
            $task = Task::create($taskData);
            
            if (!empty($request->input('subtasks')) && is_array($request->input('subtasks'))) {
                foreach ($request->input('subtasks') as $subtaskTitle) {
                    Task::create([
                        'id' => (string) \Illuminate\Support\Str::uuid(),
                        'user_id' => $task->assignee_id ?? $request->user()->id,
                        'title' => $subtaskTitle,
                        'parent_task_id' => $task->id,
                        'task_type' => 'SMALL',
                        'status' => 'PENDING',
                        'updated_at_ms' => now()->getTimestampMs(),
                        'created_at_ms' => now()->getTimestampMs(),
                        'group_id' => $task->group_id,
                        'assigner_id' => $task->assigner_id,
                        'assignee_id' => $task->assignee_id,
                    ]);
                }
            }
            DB::commit();
            
            if ($task->group_id) {
                \App\Models\Message::create([
                    'id' => (string) \Illuminate\Support\Str::uuid(),
                    'group_id' => $task->group_id,
                    'sender_id' => $request->user()->id,
                    'content' => '__SYSTEM_SYNC_TASK__',
                    'created_at_ms' => now()->timestamp * 1000,
                ]);
            }
        } catch (\Exception $e) {
            DB::rollBack();
            return $this->errorResponse('Failed to create task: ' . $e->getMessage(), null, 500);
        }

        return $this->successResponse($task, 'Task created successfully', 201);
    }

    /**
     * Get a single task
     */
    public function show(Task $task, Request $request)
    {
        $userId = $request->user()->id;
        $isOwner = $task->user_id === $userId;
        $isAssignee = $task->assignee_id === $userId;
        $isAssigner = $task->assigner_id === $userId;
        $isGroupMember = $task->group_id && \App\Models\GroupMember::where('group_id', $task->group_id)->where('user_id', $userId)->exists();
        
        if (!$isOwner && !$isAssignee && !$isAssigner && !$isGroupMember) {
            return $this->errorResponse('Unauthorized', null, 403);
        }

        // Eager load names
        $task->load(['assignee:id,name', 'assigner:id,name']);
        $task->assignee_name = $task->assignee?->name;
        $task->assigner_name = $task->assigner?->name;
        unset($task->assignee, $task->assigner);

        return $this->successResponse($task, 'Task retrieved successfully');
    }

    /**
     * Update a task
     */
    public function update(Task $task, Request $request)
    {
        if ($task->user_id !== $request->user()->id && $task->assignee_id !== $request->user()->id) {
            return $this->errorResponse('Unauthorized', null, 403);
        }

        $validated = $request->validate([
            'title' => 'sometimes|string|max:255',
            'description' => 'nullable|string',
            'tag' => 'nullable|string',
            'due_date' => 'nullable|integer',
            'completed' => 'sometimes|boolean',
            'important' => 'sometimes|boolean',
            'palette_idx' => 'sometimes|integer|between:0,4',
            'task_type' => 'sometimes|string',
            'repeat_mode' => 'sometimes|string',
            'total_weeks' => 'sometimes|integer',
            'completed_weeks' => 'sometimes|integer',
            'missed_weeks' => 'sometimes|integer',
            'weekly_commitment' => 'sometimes|string',
            'last_completed_date' => 'sometimes|nullable|integer',
            'completed_at' => 'sometimes|nullable|integer',
            'streak_count' => 'sometimes|integer',
            'snooze_count' => 'sometimes|integer',
            'snoozed_until' => 'sometimes|nullable|integer',
            'scheduled_start_time' => 'sometimes|nullable|integer',
            'scheduled_end_time' => 'sometimes|nullable|integer',
            'group_id' => 'sometimes|nullable|uuid',
            'assigner_id' => 'sometimes|nullable|uuid',
            'assignee_id' => 'sometimes|nullable|uuid',
            'parent_task_id' => 'sometimes|nullable|uuid',
            'status' => 'sometimes|string',
            'updated_at_ms' => 'sometimes|integer',
            'created_at_ms' => 'sometimes|integer',
        ]);

        $validated['updated_at_ms'] = $validated['updated_at_ms'] ?? now()->getTimestampMs();
        $task->update($validated);

        if ($task->group_id) {
            \App\Models\Message::create([
                'id' => (string) \Illuminate\Support\Str::uuid(),
                'group_id' => $task->group_id,
                'sender_id' => $request->user()->id,
                'content' => '__SYSTEM_SYNC_TASK__',
                'created_at_ms' => now()->timestamp * 1000,
            ]);
        }

        return $this->successResponse($task, 'Task updated successfully');
    }

    public function submitWork(Request $request)
    {
        $validated = $request->validate([
            'task_id' => 'required|uuid|exists:tasks,id',
            'content' => 'nullable|string',
            'file_urls' => 'nullable|array'
        ]);

        $task = Task::find($validated['task_id']);
        $isAssignee = $task->assignee_id === $request->user()->id;
        $isOwner = $task->user_id === $request->user()->id;
        $isGroupMember = $task->group_id && \App\Models\GroupMember::where('group_id', $task->group_id)->where('user_id', $request->user()->id)->exists();

        if (!$isAssignee && !$isOwner && !$isGroupMember) {
            return $this->errorResponse('Unauthorized to submit work for this task', null, 403);
        }

        $submission = \App\Models\TaskSubmission::create([
            'id' => (string) \Illuminate\Support\Str::uuid(),
            'task_id' => $task->id,
            'user_id' => $request->user()->id,
            'content' => $validated['content'] ?? '',
            'file_urls' => $validated['file_urls'] ?? []
        ]);

        $task->update([
            'status' => 'IN_REVIEW',
            'updated_at_ms' => now()->getTimestampMs()
        ]);

        if ($task->group_id) {
             \App\Models\Message::create([
                 'id' => (string) \Illuminate\Support\Str::uuid(),
                 'group_id' => $task->group_id,
                 'sender_id' => $request->user()->id,
                 'content' => '__SYSTEM_SYNC_TASK__',
                 'created_at_ms' => now()->timestamp * 1000,
             ]);
        }

        return $this->successResponse($submission, 'Work submitted successfully');
    }

    public function claimTask(Request $request, $id)
    {
        $task = Task::findOrFail($id);
        
        if (!$task->group_id) {
            return $this->errorResponse("Task is not a group task", null, 400);
        }
        
        $isMember = \App\Models\GroupMember::where("group_id", $task->group_id)
            ->where("user_id", $request->user()->id)
            ->exists();
            
        if (!$isMember) {
            return $this->errorResponse("You are not a member of this group", null, 403);
        }
        
        if ($task->assignee_id && $task->assignee_id !== $request->user()->id) {
            return $this->errorResponse("Task is already assigned to someone else", null, 400);
        }
        
        $task->update([
            "assignee_id" => $request->user()->id,
            "updated_at_ms" => now()->getTimestampMs()
        ]);

        if ($task->group_id) {
             \App\Models\Message::create([
                 'id' => (string) \Illuminate\Support\Str::uuid(),
                 'group_id' => $task->group_id,
                 'sender_id' => $request->user()->id,
                 'content' => '__SYSTEM_SYNC_TASK__',
                 'created_at_ms' => now()->timestamp * 1000,
             ]);
        }
        
        return $this->successResponse($task, "Task claimed successfully");
    }

    public function uploadAttachment(Request $request)
    {
        $request->validate([
            'file' => 'required|file|max:51200' // max 50MB
        ]);

        $file = $request->file('file');
        $originalName = $file->getClientOriginalName();
        $path = $file->store('task_submissions', 'public');
        $url = asset('storage/' . $path);

        return $this->successResponse([
            'url' => $url,
            'path' => '/storage/' . $path,
            'name' => $originalName,
            'size' => $file->getSize()
        ], 'File uploaded successfully');
    }

    public function getSubmissions($id)
    {
        $task = Task::find($id);
        if (!$task) {
            $submission = \App\Models\TaskSubmission::find($id);
            if ($submission) {
                return $this->successResponse([$submission], 'Submissions retrieved successfully');
            }
            return $this->errorResponse('Task not found', null, 404);
        }

        $submissions = \App\Models\TaskSubmission::where('task_id', $task->id)
            ->with('user:id,name,email')
            ->latest()
            ->get();

        return $this->successResponse($submissions, 'Submissions retrieved successfully');
    }

    public function reviewWork(Request $request, $id)
    {
        $validated = $request->validate([
            'action' => 'required|string|in:APPROVE,REJECT',
            'task_id' => 'sometimes|nullable|string',
            'note' => 'sometimes|nullable|string'
        ]);

        $task = null;
        $submission = \App\Models\TaskSubmission::find($id);
        if ($submission) {
            $task = $submission->task;
        }

        if (!$task) {
            $task = Task::find($id);
        }

        if (!$task && !empty($validated['task_id'])) {
            $task = Task::find($validated['task_id']);
        }

        if (!$task && !empty($request->input('task_id'))) {
            $task = Task::find($request->input('task_id'));
        }

        if (!$task) {
            return $this->errorResponse('Task or submission not found', null, 404);
        }

        // Verify authorization: assigner, task owner, or group admin
        $userId = $request->user()->id;
        $isAssigner = ($task->assigner_id === $userId || $task->user_id === $userId);
        $isGroupAdmin = $task->group_id && (
            \App\Models\Group::where('id', $task->group_id)->where('owner_id', $userId)->exists() ||
            \App\Models\GroupMember::where('group_id', $task->group_id)->where('user_id', $userId)->whereIn('role', ['admin', 'owner'])->exists()
        );

        if (!$isAssigner && !$isGroupAdmin) {
            return $this->errorResponse('Only the assigner or group admin can review this work', null, 403);
        }

        if ($submission && !empty($validated['note'])) {
            $submission->update(['reviewer_note' => $validated['note']]);
        }

        $isLargeTask = ($task->task_type === 'LARGE');
        $nowMs = now()->getTimestampMs();

        if ($validated['action'] === 'APPROVE') {
            if ($isLargeTask) {
                $totalWeeks = max(1, (int)($task->total_weeks ?? 1));
                $newCompletedWeeks = min($totalWeeks, ((int)($task->completed_weeks ?? 0)) + 1);
                $isAllCompleted = ($newCompletedWeeks >= $totalWeeks);
                
                $task->update([
                    'completed_weeks' => $newCompletedWeeks,
                    'status' => $isAllCompleted ? 'COMPLETED' : 'ACTIVE',
                    'completed' => $isAllCompleted,
                    'last_completed_date' => $nowMs,
                    'updated_at_ms' => $nowMs
                ]);
            } else {
                $task->update([
                    'status' => 'COMPLETED',
                    'completed' => true,
                    'last_completed_date' => $nowMs,
                    'updated_at_ms' => $nowMs
                ]);
            }
        } else {
            $task->update([
                'status' => 'REJECTED',
                'completed' => false,
                'updated_at_ms' => $nowMs
            ]);
        }

        if ($task->group_id) {
             \App\Models\Message::create([
                 'id' => (string) \Illuminate\Support\Str::uuid(),
                 'group_id' => $task->group_id,
                 'sender_id' => $request->user()->id,
                 'content' => '__SYSTEM_SYNC_TASK__',
                 'created_at_ms' => now()->timestamp * 1000,
             ]);
        }

        // Eager load assignee and assigner
        $task->load(['assignee:id,name', 'assigner:id,name']);
        $task->assignee_name = $task->assignee?->name;
        $task->assigner_name = $task->assigner?->name;
        unset($task->assignee, $task->assigner);

        return $this->successResponse($task, 'Work reviewed successfully');
    }

    /**
     * Soft delete a task
     */
    public function destroy(Task $task, Request $request)
    {
        if ($task->user_id !== $request->user()->id) {
            return $this->errorResponse('Unauthorized', null, 403);
        }

        $task->softDelete();
        return $this->successResponse(null, 'Task deleted successfully');
    }

    /**
     * Restore a soft-deleted task
     */
    public function restore(Task $task, Request $request)
    {
        if ($task->user_id !== $request->user()->id) {
            return $this->errorResponse('Unauthorized', null, 403);
        }

        $task->restore();
        return $this->successResponse($task, 'Task restored successfully');
    }

    /**
     * Batch sync tasks from Android
     */
    public function sync(Request $request)
    {
        $validated = $request->validate([
            'items' => 'required|array',
            'items.*.id' => 'required|string|uuid',
            'items.*.workspace_id' => 'nullable|string',
            'items.*.title' => 'sometimes|string|max:255',
            'items.*.tag' => 'sometimes|nullable|string',
            'items.*.palette_idx' => 'sometimes|integer|between:0,4',
            'items.*.important' => 'sometimes|boolean',
            'items.*.completed' => 'sometimes|boolean',
            'items.*.is_deleted' => 'sometimes|boolean',
            'items.*.updated_at_ms' => 'required|integer',
            'items.*.created_at_ms' => 'sometimes|integer',
            
            'items.*.task_type' => 'sometimes|string',
            'items.*.repeat_mode' => 'sometimes|string',
            'items.*.total_weeks' => 'sometimes|integer',
            'items.*.completed_weeks' => 'sometimes|integer',
            'items.*.missed_weeks' => 'sometimes|integer',
            'items.*.weekly_commitment' => 'sometimes|nullable|string',
            
            'items.*.last_completed_date' => 'sometimes|nullable|integer',
            'items.*.completed_at' => 'sometimes|nullable|integer',
            'items.*.streak_count' => 'sometimes|integer',
            'items.*.snooze_count' => 'sometimes|integer',
            'items.*.snoozed_until' => 'sometimes|nullable|integer',
            
            'items.*.scheduled_start_time' => 'sometimes|nullable|integer',
            'items.*.scheduled_end_time' => 'sometimes|nullable|integer',
            'items.*.due_date' => 'sometimes|nullable|integer',
            
            'items.*.group_id' => 'sometimes|nullable|uuid',
            'items.*.assigner_id' => 'sometimes|nullable|uuid',
            'items.*.assignee_id' => 'sometimes|nullable|uuid',
            'items.*.parent_task_id' => 'sometimes|nullable|uuid',
            'items.*.is_public' => 'sometimes|boolean',
            'items.*.status' => 'sometimes|string',
        ]);

        $items = $validated['items'];
        $syncedIds = [];
        $syncedGroupIds = [];

        foreach ($items as $item) {
            $task = Task::find($item['id']);

            if ($task) {
                // Ensure the user owns or is assigned to or in group for this task
                $isOwner = $task->user_id === $request->user()->id;
                $isAssignee = $task->assignee_id === $request->user()->id;
                $isAssigner = $task->assigner_id === $request->user()->id;
                $isGroupMember = $task->group_id && \App\Models\GroupMember::where('group_id', $task->group_id)->where('user_id', $request->user()->id)->exists();
                
                if (!$isOwner && !$isAssignee && !$isAssigner && !$isGroupMember) {
                    continue;
                }
                
                // Only update if remote version is newer
                if ($item['updated_at_ms'] >= ($task->updated_at_ms ?? 0)) {
                    $task->update($item);
                    $syncedIds[] = $task->id;
                    if ($task->group_id) {
                        $syncedGroupIds[$task->group_id] = true;
                    }
                } else {
                    $syncedIds[] = $task->id; // Acknowledge anyway
                }
            } else {
                // Create new task
                $item['user_id'] = $request->user()->id;
                if (empty($item['assigner_id'])) {
                    $item['assigner_id'] = $request->user()->id;
                }
                
                // Set default values for optional fields if creating
                $item['title'] = $item['title'] ?? 'Untitled';
                $item['weekly_commitment'] = $item['weekly_commitment'] ?? '';
                $item['tag'] = $item['tag'] ?? '';
                
                $newTask = Task::create($item);
                $syncedIds[] = $newTask->id;
                if (!empty($newTask->group_id)) {
                    $syncedGroupIds[$newTask->group_id] = true;
                }
            }
        }

        // Broadcast sync to group members
        foreach (array_keys($syncedGroupIds) as $gId) {
            \App\Models\Message::create([
                'id' => (string) \Illuminate\Support\Str::uuid(),
                'group_id' => $gId,
                'sender_id' => $request->user()->id,
                'content' => '__SYSTEM_SYNC_TASK__',
                'created_at_ms' => now()->timestamp * 1000,
            ]);
        }

        return response()->json([
            'success' => true,
            'synced_ids' => $syncedIds,
        ]);
    }
}
