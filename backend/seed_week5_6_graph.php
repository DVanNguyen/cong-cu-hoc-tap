<?php
/**
 * Seed Week 5 & 6 Knowledge Graph Concepts & Relations into MySQL database 'focusboard'
 */
require 'vendor/autoload.php';
$app = require_once 'bootstrap/app.php';
$kernel = $app->make(Illuminate\Contracts\Console\Kernel::class);
$kernel->bootstrap();

use Illuminate\Support\Facades\DB;

echo "=== SEEDING WEEK 5 & 6 KNOWLEDGE GRAPH (17 CONCEPTS & DAG EDGES) ===" . PHP_EOL;

$courseId = 1;

// Xóa dữ liệu cũ trong bảng quan hệ và khái niệm của môn 1
DB::table('concept_relations')->where('course_id', $courseId)->delete();
DB::table('concepts')->where('course_id', $courseId)->delete();

$conceptsData = [
    [
        'key' => 'agent',
        'canonical_name' => 'Tác tử thông minh (Intelligent Agent)',
        'name' => 'Intelligent Agent',
        'aliases' => ['Intelligent Agent', 'Tác tử', 'Agent', 'AI Agent'],
        'definition_summary' => 'Thực thể nhận thức môi trường qua sensors và hành động qua actuators theo định hướng mục tiêu.',
        'def_lecture' => 1, 'def_slide' => 1, 'used_lecture' => 1, 'used_slide' => 1, 'violation' => false
    ],
    [
        'key' => 'environment',
        'canonical_name' => 'Môi trường (Environment)',
        'name' => 'Environment',
        'aliases' => ['Environment', 'Môi trường tác tử'],
        'definition_summary' => 'Thế giới hoặc ngữ cảnh mà tác tử hoạt động và tương tác.',
        'def_lecture' => 1, 'def_slide' => 2, 'used_lecture' => 1, 'used_slide' => 2, 'violation' => false
    ],
    [
        'key' => 'sensors',
        'canonical_name' => 'Cảm biến (Sensors)',
        'name' => 'Sensors',
        'aliases' => ['Sensors', 'Cảm biến'],
        'definition_summary' => 'Thiết bị hoặc cổng giao tiếp tiếp nhận tín hiệu từ môi trường đầu vào.',
        'def_lecture' => 1, 'def_slide' => 3, 'used_lecture' => 1, 'used_slide' => 3, 'violation' => false
    ],
    [
        'key' => 'actuators',
        'canonical_name' => 'Bộ tác động (Actuators)',
        'name' => 'Actuators',
        'aliases' => ['Actuators', 'Cơ cấu tác động'],
        'definition_summary' => 'Thiết bị hoặc cơ chế thực thi hành động làm biến đổi trạng thái môi trường.',
        'def_lecture' => 1, 'def_slide' => 3, 'used_lecture' => 1, 'used_slide' => 3, 'violation' => false
    ],
    [
        'key' => 'percept_sequence',
        'canonical_name' => 'Chuỗi nhận thức (Percept Sequence)',
        'name' => 'Percept Sequence',
        'aliases' => ['Percept Sequence', 'Dãy quan sát'],
        'definition_summary' => 'Lịch sử toàn bộ các dữ liệu quan sát mà tác tử đã tiếp nhận từ trước đến nay.',
        'def_lecture' => 1, 'def_slide' => 4, 'used_lecture' => 1, 'used_slide' => 4, 'violation' => false
    ],
    [
        'key' => 'agent_function',
        'canonical_name' => 'Hàm tác tử (Agent Function)',
        'name' => 'Agent Function',
        'aliases' => ['Agent Function', 'Hàm quyết định'],
        'definition_summary' => 'Hàm toán học f ánh xạ từ chuỗi nhận thức percept sequence sang hành động cụ thể.',
        'def_lecture' => 1, 'def_slide' => 5, 'used_lecture' => 1, 'used_slide' => 5, 'violation' => false
    ],
    [
        'key' => 'peas',
        'canonical_name' => 'Khuôn mẫu PEAS (PEAS Framework)',
        'name' => 'PEAS Framework',
        'aliases' => ['PEAS', 'PEAS Framework', 'Mô hình PEAS'],
        'definition_summary' => 'Khuôn mẫu đặc tả tác tử gồm: Performance, Environment, Actuators, Sensors.',
        'def_lecture' => 1, 'def_slide' => 6, 'used_lecture' => 1, 'used_slide' => 6, 'violation' => false
    ],
    [
        'key' => 'state_space',
        'canonical_name' => 'Không gian trạng thái (State Space)',
        'name' => 'State Space',
        'aliases' => ['State Space', 'Không gian bài toán'],
        'definition_summary' => 'Tập hợp tất cả các trạng thái có thể có của bài toán cùng các phép chuyển dịch.',
        'def_lecture' => 2, 'def_slide' => 1, 'used_lecture' => 2, 'used_slide' => 1, 'violation' => false
    ],
    [
        'key' => 'bfs',
        'canonical_name' => 'Tìm kiếm theo chiều rộng (Breadth-First Search)',
        'name' => 'Breadth-First Search',
        'aliases' => ['BFS', 'Breadth-First Search', 'Tìm kiếm rộng'],
        'definition_summary' => 'Thuật toán tìm kiếm duyệt theo từng tầng chiều sâu sử dụng hàng đợi FIFO.',
        'def_lecture' => 2, 'def_slide' => 5, 'used_lecture' => 2, 'used_slide' => 5, 'violation' => false
    ],
    [
        'key' => 'dfs',
        'canonical_name' => 'Tìm kiếm theo chiều sâu (Depth-First Search)',
        'name' => 'Depth-First Search',
        'aliases' => ['DFS', 'Depth-First Search', 'Tìm kiếm sâu'],
        'definition_summary' => 'Thuật toán tìm kiếm mở rộng nhánh sâu nhất trước tiên sử dụng ngăn xếp LIFO.',
        'def_lecture' => 2, 'def_slide' => 8, 'used_lecture' => 2, 'used_slide' => 8, 'violation' => false
    ],
    [
        'key' => 'ucs',
        'canonical_name' => 'Tìm kiếm chi phí đồng nhất (Uniform Cost Search)',
        'name' => 'Uniform Cost Search',
        'aliases' => ['UCS', 'Uniform Cost Search', 'Dijkstra trên cây'],
        'definition_summary' => 'Thuật toán mở rộng node có chi phí đường đi g(n) thấp nhất sử dụng priority queue.',
        'def_lecture' => 2, 'def_slide' => 12, 'used_lecture' => 2, 'used_slide' => 12, 'violation' => false
    ],
    [
        'key' => 'heuristic_search',
        'canonical_name' => 'Tìm kiếm Heuristic (Heuristic Search)',
        'name' => 'Heuristic Search',
        'aliases' => ['Heuristic Search', 'Tìm kiếm có thông tin'],
        'definition_summary' => 'Chiến lược tìm kiếm sử dụng hàm tri thức h(n) để ước lượng khoảng cách tới đích.',
        'def_lecture' => 3, 'def_slide' => 2, 'used_lecture' => 3, 'used_slide' => 2, 'violation' => false
    ],
    [
        'key' => 'astar',
        'canonical_name' => 'Thuật toán A* (A* Search)',
        'name' => 'A* Search',
        'aliases' => ['A*', 'A* Search', 'Thuật toán A-sao'],
        'definition_summary' => 'Thuật toán tìm kiếm tối ưu kết hợp f(n) = g(n) + h(n).',
        'def_lecture' => 3, 'def_slide' => 6,
        'used_lecture' => 1, 'used_slide' => 2, // Used in Lecture 1 Slide 2 -> VIOLATION!
        'violation' => true
    ],
    [
        'key' => 'admissible_heuristic',
        'canonical_name' => 'Heuristic chấp nhận được (Admissible Heuristic)',
        'name' => 'Admissible Heuristic',
        'aliases' => ['Admissible Heuristic', 'Heuristic không đánh giá quá'],
        'definition_summary' => 'Tính chất hàm h(n) không bao giờ đánh giá vượt quá chi phí thực tế nhỏ nhất đến đích.',
        'def_lecture' => 3, 'def_slide' => 9, 'used_lecture' => 3, 'used_slide' => 9, 'violation' => false
    ],
    [
        'key' => 'consistent_heuristic',
        'canonical_name' => 'Heuristic nhất quán (Consistent Heuristic)',
        'name' => 'Consistent Heuristic',
        'aliases' => ['Consistent Heuristic', 'Monotonic Heuristic'],
        'definition_summary' => 'Tính chất đơn điệu thỏa mãn bất đẳng thức tam giác: h(n) <= c(n, a, n\') + h(n\').',
        'def_lecture' => 3, 'def_slide' => 11, 'used_lecture' => 3, 'used_slide' => 11, 'violation' => false
    ],
    [
        'key' => 'hill_climbing',
        'canonical_name' => 'Tìm kiếm leo đồi (Hill Climbing)',
        'name' => 'Hill Climbing',
        'aliases' => ['Hill Climbing', 'Leo đồi cục bộ'],
        'definition_summary' => 'Thuật toán tối ưu hóa cục bộ liên tục di chuyển theo hướng cải thiện giá trị hàm mục tiêu.',
        'def_lecture' => 4, 'def_slide' => 3, 'used_lecture' => 4, 'used_slide' => 3, 'violation' => false
    ],
    [
        'key' => 'simulated_annealing',
        'canonical_name' => 'Mô phỏng tôi thép (Simulated Annealing)',
        'name' => 'Simulated Annealing',
        'aliases' => ['Simulated Annealing', 'Tôi luyện kim loại'],
        'definition_summary' => 'Thuật toán ngẫu nhiên cho phép các bước đi xấu với xác suất giảm dần theo nhiệt độ để thoát cực trị.',
        'def_lecture' => 4, 'def_slide' => 7, 'used_lecture' => 4, 'used_slide' => 7, 'violation' => false
    ],
];

