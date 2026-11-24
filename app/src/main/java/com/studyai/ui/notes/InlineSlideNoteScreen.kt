package com.studyai.ui.notes

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp

enum class HighlightSourceType {
    PREVIOUS_LECTURE,   // Khái niệm đã học ở bài cũ -> Highlight xanh ngọc
    ACADEMIC_INTERNET,  // Khái niệm chưa học, tra từ nguồn học thuật uy tín -> Highlight vàng cam
}

data class InlineConceptHighlight(
    val term: String,
    val sourceType: HighlightSourceType,
    val sourceLabel: String,     // VD: "Đã học ở Chương 1 • Slide 4" hoặc "MIT OCW • Semantic Scholar"
    val explanation: String,     // Chỉ hiển thị đúng đoạn giải thích ngắn gọn trong pop-up
)

data class InlineLecturerNote(
    val id: String,
    val explanationText: String,
    val timestampSec: Float,
    val timestampLabel: String,
)

data class SlideDocumentBlockUi(
    val blockId: String,
    val kind: String = "bullet", // "heading" | "bullet" | "formula"
    val originalSlideText: String,
    val isCurrentlySpoken: Boolean = false,
    val isMarkedConfused: Boolean = false,
    val inlineNotes: List<InlineLecturerNote> = emptyList(),
    val highlights: List<InlineConceptHighlight> = emptyList(),
)

data class AnnotatedSlidePageUi(
    val pageNumber: Int,
    val slideTitle: String,
    val blocks: List<SlideDocumentBlockUi>,
)

/**
 * Màn hình Vở Ghi Chép Nhúng Trực Tiếp Trên Slide:
 * - Copy toàn bộ nội dung từng trang slide thành 1 tài liệu ghi chú sạch, dễ đọc.
 * - Ghi chú phân tích của giảng viên được chèn NGAY DƯỚI ĐÚNG Ý (block) đang được giảng.
 * - Khái niệm đã học ở bài cũ (highlight xanh) & khái niệm mới từ nguồn học thuật quốc tế (highlight vàng)
 *   được đánh dấu ngay trong dòng chữ; chạm vào bật Pop-up gọn chỉ hiện đúng đoạn giải thích.
 * - Không còn các khung "Liên kết chéo" to chiếm màn hình hay thuật ngữ kỹ thuật (Novelty & MMR).
 */
@Composable
fun InlineSlideNoteDocumentScreen(
    pages: List<AnnotatedSlidePageUi>,
    onToggleConfusedBlock: (blockId: String) -> Unit = {},
    onPlayTimestamp: (Float) -> Unit = {},
) {
    var activePopupConcept by remember { mutableStateOf<InlineConceptHighlight?>(null) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Chú giải màu highlight siêu gọn ở đầu tài liệu để người dùng mới nhìn là hiểu ngay
        item {
            HighlightLegendBar()
        }

        items(pages, key = { it.pageNumber }) { page ->
            AnnotatedSlidePageCard(
                page = page,
                onClickConcept = { activePopupConcept = it },
                onToggleConfusedBlock = onToggleConfusedBlock,
                onPlayTimestamp = onPlayTimestamp,
            )
        }
    }

    // Pop-up gọn: chỉ hiển thị đúng đoạn giải thích ở bài cũ hoặc nguồn học thuật quốc tế
    activePopupConcept?.let { concept ->
        val isAcademic = concept.sourceType == HighlightSourceType.ACADEMIC_INTERNET
        AlertDialog(
            onDismissRequest = { activePopupConcept = null },
            icon = {
                Icon(
                    imageVector = if (isAcademic) Icons.Default.Public else Icons.Default.School,
                    contentDescription = null,
                    tint = if (isAcademic) Color(0xFFD97706) else Color(0xFF0D9488),
                )
            },
            title = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = concept.term,
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Spacer(Modifier.height(4.dp))
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = if (isAcademic) {
                            Color(0xFFFEF3C7)
                        } else {
                            Color(0xFFCCFBF1)
                        },
                    ) {
                        Text(
                            text = concept.sourceLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isAcademic) Color(0xFF92400E) else Color(0xFF115E59),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                        )
                    }
                }
            },
            text = {
                Text(
                    text = concept.explanation,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            },
            confirmButton = {
                TextButton(onClick = { activePopupConcept = null }) {
                    Text("Đã hiểu")
                }
            },
        )
    }
}

@Composable
private fun HighlightLegendBar() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LegendChip(
            label = "Bài cũ",
            bg = Color(0xFFCCFBF1),
            fg = Color(0xFF115E59),
        )
        LegendChip(
            label = "Học thuật",
            bg = Color(0xFFFEF3C7),
            fg = Color(0xFF92400E),
        )
        LegendChip(
            label = "Chưa hiểu",
            bg = Color(0xFFFFE4E6),
            fg = Color(0xFF9F1239),
        )
    }
}

