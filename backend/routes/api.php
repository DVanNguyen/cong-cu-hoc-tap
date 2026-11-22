<?php

use App\Http\Controllers\AuthController;
use App\Http\Controllers\WorkspaceController;
use App\Http\Controllers\EventController;
use App\Http\Controllers\TaskController;
use App\Http\Controllers\UserSearchController;
use App\Http\Controllers\Api\PageController;
use App\Http\Controllers\Api\BlockController;
use App\Http\Controllers\GroupController;
use App\Http\Controllers\MessageController;
use App\Http\Controllers\NotificationController;
use App\Http\Controllers\Api\AiController;
use Illuminate\Support\Facades\Route;

/*
|--------------------------------------------------------------------------
| FocusBoard API Routes v5.0
| Base URL: http://localhost:8000/api
|--------------------------------------------------------------------------
*/

// ── Auth — Public Routes with Rate Limiting ────────────────────────────────────
Route::post('auth/register', [AuthController::class, 'register'])->middleware('throttle:10,1');
Route::post('auth/login', [AuthController::class, 'login'])->middleware('throttle:5,1');
Route::get('files/download/{id}', [\App\Http\Controllers\FileController::class, 'download']);

// ── Cần xác thực token Sanctum ───────────────────────────────
Route::middleware('auth:sanctum')->group(function () {

    // Auth
    Route::get('auth/me',      [AuthController::class, 'me']);
    Route::get('auth/profile', [AuthController::class, 'me']);
    Route::post('auth/logout', [AuthController::class, 'logout']);
    Route::post('auth/change-password', [AuthController::class, 'changePassword']);

    // ── User Search ────────────────────────────────────────
    Route::get('users/search', [UserSearchController::class, 'search']);
    Route::get('users/{id}',   [UserSearchController::class, 'show']);

    // ── Workspace ─────────────────────────────────────────
    Route::get   ('workspaces',        [WorkspaceController::class, 'index']);
    Route::post  ('workspaces',        [WorkspaceController::class, 'store']);
    Route::get   ('workspaces/{workspace}',   [WorkspaceController::class, 'show']);
    Route::put   ('workspaces/{workspace}',   [WorkspaceController::class, 'update']);
    Route::delete('workspaces/{workspace}',   [WorkspaceController::class, 'destroy']);
    // Batch sync — SyncWorker gọi endpoint này
    Route::post  ('workspaces/sync',   [WorkspaceController::class, 'sync']);

    // ── Page ──────────────────────────────────────────────
    Route::get   ('pages/all',                      [PageController::class, 'all']);
    Route::get   ('workspaces/{workspaceId}/pages', [PageController::class, 'index']);
    Route::post  ('workspaces/{workspaceId}/pages', [PageController::class, 'store']);
    Route::put   ('pages/{id}',                     [PageController::class, 'update']);
    Route::delete('pages/{id}',                     [PageController::class, 'destroy']);
    Route::post  ('pages/sync',                     [PageController::class, 'sync']);

    // ── Block ─────────────────────────────────────────────
    Route::get   ('blocks/all',                     [BlockController::class, 'all']);
    Route::get   ('pages/{pageId}/blocks',          [BlockController::class, 'index']);
    Route::post  ('pages/{pageId}/blocks',          [BlockController::class, 'store']);
    Route::put   ('pages/{pageId}/blocks/reorder',  [BlockController::class, 'reorder']);
    Route::put   ('blocks/{id}',                    [BlockController::class, 'update']);
    Route::delete('blocks/{id}',                    [BlockController::class, 'destroy']);
    Route::post  ('blocks/sync',                    [BlockController::class, 'sync']);

    // ── File ──────────────────────────────────────────────
    Route::get   ('files/all',                      [\App\Http\Controllers\FileController::class, 'all']);
    Route::get   ('workspaces/{workspaceId}/files', [\App\Http\Controllers\FileController::class, 'index']);
    Route::post  ('workspaces/{workspaceId}/files', [\App\Http\Controllers\FileController::class, 'store']);
    Route::delete('files/{id}',                     [\App\Http\Controllers\FileController::class, 'destroy']);

    // ── Event (Schedule) ───────────────────────────────────
    Route::get   ('events',              [EventController::class, 'index']);
    Route::post  ('events',              [EventController::class, 'store']);
    Route::get   ('events/{event}',         [EventController::class, 'show']);
    Route::put   ('events/{event}',         [EventController::class, 'update']);
    Route::delete('events/{event}',         [EventController::class, 'destroy']);
    Route::post  ('events/sync',         [EventController::class, 'sync']);

    // ── Tasks ───────────────────────────────────────────────
    Route::post  ('tasks/upload-attachment', [TaskController::class, 'uploadAttachment']);
    Route::get   ('tasks/{id}/submissions',  [TaskController::class, 'getSubmissions']);
    Route::post  ('tasks/sync',          [TaskController::class, 'sync']);
    Route::post  ('tasks/submissions',   [TaskController::class, 'submitWork']);
    Route::post  ('tasks/submissions/{id}/review', [TaskController::class, 'reviewWork']);
    Route::post  ('tasks/{id}/claim',                [TaskController::class, 'claimTask']);
    Route::apiResource('tasks', TaskController::class);

    // ── AI ──────────────────────────────────────────────────
    Route::post  ('ai/generate-subtasks', [AiController::class, 'generateSubtasks']);

    // ── Groups ────────────────────────────────────────────
    Route::get   ('groups',                     [GroupController::class, 'index']);
    Route::post  ('groups',                     [GroupController::class, 'store']);
    Route::post  ('groups/{groupId}/members',   [GroupController::class, 'addMember']);
    Route::get   ('groups/{groupId}/members',   [GroupController::class, 'members']);
    Route::delete('groups/{groupId}/members/{memberId}', [GroupController::class, 'removeMember']);
    Route::post  ('groups/{groupId}/transfer-admin', [GroupController::class, 'transferAdmin']);
    Route::delete('groups/{groupId}',           [GroupController::class, 'destroy']);

    // ── Messages & Chat Stream ────────────────────────────
    Route::get   ('groups/{groupId}/messages',         [MessageController::class, 'index']);
    Route::post  ('groups/{groupId}/messages',         [MessageController::class, 'store']);
    Route::get   ('groups/{groupId}/messages/stream',  [MessageController::class, 'stream']);

    // ── Notifications & Friends ───────────────────────────
    Route::get   ('notifications',              [NotificationController::class, 'index']);
    Route::put   ('notifications/{id}',         [NotificationController::class, 'update']);
    Route::delete('notifications/{id}',         [NotificationController::class, 'destroy']);
    Route::post  ('friends/request',            [NotificationController::class, 'sendFriendRequest']);
    Route::get   ('friends',                    [NotificationController::class, 'friends']);

    // ── StudyAI Assistant (Đồ án Cơ sở 4) ──────────────────
    Route::prefix('study-ai')->group(function () {
        // Trụ cột 3: Gom Deadline & Xếp lịch ưu tiên
        Route::post('deadlines/extract', [\App\Http\Controllers\Api\StudyAi\DeadlineController::class, 'extract']);
        Route::get ('deadlines/confirmation-queue', [\App\Http\Controllers\Api\StudyAi\DeadlineController::class, 'confirmationQueue']);
        Route::post('deadlines/{id}/confirm', [\App\Http\Controllers\Api\StudyAi\DeadlineController::class, 'confirmTask']);
        Route::get ('deadlines/ranked', [\App\Http\Controllers\Api\StudyAi\DeadlineController::class, 'rankedTasks']);

        // Trụ cột 1: Ghi chú bài giảng bám Slide
        Route::post('lectures/upload-and-parse', [\App\Http\Controllers\Api\StudyAi\LectureController::class, 'uploadAndParse']);
        Route::post('lectures/{id}/generate-notes', [\App\Http\Controllers\Api\StudyAi\LectureController::class, 'generateNotes']);
        Route::get ('lectures/{id}/notes', [\App\Http\Controllers\Api\StudyAi\LectureController::class, 'getNotes']);

        // Trụ cột 2: Đồ thị tri thức liên bài (DAG)
        Route::get ('courses/{courseId}/concept-graph', [\App\Http\Controllers\Api\StudyAi\ConceptGraphController::class, 'getGraph']);
        Route::post('courses/{courseId}/seed-concepts', [\App\Http\Controllers\Api\StudyAi\ConceptGraphController::class, 'seedConcepts']);
    });
});

