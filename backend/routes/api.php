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
    Route::post('lectures/{lectureId}/blocks/{blockId}/toggle-confused', [StudyAiUnifiedController::class, 'toggleConfusedBlock']);
    Route::post('lectures/upload-pair', [StudyAiUnifiedController::class, 'uploadLecturePair']);
    Route::get('lectures/{lectureId}/inline-notes', [StudyAiUnifiedController::class, 'getLectureInlineNotes']);
    Route::patch('highlights/{highlightId}/select-context', [StudyAiUnifiedController::class, 'selectHighlightContext']);

    // =========================================================================
    // 5. Các endpoint hiện hữu (Chương trình học, Đồ thị tri thức & Từ điển sống)
    // =========================================================================
    Route::get('courses/{courseId}/lectures', [StudyAiUnifiedController::class, 'getCourseLectures']);
    Route::get('lectures/{lectureId}/workspace', [StudyAiUnifiedController::class, 'getLectureWorkspace']);
    Route::get('courses/{courseId}/knowledge-graph', [StudyAiUnifiedController::class, 'getCourseKnowledgeGraph']);
    Route::get('concepts/{conceptId}/dictionary', [StudyAiUnifiedController::class, 'getConceptDictionary']);
    Route::get('courses/{courseId}/dictionary', [StudyAiUnifiedController::class, 'getCourseDictionary']);
    Route::get('courses/{courseId}/tasks-dashboard', [StudyAiUnifiedController::class, 'getTasksDashboard']);
});
