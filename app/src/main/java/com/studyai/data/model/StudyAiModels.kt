package com.studyai.data.model

import com.google.gson.annotations.SerializedName

// ============================================================================
// 1. DTO CHO MÔN HỌC (COURSES)
// ============================================================================
data class CoursesResponse(
    val courses: List<CourseItemDto>
)

data class CourseItemDto(
    val id: Long,
    val name: String,
    val code: String? = "",
    val lecturer: String? = "",
    @SerializedName("slide_count") val slideCount: Int = 0,
    @SerializedName("lecture_count") val lectureCount: Int = 0,
    @SerializedName("created_at") val createdAt: String? = null
)

data class CourseCreateUpdateRequest(
    val name: String,
    val code: String? = null,
    val lecturer: String? = null
)

// ============================================================================
// 2. DTO CHO KHO LƯU TRỮ SLIDE THEO MÔN (SLIDE VAULT)
// ============================================================================
data class SlideVaultResponse(
    @SerializedName("course_id") val courseId: Long,
    @SerializedName("slide_vault") val slideVault: List<VaultSlideItemDto>
)

data class VaultSlideItemDto(
    val id: String,
    val fileName: String,
    val pageCount: Int,
    @SerializedName("file_path") val filePath: String? = null,
    @SerializedName("created_at") val createdAt: String? = null
)

data class VaultUploadResponse(
    val id: String,
    @SerializedName("course_id") val courseId: Long,
    val fileName: String,
    val pageCount: Int,
    @SerializedName("file_path") val filePath: String? = null
)

// ============================================================================
// 3. DTO CHO BÀI GIẢNG VỞ GHI CHÉP INLINE & LIVE FOLLOW
// ============================================================================
data class StartLiveLectureRequest(
    @SerializedName("course_id") val courseId: Long,
    @SerializedName("vault_slide_id") val vaultSlideId: Long,
    val title: String? = null,
    val language: String? = "auto"
)

data class StartLiveLectureResponse(
    @SerializedName("lecture_id") val lectureId: Long,
    val title: String,
    val pages: List<AnnotatedSlidePageDto>
)

data class AnnotatedSlidePageDto(
    val pageNumber: Int,
    val slideTitle: String,
    val timeRangeLabel: String? = "",
    val blocks: List<SlideDocumentBlockDto>
)

data class SlideDocumentBlockDto(
    val blockId: String,
    val kind: String = "bullet",
    val originalSlideText: String,
    val isCurrentlySpoken: Boolean = false,
    val isMarkedConfused: Boolean = false,
    val inlineNotes: List<InlineLecturerNoteDto> = emptyList(),
    val highlights: List<InlineConceptHighlightDto> = emptyList()
)

data class InlineLecturerNoteDto(
    val id: String,
    val explanationText: String,
    val timestampSec: Float,
    val timestampLabel: String = "",
    val category: String? = "core_explanation",
    val evidenceQuote: String? = "",
    val isEdited: Boolean? = false
)

data class InlineConceptHighlightDto(
    val term: String,
    val sourceType: String, // "PREVIOUS_LECTURE" | "ACADEMIC_INTERNET"
    val sourceLabel: String,
    val explanation: String,
    @SerializedName("course_context_id") val courseContextId: Long? = null,
    @SerializedName("alternative_contexts") val alternativeContexts: List<Any>? = null
)

data class LiveChunkResponse(
    @SerializedName("matched_slide_page") val matchedSlidePage: Int?,
    @SerializedName("matched_block_id") val matchedBlockId: String?,
    val action: String, // "highlight_only" | "attach_inline_note" | "general_note"
    @SerializedName("inline_note") val inlineNote: InlineNoteDto? = null,
    @SerializedName("new_highlights") val newHighlights: List<InlineConceptHighlightDto>? = null,
    @SerializedName("jumped_non_linear") val jumpedNonLinear: Boolean = false,
    val transcript: String? = null
)

data class InlineNoteDto(
    val id: String? = null,
    @SerializedName("explanation_text") val explanationText: String,
    @SerializedName("raw_transcript") val rawTranscript: String? = null,
    @SerializedName("timestamp_start_sec") val timestampStartSec: Float = 0f,
    @SerializedName("timestamp_end_sec") val timestampEndSec: Float = 0f,
    @SerializedName("novelty_score") val noveltyScore: Float = 0.8f,
    @SerializedName("jumped_non_linear") val jumpedNonLinear: Boolean = false,
    val category: String? = "core_explanation"
)

