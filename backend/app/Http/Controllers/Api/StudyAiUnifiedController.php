<?php

namespace App\Http\Controllers\Api;

use App\Http\Controllers\Controller;
use App\Jobs\ProcessLectureJob;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Http;
use Illuminate\Support\Facades\Log;
use Illuminate\Support\Facades\Schema;

class StudyAiUnifiedController extends Controller
{
    /**
     * =========================================================================
     * 1. CRUD MÔN HỌC (COURSES)
     * =========================================================================
     */
    public function getCourses()
    {
        $courses = DB::table('courses')
            ->select('id', 'name', 'code', 'lecturer', 'created_at')
            ->orderBy('id', 'asc')
            ->get();

        $slideCounts = DB::table('course_slide_vaults')
            ->select('course_id', DB::raw('count(*) as count'))
            ->groupBy('course_id')
            ->pluck('count', 'course_id');

        $lectureCounts = DB::table('lectures')
            ->select('course_id', DB::raw('count(*) as count'))
            ->groupBy('course_id')
            ->pluck('count', 'course_id');

        $data = $courses->map(function ($c) use ($slideCounts, $lectureCounts) {
            return [
                'id' => (int) $c->id,
                'name' => (string) $c->name,
                'code' => (string) ($c->code ?? ''),
                'lecturer' => (string) ($c->lecturer ?? ''),
                'slide_count' => (int) ($slideCounts[$c->id] ?? 0),
                'lecture_count' => (int) ($lectureCounts[$c->id] ?? 0),
                'created_at' => (string) $c->created_at,
            ];
        });

        return response()->json([
            'courses' => $data,
        ]);
    }

    public function createCourse(Request $request)
    {
        $validated = $request->validate([
            'name' => 'required|string|max:255',
            'code' => 'nullable|string|max:50',
            'lecturer' => 'nullable|string|max:255',
        ]);

        $firstUser = DB::table('users')->first();
        $userId = $firstUser ? $firstUser->id : null;

        $courseId = DB::table('courses')->insertGetId([
            'name' => $validated['name'],
            'code' => $validated['code'] ?? '',
            'lecturer' => $validated['lecturer'] ?? '',
            'user_id' => $userId,
            'created_at' => now(),
            'updated_at' => now(),
        ]);

        return response()->json([
            'id' => $courseId,
            'name' => $validated['name'],
            'code' => $validated['code'] ?? '',
            'lecturer' => $validated['lecturer'] ?? '',
            'slide_count' => 0,
            'lecture_count' => 0,
        ], 201);
    }

    public function updateCourse(Request $request, int $courseId)
    {
        $course = DB::table('courses')->where('id', $courseId)->first();
        if (!$course) {
            return response()->json(['error' => 'Course not found'], 404);
        }

        $updates = [];
        if ($request->has('name')) $updates['name'] = $request->input('name');
        if ($request->has('code')) $updates['code'] = $request->input('code');
        if ($request->has('lecturer')) $updates['lecturer'] = $request->input('lecturer');
        $updates['updated_at'] = now();

        DB::table('courses')->where('id', $courseId)->update($updates);

        $updated = DB::table('courses')->where('id', $courseId)->first();
        return response()->json([
            'id' => (int) $updated->id,
            'name' => (string) $updated->name,
            'code' => (string) ($updated->code ?? ''),
            'lecturer' => (string) ($updated->lecturer ?? ''),
        ]);
    }

    public function deleteCourse(int $courseId)
    {
        $course = DB::table('courses')->where('id', $courseId)->first();
        if (!$course) {
            return response()->json(['error' => 'Course not found'], 404);
        }

        DB::table('courses')->where('id', $courseId)->delete();
        return response()->json(['success' => true, 'message' => 'Course deleted']);
    }

    /**
     * =========================================================================
     * 2. KHO LƯU TRỮ SLIDE THEO MÔN (SLIDE VAULT)
     * =========================================================================
     */
    public function getSlideVault(int $courseId)
    {
        $vaults = DB::table('course_slide_vaults')
            ->where('course_id', $courseId)
            ->orderByDesc('id')
            ->get();

        $items = $vaults->map(function ($v) {
            return [
                'id' => (string) $v->id,
                'fileName' => (string) $v->file_name,
                'pageCount' => (int) $v->page_count,
                'file_path' => (string) $v->file_path,
                'created_at' => (string) $v->created_at,
            ];
        });

        return response()->json([
            'course_id' => $courseId,
            'slide_vault' => $items,
        ]);
    }

    public function uploadSlideToVault(Request $request, int $courseId)
    {
        $course = DB::table('courses')->where('id', $courseId)->first();
        if (!$course) {
            return response()->json(['error' => 'Course not found'], 404);
        }

        $request->validate([
            'slide_file' => 'required|file',
        ]);

        $file = $request->file('slide_file');
        $origName = $file->getClientOriginalName();
        $mime = $file->getClientMimeType() ?: 'application/pdf';

        $storedPath = $file->store('slides', 'public');
        $aiUrl = env('AI_SERVICE_URL', 'http://127.0.0.1:8001');
        $pageCount = 1;
        $parsedBlocks = [];

        try {
            $response = Http::timeout(60)
                ->attach('slide_file', file_get_contents($file->getRealPath()), $origName)
                ->post("{$aiUrl}/ai/parse-slide-blocks");

            if ($response->successful()) {
                $data = $response->json();
                $pageCount = $data['page_count'] ?? 1;
                $parsedBlocks = $data['blocks'] ?? [];
            }
        } catch (\Exception $e) {
            Log::warning('AI parse-slide-blocks failed, fallback: ' . $e->getMessage());
        }

        $vaultId = DB::table('course_slide_vaults')->insertGetId([
            'course_id' => $courseId,
            'file_name' => $origName,
            'file_path' => $storedPath,
            'mime_type' => $mime,
            'page_count' => $pageCount,
            'parsed_blocks_json' => json_encode($parsedBlocks),
            'created_at' => now(),
            'updated_at' => now(),
        ]);

        try {
            $cleanBranchTitle = preg_replace('/\.(pdf|pptx?|PDF|PPTX?)$/u', '', (string) $origName) ?: (string) $origName;
            $this->upsertMindmapBranchFromBlocks(
                $courseId,
                null,
                $vaultId,
                $cleanBranchTitle,
                is_array($parsedBlocks) ? $parsedBlocks : [],
                true
            );
        } catch (\Throwable $e) {
            Log::warning("Mindmap auto-append failed for vault #{$vaultId}: " . $e->getMessage());
        }

        return response()->json([
            'id' => (string) $vaultId,
            'course_id' => $courseId,
            'fileName' => $origName,
            'pageCount' => $pageCount,
            'file_path' => $storedPath,
            'blocks' => $parsedBlocks,
        ], 201);
    }

    /**
     * =========================================================================
     * 3. BÀI GIẢNG BÁM SLIDE & VỞ GHI CHÉP INLINE
     * =========================================================================
     */
    public function startLiveLecture(Request $request)
    {
        $validated = $request->validate([
            'course_id' => 'required|integer',
            'vault_slide_id' => 'required|integer',
            'title' => 'nullable|string',
            'language' => 'nullable|string',
        ]);

        $vault = DB::table('course_slide_vaults')->where('id', $validated['vault_slide_id'])->first();
        if (!$vault) {
            return response()->json(['error' => 'Vault slide not found'], 404);
        }

        $title = $validated['title'] ?: $vault->file_name;
        $lectureId = DB::table('lectures')->insertGetId([
            'course_id' => $validated['course_id'],
            'title' => $title,
            'slide_file_path' => $vault->file_path,
            'status' => 'processing',
            'created_at' => now(),
            'updated_at' => now(),
        ]);

        $parsedBlocks = json_decode($vault->parsed_blocks_json ?? '[]', true) ?: [];
        $pages = [];

        foreach ($parsedBlocks as $b) {
            DB::table('lecture_slide_blocks')->insert([
                'lecture_id' => $lectureId,
                'slide_page' => $b['slide_page'] ?? 1,
                'block_index' => $b['block_index'] ?? 1,
                'block_id' => $b['block_id'],
                'kind' => $b['kind'] ?? 'bullet',
                'original_slide_text' => $b['text'] ?? '',
                'is_confused' => false,
                'last_spoken_sec' => null,
                'created_at' => now(),
                'updated_at' => now(),
            ]);
        }

        $byPage = collect($parsedBlocks)->groupBy('slide_page');
        foreach ($byPage as $pageNum => $bList) {
            $firstBlock = $bList->first();
            $pageTitle = ($firstBlock && ($firstBlock['kind'] ?? '') === 'heading')
                ? $firstBlock['text']
                : "Trang {$pageNum}";

            $pages[] = [
                'pageNumber' => (int) $pageNum,
                'slideTitle' => $pageTitle,
                'blocks' => $bList->map(function ($b) {
                    return [
                        'blockId' => $b['block_id'],
                        'kind' => $b['kind'] ?? 'bullet',
                        'originalSlideText' => $b['text'] ?? '',
                        'isCurrentlySpoken' => false,
                        'isMarkedConfused' => false,
                        'inlineNotes' => [],
                        'highlights' => [],
                    ];
                })->values(),
            ];
        }

        return response()->json([
            'lecture_id' => $lectureId,
            'title' => $title,
            'pages' => $pages,
        ]);
    }

