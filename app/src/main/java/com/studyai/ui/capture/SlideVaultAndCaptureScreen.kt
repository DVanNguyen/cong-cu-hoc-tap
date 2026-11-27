package com.studyai.ui.capture

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Summarize
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.studyai.audio.AudioLectureRecorder
import com.studyai.data.model.VoiceNotesTestResponse
import com.studyai.ui.notes.AnnotatedSlidePageUi
import com.studyai.ui.theme.StudyAiExtras
import com.studyai.ui.theme.StudyAiPalette
import kotlinx.coroutines.delay
import java.io.File
import kotlin.math.abs
import kotlin.math.sin

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

enum class LiveFeedKind {
    LECTURER_NOTE,
    BLOCKED_NON_TEACHER,
    CONFUSED_PIN,
}

data class LiveFeedItemUi(
    val id: String,
    val kind: LiveFeedKind,
    val slideNumber: Int = 1,
    val timestampLabel: String = "00:00",
    val text: String = "",
)

private fun resolveDisplayFileName(context: Context, uri: Uri?): String? {
    if (uri == null) return null
    try {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx != -1) {
                    val name = cursor.getString(idx)
                    if (!name.isNullOrBlank()) return name
                }
            }
        }
    } catch (_: Exception) {
    }
    return uri.lastPathSegment?.substringAfterLast('/')
}

/**
 * Màn hình 2: Thu Âm & Bám Slide theo đặc tả screen2_compose_design_spec.md.
 */
