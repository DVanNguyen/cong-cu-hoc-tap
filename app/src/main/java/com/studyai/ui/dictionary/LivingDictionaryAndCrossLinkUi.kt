package com.studyai.ui.dictionary

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.studyai.ui.theme.StudyAiExtras
import com.studyai.ui.theme.StudyAiPalette

// =========================================================================
// 1. DATA MODELS TƯƠNG THÍCH CHO LIÊN KẾT CHÉO & KHÁI NIỆM MÔN HỌC
// =========================================================================

data class CrossChapterLinkUiModel(
    val conceptId: Long,
    val conceptNameVi: String,
    val conceptNameEn: String,
    val linkDirection: String,
    val targetLectureId: Int,
    val targetLectureOrder: Int,
    val targetLectureTitle: String,
    val targetSlideNumber: Int,
    val targetTimestampSec: Float,
    val badgeText: String
)

data class ExternalReferenceUiModel(
    val id: Long,
    val title: String,
    val url: String,
    val sourceProvider: String,
    val domainName: String,
    val snippetSummary: String,
    val finalRankScore: Float,
    val citationBadge: String
)

data class LivingDictionaryUiModel(
    val conceptId: Long,
    val canonicalNameVi: String,
    val canonicalNameEn: String,
    val abstractnessScore: Float,
    val slideSkeletonContext: String? = null,
    val lecturerOralExplanation: String,
    val aiDeepExplanation: String,
    val intuitionExample: String? = null,
    val crossChapterLinks: List<CrossChapterLinkUiModel> = emptyList(),
    val externalReferences: List<ExternalReferenceUiModel> = emptyList()
)

data class CourseDictionaryItemUi(
    val id: Long,
    val canonicalNameVi: String,
    val canonicalNameEn: String,
    val aliases: List<String>,
    val definitionSummary: String,
    val abstractnessScore: Float,
    val aiDeepExplanation: String,
    val intuitionExample: String,
    val firstIntroducedLectureId: Int,
    val firstIntroducedSlideNo: Int,
    val referencesCount: Int
)

// =========================================================================
// 2. COMPONENT LIÊN KẾT CHÉO KIẾN THỨC GIỮA CÁC CHƯƠNG (GIỮ TƯƠNG THÍCH)
// =========================================================================