data class FinishLiveLectureRequest(
    @SerializedName("refine_full_lecture") val refineFullLecture: Boolean = false
)

data class FinishLiveLectureResponse(
    @SerializedName("lecture_id") val lectureId: Long = 0L,
    val status: String = "completed",
    @SerializedName("audio_ready") val audioReady: Boolean = false,
    @SerializedName("audio_url") val audioUrl: String? = null,
    @SerializedName("chunks_merged") val chunksMerged: Int = 0,
    @SerializedName("duration_sec") val durationSec: Int = 0,
    @SerializedName("refined_queued") val refinedQueued: Boolean = false
)

data class ToggleConfusedResponse(
    @SerializedName("block_id") val blockId: String,
    @SerializedName("is_confused") val isConfused: Boolean
)

data class LectureInlineNotesResponse(
    @SerializedName("lecture_id") val lectureId: Long,
    val title: String,
    val pages: List<AnnotatedSlidePageDto>,
    @SerializedName("general_notes") val generalNotes: List<GeneralNoteDto>? = null
)

data class GeneralNoteDto(
    val id: String,
    val explanationText: String,
    val timestampSec: Float = 0f
)

data class SelectContextRequest(
    @SerializedName("course_context_id") val courseContextId: Long
)

data class SelectContextResponse(
    val id: Long,
    @SerializedName("course_context_id") val courseContextId: Long,
    val status: String
)

// ============================================================================
// 4. DTO CHO CÁC MÀN HÌNH HIỆN HỮU (WORKSPACE, GRAPH, DICTIONARY, DASHBOARD)
// ============================================================================
data class LectureWorkspaceResponse(
    val lecture: Map<String, Any?>,
    val slides: List<SlideWorkspaceDto>
)

data class SlideWorkspaceDto(
    val id: Long,
    @SerializedName("slide_number") val slideNumber: Int,
    @SerializedName("extracted_text") val extractedText: String,
    @SerializedName("image_url") val imageUrl: String?,
    @SerializedName("terms_json") val termsJson: List<String>,
    @SerializedName("start_time_sec") val startTimeSec: Float,
    @SerializedName("end_time_sec") val endTimeSec: Float,
    @SerializedName("confidence_score") val confidenceScore: Float,
    val notes: List<WorkspaceNoteDto>,
    @SerializedName("cross_chapter_links") val crossChapterLinks: List<CrossChapterLinkDto>? = emptyList()
)

data class CrossChapterLinkDto(
    @SerializedName("concept_id") val conceptId: Long,
    @SerializedName("concept_name_vi") val conceptNameVi: String,
    @SerializedName("concept_name_en") val conceptNameEn: String,
    @SerializedName("link_direction") val linkDirection: String,
    @SerializedName("target_lecture_id") val targetLectureId: Int,
    @SerializedName("target_lecture_order") val targetLectureOrder: Int,
    @SerializedName("target_lecture_title") val targetLectureTitle: String,
    @SerializedName("target_slide_number") val targetSlideNumber: Int,
    @SerializedName("target_timestamp_sec") val targetTimestampSec: Float,
    @SerializedName("badge_text") val badgeText: String
)

data class ExternalReferenceDto(
    val id: Long,
    val title: String,
    val url: String,
    @SerializedName("source_provider") val sourceProvider: String,
    @SerializedName("domain_name") val domainName: String,
    @SerializedName("snippet_summary") val snippetSummary: String,
    @SerializedName("final_rank_score") val finalRankScore: Float,
    @SerializedName("citation_badge") val citationBadge: String
)

data class DictionaryEntryDetailResponse(
    @SerializedName("concept_id") val conceptId: Long,
    @SerializedName("canonical_name_vi") val canonicalNameVi: String,
    @SerializedName("canonical_name_en") val canonicalNameEn: String,
    @SerializedName("abstractness_score") val abstractnessScore: Float,
    @SerializedName("slide_skeleton_context") val slideSkeletonContext: String?,
    @SerializedName("lecturer_oral_explanation") val lecturerOralExplanation: String?,
    @SerializedName("ai_deep_explanation") val aiDeepExplanation: String?,
    @SerializedName("intuition_example") val intuitionExample: String?,
    @SerializedName("cross_chapter_links") val crossChapterLinks: List<CrossChapterLinkDto> = emptyList(),
    @SerializedName("external_references") val externalReferences: List<ExternalReferenceDto> = emptyList()
)