@Composable
private fun LegendChip(label: String, bg: Color, fg: Color) {
    Surface(shape = RoundedCornerShape(10.dp), color = bg) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = fg,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

@Composable
private fun AnnotatedSlidePageCard(
    page: AnnotatedSlidePageUi,
    onClickConcept: (InlineConceptHighlight) -> Unit,
    onToggleConfusedBlock: (String) -> Unit,
    onPlayTimestamp: (Float) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Tiêu đề trang Slide rõ ràng
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = page.slideTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Text(
                        text = "Trang ${page.pageNumber}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }

            // Từng ý (block) của slide kèm highlight & ghi chú chèn thẳng ngay bên dưới
            page.blocks.forEach { block ->
                SlideBlockRow(
                    block = block,
                    onClickConcept = onClickConcept,
                    onToggleConfused = { onToggleConfusedBlock(block.blockId) },
                    onPlayTimestamp = onPlayTimestamp,
                )
            }
        }
    }
}

@Composable
private fun SlideBlockRow(
    block: SlideDocumentBlockUi,
    onClickConcept: (InlineConceptHighlight) -> Unit,
    onToggleConfused: () -> Unit,
    onPlayTimestamp: (Float) -> Unit,
) {
    val containerColor = when {
        block.isMarkedConfused -> Color(0xFFFFE4E6).copy(alpha = 0.65f)
        block.isCurrentlySpoken -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
        else -> Color.Transparent
    }
    val borderColor = when {
        block.isMarkedConfused -> Color(0xFFE11D48).copy(alpha = 0.55f)
        block.isCurrentlySpoken -> MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
        else -> Color.Transparent
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(containerColor)
            .padding(horizontal = if (containerColor == Color.Transparent) 2.dp else 10.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (block.isCurrentlySpoken) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.GraphicEq,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "Giảng viên đang giảng đoạn này",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
        ) {
            // Nội dung gốc từ slide với highlight inline có thể bấm
            val annotatedText = remember(block.originalSlideText, block.highlights) {
                buildAnnotatedString {
                    val base = if (block.kind == "bullet") "• ${block.originalSlideText}" else block.originalSlideText
                    append(base)
                    block.highlights.forEachIndexed { idx, hl ->
                        val start = base.indexOf(hl.term, ignoreCase = true)
                        if (start >= 0) {
                            val end = start + hl.term.length
                            val bg = if (hl.sourceType == HighlightSourceType.ACADEMIC_INTERNET) {
                                Color(0xFFFEF3C7)
                            } else {
                                Color(0xFFCCFBF1)
                            }
                            val fg = if (hl.sourceType == HighlightSourceType.ACADEMIC_INTERNET) {
                                Color(0xFF92400E)
                            } else {
                                Color(0xFF115E59)
                            }
                            addStyle(
                                style = SpanStyle(
                                    background = bg,
                                    color = fg,
                                    fontWeight = FontWeight.SemiBold,
                                    textDecoration = TextDecoration.Underline,
                                ),
                                start = start,
                                end = end,
                            )
                            addStringAnnotation(
                                tag = "CONCEPT",
                                annotation = idx.toString(),
                                start = start,
                                end = end,
                            )
                        }
                    }
                }
            }

            @Suppress("DEPRECATION")
            ClickableText(
                text = annotatedText,
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = if (block.kind == "heading") FontWeight.Bold else FontWeight.Normal,
                    fontStyle = if (block.kind == "formula") FontStyle.Italic else FontStyle.Normal,
                ),
                modifier = Modifier.weight(1f),
                onClick = { offset ->
                    annotatedText.getStringAnnotations(tag = "CONCEPT", start = offset, end = offset)
                        .firstOrNull()
                        ?.item
                        ?.toIntOrNull()
                        ?.let { idx ->
                            block.highlights.getOrNull(idx)?.let(onClickConcept)
                        }
                },
            )

            // Nút cờ nhỏ đánh dấu "Chưa hiểu đoạn này" ngay trên từng ý slide
            Icon(
                imageVector = Icons.Default.Flag,
                contentDescription = "Đánh dấu chưa hiểu đoạn này",
                tint = if (block.isMarkedConfused) Color(0xFFE11D48) else MaterialTheme.colorScheme.outline,
                modifier = Modifier
                    .padding(start = 6.dp, top = 2.dp)
                    .size(18.dp)
                    .clickable(onClick = onToggleConfused),
            )
        }

        // Ghi chú giảng viên phân tích chèn TRỰC TIẾP ngay dưới ý slide tương ứng
        block.inlineNotes.forEach { note ->
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.25f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 10.dp),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(13.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "Giải thích của giảng viên",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Surface(
                            onClick = { onPlayTimestamp(note.timestampSec) },
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surface,
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Nghe lại",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(13.dp),
                                )
                                Text(
                                    text = note.timestampLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                    Text(
                        text = note.explanationText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}