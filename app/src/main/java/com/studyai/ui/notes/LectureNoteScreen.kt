package com.studyai.ui.notes

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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

data class ConceptContextChoice(
    val id: String,
    val term: String,
    val courseName: String,
    val location: String,
    val summary: String
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
            ChapterUiModel(1L, "Chương 1: Tổng quan AI & Tác tử", 25),
            ChapterUiModel(2L, "Chương 2: Không gian Trạng thái & BFS/DFS", 25),
            ChapterUiModel(3L, "Chương 3: Tìm kiếm có Thông tin & A*", 25),
            ChapterUiModel(4L, "Chương 4: Tìm kiếm Cục bộ", 25),
            ChapterUiModel(5L, "Chương 5: Mô hình Sinh Sâu (GAN)", 25)
        )
    }
    val activeChapters = if (chapters.isNotEmpty()) chapters else defaultChapters

    var isPlaying by remember { mutableStateOf(false) }
    var currentPlaybackSec by remember { mutableFloatStateOf(0f) }
    var showDisambiguationSheet by remember { mutableStateOf(false) }
    var selectedDisambiguationTerm by remember { mutableStateOf<String?>(null) }
    var confusionPinned by remember { mutableStateOf(false) }

    val sampleContexts = remember {
        listOf(
            ConceptContextChoice("1", "Tác tử thông minh", "Trí tuệ Nhân tạo (AI)", "Chương 1 • Slide 4", "Thực thể nhận thức môi trường qua Sensors và tối đa hóa độ đo hiệu năng (PEAS)."),
            ConceptContextChoice("2", "Tác tử thông minh", "Học máy (ML)", "Chương 6 • Slide 3", "Agent trong Học tăng cường (RL) học chính sách tối đa hóa phần thưởng tích lũy."),
            ConceptContextChoice("3", "Entropy", "Học máy (ML)", "Chương 2 • Slide 7", "Độ bất định phân lớp dùng để tách nút trong cây quyết định ID3 / C4.5."),
            ConceptContextChoice("4", "Entropy", "Lý thuyết Thông tin", "Chương 1 • Slide 3", "Số bit trung bình tối thiểu cần thiết để mã hóa một nguồn tin rời rạc theo Shannon."),
            ConceptContextChoice("5", "Transaction", "Hệ quản trị CSDL", "Bài 9 • Slide 14", "Khối lệnh đảm bảo 4 thuộc tính ACID (BEGIN TRAN / COMMIT / ROLLBACK)."),
            ConceptContextChoice("6", "Transaction", "Hệ điều hành", "Chương 5 • Slide 10", "Giao tác đồng thời liên quan đến tranh chấp khóa và giải thuật tránh Deadlock.")
        )
    }

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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // 1. Thanh chọn Chapter nhanh (Material 3 Expressive Chips)
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
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
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
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

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 2. Thẻ Slide Bài giảng
            item {
                OutlinedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.22f))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = "Slide ${currentSlide?.slideNumber ?: (safeSlideIdx + 1)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                            if (currentSlide != null) {
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = "Mốc bài giảng: ${formatSec(currentSlide.startTimeSec)} – ${formatSec(currentSlide.endTimeSec)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = currentSlide?.extractedText ?: "Chưa có nội dung slide",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            lineHeight = 22.sp
                        )

                        // Thuật ngữ chuyên ngành trong slide
                        if (currentSlide?.terms?.isNotEmpty() == true) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
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
            }

            // 3. Liên kết chéo liên chương (Nếu có)
            if (currentSlide?.crossChapterLinks?.isNotEmpty() == true) {
                item {
                    CrossChapterLinksSection(
                        links = currentSlide.crossChapterLinks,
                        onNavigateToChapterSlide = onNavigateToChapterSlide
                    )
                }
            }

            // 4. Khái niệm liên quan (Chọn ngữ cảnh môn khi khái niệm trùng tên)
            item {
                OutlinedCard(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Link,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Khái niệm liên quan (Chọn ngữ cảnh môn)",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Text(
                            text = "Khi khái niệm trùng tên giữa nhiều môn học, chạm vào thẻ để chọn định nghĩa phù hợp:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
                        )

                        val activeConcept = if (currentLectureId == 8L) "Transaction" else "Tác tử thông minh"
                        Surface(
                            onClick = {
                                selectedDisambiguationTerm = activeConcept
                                showDisambiguationSheet = true
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = activeConcept,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = MaterialTheme.colorScheme.primaryContainer
                                        ) {
                                            Text(
                                                text = if (currentLectureId == 8L) "Hệ quản trị CSDL" else "Trí tuệ Nhân tạo (AI)",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    Text(
                                        text = if (currentLectureId == 8L) "Bài 9 • Slide 14 — Đảm bảo tính toàn vẹn dữ liệu ACID" else "Chương 1 • Slide 4 — Nhận thức qua cảm biến & hành động theo mục tiêu",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Default.Tune,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }

            // 5. Thanh điều khiển Audio Player & Nút Ghim Chưa hiểu
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = { if (safeSlideIdx > 0) onSlideIdxChange(safeSlideIdx - 1) },
                                enabled = safeSlideIdx > 0
                            ) {
                                Icon(Icons.Default.ArrowBackIosNew, contentDescription = "Slide trước", modifier = Modifier.size(18.dp))
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { isPlaying = !isPlaying }) {
                                    Icon(
                                        imageVector = if (isPlaying) Icons.Default.PauseCircle else Icons.Default.PlayCircle,
                                        contentDescription = "Phát/Tạm dừng",
                                        modifier = Modifier.size(38.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = formatSec(currentPlaybackSec),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            IconButton(
                                onClick = { if (safeSlideIdx < slides.size - 1) onSlideIdxChange(safeSlideIdx + 1) },
                                enabled = safeSlideIdx < slides.size - 1
                            ) {
                                Icon(Icons.Default.ArrowForwardIos, contentDescription = "Slide sau", modifier = Modifier.size(18.dp))
                            }
                        }

                        // Tính năng thực tế: Nút Ghim chỗ chưa hiểu (Confusion Pin)
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedButton(
                            onClick = { confusionPinned = !confusionPinned },
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (confusionPinned) MaterialTheme.colorScheme.tertiaryContainer else Color.Transparent,
                                contentColor = if (confusionPinned) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.tertiary
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = if (confusionPinned) Icons.Default.BookmarkAdded else Icons.Default.BookmarkAdd,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (confusionPinned) "Đã ghim mốc chưa hiểu (AI đang phân tích)" else "📌 Đánh dấu chỗ chưa hiểu (Ghim mốc để AI giải thích lại)",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // 6. Danh sách Ghi chú Lời giảng Mở rộng (Novelty >= 0.28)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Ghi chú Lời giảng Mở rộng (${currentSlide?.notes?.size ?: 0}):",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Lọc bởi Novelty & MMR",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            if (currentSlide?.notes.isNullOrEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp)
                    ) {
                        Text(
                            text = "Trang slide này giảng viên đọc bám sát tài liệu,\nkhông phát sinh thông tin mở rộng ngoài slide.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(18.dp)
                        )
                    }
                }
            } else {
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

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }

    // Modal Sheet Chọn Ngữ cảnh Khái niệm (Concept Disambiguation)
    if (showDisambiguationSheet && selectedDisambiguationTerm != null) {
        val matchingContexts = sampleContexts.filter { it.term.equals(selectedDisambiguationTerm, ignoreCase = true) }
        ModalBottomSheet(
            onDismissRequest = { showDisambiguationSheet = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = "CHỌN NGỮ CẢNH KHÁI NIỆM",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = selectedDisambiguationTerm ?: "",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Khái niệm này xuất hiện ở nhiều môn học. Hãy chọn đúng môn bạn muốn gắn vào vở ghi chép:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp, bottom = 14.dp)
                )

                matchingContexts.forEach { ctx ->
                    Surface(
                        onClick = { showDisambiguationSheet = false },
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = ctx.courseName,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = ctx.location,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = ctx.summary,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(28.dp))
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
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(
            width = if (isHighlighted) 2.dp else 1.dp,
            color = if (isHighlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)
        ),
        colors = CardDefaults.outlinedCardColors(
            containerColor = if (isHighlighted) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = "Giảng viên mở rộng",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                Surface(
                    onClick = { onJumpAudio(note.startTimeSec) },
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Nghe lời giảng",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = formatSec(note.startTimeSec),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = note.bulletPoint,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Normal
            )

            if (note.verbatimQuote.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .clickable { showQuote = !showQuote }
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (showQuote) Icons.Default.ExpandLess else Icons.Default.FormatQuote,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (showQuote) "Ẩn bằng chứng đối chiếu" else "Xem bằng chứng trích dẫn nguyên văn",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                AnimatedVisibility(visible = showQuote) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                    ) {
                        Text(
                            text = "\"${note.verbatimQuote}\"",
                            style = MaterialTheme.typography.bodySmall,
                            fontStyle = FontStyle.Italic,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun formatSec(sec: Float): String {
    val totalSec = sec.toInt()
    val m = totalSec / 60
    val s = totalSec % 60
    return String.format("%02d:%02d", m, s)
}
