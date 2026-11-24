package com.studyai.data.api

import com.studyai.data.model.*
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.*

interface StudyAiApiService {

    // =========================================================================
    // 1. Quản lý Môn học (Course CRUD)
    // =========================================================================
    @GET("api/v1/courses")
    suspend fun getCourses(): Response<CoursesResponse>

    @POST("api/v1/courses")
    suspend fun createCourse(
        @Body request: CourseCreateUpdateRequest
    ): Response<CourseItemDto>

    @PUT("api/v1/courses/{courseId}")
    suspend fun updateCourse(
        @Path("courseId") courseId: Long,
        @Body request: CourseCreateUpdateRequest
    ): Response<CourseItemDto>

    @DELETE("api/v1/courses/{courseId}")
    suspend fun deleteCourse(
        @Path("courseId") courseId: Long
    ): Response<Map<String, Any>>

    // =========================================================================
    // 2. Kho lưu trữ Slide theo Môn (Course Slide Vault)
    // =========================================================================
    @GET("api/v1/courses/{courseId}/slide-vault")
    suspend fun getSlideVault(
        @Path("courseId") courseId: Long
    ): Response<SlideVaultResponse>

    @Multipart
    @POST("api/v1/courses/{courseId}/slide-vault")
    suspend fun uploadSlideToVault(
        @Path("courseId") courseId: Long,
        @Part slideFile: MultipartBody.Part
    ): Response<VaultUploadResponse>

    // =========================================================================
    // 3. Bài giảng bám Slide (Live Following & Vở ghi chép Inline)
    // =========================================================================
    @POST("api/v1/lectures/start-live")
    suspend fun startLiveLecture(
        @Body request: StartLiveLectureRequest
    ): Response<StartLiveLectureResponse>

    @Multipart
    @POST("api/v1/lectures/{lectureId}/live-chunk")
    suspend fun uploadLiveAudioChunk(
        @Path("lectureId") lectureId: Long,
        @Part audioChunk: MultipartBody.Part,
        @Part("chunk_start_sec") chunkStartSec: RequestBody?,
        @Part("language") language: RequestBody?
    ): Response<LiveChunkResponse>

    @POST("api/v1/lectures/{lectureId}/blocks/{blockId}/toggle-confused")
    suspend fun toggleConfusedBlock(
        @Path("lectureId") lectureId: Long,
        @Path("blockId") blockId: String
    ): Response<ToggleConfusedResponse>

    @Multipart
    @POST("api/v1/courses/{courseId}/lectures/upload")
    suspend fun uploadLecture(
        @Path("courseId") courseId: Long,
        @Part("title") title: RequestBody,
        @Part("language") language: RequestBody,
        @Part("slide_file") slideFile: MultipartBody.Part,
        @Part("audio_file") audioFile: MultipartBody.Part,
        @Part("slide_clicks_json") slideClicksJson: RequestBody? = null
    ): Response<LectureUploadResponse>

    @GET("api/v1/lectures/{lectureId}/status")
    suspend fun getLectureStatus(
        @Path("lectureId") lectureId: Long
    ): Response<LectureStatusResponse>

    @Multipart
    @POST("api/v1/lectures/upload-pair")
    suspend fun uploadLecturePair(
        @Part("course_id") courseId: RequestBody,
        @Part("title") title: RequestBody?,
        @Part("language") language: RequestBody?,
        @Part slideFile: MultipartBody.Part,
        @Part audioFile: MultipartBody.Part
    ): Response<LectureInlineNotesResponse>

    @GET("api/v1/lectures/{lectureId}/inline-notes")
    suspend fun getLectureInlineNotes(
        @Path("lectureId") lectureId: Long
    ): Response<LectureInlineNotesResponse>

    @PATCH("api/v1/highlights/{highlightId}/select-context")
    suspend fun selectHighlightContext(
        @Path("highlightId") highlightId: Long,
        @Body request: SelectContextRequest
    ): Response<SelectContextResponse>

    // =========================================================================
    // 4. Các endpoint phục vụ 3 màn hình còn lại (Workspace, Graph, Dictionary)
    // =========================================================================
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

    @GET("api/v1/concepts/{conceptId}/dictionary")
    suspend fun getConceptDictionary(
        @Path("conceptId") conceptId: Long
    ): Response<DictionaryEntryDetailResponse>

    @GET("api/v1/courses/{courseId}/dictionary")
    suspend fun getCourseDictionary(
        @Path("courseId") courseId: Long
    ): Response<CourseDictionaryListResponse>
}
