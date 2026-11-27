package com.studyai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.studyai.data.api.ApiClient
import com.studyai.ui.LectureLanguageMode
import com.studyai.ui.MainStudyAiScreen
import com.studyai.ui.theme.StudyAiTheme
import com.studyai.viewmodel.StudyAiViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: StudyAiViewModel by viewModels {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return StudyAiViewModel(ApiClient.apiService, applicationContext) as T
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Nạp Server URL đã lưu trong SharedPreferences (nếu có) trước khi gọi API
        ApiClient.init(applicationContext)

        // Tải dữ liệu toàn hệ thống qua Backend API
        viewModel.loadAllData(courseId = 1L, lectureId = null)

        setContent {
            val systemDark = isSystemInDarkTheme()
            var darkThemeOverride by rememberSaveable { mutableStateOf<Boolean?>(null) }
            val isDarkTheme = darkThemeOverride ?: systemDark
            var selectedLanguageMode by rememberSaveable { mutableStateOf(LectureLanguageMode.AUTO) }

            StudyAiTheme(darkTheme = isDarkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    val slides by viewModel.slides.collectAsState()
                    val chapters by viewModel.chapters.collectAsState()
                    val currentLectureId by viewModel.currentLectureId.collectAsState()
                    val currentLectureTitle by viewModel.currentLectureTitle.collectAsState()
                    val currentSlideIndex by viewModel.currentSlideIndex.collectAsState()
                    val targetAudioSec by viewModel.targetAudioSec.collectAsState()
                    val targetSlidePage by viewModel.targetSlidePage.collectAsState()
                    val nodes by viewModel.nodes.collectAsState()
                    val edges by viewModel.edges.collectAsState()
                    val mindmapState by viewModel.mindmapState.collectAsState()
                    val dictionaryEntries by viewModel.dictionaryEntries.collectAsState()
                    val selectedConceptDetail by viewModel.selectedConceptDetail.collectAsState()
                    val isLoading by viewModel.isLoading.collectAsState()
                    val errorMessage by viewModel.errorMessage.collectAsState()
                    val courses by viewModel.courses.collectAsState()
                    val selectedCourseId by viewModel.selectedCourseId.collectAsState()
                    val slideVault by viewModel.slideVault.collectAsState()
                    val inlinePages by viewModel.inlinePages.collectAsState()
                    val activeSpokenBlockId by viewModel.activeSpokenBlockId.collectAsState()
                    val isRecording by viewModel.isRecording.collectAsState()
                    val recordingElapsedSec by viewModel.recordingElapsedSec.collectAsState()
                    val liveAmplitude by viewModel.liveAmplitude.collectAsState()
                    val liveSessionState by viewModel.liveSessionState.collectAsState()
                    val liveFeedItems by viewModel.liveFeedItems.collectAsState()
                    val isProcessingQueue by viewModel.isProcessingQueue.collectAsState()
                    val uploadProgress by viewModel.uploadProgress.collectAsState()
                    val uploadStatusText by viewModel.uploadStatusText.collectAsState()
                    val voiceTestResult by viewModel.voiceTestResult.collectAsState()
                    val isVoiceTesting by viewModel.isVoiceTesting.collectAsState()
                    val voiceprintStatus by viewModel.voiceprintStatus.collectAsState()
                    val isEnrollingVoice by viewModel.isEnrollingVoice.collectAsState()
                    val isOfflineMode by viewModel.isOfflineMode.collectAsState()

                    LaunchedEffect(selectedCourseId) {
                        viewModel.checkVoiceprintStatus(selectedCourseId)
                    }

                    if (isLoading && slides.isEmpty() && nodes.isEmpty() && dictionaryEntries.isEmpty() && inlinePages.isEmpty()) {
                        StudyAiLoadingScreen()
                    } else {
                        MainStudyAiScreen(
                            currentLectureId = currentLectureId,
                            currentLectureTitle = currentLectureTitle,
                            chapters = chapters,
                            onSelectLecture = { id -> viewModel.switchLecture(id, 0, null) },
                            sampleSlides = slides,
                            currentSlideIdx = currentSlideIndex,
                            onSlideIdxChange = { idx -> viewModel.setSlideIndex(idx) },
                            targetAudioSec = targetAudioSec,
                            onClearTargetAudio = { viewModel.clearTargetAudio() },
                            targetSlidePage = targetSlidePage,
                            onClearTargetSlidePage = { viewModel.clearTargetSlidePage() },
                            sampleNodes = nodes,
                            sampleEdges = edges,
                            mindmapState = mindmapState,
                            onMindmapZoomChange = { viewModel.setMindmapZoom(it) },
                            onMindmapFilterChange = { viewModel.setMindmapFilter(it) },
                            onMindmapSearchQueryChange = { viewModel.setMindmapSearchQuery(it) },
                            onToggleMindmapLeafMastery = { viewModel.toggleMindmapLeafMastery(it) },
                            onMarkMindmapBranchSeen = { viewModel.markMindmapBranchSeen(it) },
                            dictionaryEntries = dictionaryEntries,
                            selectedConceptDetail = selectedConceptDetail,
                            onJumpToSlide = { lectureId, slideNo ->
                                viewModel.jumpToChapterSlide(lectureId, slideNo, 0f)
                            },
                            onSelectConcept = { conceptId ->
                                viewModel.loadConceptDetail(conceptId)
                            },
                            onSelectTerm = { term ->
                                viewModel.findAndLoadConceptByName(term)
                            },
                            onDismissConceptDetail = {
                                viewModel.dismissConceptDetail()
                            },
                            onNavigateToChapterSlide = { targetLectureId, targetSlideNo, targetSec ->
                                viewModel.jumpToChapterSlide(targetLectureId, targetSlideNo, targetSec)
                            },
                            onSelectCourse = { courseId ->
                                viewModel.switchCourse(courseId)
                            },
                            isDarkTheme = isDarkTheme,
                            onToggleDarkTheme = { darkThemeOverride = !isDarkTheme },
                            selectedLanguageMode = selectedLanguageMode,
                            onLanguageModeChange = { mode ->
                                selectedLanguageMode = mode
                                viewModel.setLanguageMode(mode)
                            },
                            errorMessage = errorMessage,
                            onRetryLoad = {
                                viewModel.loadAllData(courseId = selectedCourseId, lectureId = currentLectureId)
                            },
                            courseList = courses,
                            selectedCourseId = selectedCourseId,
                            onAddCourse = { name, code, lecturer -> viewModel.addCourse(name, code, lecturer) },
                            onUpdateCourse = { c -> viewModel.updateCourse(c.id, c.name, c.code, c.lecturer) },
                            onDeleteCourse = { c -> viewModel.deleteCourse(c.id) },
                            slideVault = slideVault,
                            onUploadSlideToVault = { uri -> viewModel.uploadSlideToVault(this@MainActivity, uri) },
                            inlinePages = inlinePages,
                            activeSpokenBlockId = activeSpokenBlockId,
                            onToggleConfusedBlock = { blockId -> viewModel.toggleConfusedBlock(blockId) },
                            isRecording = isRecording,
                            recordingElapsedSec = recordingElapsedSec,
                            liveAmplitude = liveAmplitude,
                            liveSessionState = liveSessionState,
                            liveFeedItems = liveFeedItems,
                            onStartLiveFollow = { slide -> viewModel.startLiveFollowRecording(this@MainActivity, slide) },
                            onStopLiveFollow = { refineFull -> viewModel.stopLiveFollowRecording(refineFull) },
                            onMarkCurrentBlockConfused = { viewModel.markCurrentBlockConfused() },
                            isProcessingQueue = isProcessingQueue,
                            uploadProgress = uploadProgress,
                            uploadStatusText = uploadStatusText,
                            onAnalyzeUploadedPair = { slideUri, audioUri ->
                                viewModel.uploadAndTrackLecture(this@MainActivity, slideUri, audioUri, null, selectedLanguageMode)
                            },
                            voiceTestResult = voiceTestResult,
                            isVoiceTesting = isVoiceTesting,
                            onTestVoiceNotes = { uri ->
                                viewModel.testVoiceOnlyNotes(this@MainActivity, uri)
                            },
                            onDismissVoiceTestResult = {
                                viewModel.clearVoiceTestResult()
                            },
                            voiceprintStatus = voiceprintStatus,
                            isEnrollingVoice = isEnrollingVoice,
                            onEnrollVoiceprint = { uri ->
                                viewModel.enrollTeacherVoice(this@MainActivity, uri)
                            },
                            isOfflineCache = isOfflineMode,
                            onUpdateInlineNote = { noteId, newText ->
                                viewModel.updateInlineNote(noteId, newText)
                            },
                            onDeleteInlineNote = { noteId ->
                                viewModel.deleteInlineNote(noteId)
                            },
                            onExportMarkdown = {
                                viewModel.exportAndShareMarkdown(this@MainActivity, currentLectureId)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StudyAiLoadingScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(
            modifier = Modifier.size(68.dp),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.MenuBook,
                contentDescription = "StudyAI",
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(18.dp),
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        Text(
            text = "StudyAI",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Đang đồng bộ vở ghi chép, kho slide và từ điển học thuật...",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(22.dp))

        LinearProgressIndicator(
            modifier = Modifier
                .width(190.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
    }
}
