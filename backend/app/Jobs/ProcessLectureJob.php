<?php

namespace App\Jobs;

use Illuminate\Bus\Queueable;
use Illuminate\Contracts\Queue\ShouldQueue;
use Illuminate\Foundation\Bus\Dispatchable;
use Illuminate\Queue\InteractsWithQueue;
use Illuminate\Queue\SerializesModels;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Http;
use Illuminate\Support\Facades\Log;
use Throwable;

class ProcessLectureJob implements ShouldQueue
{
    use Dispatchable, InteractsWithQueue, Queueable, SerializesModels;

    public int $timeout = 1800; // 30 phút cho bài giảng dài
    public int $tries = 2;

    protected int $lectureId;
    protected ?array $slideClicks;

    public function __construct(int $lectureId, ?array $slideClicks = null)
    {
        $this->lectureId = $lectureId;
        $this->slideClicks = $slideClicks;
    }

    private function resolveLocalFilePath(?string $rawPath): string
    {
        $raw = trim((string) ($rawPath ?? ''));
        if ($raw === '') {
            return '';
        }
        $candidates = [
            $raw,
            storage_path('app/public/' . ltrim($raw, '/\\')),
            storage_path('app/private/' . ltrim($raw, '/\\')),
            storage_path('app/' . ltrim($raw, '/\\')),
        ];
        foreach ($candidates as $cand) {
            if (file_exists($cand) && is_file($cand)) {
                return $cand;
            }
        }
        return $raw;
    }

    private function extractSlideNumberFromBlockId(?string $blockId): int
    {
        if (!$blockId) {
            return 1;
        }
        if (preg_match('/^(?:slide_|s)(\d+)_b(\d+)$/i', $blockId, $m)) {
            return max(1, (int) $m[1]);
        }
        return 1;
    }