    public function processLiveChunk(Request $request, int $lectureId)
    {
        $lecture = DB::table('lectures')->where('id', $lectureId)->first();
        if (!$lecture) {
            return response()->json(['error' => 'Lecture not found'], 404);
        }

        $request->validate([
            'audio_chunk' => 'required|file',
            'chunk_start_sec' => 'nullable|numeric',
            'language' => 'nullable|string',
        ]);

        $file = $request->file('audio_chunk');
        $startSec = (float) $request->input('chunk_start_sec', 0.0);
        $language = $request->input('language', 'auto');

        // 1. Lưu chunk tạm vào storage/app/private/lectures/{id}/chunks để ghép thành audio hoàn chỉnh khi kết thúc Live
        $chunksDir = storage_path("app/private/lectures/{$lectureId}/chunks");
        if (!is_dir($chunksDir)) {
            @mkdir($chunksDir, 0775, true);
        }
        $ext = strtolower($file->getClientOriginalExtension() ?: 'm4a');
        $existingChunks = glob($chunksDir . DIRECTORY_SEPARATOR . 'chunk_*') ?: [];
        $seqIdx = count($existingChunks) + 1;
        $timeTag = str_pad((string) max(0, (int) round($startSec * 10)), 7, '0', STR_PAD_LEFT);
        $seqTag = str_pad((string) $seqIdx, 4, '0', STR_PAD_LEFT);
        $savedChunkPath = $chunksDir . DIRECTORY_SEPARATOR . "chunk_{$timeTag}_{$seqTag}.{$ext}";
        $chunkBytes = file_get_contents($file->getRealPath());
        @file_put_contents($savedChunkPath, $chunkBytes);

        // 2. Lấy trang slide đang giảng gần nhất (bỏ số 1 cứng)
        $lastSpokenBlock = DB::table('lecture_slide_blocks')
            ->where('lecture_id', $lectureId)
            ->whereNotNull('last_spoken_sec')
            ->orderByDesc('last_spoken_sec')
            ->orderByDesc('updated_at')
            ->first();
        $currentSlidePage = $lastSpokenBlock ? (int) $lastSpokenBlock->slide_page : 1;

        // 3. Lấy mẫu giọng giảng viên của môn học (nếu đã đăng ký ở Tuần 2)
        $vp = DB::table('lecturer_voiceprints')
            ->where('course_id', $lecture->course_id)
            ->latest()
            ->first();
        $teacherEmbJson = $vp ? (string) $vp->embedding_vector : '';

        $blocks = DB::table('lecture_slide_blocks')
            ->where('lecture_id', $lectureId)
            ->orderBy('slide_page')
            ->orderBy('block_index')
            ->get();

        $blocksPayload = $blocks->map(function ($b) {
            return [
                'block_id' => $b->block_id,
                'slide_page' => (int) $b->slide_page,
                'block_index' => (int) $b->block_index,
                'kind' => $b->kind,
                'text' => $b->original_slide_text,
            ];
        })->values()->toArray();

        $prevConcepts = DB::table('concepts as c')
            ->join('concept_occurrences as co', 'c.id', '=', 'co.concept_id')
            ->join('lectures as l', 'co.lecture_id', '=', 'l.id')
            ->where('l.course_id', $lecture->course_id)
            ->where('l.id', '<', $lectureId)
            ->select('c.name', 'c.canonical_name', 'co.slide_id', 'l.title as lecture_title')
            ->limit(30)
            ->get();

        $aiUrl = env('AI_SERVICE_URL', 'http://127.0.0.1:8001');

        try {
            $response = Http::timeout(45)
                ->attach('audio_chunk', $chunkBytes, $file->getClientOriginalName() ?: "chunk.{$ext}")
                ->post("{$aiUrl}/ai/live-align-chunk", [
                    'blocks_json' => json_encode($blocksPayload),
                    'current_slide_page' => $currentSlidePage,
                    'language' => $language,
                    'course_id' => $lecture->course_id,
                    'chunk_start_sec' => $startSec,
                    'previous_concepts_json' => json_encode($prevConcepts),
                    'teacher_embedding_json' => $teacherEmbJson,
                ]);

            if ($response->successful()) {
                $data = $response->json();

                // Nếu là tiếng sinh viên / nhiễu không phải giảng viên -> bỏ qua không lưu note & không đổi trang
                if (($data['action'] ?? '') === 'ignored_non_teacher') {
                    Log::info("Live chunk ignored (non-teacher) for lecture #{$lectureId} at {$startSec}s");
                    return response()->json($data);
                }

                if (!empty($data['inline_note']) && in_array($data['action'], ['attach_inline_note', 'general_note'])) {
                    $n = $data['inline_note'];
                    $insertedNoteId = DB::table('inline_block_notes')->insertGetId([
                        'lecture_id' => $lectureId,
                        'block_id' => $data['matched_block_id'],
                        'explanation_text' => $n['explanation_text'] ?? '',
                        'raw_transcript' => $n['raw_transcript'] ?? '',
                        'timestamp_start_sec' => $n['timestamp_start_sec'] ?? $startSec,
                        'timestamp_end_sec' => $n['timestamp_end_sec'] ?? ($startSec + 8.0),
                        'novelty_score' => $n['novelty_score'] ?? 0.8,
                        'jumped_non_linear' => $data['jumped_non_linear'] ?? false,
                        'created_at' => now(),
                        'updated_at' => now(),
                    ]);
                    $data['inline_note']['id'] = (string) $insertedNoteId;
                }

                if (!empty($data['new_highlights']) && !empty($data['matched_block_id'])) {
                    foreach ($data['new_highlights'] as $h) {
                        DB::table('block_concept_highlights')->insert([
                            'lecture_id' => $lectureId,
                            'block_id' => $data['matched_block_id'],
                            'term' => $h['term'],
                            'source_type' => $h['source_type'] ?? 'academic_internet',
                            'source_label' => $h['source_label'] ?? '',
                            'explanation' => $h['explanation'] ?? '',
                            'course_context_id' => $h['course_context_id'] ?? null,
                            'alternative_contexts_json' => json_encode($h['alternative_contexts'] ?? []),
                            'created_at' => now(),
                            'updated_at' => now(),
                        ]);
                    }
                }

                if (!empty($data['matched_block_id'])) {
                    DB::table('lecture_slide_blocks')
                        ->where('lecture_id', $lectureId)
                        ->where('block_id', $data['matched_block_id'])
                        ->update([
                            'last_spoken_sec' => max($startSec, 0.01),
                            'updated_at' => now(),
                        ]);
                }

                return response()->json($data);
            }
        } catch (\Exception $e) {
            Log::warning('AI live-align-chunk error: ' . $e->getMessage());
        }

        return response()->json([
            'matched_slide_page' => $currentSlidePage,
            'matched_block_id' => null,
            'action' => 'highlight_only',
            'inline_note' => null,
            'new_highlights' => [],
            'jumped_non_linear' => false,
        ]);
    }

    private function resolveFfmpegBinary(): string
    {
        $fromEnv = env('FFMPEG_BINARY', '');
        if ($fromEnv && file_exists($fromEnv)) {
            return $fromEnv;
        }
        $probe = PHP_OS_FAMILY === 'Windows' ? 'where ffmpeg 2>NUL' : 'which ffmpeg 2>/dev/null';
        $found = trim((string) @shell_exec($probe));
        if ($found !== '') {
            $firstLine = trim(explode("\n", $found)[0]);
            if (file_exists($firstLine)) {
                return $firstLine;
            }
        }
        // Fallback sang binary của imageio_ffmpeg trong Python environment
        $pyOut = trim((string) @shell_exec('python -c "import imageio_ffmpeg; print(imageio_ffmpeg.get_ffmpeg_exe())" 2>NUL'));
        if ($pyOut !== '' && file_exists($pyOut)) {
            return $pyOut;
        }
        return 'ffmpeg';
    }

    public function finishLiveLecture(Request $request, int $lectureId)
    {
        $lecture = DB::table('lectures')->where('id', $lectureId)->first();
        if (!$lecture) {
            return response()->json(['message' => 'Không tìm thấy bài giảng.'], 404);
        }

        $refineFull = $request->boolean('refine_full_lecture', false);
        $lectureDir = storage_path("app/private/lectures/{$lectureId}");
        $chunksDir = $lectureDir . DIRECTORY_SEPARATOR . 'chunks';
        $finalAudioPath = $lectureDir . DIRECTORY_SEPARATOR . 'live_recording.m4a';

        $chunks = is_dir($chunksDir)
            ? (glob($chunksDir . DIRECTORY_SEPARATOR . 'chunk_*') ?: [])
            : [];
        sort($chunks, SORT_STRING);

        $merged = false;
        if (count($chunks) === 1 && strtolower(pathinfo($chunks[0], PATHINFO_EXTENSION)) === 'm4a') {
            $merged = @copy($chunks[0], $finalAudioPath);
        } elseif (count($chunks) >= 1) {
            if (!is_dir($lectureDir)) {
                @mkdir($lectureDir, 0775, true);
            }
            $listFile = $lectureDir . DIRECTORY_SEPARATOR . 'concat_list.txt';
            $listLines = [];
            foreach ($chunks as $cPath) {
                $escaped = str_replace("'", "'\\''", str_replace('\\', '/', $cPath));
                $listLines[] = "file '{$escaped}'";
            }
            file_put_contents($listFile, implode("\n", $listLines));

            $ffmpeg = $this->resolveFfmpegBinary();
            $cmdCopy = sprintf(
                '"%s" -y -f concat -safe 0 -i "%s" -c copy "%s" 2>&1',
                $ffmpeg,
                $listFile,
                $finalAudioPath
            );
            $out = [];
            $ret = 1;
            @exec($cmdCopy, $out, $ret);

            // Fallback re-encode AAC nếu các chunk khác container/codec (VD: .wav) hoặc -c copy lỗi
            if ($ret !== 0 || !file_exists($finalAudioPath) || filesize($finalAudioPath) < 256) {
                $cmdReencode = sprintf(
                    '"%s" -y -f concat -safe 0 -i "%s" -c:a aac -b:a 96k -ar 16000 -ac 1 "%s" 2>&1',
                    $ffmpeg,
                    $listFile,
                    $finalAudioPath
                );
                $out2 = [];
                $ret2 = 1;
                @exec($cmdReencode, $out2, $ret2);
                $merged = ($ret2 === 0 && file_exists($finalAudioPath) && filesize($finalAudioPath) >= 256);
                if (!$merged) {
                    Log::error("ffmpeg finish-live failed for lecture #{$lectureId}: " . implode("\n", $out2));
                }
            } else {
                $merged = true;
            }
            @unlink($listFile);
        }

        $maxSec = DB::table('inline_block_notes')
            ->where('lecture_id', $lectureId)
            ->max('timestamp_end_sec');
        $estimatedDuration = max((float) ($maxSec ?? 0.0), count($chunks) * 8.0);

        $updateData = [
            'duration_sec' => (int) round($estimatedDuration),
            'updated_at' => now(),
        ];
        if ($merged && file_exists($finalAudioPath)) {
            $updateData['audio_file_path'] = $finalAudioPath;
        }

        $refinedQueued = false;
        if ($refineFull && $merged && !empty($lecture->slide_file_path)) {
            $updateData['status'] = 'processing';
            $updateData['processing_status'] = 'queued';
            $updateData['progress_percent'] = 5;
            DB::table('lectures')->where('id', $lectureId)->update($updateData);
            ProcessLectureJob::dispatch($lectureId, []);
            $refinedQueued = true;
        } else {
            $updateData['status'] = 'completed';
            $updateData['processing_status'] = 'completed';
            $updateData['progress_percent'] = 100;
            DB::table('lectures')->where('id', $lectureId)->update($updateData);
        }

        return response()->json([
            'lecture_id' => $lectureId,
            'status' => $refinedQueued ? 'queued_for_refinement' : 'completed',
            'audio_ready' => $merged,
            'audio_file_path' => $merged ? $finalAudioPath : null,
            'audio_url' => $merged ? "/api/v1/lectures/{$lectureId}/audio" : null,
            'chunks_merged' => count($chunks),
            'duration_sec' => (int) round($estimatedDuration),
            'refined_queued' => $refinedQueued,
        ]);
    }

