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

        try {
            $response = Http::timeout(1500)->post("{$aiBaseUrl}/pipeline/process-lecture", [
                'lecture_id' => $this->lectureId,
                'course_title' => $courseTitle,
                'slide_path' => $lecture->slide_file_path,
                'audio_path' => $lecture->audio_file_path,
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

                    // Lưu Slide Blocks để phục vụ Inline Viewer
                    $cleanedBullets = $s['cleaned_slide_bullets'] ?? [];
                    if (empty($cleanedBullets) && !empty($rawText)) {
                        $cleanedBullets = array_filter(array_map('trim', explode("\n", $rawText)));
                    }

                    $blockIndex = 1;
                    $slideBlocksMap[$slideNumber] = [];
                    foreach ($cleanedBullets as $bText) {
                        $blockId = "slide_{$slideNumber}_b{$blockIndex}";
                        DB::table('lecture_slide_blocks')->insert([
                            'lecture_id' => $this->lectureId,
                            'slide_page' => $slideNumber,
                            'block_index' => $blockIndex,
                            'block_id' => $blockId,
                            'kind' => ($blockIndex === 1 && strlen($bText) < 80) ? 'heading' : 'bullet',
                            'original_slide_text' => $bText,
                            'is_confused' => false,
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
                        $evidenceQuote = (string) ($n['evidence_quote'] ?? '');
                        $startSec = (float) ($n['start_time_sec'] ?? 0.0);
                        $endSec = (float) ($n['end_time_sec'] ?? 0.0);

                        $evidenceId = null;
                        if (!empty($evidenceQuote)) {
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
                        }

                        $bulletContent = !empty($headline) ? "{$headline}: {$content}" : $content;

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
                            $targetBlockId = $validBlocks[min(1, $totalBlocks - 1)]; // b2: block nội dung đầu tiên
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
