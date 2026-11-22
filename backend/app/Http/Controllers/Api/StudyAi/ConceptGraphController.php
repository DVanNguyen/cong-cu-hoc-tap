<?php

namespace App\Http\Controllers\Api\StudyAi;

use App\Http\Controllers\Controller;
use App\Models\Course;
use App\Models\Concept;
use App\Models\ConceptEdge;
use App\Services\AiServiceClient;
use Illuminate\Http\Request;

class ConceptGraphController extends Controller
{
    protected AiServiceClient $aiService;

    public function __construct(AiServiceClient $aiService)
    {
        $this->aiService = $aiService;
    }

    /**
     * Lấy dữ liệu đồ thị tri thức của môn học kèm thứ tự học Topo và cảnh báo
     */
    public function getGraph($courseId)
    {
        $course = Course::with(['concepts', 'conceptEdges.fromConcept', 'conceptEdges.toConcept'])->findOrFail($courseId);

        $conceptsPayload = $course->concepts->map(function ($c) {
            return [
                'id' => (string) $c->id,
                'name' => $c->name,
                'aliases' => $c->aliases ?? [],
                'definition' => $c->definition ?? '',
                'defined_in_lecture_idx' => $c->first_defined_lecture_id ?? 1,
            ];
        })->toArray();

        $edgesPayload = $course->conceptEdges->map(function ($e) {
            return [
                'from_concept' => $e->fromConcept->name,
                'to_concept' => $e->toConcept->name,
                'relation' => $e->relation,
                'evidence_quote' => $e->evidence_quote ?? '',
                'mentioned_in_lecture_idx' => $e->evidence_lecture_id ?? 1,
            ];
        })->toArray();

        // Kiểm tra DAG và lấy thứ tự học
        $analysis = $this->aiService->validateGraph($conceptsPayload, $edgesPayload);

        return response()->json([
            'course' => [
                'id' => $course->id,
                'name' => $course->name,
                'code' => $course->code,
            ],
            'nodes' => $conceptsPayload,
            'edges' => $edgesPayload,
            'analysis' => $analysis,
        ]);
    }

    /**
     * Nạp nhanh các khái niệm và cạnh từ dữ liệu cấu hình
     */
    public function seedConcepts(Request $request, $courseId)
    {
        $course = Course::findOrFail($courseId);
        $conceptsData = $request->input('concepts', []);
        $edgesData = $request->input('edges', []);

        $createdConcepts = [];
        foreach ($conceptsData as $c) {
            $lectureId = $c['defined_in_lecture_idx'] ?? null;
            $lectureExists = $lectureId && \App\Models\Lecture::where('id', $lectureId)->exists();

            $concept = Concept::updateOrCreate(
                ['course_id' => $course->id, 'name' => $c['name']],
                [
                    'aliases' => $c['aliases'] ?? [],
                    'definition' => $c['definition'] ?? '',
                    'first_defined_lecture_id' => $lectureExists ? $lectureId : null,
                ]
            );
            $createdConcepts[$c['name']] = $concept;
        }

        $createdEdgesCount = 0;
        foreach ($edgesData as $e) {
            if (isset($createdConcepts[$e['from_concept']]) && isset($createdConcepts[$e['to_concept']])) {
                $evLectureId = $e['evidence_lecture_id'] ?? null;
                $evLectureExists = $evLectureId && \App\Models\Lecture::where('id', $evLectureId)->exists();

                ConceptEdge::updateOrCreate(
                    [
                        'course_id' => $course->id,
                        'from_concept_id' => $createdConcepts[$e['from_concept']]->id,
                        'to_concept_id' => $createdConcepts[$e['to_concept']]->id,
                        'relation' => $e['relation'] ?? 'prerequisite_of',
                    ],
                    [
                        'evidence_quote' => $e['evidence_quote'] ?? '',
                        'evidence_lecture_id' => $evLectureExists ? $evLectureId : null,
                    ]
                );
                $createdEdgesCount++;
            }
        }

        return response()->json([
            'message' => 'Đã nạp xong đồ thị khái niệm môn học',
            'concepts_count' => count($createdConcepts),
            'edges_count' => $createdEdgesCount,
        ]);
    }
}