    public function toggleConfusedBlock(Request $request, int $lectureId, string $blockId)
    {
        $block = DB::table('lecture_slide_blocks')
            ->where('lecture_id', $lectureId)
            ->where('block_id', $blockId)
            ->first();

        if (!$block) {
            return response()->json(['error' => 'Block not found'], 404);
        }

        $newConfused = !$block->is_confused;
        DB::table('lecture_slide_blocks')
            ->where('lecture_id', $lectureId)
            ->where('block_id', $blockId)
            ->update(['is_confused' => $newConfused, 'updated_at' => now()]);

        return response()->json([
            'block_id' => $blockId,
            'is_confused' => (bool) $newConfused,
        ]);
    }

    public function uploadLecturePair(Request $request)
    {
        $request->validate([
            'course_id' => 'required|integer',
            'title' => 'nullable|string',
            'language' => 'nullable|string',
            'slide_file' => 'required|file',
            'audio_file' => 'required|file',
        ]);

        $courseId = (int) $request->input('course_id');
        $title = $request->input('title') ?: 'Bài giảng mới';
        $language = $request->input('language', 'auto');

        $slideFile = $request->file('slide_file');
        $audioFile = $request->file('audio_file');

        $slidePath = $slideFile->store('slides', 'public');
        $audioPath = $audioFile->store('audios', 'public');

        $lectureId = DB::table('lectures')->insertGetId([
            'course_id' => $courseId,
            'title' => $title,
            'slide_file_path' => $slidePath,
            'audio_file_path' => $audioPath,
            'status' => 'processing',
            'created_at' => now(),
            'updated_at' => now(),
        ]);

        $aiUrl = env('AI_SERVICE_URL', 'http://127.0.0.1:8001');

        try {
            $response = Http::timeout(600)
                ->attach('slide_file', file_get_contents($slideFile->getRealPath()), $slideFile->getClientOriginalName())
                ->attach('audio_file', file_get_contents($audioFile->getRealPath()), $audioFile->getClientOriginalName())
                ->post("{$aiUrl}/ai/process-lecture-pair", [
                    'language' => $language,
                    'course_id' => $courseId,
                    'title' => $title,
                ]);

            if ($response->successful()) {
                $data = $response->json();
                $pages = $data['pages'] ?? [];
                $generalNotes = $data['general_notes'] ?? [];

                foreach ($pages as $p) {
                    $pageNum = $p['page_number'] ?? 1;
                    foreach ($p['blocks'] ?? [] as $b) {
                        DB::table('lecture_slide_blocks')->insert([
                            'lecture_id' => $lectureId,
                            'slide_page' => $pageNum,
                            'block_index' => $b['block_index'] ?? 1,
                            'block_id' => $b['block_id'],
                            'kind' => $b['kind'] ?? 'bullet',
                            'original_slide_text' => $b['original_slide_text'] ?? '',
                            'is_confused' => false,
                            'last_spoken_sec' => $b['last_spoken_sec'] ?? null,
                            'created_at' => now(),
                            'updated_at' => now(),
                        ]);

                        foreach ($b['inline_notes'] ?? [] as $n) {
                            DB::table('inline_block_notes')->insert([
                                'lecture_id' => $lectureId,
                                'block_id' => $b['block_id'],
                                'explanation_text' => $n['explanation_text'] ?? '',
                                'raw_transcript' => $n['raw_transcript'] ?? '',
                                'timestamp_start_sec' => $n['timestamp_start_sec'] ?? 0.0,
                                'timestamp_end_sec' => $n['timestamp_end_sec'] ?? 0.0,
                                'novelty_score' => $n['novelty_score'] ?? 0.8,
                                'jumped_non_linear' => $n['jumped_non_linear'] ?? false,
                                'created_at' => now(),
                                'updated_at' => now(),
                            ]);
                        }

                        foreach ($b['highlights'] ?? [] as $h) {
                            DB::table('block_concept_highlights')->insert([
                                'lecture_id' => $lectureId,
                                'block_id' => $b['block_id'],
                                'term' => $h['term'],
                                'source_type' => $h['source_type'] ?? 'academic_internet',
                                'source_label' => $h['source_label'] ?? '',
                                'explanation' => $h['explanation'] ?? '',
                                'course_context_id' => $h['course_context_id'] ?? null,
                                'alternative_contexts_json' => json_encode($h['alternative_contexts'] ?? []),
                                'created_at' => now(),
                                'updated_at' => now(),
                            ]);
                        }
                    }
                }

                foreach ($generalNotes as $gn) {
                    DB::table('inline_block_notes')->insert([
                        'lecture_id' => $lectureId,
                        'block_id' => null,
                        'explanation_text' => $gn['explanation_text'] ?? '',
                        'raw_transcript' => $gn['raw_transcript'] ?? '',
                        'timestamp_start_sec' => $gn['timestamp_start_sec'] ?? 0.0,
                        'timestamp_end_sec' => $gn['timestamp_end_sec'] ?? 0.0,
                        'novelty_score' => 1.0,
                        'jumped_non_linear' => false,
                        'created_at' => now(),
                        'updated_at' => now(),
                    ]);
                }

                DB::table('lectures')->where('id', $lectureId)->update(['status' => 'completed']);

                return response()->json([
                    'lecture_id' => $lectureId,
                    'title' => $title,
                    'pages' => $pages,
                    'general_notes' => $generalNotes,
                ], 201);
            } else {
                Log::error("AI process-lecture-pair returned HTTP {$response->status()}: " . $response->body());
            }
        } catch (\Exception $e) {
            Log::error('AI process-lecture-pair exception: ' . $e->getMessage());
        }

        DB::table('lectures')->where('id', $lectureId)->update(['status' => 'failed']);
        return response()->json([
            'error' => 'AI Service processing failed',
            'lecture_id' => $lectureId,
            'title' => $title,
            'pages' => [],
            'general_notes' => [],
        ], 500);
    }

