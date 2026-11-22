package com.studyai.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.studyai.ui.capture.CaptureScreen
import com.studyai.ui.dictionary.CourseDictionaryItemUi
import com.studyai.ui.dictionary.CourseDictionaryScreen
import com.studyai.ui.dictionary.LivingDictionaryBottomSheet
import com.studyai.ui.dictionary.LivingDictionaryUiModel
import com.studyai.ui.graph.ConceptEdgeUi
import com.studyai.ui.graph.ConceptNodeUi
import com.studyai.ui.graph.KnowledgeRoadmapScreen
import com.studyai.ui.notes.ChapterUiModel
import com.studyai.ui.notes.LectureNoteScreen
import com.studyai.ui.notes.SlideUi

enum class StudyAiTab(val title: String, val shortTitle: String, val icon: ImageVector) {
    NOTES("Ghi chú", "Ghi chú", Icons.Default.MenuBook),
    CAPTURE("Thu âm", "Thu âm", Icons.Default.Mic),
    ROADMAP("Hệ thống", "Hệ thống", Icons.Default.AccountTree),
    DICTIONARY("Từ điển", "Từ điển", Icons.Default.AutoStories),
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

data class CourseChoice(
    val id: Long,
    val name: String,
    val meta: String
)

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
    onSelectCourse: (Long) -> Unit = {},
    isDarkTheme: Boolean = false,
    onToggleDarkTheme: () -> Unit = {},
    selectedLanguageMode: LectureLanguageMode = LectureLanguageMode.AUTO,
    onLanguageModeChange: (LectureLanguageMode) -> Unit = {},
    errorMessage: String? = null,
    onRetryLoad: () -> Unit = {},
) {
    var selectedTab by remember { mutableStateOf(StudyAiTab.NOTES) }

    val courses = remember {
        listOf(
            CourseChoice(1L, "Trí tuệ Nhân tạo (AI)", "VKU • 6 chương • 18 khái niệm"),
            CourseChoice(2L, "Học máy (Machine Learning)", "VKU • 5 chương • 14 khái niệm"),
            CourseChoice(3L, "Hệ quản trị CSDL (SQL Server)", "HCMUE / VKU • 10 bài • 12 khái niệm")
        )
    }

    var selectedCourseId by remember { mutableLongStateOf(if (currentLectureId == 8L) 3L else 1L) }
    val currentCourse = courses.find { it.id == selectedCourseId } ?: courses[0]

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
                course = currentCourse,
                courses = courses,
                onSelectCourse = { course ->
                    selectedCourseId = course.id
                    onSelectCourse(course.id)
                },
                selectedLanguageMode = selectedLanguageMode,
                onLanguageModeChange = onLanguageModeChange,
                isDarkTheme = isDarkTheme,
                onToggleDarkTheme = onToggleDarkTheme,
            )
        },
        bottomBar = {
            FloatingCapsuleBottomBar(
                selectedTab = selectedTab,
                onSelectTab = { selectedTab = it }
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
                    animationSpec = tween(durationMillis = 200),
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

                        StudyAiTab.CAPTURE -> {
                            CaptureScreen(
                                currentCourseName = currentCourse.name,
                                selectedLanguage = selectedLanguageMode.shortLabel,
                                onRunBatchSync = { _, _ ->
                                    selectedTab = StudyAiTab.NOTES
                                },
                                onJumpToSlide = { slideNo ->
                                    selectedTab = StudyAiTab.NOTES
                                    onJumpToSlide(currentLectureId.toInt(), slideNo)
                                }
                            )
                        }

                        StudyAiTab.ROADMAP -> {
                            KnowledgeRoadmapScreen(
                                courseTitle = currentCourse.name,
                                conceptNodes = sampleNodes,
                                conceptEdges = sampleEdges,
                                onJumpToSlide = handleJumpToSlide,
                            )
                        }

                        StudyAiTab.DICTIONARY -> {
                            CourseDictionaryScreen(
                                courseTitle = currentCourse.name,
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
    course: CourseChoice,
    courses: List<CourseChoice>,
    onSelectCourse: (CourseChoice) -> Unit,
    selectedLanguageMode: LectureLanguageMode,
    onLanguageModeChange: (LectureLanguageMode) -> Unit,
    isDarkTheme: Boolean,
    onToggleDarkTheme: () -> Unit,
) {
    var courseMenuExpanded by remember { mutableStateOf(false) }
    var langMenuExpanded by remember { mutableStateOf(false) }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        shadowElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                // Course Switcher Trigger
                Box(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { courseMenuExpanded = true }
                            .padding(end = 6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(11.dp))
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
                                modifier = Modifier.size(18.dp),
                            )
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = course.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowDown,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Text(
                                text = course.meta,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = courseMenuExpanded,
                        onDismissRequest = { courseMenuExpanded = false },
                    ) {
                        courses.forEach { c ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(
                                            text = c.name,
                                            fontWeight = if (c.id == course.id) FontWeight.Bold else FontWeight.Normal,
                                        )
                                        Text(
                                            text = c.meta,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                },
                                trailingIcon = if (c.id == course.id) {
                                    {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                } else null,
                                onClick = {
                                    courseMenuExpanded = false
                                    onSelectCourse(c)
                                },
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(6.dp))

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
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Translate,
                                contentDescription = "Ngôn ngữ bài giảng",
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(14.dp),
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = selectedLanguageMode.shortLabel,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(15.dp),
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
                                        fontWeight = if (mode == selectedLanguageMode) FontWeight.SemiBold else FontWeight.Normal,
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
                                } else null,
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
    onSelectTab: (StudyAiTab) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(26.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp,
            shadowElevation = 8.dp,
            border = BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.22f),
            ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StudyAiTab.values().forEach { tab ->
                    val isSelected = selectedTab == tab
                    val pillColor by animateColorAsState(
                        targetValue = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                        animationSpec = tween(180),
                        label = "tab_pill_bg",
                    )
                    val contentColor by animateColorAsState(
                        targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        animationSpec = tween(180),
                        label = "tab_content_color",
                    )

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(18.dp))
                            .background(pillColor)
                            .clickable { onSelectTab(tab) }
                            .padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = tab.title,
                            tint = contentColor,
                            modifier = Modifier.size(19.dp),
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = tab.shortTitle,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = contentColor,
                            fontSize = 11.sp,
                            maxLines = 1,
                        )
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
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.35f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier.weight(1f),
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
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            TextButton(onClick = onRetry) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Thử lại", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