// ── Webhooks (Public) ──────────────────────────────────────────
Route::post('webhooks/telegram', [\App\Http\Controllers\Api\StudyAi\DeadlineController::class, 'telegramWebhook']);

// ── StudyAI Unified Mobile & Evaluation APIs (v1) ─────────────
Route::prefix('v1')->group(function () {
    Route::get('courses/{courseId}/lectures', [\App\Http\Controllers\Api\StudyAiUnifiedController::class, 'getCourseLectures']);
    Route::get('lectures/{lectureId}/workspace', [\App\Http\Controllers\Api\StudyAiUnifiedController::class, 'getLectureWorkspace']);
    Route::get('courses/{courseId}/knowledge-graph', [\App\Http\Controllers\Api\StudyAiUnifiedController::class, 'getCourseKnowledgeGraph']);
    Route::get('concepts/{conceptId}/dictionary', [\App\Http\Controllers\Api\StudyAiUnifiedController::class, 'getConceptDictionary']);
    Route::get('courses/{courseId}/dictionary', [\App\Http\Controllers\Api\StudyAiUnifiedController::class, 'getCourseDictionary']);
    Route::get('courses/{courseId}/tasks-dashboard', [\App\Http\Controllers\Api\StudyAiUnifiedController::class, 'getTasksDashboard']);
    Route::patch('milestones/{milestoneId}/toggle', [\App\Http\Controllers\Api\StudyAiUnifiedController::class, 'toggleMilestone']);
});