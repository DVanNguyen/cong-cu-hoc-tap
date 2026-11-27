package com.studyai.viewmodel

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.studyai.audio.ChunkedAudioRecorder
import com.studyai.data.api.StudyAiApiService
import com.studyai.data.model.*
import com.studyai.ui.LectureLanguageMode
import com.studyai.ui.capture.LiveFeedItemUi
import com.studyai.ui.capture.LiveFeedKind
import com.studyai.ui.capture.VaultSlideFileUi
import com.studyai.ui.course.CourseUiItem
import com.studyai.ui.dictionary.CourseDictionaryItemUi
import com.studyai.ui.dictionary.CrossChapterLinkUiModel
import com.studyai.ui.dictionary.ExternalReferenceUiModel
import com.studyai.ui.dictionary.LivingDictionaryUiModel
import com.studyai.ui.dictionary.LivingLookupResult
import com.studyai.ui.dictionary.LivingVaultFilter
import com.studyai.ui.dictionary.SavedLivingEntry
import com.studyai.ui.dictionary.TrustedSourceDoc
import com.studyai.ui.graph.ConceptEdgeUi
import com.studyai.ui.graph.ConceptNodeUi
import com.studyai.ui.notes.*
import com.studyai.ui.roadmap.*
import com.studyai.ui.tasks.MilestoneUi
import com.studyai.ui.tasks.TaskItemUi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File

private data class LectureOfflineCachePayload(
    val lectureId: Long,
    val title: String,
    val inlineNotes: LectureInlineNotesResponse? = null,
    val workspace: LectureWorkspaceResponse? = null,
    val cachedAtMs: Long = System.currentTimeMillis(),
)

private data class LivingDictionaryCachePayload(
    val courseId: Long,
    val entries: List<SavedLivingEntry> = emptyList(),
    val cachedAtMs: Long = System.currentTimeMillis(),
)

