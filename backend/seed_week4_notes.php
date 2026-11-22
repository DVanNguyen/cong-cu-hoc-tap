<?php
/**
 * Seed Week 4 Lecture Notes & Evidences into MySQL database 'focusboard'
 */
require 'vendor/autoload.php';
$app = require_once 'bootstrap/app.php';
$kernel = $app->make(Illuminate\Contracts\Console\Kernel::class);
$kernel->bootstrap();

use Illuminate\Support\Facades\DB;

echo "=== SEEDING WEEK 4 LECTURE NOTES & EVIDENCES ===" . PHP_EOL;

$slides = DB::table('slides')->where('lecture_id', 1)->orderBy('slide_number')->get()->keyBy('slide_number');

$sampleNotes = [
    [
        'slide_number' => 1,
        'bullet_content' => 'Giới thiệu Chapter 2 về Intelligent Agents trong hệ thống trí tuệ nhân tạo hiện đại.',
        'exact_quote' => 'Hôm nay chúng ta bắt đầu Chapter 2 về Intelligent Agents trong hệ thống trí tuệ nhân tạo.',
        'start_sec' => 0.0,
        'end_sec' => 12.5,
    ],
    [
        'slide_number' => 1,
        'bullet_content' => 'Định nghĩa cơ chế tương tác: Agent nhận biết thế giới qua Sensors và hành động qua Actuators.',
        'exact_quote' => 'Một agent tương tác với environment thông qua sensors và actuators.',
        'start_sec' => 13.0,
        'end_sec' => 28.0,
    ],
    [
        'slide_number' => 2,
        'bullet_content' => 'Chi tiết 4 thành phần PEAS: Performance measure, Environment, Actuators, Sensors.',
        'exact_quote' => 'Mô hình PEAS bao gồm Performance measure, Environment, Actuators, Sensors.',
        'start_sec' => 65.5,
        'end_sec' => 85.0,
    ],
    [
        'slide_number' => 7,
        'bullet_content' => 'Thuật toán Breadth-First Search (BFS) sử dụng hàng đợi FIFO, bảo đảm tính tối ưu với chi phí bước đều nhau.',
        'exact_quote' => 'Thuật toán Breadth-First Search duyệt theo chiều rộng, dùng queue FIFO.',
        'start_sec' => 20.5,
        'end_sec' => 40.0,
    ],
    [
        'slide_number' => 11,
        'bullet_content' => 'Thuật toán A* kết hợp hàm đánh giá f(n) = g(n) + h(n), cân bằng tối ưu giữa chi phí thực tế và ước lượng.',
        'exact_quote' => 'Thuật toán A* kết hợp f(n) = g(n) + h(n), cân bằng giữa chi phí đã đi và ước lượng tới đích.',
        'start_sec' => 22.5,
        'end_sec' => 45.0,
    ],
];

// Xóa dữ liệu cũ nếu có
DB::table('lecture_notes')->where('slide_id', '>', 0)->delete();

$insertedNotes = 0;
foreach ($sampleNotes as $item) {
    $slide = $slides->get($item['slide_number']);
    if (!$slide) continue;

    // 1. Tạo bản ghi Evidence
    $evidenceId = DB::table('evidences')->insertGetId([
        'source_type'   => 'lecture_audio',
        'lecture_id'    => 1,
        'slide_id'      => $slide->id,
        'exact_quote'   => $item['exact_quote'],
        'start_time_sec'=> $item['start_sec'],
        'end_time_sec'  => $item['end_sec'],
        'created_at'    => now(),
        'updated_at'    => now(),
    ]);

    // 2. Tạo bản ghi LectureNote
    DB::table('lecture_notes')->insert([
        'slide_id'         => $slide->id,
        'evidence_id'      => $evidenceId,
        'bullet_content'   => $item['bullet_content'],
        'term_explanations'=> json_encode(['AI', 'Agent', 'PEAS']),
        'is_edited_by_user'=> 0,
        'created_at'       => now(),
        'updated_at'       => now(),
    ]);

    $insertedNotes++;
}

echo " [+] Đã nạp thành công {$insertedNotes} ghi chú mở rộng và bằng chứng vào CSDL." . PHP_EOL;
