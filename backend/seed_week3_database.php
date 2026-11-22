<?php

require __DIR__ . '/vendor/autoload.php';
$app = require_once __DIR__ . '/bootstrap/app.php';
$kernel = $app->make(Illuminate\Contracts\Console\Kernel::class);
$kernel->bootstrap();

use App\Models\Course;
use App\Models\Lecture;
use App\Models\Slide;
use App\Models\SlideAlignment;
use App\Models\TranscriptSegment;
use App\Models\Evidence;
use Illuminate\Support\Str;

echo "========================================================\n";
echo "  SEEDING WEEK 3 DATA INTO MYSQL (STUDY ASSISTANT)      \n";
echo "========================================================\n\n";

$course = Course::first();
if (!$course) {
    die("No course found.\n");
}
echo "[+] Course: {$course->name} (Code: {$course->code})\n";

// 1. Tạo/cập nhật 3 bài giảng tương ứng với các bộ slide
$lectureDefinitions = [
    [
        'order' => 1,
        'title' => 'Chapter 1: Introduction to Artificial Intelligence & Agents',
        'slide' => 'Chapter 1. INTELLIGENT AGENTS (1).pdf',
        'audio' => 'dataset_eval/audio/lecture_01_agents.wav'
    ],
    [
        'order' => 2,
        'title' => 'Chapter 2: Intelligent Agents & PEAS Framework',
        'slide' => '2026.Chapter 2. INTELLIGENT AGENTS (1).pdf',
        'audio' => 'dataset_eval/audio/lecture_01_agents.wav'
    ],
    [
        'order' => 3,
        'title' => 'Chapter 3: Solving Problems by Searching & Heuristics',
        'slide' => 'Chapter 3. SOLVING PROBLEMS BY SEARCHING (1).pdf',
        'audio' => 'dataset_eval/audio/lecture_02_search.wav'
    ]
];

$lectures = [];
foreach ($lectureDefinitions as $def) {
    $lec = Lecture::firstOrCreate(
        [
            'course_id' => $course->id,
            'lecture_order' => $def['order']
        ],
        [
            'title' => $def['title'],
            'slide_file_path' => 'dataset_eval/slides/' . $def['slide'],
            'audio_file_path' => $def['audio'],
            'processing_status' => 'completed',
            'progress_percent' => 100,
            'recorded_at' => now()->subDays(7 - $def['order'])
        ]
    );
    $lec->update([
        'title' => $def['title'],
        'slide_file_path' => 'dataset_eval/slides/' . $def['slide'],
        'audio_file_path' => $def['audio'],
        'processing_status' => 'completed',
        'progress_percent' => 100
    ]);
    $lectures[$def['order']] = $lec;
    echo "  [+] Lecture #{$def['order']}: {$lec->title} (ID: {$lec->id})\n";
}

$mainLecture = $lectures[2]; // Chapter 2 làm bài giảng chính cho 18 segment đánh giá

// 2. Đảm bảo ít nhất 18 slide cho mainLecture
$slides = [];
for ($p = 1; $p <= 25; $p++) {
    $sl = Slide::firstOrCreate(
        [
            'lecture_id' => $mainLecture->id,
            'slide_number' => $p
        ],
        [
            'page_number' => $p,
            'title' => "Chapter 2 - Slide $p: Intelligent Agents",
            'raw_text' => "Nội dung slide $p: Kiến thức về Intelligent Agent, Sensors, Actuators và mô hình PEAS.",
            'extracted_terms' => ['Agent', 'PEAS', 'Sensors', 'Actuators'],
            'qdrant_point_id' => (string) Str::uuid()
        ]
    );
    $slides[$p] = $sl;
}
echo "[+] Slides checked/created for Lecture #{$mainLecture->id}: " . count($slides) . " slides.\n";

// 3. Đọc eval_slide_alignment.json và nạp vào slide_alignments
$alignJsonPath = 'c:/Users/Nguyen/AndroidStudioProjects/StudyAI/dataset_eval/eval_slide_alignment.json';
if (file_exists($alignJsonPath)) {
    $alignData = json_decode(file_get_contents($alignJsonPath), true);
    SlideAlignment::where('lecture_id', $mainLecture->id)->delete();
    foreach ($alignData as $item) {
        $slideNumber = $item['true_slide_number'];
        $slideId = isset($slides[$slideNumber]) ? $slides[$slideNumber]->id : $slides[1]->id;

        SlideAlignment::create([
            'lecture_id' => $mainLecture->id,
            'slide_id' => $slideId,
            'start_time_sec' => $item['start_sec'],
            'end_time_sec' => $item['end_sec'],
            'alignment_source' => 'manual_click',
            'alignment_confidence' => 1.0
        ]);
    }
    echo "[+] Seeded " . count($alignData) . " records into slide_alignments.\n";
}

// 4. Đọc eval_asr_wer.json và nạp vào transcript_segments & evidences
$asrJsonPath = 'c:/Users/Nguyen/AndroidStudioProjects/StudyAI/dataset_eval/eval_asr_wer.json';
if (file_exists($asrJsonPath)) {
    $asrData = json_decode(file_get_contents($asrJsonPath), true);
    TranscriptSegment::where('lecture_id', $mainLecture->id)->delete();

    $alignLookup = [];
    if (isset($alignData)) {
        foreach ($alignData as $a) {
            $alignLookup[$a['segment_id']] = $a['true_slide_number'];
        }
    }

    $seededEvidences = 0;
    foreach ($asrData as $idx => $sample) {
        $segId = $sample['segment_id'];
        $slideNum = $alignLookup[$segId] ?? min(18, $segId);
        $slideId = isset($slides[$slideNum]) ? $slides[$slideNum]->id : $slides[1]->id;
        $noveltyScore = ($segId % 3 == 0) ? 0.72 : (($segId % 3 == 1) ? 0.35 : 0.15);

        $segment = TranscriptSegment::create([
            'lecture_id' => $mainLecture->id,
            'aligned_slide_id' => $slideId,
            'start_time_sec' => $sample['start_sec'],
            'end_time_sec' => $sample['end_sec'],
            'text' => $sample['ground_truth_text'],
            'novelty_score' => $noveltyScore
        ]);

        // Tạo Evidence cho các câu có độ mới >= 0.28
        if ($noveltyScore >= 0.28) {
            Evidence::create([
                'source_type' => 'lecture_audio',
                'lecture_id' => $mainLecture->id,
                'slide_id' => $slideId,
                'exact_quote' => $sample['ground_truth_text'],
                'start_time_sec' => $sample['start_sec'],
                'end_time_sec' => $sample['end_sec']
            ]);
            $seededEvidences++;
        }
    }
    echo "[+] Seeded " . count($asrData) . " records into transcript_segments.\n";
    echo "[+] Seeded $seededEvidences grounded records into evidences table.\n";
}

echo "\n========================================================\n";
echo "  WEEK 3 MYSQL SEEDING COMPLETED PERFECTLY!             \n";
echo "========================================================\n";
