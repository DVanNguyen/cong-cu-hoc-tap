<?php

use App\Http\Controllers\Api\StudyAiUnifiedController;
use App\Http\Controllers\Api\StudyAi\CourseController;
use App\Http\Controllers\Api\StudyAi\LectureUploadController;
use Illuminate\Support\Facades\Route;

/*
|--------------------------------------------------------------------------
| StudyAI API Routes v1.0
| Monorepo Backend: C:\Users\Nguyen\AndroidStudioProjects\StudyAI\backend
| Database: studyai_db (MySQL 9.1)
|--------------------------------------------------------------------------
*/

Route::prefix('v1')->group(function () {
    // =========================================================================
    // 1. Quản lý Môn học (Course CRUD - Chuẩn hóa Tuần 1)
    // =========================================================================
    Route::get('courses', [CourseController::class, 'index']);
    Route::post('courses', [CourseController::class, 'store']);
    Route::put('courses/{courseId}', [CourseController::class, 'update']);
    Route::delete('courses/{courseId}', [CourseController::class, 'destroy']);

    // =========================================================================
    // 2. Tải Slide + Audio & Theo dõi tiến độ xử lý hàng đợi bất đồng bộ
    // =========================================================================
    Route::post('courses/{courseId}/lectures/upload', [LectureUploadController::class, 'upload']);
    Route::get('lectures/{lectureId}/status', [LectureUploadController::class, 'status']);
    Route::post('internal/lectures/{lectureId}/progress', [LectureUploadController::class, 'updateProgress']);
    Route::post('test/voice-notes', [LectureUploadController::class, 'quickVoiceNotes']);
    Route::post('courses/{courseId}/voiceprint', [LectureUploadController::class, 'enrollVoiceprint']);
    Route::get('courses/{courseId}/voiceprint', [LectureUploadController::class, 'getVoiceprint']);

    // =========================================================================
    // 3. Kho lưu trữ Slide theo Môn (Course Slide Vault)
    // =========================================================================
    Route::get('courses/{courseId}/slide-vault', [StudyAiUnifiedController::class, 'getSlideVault']);
    Route::post('courses/{courseId}/slide-vault', [StudyAiUnifiedController::class, 'uploadSlideToVault']);

    // =========================================================================
    // 4. Bài giảng bám Slide (Live Following & Vở ghi chép Inline)
    // =========================================================================
    Route::post('lectures/start-live', [StudyAiUnifiedController::class, 'startLiveLecture']);
    Route::post('lectures/{lectureId}/live-chunk', [StudyAiUnifiedController::class, 'processLiveChunk']);
    Route::post('lectures/{lectureId}/finish-live', [StudyAiUnifiedController::class, 'finishLiveLecture']);
    Route::post('lectures/{lectureId}/blocks/{blockId}/toggle-confused', [StudyAiUnifiedController::class, 'toggleConfusedBlock']);
    Route::post('lectures/upload-pair', [StudyAiUnifiedController::class, 'uploadLecturePair']);
    Route::get('lectures/{lectureId}/inline-notes', [StudyAiUnifiedController::class, 'getLectureInlineNotes']);
    Route::patch('highlights/{highlightId}/select-context', [StudyAiUnifiedController::class, 'selectHighlightContext']);

    // Phát audio bài giảng hỗ trợ tua (HTTP 206 Partial Content) cho ExoPlayer (Tuần 4)
    Route::get('lectures/{lectureId}/audio', [StudyAiUnifiedController::class, 'streamLectureAudio']);

    // Chỉnh sửa / xóa từng ghi chú inline (đồng bộ is_edited_by_user)
    Route::put('inline-notes/{noteId}', [StudyAiUnifiedController::class, 'updateInlineNote']);
    Route::delete('inline-notes/{noteId}', [StudyAiUnifiedController::class, 'deleteInlineNote']);

    // Xuất toàn bộ vở ghi chép sạch dạng Markdown
    Route::get('lectures/{lectureId}/export-markdown', [StudyAiUnifiedController::class, 'exportLectureMarkdown']);

    // =========================================================================
    // 5. Các endpoint hiện hữu (Chương trình học, Đồ thị tri thức & Từ điển sống)
    // =========================================================================
    Route::get('courses/{courseId}/lectures', [StudyAiUnifiedController::class, 'getCourseLectures']);
    Route::get('lectures/{lectureId}/workspace', [StudyAiUnifiedController::class, 'getLectureWorkspace']);
    Route::get('courses/{courseId}/knowledge-graph', [StudyAiUnifiedController::class, 'getCourseKnowledgeGraph']);
    Route::get('concepts/{conceptId}/dictionary', [StudyAiUnifiedController::class, 'getConceptDictionary']);
    Route::get('courses/{courseId}/dictionary', [StudyAiUnifiedController::class, 'getCourseDictionary']);
    Route::get('courses/{courseId}/tasks-dashboard', [StudyAiUnifiedController::class, 'getTasksDashboard']);

    // =========================================================================
    // 6. Mindmap tri thức 3 cấp & tự động bổ sung nhánh khi nạp Slide mới
    // =========================================================================
    Route::get('courses/{courseId}/mindmap', [StudyAiUnifiedController::class, 'getCourseMindmap']);
    Route::post('courses/{courseId}/mindmap/append-slide', [StudyAiUnifiedController::class, 'appendSlideToMindmap']);
    Route::patch('mindmap/branches/{branchId}/seen', [StudyAiUnifiedController::class, 'markMindmapBranchSeen']);
    Route::patch('mindmap/leaves/{leafId}/toggle-mastery', [StudyAiUnifiedController::class, 'toggleMindmapLeafMastery']);

    // =========================================================================
    // 7. Từ điển sống toàn trình (Tra cứu bôi đen + Kho đã lưu cá nhân - Màn 4)
    // =========================================================================
    Route::post('living-dictionary/lookup', [StudyAiUnifiedController::class, 'lookupLivingTerm']);
    Route::get('courses/{courseId}/living-dictionary/entries', [StudyAiUnifiedController::class, 'getSavedLivingEntries']);
    Route::post('living-dictionary/entries', [StudyAiUnifiedController::class, 'saveLivingDictionaryEntry']);
    Route::patch('living-dictionary/entries/{entryId}/toggle-mastery', [StudyAiUnifiedController::class, 'toggleLivingEntryMastery']);
    Route::delete('living-dictionary/entries/{entryId}', [StudyAiUnifiedController::class, 'deleteLivingEntry']);
});
