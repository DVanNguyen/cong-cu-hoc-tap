<?php
/**
 * Seed Week 7 Multi-source Tasks, Milestones & Raw Messages into MySQL database 'focusboard'
 */
require 'vendor/autoload.php';
$app = require_once 'bootstrap/app.php';
$kernel = $app->make(Illuminate\Contracts\Console\Kernel::class);
$kernel->bootstrap();

use Illuminate\Support\Facades\DB;
use Illuminate\Support\Str;

echo "=== SEEDING WEEK 7 MULTI-SOURCE TASKS & MILESTONES ===" . PHP_EOL;

$courseId = 1;

$user = DB::table('users')->first();
if (!$user) {
    $userId = (string) Str::uuid();
    DB::table('users')->insert([
        'id' => $userId,
        'name' => 'Đàm Văn Nguyên',
        'email' => 'nguyendv.24it@vku.udn.vn',
        'password' => bcrypt('password123'),
        'created_at' => now(),
        'updated_at' => now(),
    ]);
} else {
    $userId = $user->id;
}

// Xóa dữ liệu cũ
DB::table('task_milestones')->delete();
DB::table('tasks')->where('course_id', $courseId)->delete();

$tasksData = [
    [
        'title' => 'Bài tập lớn: Cài đặt thuật toán A* và Heuristic xe tự hành',
        'desc' => 'Ưu tiên CRITICAL: Khối lượng công việc lớn (8.0h) trong khi chỉ còn 2 ngày trước hạn chót.',
        'due_at' => date('Y-m-d 23:59:59', strtotime('+2 days')),
        'urgency_score' => 88.0,
        'effort_hours' => 8.0,
        'status' => 'TODO',
        'source_type' => 'telegram',
        'sender_name' => 'TS. Trần Uyên Trang (Telegram Bot)',
        'evidence_quote' => '[GV] Nhắc nhở: Các nhóm nộp Bài tập lớn A* trước 23h59 ngày kia trên Drive môn học.',
        'milestones' => [
            ['title' => 'Mốc 1: Cài đặt hàm heuristic Manhattan & Euclidean', 'hours' => 2.0, 'days_offset' => 0.5, 'done' => true],
            ['title' => 'Mốc 2: Hiện thực Priority Queue và thuật toán A*', 'hours' => 4.0, 'days_offset' => 1.2, 'done' => false],
            ['title' => 'Mốc 3: Chạy thực nghiệm mê cung & Viết báo cáo PDF', 'hours' => 2.0, 'days_offset' => 1.8, 'done' => false],
        ]
    ],
    [
        'title' => 'Báo cáo Thực hành Lab 2: Không gian trạng thái và BFS/DFS',
        'desc' => 'Ưu tiên HIGH: Bài thực hành cơ bản cần nộp trong tuần, còn 4 ngày.',
        'due_at' => date('Y-m-d 23:59:59', strtotime('+4 days')),
        'urgency_score' => 65.0,
        'effort_hours' => 4.0,
        'status' => 'TODO',
        'source_type' => 'lecture_audio',
        'sender_name' => 'Lời dặn trong buổi giảng Bài 2',
        'evidence_quote' => 'Các em lưu ý nộp bài tập thực hành Lab 2 trước 23h59 Chủ nhật tuần này nhé.',
        'milestones' => [
            ['title' => 'Mốc 1: Hoàn thành code duyệt cây trạng thái BFS', 'hours' => 2.0, 'days_offset' => 1.5, 'done' => true],
            ['title' => 'Mốc 2: So sánh bộ nhớ tiêu thụ giữa BFS và DFS', 'hours' => 2.0, 'days_offset' => 3.0, 'done' => false],
        ]
    ],
    [
        'title' => 'Trắc nghiệm kiến thức PEAS & Môi trường tác tử (Quiz 1)',
        'desc' => 'Ưu tiên MEDIUM: Quiz online ngắn (1.0h), hạn còn 6 ngày.',
        'due_at' => date('Y-m-d 23:59:59', strtotime('+6 days')),
        'urgency_score' => 35.0,
        'effort_hours' => 1.0,
        'status' => 'TODO',
        'source_type' => 'email',
        'sender_name' => 'Hệ thống LMS VKU (Email thông báo)',
        'evidence_quote' => 'Hệ thống mở bài kiểm tra Quiz 1 về PEAS từ hôm nay đến 23h59 cuối tuần sau.',
        'milestones' => [
            ['title' => 'Mốc 1: Đọc lại Slide 1 đến Slide 6 Chapter 2', 'hours' => 0.5, 'days_offset' => 2.0, 'done' => false],
            ['title' => 'Mốc 2: Vào LMS làm bài trắc nghiệm 20 câu', 'hours' => 0.5, 'days_offset' => 5.0, 'done' => false],
        ]
    ],
    [
        'title' => 'Đọc trước tài liệu: Giải thuật di truyền (Genetic Algorithms)',
        'desc' => 'Ưu tiên LOW: Đọc mở rộng chuẩn bị cho tuần sau.',
        'due_at' => date('Y-m-d 23:59:59', strtotime('+10 days')),
        'urgency_score' => 15.0,
        'effort_hours' => 2.0,
        'status' => 'TODO',
        'source_type' => 'lecture_audio',
        'sender_name' => 'Giảng viên dặn cuối buổi',
        'evidence_quote' => 'Về nhà các em đọc trước phần Genetic Algorithm trong sách AIMA.',
        'milestones' => [
            ['title' => 'Mốc 1: Đọc chương 4 sách Russell & Norvig', 'hours' => 2.0, 'days_offset' => 7.0, 'done' => false],
        ]
    ]
];