    public function getLectureInlineNotes(int $lectureId)
    {
        $lecture = DB::table('lectures')->where('id', $lectureId)->first();
        if (!$lecture) {
            return response()->json(['error' => 'Lecture not found'], 404);
        }

        $blocks = DB::table('lecture_slide_blocks')
            ->where('lecture_id', $lectureId)
            ->orderBy('slide_page')
            ->orderBy('block_index')
            ->get();

        $inlineNotes = DB::table('inline_block_notes')
            ->where('lecture_id', $lectureId)
            ->get()
            ->groupBy('block_id');

        $categoryByContent = [];
        $editedByContent = [];
        $lectureNoteRows = DB::table('lecture_notes as ln')
            ->join('slides as s', 'ln.slide_id', '=', 's.id')
            ->where('s.lecture_id', $lectureId)
            ->select('ln.bullet_content', 'ln.term_explanations', 'ln.is_edited_by_user')
            ->get();
        foreach ($lectureNoteRows as $ln) {
            $meta = json_decode($ln->term_explanations ?? '{}', true);
            $cat = is_array($meta) ? ($meta['category'] ?? 'core_explanation') : 'core_explanation';
            $key = trim((string) $ln->bullet_content);
            if ($key !== '') {
                $categoryByContent[$key] = $cat;
                $editedByContent[$key] = (bool) ($ln->is_edited_by_user ?? false);
            }
        }

        $slideIdToNumber = [];
        $slidesRows = DB::table('slides')
            ->where('lecture_id', $lectureId)
            ->select('id', 'slide_number', 'page_number')
            ->get();
        foreach ($slidesRows as $sr) {
            $num = (int) ($sr->slide_number ?? $sr->page_number ?? 0);
            if ($num > 0) {
                $slideIdToNumber[(int) $sr->id] = $num;
            }
        }

        $pageTimeBounds = [];
        $alignmentRows = DB::table('slide_alignments')
            ->where('lecture_id', $lectureId)
            ->select('slide_id', 'start_time_sec', 'end_time_sec')
            ->get();
        foreach ($alignmentRows as $ar) {
            $pNum = $slideIdToNumber[(int) $ar->slide_id] ?? null;
            if (!$pNum) continue;
            $s = (float) ($ar->start_time_sec ?? 0.0);
            $e = (float) ($ar->end_time_sec ?? $s);
            if (!isset($pageTimeBounds[$pNum])) {
                $pageTimeBounds[$pNum] = ['min' => $s, 'max' => $e];
            } else {
                $pageTimeBounds[$pNum]['min'] = min($pageTimeBounds[$pNum]['min'], $s);
                $pageTimeBounds[$pNum]['max'] = max($pageTimeBounds[$pNum]['max'], $e);
            }
        }

        if (empty($pageTimeBounds)) {
            $segRows = DB::table('transcript_segments')
                ->where('lecture_id', $lectureId)
                ->whereNotNull('aligned_slide_id')
                ->select('aligned_slide_id', 'start_time_sec', 'end_time_sec')
                ->get();
            foreach ($segRows as $sr) {
                $pNum = $slideIdToNumber[(int) $sr->aligned_slide_id] ?? null;
                if (!$pNum) continue;
                $s = (float) ($sr->start_time_sec ?? 0.0);
                $e = (float) ($sr->end_time_sec ?? $s);
                if (!isset($pageTimeBounds[$pNum])) {
                    $pageTimeBounds[$pNum] = ['min' => $s, 'max' => $e];
                } else {
                    $pageTimeBounds[$pNum]['min'] = min($pageTimeBounds[$pNum]['min'], $s);
                    $pageTimeBounds[$pNum]['max'] = max($pageTimeBounds[$pNum]['max'], $e);
                }
            }
        }

        $highlights = DB::table('block_concept_highlights')
            ->where('lecture_id', $lectureId)
            ->get()
            ->groupBy('block_id');

        $byPage = $blocks->groupBy('slide_page');
        $pages = [];

        $formatMmSs = function (float $sec): string {
            $total = max(0, (int) floor($sec));
            return sprintf('%02d:%02d', intdiv($total, 60), $total % 60);
        };

        foreach ($byPage as $pageNum => $bList) {
            $firstBlock = $bList->first();
            $pageTitle = ($firstBlock && $firstBlock->kind === 'heading')
                ? $firstBlock->original_slide_text
                : "Trang {$pageNum}";

            $pageNoteStartMin = null;
            $pageNoteEndMax = null;

            $pageBlocks = $bList->map(function ($b) use ($inlineNotes, $highlights, $categoryByContent, $editedByContent, &$pageNoteStartMin, &$pageNoteEndMax) {
                $notes = ($inlineNotes->get($b->block_id) ?? collect())->map(function ($n) use ($categoryByContent, $editedByContent, &$pageNoteStartMin, &$pageNoteEndMax) {
                    $startSec = (float) $n->timestamp_start_sec;
                    $endSec = (float) ($n->timestamp_end_sec ?? $startSec);
                    $min = floor($startSec / 60);
                    $sec = floor($startSec % 60);
                    $textKey = trim((string) $n->explanation_text);
                    if ($startSec > 0.0 || $endSec > 0.0) {
                        $pageNoteStartMin = ($pageNoteStartMin === null) ? $startSec : min($pageNoteStartMin, $startSec);
                        $pageNoteEndMax = ($pageNoteEndMax === null) ? max($startSec, $endSec) : max($pageNoteEndMax, $startSec, $endSec);
                    }
                    $updatedMoreThan2s = !empty($n->updated_at) && !empty($n->created_at)
                        && (strtotime((string) $n->updated_at) - strtotime((string) $n->created_at)) >= 2;
                    return [
                        'id' => (string) $n->id,
                        'explanationText' => $n->explanation_text,
                        'timestampSec' => $startSec,
                        'timestampLabel' => sprintf('%02d:%02d', $min, $sec),
                        'category' => $categoryByContent[$textKey] ?? 'core_explanation',
                        'evidenceQuote' => (string) ($n->raw_transcript ?? ''),
                        'isEdited' => (bool) (($editedByContent[$textKey] ?? false) || $updatedMoreThan2s),
                    ];
                })->values();

                $hl = ($highlights->get($b->block_id) ?? collect())->map(function ($h) {
                    return [
                        'term' => $h->term,
                        'sourceType' => $h->source_type === 'previous_lecture' ? 'PREVIOUS_LECTURE' : 'ACADEMIC_INTERNET',
                        'sourceLabel' => $h->source_label,
                        'explanation' => $h->explanation,
                    ];
                })->values();

                return [
                    'blockId' => $b->block_id,
                    'kind' => $b->kind,
                    'originalSlideText' => $b->original_slide_text,
                    'isCurrentlySpoken' => false,
                    'isMarkedConfused' => (bool) $b->is_confused,
                    'inlineNotes' => $notes,
                    'highlights' => $hl,
                ];
            })->values();

            $pInt = (int) $pageNum;
            $bounds = $pageTimeBounds[$pInt] ?? null;
            $minSec = $bounds ? $bounds['min'] : $pageNoteStartMin;
            $maxSec = $bounds ? $bounds['max'] : $pageNoteEndMax;
            $timeRangeLabel = '';
            if ($minSec !== null && $maxSec !== null && ($minSec > 0.0 || $maxSec > 0.0)) {
                $startStr = $formatMmSs($minSec);
                $endStr = $formatMmSs($maxSec);
                $timeRangeLabel = ($maxSec > $minSec && $endStr !== $startStr)
                    ? "{$startStr} – {$endStr}"
                    : $startStr;
            }

            $pages[] = [
                'pageNumber' => $pInt,
                'slideTitle' => $pageTitle,
                'timeRangeLabel' => $timeRangeLabel,
                'blocks' => $pageBlocks,
            ];
        }

        $generalNotes = ($inlineNotes->get(null) ?? collect())->map(function ($n) {
            return [
                'id' => (string) $n->id,
                'explanationText' => $n->explanation_text,
                'timestampSec' => (float) $n->timestamp_start_sec,
            ];
        })->values();

        return response()->json([
            'lecture_id' => $lectureId,
            'title' => $lecture->title,
            'pages' => $pages,
            'general_notes' => $generalNotes,
        ]);
    }

    public function selectHighlightContext(Request $request, int $highlightId)
    {
        $validated = $request->validate([
            'course_context_id' => 'required|integer',
        ]);

        $highlight = DB::table('block_concept_highlights')->where('id', $highlightId)->first();
        if (!$highlight) {
            return response()->json(['error' => 'Highlight not found'], 404);
        }

        DB::table('block_concept_highlights')
            ->where('id', $highlightId)
            ->update([
                'course_context_id' => $validated['course_context_id'],
                'updated_at' => now(),
            ]);

        return response()->json([
            'id' => $highlightId,
            'course_context_id' => $validated['course_context_id'],
            'status' => 'updated',
        ]);
    }

    /**
     * =========================================================================
     * 3B. STREAM AUDIO (HTTP 206), CRUD INLINE NOTE & XUẤT MARKDOWN (TUẦN 4)
     * =========================================================================
     */
    public function streamLectureAudio(int $lectureId)
    {
        $lecture = DB::table('lectures')->where('id', $lectureId)->first();
        if (!$lecture || empty($lecture->audio_file_path)) {
            return response()->json(['message' => 'Không tìm thấy file âm thanh của bài giảng.'], 404);
        }

        $rawPath = (string) $lecture->audio_file_path;
        $resolvedPath = null;
        $candidates = [
            $rawPath,
            storage_path('app/public/' . ltrim($rawPath, '/\\')),
            storage_path('app/' . ltrim($rawPath, '/\\')),
        ];
        foreach ($candidates as $cand) {
            if ($cand && file_exists($cand) && is_file($cand)) {
                $resolvedPath = $cand;
                break;
            }
        }

        if (!$resolvedPath) {
            return response()->json(['message' => 'File âm thanh không tồn tại trên máy chủ.'], 404);
        }

        $ext = strtolower(pathinfo($resolvedPath, PATHINFO_EXTENSION));
        $mime = match ($ext) {
            'm4a', 'mp4' => 'audio/mp4',
            'mp3' => 'audio/mpeg',
            'wav' => 'audio/wav',
            'ogg' => 'audio/ogg',
            'aac' => 'audio/aac',
            default => 'application/octet-stream',
        };

        // BinaryFileResponse của Symfony/Laravel tự xử lý HTTP Range (206 Partial Content)
        return response()->file($resolvedPath, [
            'Content-Type' => $mime,
            'Accept-Ranges' => 'bytes',
        ]);
    }

    public function updateInlineNote(Request $request, int $noteId)
    {
        $validated = $request->validate([
            'explanation_text' => 'required|string|max:2000',
        ]);

        $note = DB::table('inline_block_notes')->where('id', $noteId)->first();
        if (!$note) {
            return response()->json(['message' => 'Không tìm thấy ghi chú.'], 404);
        }

        $oldText = (string) $note->explanation_text;
        $newText = trim($validated['explanation_text']);

        DB::table('inline_block_notes')
            ->where('id', $noteId)
            ->update([
                'explanation_text' => $newText,
                'updated_at' => now(),
            ]);

        // Đồng bộ trạng thái is_edited_by_user = true sang bảng lecture_notes nếu khớp bài giảng & nội dung gốc
        $slideIds = DB::table('slides')
            ->where('lecture_id', $note->lecture_id)
            ->pluck('id');
        if ($slideIds->isNotEmpty()) {
            DB::table('lecture_notes')
                ->whereIn('slide_id', $slideIds)
                ->where('bullet_content', $oldText)
                ->update([
                    'bullet_content' => $newText,
                    'is_edited_by_user' => true,
                    'updated_at' => now(),
                ]);
        }

        return response()->json([
            'id' => (string) $noteId,
            'lecture_id' => (int) $note->lecture_id,
            'block_id' => $note->block_id,
            'explanationText' => $newText,
            'is_edited_by_user' => true,
        ]);
    }

    public function deleteInlineNote(int $noteId)
    {
        $note = DB::table('inline_block_notes')->where('id', $noteId)->first();
        if (!$note) {
            return response()->json(['message' => 'Không tìm thấy ghi chú.'], 404);
        }

        DB::table('inline_block_notes')->where('id', $noteId)->delete();

        $slideIds = DB::table('slides')
            ->where('lecture_id', $note->lecture_id)
            ->pluck('id');
        if ($slideIds->isNotEmpty()) {
            DB::table('lecture_notes')
                ->whereIn('slide_id', $slideIds)
                ->where('bullet_content', $note->explanation_text)
                ->delete();
        }

        return response()->json([
            'deleted' => true,
            'id' => (string) $noteId,
        ]);
    }