@Composable
fun SlideVaultAndCaptureScreen(
    @Suppress("UNUSED_PARAMETER") courseName: String = "",
    slideVault: List<VaultSlideFileUi>,
    liveAnnotatedPages: List<AnnotatedSlidePageUi>,
    @Suppress("UNUSED_PARAMETER") activeSpokenBlockId: String? = null,
    isRecording: Boolean,
    recordingElapsedLabel: String = "00:00",
    liveAmplitude: Float = 0.06f,
    liveSessionState: String = "ready",
    liveFeedItems: List<LiveFeedItemUi> = emptyList(),
    isProcessingQueue: Boolean = false,
    uploadProgress: Int = 0,
    uploadStatusText: String = "",
    onUploadSlideToVault: (Uri) -> Unit,
    onStartLiveFollow: (VaultSlideFileUi) -> Unit,
    onStopLiveFollow: (refineFullLecture: Boolean) -> Unit,
    onMarkCurrentBlockConfused: () -> Unit,
    @Suppress("UNUSED_PARAMETER") onToggleConfusedBlock: (String) -> Unit = {},
    onAnalyzeUploadedPair: (slideUri: Uri, audioUri: Uri) -> Unit,
    onOpenGeneratedNotes: () -> Unit = {},
    voiceTestResult: VoiceNotesTestResponse? = null,
    isVoiceTesting: Boolean = false,
    onTestVoiceNotes: ((Uri) -> Unit)? = null,
    onDismissVoiceTestResult: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val isDark = StudyAiExtras.isDark
    val surfaceSubtle = MaterialTheme.colorScheme.surfaceVariant
    val primarySoft = if (isDark) StudyAiPalette.PrimarySoftDark else StudyAiPalette.PrimarySoftLight
    val primaryText = if (isDark) StudyAiPalette.PrimaryTextDark else StudyAiPalette.PrimaryTextLight
    val tealSoft = if (isDark) StudyAiPalette.TealSoftDark else StudyAiPalette.TealSoftLight
    val tealText = if (isDark) StudyAiPalette.TealTextDark else StudyAiPalette.TealTextLight
    val roseSoft = if (isDark) StudyAiPalette.RoseSoftDark else StudyAiPalette.RoseSoftLight
    val roseText = if (isDark) StudyAiPalette.RoseTextDark else StudyAiPalette.RoseTextLight

    var mode by remember { mutableStateOf(CaptureTabMode.LIVE_FOLLOW_SLIDE) }
    var selectedVaultSlideId by remember { mutableStateOf(slideVault.firstOrNull()?.id) }
    LaunchedEffect(slideVault) {
        if (selectedVaultSlideId == null || slideVault.none { it.id == selectedVaultSlideId }) {
            selectedVaultSlideId = slideVault.firstOrNull()?.id
        }
    }

    var showFinishLiveSheet by remember { mutableStateOf(false) }
    var pendingSlideToRecord by remember { mutableStateOf<VaultSlideFileUi?>(null) }

    var batchSlideUri by remember { mutableStateOf<Uri?>(null) }
    var batchAudioUri by remember { mutableStateOf<Uri?>(null) }
    var isDirectMicRecording by remember { mutableStateOf(false) }
    var directMicElapsedSec by remember { mutableIntStateOf(0) }
    var directAudioFile by remember { mutableStateOf<File?>(null) }
    val directRecorder = remember { AudioLectureRecorder(context) }

    val liveMicPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            pendingSlideToRecord?.let(onStartLiveFollow)
        }
        pendingSlideToRecord = null
    }

    val batchMicPermissionLauncher = rememberLauncherForActivityResult(
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
                delay(1000L)
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

    val derivedPreviewFeed = remember(liveAnnotatedPages) {
        buildList {
            liveAnnotatedPages.forEach { page ->
                page.blocks.forEach { block ->
                    if (block.isMarkedConfused) {
                        add(
                            LiveFeedItemUi(
                                id = "preview_pin_${block.blockId}",
                                kind = LiveFeedKind.CONFUSED_PIN,
                                slideNumber = page.pageNumber,
                                timestampLabel = page.timeRangeLabel.ifBlank { "--:--" },
                                text = block.originalSlideText,
                            )
                        )
                    }
                    block.inlineNotes.forEach { note ->
                        add(
                            LiveFeedItemUi(
                                id = "preview_note_${note.id}",
                                kind = LiveFeedKind.LECTURER_NOTE,
                                slideNumber = page.pageNumber,
                                timestampLabel = note.timestampLabel,
                                text = note.explanationText,
                            )
                        )
                    }
                }
            }
        }.take(6)
    }

    val activeFeedItems = remember(liveFeedItems, derivedPreviewFeed, isRecording, liveSessionState) {
        if (isRecording || liveSessionState == "finished" || liveFeedItems.isNotEmpty()) {
            liveFeedItems
        } else {
            derivedPreviewFeed
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Hàng 2: Segmented Control 2 chế độ
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = surfaceSubtle,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Row(
                modifier = Modifier.padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                ModeSegmentButton(
                    text = "Bám Slide trực tiếp",
                    selected = mode == CaptureTabMode.LIVE_FOLLOW_SLIDE,
                    primaryTextColor = primaryText,
                    onClick = { mode = CaptureTabMode.LIVE_FOLLOW_SLIDE },
                    modifier = Modifier.weight(1f),
                )
                ModeSegmentButton(
                    text = "Tải Slide + Audio",
                    selected = mode == CaptureTabMode.BATCH_UPLOAD_PAIR,
                    primaryTextColor = primaryText,
                    onClick = { mode = CaptureTabMode.BATCH_UPLOAD_PAIR },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        if (mode == CaptureTabMode.LIVE_FOLLOW_SLIDE) {
            val activeVaultSlide = slideVault.find { it.id == selectedVaultSlideId }
            val startLiveWithPermission = { slide: VaultSlideFileUi ->
                val granted = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED
                if (granted) {
                    onStartLiveFollow(slide)
                } else {
                    pendingSlideToRecord = slide
                    liveMicPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            }

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // 2.1. Kho Slide nạp trước
                item(key = "vault_slide_picker") {
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "1. Chọn Slide đã nạp trước",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                TextButton(
                                    onClick = {
                                        vaultSlidePicker.launch(
                                            arrayOf(
                                                "application/pdf",
                                                "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                                            )
                                        )
                                    },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                ) {
                                    Text(
                                        text = "+ Thêm slide",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = primaryText,
                                    )
                                }
                            }

                            Spacer(Modifier.height(6.dp))

                            if (slideVault.isEmpty()) {
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = surfaceSubtle,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(
                                        text = "Kho Slide chưa có tài liệu nào. Bấm \"+ Thêm slide\" để nạp PDF hoặc PPTX trước buổi học.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(12.dp),
                                    )
                                }
                            } else {
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    items(slideVault, key = { it.id }) { file ->
                                        val isSelected = file.id == selectedVaultSlideId
                                        Surface(
                                            onClick = {
                                                if (!isRecording) {
                                                    selectedVaultSlideId = file.id
                                                }
                                            },
                                            shape = RoundedCornerShape(16.dp),
                                            color = if (isSelected) primarySoft else surfaceSubtle,
                                            border = BorderStroke(
                                                width = 1.dp,
                                                color = if (isSelected) {
                                                    StudyAiPalette.PrimaryCobalt
                                                } else {
                                                    MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)
                                                },
                                            ),
                                            modifier = Modifier.widthIn(min = 148.dp, max = 216.dp),
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                                verticalArrangement = Arrangement.spacedBy(3.dp),
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Description,
                                                        contentDescription = null,
                                                        tint = if (isSelected) primaryText else MaterialTheme.colorScheme.onSurfaceVariant,
                                                        modifier = Modifier.size(16.dp),
                                                    )
                                                    Text(
                                                        text = file.fileName,
                                                        fontSize = 12.5.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = MaterialTheme.colorScheme.onSurface,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                    )
                                                }
                                                Text(
                                                    text = "${file.pageCount} trang slide",
                                                    fontSize = 11.sp,
                                                    color = if (isSelected) primaryText else MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 2.2. Trình thu âm Live
                item(key = "live_recorder_card") {
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(
                            width = 1.dp,
                            color = if (isRecording) StudyAiPalette.Rose.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outline,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "2. Ghi âm bám Slide",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                val (pillBg, pillFg, pillLabel) = when {
                                    isRecording -> Triple(roseSoft, roseText, "Đang lọc giọng thầy")
                                    liveSessionState == "finished" -> Triple(tealSoft, tealText, "Đã chốt & lưu")
                                    else -> Triple(surfaceSubtle, MaterialTheme.colorScheme.onSurfaceVariant, "Sẵn sàng")
                                }
                                Surface(
                                    shape = RoundedCornerShape(999.dp),
                                    color = pillBg,
                                ) {
                                    Text(
                                        text = pillLabel,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = pillFg,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    )
                                }
                            }

                            Spacer(Modifier.height(14.dp))

                            Text(
                                text = recordingElapsedLabel,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 36.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (isRecording) StudyAiPalette.Rose else MaterialTheme.colorScheme.onSurface,
                            )

                            Spacer(Modifier.height(12.dp))

                            Live16BarWaveform(
                                isRecording = isRecording,
                                amplitude = liveAmplitude,
                            )

                            Spacer(Modifier.height(16.dp))

                            Button(
                                onClick = {
                                    if (isRecording) {
                                        showFinishLiveSheet = true
                                    } else {
                                        activeVaultSlide?.let(startLiveWithPermission)
                                    }
                                },
                                enabled = isRecording || (activeVaultSlide != null && !isProcessingQueue),
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isRecording) StudyAiPalette.Rose else StudyAiPalette.PrimaryCobalt,
                                    contentColor = Color.White,
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp),
                            ) {
                                Icon(
                                    imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = if (isRecording) "Dừng & Chốt buổi học" else "Bắt đầu bám Slide trực tiếp",
                                    fontWeight = FontWeight.Bold,
                                )
                            }

                            AnimatedVisibility(visible = isRecording) {
                                OutlinedButton(
                                    onClick = onMarkCurrentBlockConfused,
                                    shape = RoundedCornerShape(16.dp),
                                    border = BorderStroke(1.dp, StudyAiPalette.Rose),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = roseSoft,
                                        contentColor = roseText,
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 8.dp)
                                        .height(44.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Flag,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = "Chưa hiểu đoạn này • Ghim mốc ngay",
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 12.5.sp,
                                    )
                                }
                            }
                        }
                    }
                }

                // Hiển thị tiến trình tinh chỉnh toàn bài bằng AI nếu sinh viên chọn Refine sau khi chốt Live
                if (isProcessingQueue || (liveSessionState == "finished" && uploadProgress >= 100)) {
                    item(key = "live_refinement_progress") {
                        QueueProgressCard(
                            isProcessingQueue = isProcessingQueue,
                            uploadProgress = uploadProgress,
                            uploadStatusText = uploadStatusText,
                            onOpenGeneratedNotes = onOpenGeneratedNotes,
                        )
                    }
                }

                // 2.3. Luồng bám Slide thời gian thực (LiveFeedList)
                item(key = "live_feed_header") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Luồng bám Slide thời gian thực",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "${activeFeedItems.size} mục",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                if (activeFeedItems.isEmpty()) {
                    item(key = "live_feed_empty") {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = "Khi thu âm trực tiếp, từng ý giảng viên nói khớp với Slide, đoạn sinh viên bị chặn và các mốc ghim chưa hiểu sẽ xuất hiện liên tục tại đây.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(16.dp),
                            )
                        }
                    }
                } else {
                    items(activeFeedItems, key = { it.id }) { feedItem ->
                        LiveFeedEventCard(
                            item = feedItem,
                            primarySoft = primarySoft,
                            primaryText = primaryText,
                            roseSoft = roseSoft,
                            roseText = roseText,
                            surfaceSubtle = surfaceSubtle,
                        )
                    }
                }

                item(key = "live_bottom_spacer") {
                    Spacer(Modifier.height(10.dp))
                }
            }
        } else {
            // Chế độ B: Tải Slide + Audio & Tiến độ Queue (BatchUploadTab)
            val resolvedSlideName = resolveDisplayFileName(context, batchSlideUri) ?: "Chưa chọn file Slide"
            val resolvedAudioName = resolveDisplayFileName(context, batchAudioUri)
                ?: directAudioFile?.name
                ?: "Chưa chọn file Audio"
            val isReadyToUpload = (batchSlideUri != null) && (batchAudioUri != null) && !isProcessingQueue && !isDirectMicRecording

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = "Tải Slide & Ghi âm để xử lý Queue",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = "Chọn tài liệu PDF/PPTX và tệp âm thanh bài giảng để AI căn gióng và tổng hợp ghi chú 3 lớp",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        BatchFilePickerSlot(
                            icon = Icons.Default.Description,
                            iconBg = primarySoft,
                            iconTint = StudyAiPalette.PrimaryCobalt,
                            title = "Tài liệu Slide (.pdf / .pptx)",
                            fileName = resolvedSlideName,
                            hasFile = batchSlideUri != null,
                            actionColor = primaryText,
                            surfaceSubtle = surfaceSubtle,
                            onClick = {
                                batchSlidePicker.launch(
                                    arrayOf(
                                        "application/pdf",
                                        "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                                    )
                                )
                            },
                        )

                        BatchFilePickerSlot(
                            icon = Icons.Default.AudioFile,
                            iconBg = tealSoft,
                            iconTint = StudyAiPalette.Teal,
                            title = "Tệp Ghi âm (.m4a / .mp3 / .wav)",
                            fileName = resolvedAudioName,
                            hasFile = batchAudioUri != null || directAudioFile != null,
                            actionColor = tealText,
                            surfaceSubtle = surfaceSubtle,
                            onClick = { batchAudioPicker.launch(arrayOf("audio/*", "video/mp4")) },
                        )

                        Button(
                            onClick = {
                                val s = batchSlideUri
                                val a = batchAudioUri
                                if (s != null && a != null) {
                                    onAnalyzeUploadedPair(s, a)
                                }
                            },
                            enabled = isReadyToUpload,
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = StudyAiPalette.PrimaryCobalt,
                                contentColor = Color.White,
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = if (isProcessingQueue) "Đang xử lý Queue..." else "Tải lên & Xử lý AI (Queue)",
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }

                if (isProcessingQueue || uploadProgress >= 100) {
                    QueueProgressCard(
                        isProcessingQueue = isProcessingQueue,
                        uploadProgress = uploadProgress,
                        uploadStatusText = uploadStatusText,
                        onOpenGeneratedNotes = onOpenGeneratedNotes,
                    )
                }

                // Tùy chọn thu nhanh Micro hoặc thử nhanh ghi chú từ Voice
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = {
                            if (!isDirectMicRecording) {
                                val granted = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.RECORD_AUDIO
                                ) == PackageManager.PERMISSION_GRANTED
                                if (granted) {
                                    try {
                                        directRecorder.start("lecture_mic")
                                        isDirectMicRecording = true
                                        directMicElapsedSec = 0
                                    } catch (_: Exception) {
                                    }
                                } else {
                                    batchMicPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            } else {
                                val recorded = directRecorder.stop()
                                isDirectMicRecording = false
                                if (recorded != null) {
                                    directAudioFile = recorded
                                    batchAudioUri = Uri.fromFile(recorded)
                                }
                            }
                        },
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(
                            1.dp,
                            if (isDirectMicRecording) StudyAiPalette.Rose else MaterialTheme.colorScheme.outline,
                        ),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (isDirectMicRecording) roseSoft else Color.Transparent,
                            contentColor = if (isDirectMicRecording) roseText else MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(
                            imageVector = if (isDirectMicRecording) Icons.Default.Stop else Icons.Default.Mic,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = if (isDirectMicRecording) {
                                "Dừng (%02d:%02d)".format(directMicElapsedSec / 60, directMicElapsedSec % 60)
                            } else {
                                "Thu từ Micro"
                            },
                            fontSize = 12.sp,
                            maxLines = 1,
                        )
                    }

                    if (onTestVoiceNotes != null) {
                        OutlinedButton(
                            onClick = { batchAudioUri?.let(onTestVoiceNotes) },
                            enabled = batchAudioUri != null && !isVoiceTesting && !isProcessingQueue && !isDirectMicRecording,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            if (isVoiceTesting) {
                                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(6.dp))
                                Text("Đang thử...", fontSize = 12.sp)
                            } else {
                                Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Thử nhanh Voice", fontSize = 12.sp, maxLines = 1)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))
            }
        }
    }

    if (showFinishLiveSheet) {
        FinishLiveBottomSheet(
            onDismiss = { showFinishLiveSheet = false },
            onConfirmFinish = { refineFull ->
                showFinishLiveSheet = false
                onStopLiveFollow(refineFull)
            },
        )
    }

    if (voiceTestResult != null) {
        VoiceTestResultDialog(
            voiceTestResult = voiceTestResult,
            onDismiss = { onDismissVoiceTestResult?.invoke() },
        )
    }
}

