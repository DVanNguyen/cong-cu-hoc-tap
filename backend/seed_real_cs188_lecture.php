<?php
require 'vendor/autoload.php';
$app = require_once 'bootstrap/app.php';
$kernel = $app->make(Illuminate\Contracts\Console\Kernel::class);
$kernel->bootstrap();

use Illuminate\Support\Facades\DB;
use Illuminate\Support\Str;

echo "=== SEEDING REAL BERKELEY CS188 LECTURE (VIDEO & SLIDE) ===\n";

$courseId = 1;
$lectureId = 6;
$title = "Chương Thực nghiệm: UC Berkeley CS188 - Intro to AI & Rational Agents";

// 1. Cập nhật Lecture 6
DB::table('lectures')->where('id', $lectureId)->update([
    'title' => $title,
    'duration_sec' => 360,
    'status' => 'completed',
    'updated_at' => now(),
]);

// 2. Xóa dữ liệu cũ của Lecture 6
$oldSlides = DB::table('slides')->where('lecture_id', $lectureId)->pluck('id');
if ($oldSlides->isNotEmpty()) {
    $oldNotes = DB::table('lecture_notes')->whereIn('slide_id', $oldSlides)->pluck('id');
    $oldEvidenceIds = DB::table('lecture_notes')->whereIn('slide_id', $oldSlides)->pluck('evidence_id');
    DB::table('lecture_notes')->whereIn('id', $oldNotes)->delete();
    DB::table('evidences')->whereIn('id', $oldEvidenceIds)->delete();
    DB::table('slide_alignments')->where('lecture_id', $lectureId)->delete();
    DB::table('slides')->where('lecture_id', $lectureId)->delete();
}

// 3. Đọc dữ liệu đã trích xuất từ file JSON (cs188_real_data.json)
$jsonPath = 'c:/Users/Nguyen/AndroidStudioProjects/StudyAI/dataset_eval/cs188_real_data.json';
if (!file_exists($jsonPath)) {
    echo "[-] File not found: $jsonPath\n";
    exit(1);
}

$data = json_decode(file_get_contents($jsonPath), true);
$slidesData = $data['slides'] ?? [];
$notesData = $data['notes'] ?? [];

$slideIdMap = [];
foreach ($slidesData as $s) {
    $sid = DB::table('slides')->insertGetId([
        'lecture_id' => $lectureId,
        'slide_number' => $s['slide_number'],
        'page_number' => $s['slide_number'],
        'title' => $s['title'],
        'raw_text' => $s['extracted_text'],
        'extracted_terms' => json_encode($s['terms_json'], JSON_UNESCAPED_UNICODE),
        'created_at' => now(),
        'updated_at' => now(),
    ]);
    $slideIdMap[$s['slide_number']] = $sid;

    // Alignment cho slide này
    if (isset($s['start_sec']) && isset($s['end_sec'])) {
        DB::table('slide_alignments')->insert([
            'lecture_id' => $lectureId,
            'slide_id' => $sid,
            'start_time_sec' => $s['start_sec'],
            'end_time_sec' => $s['end_sec'],
            'alignment_confidence' => 0.95,
            'created_at' => now(),
            'updated_at' => now(),
        ]);
    }
}
echo "[+] Inserted " . count($slideIdMap) . " real slides from Berkeley CS188.\n";

// 4. Insert lecture notes & evidences
$insertedNotes = 0;
foreach ($notesData as $n) {
    $sNo = $n['slide_number'];
    if (!isset($slideIdMap[$sNo])) continue;
    $targetSlideId = $slideIdMap[$sNo];

    $evId = DB::table('evidences')->insertGetId([
        'source_type' => 'lecture_audio',
        'lecture_id'  => $lectureId,
        'slide_id'    => $targetSlideId,
        'exact_quote' => $n['verbatim_quote'],
        'start_time_sec' => $n['start_time_sec'],
        'end_time_sec' => $n['end_time_sec'],
        'created_at' => now(),
        'updated_at' => now(),
    ]);

    DB::table('lecture_notes')->insert([
        'slide_id' => $targetSlideId,
        'evidence_id' => $evId,
        'bullet_content' => $n['bullet_point'],
        'term_explanations' => json_encode(['AI', 'Agent', 'CS188']),
        'is_edited_by_user' => 0,
        'created_at' => now(),
        'updated_at' => now(),
    ]);
    $insertedNotes++;
}
echo "[+] Inserted $insertedNotes real oral expansion notes with verbatim evidence.\n";

// 5. Thêm liên kết chéo liên chương cho Lecture 6
$agentConcept = DB::table('concepts')->where('canonical_name', 'like', '%Intelligent Agent%')->orWhere('name', 'like', '%Agent%')->first();
if ($agentConcept && isset($slideIdMap[1])) {
    DB::table('concept_occurrences')->insert([
        'concept_id' => $agentConcept->id,
        'lecture_id' => $lectureId,
        'slide_id' => $slideIdMap[1],
        'occurrence_role' => 'REFERENCED_BACK',
        'pos_linear_index' => $lectureId * 1000 + 1,
        'context_excerpt' => 'CS 188 Berkeley Introduction to Artificial Intelligence and Rational Agents.',
        'timestamp_sec' => 2.6,
        'created_at' => now(),
    ]);
    echo "[+] Linked cross-chapter deep link for Intelligent Agent to Chapter 1.\n";
}

echo "=== SEEDING BERKELEY CS188 COMPLETED SUCCESSFULLY! ===\n";
