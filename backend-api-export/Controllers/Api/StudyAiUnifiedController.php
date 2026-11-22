<?php

namespace App\Http\Controllers\Api;

use App\Http\Controllers\Controller;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\DB;

class StudyAiUnifiedController extends Controller
{
    /**
     * 1. API CHO MÀN HÌNH 1 (TRỤ CỘT 1 - GHI CHÚ BÀI GIẢNG BÁM SLIDE & LIÊN KẾT CHÉO)
     * GET /api/v1/lectures/{lectureId}/workspace
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

        // Lấy ghi chú bài giảng kèm bằng chứng liên kết
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

        // Lấy các liên kết chéo liên chương cho các slide trong buổi học này
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

        // Gom theo concept_id để tìm origin và các lần nhắc lại
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

                // Back link tại later slide
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

                // Forward link tại origin slide
                $crossLinksBySlide[$origin->slide_id][] = [
                    'concept_id'            => $origin->concept_id,
                    'concept_name_vi'       => $origin->concept_name_vi,
                    'concept_name_en'       => $origin->concept_name_en,
                    'link_direction'        => 'FORWARD_TO_LATER_CHAPTER',
                    'target_lecture_id'     => $later->lecture_id,
                    'target_lecture_order'  => $laterLectureOrder,
                    'target_lecture_title'  => $later->lecture_title,
                    'target_slide_id'       => $later->slide_id,
                    'target_slide_number'   => $laterSlideNo,
                    'target_timestamp_sec'  => (float) ($later->timestamp_sec ?? 0.0),
                    'badge_text'            => "Nhắc lại ở Chương {$laterLectureOrder} (Slide {$laterSlideNo}) — Bấm để chuyển tới",
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

    /**
     * 2. API CHO MÀN HÌNH 2 (TRỤ CỘT 3 - ĐỒ THỊ KHÁI NIỆM LIÊN BÀI & CẢNH BÁO TIÊN QUYẾT)
     * GET /api/v1/courses/{courseId}/knowledge-graph
     */
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

    /**
     * 3. API CHO TRỤ CỘT 2 ("TỪ ĐIỂN SỐNG & TÀI LIỆU THAM KHẢO MỞ RỘNG")
     * GET /api/v1/concepts/{conceptId}/dictionary
     */
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

        // Lấy các liên kết chéo liên chương của riêng khái niệm này
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

    /**
     * 4. API DUYỆT TOÀN BỘ "TỪ ĐIỂN SỐNG" CỦA MÔN HỌC
     * GET /api/v1/courses/{courseId}/dictionary
     */
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

    /**
     * API LẤY DANH SÁCH BÀI GIẢNG / CHƯƠNG CỦA MÔN HỌC
     * GET /api/v1/courses/{courseId}/lectures
     */
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

    /**
     * API TƯƠNG THÍCH NGƯỢC CHO TASKS DASHBOARD
     * GET /api/v1/courses/{courseId}/tasks-dashboard
     */
    public function getTasksDashboard(int $courseId)
    {
        return response()->json([
            'course_id' => $courseId,
            'summary'   => ['critical_count' => 0, 'high_count' => 0, 'total_active' => 0],
            'tasks'     => [],
        ]);
    }
}
