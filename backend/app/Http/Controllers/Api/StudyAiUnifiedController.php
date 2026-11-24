<?php

namespace App\Http\Controllers\Api;

use App\Http\Controllers\Controller;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Http;
use Illuminate\Support\Facades\Log;

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
            $response = Http::timeout(30)
                ->attach('audio_chunk', file_get_contents($file->getRealPath()), $file->getClientOriginalName() ?: 'chunk.m4a')
                ->post("{$aiUrl}/ai/live-align-chunk", [
                    'blocks_json' => json_encode($blocksPayload),
                    'current_slide_page' => 1,
                    'language' => $language,
                    'course_id' => $lecture->course_id,
                    'chunk_start_sec' => $startSec,
                    'previous_concepts_json' => json_encode($prevConcepts),
                ]);

            if ($response->successful()) {
                $data = $response->json();

                if (!empty($data['inline_note']) && in_array($data['action'], ['attach_inline_note', 'general_note'])) {
                    $n = $data['inline_note'];
                    DB::table('inline_block_notes')->insert([
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
                        ->update(['last_spoken_sec' => $startSec]);
                }

                return response()->json($data);
            }
        } catch (\Exception $e) {
            Log::warning('AI live-align-chunk error: ' . $e->getMessage());
        }

        return response()->json([
            'matched_slide_page' => 1,
            'matched_block_id' => null,
            'action' => 'highlight_only',
            'inline_note' => null,
            'new_highlights' => [],
            'jumped_non_linear' => false,
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

        $highlights = DB::table('block_concept_highlights')
            ->where('lecture_id', $lectureId)
            ->get()
            ->groupBy('block_id');

        $byPage = $blocks->groupBy('slide_page');
        $pages = [];

        foreach ($byPage as $pageNum => $bList) {
            $firstBlock = $bList->first();
            $pageTitle = ($firstBlock && $firstBlock->kind === 'heading')
                ? $firstBlock->original_slide_text
                : "Trang {$pageNum}";

            $pageBlocks = $bList->map(function ($b) use ($inlineNotes, $highlights) {
                $notes = ($inlineNotes->get($b->block_id) ?? collect())->map(function ($n) {
                    $min = floor($n->timestamp_start_sec / 60);
                    $sec = floor($n->timestamp_start_sec % 60);
                    return [
                        'id' => (string) $n->id,
                        'explanationText' => $n->explanation_text,
                        'timestampSec' => (float) $n->timestamp_start_sec,
                        'timestampLabel' => sprintf('%02d:%02d', $min, $sec),
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

            $pages[] = [
                'pageNumber' => (int) $pageNum,
                'slideTitle' => $pageTitle,
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
}
