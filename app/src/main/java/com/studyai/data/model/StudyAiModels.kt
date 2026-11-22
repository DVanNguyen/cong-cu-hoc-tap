package com.studyai.data.model

import com.google.gson.annotations.SerializedName

// ── Trụ cột 3: Gom Deadline & Xếp lịch ────────────────────────
data class DeadlineExtractRequest(
    val text: String,
    val source: String = "telegram",
    @SerializedName("course_hint") val courseHint: String = "Trí tuệ nhân tạo"
)

data class DeadlineExtractResponse(
    val message: String?,
    @SerializedName("is_deadline") val isDeadline: Boolean,
    val task: TaskModel?,
    @SerializedName("needs_confirmation") val needsConfirmation: Boolean
)

data class TaskModel(
    val id: String,
    val title: String,
    val description: String?,
    val tag: String?,
    @SerializedName("due_date") val dueDateMs: Long?,
    val confidence: Float,
    val intent: String,
    @SerializedName("effort_est_min") val effortEstMin: Int,
    @SerializedName("load_ratio") val loadRatio: Float,
    @SerializedName("evidence_quote") val evidenceQuote: String?,
    @SerializedName("needs_confirmation") val needsConfirmation: Boolean
)

data class RankedTaskItem(
    @SerializedName("task_id") val taskId: String,
    val title: String,
    @SerializedName("remaining_effort_hours") val remainingEffortHours: Float,
    @SerializedName("available_hours") val availableHours: Float,
    @SerializedName("load_ratio") val loadRatio: Float,
    @SerializedName("urgency_level") val urgencyLevel: String, // gentle, should_do_today, urgent, overloaded
    @SerializedName("priority_score") val priorityScore: Float,
    @SerializedName("recommended_action") val recommendedAction: String
)

data class RankedTasksResponse(
    val tasks: List<RankedTaskItem>,
    val count: Int
)

// ── Trụ cột 1: Ghi chú bài giảng bám Slide ────────────────────
data class LectureNoteItem(
    val id: Long,
    val content: String,
    @SerializedName("timestamp_start") val timestampStartSec: Int,
    @SerializedName("timestamp_end") val timestampEndSec: Int,
    val type: String // addition, term, exam_hint
)

data class SlideNoteDto(
    @SerializedName("page_number") val pageNumber: Int,
    @SerializedName("slide_text") val slideText: String?,
    val notes: List<LectureNoteItem>
)

data class LectureNotesResponse(
    val lecture: LectureSummary,
    val slides: List<SlideNoteDto>
)

data class LectureSummary(
    val id: Long,
    val title: String,
    val status: String,
    @SerializedName("audio_path") val audioPath: String?,
    @SerializedName("slide_path") val slidePath: String?
)

// ── Trụ cột 2: Đồ thị tri thức liên bài ───────────────────────
data class ConceptNodeDto(
    val id: String,
    val name: String,
    val aliases: List<String>?,
    val definition: String?,
    @SerializedName("defined_in_lecture_idx") val definedInLectureIdx: Int
)

data class ConceptEdgeDto(
    @SerializedName("from_concept") val fromConcept: String,
    @SerializedName("to_concept") val toConcept: String,
    val relation: String,
    @SerializedName("evidence_quote") val evidenceQuote: String?
)

data class GraphAnalysisDto(
    @SerializedName("is_dag") val isDag: Boolean,
    @SerializedName("topological_order") val topologicalOrder: List<String>,
    @SerializedName("used_before_defined_warnings") val usedBeforeDefinedWarnings: List<String>,
    @SerializedName("total_concepts") val totalConcepts: Int,
    @SerializedName("total_edges") val totalEdges: Int
)

data class ConceptGraphResponse(
    val nodes: List<ConceptNodeDto>,
    val edges: List<ConceptEdgeDto>,
    val analysis: GraphAnalysisDto
)

// ── Unified API Models (v1) cho 3 Màn hình Chính ──────────────

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

// ── Trụ cột 2: Từ điển sống (Living Dictionary) & Liên kết Chéo Liên Chương ──
data class CrossChapterLinkDto(
    @SerializedName("concept_id") val conceptId: Long,
    @SerializedName("concept_name_vi") val conceptNameVi: String,
    @SerializedName("concept_name_en") val conceptNameEn: String,
    @SerializedName("link_direction") val linkDirection: String, // "BACK_TO_ORIGIN" | "FORWARD_TO_LATER_CHAPTER"
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
    @SerializedName("source_provider") val sourceProvider: String, // "WIKIPEDIA" | "SEMANTIC_SCHOLAR" | "WEB_TUTORIAL" | "YOUTUBE_VIDEO"
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

// ── Danh sách Bài giảng / Chương học ──────────────
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
