package com.studyai.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import com.studyai.ui.theme.StudyAiPalette
import kotlinx.coroutines.launch

enum class StudyAiTab(val title: String, val shortTitle: String, val icon: ImageVector) {
    NOTES("Ghi chú", "Ghi chú", Icons.AutoMirrored.Filled.MenuBook),
    CAPTURE("Thu âm", "Thu âm", Icons.Default.Mic),
    ROADMAP("Hệ thống", "Lộ trình", Icons.Default.AccountTree),
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
    currentLectureTitle: String = "Chapter 1: Tổng quan bài giảng",
    chapters: List<ChapterUiModel> = emptyList(),
    onSelectLecture: (Long) -> Unit = {},
    sampleSlides: List<SlideUi> = emptyList(),
    currentSlideIdx: Int = 0,
    onSlideIdxChange: (Int) -> Unit = {},
    targetAudioSec: Float? = null,
    onClearTargetAudio: () -> Unit = {},
    targetSlidePage: Int? = null,
    onClearTargetSlidePage: () -> Unit = {},
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
    var showLecturePickerSheet by remember { mutableStateOf(false) }
    var showServerConfigDialog by remember { mutableStateOf(false) }
    var showNotesSearchBar by remember { mutableStateOf(false) }
    var notesSearchQuery by remember { mutableStateOf("") }
    var scrollToTopNonce by remember { mutableIntStateOf(0) }

    val activeCourseList = courseList
    val activeSlideVault = slideVault

    var confusedBlocksState by remember { mutableStateOf(setOf<String>()) }

