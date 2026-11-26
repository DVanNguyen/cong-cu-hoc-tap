package com.studyai.ui.notes

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.outlined.OutlinedFlag
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.studyai.audio.AudioPlayerUiState
import com.studyai.ui.theme.StudyAiCodeTextStyle
import com.studyai.ui.theme.StudyAiExtras
import com.studyai.ui.theme.StudyAiTimestampTextStyle
import kotlinx.coroutines.launch

enum class HighlightSourceType {
    PREVIOUS_LECTURE,
    ACADEMIC_INTERNET,
}

data class InlineConceptHighlight(
    val term: String,
    val sourceType: HighlightSourceType,
    val sourceLabel: String,
    val explanation: String,
)

data class InlineLecturerNote(
    val id: String,
    val explanationText: String,
    val timestampSec: Float,
    val timestampLabel: String,
    val category: String = "core_explanation",
    val evidenceQuote: String = "",
)

data class SlideDocumentBlockUi(
    val blockId: String,
    val kind: String = "bullet",
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

private fun isCodeLikeBlock(kind: String, text: String): Boolean {
    if (kind.equals("code", ignoreCase = true) || kind.equals("formula", ignoreCase = true)) {
        return true
    }
    val upper = text.uppercase()
    val sqlOrCodePrefixes = listOf(
        "DECLARE ", "SELECT ", "INSERT ", "UPDATE ", "DELETE ", "CREATE ",
        "SET @", "PRINT ", "BEGIN", "END;", "IF (", "IF(", "WHILE ", "ELSE",
        "DEF ", "CLASS ", "IMPORT ", "RETURN ", "FOR ", "FROM ",
    )
    if (sqlOrCodePrefixes.any { upper.startsWith(it) || upper.contains("\n$it") }) {
        return true
    }
    val syntaxScore = listOf(";", "{", "}", "()", "->", "@").count { text.contains(it) }
    return syntaxScore >= 2
}

private fun cleanSlideTitle(slideTitle: String, pageNumber: Int): String {
    val cleaned = slideTitle
        .replace(Regex("^Slide\\s+$pageNumber\\s*[:\\-–]\\s*", RegexOption.IGNORE_CASE), "")
        .replace(Regex("^Trang\\s+$pageNumber\\s*[:\\-–]\\s*", RegexOption.IGNORE_CASE), "")
        .trim()
    return cleaned.ifBlank { "Trang $pageNumber" }
}

/**
 * Màn hình Vở Ghi Chép Nhúng Trực Tiếp Trên Slide theo phong cách Editorial Academic Notebook.
 */
@Composable
fun InlineSlideNoteDocumentScreen(
    pages: List<AnnotatedSlidePageUi>,
    activeSpokenBlockId: String? = null,
    targetSlidePage: Int? = null,
    onClearTargetSlidePage: () -> Unit = {},
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
    val coroutineScope = rememberCoroutineScope()

    val headerOffset = (if (isOfflineCache) 1 else 0) + 1 + (if (pages.isNotEmpty()) 1 else 0)

    val currentVisiblePageNumber by remember(pages, headerOffset) {
        derivedStateOf {
            val pageIdx = (listState.firstVisibleItemIndex - headerOffset).coerceAtLeast(0)
            pages.getOrNull(pageIdx)?.pageNumber ?: pages.firstOrNull()?.pageNumber
        }
    }

    // Tự động cuộn mượt khi đang Live theo activeSpokenBlockId
    LaunchedEffect(activeSpokenBlockId, pages.size, headerOffset) {
        if (!activeSpokenBlockId.isNullOrBlank()) {
            val targetPageIdx = pages.indexOfFirst { page ->
                page.blocks.any { it.blockId == activeSpokenBlockId || it.isCurrentlySpoken }
            }
            if (targetPageIdx >= 0) {
                listState.animateScrollToItem(index = targetPageIdx + headerOffset)
            }
        }
    }

    // Tự động cuộn mượt khi điều hướng từ Lộ trình / Từ điển theo targetSlidePage
    LaunchedEffect(targetSlidePage, pages.size, headerOffset) {
        if (targetSlidePage != null && pages.isNotEmpty()) {
            val idx = pages.indexOfFirst { it.pageNumber == targetSlidePage }
            if (idx >= 0) {
                listState.animateScrollToItem(index = idx + headerOffset)
            }
            onClearTargetSlidePage()
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (isOfflineCache) {
            item(key = "offline_banner") {
                OfflineCacheBanner()
            }
        }

        item(key = "legend_bar") {
            HighlightLegendAndExportBar(onExportMarkdown = onExportMarkdown)
        }

        if (pages.isNotEmpty()) {
            item(key = "slide_quick_jump") {
                SlideQuickJumpStrip(
                    pages = pages,
                    activePageNumber = currentVisiblePageNumber,
                    onSelectPage = { idx ->
                        coroutineScope.launch {
                            listState.animateScrollToItem(index = idx + headerOffset)
                        }
                    },
                )
            }
        }

        if (pages.isEmpty()) {
            item(key = "empty_notes_card") {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.MenuBook,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(30.dp),
                        )
                        Text(
                            text = "Chưa có trang slide hoặc ghi chú nào",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            text = "Chuyển sang tab Thu âm để ghi âm bám slide trực tiếp hoặc tải cặp file Slide PDF + Audio để StudyAI biên tập vở ghi chép.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
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

    editingNote?.let { note ->
        AlertDialog(
            onDismissRequest = { editingNote = null },
            title = {
                Text(
                    text = "Chỉnh sửa ghi chú (${note.timestampLabel})",
                    style = MaterialTheme.typography.titleLarge,
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
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(6.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
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
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
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
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.45f)),
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
                text = "Đang đọc bản lưu ngoại tuyến (chế độ không mạng)",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
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
                label = "Cần xem lại",
                bg = MaterialTheme.colorScheme.errorContainer,
                fg = MaterialTheme.colorScheme.onErrorContainer,
            )
        }

        if (onExportMarkdown != null) {
            Surface(
                onClick = onExportMarkdown,
                shape = RoundedCornerShape(10.dp),
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
    Surface(shape = RoundedCornerShape(8.dp), color = bg) {
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
private fun SlideQuickJumpStrip(
    pages: List<AnnotatedSlidePageUi>,
    activePageNumber: Int?,
    onSelectPage: (index: Int) -> Unit,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        itemsIndexed(pages, key = { _, p -> p.pageNumber }) { idx, page ->
            val isActive = page.pageNumber == activePageNumber
            val hasNotes = page.blocks.any { it.inlineNotes.isNotEmpty() }
            val hasConfused = page.blocks.any { it.isMarkedConfused }

            Surface(
                onClick = { onSelectPage(idx) },
                shape = RoundedCornerShape(10.dp),
                color = if (isActive) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surface
                },
                border = BorderStroke(
                    width = if (isActive) 1.2.dp else 1.dp,
                    color = if (isActive) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
                    },
                ),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Text(
                        text = "Trang ${page.pageNumber}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                        color = if (isActive) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                    if (hasNotes) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                    }
                    if (hasConfused) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.error)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Thanh phát audio thu gọn gắn đáy màn hình (Media3 ExoPlayer).
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
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
        shadowElevation = 4.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
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
                    IconButton(onClick = onClose, modifier = Modifier.size(36.dp)) {
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
                    Surface(
                        onClick = onTogglePlayPause,
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (audioState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (audioState.isPlaying) "Tạm dừng" else "Phát",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }

                    Spacer(Modifier.width(8.dp))

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
                                .height(24.dp),
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = formatMsLabel(audioState.currentPositionMs),
                                style = StudyAiTimestampTextStyle,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = if (audioState.durationMs > 0L) formatMsLabel(audioState.durationMs) else "--:--",
                                style = StudyAiTimestampTextStyle,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    Spacer(Modifier.width(8.dp))

                    Surface(
                        onClick = onCycleSpeed,
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                    ) {
                        val speedLabel = if (audioState.playbackSpeed % 1f == 0f) {
                            "${audioState.playbackSpeed.toInt()}.0x"
                        } else {
                            "${audioState.playbackSpeed}x"
                        }
                        Text(
                            text = speedLabel,
                            style = StudyAiTimestampTextStyle,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }

                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.size(36.dp),
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
    val hasActiveSpokenBlock = page.blocks.any { it.isCurrentlySpoken }
    val displayTitle = remember(page.slideTitle, page.pageNumber) {
        cleanSlideTitle(page.slideTitle, page.pageNumber)
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = if (hasActiveSpokenBlock) 1.5.dp else 1.dp,
            color = if (hasActiveSpokenBlock) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
            },
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = displayTitle,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(10.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                ) {
                    Text(
                        text = "SLIDE %02d".format(page.pageNumber),
                        style = StudyAiTimestampTextStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }

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
    val extras = StudyAiExtras.colors
    val isCode = remember(block.kind, block.originalSlideText) {
        isCodeLikeBlock(block.kind, block.originalSlideText)
    }

    val containerColor = when {
        block.isMarkedConfused -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
        block.isCurrentlySpoken -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
        isCode -> extras.codeSurface
        else -> Color.Transparent
    }
    val containerBorder = when {
        block.isMarkedConfused -> BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.45f))
        block.isCurrentlySpoken -> BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f))
        isCode -> BorderStroke(1.dp, extras.codeBorder)
        else -> null
    }
    val textColor = when {
        block.isMarkedConfused -> MaterialTheme.colorScheme.onErrorContainer
        isCode -> extras.codeText
        else -> MaterialTheme.colorScheme.onSurface
    }

    val prevBg = MaterialTheme.colorScheme.secondaryContainer
    val prevFg = MaterialTheme.colorScheme.onSecondaryContainer
    val acadBg = MaterialTheme.colorScheme.tertiaryContainer
    val acadFg = MaterialTheme.colorScheme.onTertiaryContainer

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = containerColor,
            border = containerBorder,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(
                    horizontal = if (containerColor == Color.Transparent) 2.dp else 10.dp,
                    vertical = if (containerColor == Color.Transparent) 2.dp else 8.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(4.dp),
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
                            text = "Giảng viên đang nói đoạn này",
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
                    val annotatedText = remember(block.originalSlideText, block.highlights, isCode, prevBg, prevFg, acadBg, acadFg) {
                        buildAnnotatedString {
                            val base = if (!isCode && block.kind == "bullet") "• ${block.originalSlideText}" else block.originalSlideText
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

                    val baseTextStyle = if (isCode) {
                        StudyAiCodeTextStyle.copy(color = textColor)
                    } else {
                        MaterialTheme.typography.bodyMedium.copy(
                            color = textColor,
                            fontWeight = if (block.kind == "heading") FontWeight.SemiBold else FontWeight.Normal,
                            fontStyle = if (block.kind == "formula") FontStyle.Italic else FontStyle.Normal,
                        )
                    }

                    @Suppress("DEPRECATION")
                    ClickableText(
                        text = annotatedText,
                        style = baseTextStyle,
                        modifier = Modifier
                            .weight(1f)
                            .padding(top = 4.dp),
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

                    IconButton(
                        onClick = onToggleConfused,
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            imageVector = if (block.isMarkedConfused) Icons.Default.Flag else Icons.Outlined.OutlinedFlag,
                            contentDescription = "Đánh dấu chưa hiểu đoạn này",
                            tint = if (block.isMarkedConfused) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }

        // Các thẻ ghi chú AI phân loại 3 tầng đặt ngay dưới ý slide tương ứng
        block.inlineNotes.forEach { note ->
            LecturerNoteCallout(
                note = note,
                onPlayTimestamp = { onPlayTimestamp(note.timestampSec) },
                onStartEditNote = { onStartEditNote(note) },
                onDeleteNote = { onDeleteNote(note.id) },
            )
        }
    }
}

@Composable
private fun LecturerNoteCallout(
    note: InlineLecturerNote,
    onPlayTimestamp: () -> Unit,
    onStartEditNote: () -> Unit,
    onDeleteNote: () -> Unit,
) {
    val extras = StudyAiExtras.colors
    var menuExpanded by remember(note.id) { mutableStateOf(false) }
    var quoteExpanded by remember(note.id) { mutableStateOf(false) }

    val (bgColor, borderColor, badgeBg, badgeFg, categoryLabel, categoryIcon) = when (note.category) {
        "worked_example" -> CalloutStyle(
            bg = extras.exampleBg,
            border = extras.exampleBorder,
            badgeBg = extras.exampleBadgeBg,
            badgeFg = extras.exampleBadgeFg,
            label = "Ví dụ thực hành",
            icon = Icons.Default.Terminal,
        )
        "exam_tip" -> CalloutStyle(
            bg = extras.tipBg,
            border = extras.tipBorder,
            badgeBg = extras.tipBadgeBg,
            badgeFg = extras.tipBadgeFg,
            label = "Mẹo thi & Lưu ý",
            icon = Icons.Default.Lightbulb,
        )
        else -> CalloutStyle(
            bg = extras.coreBg,
            border = extras.coreBorder,
            badgeBg = extras.coreBadgeBg,
            badgeFg = extras.coreBadgeFg,
            label = "Giải thích cốt lõi",
            icon = Icons.AutoMirrored.Filled.MenuBook,
        )
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = bgColor,
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = RoundedCornerShape(7.dp),
                    color = badgeBg,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            imageVector = categoryIcon,
                            contentDescription = null,
                            tint = badgeFg,
                            modifier = Modifier.size(13.dp),
                        )
                        Text(
                            text = categoryLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = badgeFg,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        onClick = onPlayTimestamp,
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Nghe lại tại ${note.timestampLabel}",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(13.dp),
                            )
                            Text(
                                text = note.timestampLabel,
                                style = StudyAiTimestampTextStyle,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }

                    Box {
                        IconButton(
                            onClick = { menuExpanded = true },
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Tùy chọn ghi chú",
                                tint = badgeFg,
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
                                    onStartEditNote()
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
                                    onDeleteNote()
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

            if (note.evidenceQuote.isNotBlank()) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { quoteExpanded = !quoteExpanded }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            imageVector = if (quoteExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(15.dp),
                        )
                        Text(
                            text = if (quoteExpanded) "Ẩn trích dẫn lời giảng" else "Xem trích dẫn lời giảng",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    AnimatedVisibility(visible = quoteExpanded) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                        ) {
                            Text(
                                text = "\"${note.evidenceQuote.trim()}\"",
                                style = MaterialTheme.typography.bodySmall,
                                fontStyle = FontStyle.Italic,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

private data class CalloutStyle(
    val bg: Color,
    val border: Color,
    val badgeBg: Color,
    val badgeFg: Color,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
)
