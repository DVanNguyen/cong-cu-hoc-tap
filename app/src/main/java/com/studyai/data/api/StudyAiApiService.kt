package com.studyai.data.api

import com.studyai.data.model.*
import retrofit2.Response
import retrofit2.http.*

interface StudyAiApiService {

    // ── Trụ cột 3: Deadlines & Scheduling ─────────────────────────
    @POST("api/study-ai/deadlines/extract")
    suspend fun extractDeadline(
        @Body request: DeadlineExtractRequest
    ): Response<DeadlineExtractResponse>

    @GET("api/study-ai/deadlines/confirmation-queue")
    suspend fun getConfirmationQueue(): Response<List<TaskModel>>

    @POST("api/study-ai/deadlines/{id}/confirm")
    suspend fun confirmTask(
        @Path("id") taskId: String,
        @Body updates: Map<String, Any>
    ): Response<Map<String, Any>>

    @GET("api/study-ai/deadlines/ranked")
    suspend fun getRankedTasks(): Response<RankedTasksResponse>

    // ── Trụ cột 1: Lecture Notes & Slide Alignment ────────────────
    @GET("api/study-ai/lectures/{id}/notes")
    suspend fun getLectureNotes(
        @Path("id") lectureId: Long
    ): Response<LectureNotesResponse>

    // ── Trụ cột 2: Knowledge Graph ────────────────────────────────
    @GET("api/study-ai/courses/{courseId}/concept-graph")
    suspend fun getConceptGraph(
        @Path("courseId") courseId: Long
    ): Response<ConceptGraphResponse>

    // ── Unified Endpoints (v1) phục vụ 3 Màn hình Chính ────────────
    @GET("api/v1/courses/{courseId}/lectures")
    suspend fun getCourseLectures(
        @Path("courseId") courseId: Long
    ): Response<CourseLecturesResponse>

    @GET("api/v1/lectures/{lectureId}/workspace")
    suspend fun getLectureWorkspace(
        @Path("lectureId") lectureId: Long
    ): Response<LectureWorkspaceResponse>

    @GET("api/v1/courses/{courseId}/knowledge-graph")
    suspend fun getCourseKnowledgeGraph(
        @Path("courseId") courseId: Long
    ): Response<CourseKnowledgeGraphResponse>

    @GET("api/v1/courses/{courseId}/tasks-dashboard")
    suspend fun getTasksDashboard(
        @Path("courseId") courseId: Long
    ): Response<TasksDashboardResponse>

    @PATCH("api/v1/milestones/{milestoneId}/toggle")
    suspend fun toggleMilestone(
        @Path("milestoneId") milestoneId: Long
    ): Response<Map<String, Any>>

    // ── Trụ cột 2: Từ điển sống (Living Dictionary) & Mở rộng Tham khảo ──
    @GET("api/v1/concepts/{conceptId}/dictionary")
    suspend fun getConceptDictionary(
        @Path("conceptId") conceptId: Long
    ): Response<DictionaryEntryDetailResponse>

    @GET("api/v1/courses/{courseId}/dictionary")
    suspend fun getCourseDictionary(
        @Path("courseId") courseId: Long
    ): Response<CourseDictionaryListResponse>
}
