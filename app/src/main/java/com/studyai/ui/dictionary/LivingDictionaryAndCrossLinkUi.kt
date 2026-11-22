package com.studyai.ui.dictionary

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// =========================================================================
// 1. DATA MODELS CHO LIÊN KẾT CHÉO LIÊN CHƯƠNG & TỪ ĐIỂN SỐNG
// =========================================================================

data class CrossChapterLinkUiModel(
    val conceptId: Long,
    val conceptNameVi: String,
    val conceptNameEn: String,
    val linkDirection: String, // "BACK_TO_ORIGIN" | "FORWARD_TO_LATER_CHAPTER"
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
    val sourceProvider: String, // "WIKIPEDIA" | "SEMANTIC_SCHOLAR" | "WEB_TUTORIAL" | "YOUTUBE_VIDEO"
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
// 2. COMPONENT HIỂN THỊ LIÊN KẾT CHÉO GIỮA CÁC CHƯƠNG NGAY TRÊN SLIDE
// =========================================================================

@Composable
fun CrossChapterLinksSection(
    links: List<CrossChapterLinkUiModel>,
    onNavigateToChapterSlide: (lectureId: Int, slideNumber: Int, timestampSec: Float) -> Unit
) {
    if (links.isEmpty()) return

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.AccountTree,
                    contentDescription = null,
                    tint = Color(0xFF1B5E20),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "LIÊN KẾT CHÉO KIẾN THỨC LIÊN CHƯƠNG (${links.size})",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1B5E20)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            links.forEach { link ->
                val isBackLink = link.linkDirection == "BACK_TO_ORIGIN"
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                        .clickable {
                            onNavigateToChapterSlide(
                                link.targetLectureId,
                                link.targetSlideNumber,
                                link.targetTimestampSec
                            )
                        },
                    shape = RoundedCornerShape(8.dp),
                    color = Color.White
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${link.conceptNameVi} (${link.conceptNameEn})",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0D47A1)
                            )
                            Text(
                                text = link.badgeText,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF2E7D32)
                            )
                        }
                        Icon(
                            imageVector = if (isBackLink) Icons.Default.History else Icons.Default.ArrowForward,
                            contentDescription = "Chuyển hướng",
                            tint = Color(0xFF1565C0)
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

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header: Tên khái niệm song ngữ + Chỉ số độ trừu tượng
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "TỪ ĐIỂN SỐNG & MỞ RỘNG TRI THỨC",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "${entry.canonicalNameVi} (${entry.canonicalNameEn})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                    Surface(
                        color = Color(0xFFFFF3E0),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "Độ trừu tượng: ${(entry.abstractnessScore * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFE65100),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            // Khối 1: Liên kết chéo qua các chương (Nếu khái niệm xuất hiện ở nhiều bài)
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

            // Khối 2: Giảng viên đã nói gì trên lớp + AI Giải thích đào sâu
            item {
                OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "1. Lời giảng viên giải thích trên lớp:",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1565C0)
                        )
                        Text(
                            text = "\"${entry.lecturerOralExplanation}\"",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
                        )

                        HorizontalDivider()

                        Text(
                            text = "2. Giải thích đào sâu bản chất (Bổ trợ khung sườn Slide):",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF6A1B9A),
                            modifier = Modifier.padding(top = 8.dp)
                        )
                        Text(
                            text = entry.aiDeepExplanation,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 4.dp)
                        )

                        if (!entry.intuitionExample.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "💡 Ví dụ trực quan: ${entry.intuitionExample}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF2E7D32)
                            )
                        }
                    }
                }
            }

            // Khối 3: Danh sách Liên kết Tham khảo Bên ngoài (Đã kiểm chứng & xếp hạng)
            item {
                Text(
                    text = "NGUỒN TÀI LIỆU THAM KHẢO MỞ RỘNG (${entry.externalReferences.size})",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
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
    val badgeColor = when (ref.sourceProvider) {
        "SEMANTIC_SCHOLAR" -> Color(0xFFB71C1C)
        "WIKIPEDIA" -> Color(0xFF0D47A1)
        "YOUTUBE_VIDEO" -> Color(0xFFD84315)
        else -> Color(0xFF2E7D32)
    }

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenUrl(ref.url) },
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = badgeColor.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = "${ref.citationBadge} • ${ref.domainName}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = badgeColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
                Text(
                    text = "Độ khớp: ${(ref.finalRankScore * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = ref.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = ref.snippetSummary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 3.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End
            ) {
                Icon(
                    imageVector = Icons.Default.OpenInNew,
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
// 5. TOÀN CẢNH MÀN HÌNH TỪ ĐIỂN SỐNG (BỘ TỪ ĐIỂN MÔN HỌC)
// =========================================================================

@Composable
fun CourseDictionaryScreen(
    courseTitle: String = "Trí tuệ Nhân tạo (AI)",
    dictionaryEntries: List<CourseDictionaryItemUi>,
    onSelectConcept: (Long) -> Unit,
    onNavigateToChapterSlide: (Int, Int, Float) -> Unit = { _, _, _ -> }
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC))
            .padding(16.dp)
    ) {
        Text(
            text = "TỪ ĐIỂN SỐNG MÔN HỌC",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = courseTitle,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.ExtraBold,
            color = Color(0xFF0F172A)
        )
        Text(
            text = "Bồi đắp kiến thức chuyên sâu & Tài liệu mở rộng cho các khái niệm cốt lõi (${dictionaryEntries.size} khái niệm)",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp, bottom = 12.dp)
        )

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(dictionaryEntries, key = { it.id }) { item ->
                OutlinedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectConcept(item.id) },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.outlinedCardColors(containerColor = Color.White)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = item.canonicalNameVi,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1E293B),
                                modifier = Modifier.weight(1f)
                            )
                            Surface(
                                color = if (item.abstractnessScore >= 0.7f) Color(0xFFFFEBEE) else Color(0xFFE8F5E9),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = "Độ trừu tượng: ${(item.abstractnessScore * 100).toInt()}%",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (item.abstractnessScore >= 0.7f) Color(0xFFC62828) else Color(0xFF2E7D32),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Text(
                            text = item.canonicalNameEn,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF64748B),
                            fontWeight = FontWeight.Medium
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = item.definitionSummary.ifBlank { item.aiDeepExplanation },
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF334155),
                            maxLines = 3
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Xuất hiện: Chương ${item.firstIntroducedLectureId} (Slide ${item.firstIntroducedSlideNo})",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF0284C7)
                            )

                            Surface(
                                color = Color(0xFFEFF6FF),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = "${item.referencesCount} nguồn tham khảo",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1D4ED8),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