data class CourseDictionaryItemDto(
    val id: Long,
    @SerializedName("canonical_name_vi") val canonicalNameVi: String,
    @SerializedName("canonical_name_en") val canonicalNameEn: String,
    val aliases: List<String>?,
    @SerializedName("definition_summary") val definitionSummary: String?,
    @SerializedName("abstractness_score") val abstractnessScore: Float,
    @SerializedName("ai_deep_explanation") val aiDeepExplanation: String?,
    @SerializedName("intuition_example") val intuitionExample: String?,
    @SerializedName("first_introduced_lecture_id") val firstIntroducedLectureId: Int?,
    @SerializedName("first_introduced_slide_no") val firstIntroducedSlideNo: Int?,
    @SerializedName("references_count") val referencesCount: Int
)

data class CourseDictionaryListResponse(
    @SerializedName("course_id") val courseId: Long,
    @SerializedName("total_entries") val totalEntries: Int,
    @SerializedName("dictionary_list") val dictionaryList: List<CourseDictionaryItemDto>
)

data class WorkspaceNoteDto(
    val id: Long,
    @SerializedName("slide_id") val slideId: Long,
    @SerializedName("bullet_point") val bulletPoint: String,
    @SerializedName("novelty_score") val noveltyScore: Float?,
    @SerializedName("is_instructor_oral_only") val isInstructorOralOnly: Boolean,
    @SerializedName("summary_type") val summaryType: String?,
    @SerializedName("verbatim_quote") val verbatimQuote: String?,
    @SerializedName("similarity_ratio") val similarityRatio: Float?,
    @SerializedName("start_time_sec") val startTimeSec: Float,
    @SerializedName("end_time_sec") val endTimeSec: Float
)

data class CourseKnowledgeGraphResponse(
    @SerializedName("course_id") val courseId: Long,
    @SerializedName("total_concepts") val totalConcepts: Int,
    @SerializedName("total_relations") val totalRelations: Int,
    @SerializedName("violations_count") val violationsCount: Int,
    val concepts: List<KnowledgeConceptDto>,
    val relations: List<KnowledgeRelationDto>
)

data class KnowledgeConceptDto(
    val id: Long,
    @SerializedName("course_id") val courseId: Long,
    @SerializedName("canonical_name_vi") val canonicalNameVi: String,
    @SerializedName("canonical_name_en") val canonicalNameEn: String,
    @SerializedName("aliases_json") val aliasesJson: List<String>,
    @SerializedName("first_introduced_lecture_id") val firstIntroducedLectureId: Int,
    @SerializedName("first_introduced_slide_no") val firstIntroducedSlideNo: Int,
    @SerializedName("defined_order_index") val definedOrderIndex: Int,
    @SerializedName("definition_summary") val definitionSummary: String,
    @SerializedName("is_used_before_defined") val isUsedBeforeDefined: Boolean
)

data class KnowledgeRelationDto(
    val id: Long,
    @SerializedName("source_concept_id") val sourceConceptId: Long,
    @SerializedName("target_concept_id") val targetConceptId: Long,
    @SerializedName("relation_type") val relationType: String,
    val weight: Float,
    @SerializedName("evidence_quote") val evidenceQuote: String,
    @SerializedName("is_used_before_defined_violation") val isUsedBeforeDefinedViolation: Boolean
)

data class TasksDashboardResponse(
    @SerializedName("course_id") val courseId: Long,
    val summary: TasksSummaryDto,
    val tasks: List<DashboardTaskDto>
)

data class TasksSummaryDto(
    @SerializedName("critical_count") val criticalCount: Int,
    @SerializedName("high_count") val highCount: Int,
    @SerializedName("total_active") val totalActive: Int
)

data class DashboardTaskDto(
    val id: String,
    @SerializedName("course_id") val courseId: Long,
    val title: String,
    val description: String,
    @SerializedName("due_at") val dueAt: String?,
    @SerializedName("due_date") val dueDate: Long?,
    @SerializedName("urgency_score") val urgencyScore: Float,
    @SerializedName("priority_level") val priorityLevel: String,
    @SerializedName("estimated_effort_hours") val estimatedEffortHours: Float,
    val status: String,
    @SerializedName("source_type") val sourceType: String,
    @SerializedName("sender_name") val senderName: String,
    @SerializedName("evidence_quote") val evidenceQuote: String,
    val milestones: List<DashboardMilestoneDto>
)

