package com.studyai.viewmodel

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.studyai.audio.ChunkedAudioRecorder
import com.studyai.data.api.StudyAiApiService
import com.studyai.data.model.*
import com.studyai.ui.LectureLanguageMode
import com.studyai.ui.capture.VaultSlideFileUi
import com.studyai.ui.course.CourseUiItem
import com.studyai.ui.dictionary.CourseDictionaryItemUi
import com.studyai.ui.dictionary.CrossChapterLinkUiModel
import com.studyai.ui.dictionary.ExternalReferenceUiModel
import com.studyai.ui.dictionary.LivingDictionaryUiModel
import com.studyai.ui.graph.ConceptEdgeUi
import com.studyai.ui.graph.ConceptNodeUi
import com.studyai.ui.notes.*
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

class StudyAiViewModel(
    private val apiService: StudyAiApiService
) : ViewModel() {

    companion object {
        private const val TAG = "StudyAiViewModel"
    }

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

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _recordingElapsedSec = MutableStateFlow(0)
    val recordingElapsedSec: StateFlow<Int> = _recordingElapsedSec.asStateFlow()

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
        loadAllData(courseId = courseId)
        loadSlideVault(courseId = courseId)
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
                }
            } catch (e: Exception) {
                _errorMessage.value = "Lỗi khi upload slide vào kho: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    // =========================================================================
    // HÀM XỬ LÝ VỞ GHI CHÉP INLINE & LIVE RECORDING
    // =========================================================================
    fun loadLectureInlineNotes(lectureId: Long) {
        viewModelScope.launch {
            try {
                val res = apiService.getLectureInlineNotes(lectureId)
                if (res.isSuccessful && res.body() != null) {
                    val body = res.body()!!
                    _inlinePages.value = mapDtoToAnnotatedPages(body.pages)
                    _generalNotes.value = body.generalNotes ?: emptyList()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Lỗi loadLectureInlineNotes: ${e.message}")
            }
        }
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
            toggleConfusedBlock(targetBlockId)
        }
    }

    fun startLiveFollowRecording(context: Context, vaultSlide: VaultSlideFileUi) {
        if (_isRecording.value) return

        viewModelScope.launch {
            _isRecording.value = true
            _recordingElapsedSec.value = 0

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

            // 2. Chạy Timer hiển thị giây
            timerJob = launch {
                while (isActive && _isRecording.value) {
                    delay(1000L)
                    _recordingElapsedSec.value += 1
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
        viewModelScope.launch {
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
                    _activeSpokenBlockId.value = ev.matchedBlockId

                    // Cập nhật trạng thái đang giảng và thêm note vào đúng block
                    _inlinePages.value = _inlinePages.value.map { page ->
                        page.copy(
                            blocks = page.blocks.map { b ->
                                val isNowSpoken = (b.blockId == ev.matchedBlockId)
                                var updatedNotes = b.inlineNotes
                                if (isNowSpoken && ev.inlineNote != null && ev.action == "attach_inline_note") {
                                    val newNote = InlineLecturerNote(
                                        id = "live_note_${System.currentTimeMillis()}",
                                        explanationText = ev.inlineNote.explanationText,
                                        timestampSec = ev.inlineNote.timestampStartSec,
                                        timestampLabel = formatSeconds(ev.inlineNote.timestampStartSec)
                                    )
                                    updatedNotes = updatedNotes + newNote
                                }
                                var updatedHighlights = b.highlights
                                if (isNowSpoken && !ev.newHighlights.isNullOrEmpty()) {
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
                // Xóa file chunk sau khi upload
                try {
                    if (chunkFile.exists()) chunkFile.delete()
                } catch (_: Exception) {}
            }
        }
    }

    fun stopLiveFollowRecording() {
        _isRecording.value = false
        timerJob?.cancel()
        timerJob = null
        audioRecorder?.stopRecording { finalFile, startSec ->
            uploadLiveChunk(finalFile, startSec)
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
                    _uploadProgress.value = res.body()!!.progressPercent
                    _uploadStatusText.value = "Đã xếp hàng xử lý (ID: $lectureId)..."

                    // Bắt đầu Polling tiến độ mỗi 2 giây
                    var isDone = false
                    while (!isDone && isActive) {
                        delay(2000)
                        val statusRes = apiService.getLectureStatus(lectureId)
                        if (statusRes.isSuccessful && statusRes.body() != null) {
                            val st = statusRes.body()!!
                            _uploadProgress.value = st.progressPercent
                            _uploadStatusText.value = when (st.processingStatus) {
                                "queued" -> "Đang xếp hàng chờ xử lý..."
                                "parsing_slides" -> "Đang phân tích và trích xuất nội dung Slide (15%)..."
                                "running_asr" -> "Đang nhận dạng giọng nói ASR & VAD (45%)..."
                                "aligning_slides" -> "Đang căn gióng lời giảng vào từng trang Slide (75%)..."
                                "synthesizing_notes" -> "Đang dùng LLM tổng hợp ghi chú thông minh (90%)..."
                                "completed" -> "Hoàn tất xử lý bài giảng (100%)!"
                                "failed" -> "Xử lý thất bại: ${st.errorMessage ?: "Lỗi không xác định"}"
                                else -> "Đang xử lý: ${st.processingStatus} (${st.progressPercent}%)"
                            }

                            if (st.processingStatus == "completed") {
                                isDone = true
                                _currentLectureId.value = lectureId
                                _currentLectureTitle.value = lectureTitle
                                // Tải lại toàn bộ dữ liệu bài giảng
                                loadAllData(_selectedCourseId.value, lectureId)
                                onDone(lectureId)
                            } else if (st.processingStatus == "failed") {
                                isDone = true
                                _errorMessage.value = st.errorMessage ?: "Xử lý bài giảng thất bại."
                            }
                        }
                    }
                } else {
                    _errorMessage.value = "Tải bài giảng lên server thất bại."
                }
            } catch (e: Exception) {
                _errorMessage.value = "Lỗi upload bài giảng: ${e.message}"
            } finally {
                _isProcessingQueue.value = false
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
    private fun mapDtoToAnnotatedPages(dtoPages: List<AnnotatedSlidePageDto>): List<AnnotatedSlidePageUi> {
        return dtoPages.map { p ->
            AnnotatedSlidePageUi(
                pageNumber = p.pageNumber,
                slideTitle = p.slideTitle,
                blocks = p.blocks.map { b ->
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
                                timestampLabel = n.timestampLabel.ifEmpty { formatSeconds(n.timestampSec) }
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

    fun loadAllData(courseId: Long = 1L, lectureId: Long = 1L) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                loadCourses()
                loadSlideVault(courseId)
                loadLectureInlineNotes(lectureId)

                // 0. Tải danh mục Bài giảng
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
                } catch (_: Exception) {}

                // 1. Tải workspace cũ
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
            } catch (e: Exception) {
                Log.e(TAG, "loadAllData error", e)
                _errorMessage.value = "Lỗi kết nối máy chủ StudyAI: ${e.localizedMessage ?: e.message ?: e.javaClass.simpleName}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun switchCourse(courseId: Long) {
        val initialLectureId = if (courseId == 3L) 8L else 1L
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
}