$insertedTasks = 0;
$insertedMilestones = 0;

foreach ($tasksData as $t) {
    $taskId = (string) Str::uuid();
    $dueTs = strtotime($t['due_at']);

    // 1. Tạo RawMessage
    $sourceChannel = match ($t['source_type']) {
        'email' => 'gmail',
        'lecture_audio' => 'lecture_oral',
        default => 'telegram',
    };

    $rawMsgId = DB::table('raw_messages')->insertGetId([
        'user_id' => $userId,
        'source_channel' => $sourceChannel,
        'external_message_id' => 'msg_' . Str::random(8),
        'sender' => $t['sender_name'],
        'subject' => $t['title'],
        'body_text' => $t['evidence_quote'],
        'received_at' => now(),
        'created_at' => now(),
        'updated_at' => now(),
    ]);

    // 2. Tạo Task
    DB::table('tasks')->insert([
        'id' => $taskId,
        'course_id' => $courseId,
        'user_id' => $userId,
        'raw_item_id' => null,
        'title' => $t['title'],
        'description' => $t['desc'],
        'due_at' => $t['due_at'],
        'due_date' => $dueTs * 1000,
        'urgency_score' => $t['urgency_score'],
        'urgency_explanation' => $t['desc'],
        'estimated_effort_hours' => $t['effort_hours'],
        'remaining_effort_hours' => $t['effort_hours'],
        'status' => $t['status'],
        'intent' => 'create',
        'evidence_quote' => $t['evidence_quote'],
        'needs_confirmation' => 0,
        'completed' => 0,
        'palette_idx' => 1,
        'important' => $t['urgency_score'] >= 80 ? 1 : 0,
        'updated_at_ms' => round(microtime(true) * 1000),
        'is_deleted' => 0,
        'task_type' => 'assignment',
        'confidence' => 0.95,
        'effort_est_min' => (int) ($t['effort_hours'] * 60),
        'load_ratio' => $t['urgency_score'] / 100.0,
        'created_at' => now(),
        'updated_at' => now(),
    ]);
    $insertedTasks++;

    // 3. Tạo Milestones
    foreach ($t['milestones'] as $order => $m) {
        $suggestedDate = date('Y-m-d H:i:s', time() + (int)($m['days_offset'] * 86400));
        DB::table('task_milestones')->insert([
            'task_id' => $taskId,
            'step_order' => $order + 1,
            'title' => $m['title'],
            'estimated_hours' => $m['hours'],
            'suggested_deadline' => $suggestedDate,
            'is_completed' => $m['done'] ? 1 : 0,
            'created_at' => now(),
            'updated_at' => now(),
        ]);
        $insertedMilestones++;
    }
}

echo " [+] Đã nạp thành công {$insertedTasks} nhiệm vụ đa nguồn và {$insertedMilestones} mốc chia nhỏ (milestones)." . PHP_EOL;
