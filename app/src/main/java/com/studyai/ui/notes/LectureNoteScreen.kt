package com.studyai.ui.notes

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.studyai.ui.dictionary.CrossChapterLinkUiModel
import com.studyai.ui.dictionary.CrossChapterLinksSection

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LectureNoteScreen(
    lectureTitle: String,
    currentLectureId: Long = 1L,
    chapters: List<ChapterUiModel> = emptyList(),
    onSelectLecture: (Long) -> Unit = {},
    slides: List<SlideUi> = emptyList(),
    currentSlideIdx: Int = 0,
    onSlideIdxChange: (Int) -> Unit = {},
    targetAudioSec: Float? = null,
    onClearTargetAudio: () -> Unit = {},
    onSeekAudio: (Float) -> Unit = {},
    onSelectTerm: (String) -> Unit = {},
    onNavigateToChapterSlide: (Int, Int, Float) -> Unit = { _, _, _ -> }
) {
    val defaultChapters = remember {
        listOf(
            ChapterUiModel(1L, "Chapter 1: Tổng quan AI & Tác tử", 25),
            ChapterUiModel(2L, "Chapter 2: Không gian Trạng thái & BFS/DFS", 25),
            ChapterUiModel(3L, "Chapter 3: Tìm kiếm có Thông tin & A*", 25),
            ChapterUiModel(4L, "Chapter 4: Tìm kiếm Cục bộ", 25),
            ChapterUiModel(5L, "Chapter 5: Mô hình Sinh Sâu (GAN)", 25)
        )
    }
    val activeChapters = if (chapters.isNotEmpty()) chapters else defaultChapters

    var isPlaying by remember { mutableStateOf(false) }
    var currentPlaybackSec by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(targetAudioSec) {
        targetAudioSec?.let { sec ->
            currentPlaybackSec = sec
            isPlaying = true
            onSeekAudio(sec)
            onClearTargetAudio()
        }
    }

    val safeSlideIdx = currentSlideIdx.coerceIn(0, (slides.size - 1).coerceAtLeast(0))
    val currentSlide = slides.getOrNull(safeSlideIdx)

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text(lectureTitle, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1)
                            Text(
                                "Trang ${safeSlideIdx + 1}/${slides.size.coerceAtLeast(1)} • Căn gióng đồng bộ thời gian thực",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                )
                // Thanh chọn Chapter nhanh (Pillar 3 Cross-Chapter Quick Selector)
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(activeChapters, key = { it.id }) { chap ->
                        val isSelected = chap.id == currentLectureId
                        FilterChip(
                            selected = isSelected,
                            onClick = { onSelectLecture(chap.id) },
                            label = {
                                Text(
                                    text = "Chương ${chap.id}",
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 12.sp
                                )
                            },
                            leadingIcon = if (isSelected) {
                                {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            } else null,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // 1. Khung Trình chiếu Slide hiện tại
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F5F9))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Slide ${currentSlide?.slideNumber ?: (safeSlideIdx + 1)}",
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (currentSlide != null) {
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = "Mốc bài giảng: ${formatSec(currentSlide.startTimeSec)} - ${formatSec(currentSlide.endTimeSec)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = currentSlide?.extractedText ?: "Chưa có nội dung slide",
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 6
                    )

                    // Thuật ngữ chuyên ngành trong slide
                    if (currentSlide?.terms?.isNotEmpty() == true) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            currentSlide.terms.take(4).forEach { term ->
                                SuggestionChip(
                                    onClick = { onSelectTerm(term) },
                                    label = { Text(term, style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                        }
                    }
                }
            }

            // 1.5. Liên kết chéo liên chương (Nếu slide có trích dẫn/ôn lại bài trước)
            if (currentSlide?.crossChapterLinks?.isNotEmpty() == true) {
                Box(modifier = Modifier.padding(horizontal = 12.dp)) {
                    CrossChapterLinksSection(
                        links = currentSlide.crossChapterLinks,
                        onNavigateToChapterSlide = onNavigateToChapterSlide
                    )
                }
            }

            // 2. Thanh điều hướng Chuyển Slide & Điều khiển Audio Player
            Surface(
                tonalElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { if (safeSlideIdx > 0) onSlideIdxChange(safeSlideIdx - 1) },
                        enabled = safeSlideIdx > 0
                    ) {
                        Icon(Icons.Default.ArrowBackIosNew, contentDescription = "Slide trước")
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = {
                            isPlaying = !isPlaying
                        }) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.PauseCircle else Icons.Default.PlayCircle,
                                contentDescription = "Phát/Tạm dừng",
                                modifier = Modifier.size(36.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = formatSec(currentPlaybackSec),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    IconButton(
                        onClick = { if (safeSlideIdx < slides.size - 1) onSlideIdxChange(safeSlideIdx + 1) },
                        enabled = safeSlideIdx < slides.size - 1
                    ) {
                        Icon(Icons.Default.ArrowForwardIos, contentDescription = "Slide sau")
                    }
                }
            }

            // 3. Danh sách Ghi chú Bổ sung ngoài Slide (Novelty >= 0.28)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Ghi chú Lời giảng Mở rộng (${currentSlide?.notes?.size ?: 0}):",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Lọc bởi Novelty & MMR",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                if (currentSlide?.notes.isNullOrEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Trang slide này giảng viên đọc bám sát tài liệu,\nkhông phát sinh thông tin mở rộng ngoài slide.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(currentSlide!!.notes, key = { it.id }) { note ->
                            val isHighlighted = isPlaying && currentPlaybackSec >= note.startTimeSec && currentPlaybackSec <= note.endTimeSec
                            NoteCardItem(
                                note = note,
                                isHighlighted = isHighlighted,
                                onJumpAudio = { sec ->
                                    currentPlaybackSec = sec
                                    isPlaying = true
                                    onSeekAudio(sec)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun NoteCardItem(
    note: NoteUi,
    isHighlighted: Boolean = false,
    onJumpAudio: (Float) -> Unit
) {
    var showQuote by remember { mutableStateOf(false) }

    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        border = if (isHighlighted) {
            CardDefaults.outlinedCardBorder().copy(
                brush = SolidColor(MaterialTheme.colorScheme.primary),
                width = 2.dp
            )
        } else {
            CardDefaults.outlinedCardBorder()
        },
        colors = CardDefaults.outlinedCardColors(
            containerColor = if (isHighlighted) Color(0xFFF0FDF4) else MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = Color(0xFFE8F5E9),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = "Novelty: ${String.format("%.2f", note.noveltyScore)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF2E7D32),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                AssistChip(
                    onClick = { onJumpAudio(note.startTimeSec) },
                    leadingIcon = {
                        Icon(
                            Icons.Default.VolumeUp,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                    },
                    label = {
                        Text(
                            text = "${formatSec(note.startTimeSec)} - ${formatSec(note.endTimeSec)}",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "• ${note.bulletPoint}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )

            if (note.verbatimQuote.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (showQuote) "Ẩn bằng chứng gốc" else "Xem bằng chứng lời nói gốc",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable { showQuote = !showQuote }
                )

                AnimatedVisibility(visible = showQuote) {
                    Surface(
                        color = Color(0xFFF8FAFC),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                    ) {
                        Text(
                            text = "\"${note.verbatimQuote}\"",
                            style = MaterialTheme.typography.bodySmall,
                            fontStyle = FontStyle.Italic,
                            modifier = Modifier.padding(8.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

fun formatSec(seconds: Float): String {
    val secInt = seconds.toInt()
    val m = secInt / 60
    val s = secInt % 60
    return "%02d:%02d".format(m, s)
}
