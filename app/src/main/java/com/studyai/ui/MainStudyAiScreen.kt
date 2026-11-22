package com.studyai.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
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

enum class StudyAiTab(val title: String, val icon: ImageVector) {
    NOTES("Ghi chú bài giảng", Icons.Default.MenuBook),
    GRAPH("Đồ thị tri thức", Icons.Default.AccountTree),
    DICTIONARY("Từ điển sống", Icons.Default.AutoStories)
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
    onNavigateToChapterSlide: (Int, Int, Float) -> Unit = { _, _, _ -> }
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
        bottomBar = {
            NavigationBar {
                StudyAiTab.values().forEach { tab ->
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        icon = { Icon(tab.icon, contentDescription = tab.title) },
                        label = { Text(tab.title) }
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
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
                        onNavigateToChapterSlide = handleNavigateToChapterSlide
                    )
                }
                StudyAiTab.GRAPH -> {
                    KnowledgeGraphScreen(
                        courseTitle = "Trí tuệ Nhân tạo (AI)",
                        nodes = sampleNodes,
                        edges = sampleEdges,
                        onJumpToSlide = handleJumpToSlide
                    )
                }
                StudyAiTab.DICTIONARY -> {
                    CourseDictionaryScreen(
                        courseTitle = "Trí tuệ Nhân tạo (AI)",
                        dictionaryEntries = dictionaryEntries,
                        onSelectConcept = onSelectConcept,
                        onNavigateToChapterSlide = handleNavigateToChapterSlide
                    )
                }
            }

            // Hiển thị ngăn kéo Bottom Sheet "Từ điển sống & Tài liệu tham khảo mở rộng"
            selectedConceptDetail?.let { entry ->
                LivingDictionaryBottomSheet(
                    entry = entry,
                    onDismiss = onDismissConceptDetail,
                    onNavigateToChapterSlide = handleNavigateToChapterSlide
                )
            }
        }
    }
}
