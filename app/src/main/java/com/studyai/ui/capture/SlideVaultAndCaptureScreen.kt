package com.studyai.ui.capture

import android.Manifest
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Summarize
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.studyai.audio.AudioLectureRecorder
import com.studyai.data.model.VoiceNotesTestResponse
import com.studyai.data.model.VoiceprintStatusResponse
import com.studyai.ui.notes.AnnotatedSlidePageUi
import com.studyai.ui.notes.InlineSlideNoteDocumentScreen
import com.studyai.ui.theme.StudyAiExtras
import com.studyai.ui.theme.StudyAiTimestampTextStyle
import kotlinx.coroutines.delay
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
    val extras = StudyAiExtras.colors

    var mode by remember { mutableStateOf(CaptureTabMode.LIVE_FOLLOW_SLIDE) }
    var selectedVaultSlideId by remember { mutableStateOf(slideVault.firstOrNull()?.id) }
    LaunchedEffect(slideVault) {
        if (selectedVaultSlideId == null || slideVault.none { it.id == selectedVaultSlideId }) {
            selectedVaultSlideId = slideVault.firstOrNull()?.id
        }
    }

    var batchSlideUri by remember { mutableStateOf<Uri?>(null) }
    var batchAudioUri by remember { mutableStateOf<Uri?>(null) }

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
            } catch (_: Exception) {
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

        // Bộ chuyển 2 chế độ: Bám Slide trực tiếp / Tải Slide + Audio
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
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

        // Tiến trình xử lý Queue AI bất đồng bộ
        if (isProcessingQueue) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
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
                            style = StudyAiTimestampTextStyle,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { uploadProgress / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = uploadStatusText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                    )
                }
            }
        }

        if (mode == CaptureTabMode.LIVE_FOLLOW_SLIDE) {
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
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "Chọn tài liệu trên lớp và bấm Thu âm để bám slide theo thời gian thực",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
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

                // Thẻ trạng thái & đăng ký mẫu giọng giảng viên đồng bộ Light & Dark Mode
                val hasVp = voiceprintStatus?.hasVoiceprint == true
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (hasVp) extras.successBg else MaterialTheme.colorScheme.surface,
                    border = BorderStroke(
                        width = 1.dp,
                        color = if (hasVp) extras.successBorder else MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
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
                                imageVector = if (hasVp) Icons.Default.VerifiedUser else Icons.Default.RecordVoiceOver,
                                contentDescription = null,
                                tint = if (hasVp) extras.successFg else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = if (hasVp) "Đã lưu giọng giảng viên (Sẵn sàng lọc nhiễu)" else "Chưa đăng ký giọng giảng viên",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (hasVp) extras.successFg else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (hasVp) {
                                        "ECAPA 192-d • Ngưỡng lọc ${voiceprintStatus?.cosineThreshold ?: 0.35f}"
                                    } else {
                                        "Tải 15–30s mẫu giọng sạch để chặn tiếng sinh viên bàn tán"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (hasVp) extras.successFg.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        FilledTonalButton(
                            onClick = { voiceSamplePicker.launch(arrayOf("audio/*")) },
                            enabled = !isEnrollingVoice,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.padding(start = 8.dp)
                        ) {
                            if (isEnrollingVoice) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            } else {
                                Text(if (hasVp) "Cập nhật" else "Đăng ký", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }

                if (slideVault.isEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Description,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(30.dp),
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "Kho Slide chưa có tệp nào",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "Bấm \"Thêm Slide\" ở góc trên để nạp PDF hoặc PPTX trước giờ học.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                } else {
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
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                                } else {
                                    MaterialTheme.colorScheme.surface
                                },
                                border = BorderStroke(
                                    width = if (selected) 1.5.dp else 1.dp,
                                    color = if (selected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
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
                                        tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = file.fileName,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            text = "${file.pageCount} trang slide • Sẵn sàng bám theo lời giảng",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    if (selected) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = "Đã chọn",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                val activeFile = slideVault.find { it.id == selectedVaultSlideId }
                Button(
                    onClick = { activeFile?.let(onStartLiveFollow) },
                    enabled = activeFile != null && !isProcessingQueue,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 14.dp)
                        .height(50.dp),
                ) {
                    Icon(Icons.Default.Mic, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Bắt đầu thu âm bám theo Slide đã chọn", style = MaterialTheme.typography.labelLarge)
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "Phân tích bộ Slide & File ghi âm",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "Chọn tệp PDF/PPTX và tệp ghi âm (hoặc thu âm trực tiếp bằng micro) để AI căn gióng từng block và tổng hợp vở ghi chép.",
                    style = MaterialTheme.typography.bodySmall,
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
                    title = "2. Chọn file Ghi âm (.mp3, .m4a, .wav)",
                    selectedLabel = if (batchAudioUri != null) {
                        batchAudioUri?.lastPathSegment ?: "Đã chọn file audio"
                    } else if (directAudioFile != null) {
                        "Đã thu từ Micro: ${directAudioFile?.name}"
                    } else {
                        "Chưa có tệp âm thanh"
                    },
                    icon = Icons.Default.AudioFile,
                    onClick = { batchAudioPicker.launch(arrayOf("audio/*", "video/mp4")) },
                )

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (isDirectMicRecording) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surface,
                    border = BorderStroke(
                        width = 1.dp,
                        color = if (isDirectMicRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .padding(14.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isDirectMicRecording) {
                                    "Đang ghi âm Micro: %02d:%02d".format(directMicElapsedSec / 60, directMicElapsedSec % 60)
                                } else {
                                    "Hoặc thu trực tiếp bằng Micro"
                                },
                                style = MaterialTheme.typography.titleSmall,
                                color = if (isDirectMicRecording) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Chuẩn 16kHz Mono AAC 64kbps cho Whisper ASR",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isDirectMicRecording) MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (!isDirectMicRecording) {
                            FilledTonalButton(
                                onClick = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
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

                OutlinedButton(
                    onClick = { batchAudioUri?.let { onTestVoiceNotes?.invoke(it) } },
                    enabled = batchAudioUri != null && !isVoiceTesting && !isProcessingQueue && !isDirectMicRecording,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                ) {
                    if (isVoiceTesting) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("AI đang nghe và tổng hợp ghi chú...")
                    } else {
                        Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Thử nhanh: Tạo ghi chú từ Voice (Không cần Slide)")
                    }
                }

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
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, bottom = 18.dp)
                        .height(50.dp),
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = if (isProcessingQueue) "Đang phân tích qua hàng đợi..." else "Tải lên & Phân tích bài giảng",
                        style = MaterialTheme.typography.labelLarge,
                    )
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
                        style = MaterialTheme.typography.titleLarge,
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
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(
                                    imageVector = Icons.Default.Summarize,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp),
                                )
                                Text(
                                    text = "Tóm tắt tổng quan (${voiceTestResult.durationSec.toInt()}s)",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = voiceTestResult.summary,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }

                    if (voiceTestResult.keyPoints.isNotEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(
                                imageVector = Icons.Default.Lightbulb,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(16.dp),
                            )
                            Text(
                                text = "Ý chính & Nội dung cốt lõi",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        voiceTestResult.keyPoints.forEachIndexed { idx, kp ->
                            OutlinedCard(
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(
                                        text = "${idx + 1}. [${kp.timestampLabel}] ${kp.headline}",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(Modifier.height(3.dp))
                                    Text(text = kp.content, style = MaterialTheme.typography.bodySmall)
                                    if (kp.evidenceQuote.isNotBlank()) {
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            text = "\"${kp.evidenceQuote}\"",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (voiceTestResult.technicalTerms.isNotEmpty()) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.MenuBook,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp),
                            )
                            Text(
                                text = "Thuật ngữ chuyên ngành",
                                style = MaterialTheme.typography.titleSmall,
                            )
                        }
                        voiceTestResult.technicalTerms.forEach { t ->
                            Text(
                                text = "• ${t.term}: ${t.explanation}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }

                    if (voiceTestResult.actionItemsOrTips.isNotEmpty()) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(
                                imageVector = Icons.Default.WarningAmber,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(16.dp),
                            )
                            Text(
                                text = "Dặn dò & Lưu ý quan trọng",
                                style = MaterialTheme.typography.titleSmall,
                            )
                        }
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
private fun LiveWaveformBars(
    color: Color,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "live_wave")
    val bars = listOf(0, 120, 240, 160, 80)
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        bars.forEachIndexed { idx, delayMs ->
            val factor by transition.animateFloat(
                initialValue = 0.35f,
                targetValue = 1.0f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 480 + idx * 60, delayMillis = delayMs, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "wave_bar_$idx",
            )
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height((18f * factor).dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(color)
            )
        }
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
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        LiveWaveformBars(color = MaterialTheme.colorScheme.error)
                        Text(
                            text = elapsedLabel,
                            style = StudyAiTimestampTextStyle,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }

                OutlinedButton(
                    onClick = onMarkConfused,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                ) {
                    Icon(Icons.Default.Flag, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Chưa hiểu", style = MaterialTheme.typography.labelMedium, maxLines = 1)
                }

                Button(
                    onClick = onStop,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Dừng", style = MaterialTheme.typography.labelMedium)
                }
            }
            HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
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
        shape = RoundedCornerShape(10.dp),
        color = if (selected) MaterialTheme.colorScheme.surface else Color.Transparent,
        border = if (selected) BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)) else null,
        modifier = modifier.height(38.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun FilePickerCard(
    title: String,
    selectedLabel: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(38.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
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
