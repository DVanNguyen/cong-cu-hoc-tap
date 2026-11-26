package com.studyai.ui.graph

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

enum class RoadmapViewMode {
    LAYERED,
    GRAPH
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

@Composable
fun KnowledgeRoadmapScreen(
    courseTitle: String = "Môn học",
    conceptNodes: List<ConceptNodeUi> = emptyList(),
    conceptEdges: List<ConceptEdgeUi> = emptyList(),
    onJumpToSlide: (lectureId: Int, slideNo: Int) -> Unit = { _, _ -> }
) {
    var viewMode by remember { mutableStateOf(RoadmapViewMode.LAYERED) }

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
                            shortCode = n.canonicalNameEn.take(4).uppercase().ifBlank { "KN" },
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
                    levelName = "Tầng 2 • Khái niệm cốt lõi",
                    progressPercent = 65,
                    items = tier2Nodes.map { n ->
                        RoadmapItemUi(
                            id = n.id.toString(),
                            shortCode = if (n.canonicalNameEn.contains("A*")) "A*" else n.canonicalNameEn.take(4).uppercase().ifBlank { "KN" },
                            title = n.canonicalNameVi,
                            location = "Bài ${n.firstIntroducedLectureId} • Slide ${n.firstIntroducedSlideNo}",
                            lectureId = n.firstIntroducedLectureId,
                            slideNumber = n.firstIntroducedSlideNo,
                            mastered = !n.hasViolation,
                            isWarning = n.hasViolation,
                            prerequisites = if (n.canonicalNameEn.contains("A*")) listOf("BFS", "Heuristic") else listOf("Nền tảng")
                        )
                    }
                ),
                RoadmapTierUi(
                    levelName = "Tầng 3 • Mở rộng & Ứng dụng",
                    progressPercent = 30,
                    items = tier3Nodes.map { n ->
                        RoadmapItemUi(
                            id = n.id.toString(),
                            shortCode = if (n.canonicalNameEn.contains("GAN")) "GAN" else n.canonicalNameEn.take(4).uppercase().ifBlank { "KN" },
                            title = n.canonicalNameVi,
                            location = "Bài ${n.firstIntroducedLectureId} • Slide ${n.firstIntroducedSlideNo}",
                            lectureId = n.firstIntroducedLectureId,
                            slideNumber = n.firstIntroducedSlideNo,
                            mastered = false,
                            isWarning = n.hasViolation,
                            prerequisites = emptyList()
                        )
                    }
                )
            ).filter { it.items.isNotEmpty() }
        } else {
            emptyList()
        }
    }

    val violationItems = remember(tiers) {
        tiers.flatMap { it.items }.filter { it.isWarning }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Header: Tiêu đề + Bộ chuyển chế độ Lộ trình / Sơ đồ
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Hệ thống hóa kiến thức",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "$courseTitle • ${conceptNodes.size} khái niệm",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Spacer(Modifier.width(8.dp))

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                ) {
                    Row(modifier = Modifier.padding(3.dp)) {
                        val isLayered = viewMode == RoadmapViewMode.LAYERED
                        Surface(
                            onClick = { viewMode = RoadmapViewMode.LAYERED },
                            shape = RoundedCornerShape(9.dp),
                            color = if (isLayered) MaterialTheme.colorScheme.surface else Color.Transparent,
                        ) {
                            Text(
                                text = "Lộ trình",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (isLayered) FontWeight.Bold else FontWeight.Medium,
                                color = if (isLayered) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }

                        val isGraph = viewMode == RoadmapViewMode.GRAPH
                        Surface(
                            onClick = { viewMode = RoadmapViewMode.GRAPH },
                            shape = RoundedCornerShape(9.dp),
                            color = if (isGraph) MaterialTheme.colorScheme.surface else Color.Transparent,
                        ) {
                            Text(
                                text = "Sơ đồ",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (isGraph) FontWeight.Bold else FontWeight.Medium,
                                color = if (isGraph) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }
        }

        // Cảnh báo sư phạm nếu phát hiện khái niệm được nhắc trước khi định nghĩa chính thức
        if (violationItems.isNotEmpty()) {
            item {
                val firstWarn = violationItems.first()
                Surface(
                    onClick = { onJumpToSlide(firstWarn.lectureId, firstWarn.slideNumber) },
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Default.WarningAmber,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Lưu ý thứ tự khái niệm (${violationItems.size} vị trí)",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "'${firstWarn.title}' xuất hiện sớm trước khi định nghĩa chính thức tại ${firstWarn.location}. Chạm để mở slide.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }
        }

        if (tiers.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccountTree,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                        Text(
                            text = "Chưa có đồ thị khái niệm cho môn học này",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Hãy tải bài giảng kèm âm thanh ở tab Thu âm để StudyAI xây dựng cây khái niệm tiên quyết tự động.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        } else if (viewMode == RoadmapViewMode.LAYERED) {
            items(tiers) { tier ->
                OutlinedCard(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
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
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${tier.items.size} khái niệm",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        LinearProgressIndicator(
                            progress = { tier.progressPercent / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(5.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = MaterialTheme.colorScheme.secondary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            tier.items.forEach { item ->
                                Surface(
                                    onClick = { onJumpToSlide(item.lectureId, item.slideNumber) },
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(38.dp)
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
                                                fontWeight = FontWeight.Bold,
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
                                                        text = "• Tiên quyết: ${item.prerequisites.joinToString(", ")}",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.primary,
                                                        fontWeight = FontWeight.Medium,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                    )
                                                }
                                            }
                                        }

                                        Icon(
                                            imageVector = Icons.Default.ChevronRight,
                                            contentDescription = "Mở slide",
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
            item {
                var scale by remember { mutableFloatStateOf(1f) }
                var offset by remember { mutableStateOf(Offset.Zero) }

                OutlinedCard(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Sơ đồ phụ thuộc (Chạm để mở Slide)",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Chụm hai ngón hoặc kéo để thu phóng bản đồ",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(
                                onClick = {
                                    scale = 1f
                                    offset = Offset.Zero
                                },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.RestartAlt,
                                    contentDescription = "Đặt lại thu phóng",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(300.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                                .pointerInput(Unit) {
                                    detectTransformGestures { _, pan, zoom, _ ->
                                        scale = (scale * zoom).coerceIn(0.75f, 2.2f)
                                        offset += pan
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .padding(16.dp)
                                    .graphicsLayer(
                                        scaleX = scale,
                                        scaleY = scale,
                                        translationX = offset.x,
                                        translationY = offset.y,
                                    )
                            ) {
                                tiers.forEachIndexed { idx, tier ->
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        tier.items.take(3).forEach { node ->
                                            val bg = when {
                                                node.isWarning -> MaterialTheme.colorScheme.errorContainer
                                                node.mastered -> MaterialTheme.colorScheme.secondaryContainer
                                                else -> MaterialTheme.colorScheme.primaryContainer
                                            }
                                            val fg = when {
                                                node.isWarning -> MaterialTheme.colorScheme.onErrorContainer
                                                node.mastered -> MaterialTheme.colorScheme.onSecondaryContainer
                                                else -> MaterialTheme.colorScheme.onPrimaryContainer
                                            }
                                            CompactPillNode(
                                                label = "${node.shortCode}: ${node.title.take(18)}",
                                                bgColor = bg,
                                                fgColor = fg,
                                            ) {
                                                onJumpToSlide(node.lectureId, node.slideNumber)
                                            }
                                        }
                                    }
                                    if (idx < tiers.lastIndex) {
                                        Icon(
                                            imageVector = Icons.Default.ArrowDownward,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.outline,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

@Composable
private fun CompactPillNode(
    label: String,
    bgColor: Color,
    fgColor: Color,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = bgColor,
        border = BorderStroke(1.dp, fgColor.copy(alpha = 0.25f)),
        shadowElevation = 1.dp
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = fgColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
        )
    }
}