class StudyAiViewModel(
    private val apiService: StudyAiApiService,
    private val appContext: Context? = null,
) : ViewModel() {

    companion object {
        private const val TAG = "StudyAiViewModel"
    }

    private val gson = Gson()

    private val _isOfflineMode = MutableStateFlow(false)
    val isOfflineMode: StateFlow<Boolean> = _isOfflineMode.asStateFlow()

    // =========================================================================
    // 1. Quản lý Môn học (Courses)
    // =========================================================================
    private val _courses = MutableStateFlow<List<CourseUiItem>>(emptyList())
    val courses: StateFlow<List<CourseUiItem>> = _courses.asStateFlow()

    private val _selectedCourseId = MutableStateFlow(1L)
    val selectedCourseId: StateFlow<Long> = _selectedCourseId.asStateFlow()

    // =========================================================================
    // 2. Kho lưu trữ Slide theo Môn (Slide Vault)
    // =========================================================================
    private val _slideVault = MutableStateFlow<List<VaultSlideFileUi>>(emptyList())
    val slideVault: StateFlow<List<VaultSlideFileUi>> = _slideVault.asStateFlow()

    // =========================================================================
    // 3. Vở ghi chép Inline & Live Following
    // =========================================================================
    private val _inlinePages = MutableStateFlow<List<AnnotatedSlidePageUi>>(emptyList())
    val inlinePages: StateFlow<List<AnnotatedSlidePageUi>> = _inlinePages.asStateFlow()

    private val _generalNotes = MutableStateFlow<List<GeneralNoteDto>>(emptyList())
    val generalNotes: StateFlow<List<GeneralNoteDto>> = _generalNotes.asStateFlow()

    private val _activeSpokenBlockId = MutableStateFlow<String?>(null)
    val activeSpokenBlockId: StateFlow<String?> = _activeSpokenBlockId.asStateFlow()

    // Trạng thái Thu âm Microphone
    private var audioRecorder: ChunkedAudioRecorder? = null
    private var timerJob: Job? = null
    private var amplitudeJob: Job? = null
    private val inFlightLiveChunkJobs = mutableListOf<Job>()

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _recordingElapsedSec = MutableStateFlow(0)
    val recordingElapsedSec: StateFlow<Int> = _recordingElapsedSec.asStateFlow()

    private val _liveAmplitude = MutableStateFlow(0.06f)
    val liveAmplitude: StateFlow<Float> = _liveAmplitude.asStateFlow()

    private val _liveSessionState = MutableStateFlow("ready")
    val liveSessionState: StateFlow<String> = _liveSessionState.asStateFlow()

    private val _liveFeedItems = MutableStateFlow<List<LiveFeedItemUi>>(emptyList())
    val liveFeedItems: StateFlow<List<LiveFeedItemUi>> = _liveFeedItems.asStateFlow()

    // Trạng thái Batch Upload
    private val _isAnalyzingBatch = MutableStateFlow(false)
    val isAnalyzingBatch: StateFlow<Boolean> = _isAnalyzingBatch.asStateFlow()

    // Ngôn ngữ
    private val _languageMode = MutableStateFlow(LectureLanguageMode.AUTO)
    val languageMode: StateFlow<LectureLanguageMode> = _languageMode.asStateFlow()

    // =========================================================================
    // 4. Các luồng dữ liệu hiện hữu (Slides, Chapters, Graph, Dictionary)
    // =========================================================================
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

    private val _targetSlidePage = MutableStateFlow<Int?>(null)
    val targetSlidePage: StateFlow<Int?> = _targetSlidePage.asStateFlow()

    private val _slides = MutableStateFlow<List<SlideUi>>(emptyList())
    val slides: StateFlow<List<SlideUi>> = _slides.asStateFlow()

    private val _nodes = MutableStateFlow<List<ConceptNodeUi>>(emptyList())
    val nodes: StateFlow<List<ConceptNodeUi>> = _nodes.asStateFlow()

    private val _edges = MutableStateFlow<List<ConceptEdgeUi>>(emptyList())
    val edges: StateFlow<List<ConceptEdgeUi>> = _edges.asStateFlow()

    private val _mindmapState = MutableStateFlow(CourseMindmapUiState(courseId = 1L, courseName = "Môn học"))
    val mindmapState: StateFlow<CourseMindmapUiState> = _mindmapState.asStateFlow()

    private val _tasks = MutableStateFlow<List<TaskItemUi>>(emptyList())
    val tasks: StateFlow<List<TaskItemUi>> = _tasks.asStateFlow()

    private val _dictionaryEntries = MutableStateFlow<List<CourseDictionaryItemUi>>(emptyList())
    val dictionaryEntries: StateFlow<List<CourseDictionaryItemUi>> = _dictionaryEntries.asStateFlow()

    private val _selectedConceptDetail = MutableStateFlow<LivingDictionaryUiModel?>(null)
    val selectedConceptDetail: StateFlow<LivingDictionaryUiModel?> = _selectedConceptDetail.asStateFlow()

    private val _savedLivingEntries = MutableStateFlow<List<SavedLivingEntry>>(emptyList())
    val savedLivingEntries: StateFlow<List<SavedLivingEntry>> = _savedLivingEntries.asStateFlow()

    private val _livingVaultFilter = MutableStateFlow(LivingVaultFilter.ALL)
    val livingVaultFilter: StateFlow<LivingVaultFilter> = _livingVaultFilter.asStateFlow()

    private val _activeLivingLookup = MutableStateFlow<LivingLookupResult?>(null)
    val activeLivingLookup: StateFlow<LivingLookupResult?> = _activeLivingLookup.asStateFlow()

    private val _isLookingUpLivingTerm = MutableStateFlow(false)
    val isLookingUpLivingTerm: StateFlow<Boolean> = _isLookingUpLivingTerm.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // State cho tính năng kiểm thử nhanh ghi chú giọng nói (Voice Only Test)
    private val _voiceTestResult = MutableStateFlow<VoiceNotesTestResponse?>(null)
    val voiceTestResult: StateFlow<VoiceNotesTestResponse?> = _voiceTestResult.asStateFlow()

    private val _isVoiceTesting = MutableStateFlow(false)
    val isVoiceTesting: StateFlow<Boolean> = _isVoiceTesting.asStateFlow()

    // State cho tính năng Nhận diện & Lọc giọng giảng viên (Speaker Verification)
    private val _voiceprintStatus = MutableStateFlow<VoiceprintStatusResponse?>(null)
    val voiceprintStatus: StateFlow<VoiceprintStatusResponse?> = _voiceprintStatus.asStateFlow()

    private val _isEnrollingVoice = MutableStateFlow(false)
    val isEnrollingVoice: StateFlow<Boolean> = _isEnrollingVoice.asStateFlow()

    fun clearVoiceTestResult() {
        _voiceTestResult.value = null
    }

    // =========================================================================
    // HÀM XỬ LÝ MÔN HỌC (COURSES)
    // =========================================================================
    fun loadCourses() {
        viewModelScope.launch {
            try {
                val res = apiService.getCourses()
                if (res.isSuccessful && res.body() != null) {
                    val list = res.body()!!.courses.map {
                        CourseUiItem(
                            id = it.id,
                            name = it.name,
                            code = it.code ?: "",
                            lecturer = it.lecturer ?: "",
                            slideCount = it.slideCount,
                        )
                    }
                    _courses.value = list
                }
            } catch (e: Exception) {
                Log.e(TAG, "Lỗi khi tải danh sách môn học: ${e.message}")
            }
        }
    }

    fun addCourse(name: String, code: String, lecturer: String) {
        viewModelScope.launch {
            try {
                val req = CourseCreateUpdateRequest(name = name, code = code, lecturer = lecturer)
                val res = apiService.createCourse(req)
                if (res.isSuccessful && res.body() != null) {
                    val created = res.body()!!
                    val newItem = CourseUiItem(
                        id = created.id,
                        name = created.name,
                        code = created.code ?: "",
                        lecturer = created.lecturer ?: "",
                        slideCount = created.slideCount,
                    )
                    _courses.value = _courses.value + newItem
                }
            } catch (e: Exception) {
                _errorMessage.value = "Không thể thêm môn học: ${e.message}"
            }
        }
    }

    fun updateCourse(id: Long, name: String, code: String, lecturer: String) {
        viewModelScope.launch {
            try {
                val req = CourseCreateUpdateRequest(name = name, code = code, lecturer = lecturer)
                val res = apiService.updateCourse(id, req)
                if (res.isSuccessful) {
                    _courses.value = _courses.value.map {
                        if (it.id == id) it.copy(name = name, code = code, lecturer = lecturer) else it
                    }
                }
            } catch (e: Exception) {
                _errorMessage.value = "Không thể cập nhật môn học: ${e.message}"
            }
        }
    }

    fun deleteCourse(id: Long) {
        viewModelScope.launch {
            try {
                val res = apiService.deleteCourse(id)
                if (res.isSuccessful) {
                    _courses.value = _courses.value.filter { it.id != id }
                }
            } catch (e: Exception) {
                _errorMessage.value = "Không thể xóa môn học: ${e.message}"
            }
        }
    }

    fun selectCourse(courseId: Long) {
        _selectedCourseId.value = courseId
        _liveSessionState.value = "ready"
        _liveFeedItems.value = emptyList()
        _uploadProgress.value = 0
        _uploadStatusText.value = ""
        loadAllData(courseId = courseId, lectureId = null)
    }

    // =========================================================================
    // HÀM XỬ LÝ KHO LƯU TRỮ SLIDE (SLIDE VAULT)
    // =========================================================================
    fun loadSlideVault(courseId: Long) {
        viewModelScope.launch {
            try {
                val res = apiService.getSlideVault(courseId)
                if (res.isSuccessful && res.body() != null) {
                    _slideVault.value = res.body()!!.slideVault.map {
                        VaultSlideFileUi(
                            id = it.id,
                            fileName = it.fileName,
                            pageCount = it.pageCount,
                            uri = null,
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Lỗi loadSlideVault: ${e.message}")
            }
        }
    }

    fun uploadSlideToVault(context: Context, uri: Uri) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val part = uriToMultipartPart(context, uri, "slide_file")
                val res = apiService.uploadSlideToVault(_selectedCourseId.value, part)
                if (res.isSuccessful && res.body() != null) {
                    val b = res.body()!!
                    val newItem = VaultSlideFileUi(
                        id = b.id,
                        fileName = b.fileName,
                        pageCount = b.pageCount,
                        uri = uri,
                    )
                    _slideVault.value = listOf(newItem) + _slideVault.value

                    // Tự động mọc nhánh Mindmap mới cho Slide vừa nạp (Màn hình 3)
                    appendOptimisticSlideBranchToMindmap(b)
                    refreshCourseMindmapSuspend(_selectedCourseId.value)
                }
            } catch (e: Exception) {
                _errorMessage.value = "Lỗi khi upload slide vào kho: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    // =========================================================================
    // HÀM XỬ LÝ VỞ GHI CHÉP INLINE, OFFLINE CACHE, CRUD & EXPORT (TUẦN 4)
    // =========================================================================
    private fun lectureCacheFile(lectureId: Long): File? {
        val ctx = appContext ?: return null
        return File(ctx.filesDir, "cache_lecture_${lectureId}.json")
    }

    private fun saveLectureOfflineCache(
        lectureId: Long,
        title: String? = null,
        inlineNotes: LectureInlineNotesResponse? = null,
        workspace: LectureWorkspaceResponse? = null,
    ) {
        val file = lectureCacheFile(lectureId) ?: return
        try {
            val existing = loadLectureOfflineCache(lectureId)
            val merged = LectureOfflineCachePayload(
                lectureId = lectureId,
                title = title ?: inlineNotes?.title ?: existing?.title ?: _currentLectureTitle.value,
                inlineNotes = inlineNotes ?: existing?.inlineNotes,
                workspace = workspace ?: existing?.workspace,
                cachedAtMs = System.currentTimeMillis(),
            )
            file.writeText(gson.toJson(merged))
        } catch (e: Exception) {
            Log.w(TAG, "Không thể ghi cache offline cho bài giảng $lectureId: ${e.message}")
        }
    }

    private fun loadLectureOfflineCache(lectureId: Long): LectureOfflineCachePayload? {
        val file = lectureCacheFile(lectureId) ?: return null
        if (!file.exists()) return null
        return try {
            gson.fromJson(file.readText(), LectureOfflineCachePayload::class.java)
        } catch (e: Exception) {
            Log.w(TAG, "Không thể đọc cache offline cho bài giảng $lectureId: ${e.message}")
            null
        }
    }

    private fun applyOfflineCacheIfAvailable(lectureId: Long): Boolean {
        val cached = loadLectureOfflineCache(lectureId) ?: return false
        var applied = false
        cached.inlineNotes?.let { inline ->
            _inlinePages.value = mapDtoToAnnotatedPages(inline.pages)
            _generalNotes.value = inline.generalNotes ?: emptyList()
            if (inline.title.isNotBlank()) _currentLectureTitle.value = inline.title
            applied = true
        }
        cached.workspace?.let { ws ->
            _slides.value = mapSlides(ws.slides)
            applied = true
        }
        if (applied) {
            _currentLectureId.value = lectureId
            if (cached.title.isNotBlank()) _currentLectureTitle.value = cached.title
            _isOfflineMode.value = true
            _errorMessage.value = null
        }
        return applied
    }

    fun loadLectureInlineNotes(lectureId: Long) {
        viewModelScope.launch {
            try {
                val res = apiService.getLectureInlineNotes(lectureId)
                if (res.isSuccessful && res.body() != null) {
                    val body = res.body()!!
                    _inlinePages.value = mapDtoToAnnotatedPages(body.pages)
                    _generalNotes.value = body.generalNotes ?: emptyList()
                    if (body.title.isNotBlank()) _currentLectureTitle.value = body.title
                    _isOfflineMode.value = false
                    saveLectureOfflineCache(lectureId = lectureId, title = body.title, inlineNotes = body)
                } else {
                    applyOfflineCacheIfAvailable(lectureId)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Lỗi loadLectureInlineNotes: ${e.message}")
                applyOfflineCacheIfAvailable(lectureId)
            }
        }
    }

    private fun persistCurrentInlinePagesToCache() {
        val lectureId = _currentLectureId.value
        val dtoPages = _inlinePages.value.map { page ->
            AnnotatedSlidePageDto(
                pageNumber = page.pageNumber,
                slideTitle = page.slideTitle,
                timeRangeLabel = page.timeRangeLabel,
                blocks = page.blocks.map { b ->
                    SlideDocumentBlockDto(
                        blockId = b.blockId,
                        kind = b.kind,
                        originalSlideText = b.originalSlideText,
                        isCurrentlySpoken = b.isCurrentlySpoken,
                        isMarkedConfused = b.isMarkedConfused,
                        inlineNotes = b.inlineNotes.map { n ->
                            InlineLecturerNoteDto(
                                id = n.id,
                                explanationText = n.explanationText,
                                timestampSec = n.timestampSec,
                                timestampLabel = n.timestampLabel,
                                category = n.category,
                                evidenceQuote = n.evidenceQuote,
                                isEdited = n.isEdited,
                            )
                        },
                        highlights = b.highlights.map { h ->
                            InlineConceptHighlightDto(
                                term = h.term,
                                sourceType = if (h.sourceType == HighlightSourceType.PREVIOUS_LECTURE) {
                                    "PREVIOUS_LECTURE"
                                } else {
                                    "ACADEMIC_INTERNET"
                                },
                                sourceLabel = h.sourceLabel,
                                explanation = h.explanation,
                            )
                        },
                    )
                },
            )
        }
        saveLectureOfflineCache(
            lectureId = lectureId,
            title = _currentLectureTitle.value,
            inlineNotes = LectureInlineNotesResponse(
                lectureId = lectureId,
                title = _currentLectureTitle.value,
                pages = dtoPages,
                generalNotes = _generalNotes.value,
            ),
        )
    }

    fun updateInlineNote(noteId: String, newText: String) {
        val cleaned = newText.trim()
        if (cleaned.isEmpty()) return

        // Cập nhật UI ngay lập tức và đồng bộ xuống cache ngoại tuyến
        _inlinePages.value = _inlinePages.value.map { page ->
            page.copy(
                blocks = page.blocks.map { block ->
                    block.copy(
                        inlineNotes = block.inlineNotes.map { n ->
                            if (n.id == noteId) n.copy(explanationText = cleaned, isEdited = true) else n
                        }
                    )
                }
            )
        }
        persistCurrentInlinePagesToCache()

        val numericId = noteId.removePrefix("note_").toLongOrNull() ?: return
        viewModelScope.launch {
            try {
                val res = apiService.updateInlineNote(numericId, UpdateInlineNoteRequest(cleaned))
                if (res.isSuccessful) {
                    loadLectureInlineNotes(_currentLectureId.value)
                } else {
                    _errorMessage.value = "Không thể lưu ghi chú lên máy chủ (${res.code()})."
                }
            } catch (e: Exception) {
                if (!_isOfflineMode.value) {
                    _errorMessage.value = "Lỗi kết nối khi cập nhật ghi chú: ${e.message}"
                }
            }
        }
    }

    fun deleteInlineNote(noteId: String) {
        // Xóa khỏi UI ngay lập tức và đồng bộ xuống cache ngoại tuyến
        _inlinePages.value = _inlinePages.value.map { page ->
            page.copy(
                blocks = page.blocks.map { block ->
                    block.copy(
                        inlineNotes = block.inlineNotes.filterNot { it.id == noteId }
                    )
                }
            )
        }
        persistCurrentInlinePagesToCache()

        val numericId = noteId.removePrefix("note_").toLongOrNull() ?: return
        viewModelScope.launch {
            try {
                val res = apiService.deleteInlineNote(numericId)
                if (res.isSuccessful) {
                    loadLectureInlineNotes(_currentLectureId.value)
                } else {
                    _errorMessage.value = "Không thể xóa ghi chú trên máy chủ (${res.code()})."
                }
            } catch (e: Exception) {
                if (!_isOfflineMode.value) {
                    _errorMessage.value = "Lỗi kết nối khi xóa ghi chú: ${e.message}"
                }
            }
        }
    }

    fun exportAndShareMarkdown(context: Context, lectureId: Long = _currentLectureId.value) {
        viewModelScope.launch {
            val markdown = try {
                val res = apiService.exportLectureMarkdown(lectureId)
                if (res.isSuccessful && res.body() != null) {
                    res.body()!!.string()
                } else {
                    buildLocalMarkdownFallback()
                }
            } catch (e: Exception) {
                Log.w(TAG, "exportLectureMarkdown offline fallback: ${e.message}")
                buildLocalMarkdownFallback()
            }

            if (markdown.isBlank()) {
                Toast.makeText(context, "Chưa có nội dung ghi chú để xuất.", Toast.LENGTH_SHORT).show()
                return@launch
            }

            // Lưu bản sao cục bộ lecture_{id}_notes.md trong thư mục app
            try {
                val outFile = File(context.filesDir, "lecture_${lectureId}_notes.md")
                outFile.writeText(markdown)
            } catch (_: Exception) {}

            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/markdown"
                putExtra(Intent.EXTRA_SUBJECT, "${_currentLectureTitle.value} - StudyAI Notes.md")
                putExtra(Intent.EXTRA_TEXT, markdown)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(sendIntent, "Xuất & Chia sẻ vở ghi chép (.md)"))
        }
    }

    private fun buildLocalMarkdownFallback(): String {
        val sb = StringBuilder()
        sb.appendLine("# ${_currentLectureTitle.value}")
        sb.appendLine()
        _inlinePages.value.forEach { page ->
            sb.appendLine("## Slide ${page.pageNumber}: ${page.slideTitle}")
            sb.appendLine()
            page.blocks.forEach { b ->
                val confused = if (b.isMarkedConfused) " **[Cần xem lại]**" else ""
                when (b.kind) {
                    "heading" -> sb.appendLine("### ${b.originalSlideText}$confused")
                    "formula" -> sb.appendLine("- `${b.originalSlideText}`$confused")
                    else -> sb.appendLine("- ${b.originalSlideText}$confused")
                }
                b.inlineNotes.forEach { n ->
                    sb.appendLine("  > **[${n.timestampLabel}]** ${n.explanationText}")
                }
            }
            sb.appendLine()
        }
        return sb.toString()
    }

    fun toggleConfusedBlock(blockId: String) {
        viewModelScope.launch {
            try {
                val res = apiService.toggleConfusedBlock(_currentLectureId.value, blockId)
                val newStatus = if (res.isSuccessful && res.body() != null) {
                    res.body()!!.isConfused
                } else {
                    // Fallback toggle local
                    val currentBlock = _inlinePages.value
                        .flatMap { it.blocks }
                        .find { it.blockId == blockId }
                    !(currentBlock?.isMarkedConfused ?: false)
                }

                _inlinePages.value = _inlinePages.value.map { page ->
                    page.copy(
                        blocks = page.blocks.map { b ->
                            if (b.blockId == blockId) b.copy(isMarkedConfused = newStatus) else b
                        }
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Lỗi toggleConfusedBlock: ${e.message}")
            }
        }
    }

    fun markCurrentBlockConfused() {
        val targetBlockId = _activeSpokenBlockId.value
            ?: _inlinePages.value.firstOrNull()?.blocks?.firstOrNull()?.blockId
        if (targetBlockId != null) {
            val matchedPage = _inlinePages.value.find { p -> p.blocks.any { it.blockId == targetBlockId } }
            val matchedBlock = matchedPage?.blocks?.find { it.blockId == targetBlockId }
            val pinItem = LiveFeedItemUi(
                id = "pin_${System.currentTimeMillis()}_$targetBlockId",
                kind = LiveFeedKind.CONFUSED_PIN,
                slideNumber = matchedPage?.pageNumber ?: 1,
                timestampLabel = formatSeconds(_recordingElapsedSec.value.toFloat()),
                text = matchedBlock?.originalSlideText?.takeIf { it.isNotBlank() } ?: "Đoạn đang giảng",
            )
            _liveFeedItems.value = listOf(pinItem) + _liveFeedItems.value
            toggleConfusedBlock(targetBlockId)
        }
    }

    fun startLiveFollowRecording(context: Context, vaultSlide: VaultSlideFileUi) {
        if (_isRecording.value) return

        viewModelScope.launch {
            _isRecording.value = true
            _recordingElapsedSec.value = 0
            _liveSessionState.value = "recording"
            _liveFeedItems.value = emptyList()
            _liveAmplitude.value = 0.06f

            // 1. Gọi backend khởi tạo bài giảng live và copy blocks
            try {
                val vaultId = vaultSlide.id.toLongOrNull() ?: 1L
                val req = StartLiveLectureRequest(
                    courseId = _selectedCourseId.value,
                    vaultSlideId = vaultId,
                    title = vaultSlide.fileName,
                    language = _languageMode.value.apiCode
                )
                val startRes = apiService.startLiveLecture(req)
                if (startRes.isSuccessful && startRes.body() != null) {
                    val b = startRes.body()!!
                    _currentLectureId.value = b.lectureId
                    _currentLectureTitle.value = b.title
                    _inlinePages.value = mapDtoToAnnotatedPages(b.pages)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Lỗi startLiveLecture: ${e.message}")
            }

            // 2. Chạy Timer hiển thị giây & cập nhật sóng âm thực tế
            timerJob = launch {
                while (isActive && _isRecording.value) {
                    delay(1000L)
                    _recordingElapsedSec.value += 1
                }
            }
            amplitudeJob?.cancel()
            amplitudeJob = launch {
                while (isActive && _isRecording.value) {
                    _liveAmplitude.value = audioRecorder?.getNormalizedAmplitude() ?: 0.06f
                    delay(120L)
                }
            }

            // 3. Khởi động Micro thu âm revolving chunks
            if (audioRecorder == null) {
                audioRecorder = ChunkedAudioRecorder(context.applicationContext)
            }

            audioRecorder?.startRecording(viewModelScope) { chunkFile, startSec ->
                uploadLiveChunk(chunkFile, startSec)
            }
        }
    }

    private fun uploadLiveChunk(chunkFile: File, startSec: Float) {
        val job = viewModelScope.launch {
            uploadLiveChunkSuspend(chunkFile, startSec)
        }
        inFlightLiveChunkJobs.add(job)
        job.invokeOnCompletion { inFlightLiveChunkJobs.remove(job) }
    }

    private suspend fun uploadLiveChunkSuspend(chunkFile: File, startSec: Float) {
        try {
            val reqFile = chunkFile.asRequestBody("audio/mp4".toMediaTypeOrNull())
            val chunkPart = MultipartBody.Part.createFormData("audio_chunk", chunkFile.name, reqFile)
            val startSecBody = startSec.toString().toRequestBody("text/plain".toMediaTypeOrNull())
            val langBody = _languageMode.value.apiCode.toRequestBody("text/plain".toMediaTypeOrNull())

            val res = apiService.uploadLiveAudioChunk(
                lectureId = _currentLectureId.value,
                audioChunk = chunkPart,
                chunkStartSec = startSecBody,
                language = langBody
            )

            if (res.isSuccessful && res.body() != null) {
                val ev = res.body()!!

                // Nếu AI xác định đây là giọng sinh viên / tạp âm ngoài lề -> bỏ qua, giữ nguyên block đang sáng
                if (ev.action == "ignored_non_teacher") {
                    Log.d(TAG, "Chunk tại ${startSec}s bị lọc bỏ (không phải giọng giảng viên).")
                    val blockedItem = LiveFeedItemUi(
                        id = "blocked_${System.currentTimeMillis()}_$startSec",
                        kind = LiveFeedKind.BLOCKED_NON_TEACHER,
                        slideNumber = ev.matchedSlidePage ?: 1,
                        timestampLabel = formatSeconds(startSec),
                        text = "Đã chặn tiếng sinh viên bàn tán",
                    )
                    _liveFeedItems.value = listOf(blockedItem) + _liveFeedItems.value
                    return
                }

                if (!ev.matchedBlockId.isNullOrBlank()) {
                    _activeSpokenBlockId.value = ev.matchedBlockId
                }

                // Cập nhật luồng Live Feed nếu chunk sinh ra ghi chú giảng viên
                if (ev.action == "attach_inline_note" && ev.inlineNote != null) {
                    val feedNote = LiveFeedItemUi(
                        id = "feed_note_${ev.inlineNote.id ?: System.currentTimeMillis()}",
                        kind = LiveFeedKind.LECTURER_NOTE,
                        slideNumber = ev.matchedSlidePage ?: 1,
                        timestampLabel = formatSeconds(ev.inlineNote.timestampStartSec),
                        text = ev.inlineNote.explanationText,
                    )
                    _liveFeedItems.value = listOf(feedNote) + _liveFeedItems.value
                }

                // Cập nhật trạng thái đang giảng và thêm note vào đúng block
                _inlinePages.value = _inlinePages.value.map { page ->
                    page.copy(
                        blocks = page.blocks.map { b ->
                            val isNowSpoken = if (!ev.matchedBlockId.isNullOrBlank()) {
                                b.blockId == ev.matchedBlockId
                            } else {
                                b.isCurrentlySpoken
                            }
                            var updatedNotes = b.inlineNotes
                            if (b.blockId == ev.matchedBlockId && ev.inlineNote != null && ev.action == "attach_inline_note") {
                                val rawText = ev.inlineNote.rawTranscript ?: ""
                                val newNote = InlineLecturerNote(
                                    id = ev.inlineNote.id ?: "live_note_${System.currentTimeMillis()}",
                                    explanationText = ev.inlineNote.explanationText,
                                    timestampSec = ev.inlineNote.timestampStartSec,
                                    timestampLabel = formatSeconds(ev.inlineNote.timestampStartSec),
                                    category = inferNoteCategory(ev.inlineNote.category, ev.inlineNote.explanationText),
                                    evidenceQuote = rawText,
                                )
                                updatedNotes = updatedNotes + newNote
                            }
                            var updatedHighlights = b.highlights
                            if (b.blockId == ev.matchedBlockId && !ev.newHighlights.isNullOrEmpty()) {
                                val newHl = ev.newHighlights.map {
                                    InlineConceptHighlight(
                                        term = it.term,
                                        sourceType = if (it.sourceType.equals("previous_lecture", true))
                                            HighlightSourceType.PREVIOUS_LECTURE else HighlightSourceType.ACADEMIC_INTERNET,
                                        sourceLabel = it.sourceLabel,
                                        explanation = it.explanation
                                    )
                                }
                                updatedHighlights = updatedHighlights + newHl
                            }
                            b.copy(
                                isCurrentlySpoken = isNowSpoken,
                                inlineNotes = updatedNotes,
                                highlights = updatedHighlights
                            )
                        }
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi uploadLiveChunk: ${e.message}")
        } finally {
            try {
                if (chunkFile.exists()) chunkFile.delete()
            } catch (_: Exception) {}
        }
    }

    fun stopLiveFollowRecording(refineFullLecture: Boolean = false) {
        _isRecording.value = false
        timerJob?.cancel()
        timerJob = null
        amplitudeJob?.cancel()
        amplitudeJob = null
        _liveAmplitude.value = 0.06f

        var finalChunk: Pair<File, Float>? = null
        audioRecorder?.stopRecording { finalFile, startSec ->
            finalChunk = finalFile to startSec
        }

        val lectureId = _currentLectureId.value
        val pendingJobs = inFlightLiveChunkJobs.toList()

        viewModelScope.launch {
            // 1. Chờ toàn bộ chunk đang gửi hoàn tất + gửi nốt chunk cuối để không rớt câu nào
            pendingJobs.forEach { it.join() }
            finalChunk?.let { (file, sec) ->
                uploadLiveChunkSuspend(file, sec)
            }

            // 2. Gọi finish-live ghép live_recording.m4a để ExoPlayer phát lại được ngay lập tức
            var refinedQueued = false
            try {
                val res = apiService.finishLiveLecture(
                    lectureId = lectureId,
                    request = FinishLiveLectureRequest(refineFullLecture = refineFullLecture)
                )
                if (res.isSuccessful) {
                    val body = res.body()
                    _liveSessionState.value = "finished"
                    refinedQueued = body?.refinedQueued == true
                    Log.i(TAG, "Đã chốt buổi Live #$lectureId: $body")
                } else {
                    _errorMessage.value = "Không thể ghép file audio sau buổi Live (${res.code()})."
                }
            } catch (e: Exception) {
                Log.e(TAG, "Lỗi finishLiveLecture: ${e.message}")
                _errorMessage.value = "Lỗi kết nối khi chốt buổi Live: ${e.message}"
            }

            if (refinedQueued) {
                _isProcessingQueue.value = true
                _uploadProgress.value = 5
                _uploadStatusText.value = "Đang tinh chỉnh toàn bài bằng AI (pyannote + Viterbi + LLM)..."
                try {
                    pollLectureQueueUntilDone(
                        lectureId = lectureId,
                        lectureTitle = _currentLectureTitle.value,
                    )
                } finally {
                    _isProcessingQueue.value = false
                }
            } else {
                // 3. Tải lại danh sách note & cập nhật cache offline + nhánh Mindmap mới
                loadLectureInlineNotes(lectureId)
                refreshCourseMindmapSuspend(_selectedCourseId.value)
            }
        }
    }

    // =========================================================================
    // HÀM XỬ LÝ BATCH UPLOAD (SLIDE + AUDIO) QUA HÀNG ĐỢI & POLLING (TUẦN 1)
    // =========================================================================
    private val _uploadProgress = MutableStateFlow(0)
    val uploadProgress: StateFlow<Int> = _uploadProgress.asStateFlow()

    private val _uploadStatusText = MutableStateFlow("")
    val uploadStatusText: StateFlow<String> = _uploadStatusText.asStateFlow()

    private val _isProcessingQueue = MutableStateFlow(false)
    val isProcessingQueue: StateFlow<Boolean> = _isProcessingQueue.asStateFlow()

    private suspend fun pollLectureQueueUntilDone(
        lectureId: Long,
        lectureTitle: String,
        onDone: (Long) -> Unit = {},
    ) {
        var isDone = false
        while (!isDone && kotlinx.coroutines.currentCoroutineContext().isActive) {
            delay(2000)
            val statusRes = apiService.getLectureStatus(lectureId)
            if (statusRes.isSuccessful && statusRes.body() != null) {
                val st = statusRes.body()!!
                val (mappedPercent, labelText) = when (st.processingStatus) {
                    "queued" -> 5 to "Đang xếp hàng chờ xử lý..."
                    "parsing_slides" -> 15 to "Đang phân tích và trích xuất nội dung Slide (15%)..."
                    "running_asr" -> 45 to "Đang nhận dạng giọng nói ASR & VAD (45%)..."
                    "filtering_speaker" -> 55 to "Đang lọc tách giọng giảng viên & khử nhiễu (55%)..."
                    "aligning_slides" -> 75 to "Đang căn gióng lời giảng vào từng trang Slide (75%)..."
                    "synthesizing_notes" -> 90 to "Đang dùng LLM tổng hợp ghi chú thông minh (90%)..."
                    "completed" -> 100 to "Hoàn tất xử lý bài giảng (100%)!"
                    "failed" -> 0 to "Xử lý thất bại: ${st.errorMessage ?: "Lỗi không xác định"}"
                    else -> st.progressPercent.coerceIn(5, 99) to "Đang xử lý: ${st.processingStatus} (${st.progressPercent}%)"
                }
                _uploadProgress.value = if (st.processingStatus == "failed") 0 else mappedPercent.coerceAtLeast(st.progressPercent)
                _uploadStatusText.value = labelText

                if (st.processingStatus == "completed") {
                    isDone = true
                    _uploadProgress.value = 100
                    _currentLectureId.value = lectureId
                    _currentLectureTitle.value = lectureTitle
                    loadAllData(_selectedCourseId.value, lectureId)
                    onDone(lectureId)
                } else if (st.processingStatus == "failed") {
                    isDone = true
                    _errorMessage.value = st.errorMessage ?: "Xử lý bài giảng thất bại."
                }
            }
        }
    }

    fun uploadAndTrackLecture(
        context: Context,
        slideUri: Uri,
        audioUri: Uri,
        title: String? = null,
        languageMode: LectureLanguageMode = _languageMode.value,
        onDone: (Long) -> Unit = {}
    ) {
        viewModelScope.launch {
            _isProcessingQueue.value = true
            _uploadProgress.value = 5
            _uploadStatusText.value = "Đang tải Slide và Âm thanh lên Server..."
            try {
                val slidePart = uriToMultipartPart(context, slideUri, "slide_file")
                val audioPart = uriToMultipartPart(context, audioUri, "audio_file")
                val lectureTitle = title ?: getFileName(context, slideUri).substringBeforeLast(".")
                val titleBody = lectureTitle.toRequestBody("text/plain".toMediaTypeOrNull())
                val langBody = languageMode.apiCode.toRequestBody("text/plain".toMediaTypeOrNull())

                val res = apiService.uploadLecture(
                    courseId = _selectedCourseId.value,
                    title = titleBody,
                    language = langBody,
                    slideFile = slidePart,
                    audioFile = audioPart
                )

                if (res.isSuccessful && res.body() != null) {
                    val lectureId = res.body()!!.lectureId
                    _uploadProgress.value = res.body()!!.progressPercent.coerceAtLeast(5)
                    _uploadStatusText.value = "Đã xếp hàng xử lý (ID: $lectureId)..."
                    pollLectureQueueUntilDone(
                        lectureId = lectureId,
                        lectureTitle = lectureTitle,
                        onDone = onDone,
                    )
                } else {
                    _uploadProgress.value = 0
                    _errorMessage.value = "Tải bài giảng lên server thất bại."
                }
            } catch (e: Exception) {
                _uploadProgress.value = 0
                _errorMessage.value = "Lỗi upload bài giảng: ${e.message}"
            } finally {
                _isProcessingQueue.value = false
            }
        }
    }

    fun testVoiceOnlyNotes(context: Context, audioUri: Uri, title: String = "Thử nghiệm ghi chú giọng nói") {
        viewModelScope.launch {
            _isVoiceTesting.value = true
            _errorMessage.value = null
            try {
                val audioPart = uriToMultipartPart(context, audioUri, "audio_file")
                val titleBody = title.toRequestBody("text/plain".toMediaTypeOrNull())
                val langBody = "vi".toRequestBody("text/plain".toMediaTypeOrNull())

                val res = apiService.testVoiceNotes(audioPart, titleBody, langBody)
                if (res.isSuccessful && res.body() != null) {
                    _voiceTestResult.value = res.body()
                } else {
                    _errorMessage.value = "Lỗi khi xử lý ghi chú giọng nói: ${res.code()} - ${res.message()}"
                }
            } catch (e: Exception) {
                _errorMessage.value = "Lỗi kiểm thử giọng nói: ${e.message}"
            } finally {
                _isVoiceTesting.value = false
            }
        }
    }

    fun checkVoiceprintStatus(courseId: Long = _selectedCourseId.value) {
        viewModelScope.launch {
            try {
                val res = apiService.getVoiceprint(courseId)
                if (res.isSuccessful && res.body() != null) {
                    _voiceprintStatus.value = res.body()
                }
            } catch (e: Exception) {
                Log.w(TAG, "checkVoiceprintStatus error: ${e.message}")
            }
        }
    }

    fun enrollTeacherVoice(
        context: Context,
        audioUri: Uri,
        lecturerName: String = "",
        courseId: Long = _selectedCourseId.value,
        onSuccess: (VoiceprintEnrollResponse) -> Unit = {}
    ) {
        viewModelScope.launch {
            _isEnrollingVoice.value = true
            _errorMessage.value = null
            try {
                val audioPart = uriToMultipartPart(context, audioUri, "sample_audio")
                val nameBody = if (lecturerName.isNotBlank()) {
                    lecturerName.toRequestBody("text/plain".toMediaTypeOrNull())
                } else null
                val res = apiService.enrollVoiceprint(courseId, audioPart, nameBody)
                if (res.isSuccessful && res.body() != null) {
                    val body = res.body()!!
                    _voiceprintStatus.value = VoiceprintStatusResponse(
                        hasVoiceprint = true,
                        voiceprintId = body.voiceprintId,
                        courseId = body.courseId,
                        lecturerName = body.lecturerName,
                        embeddingDim = body.embeddingDim,
                        cosineThreshold = body.cosineThreshold
                    )
                    onSuccess(body)
                } else {
                    _errorMessage.value = "Lỗi đăng ký giọng: ${res.code()} - ${res.message()}"
                }
            } catch (e: Exception) {
                _errorMessage.value = "Lỗi gửi mẫu giọng: ${e.message}"
            } finally {
                _isEnrollingVoice.value = false
            }
        }
    }

    // =========================================================================
    // HÀM XỬ LÝ BATCH UPLOAD (SLIDE + AUDIO)
    // =========================================================================
    fun analyzeUploadedPair(
        context: Context,
        slideUri: Uri,
        audioUri: Uri,
        languageMode: LectureLanguageMode = _languageMode.value,
        onDone: () -> Unit = {}
    ) {
        viewModelScope.launch {
            _isAnalyzingBatch.value = true
            _isLoading.value = true
            try {
                val slidePart = uriToMultipartPart(context, slideUri, "slide_file")
                val audioPart = uriToMultipartPart(context, audioUri, "audio_file")
                val courseIdBody = _selectedCourseId.value.toString().toRequestBody("text/plain".toMediaTypeOrNull())
                val titleBody = getFileName(context, slideUri).toRequestBody("text/plain".toMediaTypeOrNull())
                val langBody = languageMode.apiCode.toRequestBody("text/plain".toMediaTypeOrNull())

                val res = apiService.uploadLecturePair(
                    courseId = courseIdBody,
                    title = titleBody,
                    language = langBody,
                    slideFile = slidePart,
                    audioFile = audioPart
                )

                if (res.isSuccessful && res.body() != null) {
                    val body = res.body()!!
                    _currentLectureId.value = body.lectureId
                    _currentLectureTitle.value = body.title
                    _inlinePages.value = mapDtoToAnnotatedPages(body.pages)
                    _generalNotes.value = body.generalNotes ?: emptyList()
                    onDone()
                } else {
                    _errorMessage.value = "Phân tích bài giảng thất bại từ phía máy chủ."
                }
            } catch (e: Exception) {
                _errorMessage.value = "Lỗi khi phân tích tệp: ${e.message}"
            } finally {
                _isAnalyzingBatch.value = false
                _isLoading.value = false
            }
        }
    }

    fun setLanguageMode(mode: LectureLanguageMode) {
        _languageMode.value = mode
    }

    // =========================================================================
    // CÁC HÀM MAP DTO & UTILS
    // =========================================================================
    private fun inferNoteCategory(rawCategory: String?, text: String): String {
        val cleaned = rawCategory?.trim()?.lowercase()
        if (cleaned == "worked_example" || cleaned == "exam_tip") {
            return cleaned
        }
        val lower = text.trim().lowercase()
        if (
            lower.startsWith("lưu ý") ||
            lower.startsWith("mẹo") ||
            lower.startsWith("cảnh báo") ||
            lower.contains("bắt buộc") ||
            lower.contains("tránh lỗi") ||
            lower.contains("cẩn thận") ||
            lower.contains("hay ra thi")
        ) {
            return "exam_tip"
        }
        if (
            lower.startsWith("ví dụ") ||
            lower.startsWith("thực hành") ||
            lower.startsWith("minh họa") ||
            lower.contains("ví dụ thực tế")
        ) {
            return "worked_example"
        }
        return if (cleaned == "core_explanation") cleaned else "core_explanation"
    }

    private fun mapDtoToAnnotatedPages(dtoPages: List<AnnotatedSlidePageDto>): List<AnnotatedSlidePageUi> {
        return dtoPages.map { p ->
            val mappedBlocks = p.blocks.map { b ->
                SlideDocumentBlockUi(
                    blockId = b.blockId,
                    kind = b.kind,
                    originalSlideText = b.originalSlideText,
                    isCurrentlySpoken = b.isCurrentlySpoken,
                    isMarkedConfused = b.isMarkedConfused,
                    inlineNotes = b.inlineNotes.map { n ->
                        InlineLecturerNote(
                            id = n.id,
                            explanationText = n.explanationText,
                            timestampSec = n.timestampSec,
                            timestampLabel = n.timestampLabel.ifEmpty { formatSeconds(n.timestampSec) },
                            category = inferNoteCategory(n.category, n.explanationText),
                            evidenceQuote = n.evidenceQuote ?: "",
                            isEdited = n.isEdited == true,
                        )
                    },
                    highlights = b.highlights.map { h ->
                        InlineConceptHighlight(
                            term = h.term,
                            sourceType = if (h.sourceType.equals("previous_lecture", true))
                                HighlightSourceType.PREVIOUS_LECTURE else HighlightSourceType.ACADEMIC_INTERNET,
                            sourceLabel = h.sourceLabel,
                            explanation = h.explanation
                        )
                    }
                )
            }
            val resolvedTimeRange = p.timeRangeLabel?.takeIf { it.isNotBlank() } ?: run {
                val noteTimes = mappedBlocks.flatMap { it.inlineNotes }
                    .map { it.timestampSec }
                    .filter { it > 0f }
                if (noteTimes.isEmpty()) {
                    ""
                } else {
                    val minSec = noteTimes.minOrNull() ?: 0f
                    val maxSec = noteTimes.maxOrNull() ?: 0f
                    val startStr = formatSeconds(minSec)
                    val endStr = formatSeconds(maxSec)
                    if (maxSec > minSec && endStr != startStr) "$startStr – $endStr" else startStr
                }
            }
            AnnotatedSlidePageUi(
                pageNumber = p.pageNumber,
                slideTitle = p.slideTitle,
                timeRangeLabel = resolvedTimeRange,
                blocks = mappedBlocks,
            )
        }
    }

    private fun uriToMultipartPart(context: Context, uri: Uri, partName: String): MultipartBody.Part {
        val contentResolver = context.contentResolver
        val mimeType = contentResolver.getType(uri) ?: "application/octet-stream"
        val fileName = getFileName(context, uri) ?: "upload_file"
        val inputStream = contentResolver.openInputStream(uri)
            ?: throw IllegalArgumentException("Không thể mở đường dẫn tệp")
        val tempFile = File(context.cacheDir, "upload_${System.currentTimeMillis()}_$fileName")
        tempFile.outputStream().use { out -> inputStream.copyTo(out) }
        val reqFile = tempFile.asRequestBody(mimeType.toMediaTypeOrNull())
        return MultipartBody.Part.createFormData(partName, fileName, reqFile)
    }

    private fun getFileName(context: Context, uri: Uri): String {
        var name = "file_${System.currentTimeMillis()}"
        try {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        name = it.getString(nameIndex)
                    }
                }
            }
        } catch (_: Exception) {}
        return name
    }

    private fun formatSeconds(sec: Float): String {
        val total = sec.toInt()
        val m = total / 60
        val s = total % 60
        return "%02d:%02d".format(m, s)
    }

    // =========================================================================
    // CÁC HÀM TƯƠNG THÍCH CHO SCREEN 1, 2, 3
    // =========================================================================
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

    fun loadAllData(courseId: Long = 1L, lectureId: Long? = null) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            var fallbackLectureId = lectureId ?: _currentLectureId.value
            try {
                loadCourses()
                loadSlideVault(courseId)

                // 0. Tải danh mục Bài giảng của môn học trước để chọn đúng lecture thuộc môn này
                var lecturesLoadedFromServer = false
                try {
                    val lecRes = apiService.getCourseLectures(courseId)
                    if (lecRes.isSuccessful && lecRes.body() != null) {
                        lecturesLoadedFromServer = true
                        _chapters.value = lecRes.body()!!.lectures.map {
                            ChapterUiModel(
                                id = it.id,
                                title = it.title,
                                slideCount = it.slideCount
                            )
                        }
                    }
                } catch (_: Exception) {}

                val resolvedLectureId: Long? = when {
                    lecturesLoadedFromServer && _chapters.value.isEmpty() -> null
                    lectureId != null && (_chapters.value.isEmpty() || _chapters.value.any { it.id == lectureId }) -> lectureId
                    else -> _chapters.value.lastOrNull()?.id ?: _chapters.value.firstOrNull()?.id ?: 1L
                }

                if (resolvedLectureId != null) {
                    fallbackLectureId = resolvedLectureId
                    _currentLectureId.value = resolvedLectureId
                    loadLectureInlineNotes(resolvedLectureId)

                    // 1. Tải workspace & danh sách slide
                    val workspaceRes = apiService.getLectureWorkspace(resolvedLectureId)
                    if (workspaceRes.isSuccessful && workspaceRes.body() != null) {
                        val body = workspaceRes.body()!!
                        _slides.value = mapSlides(body.slides)
                        val matchedTitle = _chapters.value.find { it.id == resolvedLectureId }?.title
                            ?: (body.lecture["title"] as? String)
                            ?: "Bài giảng #$resolvedLectureId"
                        _currentLectureTitle.value = matchedTitle
                        _currentSlideIndex.value = 0
                        _isOfflineMode.value = false
                        saveLectureOfflineCache(lectureId = resolvedLectureId, title = matchedTitle, workspace = body)
                    }
                } else {
                    _slides.value = emptyList()
                    _inlinePages.value = emptyList()
                    _generalNotes.value = emptyList()
                    val courseName = _courses.value.find { it.id == courseId }?.name ?: "Môn học"
                    _currentLectureTitle.value = "$courseName • Chưa có bài giảng"
                    _currentSlideIndex.value = 0
                }

                // 2. Tải Knowledge Graph
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

                // 3. Tải Từ điển sống
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

                // 4. Đồng bộ Mindmap tri thức 3 cấp theo môn học
                refreshCourseMindmapSuspend(courseId)

                // 5. Đồng bộ Kho Từ điển sống theo môn học
                loadSavedLivingEntriesSuspend(courseId)
            } catch (e: Exception) {
                Log.e(TAG, "loadAllData error", e)
                if (!applyOfflineCacheIfAvailable(fallbackLectureId)) {
                    _errorMessage.value = "Lỗi kết nối máy chủ StudyAI: ${e.localizedMessage ?: e.message ?: e.javaClass.simpleName}"
                }
                val fallbackBranches = buildFallbackMindmapBranches(courseId)
                if (fallbackBranches.isNotEmpty()) {
                    val cName = _courses.value.find { it.id == courseId }?.name ?: _mindmapState.value.courseName
                    _mindmapState.value = _mindmapState.value.copy(
                        courseId = courseId,
                        courseName = cName,
                        branches = fallbackBranches,
                    )
                }
                val cachedLiving = loadLivingDictionaryCache(courseId)
                if (cachedLiving.isNotEmpty()) {
                    _savedLivingEntries.value = cachedLiving
                }
            } finally {
                _isLoading.value = false
            }
        }
    }

    // =========================================================================
    // HÀM XỬ LÝ MINDMAP TRI THỨC 3 CẤP (MÀN HÌNH 3)
    // =========================================================================
    fun setMindmapZoom(zoom: Float) {
        _mindmapState.value = _mindmapState.value.copy(zoomScale = zoom.coerceIn(0.75f, 1.75f))
    }

    fun setMindmapFilter(filter: MasteryFilter) {
        _mindmapState.value = _mindmapState.value.copy(selectedFilter = filter)
    }

    fun setMindmapSearchQuery(query: String) {
        _mindmapState.value = _mindmapState.value.copy(searchQuery = query)
    }

    fun markMindmapBranchSeen(branchId: String) {
        _mindmapState.value = _mindmapState.value.copy(
            branches = _mindmapState.value.branches.map { br ->
                if (br.id == branchId) br.copy(isNewlyAdded = false) else br
            }
        )
        if (branchId.toLongOrNull() == null) return
        viewModelScope.launch {
            try {
                apiService.markMindmapBranchSeen(branchId)
            } catch (e: Exception) {
                Log.w(TAG, "markMindmapBranchSeen failed: ${e.message}")
            }
        }
    }

    fun toggleMindmapLeafMastery(leafId: String) {
        _mindmapState.value = _mindmapState.value.copy(
            branches = _mindmapState.value.branches.map { br ->
                br.copy(
                    leaves = br.leaves.map { lf ->
                        if (lf.id == leafId) lf.copy(isMastered = !lf.isMastered) else lf
                    }
                )
            }
        )
        if (leafId.toLongOrNull() == null) return
        viewModelScope.launch {
            try {
                val res = apiService.toggleMindmapLeafMastery(leafId)
                if (res.isSuccessful && res.body() != null) {
                    val serverMastered = res.body()!!.isMastered
                    _mindmapState.value = _mindmapState.value.copy(
                        branches = _mindmapState.value.branches.map { br ->
                            br.copy(
                                leaves = br.leaves.map { lf ->
                                    if (lf.id == leafId) lf.copy(isMastered = serverMastered) else lf
                                }
                            )
                        }
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "toggleMindmapLeafMastery failed: ${e.message}")
            }
        }
    }

    fun refreshCourseMindmap(courseId: Long = _selectedCourseId.value) {
        viewModelScope.launch {
            refreshCourseMindmapSuspend(courseId)
        }
    }

    private suspend fun refreshCourseMindmapSuspend(courseId: Long) {
        val localCourseName = _courses.value.find { it.id == courseId }?.name
            ?: _mindmapState.value.courseName.ifBlank { "Môn học" }
        try {
            val res = apiService.getCourseMindmap(courseId)
            if (res.isSuccessful && res.body() != null) {
                val body = res.body()!!
                val mappedBranches = body.branches.mapIndexed { idx, br ->
                    val parsedTone = runCatching { BranchTone.valueOf(br.tone.uppercase()) }
                        .getOrDefault(BranchTone.values()[idx % BranchTone.values().size])
                    MindmapBranch(
                        id = br.id.ifBlank { "br_$idx" },
                        lectureId = br.lectureId,
                        title = br.title,
                        tone = parsedTone,
                        isNewlyAdded = br.isNewlyAdded,
                        leaves = br.leaves.mapIndexed { lIdx, lf ->
                            MindmapLeafNode(
                                id = lf.id.ifBlank { "lf_${idx}_$lIdx" },
                                title = lf.title,
                                slideRef = lf.slideRef.ifBlank { "Slide ${lf.slideNumber}" },
                                lectureId = if (lf.lectureId > 0L) lf.lectureId else br.lectureId,
                                slideNumber = lf.slideNumber.coerceAtLeast(1),
                                timestampSec = lf.timestampSec.coerceAtLeast(0),
                                summary = lf.summary,
                                isMastered = lf.isMastered,
                            )
                        },
                    )
                }
                val finalBranches = if (mappedBranches.isNotEmpty()) mappedBranches else buildFallbackMindmapBranches(courseId)
                _mindmapState.value = _mindmapState.value.copy(
                    courseId = courseId,
                    courseName = body.courseName.ifBlank { localCourseName },
                    branches = finalBranches,
                )
                return
            }
        } catch (e: Exception) {
            Log.w(TAG, "getCourseMindmap fallback: ${e.message}")
        }

        val fallback = buildFallbackMindmapBranches(courseId)
        _mindmapState.value = _mindmapState.value.copy(
            courseId = courseId,
            courseName = localCourseName,
            branches = fallback,
        )
    }

    private fun appendOptimisticSlideBranchToMindmap(uploaded: VaultUploadResponse) {
        val tones = BranchTone.values()
        val currentBranches = _mindmapState.value.branches
        val cleanTitle = uploaded.fileName.substringBeforeLast(".").ifBlank { uploaded.fileName }
        val byPage = (uploaded.blocks ?: emptyList()).groupBy { it.slidePage.coerceAtLeast(1) }
        val leaves = if (byPage.isNotEmpty()) {
            byPage.entries.sortedBy { it.key }.take(8).mapIndexed { idx, (pageNum, blocks) ->
                val head = blocks.firstOrNull { it.kind.equals("heading", true) }?.text
                    ?: blocks.firstOrNull()?.text
                    ?: "Slide $pageNum"
                val detail = blocks.filter { it.text != head }.take(2).joinToString(" • ") { it.text }
                MindmapLeafNode(
                    id = "tmp_leaf_${uploaded.id}_$idx",
                    title = head.take(90),
                    slideRef = "Slide $pageNum",
                    lectureId = 0L,
                    slideNumber = pageNum,
                    timestampSec = 0,
                    summary = detail.ifBlank { head },
                    isMastered = false,
                )
            }
        } else {
            listOf(
                MindmapLeafNode(
                    id = "tmp_leaf_${uploaded.id}_0",
                    title = cleanTitle,
                    slideRef = "Slide 1",
                    lectureId = 0L,
                    slideNumber = 1,
                    timestampSec = 0,
                    summary = "${uploaded.pageCount} trang slide mới nạp vào kho",
                    isMastered = false,
                )
            )
        }
        val newBranch = MindmapBranch(
            id = "tmp_vault_${uploaded.id}",
            lectureId = 0L,
            title = cleanTitle,
            tone = tones[currentBranches.size % tones.size],
            isNewlyAdded = true,
            leaves = leaves,
        )
        _mindmapState.value = _mindmapState.value.copy(
            branches = currentBranches.filterNot { it.id == newBranch.id } + newBranch
        )
    }

    private fun buildFallbackMindmapBranches(courseId: Long): List<MindmapBranch> {
        val tones = BranchTone.values()
        val branches = mutableListOf<MindmapBranch>()
        if (_chapters.value.isNotEmpty()) {
            _chapters.value.forEachIndexed { idx, ch ->
                val chTitleClean = ch.title.substringBeforeLast(".")
                val branchTitle = if (chTitleClean.startsWith("Bài", ignoreCase = true)) {
                    chTitleClean
                } else {
                    "Bài #${ch.id} • $chTitleClean"
                }
                val chLeaves = if (ch.id == _currentLectureId.value && _inlinePages.value.isNotEmpty()) {
                    _inlinePages.value.take(8).mapIndexed { pIdx, p ->
                        val head = p.slideTitle.ifBlank { p.blocks.firstOrNull()?.originalSlideText ?: "Slide ${p.pageNumber}" }
                        val firstTs = p.blocks.flatMap { it.inlineNotes }.firstOrNull()?.timestampSec?.toInt() ?: 0
                        val summary = p.blocks.filter { it.originalSlideText != head }.take(2)
                            .joinToString(" • ") { it.originalSlideText }
                            .ifBlank { head }
                        MindmapLeafNode(
                            id = "fb_${ch.id}_${p.pageNumber}_$pIdx",
                            title = head.take(90),
                            slideRef = "Slide ${p.pageNumber}",
                            lectureId = ch.id,
                            slideNumber = p.pageNumber,
                            timestampSec = firstTs,
                            summary = summary,
                            isMastered = pIdx == 0,
                        )
                    }
                } else {
                    val matchingNodes = _nodes.value.filter { it.firstIntroducedLectureId.toLong() == ch.id }
                    if (matchingNodes.isNotEmpty()) {
                        matchingNodes.take(8).map { n ->
                            MindmapLeafNode(
                                id = "fb_node_${n.id}",
                                title = n.canonicalNameVi,
                                slideRef = "Slide ${n.firstIntroducedSlideNo}",
                                lectureId = ch.id,
                                slideNumber = n.firstIntroducedSlideNo.coerceAtLeast(1),
                                timestampSec = 0,
                                summary = n.definitionSummary,
                                isMastered = !n.hasViolation,
                            )
                        }
                    } else {
                        listOf(
                            MindmapLeafNode(
                                id = "fb_ch_${ch.id}",
                                title = chTitleClean,
                                slideRef = if (ch.slideCount > 1) "Slide 1–${ch.slideCount}" else "Slide 1",
                                lectureId = ch.id,
                                slideNumber = 1,
                                timestampSec = 0,
                                summary = "${ch.slideCount} trang bài giảng thuộc khóa #$courseId",
                                isMastered = false,
                            )
                        )
                    }
                }
                branches.add(
                    MindmapBranch(
                        id = "fb_br_${ch.id}",
                        lectureId = ch.id,
                        title = branchTitle,
                        tone = tones[idx % tones.size],
                        isNewlyAdded = false,
                        leaves = chLeaves,
                    )
                )
            }
        }
        return branches
    }

    fun switchCourse(courseId: Long) {
        selectCourse(courseId)
    }

    fun switchLecture(lectureId: Long, targetSlideIdx: Int = 0, targetSec: Float? = null) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                loadLectureInlineNotes(lectureId)
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
                    _isOfflineMode.value = false
                    saveLectureOfflineCache(lectureId = lectureId, title = matchedTitle, workspace = body)
                }
            } catch (e: Exception) {
                if (!applyOfflineCacheIfAvailable(lectureId)) {
                    _errorMessage.value = "Không thể chuyển chương: ${e.message}"
                }
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun jumpToChapterSlide(lectureId: Int, slideNumber: Int, timestampSec: Float) {
        dismissConceptDetail()
        val targetSlideIdx = (slideNumber - 1).coerceAtLeast(0)
        _targetSlidePage.value = slideNumber.coerceAtLeast(1)
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

    fun requestScrollToSlide(pageNumber: Int) {
        _targetSlidePage.value = pageNumber.coerceAtLeast(1)
    }

    fun clearTargetSlidePage() {
        _targetSlidePage.value = null
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

    // =========================================================================
    // HÀM XỬ LÝ TỪ ĐIỂN SỐNG TOÀN HỆ THỐNG (MÀN HÌNH 4)
    // =========================================================================
    private fun TrustedSourceDocDto.toUiModel(): TrustedSourceDoc {
        return TrustedSourceDoc(
            domain = domain,
            badge = badge,
            title = title,
            excerpt = excerpt,
            url = url,
        )
    }

    private fun TrustedSourceDoc.toDto(): TrustedSourceDocDto {
        return TrustedSourceDocDto(
            domain = domain,
            badge = badge,
            title = title,
            excerpt = excerpt,
            url = url,
        )
    }

    private fun SavedLivingEntryDto.toUiModel(): SavedLivingEntry {
        return SavedLivingEntry(
            id = id,
            term = term,
            originLabel = originLabel.ifBlank { "Từ điển sống" },
            lectureId = lectureId,
            slideNumber = slideNumber,
            contextQuote = contextQuote,
            summary = aiExplanation,
            isMastered = isMastered,
            isNew = isNew,
            codeExample = codeExample,
            simpleFollowup = simpleFollowup,
            pitfallFollowup = pitfallFollowup,
            trustedSources = trustedSources.map { it.toUiModel() },
        )
    }

    private fun livingDictCacheFile(courseId: Long): File? {
        val ctx = appContext ?: return null
        return File(ctx.filesDir, "cache_living_dict_${courseId}.json")
    }

    private fun saveLivingDictionaryCache(courseId: Long, entries: List<SavedLivingEntry>) {
        val file = livingDictCacheFile(courseId) ?: return
        try {
            val payload = LivingDictionaryCachePayload(
                courseId = courseId,
                entries = entries,
                cachedAtMs = System.currentTimeMillis(),
            )
            file.writeText(gson.toJson(payload))
        } catch (e: Exception) {
            Log.w(TAG, "Không thể ghi cache Từ điển sống cho môn $courseId: ${e.message}")
        }
    }

    private fun loadLivingDictionaryCache(courseId: Long): List<SavedLivingEntry> {
        val file = livingDictCacheFile(courseId) ?: return emptyList()
        if (!file.exists()) return emptyList()
        return try {
            gson.fromJson(file.readText(), LivingDictionaryCachePayload::class.java)?.entries ?: emptyList()
        } catch (e: Exception) {
            Log.w(TAG, "Không thể đọc cache Từ điển sống cho môn $courseId: ${e.message}")
            emptyList()
        }
    }

    private suspend fun loadSavedLivingEntriesSuspend(courseId: Long) {
        try {
            val res = apiService.getSavedLivingEntries(courseId)
            if (res.isSuccessful && res.body() != null) {
                val mapped = res.body()!!.entries.map { it.toUiModel() }
                _savedLivingEntries.value = mapped
                saveLivingDictionaryCache(courseId, mapped)
                return
            }
        } catch (e: Exception) {
            Log.w(TAG, "loadSavedLivingEntriesSuspend error: ${e.message}")
        }
        val cached = loadLivingDictionaryCache(courseId)
        if (cached.isNotEmpty()) {
            _savedLivingEntries.value = cached
        }
    }

    fun loadSavedLivingEntries(courseId: Long = _selectedCourseId.value) {
        viewModelScope.launch {
            loadSavedLivingEntriesSuspend(courseId)
        }
    }

    fun setLivingVaultFilter(filter: LivingVaultFilter) {
        _livingVaultFilter.value = filter
    }

    fun openSavedLivingEntry(entry: SavedLivingEntry) {
        _activeLivingLookup.value = LivingLookupResult(
            id = entry.id,
            term = entry.term,
            originLabel = entry.originLabel,
            lectureId = entry.lectureId,
            slideNumber = entry.slideNumber,
            contextQuote = entry.contextQuote,
            aiExplanation = entry.summary,
            codeExample = entry.codeExample,
            simpleFollowup = entry.simpleFollowup,
            pitfallFollowup = entry.pitfallFollowup,
            trustedSources = entry.trustedSources,
        )
    }

    fun dismissLivingLookup() {
        _activeLivingLookup.value = null
        _isLookingUpLivingTerm.value = false
    }

    fun lookupLivingTerm(
        selectedText: String,
        surroundingSentence: String = selectedText,
        courseId: Long? = _selectedCourseId.value,
        lectureId: Long? = _currentLectureId.value,
        slideNumber: Int? = null,
        originLabel: String? = null,
    ) {
        val cleanTerm = selectedText.trim()
        if (cleanTerm.isEmpty()) return

        val resolvedOrigin = originLabel?.takeIf { it.isNotBlank() }
            ?: if (slideNumber != null) {
                val cleanLec = _currentLectureTitle.value
                    .replace(Regex("^Bài\\s*#?\\d+\\s*[•:\\-–]\\s*", RegexOption.IGNORE_CASE), "")
                    .trim()
                if (cleanLec.isNotBlank()) "Slide $slideNumber • $cleanLec" else "Slide $slideNumber"
            } else {
                "Từ điển sống"
            }

        val resolvedQuote = surroundingSentence.trim().ifBlank { cleanTerm }
        val existingSaved = _savedLivingEntries.value.find {
            it.term.equals(cleanTerm, ignoreCase = true)
        }

        _isLookingUpLivingTerm.value = true
        _activeLivingLookup.value = LivingLookupResult(
            id = existingSaved?.id,
            term = cleanTerm,
            originLabel = resolvedOrigin,
            lectureId = lectureId,
            slideNumber = slideNumber,
            contextQuote = resolvedQuote,
            aiExplanation = existingSaved?.summary?.takeIf { it.isNotBlank() }
                ?: "Đang phân tích ngữ cảnh và tổng hợp ví dụ thực tế cho \"$cleanTerm\"...",
            codeExample = existingSaved?.codeExample,
            simpleFollowup = existingSaved?.simpleFollowup,
            pitfallFollowup = existingSaved?.pitfallFollowup,
            trustedSources = existingSaved?.trustedSources ?: emptyList(),
        )

        viewModelScope.launch {
            try {
                val req = LivingLookupRequestDto(
                    courseId = courseId?.takeIf { it > 0L },
                    lectureId = lectureId?.takeIf { it > 0L },
                    slideNumber = slideNumber,
                    selectedText = cleanTerm,
                    surroundingSentence = resolvedQuote,
                    originLabel = resolvedOrigin,
                )
                val res = apiService.lookupLivingTerm(req)
                if (res.isSuccessful && res.body() != null) {
                    val body = res.body()!!
                    val matchedSaved = _savedLivingEntries.value.find {
                        it.term.equals(body.term.ifBlank { cleanTerm }, ignoreCase = true)
                    }
                    _activeLivingLookup.value = LivingLookupResult(
                        id = matchedSaved?.id,
                        term = body.term.ifBlank { cleanTerm },
                        originLabel = body.originLabel.ifBlank { resolvedOrigin },
                        lectureId = body.lectureId ?: lectureId,
                        slideNumber = body.slideNumber ?: slideNumber,
                        contextQuote = body.contextQuote.ifBlank { resolvedQuote },
                        aiExplanation = body.aiExplanation.ifBlank {
                            matchedSaved?.summary
                                ?: "Thuật ngữ \"$cleanTerm\" được sử dụng trong ngữ cảnh: $resolvedQuote."
                        },
                        codeExample = body.codeExample,
                        simpleFollowup = body.simpleFollowup,
                        pitfallFollowup = body.pitfallFollowup,
                        trustedSources = body.trustedSources.map { it.toUiModel() },
                    )
                } else {
                    val matchedCourseEntry = _dictionaryEntries.value.find {
                        it.canonicalNameEn.equals(cleanTerm, ignoreCase = true) ||
                            it.canonicalNameVi.equals(cleanTerm, ignoreCase = true)
                    }
                    val fallbackText = existingSaved?.summary
                        ?: matchedCourseEntry?.aiDeepExplanation?.takeIf { it.isNotBlank() }
                        ?: "Máy chủ phản hồi mã ${res.code()}. Ngữ cảnh gốc: \"$resolvedQuote\"."
                    _activeLivingLookup.value = _activeLivingLookup.value?.copy(
                        aiExplanation = fallbackText,
                        simpleFollowup = existingSaved?.simpleFollowup ?: matchedCourseEntry?.intuitionExample,
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "lookupLivingTerm error: ${e.message}")
                val matchedCourseEntry = _dictionaryEntries.value.find {
                    it.canonicalNameEn.equals(cleanTerm, ignoreCase = true) ||
                        it.canonicalNameVi.equals(cleanTerm, ignoreCase = true)
                }
                val fallbackText = existingSaved?.summary
                    ?: matchedCourseEntry?.aiDeepExplanation?.takeIf { it.isNotBlank() }
                    ?: "Không thể kết nối AI Gateway (${e.localizedMessage ?: "ngoại tuyến"}). Ngữ cảnh bạn vừa chọn: \"$resolvedQuote\"."
                _activeLivingLookup.value = _activeLivingLookup.value?.copy(
                    aiExplanation = fallbackText,
                    simpleFollowup = existingSaved?.simpleFollowup ?: matchedCourseEntry?.intuitionExample,
                )
            } finally {
                _isLookingUpLivingTerm.value = false
            }
        }
    }

    fun saveLivingLookupToVault(result: LivingLookupResult) {
        if (result.term.isBlank()) return
        viewModelScope.launch {
            val activeCourseId = _selectedCourseId.value
            val isExternal = result.originLabel == "Tra cứu ngoài ứng dụng"
            val reqCourseId = if (isExternal) null else activeCourseId.takeIf { it > 0L }
            try {
                val req = SaveLivingEntryRequestDto(
                    courseId = reqCourseId,
                    lectureId = result.lectureId?.takeIf { it > 0L },
                    slideNumber = result.slideNumber,
                    originLabel = result.originLabel.ifBlank { "Từ điển sống" },
                    term = result.term,
                    contextQuote = result.contextQuote,
                    aiExplanation = result.aiExplanation,
                    codeExample = result.codeExample,
                    simpleFollowup = result.simpleFollowup,
                    pitfallFollowup = result.pitfallFollowup,
                    trustedSources = result.trustedSources.map { it.toDto() },
                )
                val res = apiService.saveLivingDictionaryEntry(req)
                if (res.isSuccessful && res.body() != null) {
                    val savedUi = res.body()!!.toUiModel()
                    val updatedList = listOf(savedUi) + _savedLivingEntries.value.filterNot {
                        it.id == savedUi.id || it.term.equals(savedUi.term, ignoreCase = true)
                    }
                    _savedLivingEntries.value = updatedList
                    _activeLivingLookup.value = _activeLivingLookup.value?.let { current ->
                        if (current.term.equals(savedUi.term, ignoreCase = true)) {
                            current.copy(id = savedUi.id)
                        } else current
                    }
                    saveLivingDictionaryCache(activeCourseId, updatedList)
                    appContext?.let { ctx ->
                        if (!isExternal) {
                            Toast.makeText(
                                ctx,
                                "Đã lưu \"${savedUi.term}\" vào Kho Từ điển sống",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                    return@launch
                }
            } catch (e: Exception) {
                Log.w(TAG, "saveLivingLookupToVault error: ${e.message}")
            }

            // Fallback lưu cục bộ khi ngoại tuyến
            val localId = result.id ?: -System.currentTimeMillis()
            val localEntry = SavedLivingEntry(
                id = localId,
                term = result.term,
                originLabel = result.originLabel.ifBlank { "Từ điển sống" },
                lectureId = result.lectureId,
                slideNumber = result.slideNumber,
                contextQuote = result.contextQuote,
                summary = result.aiExplanation,
                isMastered = false,
                isNew = true,
                codeExample = result.codeExample,
                simpleFollowup = result.simpleFollowup,
                pitfallFollowup = result.pitfallFollowup,
                trustedSources = result.trustedSources,
            )
            val updatedList = listOf(localEntry) + _savedLivingEntries.value.filterNot {
                it.term.equals(localEntry.term, ignoreCase = true)
            }
            _savedLivingEntries.value = updatedList
            _activeLivingLookup.value = _activeLivingLookup.value?.copy(id = localId)
            saveLivingDictionaryCache(activeCourseId, updatedList)
        }
    }

    fun toggleSavedLivingMastery(entryId: Long) {
        val activeCourseId = _selectedCourseId.value
        val updated = _savedLivingEntries.value.map { item ->
            if (item.id == entryId) {
                item.copy(isMastered = !item.isMastered, isNew = false)
            } else item
        }
        _savedLivingEntries.value = updated
        saveLivingDictionaryCache(activeCourseId, updated)

        if (entryId <= 0L) return
        viewModelScope.launch {
            try {
                val res = apiService.toggleLivingEntryMastery(entryId)
                if (res.isSuccessful && res.body() != null) {
                    val body = res.body()!!
                    val synced = _savedLivingEntries.value.map { item ->
                        if (item.id == entryId) {
                            item.copy(isMastered = body.isMastered, isNew = body.isNew)
                        } else item
                    }
                    _savedLivingEntries.value = synced
                    saveLivingDictionaryCache(activeCourseId, synced)
                }
            } catch (e: Exception) {
                Log.w(TAG, "toggleSavedLivingMastery error: ${e.message}")
            }
        }
    }

    fun deleteSavedLivingEntry(entryId: Long) {
        val activeCourseId = _selectedCourseId.value
        val remaining = _savedLivingEntries.value.filterNot { it.id == entryId }
        _savedLivingEntries.value = remaining
        saveLivingDictionaryCache(activeCourseId, remaining)

        if (_activeLivingLookup.value?.id == entryId) {
            _activeLivingLookup.value = _activeLivingLookup.value?.copy(id = null)
        }

        if (entryId <= 0L) return
        viewModelScope.launch {
            try {
                apiService.deleteLivingEntry(entryId)
            } catch (e: Exception) {
                Log.w(TAG, "deleteSavedLivingEntry error: ${e.message}")
            }
        }
    }
}
