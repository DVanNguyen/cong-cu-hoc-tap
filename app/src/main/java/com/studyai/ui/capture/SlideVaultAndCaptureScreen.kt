package com.studyai.ui.capture

import android.Manifest
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.studyai.data.model.VoiceNotesTestResponse
import com.studyai.data.model.VoiceprintStatusResponse
import com.studyai.audio.AudioLectureRecorder
import com.studyai.ui.notes.AnnotatedSlidePageUi
import com.studyai.ui.notes.InlineSlideNoteDocumentScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

data class VaultSlideFileUi(
    val id: String,
    val fileName: String,
    val pageCount: Int,
    val uri: Uri? = null,
)

enum class CaptureTabMode {
    LIVE_FOLLOW_SLIDE,
    BATCH_UPLOAD_PAIR,
}

@Composable
fun SlideVaultAndCaptureScreen(
    courseName: String,
    slideVault: List<VaultSlideFileUi>,
    liveAnnotatedPages: List<AnnotatedSlidePageUi>,
    activeSpokenBlockId: String? = null,
    isRecording: Boolean,
    recordingElapsedLabel: String = "00:00",
    isProcessingQueue: Boolean = false,
    uploadProgress: Int = 0,
    uploadStatusText: String = "",
    onUploadSlideToVault: (Uri) -> Unit,
    onStartLiveFollow: (VaultSlideFileUi) -> Unit,
    onStopLiveFollow: () -> Unit,
    onMarkCurrentBlockConfused: () -> Unit,
    onToggleConfusedBlock: (String) -> Unit,
    onAnalyzeUploadedPair: (slideUri: Uri, audioUri: Uri) -> Unit,
    voiceTestResult: VoiceNotesTestResponse? = null,
    isVoiceTesting: Boolean = false,
    onTestVoiceNotes: ((Uri) -> Unit)? = null,
    onDismissVoiceTestResult: (() -> Unit)? = null,
    voiceprintStatus: VoiceprintStatusResponse? = null,
    isEnrollingVoice: Boolean = false,
    onEnrollVoiceprint: ((Uri) -> Unit)? = null,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var mode by remember { mutableStateOf(CaptureTabMode.LIVE_FOLLOW_SLIDE) }
    var selectedVaultSlideId by remember { mutableStateOf(slideVault.firstOrNull()?.id) }
    var batchSlideUri by remember { mutableStateOf<Uri?>(null) }
    var batchAudioUri by remember { mutableStateOf<Uri?>(null) }

    // State ghi âm micro trực tiếp cho chế độ Batch Upload
    var isDirectMicRecording by remember { mutableStateOf(false) }
    var directMicElapsedSec by remember { mutableStateOf(0) }
    var directAudioFile by remember { mutableStateOf<File?>(null) }
    val directRecorder = remember { AudioLectureRecorder(context) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            try {
                directRecorder.start("lecture_mic")
                isDirectMicRecording = true
                directMicElapsedSec = 0
            } catch (e: Exception) {
                // handle error
            }
        }
    }

    LaunchedEffect(isDirectMicRecording) {
        if (isDirectMicRecording) {
            while (isDirectMicRecording) {
                delay(1000)
                directMicElapsedSec++
            }
        }
    }

    val vaultSlidePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) onUploadSlideToVault(uri)
    }

    val batchSlidePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) batchSlideUri = uri
    }

    val batchAudioPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            batchAudioUri = uri
            directAudioFile = null
        }
    }

    val voiceSamplePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            onEnrollVoiceprint?.invoke(uri)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Nếu đang thu âm trực tiếp bám Slide: hiển thị ngay Vở ghi chú Full Slide + thanh điều khiển thu âm
        if (mode == CaptureTabMode.LIVE_FOLLOW_SLIDE && isRecording) {
            LiveRecordingControlBar(
                elapsedLabel = recordingElapsedLabel,
                onMarkConfused = onMarkCurrentBlockConfused,
                onStop = onStopLiveFollow,
            )
            Box(modifier = Modifier.weight(1f)) {
                InlineSlideNoteDocumentScreen(
                    pages = liveAnnotatedPages,
                    activeSpokenBlockId = activeSpokenBlockId,
                    onToggleConfusedBlock = onToggleConfusedBlock,
                )
            }
            return@Column
        }

        // Bộ chuyển 2 chế độ
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Row(modifier = Modifier.padding(4.dp)) {
                ModeSegmentButton(
                    text = "Bám Slide trực tiếp",
                    selected = mode == CaptureTabMode.LIVE_FOLLOW_SLIDE,
                    onClick = { mode = CaptureTabMode.LIVE_FOLLOW_SLIDE },
                    modifier = Modifier.weight(1f),
                )
                ModeSegmentButton(
                    text = "Tải Slide + Audio",
                    selected = mode == CaptureTabMode.BATCH_UPLOAD_PAIR,
                    onClick = { mode = CaptureTabMode.BATCH_UPLOAD_PAIR },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // Nếu đang có tiến trình xử lý hàng đợi (Processing Queue)
        if (isProcessingQueue) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Đang xử lý bài giảng qua AI...",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "$uploadProgress%",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { uploadProgress / 100f },
                        modifier = Modifier.fillMaxWidth().height(8.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = uploadStatusText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                }
            }
        }

        if (mode == CaptureTabMode.LIVE_FOLLOW_SLIDE) {
            // KHO CHỨA SLIDE CỦA MÔN
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Kho Slide • $courseName",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "Tải slide lên trước, vào lớp chỉ cần chọn và bấm Thu âm",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    FilledTonalButton(
                        onClick = {
                            vaultSlidePicker.launch(
                                arrayOf(
                                    "application/pdf",
                                    "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                                )
                            )
                        },
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Thêm Slide")
                    }
                }

                // THẺ TRẠNG THÁI & ĐĂNG KÝ MẪU GIỌNG GIẢNG VIÊN (TUẦN 2)
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (voiceprintStatus?.hasVoiceprint == true) {
                            Color(0xFFE8F5E9)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        }
                    ),
                    border = BorderStroke(
                        width = 1.dp,
                        color = if (voiceprintStatus?.hasVoiceprint == true) {
                            Color(0xFF81C784)
                        } else {
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                        }
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = if (voiceprintStatus?.hasVoiceprint == true) Icons.Default.CheckCircle else Icons.Default.Mic,
                                contentDescription = null,
                                tint = if (voiceprintStatus?.hasVoiceprint == true) Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = if (voiceprintStatus?.hasVoiceprint == true) "Đã lưu giọng giảng viên (Sẵn sàng lọc tiếng ồn)" else "Chưa đăng ký giọng giảng viên",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (voiceprintStatus?.hasVoiceprint == true) Color(0xFF1B5E20) else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (voiceprintStatus?.hasVoiceprint == true) "Vector 192d • Ngưỡng lọc ồn ${voiceprintStatus.cosineThreshold}" else "Tải file mẫu giọng sạch (15-30s) để lọc tiếng sinh viên bàn tán",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        FilledTonalButton(
                            onClick = {
                                voiceSamplePicker.launch(arrayOf("audio/*"))
                            },
                            enabled = !isEnrollingVoice,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.padding(start = 8.dp)
                        ) {
                            if (isEnrollingVoice) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            } else {
                                Text(if (voiceprintStatus?.hasVoiceprint == true) "Cập nhật" else "Đăng ký (20s)")
                            }
                        }
                    }
                }

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(slideVault, key = { it.id }) { file ->
                        val selected = file.id == selectedVaultSlideId
                        Surface(
                            onClick = { selectedVaultSlideId = file.id },
                            shape = RoundedCornerShape(14.dp),
                            color = if (selected) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surface
                            },
                            border = BorderStroke(
                                width = if (selected) 1.5.dp else 1.dp,
                                color = if (selected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                                },
                            ),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Description,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                                Spacer(Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = file.fileName,
                                        style = MaterialTheme.typography.titleMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = "${file.pageCount} trang slide • Sẵn sàng bám theo lời giảng",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }

                val activeFile = slideVault.find { it.id == selectedVaultSlideId }
                Button(
                    onClick = { activeFile?.let(onStartLiveFollow) },
                    enabled = activeFile != null && !isProcessingQueue,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                        .height(50.dp),
                ) {
                    Icon(Icons.Default.Mic, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Bắt đầu thu âm bám theo Slide đã chọn")
                }
            }
        } else {
            // CHẾ ĐỘ TẢI SLIDE + FILE GHI ÂM CÓ SẴN (HOẶC THU ÂM TRỰC TIẾP TẠO FILE)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "Phân tích bộ Slide & File ghi âm thật (End-to-End)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Chọn tệp PDF và chọn tệp âm thanh (hoặc bấm nút Ghi âm Micro để thu trực tiếp bài giảng).",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                FilePickerCard(
                    title = "1. Chọn file Slide (.pdf / .pptx)",
                    selectedLabel = batchSlideUri?.lastPathSegment ?: "Chưa chọn file slide",
                    icon = Icons.Default.Description,
                    onClick = {
                        batchSlidePicker.launch(
                            arrayOf(
                                "application/pdf",
                                "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                            )
                        )
                    },
                )

                FilePickerCard(
                    title = "2. Chọn file Ghi âm có sẵn (.mp3, .m4a, .wav)",
                    selectedLabel = if (batchAudioUri != null) {
                        batchAudioUri?.lastPathSegment ?: "Đã chọn file audio"
                    } else if (directAudioFile != null) {
                        "Đã thu từ Micro: ${directAudioFile?.name}"
                    } else {
                        "Chưa có tệp âm thanh"
                    },
                    icon = Icons.Default.AudioFile,
                    onClick = {
                        batchAudioPicker.launch(arrayOf("audio/*", "video/mp4"))
                    },
                )

                // Hoặc ghi âm Micro trực tiếp
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (isDirectMicRecording) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isDirectMicRecording) "Đang ghi âm Micro: ${String.format("%02d:%02d", directMicElapsedSec / 60, directMicElapsedSec % 60)}" else "Hoặc thu trực tiếp bằng Micro",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isDirectMicRecording) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "16kHz Mono AAC 64kbps chuẩn cho Whisper ASR",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isDirectMicRecording) MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }

                        if (!isDirectMicRecording) {
                            FilledTonalButton(
                                onClick = {
                                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                },
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Ghi âm")
                            }
                        } else {
                            Button(
                                onClick = {
                                    val f = directRecorder.stop()
                                    isDirectMicRecording = false
                                    if (f != null) {
                                        directAudioFile = f
                                        batchAudioUri = Uri.fromFile(f)
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Dừng")
                            }
                        }
                    }
                }

                // Nút kiểm thử nhanh: Chỉ ghi chú từ Voice
                OutlinedButton(
                    onClick = {
                        batchAudioUri?.let { onTestVoiceNotes?.invoke(it) }
                    },
                    enabled = batchAudioUri != null && !isVoiceTesting && !isProcessingQueue && !isDirectMicRecording,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    if (isVoiceTesting) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("AI đang nghe & tạo ghi chú...")
                    } else {
                        Icon(Icons.Default.GraphicEq, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("⚡ Thử nghiệm: Chỉ ghi chú từ Voice (Không cần Slide)")
                    }
                }

                Spacer(Modifier.weight(1f))

                val isReadyToProcess = (batchSlideUri != null) && (batchAudioUri != null) && !isProcessingQueue && !isDirectMicRecording

                Button(
                    onClick = {
                        val s = batchSlideUri
                        val a = batchAudioUri
                        if (s != null && a != null) {
                            onAnalyzeUploadedPair(s, a)
                        }
                    },
                    enabled = isReadyToProcess,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                        .height(50.dp),
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (isProcessingQueue) "Đang phân tích qua hàng đợi..." else "Tải lên & Phân tích qua Hàng đợi AI")
                }
            }
        }
    }

    if (voiceTestResult != null) {
        val clipboardManager = LocalClipboardManager.current
        AlertDialog(
            onDismissRequest = { onDismissVoiceTestResult?.invoke() },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.GraphicEq, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = voiceTestResult.title.ifBlank { "Vở ghi chép từ Voice" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = "📌 Tóm tắt tổng quan (${voiceTestResult.durationSec.toInt()}s audio)",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = voiceTestResult.summary,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }

                    if (voiceTestResult.keyPoints.isNotEmpty()) {
                        Text(
                            text = "💡 Các ý chính & Bài học cốt lõi:",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        voiceTestResult.keyPoints.forEachIndexed { idx, kp ->
                            Card(
                                shape = RoundedCornerShape(8.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Text(
                                        text = "${idx + 1}. [${kp.timestampLabel}] ${kp.headline}",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Text(text = kp.content, style = MaterialTheme.typography.bodySmall)
                                    if (kp.evidenceQuote.isNotBlank()) {
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            text = "🔊 \"${kp.evidenceQuote}\"",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (voiceTestResult.technicalTerms.isNotEmpty()) {
                        Text(
                            text = "📖 Thuật ngữ chuyên ngành:",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        voiceTestResult.technicalTerms.forEach { t ->
                            Text(
                                text = "• ${t.term}: ${t.explanation}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }

                    if (voiceTestResult.actionItemsOrTips.isNotEmpty()) {
                        Text(
                            text = "⚠️ Dặn dò & Việc cần làm:",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        voiceTestResult.actionItemsOrTips.forEach { a ->
                            Text(text = "• $a", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { onDismissVoiceTestResult?.invoke() }) {
                    Text("Đóng")
                }
            },
            dismissButton = {
                if (voiceTestResult.markdownNotes.isNotBlank()) {
                    TextButton(onClick = {
                        clipboardManager.setText(AnnotatedString(voiceTestResult.markdownNotes))
                    }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Sao chép Markdown")
                    }
                }
            }
        )
    }
}

@Composable
private fun LiveRecordingControlBar(
    elapsedLabel: String,
    onMarkConfused: () -> Unit,
    onStop: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.errorContainer,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(MaterialTheme.colorScheme.error, RoundedCornerShape(5.dp))
                    )
                    Text(
                        text = elapsedLabel,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }

            OutlinedButton(
                onClick = onMarkConfused,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
            ) {
                Icon(Icons.Default.Flag, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Chưa hiểu đoạn này", style = MaterialTheme.typography.labelMedium)
            }

            Button(
                onClick = onStop,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                shape = RoundedCornerShape(12.dp),
            ) {
                Icon(Icons.Default.Stop, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("Dừng")
            }
        }
    }
}

@Composable
private fun ModeSegmentButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (selected) MaterialTheme.colorScheme.surface else Color.Transparent,
        border = if (selected) BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)) else null,
        modifier = modifier.height(38.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = text,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun FilePickerCard(
    title: String,
    selectedLabel: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = selectedLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = "Chọn",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
