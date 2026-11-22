package com.studyai.ui.tasks

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp

data class MilestoneUi(
    val id: Long,
    val milestoneTitle: String,
    val targetDate: String,
    val isCompleted: Boolean
)

data class TaskItemUi(
    val id: Long,
    val title: String,
    val description: String,
    val dueAt: String,
    val urgencyScore: Float,
    val priorityLevel: String, // "CRITICAL" | "HIGH" | "MEDIUM" | "LOW"
    val estimatedEffortHours: Float,
    val status: String,        // "PENDING_CONFIRMATION" | "TODO" | "RESCHEDULED"
    val sourceType: String,    // "email" | "telegram" | "lecture_audio"
    val senderName: String,
    val evidenceQuote: String,
    val milestones: List<MilestoneUi>
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeadlineDashboardScreen(
    tasks: List<TaskItemUi>,
    onToggleMilestone: (Long) -> Unit,
    onConfirmLowConfidenceTask: (Long) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Tổng hợp Deadline Đa nguồn", fontWeight = FontWeight.Bold)
                        Text(
                            "Tự động gộp trùng • Sắp theo Explainable Urgency Score",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. Thẻ tóm tắt 4 cấp độ ưu tiên (CRITICAL / HIGH / MEDIUM / LOW)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PriorityStatChip(
                        label = "CRITICAL",
                        count = tasks.count { it.priorityLevel == "CRITICAL" },
                        color = Color(0xFFD32F2F),
                        modifier = Modifier.weight(1f)
                    )
                    PriorityStatChip(
                        label = "HIGH",
                        count = tasks.count { it.priorityLevel == "HIGH" },
                        color = Color(0xFFEF6C00),
                        modifier = Modifier.weight(1f)
                    )
                    PriorityStatChip(
                        label = "MEDIUM/LOW",
                        count = tasks.count { it.priorityLevel == "MEDIUM" || it.priorityLevel == "LOW" },
                        color = Color(0xFF2E7D32),
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // 2. Danh sách Nhiệm vụ sắp xếp theo Urgency Score giảm dần
            items(tasks, key = { it.id }) { task ->
                TaskUrgencyCard(
                    task = task,
                    onToggleMilestone = onToggleMilestone,
                    onConfirmTask = { onConfirmLowConfidenceTask(task.id) }
                )
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}

@Composable
fun PriorityStatChip(label: String, count: Int, color: Color, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.12f)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = "$count", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = color)
            Text(text = label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = color)
        }
    }
}

@Composable
fun TaskUrgencyCard(
    task: TaskItemUi,
    onToggleMilestone: (Long) -> Unit,
    onConfirmTask: () -> Unit
) {
    var expandDetails by remember { mutableStateOf(true) }

    val priorityColor = when (task.priorityLevel) {
        "CRITICAL" -> Color(0xFFD32F2F)
        "HIGH" -> Color(0xFFEF6C00)
        "MEDIUM" -> Color(0xFFF9A825)
        else -> Color(0xFF2E7D32)
    }

    val sourceLabel = when (task.sourceType) {
        "telegram" -> "Telegram Bot"
        "email" -> "Gmail Sinh viên"
        else -> "Lời giảng trên lớp"
    }

    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Hàng 1: Nhãn Priority + Điểm Urgency + Nhãn Nguồn trích xuất
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = priorityColor,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = "${task.priorityLevel} • Urgency ${task.urgencyScore.toInt()}/100",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                AssistChip(
                    onClick = {},
                    label = { Text(sourceLabel, style = MaterialTheme.typography.labelSmall) }
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Hàng 2: Tên công việc & Hạn nộp chuẩn hóa UTC+7
            Text(
                text = task.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Hạn chót: ${task.dueAt} • Ước lượng: ${task.estimatedEffortHours} giờ",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Giải thích minh bạch công thức Urgency Score
            Text(
                text = "Giải thích ưu tiên: ${task.description}",
                style = MaterialTheme.typography.bodySmall,
                color = priorityColor,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(top = 4.dp)
            )

            // Nếu là việc có độ tin cậy thấp cần người dùng xác nhận
            if (task.status == "PENDING_CONFIRMATION") {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFFFF8E1))
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Thông báo có ngày giờ tương đối cần bạn xác nhận:",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFE65100),
                        modifier = Modifier.weight(1f)
                    )
                    Button(onClick = onConfirmTask) {
                        Text("Xác nhận đúng")
                    }
                }
            }

            // Hàng 3: Danh sách các Mốc chia nhỏ (Task Milestones)
            if (task.milestones.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Các mốc thực hiện đề xuất (${task.milestones.count { it.isCompleted }}/${task.milestones.size}):",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { expandDetails = !expandDetails }
                )
                AnimatedVisibility(visible = expandDetails) {
                    Column {
                        task.milestones.forEach { ms ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onToggleMilestone(ms.id) }
                                    .padding(vertical = 2.dp)
                            ) {
                                Checkbox(
                                    checked = ms.isCompleted,
                                    onCheckedChange = { onToggleMilestone(ms.id) }
                                )
                                Column {
                                    Text(
                                        text = ms.milestoneTitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        textDecoration = if (ms.isCompleted) TextDecoration.LineThrough else null
                                    )
                                    Text(
                                        text = "Mốc gợi ý: ${ms.targetDate}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Hàng 4: Bằng chứng gốc (Verbatim Quote)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Bằng chứng gốc (${task.senderName}): \"${task.evidenceQuote}\"",
                style = MaterialTheme.typography.labelSmall,
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
