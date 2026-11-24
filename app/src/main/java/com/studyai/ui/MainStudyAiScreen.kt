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
import com.studyai.ui.capture.SlideVaultAndCaptureScreen
import com.studyai.ui.capture.VaultSlideFileUi
import com.studyai.ui.course.CourseManagerBottomSheet
import com.studyai.ui.course.CourseUiItem
import com.studyai.ui.dictionary.CourseDictionaryItemUi
import com.studyai.ui.dictionary.CourseDictionaryScreen
import com.studyai.ui.dictionary.LivingDictionaryBottomSheet
import com.studyai.ui.dictionary.LivingDictionaryUiModel
import com.studyai.ui.graph.ConceptEdgeUi
import com.studyai.ui.graph.ConceptNodeUi
import com.studyai.ui.graph.KnowledgeRoadmapScreen
import com.studyai.ui.notes.AnnotatedSlidePageUi
import com.studyai.ui.notes.ChapterUiModel
import com.studyai.ui.notes.HighlightSourceType
import com.studyai.ui.notes.InlineConceptHighlight
import com.studyai.ui.notes.InlineLecturerNote
import com.studyai.ui.notes.InlineSlideNoteDocumentScreen
import com.studyai.ui.notes.SlideDocumentBlockUi
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
    courseList: List<CourseUiItem> = emptyList(),
    selectedCourseId: Long = 1L,
    onAddCourse: (String, String, String) -> Unit = { _, _, _ -> },
    onUpdateCourse: (CourseUiItem) -> Unit = {},
    onDeleteCourse: (CourseUiItem) -> Unit = {},
    slideVault: List<VaultSlideFileUi> = emptyList(),
    onUploadSlideToVault: (android.net.Uri) -> Unit = {},
    inlinePages: List<AnnotatedSlidePageUi> = emptyList(),
    onToggleConfusedBlock: (String) -> Unit = {},
    isRecording: Boolean = false,
    recordingElapsedSec: Int = 0,
    onStartLiveFollow: (VaultSlideFileUi) -> Unit = {},
    onStopLiveFollow: () -> Unit = {},
    onMarkCurrentBlockConfused: () -> Unit = {},
    isProcessingQueue: Boolean = false,
    uploadProgress: Int = 0,
    uploadStatusText: String = "",
    onAnalyzeUploadedPair: (android.net.Uri, android.net.Uri) -> Unit = { _, _ -> },
) {
    var selectedTab by remember { mutableStateOf(StudyAiTab.NOTES) }
    var showCourseManagerSheet by remember { mutableStateOf(false) }
    var isDocumentNoteMode by remember { mutableStateOf(true) }

    val activeCourseList = if (courseList.isNotEmpty()) courseList else listOf(
        CourseUiItem(1L, "Trí tuệ Nhân tạo (AI)", "IT-401", "TS. Huỳnh Công Pháp", 24),
        CourseUiItem(2L, "Học máy (Machine Learning)", "IT-402", "VKU Faculty", 18),
        CourseUiItem(3L, "Hệ quản trị CSDL (SQL Server)", "CS-301", "HCMUE / VKU", 12)
    )

    val activeSlideVault = if (slideVault.isNotEmpty()) slideVault else listOf(
        VaultSlideFileUi("vault_1", "Bai_Giang_Duong_Cong_Hoc_Tap_VKU.pdf", 8, null),
        VaultSlideFileUi("vault_2", "Chapter_1_Tong_Quan_AI_Agent.pptx", 16, null),
        VaultSlideFileUi("vault_3", "T_SQL_Stored_Procedures.pdf", 12, null)
    )

    var confusedBlocksState by remember { mutableStateOf(setOf<String>()) }

    val courses = remember(activeCourseList) {
        activeCourseList.map { c ->
            CourseChoice(c.id, c.name, "${c.code} • ${c.lecturer} • ${c.slideCount} slides")
        }
    }

    val currentCourse = courses.find { it.id == selectedCourseId } ?: courses[0]

    val annotatedSlidePages = remember(inlinePages, sampleSlides, confusedBlocksState) {
        if (inlinePages.isNotEmpty()) {
            inlinePages
        } else if (sampleSlides.isEmpty()) {
            listOf(
                AnnotatedSlidePageUi(
                    pageNumber = 1,
                    slideTitle = "Đường cong học tập VKU",
                    blocks = listOf(
                        SlideDocumentBlockUi(
                            blockId = "s1_b1",
                            kind = "heading",
                            originalSlideText = "Đường cong học tập VKU (Learning Curve Theory)",
                            isMarkedConfused = confusedBlocksState.contains("s1_b1"),
                            highlights = listOf(
                                InlineConceptHighlight(
                                    term = "Đường cong học tập",
                                    sourceType = HighlightSourceType.PREVIOUS_LECTURE,
                                    sourceLabel = "Đã học ở Chương 1 • Slide 4",
                                    explanation = "Khái niệm đã học ở Chương 1: Đo lường tốc độ nâng cao hiệu suất khi lặp lại công việc."
                                )
                            )
                        ),
                        SlideDocumentBlockUi(
                            blockId = "s1_b2",
                            kind = "bullet",
                            originalSlideText = "Mỗi khi sản lượng tích lũy tăng gấp đôi, chi phí nhân công trên mỗi đơn vị giảm theo tỷ lệ phần trăm cố định.",
                            isMarkedConfused = confusedBlocksState.contains("s1_b2"),
                            inlineNotes = listOf(
                                InlineLecturerNote(
                                    id = "note_1",
                                    explanationText = "Giảng viên lưu ý: Quy luật này áp dụng thực tế rất rõ trong sản xuất phần mềm và lắp ráp linh kiện bán dẫn.",
                                    timestampSec = 45.0f,
                                    timestampLabel = "00:45"
                                )
                            )
                        ),
                        SlideDocumentBlockUi(
                            blockId = "s1_b3",
                            kind = "formula",
                            originalSlideText = "Phương trình toán học: Y = a * X^b",
                            isMarkedConfused = confusedBlocksState.contains("s1_b3"),
                            highlights = listOf(
                                InlineConceptHighlight(
                                    term = "Y = a * X^b",
                                    sourceType = HighlightSourceType.ACADEMIC_INTERNET,
                                    sourceLabel = "MIT OCW • Semantic Scholar",
                                    explanation = "Mô hình toán học T.P. Wright (1936). a là chi phí đơn vị đầu tiên, b là hệ số độ dốc học tập (b < 0)."
                                )
                            )
                        )
                    )
                )
            )
        } else {
            sampleSlides.map { slide ->
                val lines = slide.extractedText.lines().filter { it.isNotBlank() }
                val blocks = if (lines.isEmpty()) {
                    listOf(
                        SlideDocumentBlockUi(
                            blockId = "s${slide.slideNumber}_b1",
                            kind = "bullet",
                            originalSlideText = "Slide ${slide.slideNumber}",
                            isMarkedConfused = confusedBlocksState.contains("s${slide.slideNumber}_b1")
                        )
                    )
                } else {
                    lines.mapIndexed { idx, line ->
                        val blockId = "s${slide.slideNumber}_b${idx + 1}"
                        val isHeading = idx == 0 || (line.length < 35 && !line.startsWith("-"))
                        val isFormula = line.contains("=") || line.contains("^") || line.contains("∑")
                        val kind = if (isHeading) "heading" else if (isFormula) "formula" else "bullet"

                        val matchingNotes = slide.notes.map { n ->
                            val mins = (n.startTimeSec / 60).toInt()
                            val secs = (n.startTimeSec % 60).toInt()
                            InlineLecturerNote(
                                id = "note_${n.id}",
                                explanationText = n.bulletPoint,
                                timestampSec = n.startTimeSec,
                                timestampLabel = "%02d:%02d".format(mins, secs)
                            )
                        }

                        val highlights = slide.terms.filter { term -> line.contains(term, ignoreCase = true) }.map { term ->
                            InlineConceptHighlight(
                                term = term,
                                sourceType = HighlightSourceType.ACADEMIC_INTERNET,
                                sourceLabel = "Thuật ngữ bài giảng",
                                explanation = "Thuật ngữ học thuật trích xuất từ bài giảng: $term"
                            )
                        }

                        SlideDocumentBlockUi(
                            blockId = blockId,
                            kind = kind,
                            originalSlideText = line,
                            isMarkedConfused = confusedBlocksState.contains(blockId),
                            inlineNotes = if (idx == 1 || idx == lines.size - 1) matchingNotes else emptyList(),
                            highlights = highlights
                        )
                    }
                }
                AnnotatedSlidePageUi(
                    pageNumber = slide.slideNumber,
                    slideTitle = "Slide ${slide.slideNumber}: ${lines.firstOrNull() ?: ""}",
                    blocks = blocks
                )
            }
        }
    }

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
                    onSelectCourse(course.id)
                },
                onOpenCourseManager = {
                    showCourseManagerSheet = true
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
                            Column(modifier = Modifier.fillMaxSize()) {
                                Surface(
                                    color = MaterialTheme.colorScheme.surface,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        text = currentLectureTitle,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                InlineSlideNoteDocumentScreen(
                                    pages = annotatedSlidePages,
                                    onToggleConfusedBlock = { bId ->
                                        onToggleConfusedBlock(bId)
                                        confusedBlocksState = if (confusedBlocksState.contains(bId)) {
                                            confusedBlocksState - bId
                                        } else {
                                            confusedBlocksState + bId
                                        }
                                    },
                                    onPlayTimestamp = { sec ->
                                        onNavigateToChapterSlide(currentLectureId.toInt(), 1, sec)
                                    }
                                )
                            }
                        }

                        StudyAiTab.CAPTURE -> {
                            SlideVaultAndCaptureScreen(
                                courseName = currentCourse.name,
                                slideVault = activeSlideVault,
                                liveAnnotatedPages = annotatedSlidePages,
                                isRecording = isRecording,
                                recordingElapsedLabel = "%02d:%02d".format(recordingElapsedSec / 60, recordingElapsedSec % 60),
                                isProcessingQueue = isProcessingQueue,
                                uploadProgress = uploadProgress,
                                uploadStatusText = uploadStatusText,
                                onUploadSlideToVault = { uri ->
                                    onUploadSlideToVault(uri)
                                },
                                onStartLiveFollow = { slide ->
                                    onStartLiveFollow(slide)
                                },
                                onStopLiveFollow = {
                                    onStopLiveFollow()
                                },
                                onMarkCurrentBlockConfused = {
                                    onMarkCurrentBlockConfused()
                                    val firstBlock = annotatedSlidePages.firstOrNull()?.blocks?.firstOrNull()?.blockId
                                    if (firstBlock != null) {
                                        confusedBlocksState = confusedBlocksState + firstBlock
                                    }
                                },
                                onToggleConfusedBlock = { bId ->
                                    onToggleConfusedBlock(bId)
                                    confusedBlocksState = if (confusedBlocksState.contains(bId)) {
                                        confusedBlocksState - bId
                                    } else {
                                        confusedBlocksState + bId
                                    }
                                },
                                onAnalyzeUploadedPair = { slideUri, audioUri ->
                                    onAnalyzeUploadedPair(slideUri, audioUri)
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

                // Bottom Sheet "Quản lý môn học (Thêm / Sửa / Xóa)"
                if (showCourseManagerSheet) {
                    CourseManagerBottomSheet(
                        courses = activeCourseList,
                        selectedCourseId = selectedCourseId,
                        onSelectCourse = { selected ->
                            onSelectCourse(selected.id)
                            showCourseManagerSheet = false
                        },
                        onAddCourse = { name, code, lecturer ->
                            onAddCourse(name, code, lecturer)
                        },
                        onUpdateCourse = { updated ->
                            onUpdateCourse(updated)
                        },
                        onDeleteCourse = { deleted ->
                            onDeleteCourse(deleted)
                        },
                        onDismiss = { showCourseManagerSheet = false }
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
    onOpenCourseManager: () -> Unit = {},
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
                // Course Switcher Trigger - Mở BottomSheet Quản Lý Môn Học
                Box(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onOpenCourseManager() }
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