@Composable
fun CrossChapterLinksSection(
    links: List<CrossChapterLinkUiModel>,
    onNavigateToChapterSlide: (lectureId: Int, slideNumber: Int, timestampSec: Float) -> Unit
) {
    if (links.isEmpty()) return

    val extras = StudyAiExtras.colors

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        color = extras.successBg,
        border = BorderStroke(1.dp, extras.successBorder),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.AccountTree,
                    contentDescription = null,
                    tint = extras.successFg,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Liên kết chéo giữa các bài giảng (${links.size})",
                    style = MaterialTheme.typography.labelLarge,
                    color = extras.successFg
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            links.forEach { link ->
                val isBackLink = link.linkDirection == "BACK_TO_ORIGIN"
                Surface(
                    onClick = {
                        onNavigateToChapterSlide(
                            link.targetLectureId,
                            link.targetSlideNumber,
                            link.targetTimestampSec
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp),
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${link.conceptNameVi} (${link.conceptNameEn})",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = link.badgeText,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                        Icon(
                            imageVector = if (isBackLink) Icons.Default.History else Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Chuyển tới bài giảng",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LivingDictionaryBottomSheet(
    entry: LivingDictionaryUiModel,
    onDismiss: () -> Unit,
    onNavigateToChapterSlide: (lectureId: Int, slideNumber: Int, timestampSec: Float) -> Unit
) {
    val context = LocalContext.current
    val extras = StudyAiExtras.colors

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                val originLink = entry.crossChapterLinks.firstOrNull()
                val metaLine = if (originLink != null) {
                    "Bài ${originLink.targetLectureOrder} • Slide ${originLink.targetSlideNumber} • ${entry.externalReferences.size} nguồn"
                } else {
                    "Từ điển học thuật • ${entry.externalReferences.size} nguồn tham khảo"
                }
                Column(modifier = Modifier.fillMaxWidth()) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        Text(
                            text = metaLine,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "${entry.canonicalNameVi} (${entry.canonicalNameEn})",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            if (entry.crossChapterLinks.isNotEmpty()) {
                item {
                    CrossChapterLinksSection(
                        links = entry.crossChapterLinks,
                        onNavigateToChapterSlide = { lecId, slideNo, sec ->
                            onDismiss()
                            onNavigateToChapterSlide(lecId, slideNo, sec)
                        }
                    )
                }
            }

            item {
                OutlinedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(
                                imageVector = Icons.Default.RecordVoiceOver,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Lời giảng viên giải thích trên lớp",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Text(
                            text = "\"${entry.lecturerOralExplanation}\"",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))

                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.MenuBook,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Giải thích đào sâu bản chất",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                        Text(
                            text = entry.aiDeepExplanation,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        if (!entry.intuitionExample.isNullOrBlank()) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = extras.exampleBg,
                                border = BorderStroke(1.dp, extras.exampleBorder),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.Top,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Lightbulb,
                                        contentDescription = null,
                                        tint = extras.exampleBadgeFg,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "Ví dụ trực quan: ${entry.intuitionExample}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = extras.exampleBadgeFg
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(
                        imageVector = Icons.Default.Public,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Tài liệu tham khảo mở rộng (${entry.externalReferences.size})",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            items(entry.externalReferences, key = { it.url }) { ref ->
                ExternalReferenceCard(
                    ref = ref,
                    onOpenUrl = { url ->
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        context.startActivity(intent)
                    }
                )
            }

            item { Spacer(modifier = Modifier.height(28.dp)) }
        }
    }
}

@Composable
fun ExternalReferenceCard(
    ref: ExternalReferenceUiModel,
    onOpenUrl: (String) -> Unit
) {
    val extras = StudyAiExtras.colors
    val (badgeBg, badgeFg) = when (ref.sourceProvider) {
        "SEMANTIC_SCHOLAR" -> extras.scholarBg to extras.scholarFg
        "WIKIPEDIA" -> extras.wikiBg to extras.wikiFg
        "YOUTUBE_VIDEO" -> extras.videoBg to extras.videoFg
        else -> extras.successBg to extras.successFg
    }

    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenUrl(ref.url) },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(color = badgeBg, shape = RoundedCornerShape(8.dp)) {
                    Text(
                        text = "${ref.citationBadge} • ${ref.domainName}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = badgeFg,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
                Text(
                    text = "Khớp ${(ref.finalRankScore * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(text = ref.title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            Text(
                text = ref.snippetSummary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Mở nguồn tham khảo",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

// =========================================================================
// 3. MÀN HÌNH 4 CHÍNH: TỪ ĐIỂN SỐNG + KHO ĐÃ LƯU CÁ NHÂN (SPEC MÀN 4)
// =========================================================================

@Composable
fun LivingDictionaryScreen(
    courseTitle: String = "Môn học",
    savedEntries: List<SavedLivingEntry>,
    quickSuggestions: List<String> = emptyList(),
    activeFilter: LivingVaultFilter = LivingVaultFilter.ALL,
    onFilterChange: (LivingVaultFilter) -> Unit = {},
    onQuickLookup: (String) -> Unit = {},
    onOpenEntry: (SavedLivingEntry) -> Unit = {},
    onToggleMastered: (Long) -> Unit = {},
    onDeleteEntry: (Long) -> Unit = {},
    onJumpToSlide: (lectureId: Long?, slideNumber: Int?) -> Unit = { _, _ -> },
) {
    val isDark = StudyAiExtras.isDark
    val surfaceColor = MaterialTheme.colorScheme.surface
    val surfaceSubtle = MaterialTheme.colorScheme.surfaceVariant
    val borderColor = MaterialTheme.colorScheme.outline
    val primarySoft = if (isDark) StudyAiPalette.PrimarySoftDark else StudyAiPalette.PrimarySoftLight
    val primaryText = if (isDark) StudyAiPalette.PrimaryTextDark else StudyAiPalette.PrimaryTextLight
    val tealSoft = if (isDark) StudyAiPalette.TealSoftDark else StudyAiPalette.TealSoftLight
    val tealText = if (isDark) StudyAiPalette.TealTextDark else StudyAiPalette.TealTextLight
    val amberSoft = if (isDark) StudyAiPalette.AmberSoftDark else StudyAiPalette.AmberSoftLight
    val amberText = if (isDark) StudyAiPalette.AmberTextDark else StudyAiPalette.AmberTextLight

    var queryDraft by remember { mutableStateOf("") }

    val newCount = remember(savedEntries) { savedEntries.count { it.isNew } }
    val reviewCount = remember(savedEntries) { savedEntries.count { !it.isMastered } }
    val masteredCount = remember(savedEntries) { savedEntries.count { it.isMastered } }

    val filteredEntries = remember(savedEntries, activeFilter, queryDraft) {
        val q = queryDraft.trim()
        savedEntries.filter { entry ->
            val matchFilter = when (activeFilter) {
                LivingVaultFilter.ALL -> true
                LivingVaultFilter.NEW -> entry.isNew
                LivingVaultFilter.REVIEW -> !entry.isMastered
                LivingVaultFilter.MASTERED -> entry.isMastered
            }
            val matchQuery = q.isEmpty() ||
                entry.term.contains(q, ignoreCase = true) ||
                entry.contextQuote.contains(q, ignoreCase = true) ||
                entry.summary.contains(q, ignoreCase = true)
            matchFilter && matchQuery
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // 1. Thẻ Tra cứu nhanh bằng AI + Nguồn uy tín
        item(key = "living_quick_lookup_card") {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = surfaceColor,
                border = BorderStroke(1.dp, borderColor),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = StudyAiPalette.PrimaryCobalt,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = "Tra cứu ngữ cảnh bài giảng",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedTextField(
                            value = queryDraft,
                            onValueChange = { queryDraft = it },
                            placeholder = {
                                Text(
                                    text = "Nhập từ khóa, lệnh T-SQL, khái niệm...",
                                    fontSize = 12.5.sp,
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp),
                                )
                            },
                            trailingIcon = if (queryDraft.isNotEmpty()) {
                                {
                                    IconButton(
                                        onClick = { queryDraft = "" },
                                        modifier = Modifier.size(30.dp),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Xóa chữ",
                                            modifier = Modifier.size(16.dp),
                                        )
                                    }
                                }
                            } else null,
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = surfaceSubtle.copy(alpha = 0.45f),
                                unfocusedContainerColor = surfaceSubtle.copy(alpha = 0.45f),
                                focusedBorderColor = StudyAiPalette.PrimaryCobalt,
                                unfocusedBorderColor = borderColor,
                            ),
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )

                        Button(
                            onClick = {
                                val term = queryDraft.trim()
                                if (term.isNotEmpty()) {
                                    onQuickLookup(term)
                                }
                            },
                            enabled = queryDraft.trim().isNotEmpty(),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = StudyAiPalette.PrimaryCobalt,
                                contentColor = Color.White,
                            ),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 14.dp),
                        ) {
                            Text("Tra ngay", fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    if (quickSuggestions.isNotEmpty()) {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            items(quickSuggestions, key = { it }) { term ->
                                Surface(
                                    onClick = { onQuickLookup(term) },
                                    shape = RoundedCornerShape(999.dp),
                                    color = primarySoft,
                                ) {
                                    Text(
                                        text = term,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = primaryText,
                                        maxLines = 1,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                    )
                                }
                            }
                        }
                    }

                    Text(
                        text = "Mẹo: Giữ tay bôi đen bất kỳ cụm từ nào ở tab Ghi chú hoặc file PDF ngoài app → chọn ✨ Từ điển sống.",
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // 2. Header Kho đã lưu + 4 chip lọc (Tất cả / Mới lưu / Cần ôn / Đã vững)
        item(key = "living_vault_header") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Kho đã lưu",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "$masteredCount/${savedEntries.size} đã vững",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = tealText,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    val filterItems = listOf(
                        LivingVaultFilter.ALL to "Tất cả (${savedEntries.size})",
                        LivingVaultFilter.NEW to "Mới lưu ($newCount)",
                        LivingVaultFilter.REVIEW to "Cần ôn ($reviewCount)",
                        LivingVaultFilter.MASTERED to "Đã vững ($masteredCount)",
                    )
                    items(filterItems, key = { it.first.name }) { (filter, label) ->
                        val isSelected = activeFilter == filter
                        Surface(
                            onClick = { onFilterChange(filter) },
                            shape = RoundedCornerShape(999.dp),
                            color = if (isSelected) StudyAiPalette.PrimaryCobalt else surfaceColor,
                            border = if (isSelected) null else BorderStroke(1.dp, borderColor),
                        ) {
                            Text(
                                text = label,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            )
                        }
                    }
                }
            }
        }

        // 3. Danh sách mục trong Kho đã lưu
        if (filteredEntries.isEmpty()) {
            item(key = "living_vault_empty") {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = surfaceColor,
                    border = BorderStroke(1.dp, borderColor),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoStories,
                            contentDescription = null,
                            tint = StudyAiPalette.PrimaryCobalt,
                            modifier = Modifier.size(30.dp),
                        )
                        Text(
                            text = if (savedEntries.isEmpty()) {
                                "Kho Từ điển sống môn $courseTitle đang trống"
                            } else {
                                "Không có mục nào khớp bộ lọc"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            text = if (savedEntries.isEmpty()) {
                                "Hãy bôi đen cụm từ lạ ở tab Ghi chú hoặc nhập từ khóa phía trên rồi bấm \"Lưu vào Từ điển\"."
                            } else {
                                "Chạm \"Tất cả\" hoặc xóa từ khóa tìm kiếm để xem toàn bộ kho đã lưu."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        } else {
            items(filteredEntries, key = { "${it.id}_${it.term}" }) { entry ->
                Surface(
                    onClick = { onOpenEntry(entry) },
                    shape = RoundedCornerShape(24.dp),
                    color = surfaceColor,
                    border = BorderStroke(
                        width = 1.dp,
                        color = if (entry.isMastered) StudyAiPalette.Teal.copy(alpha = 0.55f) else borderColor,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.weight(1f),
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = primarySoft,
                                ) {
                                    Text(
                                        text = entry.originLabel,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = primaryText,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    )
                                }

                                if (entry.isNew) {
                                    Surface(shape = RoundedCornerShape(999.dp), color = amberSoft) {
                                        Text(
                                            text = "Mới lưu",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = amberText,
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                        )
                                    }
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    onClick = { onToggleMastered(entry.id) },
                                    shape = RoundedCornerShape(999.dp),
                                    color = if (entry.isMastered) tealSoft else surfaceSubtle,
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        Icon(
                                            imageVector = if (entry.isMastered) {
                                                Icons.Default.CheckCircle
                                            } else {
                                                Icons.Default.RadioButtonUnchecked
                                            },
                                            contentDescription = null,
                                            tint = if (entry.isMastered) tealText else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(13.dp),
                                        )
                                        Text(
                                            text = if (entry.isMastered) "Đã vững ✓" else "Cần ôn",
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (entry.isMastered) tealText else MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }

                                IconButton(
                                    onClick = { onDeleteEntry(entry.id) },
                                    modifier = Modifier.size(32.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteOutline,
                                        contentDescription = "Xóa khỏi kho",
                                        tint = StudyAiPalette.Rose.copy(alpha = 0.8f),
                                        modifier = Modifier.size(17.dp),
                                    )
                                }
                            }
                        }

                        Text(
                            text = entry.term,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )

                        if (entry.contextQuote.isNotBlank()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(IntrinsicSize.Min)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(surfaceSubtle)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(3.dp)
                                        .fillMaxHeight()
                                        .background(StudyAiPalette.PrimaryCobalt)
                                )
                                Text(
                                    text = "\"${entry.contextQuote}\"",
                                    fontSize = 11.5.sp,
                                    lineHeight = 17.sp,
                                    fontStyle = FontStyle.Italic,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                )
                            }
                        }

                        Text(
                            text = entry.summary,
                            fontSize = 12.5.sp,
                            lineHeight = 19.sp,
                            color = if (isDark) StudyAiPalette.InkSecondaryDark else StudyAiPalette.InkSecondaryLight,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Mở AI + ${entry.trustedSources.size} nguồn uy tín",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = primaryText,
                            )

                            if (entry.slideNumber != null && entry.slideNumber > 0) {
                                Text(
                                    text = "Về Slide gốc →",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = tealText,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { onJumpToSlide(entry.lectureId, entry.slideNumber) }
                                        .padding(horizontal = 6.dp, vertical = 3.dp),
                                )
                            }
                        }
                    }
                }
            }
        }

        item(key = "living_bottom_spacer") {
            Spacer(Modifier.height(14.dp))
        }
    }
}
