package com.studyai.ui.dictionary

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.BookmarkAdded
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.studyai.ui.theme.StudyAiExtras
import com.studyai.ui.theme.StudyAiPalette

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LivingLookupBottomSheet(
    result: LivingLookupResult,
    isAlreadySaved: Boolean,
    isLoading: Boolean = false,
    jumpButtonLabel: String = "Về đoạn đã bôi",
    onDismiss: () -> Unit,
    onJumpToOrigin: (lectureId: Long?, slideNumber: Int?) -> Unit,
    onSaveToVault: (LivingLookupResult) -> Unit,
) {
    val isDark = StudyAiExtras.isDark
    val surfaceColor = MaterialTheme.colorScheme.surface
    val surfaceSubtle = MaterialTheme.colorScheme.surfaceVariant
    val canvasColor = MaterialTheme.colorScheme.background
    val borderColor = MaterialTheme.colorScheme.outline
    val ink = MaterialTheme.colorScheme.onSurface
    val inkSecondary = if (isDark) StudyAiPalette.InkSecondaryDark else StudyAiPalette.InkSecondaryLight
    val inkMuted = MaterialTheme.colorScheme.onSurfaceVariant
    val primaryText = if (isDark) StudyAiPalette.PrimaryTextDark else StudyAiPalette.PrimaryTextLight
    val tealSoft = if (isDark) StudyAiPalette.TealSoftDark else StudyAiPalette.TealSoftLight
    val tealText = if (isDark) StudyAiPalette.TealTextDark else StudyAiPalette.TealTextLight

    val uriHandler = LocalUriHandler.current
    @Suppress("DEPRECATION")
    val clipboard = LocalClipboardManager.current
    var activeTab by remember(result.term) { mutableIntStateOf(0) } // 0 = Giải thích AI, 1 = Nguồn uy tín
    var activeFollowup by remember(result.term) { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = surfaceColor,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = tealSoft,
                ) {
                    Text(
                        text = "Từ điển sống",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = tealText,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
                Text(
                    text = result.originLabel,
                    fontSize = 10.5.sp,
                    fontFamily = FontFamily.Monospace,
                    color = inkMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(Modifier.height(6.dp))

            Text(
                text = result.term,
                fontSize = 18.sp,
                lineHeight = 24.sp,
                fontWeight = FontWeight.ExtraBold,
                color = ink,
            )

            // Segmented 2-Tab Switcher
            Spacer(Modifier.height(12.dp))
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = surfaceSubtle,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    listOf("Giải thích AI", "Nguồn uy tín (${result.trustedSources.size})").forEachIndexed { idx, label ->
                        val selected = activeTab == idx
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (selected) surfaceColor else Color.Transparent,
                            shadowElevation = if (selected) 1.dp else 0.dp,
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { activeTab = idx },
                        ) {
                            Box(
                                modifier = Modifier.padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 12.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                                    color = if (selected) {
                                        if (idx == 0) primaryText else tealText
                                    } else {
                                        inkMuted
                                    },
                                )
                            }
                        }
                    }
                }
            }

            // Scrollable Body
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 390.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (isLoading) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        LinearProgressIndicator(
                            color = StudyAiPalette.PrimaryCobalt,
                            trackColor = surfaceSubtle,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(999.dp)),
                        )
                        Text(
                            text = "AI đang phân tích ngữ cảnh và trích xuất nguồn học thuật chính thức...",
                            fontSize = 12.sp,
                            color = inkMuted,
                        )
                    }
                }

                if (activeTab == 0) {
                    // Trích dẫn câu vừa bôi đen
                    if (result.contextQuote.isNotBlank()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(IntrinsicSize.Min)
                                .clip(RoundedCornerShape(14.dp))
                                .background(surfaceSubtle)
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(3.dp)
                                    .fillMaxHeight()
                                    .background(StudyAiPalette.PrimaryCobalt)
                            )
                            Text(
                                text = result.contextQuote,
                                fontSize = 11.5.sp,
                                lineHeight = 17.sp,
                                fontStyle = FontStyle.Italic,
                                color = inkMuted,
                                modifier = Modifier.padding(10.dp),
                            )
                        }
                    }

                    Text(
                        text = result.aiExplanation,
                        fontSize = 13.sp,
                        lineHeight = 20.sp,
                        color = inkSecondary,
                    )

                    // Hộp Code tối màu #0F172A
                    result.codeExample?.takeIf { it.isNotBlank() }?.let { code ->
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = StudyAiPalette.CodeBg,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = "Ví dụ minh họa",
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = StudyAiPalette.InkMutedDark,
                                    )
                                    Text(
                                        text = "Chép",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = StudyAiPalette.PrimaryTextDark,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .clickable { clipboard.setText(AnnotatedString(code)) }
                                            .padding(horizontal = 6.dp, vertical = 2.dp),
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState())
                                        .padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
                                ) {
                                    Text(
                                        text = code,
                                        fontSize = 11.sp,
                                        lineHeight = 16.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = StudyAiPalette.CodeInk,
                                    )
                                }
                            }
                        }
                    }

                    // 2 chip hỏi nối tiếp
                    if (!result.simpleFollowup.isNullOrBlank() || !result.pitfallFollowup.isNullOrBlank()) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                        ) {
                            result.simpleFollowup?.takeIf { it.isNotBlank() }?.let { txt ->
                                SuggestionChip(
                                    onClick = { activeFollowup = if (activeFollowup == txt) null else txt },
                                    label = { Text("Ví dụ đời thường?", fontSize = 11.sp) },
                                )
                            }
                            result.pitfallFollowup?.takeIf { it.isNotBlank() }?.let { txt ->
                                SuggestionChip(
                                    onClick = { activeFollowup = if (activeFollowup == txt) null else txt },
                                    label = { Text("Lỗi hay gặp khi thi?", fontSize = 11.sp) },
                                )
                            }
                        }
                    }

                    activeFollowup?.let { reply ->
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = canvasColor,
                            border = BorderStroke(1.dp, borderColor),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = reply,
                                fontSize = 12.sp,
                                lineHeight = 18.sp,
                                color = inkSecondary,
                                modifier = Modifier.padding(12.dp),
                            )
                        }
                    }
                } else {
                    // Tab 2: Nguồn uy tín
                    if (result.trustedSources.isEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = canvasColor,
                            border = BorderStroke(1.dp, borderColor),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = "Chưa có tài liệu tham khảo cho thuật ngữ này.",
                                fontSize = 12.sp,
                                color = inkMuted,
                                modifier = Modifier.padding(14.dp),
                            )
                        }
                    } else {
                        result.trustedSources.forEach { src ->
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = canvasColor,
                                border = BorderStroke(1.dp, borderColor),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(18.dp))
                                    .clickable {
                                        if (src.url.isNotBlank()) {
                                            runCatching { uriHandler.openUri(src.url) }
                                        }
                                    },
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.VerifiedUser,
                                                contentDescription = null,
                                                tint = StudyAiPalette.Teal,
                                                modifier = Modifier.size(14.dp),
                                            )
                                            Text(
                                                text = src.domain,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = tealText,
                                            )
                                        }
                                        Surface(
                                            shape = RoundedCornerShape(999.dp),
                                            color = tealSoft,
                                        ) {
                                            Text(
                                                text = src.badge,
                                                fontSize = 9.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = tealText,
                                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                            )
                                        }
                                    }
                                    Text(
                                        text = src.title,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = ink,
                                    )
                                    if (src.excerpt.isNotBlank()) {
                                        Text(
                                            text = src.excerpt,
                                            fontSize = 11.5.sp,
                                            lineHeight = 17.sp,
                                            color = inkSecondary,
                                        )
                                    }
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        Text(
                                            text = "Mở trang gốc",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = primaryText,
                                        )
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                            contentDescription = null,
                                            tint = primaryText,
                                            modifier = Modifier.size(13.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Footer Actions
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 22.dp, top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(
                    onClick = { onJumpToOrigin(result.lectureId, result.slideNumber) },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = jumpButtonLabel,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                }
                Button(
                    onClick = { if (!isLoading) onSaveToVault(result) },
                    enabled = !isLoading,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isAlreadySaved) StudyAiPalette.Teal else StudyAiPalette.PrimaryCobalt,
                        contentColor = Color.White,
                    ),
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        imageVector = if (isAlreadySaved) Icons.Default.BookmarkAdded else Icons.Default.BookmarkAdd,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = if (isAlreadySaved) "Đã lưu ✓" else "Lưu vào Từ điển",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
