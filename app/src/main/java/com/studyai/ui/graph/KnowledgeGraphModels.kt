package com.studyai.ui.graph

data class ConceptNodeUi(
    val id: Long,
    val canonicalNameVi: String,
    val canonicalNameEn: String,
    val aliases: List<String>,
    val firstIntroducedLectureId: Int,
    val firstIntroducedSlideNo: Int,
    val definedOrderIndex: Int,
    val definitionSummary: String,
    val layerY: Float,
    val orderX: Float,
    val hasViolation: Boolean
)

data class ConceptEdgeUi(
    val sourceConceptId: Long,
    val targetConceptId: Long,
    val relationType: String,
    val weight: Float,
    val evidenceQuote: String,
    val isUsedBeforeDefinedViolation: Boolean
)