@Composable
private fun Live16BarWaveform(
    isRecording: Boolean,
    amplitude: Float,
) {
    var waveTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(isRecording) {
        if (isRecording) {
            while (true) {
                delay(130L)
                waveTick += 1
            }
        }
    }

    val restColor = MaterialTheme.colorScheme.outline
    Row(
        modifier = Modifier.height(36.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(16) { idx ->
            val targetHeight = if (isRecording) {
                val centerWeight = 1f - (abs(idx - 7.5f) / 9.5f)
                val ripple = 0.55f + 0.45f * sin(waveTick * 0.55 + idx * 0.72).toFloat()
                (8f + 26f * amplitude * centerWeight * ripple).coerceIn(8f, 34f).dp
            } else {
                8.dp
            }
            val barHeight by animateDpAsState(
                targetValue = targetHeight,
                animationSpec = tween(durationMillis = 110),
                label = "wave_bar_$idx",
            )
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(barHeight)
                    .clip(CircleShape)
                    .background(if (isRecording) StudyAiPalette.Rose else restColor)
            )
        }
    }
}

@Composable
private fun LiveFeedEventCard(
    item: LiveFeedItemUi,
    primarySoft: Color,
    primaryText: Color,
    roseSoft: Color,
    roseText: Color,
    surfaceSubtle: Color,
) {
    when (item.kind) {
        LiveFeedKind.LECTURER_NOTE -> {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = primarySoft,
                        ) {
                            Text(
                                text = "Slide %02d".format(item.slideNumber),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = primaryText,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            )
                        }
                        Text(
                            text = item.timestampLabel,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = item.text,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        LiveFeedKind.BLOCKED_NON_TEACHER -> {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = surfaceSubtle,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(
                            imageVector = Icons.Default.VerifiedUser,
                            contentDescription = null,
                            tint = StudyAiPalette.Teal,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = item.text.ifBlank { "Đã chặn tiếng sinh viên bàn tán" },
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = item.timestampLabel,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        LiveFeedKind.CONFUSED_PIN -> {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = roseSoft,
                border = BorderStroke(1.dp, StudyAiPalette.Rose),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = StudyAiPalette.Rose,
                            ) {
                                Text(
                                    text = "Ghim chưa hiểu",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                )
                            }
                            Text(
                                text = "Slide %02d".format(item.slideNumber),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = roseText,
                            )
                        }
                        Text(
                            text = item.timestampLabel,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = roseText,
                        )
                    }
                    Text(
                        text = item.text,
                        style = MaterialTheme.typography.bodySmall,
                        color = roseText,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun BatchFilePickerSlot(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconBg: Color,
    iconTint: Color,
    title: String,
    fileName: String,
    hasFile: Boolean,
    actionColor: Color,
    surfaceSubtle: Color,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = surfaceSubtle,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(iconBg),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(20.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = fileName,
                    fontSize = 13.sp,
                    fontWeight = if (hasFile) FontWeight.SemiBold else FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = if (hasFile) "Đổi" else "Chọn",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = actionColor,
            )
        }
    }
}

@Composable
private fun QueueProgressCard(
    isProcessingQueue: Boolean,
    uploadProgress: Int,
    uploadStatusText: String,
    onOpenGeneratedNotes: () -> Unit,
) {
    val isDark = StudyAiExtras.isDark
    val primaryText = if (isDark) StudyAiPalette.PrimaryTextDark else StudyAiPalette.PrimaryTextLight
    val tealText = if (isDark) StudyAiPalette.TealTextDark else StudyAiPalette.TealTextLight
    val isCompleted = !isProcessingQueue && uploadProgress >= 100

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = 1.dp,
            color = if (isCompleted) StudyAiPalette.Teal else StudyAiPalette.PrimaryCobalt.copy(alpha = 0.45f),
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = uploadStatusText.ifBlank {
                        if (isCompleted) "Hoàn tất xử lý bài giảng (100%)!" else "Đang xử lý bài giảng qua AI..."
                    },
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "${uploadProgress.coerceIn(0, 100)}%",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isCompleted) tealText else primaryText,
                )
            }

            LinearProgressIndicator(
                progress = { (uploadProgress / 100f).coerceIn(0f, 1f) },
                color = if (isCompleted) StudyAiPalette.Teal else StudyAiPalette.PrimaryCobalt,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(999.dp)),
            )

            if (isCompleted) {
                Button(
                    onClick = onOpenGeneratedNotes,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = StudyAiPalette.Teal,
                        contentColor = Color.White,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp),
                ) {
                    Text(
                        text = "Mở Vở Ghi Chép vừa tạo",
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinishLiveBottomSheet(
    onDismiss: () -> Unit,
    onConfirmFinish: (refineFullLecture: Boolean) -> Unit,
) {
    var refineFull by remember { mutableStateOf(true) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp)
        ) {
            Text(
                text = "Chốt buổi học trực tiếp",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "Hệ thống sẽ ghép các đoạn ghi âm 8 giây thành live_recording.m4a để bạn nghe lại bằng ExoPlayer.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            Spacer(Modifier.height(14.dp))
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { refineFull = !refineFull }
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Tinh chỉnh lại toàn bài bằng AI",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "Chạy pyannote + Viterbi BGE-M3 + Gemini 3 lớp",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Checkbox(
                        checked = refineFull,
                        onCheckedChange = { refineFull = it },
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Tiếp tục ghi")
                }
                Button(
                    onClick = { onConfirmFinish(refineFull) },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = StudyAiPalette.PrimaryCobalt,
                        contentColor = Color.White,
                    ),
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Chốt & Lưu Audio", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun ModeSegmentButton(
    text: String,
    selected: Boolean,
    primaryTextColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (selected) MaterialTheme.colorScheme.surface else Color.Transparent,
        shadowElevation = if (selected) 1.dp else 0.dp,
        modifier = modifier.height(38.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = text,
                fontSize = 13.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) primaryTextColor else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun VoiceTestResultDialog(
    voiceTestResult: VoiceNotesTestResponse,
    onDismiss: () -> Unit,
) {
    val clipboardManager = LocalClipboardManager.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.GraphicEq, contentDescription = null, tint = StudyAiPalette.PrimaryCobalt)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = voiceTestResult.title.ifBlank { "Vở ghi chép từ Voice" },
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 2,
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(
                                imageVector = Icons.Default.Summarize,
                                contentDescription = null,
                                tint = StudyAiPalette.PrimaryCobalt,
                                modifier = Modifier.size(16.dp),
                            )
                            Text(
                                text = "Tóm tắt tổng quan (${voiceTestResult.durationSec.toInt()}s)",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
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
                            tint = StudyAiPalette.Teal,
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
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = "${idx + 1}. [${kp.timestampLabel}] ${kp.headline}",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = StudyAiPalette.PrimaryCobalt,
                                )
                                Spacer(Modifier.height(3.dp))
                                Text(text = kp.content, style = MaterialTheme.typography.bodySmall)
                                if (kp.evidenceQuote.isNotBlank()) {
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = "\"${kp.evidenceQuote}\"",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }

                if (voiceTestResult.technicalTerms.isNotEmpty()) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.MenuBook,
                            contentDescription = null,
                            tint = StudyAiPalette.PrimaryCobalt,
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
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }

                if (voiceTestResult.actionItemsOrTips.isNotEmpty()) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(
                            imageVector = Icons.Default.WarningAmber,
                            contentDescription = null,
                            tint = StudyAiPalette.Amber,
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
            TextButton(onClick = onDismiss) {
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
        },
    )
}