    public function exportLectureMarkdown(int $lectureId)
    {
        $lecture = DB::table('lectures')->where('id', $lectureId)->first();
        if (!$lecture) {
            return response()->json(['message' => 'Không tìm thấy bài giảng.'], 404);
        }

        $course = DB::table('courses')->where('id', $lecture->course_id)->first();
        $courseName = $course ? $course->name : "Môn học #{$lecture->course_id}";

        $blocks = DB::table('lecture_slide_blocks')
            ->where('lecture_id', $lectureId)
            ->orderBy('slide_page')
            ->orderBy('block_index')
            ->get();

        $inlineNotes = DB::table('inline_block_notes')
            ->where('lecture_id', $lectureId)
            ->orderBy('timestamp_start_sec')
            ->get()
            ->groupBy('block_id');

        $lines = [];
        $lines[] = "# {$lecture->title}";
        $lines[] = "";
        $lines[] = "- **Môn học:** {$courseName}";
        if ($course && !empty($course->lecturer)) {
            $lines[] = "- **Giảng viên:** {$course->lecturer}";
        }
        $lines[] = "- **Xuất lúc:** " . now()->format('d/m/Y H:i');
        $lines[] = "";
        $lines[] = "---";
        $lines[] = "";

        $formatTs = function (float $sec): string {
            $s = max(0, (int) floor($sec));
            return sprintf('%02d:%02d', intdiv($s, 60), $s % 60);
        };

        if ($blocks->isNotEmpty()) {
            foreach ($blocks->groupBy('slide_page') as $pageNum => $pageBlocks) {
                $lines[] = "## Slide {$pageNum}";
                $lines[] = "";
                foreach ($pageBlocks as $b) {
                    $confusedTag = $b->is_confused ? ' **[Cần xem lại]**' : '';
                    $text = trim((string) $b->original_slide_text);
                    if ($b->kind === 'heading') {
                        $lines[] = "### {$text}{$confusedTag}";
                    } elseif ($b->kind === 'formula') {
                        $lines[] = "- `{$text}`{$confusedTag}";
                    } else {
                        $lines[] = "- {$text}{$confusedTag}";
                    }

                    $bNotes = $inlineNotes->get($b->block_id) ?? collect();
                    foreach ($bNotes as $n) {
                        $ts = $formatTs((float) $n->timestamp_start_sec);
                        $exp = trim((string) $n->explanation_text);
                        $lines[] = "  > **[{$ts}]** {$exp}";
                    }
                }
                $lines[] = "";
            }
        } else {
            // Fallback cho bài giảng cũ chưa có lecture_slide_blocks
            $slides = DB::table('slides')->where('lecture_id', $lectureId)->orderBy('slide_number')->get();
            foreach ($slides as $sl) {
                $sNum = $sl->slide_number ?? $sl->page_number ?? 1;
                $lines[] = "## Slide {$sNum}: " . ($sl->title ?? '');
                $lines[] = "";
                $raw = trim((string) ($sl->raw_text ?? $sl->slide_text ?? ''));
                if ($raw !== '') {
                    $lines[] = $raw;
                    $lines[] = "";
                }
                $sNotes = DB::table('lecture_notes as ln')
                    ->leftJoin('evidences as ev', 'ln.evidence_id', '=', 'ev.id')
                    ->where('ln.slide_id', $sl->id)
                    ->select('ln.bullet_content', 'ev.start_time_sec')
                    ->get();
                foreach ($sNotes as $sn) {
                    $ts = $formatTs((float) ($sn->start_time_sec ?? 0.0));
                    $lines[] = "> **[{$ts}]** " . trim((string) $sn->bullet_content);
                }
                $lines[] = "";
            }
        }

        $generalNotes = $inlineNotes->get(null) ?? collect();
        if ($generalNotes->isNotEmpty()) {
            $lines[] = "## Ghi chú bổ sung ngoài Slide";
            $lines[] = "";
            foreach ($generalNotes as $gn) {
                $ts = $formatTs((float) $gn->timestamp_start_sec);
                $lines[] = "> **[{$ts}]** " . trim((string) $gn->explanation_text);
            }
            $lines[] = "";
        }

        $markdown = implode("\n", $lines);
        return response($markdown, 200, [
            'Content-Type' => 'text/markdown; charset=utf-8',
            'Content-Disposition' => "attachment; filename=\"lecture_{$lectureId}_notes.md\"",
        ]);
    }

    /**
     * =========================================================================
     * 4. CÁC ENDPOINT CŨ BẢO TỒN (WORKSPACE, KNOWLEDGE GRAPH, DICTIONARY, DASHBOARD)
     * =========================================================================
     */
    public function getLectureWorkspace(int $lectureId)
    {
        $lecture = DB::table('lectures')->where('id', $lectureId)->first();
        if (!$lecture) {
            return response()->json(['error' => 'Lecture not found'], 404);
        }

        $slides = DB::table('slides')
            ->where('lecture_id', $lectureId)
            ->orderBy('slide_number')
            ->get();

        $alignments = DB::table('slide_alignments')
            ->where('lecture_id', $lectureId)
            ->orderBy('start_time_sec')
            ->get()
            ->groupBy('slide_id');

        $notes = DB::table('lecture_notes as ln')
            ->leftJoin('evidences as ev', 'ln.evidence_id', '=', 'ev.id')
            ->whereIn('ln.slide_id', $slides->pluck('id'))
            ->select(
                'ln.id',
                'ln.slide_id',
                DB::raw('COALESCE(ln.bullet_content, "") as bullet_point'),
                DB::raw('1 as is_instructor_oral_only'),
                DB::raw('"ORAL_EXPANSION" as summary_type'),
                DB::raw('COALESCE(ev.exact_quote, "") as verbatim_quote'),
                DB::raw('95.0 as similarity_ratio'),
                DB::raw('COALESCE(ev.start_time_sec, 0.0) as start_time_sec'),
                DB::raw('COALESCE(ev.end_time_sec, 0.0) as end_time_sec')
            )
            ->get()
            ->groupBy('slide_id');

        $occurrences = DB::table('concept_occurrences as co')
            ->join('concepts as c', 'co.concept_id', '=', 'c.id')
            ->join('lectures as l', 'co.lecture_id', '=', 'l.id')
            ->select(
                'co.id',
                'co.concept_id',
                'co.lecture_id',
                'co.slide_id',
                'co.occurrence_role',
                'co.pos_linear_index',
                'co.context_excerpt',
                'co.timestamp_sec',
                DB::raw('COALESCE(c.canonical_name, c.name) as concept_name_vi'),
                DB::raw('COALESCE(c.name, c.canonical_name) as concept_name_en'),
                'l.title as lecture_title'
            )
            ->get();

        $byConcept = $occurrences->groupBy('concept_id');
        $crossLinksBySlide = [];

        foreach ($byConcept as $cid => $occList) {
            $sorted = $occList->sortBy('pos_linear_index')->values();
            if ($sorted->count() <= 1) continue;

            $origin = $sorted->first();
            $originLectureOrder = (int) floor($origin->pos_linear_index / 1000);
            $originSlideNo = (int) ($origin->pos_linear_index % 1000);

            for ($i = 1; $i < $sorted->count(); $i++) {
                $later = $sorted->get($i);
                $laterLectureOrder = (int) floor($later->pos_linear_index / 1000);
                $laterSlideNo = (int) ($later->pos_linear_index % 1000);

                $crossLinksBySlide[$later->slide_id][] = [
                    'concept_id'            => $origin->concept_id,
                    'concept_name_vi'       => $origin->concept_name_vi,
                    'concept_name_en'       => $origin->concept_name_en,
                    'link_direction'        => 'BACK_TO_ORIGIN',
                    'target_lecture_id'     => $origin->lecture_id,
                    'target_lecture_order'  => $originLectureOrder,
                    'target_lecture_title'  => $origin->lecture_title,
                    'target_slide_id'       => $origin->slide_id,
                    'target_slide_number'   => $originSlideNo,
                    'target_timestamp_sec'  => (float) ($origin->timestamp_sec ?? 0.0),
                    'badge_text'            => "Đã học ở Chương {$originLectureOrder} (Slide {$originSlideNo}) — Bấm để xem lại",
                ];
            }
        }

        $slidesData = $slides->map(function ($s) use ($alignments, $notes, $crossLinksBySlide) {
            $firstAlign = ($alignments->get($s->id) ?? collect())->first();
            $terms = json_decode($s->extracted_terms ?? $s->terms_json ?? '[]', true);
            if (!is_array($terms)) {
                $terms = [];
            }

            $slideNotes = ($notes->get($s->id) ?? collect())->map(function ($n) {
                return [
                    'id'                      => (int) $n->id,
                    'slide_id'                => (int) $n->slide_id,
                    'bullet_point'            => (string) $n->bullet_point,
                    'novelty_score'           => 0.85,
                    'is_instructor_oral_only' => (bool) $n->is_instructor_oral_only,
                    'summary_type'            => (string) $n->summary_type,
                    'verbatim_quote'          => (string) $n->verbatim_quote,
                    'similarity_ratio'        => (float) $n->similarity_ratio,
                    'start_time_sec'          => (float) $n->start_time_sec,
                    'end_time_sec'            => (float) $n->end_time_sec,
                ];
            })->values();

            return [
                'id'                  => $s->id,
                'slide_number'        => $s->slide_number ?? $s->page_number ?? 1,
                'extracted_text'      => $s->slide_text ?? $s->raw_text ?? $s->title ?? '',
                'image_url'           => $s->image_url ?? null,
                'terms_json'          => $terms,
                'start_time_sec'      => $firstAlign ? (float) $firstAlign->start_time_sec : 0.0,
                'end_time_sec'        => $firstAlign ? (float) $firstAlign->end_time_sec : 0.0,
                'confidence_score'    => $firstAlign ? (float) ($firstAlign->alignment_confidence ?? 1.0) : 1.0,
                'notes'               => $slideNotes,
                'cross_chapter_links' => $crossLinksBySlide[$s->id] ?? [],
            ];
        });

        return response()->json([
            'lecture' => $lecture,
            'slides'  => $slidesData,
        ]);
    }

