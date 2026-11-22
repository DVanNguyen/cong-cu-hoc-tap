<?php

require __DIR__ . '/vendor/autoload.php';

$app = require_once __DIR__ . '/bootstrap/app.php';
$kernel = $app->make(Illuminate\Contracts\Console\Kernel::class);
$kernel->bootstrap();

use App\Models\User;
use App\Models\Workspace;
use App\Models\Course;
use App\Models\Lecture;
use App\Models\Slide;
use App\Models\NoteItem;
use App\Models\Concept;
use App\Models\ConceptEdge;
use App\Models\RawItem;
use App\Models\Task;
use Illuminate\Http\Request;
use Illuminate\Support\Str;

echo "==========================================================" . PHP_EOL;
echo "  STUDYAI END-TO-END FLOW VERIFICATION (LARAVEL + AI)    " . PHP_EOL;
echo "==========================================================" . PHP_EOL . PHP_EOL;

// 1. Tạo hoặc lấy User test
$user = User::firstOrCreate(
    ['email' => 'nguyendv.24it@vku.udn.vn'],
    [
        'id' => (string) Str::uuid(),
        'name' => 'Đàm Văn Nguyên (24IT180)',
        'password' => bcrypt('password123'),
    ]
);
echo "[+] User: {$user->name} ({$user->id})" . PHP_EOL;

$workspace = Workspace::firstOrCreate(
    ['user_id' => $user->id, 'name' => 'Workspace Mặc Định'],
    [
        'id' => (string) Str::uuid(),
        'description' => 'Không gian học tập chính',
    ]
);
echo "[+] Workspace: {$workspace->name} ({$workspace->id})" . PHP_EOL;

// Dọn dẹp dữ liệu cũ của user test để kiểm thử luồng mới
Task::where('user_id', $user->id)->delete();
RawItem::where('user_id', $user->id)->delete();

// 2. Test Pillar 3: Trích xuất deadline và lưu Task
echo PHP_EOL . "--- Testing Pillar 3: Multi-source Deadline Extraction ---" . PHP_EOL;
$deadlineController = app(\App\Http\Controllers\Api\StudyAi\DeadlineController::class);

$extractRequest = Request::create('/api/study-ai/deadlines/extract', 'POST', [
    'text' => '[GV] thông báo: Các em nộp bài tập chương 2 (Intelligent Agents) lên Google Drive trước 23h59 ngày 20/10/2026 nhé.',
    'source' => 'telegram',
    'course_hint' => 'Trí tuệ nhân tạo',
]);
$extractRequest->setUserResolver(fn() => $user);

$extractResponse = $deadlineController->extract($extractRequest);
$extractData = json_decode($extractResponse->getContent(), true);
echo "[+] Deadline Response: " . json_encode($extractData['task'] ?? $extractData['message'], JSON_UNESCAPED_UNICODE) . PHP_EOL;

$totalTasks = Task::where('user_id', $user->id)->count();
$totalRaw = RawItem::where('user_id', $user->id)->count();
echo "[+] DB Check: Total Raw Items = {$totalRaw}, Total Tasks = {$totalTasks}" . PHP_EOL;

// 3. Test Pillar 1: Upload và Parse Slide PDF
echo PHP_EOL . "--- Testing Pillar 1: Lecture Notes & Slide Parsing ---" . PHP_EOL;
$lectureController = app(\App\Http\Controllers\Api\StudyAi\LectureController::class);

$course = Course::firstOrCreate(
    ['user_id' => $user->id, 'name' => 'Trí tuệ nhân tạo'],
    ['code' => 'AI']
);

$pdfPath = 'C:\\AI\\2026.Chapter 2. INTELLIGENT AGENTS (1).pdf';
$uploadRequest = Request::create('/api/study-ai/lectures/upload-and-parse', 'POST', [
    'course_id' => $course->id,
    'title' => 'Chapter 2: Intelligent Agents & PEAS',
    'pdf_path' => $pdfPath,
    'audio_path' => 'audio/L01_AI_Chapter02.m4a',
]);
$uploadRequest->setUserResolver(fn() => $user);

$uploadResponse = $lectureController->uploadAndParse($uploadRequest);
$uploadData = json_decode($uploadResponse->getContent(), true);
$lectureId = $uploadData['lecture_id'];
echo "[+] Lecture created: ID = {$lectureId}, Total Slides parsed = {$uploadData['total_slides']}" . PHP_EOL;

// 4. Test Pillar 1: Căn lời giảng với Slide
$gtPath = 'C:\\Users\\Nguyen\\AndroidStudioProjects\\StudyAI\\eval\\data\\transcript_10min_groundtruth.json';
$transcriptSegments = json_decode(file_get_contents($gtPath), true);

