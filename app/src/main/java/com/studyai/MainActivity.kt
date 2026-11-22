package com.studyai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.studyai.data.api.ApiClient
import com.studyai.ui.MainStudyAiScreen
import com.studyai.viewmodel.StudyAiViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: StudyAiViewModel by viewModels {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return StudyAiViewModel(ApiClient.apiService) as T
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Tải dữ liệu toàn hệ thống từ 3 Trụ cột qua Laravel Backend API
        viewModel.loadAllData(courseId = 1L, lectureId = 1L)

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val slides by viewModel.slides.collectAsState()
                    val chapters by viewModel.chapters.collectAsState()
                    val currentLectureId by viewModel.currentLectureId.collectAsState()
                    val currentLectureTitle by viewModel.currentLectureTitle.collectAsState()
                    val currentSlideIndex by viewModel.currentSlideIndex.collectAsState()
                    val targetAudioSec by viewModel.targetAudioSec.collectAsState()
                    val nodes by viewModel.nodes.collectAsState()
                    val edges by viewModel.edges.collectAsState()
                    val dictionaryEntries by viewModel.dictionaryEntries.collectAsState()
                    val selectedConceptDetail by viewModel.selectedConceptDetail.collectAsState()
                    val isLoading by viewModel.isLoading.collectAsState()

                    if (isLoading && slides.isEmpty() && nodes.isEmpty() && dictionaryEntries.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
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
                            sampleNodes = nodes,
                            sampleEdges = edges,
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
                            }
                        )
                    }
                }
            }
        }
    }
}
