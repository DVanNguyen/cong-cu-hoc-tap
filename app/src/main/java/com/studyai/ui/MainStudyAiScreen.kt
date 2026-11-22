package com.studyai.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.studyai.ui.dictionary.CourseDictionaryItemUi
import com.studyai.ui.dictionary.CourseDictionaryScreen
import com.studyai.ui.dictionary.LivingDictionaryBottomSheet
import com.studyai.ui.dictionary.LivingDictionaryUiModel
import com.studyai.ui.graph.ConceptEdgeUi
import com.studyai.ui.graph.ConceptNodeUi
import com.studyai.ui.graph.KnowledgeGraphScreen
import com.studyai.ui.notes.ChapterUiModel
import com.studyai.ui.notes.LectureNoteScreen
import com.studyai.ui.notes.SlideUi

enum class StudyAiTab(val title: String, val shortTitle: String, val icon: ImageVector) {
    NOTES("Ghi chú bài giảng", "Ghi chú", Icons.Default.MenuBook),
    GRAPH("Đồ thị tri thức", "Đồ thị", Icons.Default.AccountTree),
    DICTIONARY("Từ điển sống", "Từ điển", Icons.Default.AutoStories),
}

enum class LectureLanguageMode(
    val apiCode: String,
    val shortLabel: String,
    val fullLabel: String,
) {
    AUTO("auto", "Auto", "Tự động (Việt / Anh)"),
    VI("vi", "VI", "Tiếng Việt / Trộn thuật ngữ"),
    EN("en", "EN", "Full English (MIT / CS50...)"),
}

@Composable
fun MainStudyAiScreen(
    currentLectureId: Long = 1L,
    currentLectureTitle: String = "Chapter 1: Tổng quan AI & Tác tử",
    chapters: List<ChapterUiModel> = emptyList(),
    onSelectLecture: (Long) -> Unit = {},
    sampleSlides: List<SlideUi> = emptyList(),
    currentSlideIdx: Int = 0,
    onSlideIdxChange: (Int) -> Unit = {},
    targetAudioSec: Float? = null,
    onClearTargetAudio: () -> Unit = {},
    sampleNodes: List<ConceptNodeUi> = emptyList(),
    sampleEdges: List<ConceptEdgeUi> = emptyList(),
    dictionaryEntries: List<CourseDictionaryItemUi> = emptyList(),
    selectedConceptDetail: LivingDictionaryUiModel? = null,
    onJumpToSlide: (Int, Int) -> Unit = { _, _ -> },
    onSelectConcept: (Long) -> Unit = {},
    onSelectTerm: (String) -> Unit = {},
    onDismissConceptDetail: () -> Unit = {},
    onNavigateToChapterSlide: (Int, Int, Float) -> Unit = { _, _, _ -> },
    // Các tham số mở rộng (có giá trị mặc định để tương thích 100% với code cũ)
    isDarkTheme: Boolean = false,
    onToggleDarkTheme: () -> Unit = {},
    selectedLanguageMode: LectureLanguageMode = LectureLanguageMode.AUTO,
    onLanguageModeChange: (LectureLanguageMode) -> Unit = {},
    errorMessage: String? = null,
    onRetryLoad: () -> Unit = {},
) {
    var selectedTab by remember { mutableStateOf(StudyAiTab.NOTES) }

    val handleNavigateToChapterSlide: (Int, Int, Float) -> Unit = { lecId, slideNo, sec ->
        selectedTab = StudyAiTab.NOTES
        onNavigateToChapterSlide(lecId, slideNo, sec)
    }

    val handleJumpToSlide: (Int, Int) -> Unit = { lecId, slideNo ->
        selectedTab = StudyAiTab.NOTES
        onJumpToSlide(lecId, slideNo)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            StudyAiTopHeader(
                lectureTitle = currentLectureTitle,
                selectedTab = selectedTab,
                selectedLanguageMode = selectedLanguageMode,
                onLanguageModeChange = onLanguageModeChange,
                isDarkTheme = isDarkTheme,
                onToggleDarkTheme = onToggleDarkTheme,
            )
        },
        bottomBar = {
            FloatingCapsuleBottomBar(
                selectedTab = selectedTab,
                onSelectTab = { selectedTab = it },
                slideCount = sampleSlides.size,
                nodeCount = sampleNodes.size,
                termCount = dictionaryEntries.size,
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (!errorMessage.isNullOrBlank()) {
                ErrorStatusBanner(
                    message = errorMessage,
                    onRetry = onRetryLoad,
                )
            }

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                Crossfade(
                    targetState = selectedTab,
                    animationSpec = tween(durationMillis = 220),
                    label = "studyai_tab_transition",
                ) { tab ->
                    when (tab) {
                        StudyAiTab.NOTES -> {
                            LectureNoteScreen(
                                lectureTitle = currentLectureTitle,
                                currentLectureId = currentLectureId,
                                chapters = chapters,
                                onSelectLecture = onSelectLecture,
                                slides = sampleSlides,
                                currentSlideIdx = currentSlideIdx,
                                onSlideIdxChange = onSlideIdxChange,
                                targetAudioSec = targetAudioSec,
                                onClearTargetAudio = onClearTargetAudio,
                                onSeekAudio = { /* Điều khiển trình phát audio */ },
                                onSelectTerm = onSelectTerm,
                                onNavigateToChapterSlide = handleNavigateToChapterSlide,
                            )
                        }

                        StudyAiTab.GRAPH -> {
                            KnowledgeGraphScreen(
                                courseTitle = "Trí tuệ Nhân tạo (AI)",
                                nodes = sampleNodes,
                                edges = sampleEdges,
                                onJumpToSlide = handleJumpToSlide,
                            )
                        }

                        StudyAiTab.DICTIONARY -> {
                            CourseDictionaryScreen(
                                courseTitle = "Trí tuệ Nhân tạo (AI)",
                                dictionaryEntries = dictionaryEntries,
                                onSelectConcept = onSelectConcept,
                                onNavigateToChapterSlide = handleNavigateToChapterSlide,
                            )
                        }
                    }
                }

                // Bottom Sheet "Từ điển sống & Tài liệu tham khảo mở rộng"
                selectedConceptDetail?.let { entry ->
                    LivingDictionaryBottomSheet(
                        entry = entry,
                        onDismiss = onDismissConceptDetail,
                        onNavigateToChapterSlide = handleNavigateToChapterSlide,
                    )
                }
            }
        }
    }
}