$conceptIdMap = [];
foreach ($conceptsData as $c) {
    $cid = DB::table('concepts')->insertGetId([
        'course_id' => $courseId,
        'canonical_name' => $c['canonical_name'],
        'name' => $c['name'],
        'aliases' => json_encode($c['aliases'], JSON_UNESCAPED_UNICODE),
        'definition_summary' => $c['definition_summary'],
        'first_defined_lecture_order' => $c['def_lecture'],
        'first_defined_slide_id' => $c['def_slide'],
        'first_used_lecture_order' => $c['used_lecture'],
        'first_used_slide_id' => $c['used_slide'],
        'is_used_before_defined' => $c['violation'] ? 1 : 0,
        'created_at' => now(),
        'updated_at' => now(),
    ]);
    $conceptIdMap[$c['key']] = $cid;
}

echo " [+] Đã nạp " . count($conceptIdMap) . " khái niệm chuẩn hóa." . PHP_EOL;

// Định nghĩa các cạnh quan hệ DAG
$relationsData = [
    // Pillar 1: Agent & Environment
    ['src' => 'sensors', 'dst' => 'agent', 'rel' => 'part_of', 'conf' => 0.95, 'violation' => false],
    ['src' => 'actuators', 'dst' => 'agent', 'rel' => 'part_of', 'conf' => 0.95, 'violation' => false],
    ['src' => 'environment', 'dst' => 'agent', 'rel' => 'prerequisite', 'conf' => 0.92, 'violation' => false],
    ['src' => 'percept_sequence', 'dst' => 'agent_function', 'rel' => 'prerequisite', 'conf' => 0.94, 'violation' => false],
    ['src' => 'agent_function', 'dst' => 'agent', 'rel' => 'part_of', 'conf' => 0.90, 'violation' => false],
    ['src' => 'peas', 'dst' => 'agent', 'rel' => 'example_of', 'conf' => 0.93, 'violation' => false],

    // Pillar 2: Search algorithms
    ['src' => 'state_space', 'dst' => 'bfs', 'rel' => 'prerequisite', 'conf' => 0.96, 'violation' => false],
    ['src' => 'state_space', 'dst' => 'dfs', 'rel' => 'prerequisite', 'conf' => 0.96, 'violation' => false],
    ['src' => 'bfs', 'dst' => 'ucs', 'rel' => 'prerequisite', 'conf' => 0.88, 'violation' => false],
    ['src' => 'state_space', 'dst' => 'heuristic_search', 'rel' => 'prerequisite', 'conf' => 0.95, 'violation' => false],
    ['src' => 'ucs', 'dst' => 'astar', 'rel' => 'prerequisite', 'conf' => 0.92, 'violation' => false],
    ['src' => 'heuristic_search', 'dst' => 'astar', 'rel' => 'prerequisite', 'conf' => 0.97, 'violation' => false],
    ['src' => 'admissible_heuristic', 'dst' => 'astar', 'rel' => 'prerequisite', 'conf' => 0.98, 'violation' => false],
    ['src' => 'consistent_heuristic', 'dst' => 'admissible_heuristic', 'rel' => 'prerequisite', 'conf' => 0.91, 'violation' => false],
    ['src' => 'heuristic_search', 'dst' => 'hill_climbing', 'rel' => 'prerequisite', 'conf' => 0.90, 'violation' => false],
    ['src' => 'hill_climbing', 'dst' => 'simulated_annealing', 'rel' => 'prerequisite', 'conf' => 0.89, 'violation' => false],

    // Cạnh vi phạm Used-Before-Defined có chủ đích:
    // A* được nhắc tới để giải bài toán định hướng cho Agent ở Bài 1 trong khi chưa được định nghĩa
    ['src' => 'astar', 'dst' => 'agent', 'rel' => 'prerequisite', 'conf' => 0.85, 'violation' => true],
];