    public function handle(): void
    {
        $lecture = DB::table('lectures')->where('id', $this->lectureId)->first();
        if (!$lecture) {
            Log::error("ProcessLectureJob: Lecture #{$this->lectureId} not found");
            return;
        }

        $course = DB::table('courses')->where('id', $lecture->course_id)->first();
        $courseTitle = $course ? $course->name : 'Khóa học';

        $aiBaseUrl = env('AI_SERVICE_URL', 'http://127.0.0.1:8001');
        // Ensure /api/v1 prefix
        if (!str_contains($aiBaseUrl, '/api/v1')) {
            $aiBaseUrl = rtrim($aiBaseUrl, '/') . '/api/v1';
        }

        $webhookSecret = env('LARAVEL_WEBHOOK_SECRET', 'studyai_secret_token_2026');
        $appUrl = env('APP_URL', 'http://127.0.0.1:8000');
        $webhookUrl = rtrim($appUrl, '/') . "/api/v1/internal/lectures/{$this->lectureId}/progress";

        // Update progress to 10%
        DB::table('lectures')->where('id', $this->lectureId)->update([
            'processing_status' => 'calling_ai_worker',
            'progress_percent' => 10,
            'updated_at' => now(),
        ]);

        // Truy vấn mẫu giọng giảng viên đã đăng ký của môn học (nếu có)
        $teacherEmbedding = null;
        $voiceThreshold = 0.38;
        if ($lecture->course_id) {
            $voiceprint = DB::table('lecturer_voiceprints')
                ->where('course_id', $lecture->course_id)
                ->latest()
                ->first();
            if ($voiceprint && !empty($voiceprint->embedding_vector)) {
                $teacherEmbedding = is_string($voiceprint->embedding_vector)
                    ? json_decode($voiceprint->embedding_vector, true)
                    : $voiceprint->embedding_vector;
            }
        }

        $resolvedSlidePath = $this->resolveLocalFilePath($lecture->slide_file_path ?? '');
        $resolvedAudioPath = $this->resolveLocalFilePath($lecture->audio_file_path ?? '');

        try {
            $response = Http::timeout(1500)->post("{$aiBaseUrl}/pipeline/process-lecture", [
                'lecture_id' => $this->lectureId,
                'course_title' => $courseTitle,
                'slide_path' => $resolvedSlidePath,
                'audio_path' => $resolvedAudioPath,
                'language' => $lecture->language_mode ?? 'auto',
                'slide_clicks' => $this->slideClicks,
                'teacher_embedding' => $teacherEmbedding,
                'voice_threshold' => $voiceThreshold,
                'progress_webhook_url' => $webhookUrl,
                'webhook_secret' => $webhookSecret,
            ]);

            if (!$response->successful()) {
                throw new \Exception("AI service failed with status {$response->status()}: " . $response->body());
            }

            $result = $response->json();
            $slides = $result['slides'] ?? [];
            $transcriptSegments = $result['transcript_segments'] ?? [];

            DB::transaction(function () use ($lecture, $slides, $transcriptSegments) {
                // 0. Snapshot dữ liệu người dùng tự đánh dấu/chỉnh sửa trước khi dọn bản ghi Live/cũ (tránh trùng hoặc lỗi UNIQUE)
                $confusedByPageIdx = [];
                $confusedByPageText = [];
                $prevConfusedBlocks = DB::table('lecture_slide_blocks')
                    ->where('lecture_id', $this->lectureId)
                    ->where('is_confused', true)
                    ->get();
                foreach ($prevConfusedBlocks as $cb) {
                    $p = (int) $cb->slide_page;
                    $confusedByPageIdx["{$p}_" . ((int) $cb->block_index)] = true;
                    $normTxt = mb_strtolower(trim((string) $cb->original_slide_text));
                    if ($normTxt !== '') {
                        $confusedByPageText["{$p}_{$normTxt}"] = true;
                    }
                }

                // Giữ lại các ghi chú đã được sinh viên chỉnh sửa thủ công (từ lecture_notes hoặc inline_block_notes lúc Live)
                $preservedUserNotes = [];
                $seenTexts = [];
                $oldSlides = DB::table('slides')->where('lecture_id', $this->lectureId)->get();
                $oldSlideIdMap = $oldSlides->pluck('slide_number', 'id');
                $oldSlideIds = $oldSlides->pluck('id');

                if ($oldSlideIds->isNotEmpty()) {
                    $editedLecNotes = DB::table('lecture_notes as ln')
                        ->leftJoin('evidences as ev', 'ln.evidence_id', '=', 'ev.id')
                        ->whereIn('ln.slide_id', $oldSlideIds)
                        ->where('ln.is_edited_by_user', true)
                        ->select('ln.slide_id', 'ln.bullet_content', 'ln.term_explanations', 'ev.exact_quote', 'ev.start_time_sec', 'ev.end_time_sec')
                        ->get();
                    foreach ($editedLecNotes as $en) {
                        $txt = trim((string) $en->bullet_content);
                        if ($txt === '' || isset($seenTexts[$txt])) {
                            continue;
                        }
                        $seenTexts[$txt] = true;
                        $sNum = (int) ($oldSlideIdMap[$en->slide_id] ?? 1);
                        $preservedUserNotes[] = [
                            'slide_number' => $sNum,
                            'block_id' => null,
                            'bullet_content' => $txt,
                            'exact_quote' => (string) ($en->exact_quote ?: $txt),
                            'start_time_sec' => (float) ($en->start_time_sec ?? 0.0),
                            'end_time_sec' => (float) ($en->end_time_sec ?? 0.0),
                            'term_explanations' => $en->term_explanations ?: json_encode(['category' => 'user_edited']),
                        ];
                    }
                }

                $editedInlineNotes = DB::table('inline_block_notes')
                    ->where('lecture_id', $this->lectureId)
                    ->whereColumn('updated_at', '>', 'created_at')
                    ->get();
                foreach ($editedInlineNotes as $ein) {
                    $txt = trim((string) $ein->explanation_text);
                    if ($txt === '' || isset($seenTexts[$txt])) {
                        continue;
                    }
                    $seenTexts[$txt] = true;
                    $preservedUserNotes[] = [
                        'slide_number' => $this->extractSlideNumberFromBlockId($ein->block_id),
                        'block_id' => $ein->block_id,
                        'bullet_content' => $txt,
                        'exact_quote' => (string) ($ein->raw_transcript ?: $txt),
                        'start_time_sec' => (float) ($ein->timestamp_start_sec ?? 0.0),
                        'end_time_sec' => (float) ($ein->timestamp_end_sec ?? 0.0),
                        'term_explanations' => json_encode(['category' => 'user_edited']),
                    ];
                }

                // Dọn sạch toàn bộ bảng con cũ của bài giảng theo đúng thứ tự khóa ngoại
                DB::table('block_concept_highlights')->where('lecture_id', $this->lectureId)->delete();
                DB::table('inline_block_notes')->where('lecture_id', $this->lectureId)->delete();
                DB::table('lecture_slide_blocks')->where('lecture_id', $this->lectureId)->delete();
                DB::table('transcript_segments')->where('lecture_id', $this->lectureId)->delete();
                DB::table('slide_alignments')->where('lecture_id', $this->lectureId)->delete();
                DB::table('concept_occurrences')->where('lecture_id', $this->lectureId)->delete();
                if ($oldSlideIds->isNotEmpty()) {
                    DB::table('lecture_notes')->whereIn('slide_id', $oldSlideIds)->delete();
                }
                DB::table('evidences')->where('lecture_id', $this->lectureId)->delete();
                DB::table('slides')->where('lecture_id', $this->lectureId)->delete();

                // 1. Lưu từng slide và ghi chú tương ứng trước để lấy slide_id thật
                $slideNumberToId = [];
                $slideBlocksMap = []; // slideNumber => [blockId1, blockId2, ...]

                foreach ($slides as $idx => $s) {
                    $slideNumber = (int) ($s['slide_number'] ?? ($idx + 1));
                    $slideTitle = (string) ($s['title'] ?? "Slide {$slideNumber}");
                    $rawText = (string) ($s['extracted_text'] ?? $s['raw_text'] ?? '');
                    $terms = $s['terms'] ?? $s['extracted_terms'] ?? [];
                    $termsJson = is_array($terms) ? json_encode($terms, JSON_UNESCAPED_UNICODE) : (string) $terms;

                    $slideId = DB::table('slides')->insertGetId([
                        'lecture_id' => $this->lectureId,
                        'slide_number' => $slideNumber,
                        'page_number' => $slideNumber,
                        'title' => $slideTitle,
                        'raw_text' => $rawText,
                        'slide_text' => $rawText,
                        'extracted_terms' => $termsJson,
                        'summary' => (string) ($s['summary'] ?? ''),
                        'created_at' => now(),
                        'updated_at' => now(),
                    ]);
                    $slideNumberToId[$slideNumber] = $slideId;

                    // Lưu Slide Blocks để phục vụ Inline Viewer (khôi phục cờ is_confused nếu sinh viên đã đánh dấu lúc Live)
                    $cleanedBullets = $s['cleaned_slide_bullets'] ?? [];
                    if (empty($cleanedBullets) && !empty($rawText)) {
                        $cleanedBullets = array_filter(array_map('trim', explode("\n", $rawText)));
                    }

                    $blockIndex = 1;
                    $slideBlocksMap[$slideNumber] = [];
                    foreach ($cleanedBullets as $bText) {
                        $blockId = "slide_{$slideNumber}_b{$blockIndex}";
                        $normBText = mb_strtolower(trim((string) $bText));
                        $wasConfused = isset($confusedByPageText["{$slideNumber}_{$normBText}"])
                            || isset($confusedByPageIdx["{$slideNumber}_{$blockIndex}"]);

                        DB::table('lecture_slide_blocks')->insert([
                            'lecture_id' => $this->lectureId,
                            'slide_page' => $slideNumber,
                            'block_index' => $blockIndex,
                            'block_id' => $blockId,
                            'kind' => ($blockIndex === 1 && strlen($bText) < 80) ? 'heading' : 'bullet',
                            'original_slide_text' => $bText,
                            'is_confused' => $wasConfused,
                            'last_spoken_sec' => null,
                            'created_at' => now(),
                            'updated_at' => now(),
                        ]);
                        $slideBlocksMap[$slideNumber][] = $blockId;
                        $blockIndex++;
                    }

                    // Lưu Notes & Evidences
                    $notes = $s['notes'] ?? [];
                    $totalBlocks = count($slideBlocksMap[$slideNumber]);
                    foreach ($notes as $nIdx => $n) {
                        $headline = (string) ($n['headline'] ?? '');
                        $content = (string) ($n['content'] ?? '');
                        $bulletContent = !empty($headline) ? "{$headline}: {$content}" : $content;
                        $evidenceQuote = (string) ($n['evidence_quote'] ?? '');
                        if ($evidenceQuote === '') {
                            $evidenceQuote = $bulletContent;
                        }
                        $startSec = (float) ($n['start_time_sec'] ?? 0.0);
                        $endSec = (float) ($n['end_time_sec'] ?? 0.0);

                        $evidenceId = DB::table('evidences')->insertGetId([
                            'source_type' => 'lecture_audio',
                            'lecture_id' => $this->lectureId,
                            'slide_id' => $slideId,
                            'raw_message_id' => null,
                            'exact_quote' => $evidenceQuote,
                            'start_time_sec' => $startSec,
                            'end_time_sec' => $endSec,
                            'created_at' => now(),
                            'updated_at' => now(),
                        ]);

                        DB::table('lecture_notes')->insert([
                            'slide_id' => $slideId,
                            'evidence_id' => $evidenceId,
                            'bullet_content' => $bulletContent,
                            'term_explanations' => json_encode(['category' => $n['category'] ?? 'core_explanation']),
                            'is_edited_by_user' => false,
                            'created_at' => now(),
                            'updated_at' => now(),
                        ]);

                        // Gắn đúng block theo target_block_id hợp lệ; fallback b1 chỉ khi slide có 1 block
                        $validBlocks = $slideBlocksMap[$slideNumber] ?? [];
                        $targetBlockId = "slide_{$slideNumber}_b1";
                        $llmTarget = $n['target_block_id'] ?? null;
                        if ($llmTarget && in_array($llmTarget, $validBlocks, true)) {
                            $targetBlockId = $llmTarget;
                        } elseif ($totalBlocks > 1) {
                            $targetBlockId = $validBlocks[min(1, $totalBlocks - 1)];
                        } elseif ($totalBlocks === 1) {
                            $targetBlockId = $validBlocks[0];
                        }

                        DB::table('inline_block_notes')->insert([
                            'lecture_id' => $this->lectureId,
                            'block_id' => $targetBlockId,
                            'explanation_text' => $bulletContent,
                            'raw_transcript' => $evidenceQuote,
                            'timestamp_start_sec' => $startSec,
                            'timestamp_end_sec' => $endSec,
                            'novelty_score' => 0.85,
                            'jumped_non_linear' => false,
                            'created_at' => now(),
                            'updated_at' => now(),
                        ]);
                    }
                }

                // 1B. Khôi phục các ghi chú đã được sinh viên chỉnh sửa thủ công (is_edited_by_user = true)
                foreach ($preservedUserNotes as $un) {
                    $sNum = (int) $un['slide_number'];
                    $slideId = $slideNumberToId[$sNum] ?? reset($slideNumberToId) ?: null;
                    if (!$slideId) {
                        continue;
                    }
                    $validBlocks = $slideBlocksMap[$sNum] ?? [];
                    $targetBlockId = "slide_{$sNum}_b1";
                    if (!empty($un['block_id']) && in_array($un['block_id'], $validBlocks, true)) {
                        $targetBlockId = $un['block_id'];
                    } elseif (count($validBlocks) > 1) {
                        $targetBlockId = $validBlocks[1];
                    } elseif (count($validBlocks) === 1) {
                        $targetBlockId = $validBlocks[0];
                    }

                    $evId = DB::table('evidences')->insertGetId([
                        'source_type' => 'lecture_audio',
                        'lecture_id' => $this->lectureId,
                        'slide_id' => $slideId,
                        'raw_message_id' => null,
                        'exact_quote' => $un['exact_quote'],
                        'start_time_sec' => $un['start_time_sec'],
                        'end_time_sec' => $un['end_time_sec'],
                        'created_at' => now(),
                        'updated_at' => now(),
                    ]);

                    DB::table('lecture_notes')->insert([
                        'slide_id' => $slideId,
                        'evidence_id' => $evId,
                        'bullet_content' => $un['bullet_content'],
                        'term_explanations' => $un['term_explanations'],
                        'is_edited_by_user' => true,
                        'created_at' => now()->subSeconds(5),
                        'updated_at' => now(),
                    ]);

                    DB::table('inline_block_notes')->insert([
                        'lecture_id' => $this->lectureId,
                        'block_id' => $targetBlockId,
                        'explanation_text' => $un['bullet_content'],
                        'raw_transcript' => $un['exact_quote'],
                        'timestamp_start_sec' => $un['start_time_sec'],
                        'timestamp_end_sec' => $un['end_time_sec'],
                        'novelty_score' => 0.95,
                        'jumped_non_linear' => false,
                        'created_at' => now()->subSeconds(5),
                        'updated_at' => now(),
                    ]);
                }

                // 2. Lưu transcript segments với aligned_slide_id thật
                foreach ($transcriptSegments as $seg) {
                    $alignedNum = (int) ($seg['aligned_slide_number'] ?? $seg['aligned_slide_id'] ?? 1);
                    $alignedSlideId = $slideNumberToId[$alignedNum] ?? null;

                    DB::table('transcript_segments')->insert([
                        'lecture_id' => $this->lectureId,
                        'aligned_slide_id' => $alignedSlideId,
                        'start_time_sec' => (float) ($seg['start_time_sec'] ?? $seg['start_sec'] ?? 0.0),
                        'end_time_sec' => (float) ($seg['end_time_sec'] ?? $seg['end_sec'] ?? 0.0),
                        'text' => (string) ($seg['text'] ?? ''),
                        'novelty_score' => (float) ($seg['novelty_score'] ?? 0.5),
                        'created_at' => now(),
                        'updated_at' => now(),
                    ]);
                }

                // Cập nhật trạng thái hoàn thành
                DB::table('lectures')->where('id', $this->lectureId)->update([
                    'processing_status' => 'completed',
                    'progress_percent' => 100,
                    'status' => 'completed',
                    'error_message' => null,
                    'updated_at' => now(),
                ]);
            });

            if ($lecture->course_id) {
                try {
                    app(\App\Http\Controllers\Api\StudyAiUnifiedController::class)->syncLectureToMindmap(
                        (int) $lecture->course_id,
                        $this->lectureId,
                        true
                    );
                } catch (Throwable $mindmapEx) {
                    Log::warning("ProcessLectureJob: Mindmap sync skipped for Lecture #{$this->lectureId}: " . $mindmapEx->getMessage());
                }
            }

            Log::info("ProcessLectureJob: Successfully processed Lecture #{$this->lectureId}");
        } catch (Throwable $e) {
            Log::error("ProcessLectureJob failed for Lecture #{$this->lectureId}: " . $e->getMessage());

            DB::table('lectures')->where('id', $this->lectureId)->update([
                'processing_status' => 'failed',
                'status' => 'failed',
                'error_message' => $e->getMessage(),
                'updated_at' => now(),
            ]);

            throw $e;
        }
    }

    public function failed(Throwable $exception): void
    {
        Log::error("ProcessLectureJob completely failed for Lecture #{$this->lectureId}: " . $exception->getMessage());
        DB::table('lectures')->where('id', $this->lectureId)->update([
            'processing_status' => 'failed',
            'status' => 'failed',
            'error_message' => $exception->getMessage(),
            'updated_at' => now(),
        ]);
    }
}
