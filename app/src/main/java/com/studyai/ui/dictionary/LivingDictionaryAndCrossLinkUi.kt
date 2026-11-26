package com.studyai.ui.dictionary

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.studyai.ui.theme.StudyAiExtras

// =========================================================================
// 1. DATA MODELS CHO LIÊN KẾT CHÉO LIÊN CHƯƠNG & TỪ ĐIỂN SỐNG
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
// 2. COMPONENT LIÊN KẾT CHÉO KIẾN THỨC GIỮA CÁC CHƯƠNG
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

// =========================================================================
// 3. BOTTOM SHEET "TỪ ĐIỂN SỐNG & TÀI LIỆU THAM KHẢO MỞ RỘNG"
// =========================================================================

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
        shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)
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

// =========================================================================
// 4. COMPONENT CARD TÀI LIỆU THAM KHẢO NGOÀI
// =========================================================================

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
                Surface(
                    color = badgeBg,
                    shape = RoundedCornerShape(8.dp)
                ) {
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

            Text(
                text = ref.title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )

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
// 5. TOÀN CẢNH MÀN HÌNH TỪ ĐIỂN SỐNG
// =========================================================================

@Composable
fun CourseDictionaryScreen(
    courseTitle: String = "Môn học",
    dictionaryEntries: List<CourseDictionaryItemUi>,
    onSelectConcept: (Long) -> Unit,
    onNavigateToChapterSlide: (Int, Int, Float) -> Unit = { _, _, _ -> }
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("ALL") }

    val filteredEntries = remember(dictionaryEntries, searchQuery, selectedFilter) {
        dictionaryEntries.filter { item ->
            val matchQuery = searchQuery.isBlank() ||
                    item.canonicalNameVi.contains(searchQuery, ignoreCase = true) ||
                    item.canonicalNameEn.contains(searchQuery, ignoreCase = true) ||
                    item.aliases.any { it.contains(searchQuery, ignoreCase = true) }

            val isAcademic = item.referencesCount >= 3 || item.abstractnessScore >= 0.75f
            val matchFilter = when (selectedFilter) {
                "LECTURE" -> !isAcademic
                "ACADEMIC" -> isAcademic
                else -> true
            }
            matchQuery && matchFilter
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(10.dp))

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Tra cứu thuật ngữ hoặc khái niệm...", style = MaterialTheme.typography.bodySmall) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            },
            trailingIcon = if (searchQuery.isNotEmpty()) {
                {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Xóa từ khóa tìm kiếm",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            } else null,
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
            ),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            FilterChip(
                selected = selectedFilter == "ALL",
                onClick = { selectedFilter = "ALL" },
                label = { Text("Tất cả (${dictionaryEntries.size})", style = MaterialTheme.typography.labelSmall) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
            FilterChip(
                selected = selectedFilter == "LECTURE",
                onClick = { selectedFilter = "LECTURE" },
                label = { Text("Bài giảng", style = MaterialTheme.typography.labelSmall) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                )
            )
            FilterChip(
                selected = selectedFilter == "ACADEMIC",
                onClick = { selectedFilter = "ACADEMIC" },
                label = { Text("Học thuật", style = MaterialTheme.typography.labelSmall) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onTertiaryContainer
                )
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        if (filteredEntries.isEmpty()) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoStories,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(30.dp)
                    )
                    Text(
                        text = if (dictionaryEntries.isEmpty()) "Từ điển môn $courseTitle đang trống" else "Không tìm thấy kết quả khớp",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = if (searchQuery.isNotBlank()) {
                            "Chưa có thuật ngữ \"$searchQuery\" trong bộ từ điển ngoại tuyến của môn học này."
                        } else {
                            "Khi xử lý bài giảng ở tab Thu âm, StudyAI sẽ tự động tổng hợp thuật ngữ kèm giải thích của giảng viên tại đây."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    if (searchQuery.isNotBlank() || selectedFilter != "ALL") {
                        Button(
                            onClick = {
                                searchQuery = ""
                                selectedFilter = "ALL"
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text("Xóa bộ lọc tìm kiếm", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 20.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(filteredEntries, key = { it.id }) { item ->
                    val isAcademic = item.referencesCount >= 3 || item.abstractnessScore >= 0.75f
                    OutlinedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectConcept(item.id) },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Surface(
                                    color = if (isAcademic) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.secondaryContainer,
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = "Bài ${item.firstIntroducedLectureId} • Slide ${item.firstIntroducedSlideNo}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isAcademic) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }

                                Text(
                                    text = "${item.referencesCount} nguồn tham khảo",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = item.canonicalNameVi,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            if (item.canonicalNameEn.isNotBlank() && !item.canonicalNameEn.equals(item.canonicalNameVi, ignoreCase = true)) {
                                Text(
                                    text = item.canonicalNameEn,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = item.definitionSummary.ifBlank { item.aiDeepExplanation },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (isAcademic) "Nguồn học thuật mở rộng" else "Trích từ bài giảng trên lớp",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.secondary,
                                    fontWeight = FontWeight.Medium
                                )

                                Text(
                                    text = "Xem chi tiết →",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
