<?php

require __DIR__ . '/vendor/autoload.php';

$app = require_once __DIR__ . '/bootstrap/app.php';
$kernel = $app->make(Illuminate\Contracts\Console\Kernel::class);
$kernel->bootstrap();

$aiClient = new \App\Services\AiServiceClient();

echo "=== 1. Testing AI Service Health ===" . PHP_EOL;
$health = $aiClient->healthCheck();
echo json_encode($health, JSON_PRETTY_PRINT | JSON_UNESCAPED_UNICODE) . PHP_EOL . PHP_EOL;

echo "=== 2. Testing Deadline Extraction from Text ===" . PHP_EOL;
$extracted = $aiClient->extractDeadline(
    "[GV] thông báo: Các em nộp bài tập chương 2 (Intelligent Agents) lên Google Drive trước 23h59 ngày 20/10/2026 nhé.",
    "telegram",
    now()->toIso8601String(),
    "Trí tuệ nhân tạo"
);
echo json_encode($extracted, JSON_PRETTY_PRINT | JSON_UNESCAPED_UNICODE) . PHP_EOL . PHP_EOL;

echo "=== 3. Testing PDF Slide Parsing (Russell & Norvig Chapter 2) ===" . PHP_EOL;
$slides = $aiClient->parseSlides("C:\\AI\\2026.Chapter 2. INTELLIGENT AGENTS (1).pdf");
echo "Total slides parsed: " . count($slides) . PHP_EOL;
if (count($slides) > 0) {
    echo "Slide 1 text preview: " . mb_substr($slides[0]['text'], 0, 100) . "..." . PHP_EOL . PHP_EOL;
}

echo "=== 4. Testing Knowledge Graph DAG Validation ===" . PHP_EOL;
$concepts = [
    ['id' => '1', 'name' => 'Intelligent Agent', 'aliases' => ['tác tử'], 'definition' => 'Agent acting on environment', 'defined_in_lecture_idx' => 1],
    ['id' => '2', 'name' => 'Rationality', 'aliases' => ['tính duy lý'], 'definition' => 'Maximizing performance measure', 'defined_in_lecture_idx' => 1],
    ['id' => '3', 'name' => 'State Space', 'aliases' => ['không gian trạng thái'], 'definition' => 'Set of reachable states', 'defined_in_lecture_idx' => 2],
    ['id' => '4', 'name' => 'A* Search', 'aliases' => ['A sao'], 'definition' => 'Optimal search algorithm with f(n) = g(n) + h(n)', 'defined_in_lecture_idx' => 2],
];
$edges = [
    ['from_concept' => 'Intelligent Agent', 'to_concept' => 'Rationality', 'relation' => 'prerequisite_of', 'evidence_quote' => 'Rational agent builds on agent definition', 'mentioned_in_lecture_idx' => 1],
    ['from_concept' => 'State Space', 'to_concept' => 'A* Search', 'relation' => 'prerequisite_of', 'evidence_quote' => 'A* operates on state space', 'mentioned_in_lecture_idx' => 2],
];
$graphResult = $aiClient->validateGraph($concepts, $edges);
echo json_encode($graphResult, JSON_PRETTY_PRINT | JSON_UNESCAPED_UNICODE) . PHP_EOL;
