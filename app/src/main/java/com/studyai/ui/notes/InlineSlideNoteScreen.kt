package com.studyai.ui.notes

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.outlined.OutlinedFlag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.studyai.audio.AudioPlayerUiState
import com.studyai.ui.dictionary.LivingDictionaryTextToolbar
import com.studyai.ui.theme.StudyAiCodeTextStyle
import com.studyai.ui.theme.StudyAiExtras
import com.studyai.ui.theme.StudyAiPalette
import com.studyai.ui.theme.StudyAiTimestampTextStyle

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
    val isEdited: Boolean = false,
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
    val timeRangeLabel: String = "",
    val blocks: List<SlideDocumentBlockUi>,
)

private fun isCodeLikeBlock(kind: String, text: String): Boolean {
    if (kind.equals("code", ignoreCase = true) || kind.equals("formula", ignoreCase = true)) {
        return true
    }
    val upper = text.uppercase().trimStart()
    val sqlOrCodePrefixes = listOf(
        "DECLARE ", "SELECT ", "INSERT ", "UPDATE ", "DELETE ", "CREATE ",
        "SET @", "PRINT ", "BEGIN", "END;", "IF (", "IF(", "WHILE ", "ELSE",
        "DEF ", "CLASS ", "IMPORT ", "RETURN ", "FOR ", "FROM ", "WHERE ",
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

private fun splitNoteHeadlineAndContent(raw: String): Pair<String, String> {
    val trimmed = raw.trim()
    val colonIdx = trimmed.indexOf(':')
    if (colonIdx in 2..75) {
        val candidate = trimmed.substring(0, colonIdx).trim()
        val rest = trimmed.substring(colonIdx + 1).trim()
        val afterColonIsSpace = trimmed.getOrNull(colonIdx + 1)?.isWhitespace() == true
        if (rest.isNotEmpty() && afterColonIsSpace && !candidate.contains('\n') && !candidate.startsWith("http", ignoreCase = true)) {
            return candidate to rest
        }
    }
    return "" to trimmed
}

/**
 * Màn hình 1: Vở Ghi Chép Bám Slide (StudyAI) theo đúng đặc tả thiết kế screen1_compose_design_spec.md.
 */
@Composable
fun InlineSlideNoteDocumentScreen(
    pages: List<AnnotatedSlidePageUi>,
    activeSpokenBlockId: String? = null,
    targetSlidePage: Int? = null,
    onClearTargetSlidePage: () -> Unit = {},
    scrollToTopNonce: Int = 0,
    isOfflineCache: Boolean = false,
    onToggleConfusedBlock: (blockId: String) -> Unit = {},
    onPlayTimestamp: (Float) -> Unit = {},
    onUpdateNote: (noteId: String, newText: String) -> Unit = { _, _ -> },
    onDeleteNote: (noteId: String) -> Unit = {},
    onLookupSelectedText: (selectedText: String, surroundingSentence: String, slideNumber: Int?) -> Unit = { _, _, _ -> },
    @Suppress("UNUSED_PARAMETER") onExportMarkdown: (() -> Unit)? = null,
) {
    val isDark = StudyAiExtras.isDark
    val hostView = LocalView.current
    @Suppress("DEPRECATION")
    val clipboardManager = LocalClipboardManager.current

    var activePopupConcept by remember { mutableStateOf<InlineConceptHighlight?>(null) }
    var activePopupConceptSlide by remember { mutableStateOf<Int?>(null) }
    var editingNote by remember { mutableStateOf<InlineLecturerNote?>(null) }
    var editDraftText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    val headerOffset = (if (isOfflineCache) 1 else 0) + 1
    val totalNotes = remember(pages) {
        pages.sumOf { page -> page.blocks.sumOf { it.inlineNotes.size } }
    }

    val livingToolbar = remember(hostView, pages, headerOffset) {
        LivingDictionaryTextToolbar(view = hostView) {
            @Suppress("DEPRECATION")
            val selected = clipboardManager.getText()?.text?.trim().orEmpty()
            if (selected.isNotEmpty()) {
                val visiblePageIndices = listState.layoutInfo.visibleItemsInfo
                    .map { it.index - headerOffset }
                    .filter { it in pages.indices }
                val orderedPages = visiblePageIndices.map { pages[it] } +
                    pages.filterIndexed { idx, _ -> idx !in visiblePageIndices }

                var matchedSlideNo: Int? = null
                var matchedSentence: String = selected
                for (p in orderedPages) {
                    val blockHit = p.blocks.firstOrNull { b ->
                        b.originalSlideText.contains(selected, ignoreCase = true) ||
                            b.inlineNotes.any { n ->
                                n.explanationText.contains(selected, ignoreCase = true) ||
                                    n.evidenceQuote.contains(selected, ignoreCase = true)
                            }
                    }
                    if (blockHit != null) {
                        matchedSlideNo = p.pageNumber
                        matchedSentence = when {
                            blockHit.originalSlideText.contains(selected, ignoreCase = true) -> blockHit.originalSlideText
                            else -> blockHit.inlineNotes.firstOrNull {
                                it.explanationText.contains(selected, ignoreCase = true)
                            }?.explanationText ?: blockHit.inlineNotes.firstOrNull {
                                it.evidenceQuote.contains(selected, ignoreCase = true)
                            }?.evidenceQuote ?: selected
                        }
                        break
                    }
                }
                if (matchedSlideNo == null && visiblePageIndices.isNotEmpty()) {
                    matchedSlideNo = pages[visiblePageIndices.first()].pageNumber
                }
                onLookupSelectedText(selected, matchedSentence, matchedSlideNo)
            }
        }
    }

    LaunchedEffect(scrollToTopNonce) {
        if (scrollToTopNonce > 0) {
            listState.animateScrollToItem(0)
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

    CompositionLocalProvider(LocalTextToolbar provides livingToolbar) {
        SelectionContainer {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (isOfflineCache) {
                    item(key = "offline_banner") {
                        DisableSelection {
                            OfflineCacheBanner()
                        }
                    }
                }

                // Hàng 3: Thanh chú giải siêu gọn, không bọc card
                item(key = "legend_bar") {
                    DisableSelection {
                        CompactLegendAndNoteCountBar(totalNotes = totalNotes)
                    }
                }

                if (pages.isEmpty()) {
                    item(key = "empty_notes_card") {
                        DisableSelection {
                            Surface(
                                shape = RoundedCornerShape(24.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
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
                                        tint = StudyAiPalette.PrimaryCobalt,
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
                }

                items(pages, key = { it.pageNumber }) { page ->
                    AnnotatedSlidePageCard(
                        page = page,
                        isDark = isDark,
                        onClickConcept = { hl ->
                            activePopupConcept = hl
                            activePopupConceptSlide = page.pageNumber
                        },
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
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = StudyAiPalette.PrimaryCobalt,
                        contentColor = Color.White,
                    ),
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
                    tint = if (isAcademic) StudyAiPalette.Amber else StudyAiPalette.Teal,
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
                Button(
                    onClick = {
                        val term = concept.term
                        val explanation = concept.explanation
                        val slideNo = activePopupConceptSlide
                        activePopupConcept = null
                        onLookupSelectedText(term, explanation, slideNo)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = StudyAiPalette.PrimaryCobalt,
                        contentColor = Color.White,
                    ),
                ) {
                    Text("Mở Từ điển sống")
                }
            },
            dismissButton = {
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
        border = BorderStroke(1.dp, StudyAiPalette.Amber.copy(alpha = 0.45f)),
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
private fun CompactLegendAndNoteCountBar(
    totalNotes: Int,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LegendDotItem(color = StudyAiPalette.Teal, label = "Bài trước")
            LegendDotItem(color = StudyAiPalette.Amber, label = "Từ điển")
            LegendDotItem(color = StudyAiPalette.Rose, label = "Chưa hiểu")
        }

        Text(
            text = "$totalNotes ghi chú AI",
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LegendDotItem(
    color: Color,
    label: String,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = label,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
        border = BorderStroke(1.dp, StudyAiPalette.PrimaryCobalt.copy(alpha = 0.35f)),
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
                        color = StudyAiPalette.Rose,
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
                        color = StudyAiPalette.PrimaryCobalt,
                        modifier = Modifier.size(36.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (audioState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (audioState.isPlaying) "Tạm dừng" else "Phát",
                                tint = Color.White,
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
    isDark: Boolean,
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
    val borderColor = if (hasActiveSpokenBlock) {
        StudyAiPalette.PrimaryCobalt
    } else {
        MaterialTheme.colorScheme.outline
    }
    val primarySoft = if (isDark) StudyAiPalette.PrimarySoftDark else StudyAiPalette.PrimarySoftLight
    val primaryText = if (isDark) StudyAiPalette.PrimaryTextDark else StudyAiPalette.PrimaryTextLight

    val visibleBlocks = remember(page.blocks, displayTitle, page.pageNumber) {
        page.blocks.filterIndexed { idx, block ->
            if (idx == 0 &&
                page.blocks.size > 1 &&
                block.kind.equals("heading", ignoreCase = true) &&
                cleanSlideTitle(block.originalSlideText, page.pageNumber).equals(displayTitle, ignoreCase = true) &&
                block.inlineNotes.isEmpty() &&
                block.highlights.isEmpty() &&
                !block.isMarkedConfused &&
                !block.isCurrentlySpoken
            ) {
                false
            } else {
                true
            }
        }
    }

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = if (hasActiveSpokenBlock) 1.5.dp else 1.dp,
            color = borderColor,
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
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = primarySoft,
                ) {
                    Text(
                        text = "Slide %02d".format(page.pageNumber),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = primaryText,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }

                Text(
                    text = displayTitle,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )

                if (page.timeRangeLabel.isNotBlank()) {
                    Text(
                        text = page.timeRangeLabel,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }

            HorizontalDivider(
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.outline,
            )

            visibleBlocks.forEach { block ->
                SlideBlockRow(
                    block = block,
                    isDark = isDark,
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
    isDark: Boolean,
    onClickConcept: (InlineConceptHighlight) -> Unit,
    onToggleConfused: () -> Unit,
    onPlayTimestamp: (Float) -> Unit,
    onStartEditNote: (InlineLecturerNote) -> Unit,
    onDeleteNote: (String) -> Unit,
) {
    val isCode = remember(block.kind, block.originalSlideText) {
        isCodeLikeBlock(block.kind, block.originalSlideText)
    }

    val roseSoft = if (isDark) StudyAiPalette.RoseSoftDark else StudyAiPalette.RoseSoftLight
    val roseText = if (isDark) StudyAiPalette.RoseTextDark else StudyAiPalette.RoseTextLight
    val primarySoft = if (isDark) StudyAiPalette.PrimarySoftDark else StudyAiPalette.PrimarySoftLight
    val prevBg = if (isDark) StudyAiPalette.TealSoftDark else StudyAiPalette.TealSoftLight
    val prevFg = if (isDark) StudyAiPalette.TealTextDark else StudyAiPalette.TealTextLight
    val acadBg = if (isDark) StudyAiPalette.AmberSoftDark else StudyAiPalette.AmberSoftLight
    val acadFg = if (isDark) StudyAiPalette.AmberTextDark else StudyAiPalette.AmberTextLight

    val blockWrapperBg = when {
        block.isMarkedConfused -> roseSoft
        block.isCurrentlySpoken -> primarySoft
        else -> Color.Transparent
    }
    val blockWrapperBorder = when {
        block.isMarkedConfused -> BorderStroke(1.dp, StudyAiPalette.Rose.copy(alpha = 0.35f))
        block.isCurrentlySpoken -> BorderStroke(1.dp, StudyAiPalette.PrimaryCobalt.copy(alpha = 0.4f))
        else -> null
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = blockWrapperBg,
            border = blockWrapperBorder,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(
                    horizontal = if (blockWrapperBg == Color.Transparent) 0.dp else 10.dp,
                    vertical = if (blockWrapperBg == Color.Transparent) 2.dp else 10.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (block.isCurrentlySpoken) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.GraphicEq,
                            contentDescription = null,
                            tint = StudyAiPalette.PrimaryCobalt,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "Giảng viên đang nói đoạn này",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isDark) StudyAiPalette.PrimaryTextDark else StudyAiPalette.PrimaryTextLight,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                if (block.isMarkedConfused) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Flag,
                            contentDescription = null,
                            tint = StudyAiPalette.Rose,
                            modifier = Modifier.size(13.dp),
                        )
                        Text(
                            text = "Đã ghim: Cần xem lại đoạn này",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = roseText,
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                ) {
                    if (isCode) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = StudyAiPalette.CodeBg,
                            modifier = Modifier.weight(1f),
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                                    .padding(12.dp)
                            ) {
                                Text(
                                    text = block.originalSlideText,
                                    style = StudyAiCodeTextStyle,
                                    color = StudyAiPalette.CodeInk,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.5.sp,
                                )
                            }
                        }
                    } else {
                        val annotatedText = remember(
                            block.originalSlideText,
                            block.kind,
                            block.highlights,
                            prevBg,
                            prevFg,
                            acadBg,
                            acadFg,
                        ) {
                            buildAnnotatedString {
                                val base = if (block.kind == "bullet") "• ${block.originalSlideText}" else block.originalSlideText
                                append(base)
                                block.highlights.forEachIndexed { idx, hl ->
                                    val start = base.indexOf(hl.term, ignoreCase = true)
                                    if (start >= 0) {
                                        val end = start + hl.term.length
                                        val bg = if (hl.sourceType == HighlightSourceType.ACADEMIC_INTERNET) acadBg else prevBg
                                        val fg = if (hl.sourceType == HighlightSourceType.ACADEMIC_INTERNET) acadFg else prevFg
                                        addLink(
                                            clickable = LinkAnnotation.Clickable(
                                                tag = "CONCEPT_$idx",
                                                styles = TextLinkStyles(
                                                    style = SpanStyle(
                                                        background = bg,
                                                        color = fg,
                                                        fontWeight = FontWeight.SemiBold,
                                                        textDecoration = TextDecoration.Underline,
                                                    )
                                                ),
                                                linkInteractionListener = { onClickConcept(hl) },
                                            ),
                                            start = start,
                                            end = end,
                                        )
                                    }
                                }
                            }
                        }

                        val textColor = when {
                            block.isMarkedConfused -> roseText
                            block.kind == "heading" -> MaterialTheme.colorScheme.onSurface
                            else -> if (isDark) StudyAiPalette.InkSecondaryDark else StudyAiPalette.InkSecondaryLight
                        }

                        val baseTextStyle = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 13.5.sp,
                            lineHeight = 21.sp,
                            color = textColor,
                            fontWeight = if (block.kind == "heading") FontWeight.SemiBold else FontWeight.Normal,
                            fontStyle = if (block.kind == "formula") FontStyle.Italic else FontStyle.Normal,
                        )

                        Text(
                            text = annotatedText,
                            style = baseTextStyle,
                            modifier = Modifier
                                .weight(1f)
                                .padding(top = 2.dp),
                        )
                    }

                    Surface(
                        onClick = onToggleConfused,
                        shape = CircleShape,
                        color = if (block.isMarkedConfused) StudyAiPalette.Rose.copy(alpha = 0.14f) else Color.Transparent,
                        modifier = Modifier
                            .padding(start = 8.dp, top = 1.dp)
                            .size(28.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (block.isMarkedConfused) Icons.Default.Flag else Icons.Outlined.OutlinedFlag,
                                contentDescription = "Đánh dấu chưa hiểu đoạn này",
                                tint = if (block.isMarkedConfused) StudyAiPalette.Rose else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            }
        }

        // Các thẻ ghi chú Giảng viên 3 tầng có vạch màu dọc 3dp bên trái
        block.inlineNotes.forEach { note ->
            val (headline, content) = remember(note.id, note.explanationText) {
                splitNoteHeadlineAndContent(note.explanationText)
            }
            LecturerNoteCallout(
                category = note.category,
                headline = headline,
                content = content,
                timestampLabel = note.timestampLabel,
                evidenceQuote = note.evidenceQuote,
                isEdited = note.isEdited,
                isDark = isDark,
                onPlayClick = { onPlayTimestamp(note.timestampSec) },
                onEditClick = { onStartEditNote(note) },
                onDeleteClick = { onDeleteNote(note.id) },
            )
        }
    }
}

@Composable
fun LecturerNoteCallout(
    category: String, // "core_explanation" | "worked_example" | "exam_tip"
    headline: String,
    content: String,
    timestampLabel: String,
    isEdited: Boolean,
    isDark: Boolean,
    onPlayClick: () -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
    evidenceQuote: String = "",
) {
    val (accent, softBg, labelText, titleText) = when (category) {
        "worked_example" -> Quadruple(
            StudyAiPalette.Teal,
            if (isDark) StudyAiPalette.TealSoftDark else StudyAiPalette.TealSoftLight,
            if (isDark) StudyAiPalette.TealTextDark else StudyAiPalette.TealTextLight,
            "Ví dụ thực tế"
        )
        "exam_tip" -> Quadruple(
            StudyAiPalette.Amber,
            if (isDark) StudyAiPalette.AmberSoftDark else StudyAiPalette.AmberSoftLight,
            if (isDark) StudyAiPalette.AmberTextDark else StudyAiPalette.AmberTextLight,
            "Lưu ý / Mẹo thi"
        )
        else -> Quadruple(
            StudyAiPalette.PrimaryCobalt,
            if (isDark) StudyAiPalette.PrimarySoftDark else StudyAiPalette.PrimarySoftLight,
            if (isDark) StudyAiPalette.PrimaryTextDark else StudyAiPalette.PrimaryTextLight,
            "Giải thích trực quan"
        )
    }

    val playTint = if (isDark) StudyAiPalette.PrimaryTextDark else StudyAiPalette.PrimaryCobalt
    var menuExpanded by remember { mutableStateOf(false) }
    var quoteExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
            .clip(RoundedCornerShape(topEnd = 14.dp, bottomEnd = 14.dp))
            .background(softBg)
            .height(IntrinsicSize.Min)
    ) {
        // Vạch màu dọc 3dp bên trái
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight()
                .background(accent)
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp, end = 10.dp, top = 10.dp, bottom = 10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isEdited) "$titleText • Đã sửa" else titleText,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = labelText
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Surface(
                        onClick = onPlayClick,
                        shape = RoundedCornerShape(999.dp),
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Nghe lại",
                                tint = playTint,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(Modifier.width(2.dp))
                            Text(
                                text = timestampLabel,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = playTint
                            )
                        }
                    }
                    Box {
                        IconButton(onClick = { menuExpanded = true }, modifier = Modifier.size(26.dp)) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Tùy chọn",
                                tint = labelText,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                            DropdownMenuItem(
                                text = { Text("Chỉnh sửa") },
                                onClick = {
                                    menuExpanded = false
                                    onEditClick()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Xóa ghi chú", color = StudyAiPalette.Rose) },
                                onClick = {
                                    menuExpanded = false
                                    onDeleteClick()
                                }
                            )
                        }
                    }
                }
            }

            if (headline.isNotBlank()) {
                Text(
                    text = headline,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            Text(
                text = content,
                style = MaterialTheme.typography.bodySmall,
                lineHeight = 19.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
            )

            val cleanedQuote = evidenceQuote.trim()
            if (cleanedQuote.isNotEmpty() && cleanedQuote != content && cleanedQuote != "$headline: $content") {
                Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { quoteExpanded = !quoteExpanded }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Icon(
                            imageVector = if (quoteExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp),
                        )
                        Text(
                            text = if (quoteExpanded) "Ẩn trích dẫn lời giảng" else "Xem trích dẫn lời giảng",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    AnimatedVisibility(visible = quoteExpanded) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                        ) {
                            Text(
                                text = "\"$cleanedQuote\"",
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

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
