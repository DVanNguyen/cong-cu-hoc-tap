package com.studyai.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.studyai.data.api.StudyAiApiService
import com.studyai.data.model.SlideWorkspaceDto
import com.studyai.ui.dictionary.CourseDictionaryItemUi
import com.studyai.ui.dictionary.CrossChapterLinkUiModel
import com.studyai.ui.dictionary.ExternalReferenceUiModel
import com.studyai.ui.dictionary.LivingDictionaryUiModel
import com.studyai.ui.graph.ConceptEdgeUi
import com.studyai.ui.graph.ConceptNodeUi
import com.studyai.ui.notes.ChapterUiModel
import com.studyai.ui.notes.NoteUi
import com.studyai.ui.notes.SlideUi
import com.studyai.ui.tasks.MilestoneUi
import com.studyai.ui.tasks.TaskItemUi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class StudyAiViewModel(
    private val apiService: StudyAiApiService
) : ViewModel() {

    private val _chapters = MutableStateFlow<List<ChapterUiModel>>(emptyList())
    val chapters: StateFlow<List<ChapterUiModel>> = _chapters.asStateFlow()

    private val _currentLectureId = MutableStateFlow(1L)
    val currentLectureId: StateFlow<Long> = _currentLectureId.asStateFlow()

    private val _currentLectureTitle = MutableStateFlow("Chapter 1: Tổng quan về Trí tuệ Nhân tạo & Tác tử Thông minh")
    val currentLectureTitle: StateFlow<String> = _currentLectureTitle.asStateFlow()

    private val _currentSlideIndex = MutableStateFlow(0)
    val currentSlideIndex: StateFlow<Int> = _currentSlideIndex.asStateFlow()

    private val _targetAudioSec = MutableStateFlow<Float?>(null)
    val targetAudioSec: StateFlow<Float?> = _targetAudioSec.asStateFlow()

    private val _slides = MutableStateFlow<List<SlideUi>>(emptyList())
    val slides: StateFlow<List<SlideUi>> = _slides.asStateFlow()

    private val _nodes = MutableStateFlow<List<ConceptNodeUi>>(emptyList())
    val nodes: StateFlow<List<ConceptNodeUi>> = _nodes.asStateFlow()

    private val _edges = MutableStateFlow<List<ConceptEdgeUi>>(emptyList())
    val edges: StateFlow<List<ConceptEdgeUi>> = _edges.asStateFlow()

    private val _tasks = MutableStateFlow<List<TaskItemUi>>(emptyList())
    val tasks: StateFlow<List<TaskItemUi>> = _tasks.asStateFlow()

    private val _dictionaryEntries = MutableStateFlow<List<CourseDictionaryItemUi>>(emptyList())
    val dictionaryEntries: StateFlow<List<CourseDictionaryItemUi>> = _dictionaryEntries.asStateFlow()

    private val _selectedConceptDetail = MutableStateFlow<LivingDictionaryUiModel?>(null)
    val selectedConceptDetail: StateFlow<LivingDictionaryUiModel?> = _selectedConceptDetail.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private fun mapSlides(slidesDto: List<SlideWorkspaceDto>): List<SlideUi> {
        return slidesDto.map { s ->
            SlideUi(
                id = s.id,
                slideNumber = s.slideNumber,
                extractedText = s.extractedText,
                terms = s.termsJson,
                startTimeSec = s.startTimeSec,
                endTimeSec = s.endTimeSec,
                confidenceScore = s.confidenceScore,
                notes = s.notes.map { n ->
                    NoteUi(
                        id = n.id,
                        slideId = n.slideId,
                        bulletPoint = n.bulletPoint,
                        noveltyScore = n.noveltyScore ?: 0.5f,
                        isInstructorOralOnly = n.isInstructorOralOnly,
                        verbatimQuote = n.verbatimQuote ?: "",
                        startTimeSec = n.startTimeSec,
                        endTimeSec = n.endTimeSec
                    )
                },
                crossChapterLinks = s.crossChapterLinks?.map { c ->
                    CrossChapterLinkUiModel(
                        conceptId = c.conceptId,
                        conceptNameVi = c.conceptNameVi,
                        conceptNameEn = c.conceptNameEn,
                        linkDirection = c.linkDirection,
                        targetLectureId = c.targetLectureId,
                        targetLectureOrder = c.targetLectureOrder,
                        targetLectureTitle = c.targetLectureTitle,
                        targetSlideNumber = c.targetSlideNumber,
                        targetTimestampSec = c.targetTimestampSec,
                        badgeText = c.badgeText
                    )
                } ?: emptyList()
            )
        }
    }

    fun loadAllData(courseId: Long = 1L, lectureId: Long = 1L) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                // 0. Tải danh mục các Bài giảng / Chương học
                try {
                    val lecRes = apiService.getCourseLectures(courseId)
                    if (lecRes.isSuccessful && lecRes.body() != null) {
                        _chapters.value = lecRes.body()!!.lectures.map {
                            ChapterUiModel(
                                id = it.id,
                                title = it.title,
                                slideCount = it.slideCount
                            )
                        }
                    }
                } catch (_: Exception) {
                }

                // 1. Tải dữ liệu Trụ cột 1 (Ghi chú bám slide & Căn audio)
                val workspaceRes = apiService.getLectureWorkspace(lectureId)
                if (workspaceRes.isSuccessful && workspaceRes.body() != null) {
                    val body = workspaceRes.body()!!
                    _slides.value = mapSlides(body.slides)
                    _currentLectureId.value = lectureId
                    val matchedTitle = _chapters.value.find { it.id == lectureId }?.title
                        ?: (body.lecture["title"] as? String)
                        ?: "Chapter $lectureId"
                    _currentLectureTitle.value = matchedTitle
                    _currentSlideIndex.value = 0
                }

                // 2. Tải dữ liệu Trụ cột 3 (Đồ thị Tri thức Liên chương DAG)
                val graphRes = apiService.getCourseKnowledgeGraph(courseId)
                if (graphRes.isSuccessful && graphRes.body() != null) {
                    val body = graphRes.body()!!
                    val nodeList = body.concepts.mapIndexed { idx, c ->
                        ConceptNodeUi(
                            id = c.id,
                            canonicalNameVi = c.canonicalNameVi,
                            canonicalNameEn = c.canonicalNameEn,
                            aliases = c.aliasesJson,
                            firstIntroducedLectureId = c.firstIntroducedLectureId,
                            firstIntroducedSlideNo = c.firstIntroducedSlideNo,
                            definedOrderIndex = c.definedOrderIndex,
                            definitionSummary = c.definitionSummary,
                            layerY = (c.firstIntroducedLectureId - 1) / 4f,
                            orderX = (idx % 4 + 1) / 5f,
                            hasViolation = c.isUsedBeforeDefined
                        )
                    }
                    val edgeList = body.relations.map { r ->
                        ConceptEdgeUi(
                            sourceConceptId = r.sourceConceptId,
                            targetConceptId = r.targetConceptId,
                            relationType = r.relationType,
                            weight = r.weight,
                            evidenceQuote = r.evidenceQuote,
                            isUsedBeforeDefinedViolation = r.isUsedBeforeDefinedViolation
                        )
                    }
                    _nodes.value = nodeList
                    _edges.value = edgeList
                }

                // 3. Tải dữ liệu Trụ cột 2 (Từ điển sống & Mở rộng Tham khảo Đa nguồn)
                val dictRes = apiService.getCourseDictionary(courseId)
                if (dictRes.isSuccessful && dictRes.body() != null) {
                    val entries = dictRes.body()!!.dictionaryList.map { d ->
                        CourseDictionaryItemUi(
                            id = d.id,
                            canonicalNameVi = d.canonicalNameVi,
                            canonicalNameEn = d.canonicalNameEn,
                            aliases = d.aliases ?: emptyList(),
                            definitionSummary = d.definitionSummary ?: "",
                            abstractnessScore = d.abstractnessScore,
                            aiDeepExplanation = d.aiDeepExplanation ?: "",
                            intuitionExample = d.intuitionExample ?: "",
                            firstIntroducedLectureId = d.firstIntroducedLectureId ?: 1,
                            firstIntroducedSlideNo = d.firstIntroducedSlideNo ?: 1,
                            referencesCount = d.referencesCount
                        )
                    }
                    _dictionaryEntries.value = entries
                }

            } catch (e: Exception) {
                android.util.Log.e("StudyAiViewModel", "loadAllData error", e)
                _errorMessage.value = "Lỗi kết nối máy chủ StudyAI: ${e.localizedMessage ?: e.message ?: e.javaClass.simpleName}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun switchLecture(lectureId: Long, targetSlideIdx: Int = 0, targetSec: Float? = null) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                val workspaceRes = apiService.getLectureWorkspace(lectureId)
                if (workspaceRes.isSuccessful && workspaceRes.body() != null) {
                    val body = workspaceRes.body()!!
                    val mapped = mapSlides(body.slides)
                    _slides.value = mapped
                    _currentLectureId.value = lectureId
                    val matchedTitle = _chapters.value.find { it.id == lectureId }?.title
                        ?: (body.lecture["title"] as? String)
                        ?: "Chapter $lectureId"
                    _currentLectureTitle.value = matchedTitle
                    _currentSlideIndex.value = targetSlideIdx.coerceIn(0, (mapped.size - 1).coerceAtLeast(0))
                    _targetAudioSec.value = targetSec
                }
            } catch (e: Exception) {
                _errorMessage.value = "Không thể chuyển chương: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun jumpToChapterSlide(lectureId: Int, slideNumber: Int, timestampSec: Float) {
        dismissConceptDetail()
        val targetSlideIdx = (slideNumber - 1).coerceAtLeast(0)
        if (lectureId.toLong() == _currentLectureId.value) {
            _currentSlideIndex.value = targetSlideIdx.coerceIn(0, (_slides.value.size - 1).coerceAtLeast(0))
            if (timestampSec > 0f) {
                _targetAudioSec.value = timestampSec
            }
        } else {
            switchLecture(lectureId.toLong(), targetSlideIdx, if (timestampSec > 0f) timestampSec else null)
        }
    }

    fun setSlideIndex(index: Int) {
        _currentSlideIndex.value = index.coerceIn(0, (_slides.value.size - 1).coerceAtLeast(0))
    }

    fun clearTargetAudio() {
        _targetAudioSec.value = null
    }

    fun loadConceptDetail(conceptId: Long) {
        viewModelScope.launch {
            try {
                val res = apiService.getConceptDictionary(conceptId)
                if (res.isSuccessful && res.body() != null) {
                    val b = res.body()!!
                    _selectedConceptDetail.value = LivingDictionaryUiModel(
                        conceptId = b.conceptId,
                        canonicalNameVi = b.canonicalNameVi,
                        canonicalNameEn = b.canonicalNameEn,
                        abstractnessScore = b.abstractnessScore,
                        slideSkeletonContext = b.slideSkeletonContext,
                        lecturerOralExplanation = b.lecturerOralExplanation ?: "",
                        aiDeepExplanation = b.aiDeepExplanation ?: "",
                        intuitionExample = b.intuitionExample,
                        crossChapterLinks = b.crossChapterLinks.map { c ->
                            CrossChapterLinkUiModel(
                                conceptId = c.conceptId,
                                conceptNameVi = c.conceptNameVi,
                                conceptNameEn = c.conceptNameEn,
                                linkDirection = c.linkDirection,
                                targetLectureId = c.targetLectureId,
                                targetLectureOrder = c.targetLectureOrder,
                                targetLectureTitle = c.targetLectureTitle,
                                targetSlideNumber = c.targetSlideNumber,
                                targetTimestampSec = c.targetTimestampSec,
                                badgeText = c.badgeText
                            )
                        },
                        externalReferences = b.externalReferences.map { r ->
                            ExternalReferenceUiModel(
                                id = r.id,
                                title = r.title,
                                url = r.url,
                                sourceProvider = r.sourceProvider,
                                domainName = r.domainName,
                                snippetSummary = r.snippetSummary,
                                finalRankScore = r.finalRankScore,
                                citationBadge = r.citationBadge
                            )
                        }
                    )
                }
            } catch (e: Exception) {
                _errorMessage.value = "Không thể tải chi tiết từ điển sống: ${e.message}"
            }
        }
    }

    fun dismissConceptDetail() {
        _selectedConceptDetail.value = null
    }

    fun findAndLoadConceptByName(term: String) {
        val cleanTerm = term.trim().lowercase()
        val matched = _dictionaryEntries.value.find { entry ->
            entry.canonicalNameVi.lowercase().contains(cleanTerm) ||
            entry.canonicalNameEn.lowercase().contains(cleanTerm) ||
            entry.aliases.any { it.lowercase().contains(cleanTerm) }
        }
        if (matched != null) {
            loadConceptDetail(matched.id)
        } else if (_dictionaryEntries.value.isNotEmpty()) {
            loadConceptDetail(_dictionaryEntries.value.first().id)
        }
    }

    fun toggleMilestone(milestoneId: Long) {
        viewModelScope.launch {
            try {
                apiService.toggleMilestone(milestoneId)
                // Cập nhật trạng thái tức thời trên local state
                _tasks.value = _tasks.value.map { task ->
                    task.copy(
                        milestones = task.milestones.map { ms ->
                            if (ms.id == milestoneId) ms.copy(isCompleted = !ms.isCompleted) else ms
                        }
                    )
                }
            } catch (e: Exception) {
                _errorMessage.value = "Không thể cập nhật tiến độ mốc: ${e.message}"
            }
        }
    }
}
