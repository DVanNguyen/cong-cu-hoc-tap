package com.studyai.ui.roadmap

enum class BranchTone {
    TEAL,
    COBALT,
    AMBER,
    VIOLET,
    ROSE,
}

enum class MasteryFilter {
    ALL,
    MASTERED,
    REVIEW,
}

data class MindmapLeafNode(
    val id: String,
    val title: String,
    val slideRef: String,
    val lectureId: Long,
    val slideNumber: Int,
    val timestampSec: Int,
    val summary: String,
    val isMastered: Boolean = false,
)

data class MindmapBranch(
    val id: String,
    val lectureId: Long,
    val title: String, // VD: "Bài #9 • T-SQL"
    val tone: BranchTone,
    val isNewlyAdded: Boolean = false,
    val leaves: List<MindmapLeafNode>,
)

data class CourseMindmapUiState(
    val courseId: Long,
    val courseName: String,
    val branches: List<MindmapBranch> = emptyList(),
    val selectedFilter: MasteryFilter = MasteryFilter.ALL,
    val searchQuery: String = "",
    val zoomScale: Float = 1.0f,
    val selectedLeaf: MindmapLeafNode? = null,
)
