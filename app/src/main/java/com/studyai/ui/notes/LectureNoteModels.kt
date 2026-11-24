package com.studyai.ui.notes

import com.studyai.ui.dictionary.CrossChapterLinkUiModel

data class ChapterUiModel(
    val id: Long,
    val title: String,
    val slideCount: Int
)

data class SlideUi(
    val id: Long,
    val slideNumber: Int,
    val extractedText: String,
    val terms: List<String>,
    val startTimeSec: Float,
    val endTimeSec: Float,
    val confidenceScore: Float,
    val notes: List<NoteUi>,
    val crossChapterLinks: List<CrossChapterLinkUiModel> = emptyList()
)

data class NoteUi(
    val id: Long,
    val slideId: Long,
    val bulletPoint: String,
    val noveltyScore: Float,
    val isInstructorOralOnly: Boolean,
    val verbatimQuote: String,
    val startTimeSec: Float,
    val endTimeSec: Float
)

data class ConceptContextChoice(
    val id: String,
    val term: String,
    val courseName: String,
    val badgeLabel: String,
    val shortDefinition: String
)