    public function getCourseKnowledgeGraph(int $courseId)
    {
        $concepts = DB::table('concepts')
            ->where('course_id', $courseId)
            ->get()
            ->map(function ($c) {
                $aliases = json_decode($c->aliases ?? $c->aliases_json ?? '[]', true);
                if (!is_array($aliases)) {
                    $aliases = [];
                }
                return [
                    'id'                         => $c->id,
                    'course_id'                  => $c->course_id,
                    'canonical_name_vi'          => $c->canonical_name ?? $c->name,
                    'canonical_name_en'          => $c->name ?? $c->canonical_name,
                    'aliases_json'               => $aliases,
                    'first_introduced_lecture_id'=> $c->first_defined_lecture_order ?? 1,
                    'first_introduced_slide_no'  => $c->first_defined_slide_id ?? 1,
                    'defined_order_index'        => $c->first_defined_lecture_order ? ($c->first_defined_lecture_order * 1000 + 1) : 1001,
                    'definition_summary'         => $c->definition_summary ?? $c->definition ?? '',
                    'is_used_before_defined'     => (bool) ($c->is_used_before_defined ?? false),
                ];
            });

        $conceptIds = $concepts->pluck('id');

        $relations = DB::table('concept_relations')
            ->whereIn('source_concept_id', $conceptIds)
            ->orWhereIn('target_concept_id', $conceptIds)
            ->get()
            ->map(function ($r) {
                return [
                    'id'                               => $r->id,
                    'source_concept_id'                => $r->source_concept_id,
                    'target_concept_id'                => $r->target_concept_id,
                    'relation_type'                    => strtoupper($r->relation_type ?? 'PREREQUISITE_OF'),
                    'weight'                           => (float) ($r->confidence ?? 0.9),
                    'evidence_quote'                   => 'Liên kết kiến thức chuyên ngành giữa hai khái niệm.',
                    'is_used_before_defined_violation' => (bool) ($r->status === 'pending_review' || $r->relation_type === 'contrast'),
                ];
            });

        $violationsCount = $relations->where('is_used_before_defined_violation', true)->count();

        return response()->json([
            'course_id'        => $courseId,
            'total_concepts'   => $concepts->count(),
            'total_relations'  => $relations->count(),
            'violations_count' => $violationsCount,
            'concepts'         => $concepts,
            'relations'        => $relations,
        ]);
    }

    public function getConceptDictionary(int $conceptId)
    {
        $concept = DB::table('concepts')->where('id', $conceptId)->first();
        if (!$concept) {
            return response()->json(['error' => 'Concept not found'], 404);
        }

        $entry = DB::table('dictionary_entries')->where('concept_id', $conceptId)->first();
        $references = DB::table('external_references')
            ->where('concept_id', $conceptId)
            ->orderByDesc('final_rank_score')
            ->get();

        $occurrences = DB::table('concept_occurrences as co')
            ->join('lectures as l', 'co.lecture_id', '=', 'l.id')
            ->where('co.concept_id', $conceptId)
            ->orderBy('co.pos_linear_index')
            ->select('co.*', 'l.title as lecture_title')
            ->get();

        $crossLinks = [];
        if ($occurrences->count() > 1) {
            $origin = $occurrences->first();
            $originLecOrder = (int) floor($origin->pos_linear_index / 1000);
            $originSlideNo = (int) ($origin->pos_linear_index % 1000);

            for ($i = 1; $i < $occurrences->count(); $i++) {
                $later = $occurrences->get($i);
                $laterLecOrder = (int) floor($later->pos_linear_index / 1000);
                $laterSlideNo = (int) ($later->pos_linear_index % 1000);

                $crossLinks[] = [
                    'concept_id'           => $conceptId,
                    'concept_name_vi'      => $concept->canonical_name ?? $concept->name,
                    'concept_name_en'      => $concept->name ?? $concept->canonical_name,
                    'link_direction'       => 'BACK_TO_ORIGIN',
                    'target_lecture_id'    => $origin->lecture_id,
                    'target_lecture_order' => $originLecOrder,
                    'target_lecture_title' => $origin->lecture_title,
                    'target_slide_number'  => $originSlideNo,
                    'target_timestamp_sec' => (float) ($origin->timestamp_sec ?? 0.0),
                    'badge_text'           => "Đã học ở Chương {$originLecOrder} (Slide {$originSlideNo}) — Bấm để xem lại",
                ];
            }
        }

        return response()->json([
            'concept_id'                => $concept->id,
            'canonical_name_vi'         => $concept->canonical_name ?? $concept->name,
            'canonical_name_en'         => $concept->name ?? $concept->canonical_name,
            'abstractness_score'        => $entry ? (float) $entry->abstractness_score : 0.65,
            'slide_skeleton_context'    => $entry ? $entry->slide_skeleton_context : '',
            'lecturer_oral_explanation' => $entry ? $entry->lecturer_oral_explanation : 'Giảng viên nhắc tới khái niệm này trên slide.',
            'ai_deep_explanation'       => $entry ? $entry->ai_deep_explanation : 'Giải thích chuyên sâu đang được cập nhật.',
            'intuition_example'         => $entry ? $entry->intuition_example : '',
            'cross_chapter_links'       => $crossLinks,
            'external_references'       => $references,
        ]);
    }

    public function getCourseDictionary(int $courseId)
    {
        $concepts = DB::table('concepts as c')
            ->leftJoin('dictionary_entries as de', 'c.id', '=', 'de.concept_id')
            ->where('c.course_id', $courseId)
            ->select(
                'c.id',
                'c.canonical_name as canonical_name_vi',
                'c.name as canonical_name_en',
                'c.aliases',
                'c.definition_summary',
                'c.first_defined_lecture_order',
                'c.first_defined_slide_id',
                DB::raw('COALESCE(de.abstractness_score, 0.5) as abstractness_score'),
                DB::raw('COALESCE(de.ai_deep_explanation, c.definition_summary) as ai_deep_explanation'),
                'de.intuition_example'
            )
            ->orderByDesc('abstractness_score')
            ->get();

        $refCounts = DB::table('external_references')
            ->select('concept_id', DB::raw('count(*) as total_refs'))
            ->groupBy('concept_id')
            ->pluck('total_refs', 'concept_id');

        $result = $concepts->map(function ($c) use ($refCounts) {
            $aliases = json_decode($c->aliases ?? '[]', true);
            return [
                'id'                          => $c->id,
                'canonical_name_vi'           => $c->canonical_name_vi ?? $c->canonical_name_en,
                'canonical_name_en'           => $c->canonical_name_en ?? $c->canonical_name_vi,
                'aliases'                     => is_array($aliases) ? $aliases : [],
                'abstractness_score'          => (float) $c->abstractness_score,
                'first_introduced_lecture_id' => (int) ($c->first_defined_lecture_order ?? 1),
                'first_introduced_slide_no'   => (int) ($c->first_defined_slide_id ?? 1),
                'definition_summary'          => $c->definition_summary ?? '',
                'ai_deep_explanation'        => $c->ai_deep_explanation ?? '',
                'intuition_example'           => $c->intuition_example ?? '',
                'references_count'            => (int) ($refCounts[$c->id] ?? 0),
            ];
        });

        return response()->json([
            'course_id'       => $courseId,
            'total_entries'   => $result->count(),
            'dictionary_list' => $result,
        ]);
    }

    public function getCourseLectures(int $courseId = 1)
    {
        $lectures = DB::table('lectures')
            ->where('course_id', $courseId)
            ->orderBy('id')
            ->get([
                'id',
                'title',
                DB::raw('COALESCE(duration_sec, 5400) as duration_sec'),
                'status',
            ]);

        $slideCounts = DB::table('slides')
            ->select('lecture_id', DB::raw('count(*) as count'))
            ->groupBy('lecture_id')
            ->pluck('count', 'lecture_id');

        $result = $lectures->map(function ($lec) use ($slideCounts) {
            return [
                'id'                 => $lec->id,
                'title'              => $lec->title,
                'total_duration_sec' => (float) $lec->duration_sec,
                'status'             => $lec->status,
                'slide_count'        => (int) ($slideCounts[$lec->id] ?? 0),
            ];
        });

        return response()->json([
            'course_id' => $courseId,
            'lectures'  => $result,
        ]);
    }

    public function getTasksDashboard(int $courseId)
    {
        return response()->json([
            'course_id' => $courseId,
            'summary'   => ['critical_count' => 0, 'high_count' => 0, 'total_active' => 0],
            'tasks'     => [],
        ]);
    }

    /**
     * =========================================================================
     * 5. MINDMAP TRI THỨC 3 CẤP & ĐỒNG BỘ TỰ MỌC NHÁNH KHI THÊM SLIDE
     * =========================================================================
     */
    private function normalizeMindmapBranchBase(string $raw): string
    {
        $clean = preg_replace('/\.(pdf|pptx?)$/iu', '', trim($raw));
        $clean = preg_replace('/^Bài\s*#?\d+\s*[•:\-–]\s*/iu', '', (string) $clean);
        return mb_strtolower(trim((string) $clean));
    }

