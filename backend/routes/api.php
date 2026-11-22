<?php

use App\Http\Controllers\Api\StudyAiUnifiedController;
use Illuminate\Support\Facades\Route;

/*
|--------------------------------------------------------------------------
| StudyAI API Routes v1.0
| Monorepo Backend: C:\Users\Nguyen\AndroidStudioProjects\StudyAI\backend
| Database: studyai_db (MySQL 9.1)
|--------------------------------------------------------------------------
*/

Route::prefix('v1')->group(function () {
    // 1. Trụ cột 1: Ghi chú bài giảng bám Slide & Danh sách chương
    Route::get('courses/{courseId}/lectures', [StudyAiUnifiedController::class, 'getCourseLectures']);
    Route::get('lectures/{lectureId}/workspace', [StudyAiUnifiedController::class, 'getLectureWorkspace']);

    // 2. Trụ cột 2: Hệ thống hoá kiến thức / Đồ thị tri thức liên bài (DAG)
    Route::get('courses/{courseId}/knowledge-graph', [StudyAiUnifiedController::class, 'getCourseKnowledgeGraph']);

    // 3. Trụ cột 3: Từ điển sống & Fallback học thuật quốc tế
    Route::get('concepts/{conceptId}/dictionary', [StudyAiUnifiedController::class, 'getConceptDictionary']);
    Route::get('courses/{courseId}/dictionary', [StudyAiUnifiedController::class, 'getCourseDictionary']);

    // Tương thích ngược Dashboard
    Route::get('courses/{courseId}/tasks-dashboard', [StudyAiUnifiedController::class, 'getTasksDashboard']);
});