@Composable
private fun StudyAiTopHeader(
    lectureTitle: String,
    selectedTab: StudyAiTab,
    selectedLanguageMode: LectureLanguageMode,
    onLanguageModeChange: (LectureLanguageMode) -> Unit,
    isDarkTheme: Boolean,
    onToggleDarkTheme: () -> Unit,
) {
    var langMenuExpanded by remember { mutableStateOf(false) }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        shadowElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                // Brand badge + Title
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f),
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.primary,
                                        MaterialTheme.colorScheme.secondary,
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "StudyAI",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(20.dp),
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "StudyAI",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                        )
                        Text(
                            text = if (selectedTab == StudyAiTab.NOTES) lectureTitle else selectedTab.title,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Bộ chọn ngôn ngữ ASR (Auto / VI / EN)
                Box {
                    Surface(
                        onClick = { langMenuExpanded = true },
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        border = BorderStroke(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.25f),
                        ),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Translate,
                                contentDescription = "Ngôn ngữ bài giảng",
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(15.dp),
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = selectedLanguageMode.shortLabel,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = langMenuExpanded,
                        onDismissRequest = { langMenuExpanded = false },
                    ) {
                        LectureLanguageMode.values().forEach { mode ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = mode.fullLabel,
                                        fontWeight = if (mode == selectedLanguageMode) {
                                            FontWeight.SemiBold
                                        } else {
                                            FontWeight.Normal
                                        },
                                    )
                                },
                                trailingIcon = if (mode == selectedLanguageMode) {
                                    {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                } else {
                                    null
                                },
                                onClick = {
                                    langMenuExpanded = false
                                    onLanguageModeChange(mode)
                                },
                            )
                        }
                    }
                }

                // Nút chuyển Light / Dark mode
                IconButton(onClick = onToggleDarkTheme) {
                    Icon(
                        imageVector = if (isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                        contentDescription = if (isDarkTheme) "Chế độ sáng" else "Chế độ tối",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun FloatingCapsuleBottomBar(
    selectedTab: StudyAiTab,
    onSelectTab: (StudyAiTab) -> Unit,
    slideCount: Int,
    nodeCount: Int,
    termCount: Int,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            shadowElevation = 8.dp,
            border = BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.28f),
            ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StudyAiTab.values().forEach { tab ->
                    val isSelected = selectedTab == tab
                    val badgeCount = when (tab) {
                        StudyAiTab.NOTES -> slideCount
                        StudyAiTab.GRAPH -> nodeCount
                        StudyAiTab.DICTIONARY -> termCount
                    }
                    val pillColor by animateColorAsState(
                        targetValue = if (isSelected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surface
                        },
                        animationSpec = tween(200),
                        label = "tab_pill_bg",
                    )
                    val contentColor by animateColorAsState(
                        targetValue = if (isSelected) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        animationSpec = tween(200),
                        label = "tab_content_color",
                    )

                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(22.dp))
                            .background(pillColor)
                            .clickable { onSelectTab(tab) }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = tab.title,
                            tint = contentColor,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = tab.shortTitle,
                            style = MaterialTheme.typography.labelLarge,
                            color = contentColor,
                            maxLines = 1,
                        )
                        if (badgeCount > 0) {
                            Spacer(modifier = Modifier.width(5.dp))
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(
                                        if (isSelected) {
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                                        } else {
                                            MaterialTheme.colorScheme.surfaceVariant
                                        }
                                    )
                                    .padding(horizontal = 6.dp, vertical = 1.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = badgeCount.toString(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = contentColor,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ErrorStatusBanner(
    message: String,
    onRetry: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.WarningAmber,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            TextButton(onClick = onRetry) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Thử lại",
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Thử lại",
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}