    private function extractMindmapConceptsFromBlocks(array $blocks, array $slideTimestamps = []): array
    {
        $byPage = [];
        foreach ($blocks as $b) {
            if (!is_array($b)) {
                continue;
            }
            $pNum = max(1, (int) ($b['slide_page'] ?? 1));
            $txt = trim((string) ($b['text'] ?? $b['original_slide_text'] ?? ''));
            if ($txt === '') {
                continue;
            }
            $byPage[$pNum][] = [
                'kind' => (string) ($b['kind'] ?? 'bullet'),
                'text' => $txt,
            ];
        }
        ksort($byPage);
        if (empty($byPage)) {
            return [];
        }

        $skipExactOrPrefix = [
            'nội dung', 'mục lục', 'agenda', 'today', 'topics', 'outline',
            'contents', 'table of contents', 'thank you', 'cảm ơn', 'q&a', 'hỏi đáp',
        ];

        $pageEntries = [];
        foreach ($byPage as $pageNum => $items) {
            $heading = null;
            foreach ($items as $it) {
                if ($it['kind'] === 'heading') {
                    $heading = $it['text'];
                    break;
                }
            }
            if ($heading === null) {
                $heading = $items[0]['text'];
            }
            $headingClean = trim((string) preg_replace('/^(?:Slide|Trang)\s+\d+\s*[:\-–]\s*/iu', '', $heading));
            if ($headingClean === '') {
                $headingClean = "Trang {$pageNum}";
            }

            $headingLower = mb_strtolower($headingClean);
            $isOverviewOrEnding = false;
            foreach ($skipExactOrPrefix as $kw) {
                if ($headingLower === $kw || str_starts_with($headingLower, $kw . ':') || str_starts_with($headingLower, $kw . ' ')) {
                    $isOverviewOrEnding = true;
                    break;
                }
            }
            // Bỏ qua trang bìa (Slide 1 nếu toàn thông tin môn học / giảng viên và có từ >= 3 trang)
            if ($pageNum === 1 && count($byPage) >= 3 && (str_contains($headingLower, 'bài giảng') || str_contains($headingLower, 'giảng viên') || count($items) <= 2)) {
                $isOverviewOrEnding = true;
            }

            $bullets = [];
            foreach ($items as $it) {
                $line = trim((string) $it['text']);
                if ($line === '' || $line === $heading) {
                    continue;
                }
                $bullets[] = mb_substr($line, 0, 120);
                if (count($bullets) >= 2) {
                    break;
                }
            }
            $summary = !empty($bullets) ? implode(' • ', $bullets) : $headingClean;
            $mergeKey = mb_strtolower(trim((string) preg_replace('/\s*\(\d+\)\s*$/u', '', $headingClean)));

            $pageEntries[] = [
                'page' => $pageNum,
                'title' => mb_substr($headingClean, 0, 120),
                'merge_key' => $mergeKey ?: "page_{$pageNum}",
                'summary' => mb_substr($summary, 0, 220),
                'timestamp_sec' => (int) round((float) ($slideTimestamps[$pageNum] ?? 0)),
                'skip' => $isOverviewOrEnding,
            ];
        }

        $filtered = array_values(array_filter($pageEntries, fn ($e) => !$e['skip']));
        if (empty($filtered)) {
            $filtered = $pageEntries;
        }

        // Gộp các trang liên tiếp trùng chủ đề (VD: Slide 3–4)
        $merged = [];
        foreach ($filtered as $entry) {
            $lastIdx = count($merged) - 1;
            if ($lastIdx >= 0 && $merged[$lastIdx]['merge_key'] === $entry['merge_key']) {
                $prev = $merged[$lastIdx];
                $prev['end_page'] = $entry['page'];
                $prev['slide_ref'] = ($prev['start_page'] === $entry['page'])
                    ? "Slide {$prev['start_page']}"
                    : "Slide {$prev['start_page']}–{$entry['page']}";
                if ($prev['timestamp_sec'] === 0 && $entry['timestamp_sec'] > 0) {
                    $prev['timestamp_sec'] = $entry['timestamp_sec'];
                }
                if (mb_strlen($prev['summary']) < 120 && $entry['summary'] !== $prev['title']) {
                    $prev['summary'] = mb_substr($prev['summary'] . ' • ' . $entry['summary'], 0, 220);
                }
                $merged[$lastIdx] = $prev;
            } else {
                $merged[] = [
                    'title' => $entry['title'],
                    'start_page' => $entry['page'],
                    'end_page' => $entry['page'],
                    'slide_ref' => "Slide {$entry['page']}",
                    'slide_number' => $entry['page'],
                    'timestamp_sec' => $entry['timestamp_sec'],
                    'summary' => $entry['summary'],
                    'merge_key' => $entry['merge_key'],
                ];
            }
        }

        // Giới hạn tối đa 8 khái niệm đại diện mỗi nhánh để Mindmap rõ thoáng
        if (count($merged) > 8) {
            $merged = array_slice($merged, 0, 8);
        }
        return $merged;
    }

    public function upsertMindmapBranchFromBlocks(
        int $courseId,
        ?int $lectureId,
        ?int $vaultSlideId,
        string $title,
        array $blocks,
        bool $isNewlyAdded = true,
        array $slideTimestamps = []
    ): ?int {
        if (!Schema::hasTable('course_mindmap_branches') || !Schema::hasTable('course_mindmap_leaves')) {
            return null;
        }

        $concepts = $this->extractMindmapConceptsFromBlocks($blocks, $slideTimestamps);
        if (empty($concepts)) {
            $concepts = [[
                'title' => mb_substr(trim($title) ?: 'Nội dung chính', 0, 120),
                'slide_ref' => 'Slide 1',
                'slide_number' => 1,
                'timestamp_sec' => (int) round((float) ($slideTimestamps[1] ?? 0)),
                'summary' => trim($title) ?: 'Tài liệu mới nạp cho môn học',
            ]];
        }

        return $this->upsertMindmapBranchConcepts(
            $courseId,
            $lectureId,
            $vaultSlideId,
            $title,
            $concepts,
            $isNewlyAdded
        );
    }

    private function upsertMindmapBranchConcepts(
        int $courseId,
        ?int $lectureId,
        ?int $vaultSlideId,
        string $title,
        array $concepts,
        bool $isNewlyAdded
    ): ?int {
        if (!Schema::hasTable('course_mindmap_branches') || !Schema::hasTable('course_mindmap_leaves')) {
            return null;
        }

        $tones = ['TEAL', 'COBALT', 'AMBER', 'VIOLET', 'ROSE'];
        $branchKey = $lectureId !== null
            ? "lecture_{$lectureId}"
            : ($vaultSlideId !== null ? "vault_{$vaultSlideId}" : 'custom_' . substr(md5($title), 0, 10));

        $existingBranch = DB::table('course_mindmap_branches')
            ->where('course_id', $courseId)
            ->where('branch_key', $branchKey)
            ->first();

        // Nếu bài giảng vừa tạo khớp tên với nhánh vault cũ chưa gắn lecture -> nâng cấp nhánh vault lên lecture
        if (!$existingBranch && $lectureId !== null) {
            $normTarget = $this->normalizeMindmapBranchBase($title);
            $vaultCandidates = DB::table('course_mindmap_branches')
                ->where('course_id', $courseId)
                ->whereNull('lecture_id')
                ->get();
            foreach ($vaultCandidates as $vc) {
                if ($normTarget !== '' && $this->normalizeMindmapBranchBase((string) $vc->title) === $normTarget) {
                    $existingBranch = $vc;
                    break;
                }
            }
        }

        $cleanTitle = mb_substr(trim($title), 0, 220);
        if ($existingBranch) {
            $branchId = (int) $existingBranch->id;
            DB::table('course_mindmap_branches')
                ->where('id', $branchId)
                ->update([
                    'lecture_id' => $lectureId ?? $existingBranch->lecture_id,
                    'vault_slide_id' => $vaultSlideId ?? $existingBranch->vault_slide_id,
                    'branch_key' => $branchKey,
                    'title' => $cleanTitle,
                    'is_newly_added' => $isNewlyAdded ? true : (bool) $existingBranch->is_newly_added,
                    'updated_at' => now(),
                ]);
        } else {
            $existingCount = DB::table('course_mindmap_branches')
                ->where('course_id', $courseId)
                ->count();
            $tone = $tones[$existingCount % count($tones)];
            $branchId = (int) DB::table('course_mindmap_branches')->insertGetId([
                'course_id' => $courseId,
                'lecture_id' => $lectureId,
                'vault_slide_id' => $vaultSlideId,
                'branch_key' => $branchKey,
                'title' => $cleanTitle,
                'tone' => $tone,
                'is_newly_added' => $isNewlyAdded,
                'sort_order' => $existingCount + 1,
                'created_at' => now(),
                'updated_at' => now(),
            ]);
        }

        $existingLeaves = DB::table('course_mindmap_leaves')
            ->where('branch_id', $branchId)
            ->get()
            ->keyBy('slug');

        $seenSlugs = [];
        foreach ($concepts as $idx => $c) {
            $cTitle = trim((string) ($c['title'] ?? ''));
            if ($cTitle === '') {
                continue;
            }
            $sNum = max(1, (int) ($c['slide_number'] ?? 1));
            $sRef = trim((string) ($c['slide_ref'] ?? "Slide {$sNum}"));
            $tsSec = max(0, (int) ($c['timestamp_sec'] ?? 0));
            $summary = trim((string) ($c['summary'] ?? $cTitle));
            $slugBase = substr(preg_replace('/[^a-z0-9]+/', '-', mb_strtolower($cTitle)) ?: 'concept', 0, 80);
            $slug = trim("{$slugBase}-s{$sNum}-{$idx}", '-');
            if ($slug === '') {
                $slug = "concept-{$sNum}-{$idx}";
            }
            $seenSlugs[] = $slug;

            $prevLeaf = $existingLeaves->get($slug);
            $isMastered = $prevLeaf ? (bool) $prevLeaf->is_mastered : false;

            DB::table('course_mindmap_leaves')->updateOrInsert(
                ['branch_id' => $branchId, 'slug' => $slug],
                [
                    'course_id' => $courseId,
                    'lecture_id' => $lectureId,
                    'title' => mb_substr($cTitle, 0, 220),
                    'slide_ref' => mb_substr($sRef, 0, 64),
                    'slide_number' => $sNum,
                    'timestamp_sec' => $tsSec,
                    'summary' => $summary,
                    'is_mastered' => $isMastered,
                    'sort_order' => $idx + 1,
                    'updated_at' => now(),
                    'created_at' => $prevLeaf ? $prevLeaf->created_at : now(),
                ]
            );
        }

        if (!empty($seenSlugs)) {
            DB::table('course_mindmap_leaves')
                ->where('branch_id', $branchId)
                ->whereNotIn('slug', $seenSlugs)
                ->delete();
        }

        return $branchId;
    }

