<?php

namespace App\Http\Controllers\Api\StudyAi;

use App\Http\Controllers\Controller;
use App\Models\Course;
use App\Models\Lecture;
use App\Models\Slide;
use App\Models\NoteItem;
use App\Services\AiServiceClient;
use Illuminate\Http\Request;

class LectureController extends Controller
{
    protected AiServiceClient $aiService;

    public function __construct(AiServiceClient $aiService)
    {
        $this->aiService = $aiService;
    }

    /**
     * Tạo bài giảng và trích xuất slide PDF
     */
    public function uploadAndParse(Request $request)
    {
        $request->validate([
            'course_id' => 'required|exists:courses,id',
            'title' => 'required|string',
            'pdf_path' => 'required|string',
            'audio_path' => 'nullable|string',
        ]);

        $lecture = Lecture::create([
            'course_id' => $request->input('course_id'),
            'title' => $request->input('title'),
            'slide_path' => $request->input('pdf_path'),
            'audio_path' => $request->input('audio_path'),
            'status' => 'processing_asr',
        ]);

        // Trích xuất slide PDF qua Python AI Service
        $slidesData = $this->aiService->parseSlides($request->input('pdf_path'));

        foreach ($slidesData as $s) {
            Slide::create([
                'lecture_id' => $lecture->id,
                'page_number' => $s['page_number'],
                'slide_text' => $s['text'],
            ]);
        }

        $lecture->update(['status' => 'aligning_slides']);

        return response()->json([
            'message' => 'Đã trích xuất xong nội dung slide bài giảng',
            'lecture_id' => $lecture->id,
            'total_slides' => count($slidesData),
        ]);
    }

    /**
     * Căn lời giảng với slide và sinh ghi chú bổ sung
     */
    public function generateNotes(Request $request, $id)
    {
        $lecture = Lecture::with('slides')->findOrFail($id);
        $transcriptSegments = $request->input('transcript_segments', []);
        $clicks = $request->input('user_slide_clicks');

        $slidesPayload = $lecture->slides->map(function ($s) {
            return [
                'page_number' => $s->page_number,
                'text' => $s->slide_text ?? '',
            ];
        })->toArray();

        // Gọi AI Service để căn DTW và tính Novelty Score
        $alignedNotes = $this->aiService->alignLecture($transcriptSegments, $slidesPayload, $clicks);

        // Lưu vào note_items
        $slideMap = $lecture->slides->keyBy('page_number');
        $createdCount = 0;

        foreach ($alignedNotes as $note) {
            $pageNum = $note['slide_page'];
            if (isset($slideMap[$pageNum])) {
                NoteItem::create([
                    'slide_id' => $slideMap[$pageNum]->id,
                    'content' => $note['content'],
                    'timestamp_start_sec' => (int) $note['timestamp_start_sec'],
                    'timestamp_end_sec' => (int) $note['timestamp_end_sec'],
                    'type' => $note['type'],
                    'confidence' => 1.0,
                ]);
                $createdCount++;
            }
        }

        $lecture->update(['status' => 'completed']);

        return response()->json([
            'message' => 'Đã tạo ghi chú bài giảng bám slide thành công',
            'notes_count' => $createdCount,
        ]);
    }

    /**
     * Lấy danh sách ghi chú theo từng slide của bài giảng (kèm mốc thời gian audio)
     */
    public function getNotes($id)
    {
        $lecture = Lecture::with(['slides.noteItems'])->findOrFail($id);

        return response()->json([
            'lecture' => [
                'id' => $lecture->id,
                'title' => $lecture->title,
                'status' => $lecture->status,
                'audio_path' => $lecture->audio_path,
                'slide_path' => $lecture->slide_path,
            ],
            'slides' => $lecture->slides->map(function ($s) {
                return [
                    'page_number' => $s->page_number,
                    'slide_text' => $s->slide_text,
                    'notes' => $s->noteItems->map(function ($n) {
                        return [
                            'id' => $n->id,
                            'content' => $n->content,
                            'timestamp_start' => $n->timestamp_start_sec,
                            'timestamp_end' => $n->timestamp_end_sec,
                            'type' => $n->type, // addition, term, exam_hint
                        ];
                    }),
                ];
            }),
        ]);
    }
}
