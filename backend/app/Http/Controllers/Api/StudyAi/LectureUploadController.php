<?php

namespace App\Http\Controllers\Api\StudyAi;

use App\Http\Controllers\Controller;
use App\Jobs\ProcessLectureJob;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\File;
use Illuminate\Support\Facades\Log;

class LectureUploadController extends Controller
{
    /**
     * POST /api/v1/courses/{courseId}/lectures/upload
     */
    public function upload(Request $request, int $courseId)
    {
        $course = DB::table('courses')->where('id', $courseId)->first();
        if (!$course) {
            return response()->json(['error' => 'Course not found'], 404);
        }

        $validated = $request->validate([
            'title' => 'required|string|max:200',
            'language' => 'nullable|in:auto,vi,en',
            'slide_file' => 'required|file|mimes:pdf,pptx|max:51200',
            'audio_file' => 'required|file|mimes:m4a,mp3,wav,aac,ogg,mp4|max:122880',
            'slide_clicks_json' => 'nullable|string',
        ]);

        $title = $validated['title'];
        $language = $validated['language'] ?? 'auto';

        // 1. Tạo bản ghi lecture
        $lectureId = DB::table('lectures')->insertGetId([
            'course_id' => $courseId,
            'title' => $title,
            'language_mode' => $language,
            'processing_status' => 'queued',
            'progress_percent' => 5,
            'status' => 'processing',
            'created_at' => now(),
            'updated_at' => now(),
        ]);

        // 2. Lưu tệp slide và audio vào thư mục riêng theo lecture_id
        $slideFile = $request->file('slide_file');
        $audioFile = $request->file('audio_file');

        $storageDir = storage_path("app/private/lectures/{$lectureId}");
        File::ensureDirectoryExists($storageDir);

        $slideName = 'slide_' . time() . '.' . $slideFile->getClientOriginalExtension();
        $audioName = 'audio_' . time() . '.' . $audioFile->getClientOriginalExtension();

        $slideFile->move($storageDir, $slideName);
        $audioFile->move($storageDir, $audioName);

        $slideAbsPath = $storageDir . DIRECTORY_SEPARATOR . $slideName;
        $audioAbsPath = $storageDir . DIRECTORY_SEPARATOR . $audioName;

        // Cập nhật đường dẫn tuyệt đối vào database
        DB::table('lectures')->where('id', $lectureId)->update([
            'slide_file_path' => $slideAbsPath,
            'audio_file_path' => $audioAbsPath,
            'slide_path' => $slideAbsPath,
            'audio_path' => $audioAbsPath,
            'updated_at' => now(),
        ]);

        // 3. Giải mã slide_clicks_json nếu có
        $slideClicks = null;
        if (!empty($validated['slide_clicks_json'])) {
            $slideClicks = json_decode($validated['slide_clicks_json'], true);
        }

        // 4. Đẩy job vào Laravel Queue
        ProcessLectureJob::dispatch($lectureId, $slideClicks);

        // 5. Trả về 202 Accepted ngay lập tức
        return response()->json([
            'lecture_id' => $lectureId,
            'processing_status' => 'queued',
            'progress_percent' => 5,
        ], 202);
    }

    /**
     * GET /api/v1/lectures/{lectureId}/status
     */
    public function status(int $lectureId)
    {
        $lecture = DB::table('lectures')->where('id', $lectureId)->first();
        if (!$lecture) {
            return response()->json(['error' => 'Lecture not found'], 404);
        }

        return response()->json([
            'lecture_id' => (int) $lecture->id,
            'processing_status' => (string) ($lecture->processing_status ?? 'queued'),
            'progress_percent' => (int) ($lecture->progress_percent ?? 0),
            'error_message' => $lecture->error_message ? (string) $lecture->error_message : null,
        ]);
    }

    /**
     * POST /api/v1/internal/lectures/{lectureId}/progress
     */
    public function updateProgress(Request $request, int $lectureId)
    {
        $expectedSecret = env('LARAVEL_WEBHOOK_SECRET', 'studyai_secret_token_2026');
        $secretHeader = $request->header('X-Webhook-Secret') ?: $request->input('webhook_secret');

        if ($secretHeader !== $expectedSecret) {
            return response()->json(['error' => 'Unauthorized webhook'], 401);
        }

        $lecture = DB::table('lectures')->where('id', $lectureId)->first();
        if (!$lecture) {
            return response()->json(['error' => 'Lecture not found'], 404);
        }

        $validated = $request->validate([
            'processing_status' => 'required|string',
            'progress_percent' => 'required|integer|min:0|max:100',
            'error_message' => 'nullable|string',
        ]);

        $updates = [
            'processing_status' => $validated['processing_status'],
            'progress_percent' => $validated['progress_percent'],
            'updated_at' => now(),
        ];

        if (isset($validated['error_message'])) {
            $updates['error_message'] = $validated['error_message'];
        }

        if ($validated['progress_percent'] >= 100) {
            $updates['status'] = 'completed';
        }

        DB::table('lectures')->where('id', $lectureId)->update($updates);

        return response()->json(['ok' => true]);
    }
}
