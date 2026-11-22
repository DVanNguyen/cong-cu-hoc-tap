package com.studyai.ui.graph

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class RoadmapViewMode {
    LAYERED, // Lộ trình phân tầng (Mặc định - Mobile First)
    GRAPH    // Sơ đồ thu phóng (Pill badges ngắn gọn)
}

data class RoadmapItemUi(
    val id: String,
    val shortCode: String,
    val title: String,
    val location: String,
    val lectureId: Int,
    val slideNumber: Int,
    val mastered: Boolean,
    val isWarning: Boolean = false,
    val prerequisites: List<String> = emptyList()
)

data class RoadmapTierUi(
    val levelName: String,
    val progressPercent: Int,
    val items: List<RoadmapItemUi>
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KnowledgeRoadmapScreen(
    courseTitle: String = "Trí tuệ Nhân tạo (AI)",
    conceptNodes: List<ConceptNodeUi> = emptyList(),
    conceptEdges: List<ConceptEdgeUi> = emptyList(),
    onJumpToSlide: (lectureId: Int, slideNo: Int) -> Unit = { _, _ -> }
) {
    var viewMode by remember { mutableStateOf(RoadmapViewMode.LAYERED) }
    var selectedItemDetail by remember { mutableStateOf<RoadmapItemUi?>(null) }

    // Dữ liệu phân tầng mẫu kết hợp dữ liệu thực tế từ ConceptNodeUi
    val tiers = remember(conceptNodes) {
        if (conceptNodes.isNotEmpty()) {
            val tier1Nodes = conceptNodes.filter { it.firstIntroducedLectureId <= 1 }
            val tier2Nodes = conceptNodes.filter { it.firstIntroducedLectureId in 2..3 }
            val tier3Nodes = conceptNodes.filter { it.firstIntroducedLectureId >= 4 }

            listOf(
                RoadmapTierUi(
                    levelName = "Tầng 1 • Nền tảng cơ sở",
                    progressPercent = 100,
                    items = tier1Nodes.map { n ->
                        RoadmapItemUi(
                            id = n.id.toString(),
                            shortCode = n.canonicalNameEn.take(4).uppercase(),
                            title = n.canonicalNameVi,
                            location = "Bài ${n.firstIntroducedLectureId} • Slide ${n.firstIntroducedSlideNo}",
                            lectureId = n.firstIntroducedLectureId,
                            slideNumber = n.firstIntroducedSlideNo,
                            mastered = true,
                            isWarning = n.hasViolation,
                            prerequisites = emptyList()
                        )
                    }
                ),
                RoadmapTierUi(
                    levelName = "Tầng 2 • Thuật toán lõi & Tìm kiếm",
                    progressPercent = 65,
                    items = tier2Nodes.map { n ->
                        RoadmapItemUi(
                            id = n.id.toString(),
                            shortCode = if (n.canonicalNameEn.contains("A*")) "A*" else n.canonicalNameEn.take(4).uppercase(),
                            title = n.canonicalNameVi,
                            location = "Bài ${n.firstIntroducedLectureId} • Slide ${n.firstIntroducedSlideNo}",
                            lectureId = n.firstIntroducedLectureId,
                            slideNumber = n.firstIntroducedSlideNo,
                            mastered = !n.hasViolation,
                            isWarning = n.hasViolation,
                            prerequisites = if (n.canonicalNameEn.contains("A*")) listOf("BFS", "Heuristic") else listOf("Agent")
                        )
                    }
                ),
                RoadmapTierUi(
                    levelName = "Tầng 3 • Tối ưu & Mô hình nâng cao",
                    progressPercent = 30,
                    items = tier3Nodes.map { n ->
                        RoadmapItemUi(
                            id = n.id.toString(),
                            shortCode = if (n.canonicalNameEn.contains("GAN")) "GAN" else n.canonicalNameEn.take(4).uppercase(),
                            title = n.canonicalNameVi,
                            location = "Bài ${n.firstIntroducedLectureId} • Slide ${n.firstIntroducedSlideNo}",
                            lectureId = n.firstIntroducedLectureId,
                            slideNumber = n.firstIntroducedSlideNo,
                            mastered = false,
                            isWarning = n.hasViolation,
                            prerequisites = listOf("A*")
                        )
                    }
                )
            )
        } else {
            // Mẫu dự phòng khi chưa tải được từ máy chủ
            listOf(
                RoadmapTierUi(
                    levelName = "Tầng 1 • Nền tảng cơ sở",
                    progressPercent = 100,
                    items = listOf(
                        RoadmapItemUi("1", "AGENT", "Tác tử thông minh (Intelligent Agent)", "Chương 1 • Slide 4", 1, 4, true),
                        RoadmapItemUi("2", "PEAS", "Mô hình môi trường PEAS", "Chương 1 • Slide 6", 1, 6, true, prerequisites = listOf("Agent"))
                    )
                ),
                RoadmapTierUi(
                    levelName = "Tầng 2 • Thuật toán lõi & Heuristic",
                    progressPercent = 65,
                    items = listOf(
                        RoadmapItemUi("3", "BFS", "Tìm kiếm theo chiều rộng (BFS)", "Chương 2 • Slide 8", 2, 8, true, prerequisites = listOf("Agent")),
                        RoadmapItemUi("4", "A*", "Thuật toán tìm kiếm A*", "Chương 3 • Slide 6", 3, 6, false, isWarning = true, prerequisites = listOf("BFS", "Heuristic"))
                    )
                ),
                RoadmapTierUi(
                    levelName = "Tầng 3 • Tối ưu & Học sâu",
                    progressPercent = 30,
                    items = listOf(
                        RoadmapItemUi("5", "SA", "Mô phỏng tôi thép (Simulated Annealing)", "Chương 4 • Slide 11", 4, 11, false, prerequisites = listOf("A*")),
                        RoadmapItemUi("6", "GAN", "Mạng đối nghịch tạo sinh (GAN)", "Chương 5 • Slide 12", 5, 12, false, prerequisites = listOf("SA"))
                    )
                )
            )
        }
    }

    val violationCount = remember(tiers) {
        tiers.sumOf { tier -> tier.items.count { it.isWarning } }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { Spacer(modifier = Modifier.height(4.dp)) }

        // Header: Tiêu đề + Nút chuyển chế độ (Lộ trình vs Sơ đồ)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Hệ thống hoá kiến thức",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Sắp xếp theo thứ tự tiên quyết (Prerequisite DAG)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Row(modifier = Modifier.padding(3.dp)) {
                        val isLayered = viewMode == RoadmapViewMode.LAYERED
                        Surface(
                            onClick = { viewMode = RoadmapViewMode.LAYERED },
                            shape = RoundedCornerShape(9.dp),
                            color = if (isLayered) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                        ) {
                            Text(
                                text = "Lộ trình",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isLayered) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }

                        val isGraph = viewMode == RoadmapViewMode.GRAPH
                        Surface(
                            onClick = { viewMode = RoadmapViewMode.GRAPH },
                            shape = RoundedCornerShape(9.dp),
                            color = if (isGraph) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                        ) {
                            Text(
                                text = "Sơ đồ",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isGraph) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }
        }

        // Cảnh báo sư phạm nếu có khái niệm dùng trước khi định nghĩa
        if (violationCount > 0) {
            item {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.85f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Default.WarningAmber,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Cảnh báo sư phạm ($violationCount vị trí):",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                text = "Khái niệm 'Thuật toán A*' được nhắc tại Chương 1 trước khi được định nghĩa chính thức tại Chương 3 (Slide 6).",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }
        }

        if (viewMode == RoadmapViewMode.LAYERED) {
            // Chế độ 1: Lộ trình phân tầng (Layered Roadmap) - Không bao giờ đè chữ
            items(tiers) { tier ->
                OutlinedCard(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = tier.levelName,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${tier.progressPercent}% nắm vững",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Thanh tiến độ nắm vững môn học
                        LinearProgressIndicator(
                            progress = { tier.progressPercent / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = MaterialTheme.colorScheme.secondary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Danh sách khái niệm trong tầng này
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            tier.items.forEach { item ->
                                Surface(
                                    onClick = { onJumpToSlide(item.lectureId, item.slideNumber) },
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Badge mã viết tắt (Short Code)
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(
                                                    when {
                                                        item.isWarning -> MaterialTheme.colorScheme.errorContainer
                                                        item.mastered -> MaterialTheme.colorScheme.secondaryContainer
                                                        else -> MaterialTheme.colorScheme.primaryContainer
                                                    }
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = item.shortCode,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = when {
                                                    item.isWarning -> MaterialTheme.colorScheme.onErrorContainer
                                                    item.mastered -> MaterialTheme.colorScheme.onSecondaryContainer
                                                    else -> MaterialTheme.colorScheme.onPrimaryContainer
                                                }
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(10.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = item.title,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Text(
                                                    text = item.location,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                if (item.prerequisites.isNotEmpty()) {
                                                    Text(
                                                        text = "• Cần biết: ${item.prerequisites.joinToString(", ")}",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.primary,
                                                        fontWeight = FontWeight.Medium
                                                    )
                                                }
                                            }
                                        }

                                        Icon(
                                            imageVector = Icons.Default.ChevronRight,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // Chế độ 2: Sơ đồ thu phóng (Zoomable Map) với Nhãn ngắn gọn không đè chữ
            item {
                var scale by remember { mutableFloatStateOf(1f) }
                var offset by remember { mutableStateOf(Offset.Zero) }

                OutlinedCard(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Sơ đồ phụ thuộc (Chạm để mở Slide)",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = "Nhãn rút gọn",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(260.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                                .pointerInput(Unit) {
                                    detectTransformGestures { _, pan, zoom, _ ->
                                        scale = (scale * zoom).coerceIn(0.8f, 2.5f)
                                        offset += pan
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            // Bố trí mạng lưới các nút viên nang trực quan
                            Column(
                                verticalArrangement = Arrangement.spacedBy(22.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                // Tầng 1
                                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                    CompactPillNode("1. Agent", MaterialTheme.colorScheme.primary) { onJumpToSlide(1, 4) }
                                    CompactPillNode("2. PEAS", MaterialTheme.colorScheme.secondary) { onJumpToSlide(1, 6) }
                                }
                                // Mũi tên liên kết
                                Icon(
                                    imageVector = Icons.Default.ArrowDownward,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.size(16.dp)
                                )
                                // Tầng 2
                                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                    CompactPillNode("3. BFS", MaterialTheme.colorScheme.secondary) { onJumpToSlide(2, 8) }
                                    CompactPillNode("4. A* (!)", MaterialTheme.colorScheme.error) { onJumpToSlide(3, 6) }
                                }
                                // Mũi tên liên kết
                                Icon(
                                    imageVector = Icons.Default.ArrowDownward,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.size(16.dp)
                                )
                                // Tầng 3
                                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                    CompactPillNode("5. Simulated Annealing", MaterialTheme.colorScheme.primary) { onJumpToSlide(4, 11) }
                                    CompactPillNode("6. GAN", MaterialTheme.colorScheme.primary) { onJumpToSlide(5, 12) }
                                }
                            }
                        }
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }
    }
}

@Composable
private fun CompactPillNode(
    label: String,
    tintColor: Color,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = tintColor,
        shadowElevation = 2.dp
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
        )
    }
}
