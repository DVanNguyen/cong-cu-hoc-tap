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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.studyai.audio.AudioPlayerUiState

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
 * Màn hình Vở Ghi Chép Nhúng Trực Tiếp Trên Slide (Tuần 4):
 * - Tương thích Dark/Light Mode chuẩn độ tương phản qua MaterialTheme.colorScheme.
 * - Hỗ trợ Sửa / Xóa từng ghi chú giảng viên (Menu 3 chấm).
 * - Hỗ trợ Xuất vở ghi chép dạng Markdown (.md / Chia sẻ).
 * - Hiển thị banner khi đang đọc từ bản cache ngoại tuyến.
 */
@Composable
fun InlineSlideNoteDocumentScreen(
    pages: List<AnnotatedSlidePageUi>,
    activeSpokenBlockId: String? = null,
    isOfflineCache: Boolean = false,
    onToggleConfusedBlock: (blockId: String) -> Unit = {},
    onPlayTimestamp: (Float) -> Unit = {},
    onUpdateNote: (noteId: String, newText: String) -> Unit = { _, _ -> },
    onDeleteNote: (noteId: String) -> Unit = {},
    onExportMarkdown: (() -> Unit)? = null,
) {
    var activePopupConcept by remember { mutableStateOf<InlineConceptHighlight?>(null) }
    var editingNote by remember { mutableStateOf<InlineLecturerNote?>(null) }
    var editDraftText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // Tự động cuộn mượt tới trang slide chứa block giảng viên đang giảng (Tuần 5)
    LaunchedEffect(activeSpokenBlockId, pages.size) {
        if (!activeSpokenBlockId.isNullOrBlank()) {
            val targetPageIdx = pages.indexOfFirst { page ->
                page.blocks.any { it.blockId == activeSpokenBlockId || it.isCurrentlySpoken }
            }
            if (targetPageIdx >= 0) {
                val headerOffset = if (isOfflineCache) 2 else 1
                listState.animateScrollToItem(index = targetPageIdx + headerOffset)
            }
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (isOfflineCache) {
            item {
                OfflineCacheBanner()
            }
        }

        // Chú giải màu highlight siêu gọn + nút Xuất vở (.md)
        item {
            HighlightLegendAndExportBar(onExportMarkdown = onExportMarkdown)
        }

        if (pages.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "Chưa có trang slide hoặc ghi chú nào cho bài giảng này. Hãy sang tab Thu âm để tải Slide + Audio.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }

        items(pages, key = { it.pageNumber }) { page ->
            AnnotatedSlidePageCard(
                page = page,
                onClickConcept = { activePopupConcept = it },
                onToggleConfusedBlock = onToggleConfusedBlock,
                onPlayTimestamp = onPlayTimestamp,
                onStartEditNote = { note ->
                    editingNote = note
                    editDraftText = note.explanationText
                },
                onDeleteNote = onDeleteNote,
            )
        }
    }

    // Dialog chỉnh sửa ghi chú
    editingNote?.let { note ->
        AlertDialog(
            onDismissRequest = { editingNote = null },
            title = {
                Text(
                    text = "Chỉnh sửa ghi chú (${note.timestampLabel})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                OutlinedTextField(
                    value = editDraftText,
                    onValueChange = { editDraftText = it },
                    label = { Text("Nội dung ghi chú") },
                    minLines = 3,
                    maxLines = 8,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val cleaned = editDraftText.trim()
                        if (cleaned.isNotEmpty()) {
                            onUpdateNote(note.id, cleaned)
                        }
                        editingNote = null
                    }
                ) {
                    Text("Lưu thay đổi")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingNote = null }) {
                    Text("Hủy")
                }
            },
        )
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
                    tint = if (isAcademic) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.secondary,
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
                            MaterialTheme.colorScheme.tertiaryContainer
                        } else {
                            MaterialTheme.colorScheme.secondaryContainer
                        },
                    ) {
                        Text(
                            text = concept.sourceLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isAcademic) {
                                MaterialTheme.colorScheme.onTertiaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            },
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
private fun OfflineCacheBanner() {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Default.CloudOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = "Đang xem bản lưu ngoại tuyến (không có kết nối mạng)",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun HighlightLegendAndExportBar(
    onExportMarkdown: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LegendChip(
                label = "Bài cũ",
                bg = MaterialTheme.colorScheme.secondaryContainer,
                fg = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            LegendChip(
                label = "Học thuật",
                bg = MaterialTheme.colorScheme.tertiaryContainer,
                fg = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            LegendChip(
                label = "Chưa hiểu",
                bg = MaterialTheme.colorScheme.errorContainer,
                fg = MaterialTheme.colorScheme.onErrorContainer,
            )
        }

        if (onExportMarkdown != null) {
            Surface(
                onClick = onExportMarkdown,
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.IosShare,
                        contentDescription = "Xuất vở Markdown",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        text = "Xuất .md",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
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

/**
 * Thanh phát audio thu gọn gắn đáy màn hình (Media3 ExoPlayer).
 * Hiển thị nút Play/Pause, thanh tua Slider mm:ss / mm:ss, nút tốc độ 1.0x..2.0x và nút đóng.
 */
@Composable
fun CompactAudioPlayerBar(
    audioState: AudioPlayerUiState,
    onTogglePlayPause: () -> Unit,
    onSeekToMs: (Long) -> Unit,
    onCycleSpeed: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!audioState.isVisible) return

    val durationMs = audioState.durationMs.coerceAtLeast(1L)
    val currentMs = audioState.currentPositionMs.coerceIn(0L, durationMs)
    val sliderFraction = if (audioState.durationMs > 0L) {
        (currentMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp,
        shadowElevation = 6.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 4.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            if (!audioState.errorMessage.isNullOrBlank()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = audioState.errorMessage,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onClose, modifier = Modifier.size(28.dp)) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Đóng trình phát",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = onTogglePlayPause,
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            imageVector = if (audioState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (audioState.isPlaying) "Tạm dừng" else "Phát",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp),
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Slider(
                            value = sliderFraction,
                            onValueChange = { frac ->
                                if (audioState.durationMs > 0L) {
                                    onSeekToMs((frac * audioState.durationMs).toLong())
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(26.dp),
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = formatMsLabel(audioState.currentPositionMs),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = if (audioState.durationMs > 0L) formatMsLabel(audioState.durationMs) else "--:--",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    Spacer(Modifier.width(6.dp))

                    Surface(
                        onClick = onCycleSpeed,
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                    ) {
                        val speedLabel = if (audioState.playbackSpeed % 1f == 0f) {
                            "${audioState.playbackSpeed.toInt()}.0x"
                        } else {
                            "${audioState.playbackSpeed}x"
                        }
                        Text(
                            text = speedLabel,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                        )
                    }

                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.size(30.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Đóng thanh phát",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }
    }
}

private fun formatMsLabel(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    val mins = totalSec / 60L
    val secs = totalSec % 60L
    return "%02d:%02d".format(mins, secs)
}

@Composable
private fun AnnotatedSlidePageCard(
    page: AnnotatedSlidePageUi,
    onClickConcept: (InlineConceptHighlight) -> Unit,
    onToggleConfusedBlock: (String) -> Unit,
    onPlayTimestamp: (Float) -> Unit,
    onStartEditNote: (InlineLecturerNote) -> Unit,
    onDeleteNote: (String) -> Unit,
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
                    onStartEditNote = onStartEditNote,
                    onDeleteNote = onDeleteNote,
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
    onStartEditNote: (InlineLecturerNote) -> Unit,
    onDeleteNote: (String) -> Unit,
) {
    val containerColor = when {
        block.isMarkedConfused -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f)
        block.isCurrentlySpoken -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.50f)
        else -> Color.Transparent
    }
    val textColor = if (block.isMarkedConfused) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    val prevBg = MaterialTheme.colorScheme.secondaryContainer
    val prevFg = MaterialTheme.colorScheme.onSecondaryContainer
    val acadBg = MaterialTheme.colorScheme.tertiaryContainer
    val acadFg = MaterialTheme.colorScheme.onTertiaryContainer

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
            // Nội dung gốc từ slide với highlight inline tương thích Dark/Light Mode
            val annotatedText = remember(block.originalSlideText, block.highlights, prevBg, prevFg, acadBg, acadFg) {
                buildAnnotatedString {
                    val base = if (block.kind == "bullet") "• ${block.originalSlideText}" else block.originalSlideText
                    append(base)
                    block.highlights.forEachIndexed { idx, hl ->
                        val start = base.indexOf(hl.term, ignoreCase = true)
                        if (start >= 0) {
                            val end = start + hl.term.length
                            val bg = if (hl.sourceType == HighlightSourceType.ACADEMIC_INTERNET) acadBg else prevBg
                            val fg = if (hl.sourceType == HighlightSourceType.ACADEMIC_INTERNET) acadFg else prevFg
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
                    color = textColor,
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
                tint = if (block.isMarkedConfused) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline,
                modifier = Modifier
                    .padding(start = 6.dp, top = 2.dp)
                    .size(18.dp)
                    .clickable(onClick = onToggleConfused),
            )
        }

        // Ghi chú giảng viên phân tích chèn TRỰC TIẾP ngay dưới ý slide tương ứng
        block.inlineNotes.forEach { note ->
            var menuExpanded by remember(note.id) { mutableStateOf(false) }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.50f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.30f)),
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
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f),
                        ) {
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
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
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

                            // Menu 3 chấm Sửa / Xóa ghi chú
                            Box {
                                IconButton(
                                    onClick = { menuExpanded = true },
                                    modifier = Modifier.size(26.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.MoreVert,
                                        contentDescription = "Tùy chọn ghi chú",
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                                DropdownMenu(
                                    expanded = menuExpanded,
                                    onDismissRequest = { menuExpanded = false },
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Chỉnh sửa ghi chú") },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.Edit,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp),
                                            )
                                        },
                                        onClick = {
                                            menuExpanded = false
                                            onStartEditNote(note)
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = "Xóa ghi chú",
                                                color = MaterialTheme.colorScheme.error,
                                            )
                                        },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(18.dp),
                                            )
                                        },
                                        onClick = {
                                            menuExpanded = false
                                            onDeleteNote(note.id)
                                        },
                                    )
                                }
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