data class DashboardMilestoneDto(
    val id: Long,
    @SerializedName("milestone_title") val milestoneTitle: String,
    @SerializedName("target_date") val targetDate: String,
    @SerializedName("is_completed") val isCompleted: Boolean
)

data class CourseLecturesResponse(
    @SerializedName("course_id") val courseId: Long,
    val lectures: List<LectureItemDto>
)

data class LectureItemDto(
    val id: Long,
    val title: String,
    @SerializedName("total_duration_sec") val totalDurationSec: Float,
    val status: String,
    @SerializedName("slide_count") val slideCount: Int
)

data class LectureUploadResponse(
    @SerializedName("lecture_id") val lectureId: Long,
    @SerializedName("processing_status") val processingStatus: String,
    @SerializedName("progress_percent") val progressPercent: Int
)

data class LectureStatusResponse(
    @SerializedName("lecture_id") val lectureId: Long,
    @SerializedName("processing_status") val processingStatus: String,
    @SerializedName("progress_percent") val progressPercent: Int,
    @SerializedName("error_message") val errorMessage: String? = null
)

data class VoiceNoteItemDto(
    @SerializedName("category") val category: String = "core_concept",
    @SerializedName("headline") val headline: String = "",
    @SerializedName("content") val content: String = "",
    @SerializedName("timestamp_label") val timestampLabel: String = "00:00",
    @SerializedName("start_time_sec") val startTimeSec: Float = 0f,
    @SerializedName("end_time_sec") val endTimeSec: Float = 0f,
    @SerializedName("evidence_quote") val evidenceQuote: String = ""
)

data class TechnicalTermDto(
    @SerializedName("term") val term: String = "",
    @SerializedName("explanation") val explanation: String = ""
)

data class VoiceNotesTestResponse(
    @SerializedName("status") val status: String = "success",
    @SerializedName("title") val title: String = "",
    @SerializedName("duration_sec") val durationSec: Float = 0f,
    @SerializedName("summary") val summary: String = "",
    @SerializedName("key_points") val keyPoints: List<VoiceNoteItemDto> = emptyList(),
    @SerializedName("technical_terms") val technicalTerms: List<TechnicalTermDto> = emptyList(),
    @SerializedName("action_items_or_tips") val actionItemsOrTips: List<String> = emptyList(),
    @SerializedName("markdown_notes") val markdownNotes: String = ""
)

data class VoiceprintEnrollResponse(
    @SerializedName("status") val status: String = "success",
    @SerializedName("voiceprint_id") val voiceprintId: Long = 0L,
    @SerializedName("course_id") val courseId: Long = 0L,
    @SerializedName("lecturer_name") val lecturerName: String = "",
    @SerializedName("duration_sec") val durationSec: Float? = null,
    @SerializedName("embedding_dim") val embeddingDim: Int = 192,
    @SerializedName("cosine_threshold") val cosineThreshold: Float = 0.38f,
    @SerializedName("message") val message: String = ""
)

data class VoiceprintStatusResponse(
    @SerializedName("has_voiceprint") val hasVoiceprint: Boolean = false,
    @SerializedName("voiceprint_id") val voiceprintId: Long? = null,
    @SerializedName("course_id") val courseId: Long = 0L,
    @SerializedName("lecturer_name") val lecturerName: String? = null,
    @SerializedName("embedding_dim") val embeddingDim: Int = 0,
    @SerializedName("cosine_threshold") val cosineThreshold: Float = 0.38f,
    @SerializedName("created_at") val createdAt: String? = null
)

// ============================================================================
// 5. DTO CHO CHỈNH SỬA / XÓA GHI CHÚ INLINE (TUẦN 4)
// ============================================================================
data class UpdateInlineNoteRequest(
    @SerializedName("explanation_text") val explanationText: String
)

data class UpdateInlineNoteResponse(
    val id: String = "",
    @SerializedName("lecture_id") val lectureId: Long = 0L,
    @SerializedName("block_id") val blockId: String? = null,
    val explanationText: String = "",
    @SerializedName("is_edited_by_user") val isEditedByUser: Boolean = true
)

data class DeleteInlineNoteResponse(
    val deleted: Boolean = true,
    val id: String = ""
)

