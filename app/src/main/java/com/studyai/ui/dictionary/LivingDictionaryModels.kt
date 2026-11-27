package com.studyai.ui.dictionary

enum class LivingVaultFilter {
    ALL,
    NEW,
    REVIEW,
    MASTERED,
}

data class TrustedSourceDoc(
    val domain: String,       // VD: "Microsoft Learn", "OWASP Foundation"
    val badge: String,        // VD: "Tài liệu chính thức"
    val title: String,
    val excerpt: String,
    val url: String,
)

data class LivingLookupResult(
    val id: Long? = null,
    val term: String,
    val originLabel: String,  // VD: "Bài 9 • Slide 20"
    val lectureId: Long?,
    val slideNumber: Int?,
    val contextQuote: String,
    val aiExplanation: String,
    val codeExample: String? = null,
    val simpleFollowup: String? = null,
    val pitfallFollowup: String? = null,
    val trustedSources: List<TrustedSourceDoc> = emptyList(),
)

data class SavedLivingEntry(
    val id: Long,
    val term: String,
    val originLabel: String,
    val lectureId: Long?,
    val slideNumber: Int?,
    val contextQuote: String,
    val summary: String,
    val isMastered: Boolean = false,
    val isNew: Boolean = false,
    val codeExample: String? = null,
    val simpleFollowup: String? = null,
    val pitfallFollowup: String? = null,
    val trustedSources: List<TrustedSourceDoc> = emptyList(),
)