$insertedRelations = 0;
// Lấy một evidence có sẵn hoặc tạo mới
$evidence = DB::table('evidences')->first();
$defaultEvidenceId = $evidence ? $evidence->id : DB::table('evidences')->insertGetId([
    'source_type' => 'slide_text',
    'lecture_id' => 1,
    'slide_id' => 1,
    'exact_quote' => 'Khái niệm tiền đề và quan hệ phụ thuộc trong cây tri thức.',
    'created_at' => now(),
    'updated_at' => now(),
]);

foreach ($relationsData as $r) {
    if (!isset($conceptIdMap[$r['src']]) || !isset($conceptIdMap[$r['dst']])) continue;

    DB::table('concept_relations')->insert([
        'course_id' => $courseId,
        'source_concept_id' => $conceptIdMap[$r['src']],
        'target_concept_id' => $conceptIdMap[$r['dst']],
        'relation_type' => $r['rel'],
        'confidence' => $r['conf'],
        'evidence_id' => $defaultEvidenceId,
        'status' => $r['violation'] ? 'pending_review' : 'verified',
        'created_at' => now(),
        'updated_at' => now(),
    ]);
    $insertedRelations++;
}

echo " [+] Đã nạp " . $insertedRelations . " quan hệ DAG (kèm 1 cạnh vi phạm Used-Before-Defined)." . PHP_EOL;