    public function syncLectureToMindmap(int $courseId, int $lectureId, bool $isNewlyAdded = false): ?int
    {
        if (!Schema::hasTable('course_mindmap_branches') || !Schema::hasTable('course_mindmap_leaves')) {
            return null;
        }
        $lecture = DB::table('lectures')->where('id', $lectureId)->first();
        if (!$lecture) {
            return null;
        }

        $blocksRows = DB::table('lecture_slide_blocks')
            ->where('lecture_id', $lectureId)
            ->orderBy('slide_page')
            ->orderBy('block_index')
            ->get();

        $blocks = [];
        foreach ($blocksRows as $b) {
            $blocks[] = [
                'slide_page' => (int) $b->slide_page,
                'kind' => (string) $b->kind,
                'text' => (string) $b->original_slide_text,
            ];
        }

        $slidesRows = DB::table('slides')
            ->where('lecture_id', $lectureId)
            ->orderBy('slide_number')
            ->get();

        if (empty($blocks) && $slidesRows->isNotEmpty()) {
            foreach ($slidesRows as $sr) {
                $sNum = max(1, (int) ($sr->slide_number ?? $sr->page_number ?? 1));
                $raw = (string) ($sr->raw_text ?? $sr->slide_text ?? $sr->title ?? '');
                $lines = array_values(array_filter(array_map('trim', explode("\n", $raw))));
                if (empty($lines) && !empty($sr->title)) {
                    $lines[] = trim((string) $sr->title);
                }
                foreach ($lines as $lIdx => $lTxt) {
                    $blocks[] = [
                        'slide_page' => $sNum,
                        'kind' => ($lIdx === 0 && mb_strlen($lTxt) < 90) ? 'heading' : 'bullet',
                        'text' => $lTxt,
                    ];
                }
            }
        }

        // Thu thập mốc giây phát audio cho từng trang Slide
        $slideTimestamps = [];
        $blockPageMap = $blocksRows->pluck('slide_page', 'block_id');
        $inlineNoteRows = DB::table('inline_block_notes')
            ->where('lecture_id', $lectureId)
            ->where('timestamp_start_sec', '>', 0)
            ->get();
        foreach ($inlineNoteRows as $in) {
            $p = (int) ($blockPageMap[$in->block_id] ?? 0);
            if ($p <= 0 && preg_match('/(?:slide_|s)(\d+)/i', (string) $in->block_id, $m)) {
                $p = (int) $m[1];
            }
            if ($p > 0) {
                $sec = (int) floor((float) $in->timestamp_start_sec);
                $slideTimestamps[$p] = isset($slideTimestamps[$p]) ? min($slideTimestamps[$p], $sec) : $sec;
            }
        }

        if ($slidesRows->isNotEmpty()) {
            $slideNumMap = $slidesRows->pluck('slide_number', 'id');
            $alignRows = DB::table('slide_alignments')
                ->where('lecture_id', $lectureId)
                ->where('start_time_sec', '>', 0)
                ->get();
            foreach ($alignRows as $ar) {
                $p = (int) ($slideNumMap[$ar->slide_id] ?? 0);
                if ($p > 0 && !isset($slideTimestamps[$p])) {
                    $slideTimestamps[$p] = (int) floor((float) $ar->start_time_sec);
                }
            }
            if (empty($slideTimestamps)) {
                $segRows = DB::table('transcript_segments')
                    ->where('lecture_id', $lectureId)
                    ->whereNotNull('aligned_slide_id')
                    ->where('start_time_sec', '>', 0)
                    ->get();
                foreach ($segRows as $sg) {
                    $p = (int) ($slideNumMap[$sg->aligned_slide_id] ?? 0);
                    if ($p > 0) {
                        $sec = (int) floor((float) $sg->start_time_sec);
                        $slideTimestamps[$p] = isset($slideTimestamps[$p]) ? min($slideTimestamps[$p], $sec) : $sec;
                    }
                }
            }
        }

        $rawTitle = trim((string) ($lecture->title ?: "Bài giảng #{$lectureId}"));
        $rawTitle = preg_replace('/\.(pdf|pptx?)$/iu', '', $rawTitle) ?: $rawTitle;
        $branchTitle = preg_match('/^Bài\s*#?\d+/iu', $rawTitle)
            ? $rawTitle
            : "Bài #{$lectureId} • {$rawTitle}";

        return $this->upsertMindmapBranchFromBlocks(
            $courseId,
            $lectureId,
            null,
            $branchTitle,
            $blocks,
            $isNewlyAdded,
            $slideTimestamps
        );
    }

    public function getCourseMindmap(int $courseId)
    {
        $course = DB::table('courses')->where('id', $courseId)->first();
        if (!$course) {
            return response()->json(['error' => 'Course not found'], 404);
        }

        if (!Schema::hasTable('course_mindmap_branches') || !Schema::hasTable('course_mindmap_leaves')) {
            return response()->json([
                'course_id' => $courseId,
                'course_name' => (string) $course->name,
                'branches' => [],
            ]);
        }

        // Đồng bộ lần đầu mọi bài giảng / kho slide chưa có nhánh trong Mindmap (không bật cờ MỚI cho dữ liệu cũ)
        $existingKeys = DB::table('course_mindmap_branches')
            ->where('course_id', $courseId)
            ->pluck('branch_key')
            ->flip();

        $lectures = DB::table('lectures')
            ->where('course_id', $courseId)
            ->where('status', '!=', 'failed')
            ->orderBy('id')
            ->get();

        $syncedNormTitles = [];
        foreach ($lectures as $lec) {
            $syncedNormTitles[$this->normalizeMindmapBranchBase((string) $lec->title)] = true;
            if (!$existingKeys->has("lecture_{$lec->id}")) {
                $this->syncLectureToMindmap($courseId, (int) $lec->id, false);
            }
        }

        $vaults = DB::table('course_slide_vaults')
            ->where('course_id', $courseId)
            ->orderBy('id')
            ->get();
        foreach ($vaults as $v) {
            $baseTitle = preg_replace('/\.(pdf|pptx?)$/iu', '', (string) $v->file_name) ?: (string) $v->file_name;
            $normVault = $this->normalizeMindmapBranchBase($baseTitle);
            if (isset($syncedNormTitles[$normVault])) {
                continue;
            }
            if (!$existingKeys->has("vault_{$v->id}")) {
                $blocks = json_decode($v->parsed_blocks_json ?? '[]', true);
                $this->upsertMindmapBranchFromBlocks(
                    $courseId,
                    null,
                    (int) $v->id,
                    $baseTitle,
                    is_array($blocks) ? $blocks : [],
                    false
                );
            }
        }

        $branches = DB::table('course_mindmap_branches')
            ->where('course_id', $courseId)
            ->orderBy('sort_order')
            ->orderBy('id')
            ->get();

        $leavesByBranch = DB::table('course_mindmap_leaves')
            ->where('course_id', $courseId)
            ->orderBy('sort_order')
            ->orderBy('id')
            ->get()
            ->groupBy('branch_id');

        $payloadBranches = $branches->map(function ($br) use ($leavesByBranch) {
            $leaves = ($leavesByBranch->get($br->id) ?? collect())->map(function ($lf) use ($br) {
                return [
                    'id' => (string) $lf->id,
                    'title' => (string) $lf->title,
                    'slide_ref' => (string) $lf->slide_ref,
                    'lecture_id' => (int) ($lf->lecture_id ?? $br->lecture_id ?? 0),
                    'slide_number' => (int) $lf->slide_number,
                    'timestamp_sec' => (int) $lf->timestamp_sec,
                    'summary' => (string) ($lf->summary ?? ''),
                    'is_mastered' => (bool) $lf->is_mastered,
                ];
            })->values();

            return [
                'id' => (string) $br->id,
                'lecture_id' => (int) ($br->lecture_id ?? 0),
                'vault_slide_id' => $br->vault_slide_id ? (int) $br->vault_slide_id : null,
                'title' => (string) $br->title,
                'tone' => (string) ($br->tone ?: 'TEAL'),
                'is_newly_added' => (bool) $br->is_newly_added,
                'leaves' => $leaves,
            ];
        })->values();

        return response()->json([
            'course_id' => $courseId,
            'course_name' => (string) $course->name,
            'branches' => $payloadBranches,
        ]);
    }

    public function appendSlideToMindmap(Request $request, int $courseId)
    {
        $course = DB::table('courses')->where('id', $courseId)->first();
        if (!$course) {
            return response()->json(['error' => 'Course not found'], 404);
        }

        $validated = $request->validate([
            'lecture_id' => 'nullable|integer',
            'vault_slide_id' => 'nullable|integer',
            'branch_title' => 'required|string|max:220',
            'concepts' => 'nullable|array',
            'blocks' => 'nullable|array',
        ]);

        $lectureId = isset($validated['lecture_id']) ? (int) $validated['lecture_id'] : null;
        $vaultId = isset($validated['vault_slide_id']) ? (int) $validated['vault_slide_id'] : null;

        if (!empty($validated['concepts'])) {
            $concepts = [];
            foreach ($validated['concepts'] as $c) {
                if (!is_array($c)) {
                    continue;
                }
                $sNum = max(1, (int) ($c['slide_number'] ?? 1));
                $concepts[] = [
                    'title' => (string) ($c['title'] ?? "Slide {$sNum}"),
                    'slide_number' => $sNum,
                    'slide_ref' => (string) ($c['slide_ref'] ?? "Slide {$sNum}"),
                    'timestamp_sec' => max(0, (int) ($c['timestamp_sec'] ?? 0)),
                    'summary' => (string) ($c['summary'] ?? ''),
                ];
            }
            $branchId = $this->upsertMindmapBranchConcepts(
                $courseId,
                $lectureId,
                $vaultId,
                (string) $validated['branch_title'],
                $concepts,
                true
            );
        } else {
            $branchId = $this->upsertMindmapBranchFromBlocks(
                $courseId,
                $lectureId,
                $vaultId,
                (string) $validated['branch_title'],
                $validated['blocks'] ?? [],
                true
            );
        }

        return response()->json([
            'status' => 'appended',
            'course_id' => $courseId,
            'branch_id' => $branchId ? (string) $branchId : null,
        ], 201);
    }

    public function markMindmapBranchSeen(int $branchId)
    {
        if (!Schema::hasTable('course_mindmap_branches')) {
            return response()->json(['error' => 'Mindmap table missing'], 404);
        }
        $branch = DB::table('course_mindmap_branches')->where('id', $branchId)->first();
        if (!$branch) {
            return response()->json(['error' => 'Branch not found'], 404);
        }

        DB::table('course_mindmap_branches')
            ->where('id', $branchId)
            ->update([
                'is_newly_added' => false,
                'updated_at' => now(),
            ]);

        return response()->json([
            'id' => (string) $branchId,
            'is_newly_added' => false,
        ]);
    }

    public function toggleMindmapLeafMastery(int $leafId)
    {
        if (!Schema::hasTable('course_mindmap_leaves')) {
            return response()->json(['error' => 'Mindmap table missing'], 404);
        }
        $leaf = DB::table('course_mindmap_leaves')->where('id', $leafId)->first();
        if (!$leaf) {
            return response()->json(['error' => 'Leaf not found'], 404);
        }

        $nextMastered = !((bool) $leaf->is_mastered);
        DB::table('course_mindmap_leaves')
            ->where('id', $leafId)
            ->update([
                'is_mastered' => $nextMastered,
                'updated_at' => now(),
            ]);

        return response()->json([
            'id' => (string) $leafId,
            'is_mastered' => $nextMastered,
        ]);
    }
}
