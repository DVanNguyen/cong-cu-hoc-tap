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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.studyai.audio.LectureAudioPlayer
import com.studyai.data.api.ApiClient
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
import com.studyai.ui.notes.CompactAudioPlayerBar
import com.studyai.ui.notes.HighlightSourceType
import com.studyai.ui.notes.InlineConceptHighlight
import com.studyai.ui.notes.InlineLecturerNote
import com.studyai.ui.notes.InlineSlideNoteDocumentScreen
import com.studyai.ui.notes.SlideDocumentBlockUi
import com.studyai.ui.notes.SlideUi
import kotlinx.coroutines.launch

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
    activeSpokenBlockId: String? = null,
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
    voiceTestResult: com.studyai.data.model.VoiceNotesTestResponse? = null,
    isVoiceTesting: Boolean = false,
    onTestVoiceNotes: ((android.net.Uri) -> Unit)? = null,
    onDismissVoiceTestResult: (() -> Unit)? = null,
    voiceprintStatus: com.studyai.data.model.VoiceprintStatusResponse? = null,
    isEnrollingVoice: Boolean = false,
    onEnrollVoiceprint: ((android.net.Uri) -> Unit)? = null,
    isOfflineCache: Boolean = false,
    onUpdateInlineNote: (String, String) -> Unit = { _, _ -> },
    onDeleteInlineNote: (String) -> Unit = {},
    onExportMarkdown: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val audioPlayer = remember(context) { LectureAudioPlayer(context, coroutineScope) }
    val audioState by audioPlayer.state.collectAsState()

    DisposableEffect(audioPlayer) {
        onDispose { audioPlayer.release() }
    }

    val buildLectureAudioUrl: (Long) -> String = { lecId ->
        val base = ApiClient.baseUrl.trimEnd('/')
        "$base/api/v1/lectures/$lecId/audio"
    }

    LaunchedEffect(targetAudioSec, currentLectureId) {
        if (targetAudioSec != null && targetAudioSec >= 0f) {
            audioPlayer.loadAndSeek(buildLectureAudioUrl(currentLectureId), targetAudioSec)
            onClearTargetAudio()
        }
    }

    var selectedTab by remember { mutableStateOf(StudyAiTab.NOTES) }
    var showCourseManagerSheet by remember { mutableStateOf(false) }
    var showServerConfigDialog by remember { mutableStateOf(false) }

    val activeCourseList = courseList
    val activeSlideVault = slideVault

    var confusedBlocksState by remember { mutableStateOf(setOf<String>()) }

    val courses = remember(activeCourseList) {
        activeCourseList.map { c ->
            CourseChoice(c.id, c.name, "${c.code} • ${c.lecturer} • ${c.slideCount} slides")
        }
    }

    val currentCourse = courses.find { it.id == selectedCourseId }
        ?: courses.firstOrNull()
        ?: CourseChoice(0L, "Chưa có môn học", "Bấm để tạo môn học đầu tiên")

    val annotatedSlidePages = remember(inlinePages, sampleSlides, confusedBlocksState) {
        if (inlinePages.isNotEmpty()) {
            inlinePages
        } else if (sampleSlides.isEmpty()) {
            emptyList()
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
                    val noteTargetIdx = 1.coerceAtMost(lines.lastIndex)
                    lines.mapIndexed { idx, line ->
                        val blockId = "s${slide.slideNumber}_b${idx + 1}"
                        val isHeading = idx == 0 || (line.length < 35 && !line.startsWith("-"))
                        val isFormula = line.contains("=") || line.contains("^") || line.contains("∑")
                        val kind = if (isHeading) "heading" else if (isFormula) "formula" else "bullet"

                        val matchingNotes = if (idx == noteTargetIdx) {
                            slide.notes.map { n ->
                                val mins = (n.startTimeSec / 60).toInt()
                                val secs = (n.startTimeSec % 60).toInt()
                                InlineLecturerNote(
                                    id = "note_${n.id}",
                                    explanationText = n.bulletPoint,
                                    timestampSec = n.startTimeSec,
                                    timestampLabel = "%02d:%02d".format(mins, secs)
                                )
                            }
                        } else emptyList()

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
                            inlineNotes = matchingNotes,
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
                onOpenServerSettings = { showServerConfigDialog = true },
            )
        },
        bottomBar = {
            Column(modifier = Modifier.fillMaxWidth()) {
                CompactAudioPlayerBar(
                    audioState = audioState,
                    onTogglePlayPause = { audioPlayer.togglePlayPause() },
                    onSeekToMs = { ms -> audioPlayer.seekToMs(ms) },
                    onCycleSpeed = { audioPlayer.cycleSpeed() },
                    onClose = { audioPlayer.close() },
                )
                FloatingCapsuleBottomBar(
                    selectedTab = selectedTab,
                    onSelectTab = { selectedTab = it }
                )
            }
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

            if (activeCourseList.isEmpty() && !isOfflineCache) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Chưa có môn học nào",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                            Text(
                                text = "Hãy tạo môn học để lưu Slide, giọng giảng viên và vở ghi chép.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = { showCourseManagerSheet = true },
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        ) {
                            Text("Tạo môn học đầu tiên", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
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
                                    activeSpokenBlockId = activeSpokenBlockId,
                                    isOfflineCache = isOfflineCache,
                                    onToggleConfusedBlock = { bId ->
                                        onToggleConfusedBlock(bId)
                                        confusedBlocksState = if (confusedBlocksState.contains(bId)) {
                                            confusedBlocksState - bId
                                        } else {
                                            confusedBlocksState + bId
                                        }
                                    },
                                    onPlayTimestamp = { sec ->
                                        audioPlayer.loadAndSeek(buildLectureAudioUrl(currentLectureId), sec)
                                    },
                                    onUpdateNote = onUpdateInlineNote,
                                    onDeleteNote = onDeleteInlineNote,
                                    onExportMarkdown = onExportMarkdown,
                                )
                            }
                        }

                        StudyAiTab.CAPTURE -> {
                            SlideVaultAndCaptureScreen(
                                courseName = currentCourse.name,
                                slideVault = activeSlideVault,
                                liveAnnotatedPages = annotatedSlidePages,
                                activeSpokenBlockId = activeSpokenBlockId,
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
                                },
                                voiceTestResult = voiceTestResult,
                                isVoiceTesting = isVoiceTesting,
                                onTestVoiceNotes = onTestVoiceNotes,
                                onDismissVoiceTestResult = onDismissVoiceTestResult,
                                voiceprintStatus = voiceprintStatus,
                                isEnrollingVoice = isEnrollingVoice,
                                onEnrollVoiceprint = onEnrollVoiceprint
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

                if (showServerConfigDialog) {
                    ServerUrlConfigDialog(
                        initialUrl = ApiClient.baseUrl,
                        onDismiss = { showServerConfigDialog = false },
                        onSaveUrl = { newUrl ->
                            ApiClient.updateBaseUrl(context, newUrl)
                            showServerConfigDialog = false
                            onRetryLoad()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ServerUrlConfigDialog(
    initialUrl: String,
    onDismiss: () -> Unit,
    onSaveUrl: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var draftUrl by remember { mutableStateOf(initialUrl) }
    var isTesting by remember { mutableStateOf(false) }
    var testStatus by remember { mutableStateOf<String?>(null) }
    var testPassed by remember { mutableStateOf<Boolean?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        title = {
            Text(
                text = "Cấu hình Máy chủ StudyAI",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Nhập URL Ngrok mới hoặc địa chỉ IP LAN (VD: http://192.168.1.10:8000/) để đổi máy chủ ngay không cần build lại APK.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = draftUrl,
                    onValueChange = {
                        draftUrl = it
                        testStatus = null
                        testPassed = null
                    },
                    label = { Text("Server Base URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = {
                            draftUrl = ApiClient.DEFAULT_BASE_URL
                            testStatus = null
                            testPassed = null
                        },
                    ) {
                        Text("Mặc định Ngrok", style = MaterialTheme.typography.labelSmall)
                    }
                    OutlinedButton(
                        enabled = !isTesting && draftUrl.isNotBlank(),
                        onClick = {
                            isTesting = true
                            testStatus = "Đang kiểm tra kết nối..."
                            testPassed = null
                            scope.launch {
                                val result = ApiClient.testConnection(draftUrl)
                                isTesting = false
                                result.onSuccess { msg ->
                                    testPassed = true
                                    testStatus = msg
                                }.onFailure { err ->
                                    testPassed = false
                                    testStatus = "Lỗi kết nối: ${err.localizedMessage ?: err.javaClass.simpleName}"
                                }
                            }
                        },
                    ) {
                        Text(
                            text = if (isTesting) "Đang thử..." else "Kiểm tra kết nối",
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
                if (!testStatus.isNullOrBlank()) {
                    val bg = when (testPassed) {
                        true -> MaterialTheme.colorScheme.secondaryContainer
                        false -> MaterialTheme.colorScheme.errorContainer
                        null -> MaterialTheme.colorScheme.surfaceVariant
                    }
                    val fg = when (testPassed) {
                        true -> MaterialTheme.colorScheme.onSecondaryContainer
                        false -> MaterialTheme.colorScheme.onErrorContainer
                        null -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = bg,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = testStatus!!,
                            style = MaterialTheme.typography.labelSmall,
                            color = fg,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = draftUrl.isNotBlank(),
                onClick = { onSaveUrl(draftUrl) },
            ) {
                Text("Lưu & Tải lại")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Hủy")
            }
        },
    )
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
    onOpenServerSettings: () -> Unit = {},
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

                // Nút cấu hình Server URL động (Ngrok / LAN IP) - chống rớt demo không cần rebuild APK
                IconButton(
                    onClick = onOpenServerSettings,
                    modifier = Modifier.size(34.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Cấu hình Server URL",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(19.dp),
                    )
                }

                // Nút chuyển Light / Dark mode
                IconButton(
                    onClick = onToggleDarkTheme,
                    modifier = Modifier.size(34.dp),
                ) {
                    Icon(
                        imageVector = if (isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                        contentDescription = if (isDarkTheme) "Chế độ sáng" else "Chế độ tối",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(19.dp),
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