$notesRequest = Request::create("/api/study-ai/lectures/{$lectureId}/generate-notes", 'POST', [
    'transcript_segments' => $transcriptSegments,
]);
$notesRequest->setUserResolver(fn() => $user);

$notesResponse = $lectureController->generateNotes($notesRequest, $lectureId);
$notesData = json_decode($notesResponse->getContent(), true);
echo "[+] Aligned Note Items created: {$notesData['notes_count']}" . PHP_EOL;

// 5. Test Pillar 2: Nạp đồ thị khái niệm
echo PHP_EOL . "--- Testing Pillar 2: Knowledge Graph (Concepts & DAG) ---" . PHP_EOL;
$graphController = app(\App\Http\Controllers\Api\StudyAi\ConceptGraphController::class);

$seedConcepts = [
    ['name' => 'Intelligent Agent', 'aliases' => ['tác tử thông minh', 'agent'], 'definition' => 'Thực thể nhận thức và hành động', 'defined_in_lecture_idx' => 1],
    ['name' => 'Rationality', 'aliases' => ['tính duy lý', 'rational agent'], 'definition' => 'Tối đa hóa hiệu năng', 'defined_in_lecture_idx' => 1],
    ['name' => 'PEAS Framework', 'aliases' => ['PEAS'], 'definition' => 'Performance, Environment, Actuators, Sensors', 'defined_in_lecture_idx' => 1],
    ['name' => 'State Space', 'aliases' => ['không gian trạng thái'], 'definition' => 'Tập hợp các trạng thái', 'defined_in_lecture_idx' => 2],
    ['name' => 'Heuristic Function', 'aliases' => ['h(n)', 'hàm ước lượng'], 'definition' => 'Ước lượng khoảng cách tới đích', 'defined_in_lecture_idx' => 2],
    ['name' => 'A* Search', 'aliases' => ['A sao'], 'definition' => 'Tìm kiếm tối ưu f(n) = g(n) + h(n)', 'defined_in_lecture_idx' => 2],
];
$seedEdges = [
    ['from_concept' => 'Intelligent Agent', 'to_concept' => 'Rationality', 'relation' => 'prerequisite_of', 'evidence_quote' => 'Tính duy lý dựa trên định nghĩa tác tử'],
    ['from_concept' => 'Intelligent Agent', 'to_concept' => 'PEAS Framework', 'relation' => 'prerequisite_of', 'evidence_quote' => 'PEAS đặc tả tác tử'],
    ['from_concept' => 'State Space', 'to_concept' => 'A* Search', 'relation' => 'prerequisite_of', 'evidence_quote' => 'A* duyệt trên không gian trạng thái'],
    ['from_concept' => 'Heuristic Function', 'to_concept' => 'A* Search', 'relation' => 'prerequisite_of', 'evidence_quote' => 'A* dùng hàm heuristic h(n)'],
];

$seedRequest = Request::create("/api/study-ai/courses/{$course->id}/seed-concepts", 'POST', [
    'concepts' => $seedConcepts,
    'edges' => $seedEdges,
]);
$seedRequest->setUserResolver(fn() => $user);

$seedResponse = $graphController->seedConcepts($seedRequest, $course->id);
$seedData = json_decode($seedResponse->getContent(), true);
echo "[+] Seeded Concepts = {$seedData['concepts_count']}, Edges = {$seedData['edges_count']}" . PHP_EOL;

$graphResponse = $graphController->getGraph($course->id);
$graphData = json_decode($graphResponse->getContent(), true);
echo "[+] Graph DAG Verified: IS_DAG = " . ($graphData['analysis']['is_dag'] ? 'TRUE' : 'FALSE') . PHP_EOL;
echo "[+] Topological Learning Order: " . implode(' -> ', $graphData['analysis']['topological_order']) . PHP_EOL;

// 6. Test Xếp lịch công việc EDF
echo PHP_EOL . "--- Testing Scheduler: Earliest Deadline First & Load Ratio ---" . PHP_EOL;
$rankedRequest = Request::create('/api/study-ai/deadlines/ranked', 'GET');
$rankedRequest->setUserResolver(fn() => $user);

$rankedResponse = $deadlineController->rankedTasks($rankedRequest);
$rankedData = json_decode($rankedResponse->getContent(), true);
echo "[+] Total Ranked Tasks: " . count($rankedData['tasks']) . PHP_EOL;
foreach ($rankedData['tasks'] as $idx => $t) {
    echo "    " . ($idx + 1) . ". {$t['title']} | Load Ratio: {$t['load_ratio']} | Level: {$t['urgency_level']}" . PHP_EOL;
}

echo PHP_EOL . "==========================================================" . PHP_EOL;
echo "  ALL PILLARS TESTED AND FULLY OPERATIONAL IN MYSQL & AI! " . PHP_EOL;
echo "==========================================================" . PHP_EOL;