    val courses = remember(activeCourseList) {
        activeCourseList.map { c ->
            val subtitle = buildString {
                if (c.code.isNotBlank()) append(c.code)
                if (c.lecturer.isNotBlank()) {
                    if (isNotEmpty()) append(" • ")
                    append(c.lecturer)
                }
                if (isEmpty()) append("${c.slideCount} slides")
            }
            CourseChoice(c.id, c.name, subtitle)
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
                                    timestampLabel = "%02d:%02d".format(mins, secs),
                                    evidenceQuote = n.verbatimQuote,
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
                val startM = (slide.startTimeSec / 60).toInt()
                val startS = (slide.startTimeSec % 60).toInt()
                val endM = (slide.endTimeSec / 60).toInt()
                val endS = (slide.endTimeSec % 60).toInt()
                val timeRange = if (slide.endTimeSec > 0f || slide.startTimeSec > 0f) {
                    val sLabel = "%02d:%02d".format(startM, startS)
                    val eLabel = "%02d:%02d".format(endM, endS)
                    if (slide.endTimeSec > slide.startTimeSec && sLabel != eLabel) "$sLabel – $eLabel" else sLabel
                } else ""

                AnnotatedSlidePageUi(
                    pageNumber = slide.slideNumber,
                    slideTitle = lines.firstOrNull() ?: "Trang ${slide.slideNumber}",
                    timeRangeLabel = timeRange,
                    blocks = blocks
                )
            }
        }
    }

    val filteredAnnotatedPages = remember(annotatedSlidePages, notesSearchQuery) {
        val query = notesSearchQuery.trim()
        if (query.isEmpty()) {
            annotatedSlidePages
        } else {
            annotatedSlidePages.filter { page ->
                page.slideTitle.contains(query, ignoreCase = true) ||
                    "Slide ${page.pageNumber}".contains(query, ignoreCase = true) ||
                    "Slide %02d".format(page.pageNumber).contains(query, ignoreCase = true) ||
                    page.blocks.any { b ->
                        b.originalSlideText.contains(query, ignoreCase = true) ||
                            b.inlineNotes.any { n ->
                                n.explanationText.contains(query, ignoreCase = true) ||
                                    n.evidenceQuote.contains(query, ignoreCase = true)
                            } ||
                            b.highlights.any { h -> h.term.contains(query, ignoreCase = true) }
                    }
            }
        }
    }

    val lectureSubtitleForNotes = remember(currentLectureId, currentLectureTitle, chapters, inlinePages, currentCourse.meta) {
        if (chapters.isNotEmpty() || inlinePages.isNotEmpty() || sampleSlides.isNotEmpty()) {
            val cleanTitle = currentLectureTitle
                .replace(Regex("^Bài\\s*#?\\d+\\s*[•:\\-–]\\s*", RegexOption.IGNORE_CASE), "")
                .trim()
                .ifBlank { currentCourse.name }
            "Bài #%02d • %s".format(currentLectureId, cleanTitle)
        } else {
            currentCourse.meta
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
            Column(modifier = Modifier.fillMaxWidth()) {
                StudyAiTopHeader(
                    courseName = currentCourse.name,
                    subtitleText = if (selectedTab == StudyAiTab.NOTES) lectureSubtitleForNotes else currentCourse.meta,
                    isNotesTab = selectedTab == StudyAiTab.NOTES,
                    isDarkTheme = isDarkTheme,
                    showSearchBar = showNotesSearchBar,
                    onToggleSearchBar = {
                        showNotesSearchBar = !showNotesSearchBar
                        if (!showNotesSearchBar) {
                            notesSearchQuery = ""
                        }
                    },
                    onExportMarkdown = onExportMarkdown,
                    onOpenCourseManager = { showCourseManagerSheet = true },
                    selectedLanguageMode = selectedLanguageMode,
                    onLanguageModeChange = onLanguageModeChange,
                    onToggleDarkTheme = onToggleDarkTheme,
                    onOpenServerSettings = { showServerConfigDialog = true },
                )

                // Hàng 2 (Tab Ghi chú): Thanh tìm kiếm (khi bật) + duy nhất 1 chip "Tất cả (N slide)"
                if (selectedTab == StudyAiTab.NOTES) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            AnimatedVisibility(visible = showNotesSearchBar) {
                                OutlinedTextField(
                                    value = notesSearchQuery,
                                    onValueChange = { notesSearchQuery = it },
                                    placeholder = {
                                        Text(
                                            text = "Tìm slide, từ khóa, ghi chú AI...",
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Search,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp),
                                        )
                                    },
                                    trailingIcon = {
                                        IconButton(
                                            onClick = {
                                                if (notesSearchQuery.isNotEmpty()) {
                                                    notesSearchQuery = ""
                                                } else {
                                                    showNotesSearchBar = false
                                                }
                                            },
                                            modifier = Modifier.size(32.dp),
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Đóng tìm kiếm",
                                                modifier = Modifier.size(16.dp),
                                            )
                                        }
                                    },
                                    singleLine = true,
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
                                )
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Start,
                            ) {
                                Surface(
                                    onClick = {
                                        notesSearchQuery = ""
                                        showNotesSearchBar = false
                                        scrollToTopNonce += 1
                                    },
                                    shape = RoundedCornerShape(999.dp),
                                    color = StudyAiPalette.PrimaryCobalt,
                                ) {
                                    Text(
                                        text = "Tất cả (${annotatedSlidePages.size} slide)",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                                    )
                                }
                            }

                            HorizontalDivider(
                                thickness = 1.dp,
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f),
                            )
                        }
                    }
                }
            }
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
                EditorialBottomNavBar(
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
                    color = MaterialTheme.colorScheme.primaryContainer,
                    border = BorderStroke(1.dp, StudyAiPalette.PrimaryCobalt.copy(alpha = 0.3f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
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
                            colors = ButtonDefaults.buttonColors(
                                containerColor = StudyAiPalette.PrimaryCobalt,
                                contentColor = Color.White,
                            ),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        ) {
                            Text("Tạo môn", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                Crossfade(
                    targetState = selectedTab,
                    animationSpec = tween(durationMillis = 180),
                    label = "studyai_tab_transition",
                ) { tab ->
                    when (tab) {
                        StudyAiTab.NOTES -> {
                            InlineSlideNoteDocumentScreen(
                                pages = filteredAnnotatedPages,
                                activeSpokenBlockId = activeSpokenBlockId,
                                targetSlidePage = targetSlidePage,
                                onClearTargetSlidePage = onClearTargetSlidePage,
                                scrollToTopNonce = scrollToTopNonce,
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
                                onExportMarkdown = null,
                            )
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
                                onUploadSlideToVault = onUploadSlideToVault,
                                onStartLiveFollow = onStartLiveFollow,
                                onStopLiveFollow = onStopLiveFollow,
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
                                onAnalyzeUploadedPair = onAnalyzeUploadedPair,
                                voiceTestResult = voiceTestResult,
                                isVoiceTesting = isVoiceTesting,
                                onTestVoiceNotes = onTestVoiceNotes,
                                onDismissVoiceTestResult = onDismissVoiceTestResult,
                                voiceprintStatus = voiceprintStatus,
                                isEnrollingVoice = isEnrollingVoice,
                                onEnrollVoiceprint = onEnrollVoiceprint,
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

                selectedConceptDetail?.let { entry ->
                    LivingDictionaryBottomSheet(
                        entry = entry,
                        onDismiss = onDismissConceptDetail,
                        onNavigateToChapterSlide = handleNavigateToChapterSlide,
                    )
                }

                if (showCourseManagerSheet) {
                    CourseManagerBottomSheet(
                        courses = activeCourseList,
                        activeCourseId = selectedCourseId,
                        onDismiss = { showCourseManagerSheet = false },
                        onSelectCourse = { cId ->
                            onSelectCourse(cId)
                            showCourseManagerSheet = false
                        },
                        onAddCourse = onAddCourse,
                        onUpdateCourse = { id, name, code, lecturer ->
                            val existing = activeCourseList.find { it.id == id }
                            onUpdateCourse(
                                (existing ?: CourseUiItem(id = id, name = name)).copy(
                                    name = name,
                                    code = code,
                                    lecturer = lecturer,
                                )
                            )
                        },
                        onDeleteCourse = { id ->
                            activeCourseList.find { it.id == id }?.let(onDeleteCourse)
                        },
                        chaptersCount = chapters.size,
                        onOpenLecturePicker = {
                            showCourseManagerSheet = false
                            showLecturePickerSheet = true
                        },
                        isDark = isDarkTheme,
                        onToggleDarkTheme = onToggleDarkTheme,
                        onOpenServerSettings = {
                            showCourseManagerSheet = false
                            showServerConfigDialog = true
                        },
                    )
                }

                if (showLecturePickerSheet) {
                    LecturePickerBottomSheet(
                        courseName = currentCourse.name,
                        chapters = chapters,
                        currentLectureId = currentLectureId,
                        onSelectLecture = { ch ->
                            showLecturePickerSheet = false
                            onSelectLecture(ch.id)
                        },
                        onDismiss = { showLecturePickerSheet = false },
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LecturePickerBottomSheet(
    courseName: String,
    chapters: List<ChapterUiModel>,
    currentLectureId: Long,
    onSelectLecture: (ChapterUiModel) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "Bài giảng • $courseName",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (chapters.isEmpty()) {
                Text(
                    text = "Chưa có bài giảng nào được xử lý cho môn học này.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                chapters.forEach { ch ->
                    val isSelected = ch.id == currentLectureId
                    Surface(
                        onClick = { onSelectLecture(ch) },
                        shape = RoundedCornerShape(14.dp),
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                        },
                        border = BorderStroke(
                            width = if (isSelected) 1.5.dp else 1.dp,
                            color = if (isSelected) {
                                StudyAiPalette.PrimaryCobalt
                            } else {
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                            },
                        ),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = ch.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = "${ch.slideCount} trang slide",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (isSelected) {
                                Spacer(Modifier.width(8.dp))
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Đang xem",
                                    tint = StudyAiPalette.PrimaryCobalt,
                                    modifier = Modifier.size(20.dp),
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
                imageVector = Icons.Default.Dns,
                contentDescription = null,
                tint = StudyAiPalette.PrimaryCobalt,
            )
        },
        title = {
            Text(
                text = "Cấu hình Máy chủ StudyAI",
                style = MaterialTheme.typography.titleLarge,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Nhập URL Ngrok mới hoặc địa chỉ IP LAN (VD: http://192.168.1.10:8000/) để kết nối mà không cần build lại APK.",
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
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
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
    courseName: String,
    subtitleText: String,
    isNotesTab: Boolean,
    isDarkTheme: Boolean,
    showSearchBar: Boolean,
    onToggleSearchBar: () -> Unit,
    onExportMarkdown: (() -> Unit)?,
    onOpenCourseManager: () -> Unit,
    selectedLanguageMode: LectureLanguageMode,
    onLanguageModeChange: (LectureLanguageMode) -> Unit,
    onToggleDarkTheme: () -> Unit,
    onOpenServerSettings: () -> Unit,
) {
    var langMenuExpanded by remember { mutableStateOf(false) }
    val primarySoft = if (isDarkTheme) StudyAiPalette.PrimarySoftDark else StudyAiPalette.PrimarySoftLight
    val primaryText = if (isDarkTheme) StudyAiPalette.PrimaryTextDark else StudyAiPalette.PrimaryTextLight

    Surface(
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onOpenCourseManager() }
                        .padding(end = 8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(StudyAiPalette.PrimaryCobalt, StudyAiPalette.Teal)
                                )
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.MenuBook,
                            contentDescription = "StudyAI",
                            tint = Color.White,
                            modifier = Modifier.size(19.dp),
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = courseName,
                                fontSize = 14.sp,
                                lineHeight = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = "Đổi môn học",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Text(
                            text = subtitleText,
                            fontSize = 11.sp,
                            lineHeight = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                if (isNotesTab) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Surface(
                            onClick = onToggleSearchBar,
                            shape = CircleShape,
                            color = if (showSearchBar) primarySoft else MaterialTheme.colorScheme.surfaceVariant,
                            border = BorderStroke(
                                1.dp,
                                if (showSearchBar) StudyAiPalette.PrimaryCobalt else MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                            ),
                            modifier = Modifier.size(34.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "Tìm kiếm nhanh",
                                    tint = if (showSearchBar) primaryText else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(17.dp),
                                )
                            }
                        }

                        if (onExportMarkdown != null) {
                            Surface(
                                onClick = onExportMarkdown,
                                shape = RoundedCornerShape(999.dp),
                                color = primarySoft,
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.IosShare,
                                        contentDescription = "Xuất Markdown",
                                        tint = primaryText,
                                        modifier = Modifier.size(13.dp),
                                    )
                                    Text(
                                        text = "Xuất",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = primaryText,
                                    )
                                }
                            }
                        }
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box {
                            Surface(
                                onClick = { langMenuExpanded = true },
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                border = BorderStroke(
                                    width = 1.dp,
                                    color = StudyAiPalette.Teal.copy(alpha = 0.35f),
                                ),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Translate,
                                        contentDescription = "Ngôn ngữ bài giảng",
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.size(14.dp),
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = selectedLanguageMode.shortLabel,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
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
                                                    tint = StudyAiPalette.PrimaryCobalt,
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

                        IconButton(
                            onClick = onOpenServerSettings,
                            modifier = Modifier.size(40.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Dns,
                                contentDescription = "Cấu hình Server URL",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(19.dp),
                            )
                        }

                        IconButton(
                            onClick = onToggleDarkTheme,
                            modifier = Modifier.size(40.dp),
                        ) {
                            Icon(
                                imageVector = if (isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                                contentDescription = if (isDarkTheme) "Chuyển giao diện sáng" else "Chuyển giao diện tối",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(19.dp),
                            )
                        }
                    }
                }
            }

            if (!isNotesTab) {
                HorizontalDivider(
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                )
            }
        }
    }
}

@Composable
private fun EditorialBottomNavBar(
    selectedTab: StudyAiTab,
    onSelectTab: (StudyAiTab) -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            HorizontalDivider(
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.65f),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StudyAiTab.values().forEach { tab ->
                    val isSelected = selectedTab == tab
                    val pillColor by animateColorAsState(
                        targetValue = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                        animationSpec = tween(160),
                        label = "tab_pill_bg",
                    )
                    val contentColor by animateColorAsState(
                        targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        animationSpec = tween(160),
                        label = "tab_content_color",
                    )

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .height(54.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(pillColor)
                            .clickable { onSelectTab(tab) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = tab.title,
                            tint = contentColor,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(modifier = Modifier.height(3.dp))
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
        border = BorderStroke(1.dp, StudyAiPalette.Rose.copy(alpha = 0.35f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
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
                    modifier = Modifier.size(18.dp),
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
