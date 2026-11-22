package com.studyai.ui.graph

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.filled.ZoomInMap
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.hypot

data class ConceptNodeUi(
    val id: Long,
    val canonicalNameVi: String,
    val canonicalNameEn: String,
    val aliases: List<String>,
    val firstIntroducedLectureId: Int,
    val firstIntroducedSlideNo: Int,
    val definedOrderIndex: Int,
    val definitionSummary: String,
    val layerY: Float, // [0.0 .. 1.0] từ thuật toán Sugiyama
    val orderX: Float, // [0.0 .. 1.0]
    val hasViolation: Boolean
)

data class ConceptEdgeUi(
    val sourceConceptId: Long,
    val targetConceptId: Long,
    val relationType: String, // "PREREQUISITE_OF" | "EXTENDS" | "APPLIED_IN"
    val weight: Float,
    val evidenceQuote: String,
    val isUsedBeforeDefinedViolation: Boolean
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KnowledgeGraphScreen(
    courseTitle: String,
    nodes: List<ConceptNodeUi>,
    edges: List<ConceptEdgeUi>,
    onJumpToSlide: (lectureId: Int, slideNo: Int) -> Unit
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var selectedNode by remember { mutableStateOf<ConceptNodeUi?>(null) }
    val textMeasurer = rememberTextMeasurer()
    val violationEdges = remember(edges) { edges.filter { it.isUsedBeforeDefinedViolation } }
    val nodeMap = remember(nodes) { nodes.associateBy { it.id } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Đồ thị Tri thức: $courseTitle", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        Text(
                            "${nodes.size} khái niệm chuẩn hóa • ${edges.size} liên kết DAG",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {
                        scale = 1f
                        offset = Offset.Zero
                    }) {
                        Icon(Icons.Default.ZoomInMap, contentDescription = "Reset Zoom")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // 1. Thẻ Cảnh báo Đỏ nếu phát hiện khái niệm Dùng trước khi được Định nghĩa (Used-Before-Defined)
            if (violationEdges.isNotEmpty()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.WarningAmber,
                                contentDescription = null,
                                tint = Color(0xFFC62828)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Phát hiện ${violationEdges.size} khái niệm dùng trước khi định nghĩa!",
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFC62828),
                                style = MaterialTheme.typography.titleSmall
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        violationEdges.take(2).forEach { edge ->
                            val prereq = nodeMap[edge.sourceConceptId]
                            val target = nodeMap[edge.targetConceptId]
                            if (prereq != null && target != null) {
                                Text(
                                    text = "• '${prereq.canonicalNameVi} (${prereq.canonicalNameEn})' được dùng để giảng '${target.canonicalNameVi}' trước khi được định nghĩa chính thức tại Bài ${prereq.firstIntroducedLectureId}, Slide ${prereq.firstIntroducedSlideNo}.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFFB71C1C),
                                    modifier = Modifier
                                        .clickable {
                                            onJumpToSlide(prereq.firstIntroducedLectureId, prereq.firstIntroducedSlideNo)
                                        }
                                        .padding(vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }

            // 2. Khung vẽ Đồ thị DAG Tương tác (Zoom, Pan, Tap Node)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color(0xFFF8FAFC))
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                scale = (scale * zoom).coerceIn(0.6f, 2.8f)
                                offset += pan
                            }
                        }
                        .pointerInput(nodes, scale, offset) {
                            detectTapGestures { tapOffset ->
                                val w = size.width.toFloat()
                                val h = size.height.toFloat()
                                val hit = nodes.firstOrNull { n ->
                                    val cx = (n.orderX * w * 0.85f + w * 0.075f) * scale + offset.x
                                    val cy = (n.layerY * h * 0.78f + h * 0.1f) * scale + offset.y
                                    hypot(tapOffset.x - cx, tapOffset.y - cy) <= 42f * scale
                                }
                                selectedNode = hit
                            }
                        }
                ) {
                    val w = size.width
                    val h = size.height

                    fun nodeScreenPos(n: ConceptNodeUi): Offset {
                        val x = (n.orderX * w * 0.85f + w * 0.075f) * scale + offset.x
                        val y = (n.layerY * h * 0.78f + h * 0.1f) * scale + offset.y
                        return Offset(x, y)
                    }

                    // Vẽ các cạnh quan hệ (PREREQUISITE_OF, EXTENDS, APPLIED_IN)
                    edges.forEach { e ->
                        val src = nodeMap[e.sourceConceptId] ?: return@forEach
                        val dst = nodeMap[e.targetConceptId] ?: return@forEach
                        val p1 = nodeScreenPos(src)
                        val p2 = nodeScreenPos(dst)

                        val edgeColor = when {
                            e.isUsedBeforeDefinedViolation -> Color(0xFFD32F2F)
                            e.relationType == "PREREQUISITE_OF" -> Color(0xFF1565C0)
                            e.relationType == "EXTENDS" -> Color(0xFF2E7D32)
                            else -> Color(0xFF6A1B9A)
                        }

                        val path = Path().apply {
                            moveTo(p1.x, p1.y)
                            val midY = (p1.y + p2.y) / 2f
                            cubicTo(p1.x, midY, p2.x, midY, p2.x, p2.y)
                        }

                        drawPath(
                            path = path,
                            color = edgeColor,
                            style = Stroke(
                                width = if (e.isUsedBeforeDefinedViolation) 6f else 4f,
                                pathEffect = if (e.isUsedBeforeDefinedViolation)
                                    PathEffect.dashPathEffect(floatArrayOf(16f, 10f), 0f)
                                else null
                            )
                        )
                    }

                    // Vẽ các nút Khái niệm
                    nodes.forEach { n ->
                        val center = nodeScreenPos(n)
                        val radius = 32f * scale
                        val fillColor = if (n.hasViolation) Color(0xFFFFCDD2) else Color(0xFFE3F2FD)
                        val strokeColor = if (n.hasViolation) Color(0xFFC62828) else Color(0xFF1565C0)

                        drawCircle(color = fillColor, radius = radius, center = center)
                        drawCircle(
                            color = strokeColor,
                            radius = radius,
                            center = center,
                            style = Stroke(width = 4f)
                        )

                        val labelLayout = textMeasurer.measure(
                            text = n.canonicalNameVi,
                            style = TextStyle(
                                fontSize = (11 * scale).coerceIn(9f, 16f).sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0F172A)
                            )
                        )
                        drawText(
                            textLayoutResult = labelLayout,
                            topLeft = Offset(
                                center.x - labelLayout.size.width / 2f,
                                center.y + radius + 4f
                            )
                        )
                    }
                }
            }

            // 3. Chi tiết Khái niệm & Bằng chứng khi người dùng bấm chọn Nút
            selectedNode?.let { node ->
                val relatedEdges = edges.filter {
                    it.sourceConceptId == node.id || it.targetConceptId == node.id
                }

                Surface(
                    tonalElevation = 8.dp,
                    shadowElevation = 10.dp,
                    shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${node.canonicalNameVi} (${node.canonicalNameEn})",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Tên gọi khác (Aliases): ${node.aliases.joinToString(", ")}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            FilledTonalButton(
                                onClick = {
                                    onJumpToSlide(node.firstIntroducedLectureId, node.firstIntroducedSlideNo)
                                }
                            ) {
                                Text("Bài ${node.firstIntroducedLectureId} • Slide ${node.firstIntroducedSlideNo}")
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = node.definitionSummary,
                            style = MaterialTheme.typography.bodySmall
                        )

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Bằng chứng liên kết (${relatedEdges.size}):",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )

                        LazyColumn(modifier = Modifier.heightIn(max = 120.dp)) {
                            items(relatedEdges) { edge ->
                                val other = if (edge.sourceConceptId == node.id)
                                    nodeMap[edge.targetConceptId]?.canonicalNameVi
                                else
                                    nodeMap[edge.sourceConceptId]?.canonicalNameVi
                                Text(
                                    text = "• [${edge.relationType} ↔ $other]: \"${edge.evidenceQuote}\"",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (edge.isUsedBeforeDefinedViolation) Color(0xFFC62828) else Color.Unspecified,
                                    modifier = Modifier.padding(vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
