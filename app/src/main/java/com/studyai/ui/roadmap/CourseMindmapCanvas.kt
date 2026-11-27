package com.studyai.ui.roadmap

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.studyai.ui.theme.StudyAiExtras
import com.studyai.ui.theme.StudyAiPalette

private data class MindmapLeafPos(
    val branch: MindmapBranch,
    val leaf: MindmapLeafNode,
    val cy: Float,
)

private data class MindmapBranchPos(
    val branch: MindmapBranch,
    val cy: Float,
    val leafPositions: List<MindmapLeafPos>,
)

@Composable
fun CourseMindmapCard(
    courseName: String,
    branches: List<MindmapBranch>,
    zoomScale: Float,
    onZoomChange: (Float) -> Unit,
    onLeafClick: (MindmapBranch, MindmapLeafNode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDark = StudyAiExtras.isDark
    val borderColor = MaterialTheme.colorScheme.outline
    val surfaceColor = MaterialTheme.colorScheme.surface
    val canvasBg = MaterialTheme.colorScheme.background
    val hScroll = rememberScrollState()
    val vScroll = rememberScrollState()
    val currentZoom by rememberUpdatedState(zoomScale)
    val currentOnZoomChange by rememberUpdatedState(onZoomChange)

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = surfaceColor,
        border = BorderStroke(1.dp, borderColor),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header + Zoom controls (- / 100% / +)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Mindmap tri thức môn học",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "Tự động bổ sung nhánh khi thêm slide mới",
                        fontSize = 10.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    ZoomChip("−") { onZoomChange((zoomScale - 0.15f).coerceAtLeast(0.75f)) }
                    ZoomChip("${(zoomScale * 100).toInt()}%", mono = true) { onZoomChange(1.0f) }
                    ZoomChip("+") { onZoomChange((zoomScale + 0.15f).coerceAtMost(1.75f)) }
                }
            }

            Spacer(Modifier.height(10.dp))

            // Khung Mindmap cuộn 2 chiều & hỗ trợ chụm 2 ngón để thu phóng
            val totalRows = branches.sumOf { it.leaves.size.coerceAtLeast(1) }
            val canvasWidthDp = 500.dp
            val rowHeightDp = 54.dp
            val canvasHeightDp = (totalRows * rowHeightDp.value + 44f).dp.coerceAtLeast(280.dp)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 260.dp, max = 440.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(canvasBg)
                    .border(1.dp, borderColor, RoundedCornerShape(16.dp))
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            do {
                                val event = awaitPointerEvent()
                                val pressedCount = event.changes.count { it.pressed }
                                if (pressedCount >= 2) {
                                    val zoomDelta = event.calculateZoom()
                                    if (zoomDelta != 1f) {
                                        currentOnZoomChange((currentZoom * zoomDelta).coerceIn(0.75f, 1.75f))
                                        event.changes.forEach { it.consume() }
                                    }
                                }
                            } while (event.changes.any { it.pressed })
                        }
                    }
                    .horizontalScroll(hScroll)
                    .verticalScroll(vScroll)
                    .padding(10.dp)
            ) {
                Box(
                    modifier = Modifier.size(
                        width = canvasWidthDp * zoomScale,
                        height = canvasHeightDp * zoomScale,
                    )
                ) {
                    Box(
                        modifier = Modifier
                            .wrapContentSize(Alignment.TopStart, unbounded = true)
                            .size(width = canvasWidthDp, height = canvasHeightDp)
                            .graphicsLayer(
                                scaleX = zoomScale,
                                scaleY = zoomScale,
                                transformOrigin = TransformOrigin(0f, 0f),
                            )
                    ) {
                        val density = LocalDensity.current
                        val rootX = with(density) { 10.dp.toPx() }
                        val rootW = with(density) { 114.dp.toPx() }
                        val branchX = with(density) { 164.dp.toPx() }
                        val branchW = with(density) { 126.dp.toPx() }
                        val leafX = with(density) { 326.dp.toPx() }
                        val rowH = with(density) { rowHeightDp.toPx() }

                        var cursorY = with(density) { 26.dp.toPx() }
                        val branchPositions = branches.map { br ->
                            val lPositions = br.leaves.map { leaf ->
                                val y = cursorY + rowH / 2f
                                cursorY += rowH
                                MindmapLeafPos(br, leaf, y)
                            }
                            val brCy = if (lPositions.isNotEmpty()) {
                                (lPositions.first().cy + lPositions.last().cy) / 2f
                            } else {
                                val emptyCenter = cursorY + rowH / 2f
                                cursorY += rowH
                                emptyCenter
                            }
                            MindmapBranchPos(br, brCy, lPositions)
                        }
                        val rootCy = if (branchPositions.isNotEmpty()) {
                            (branchPositions.first().cy + branchPositions.last().cy) / 2f
                        } else {
                            with(density) { 120.dp.toPx() }
                        }

                        // Lớp 1: Đường cong Cubic Bézier kết nối 3 cấp
                        Canvas(modifier = Modifier.size(canvasWidthDp, canvasHeightDp)) {
                            branchPositions.forEach { bp ->
                                val curveColor = branchAccentColor(bp.branch.tone)
                                // Root -> Branch
                                val rStart = Offset(rootX + rootW, rootCy)
                                val bEnd = Offset(branchX, bp.cy)
                                val mid1X = (rStart.x + bEnd.x) / 2f
                                val p1 = Path().apply {
                                    moveTo(rStart.x, rStart.y)
                                    cubicTo(mid1X, rStart.y, mid1X, bEnd.y, bEnd.x, bEnd.y)
                                }
                                drawPath(
                                    path = p1,
                                    color = curveColor,
                                    style = Stroke(width = 2.6.dp.toPx(), cap = StrokeCap.Round),
                                )

                                // Branch -> Leaf
                                bp.leafPositions.forEach { lp ->
                                    val bStart = Offset(branchX + branchW, bp.cy)
                                    val lEnd = Offset(leafX, lp.cy)
                                    val mid2X = (bStart.x + lEnd.x) / 2f
                                    val p2 = Path().apply {
                                        moveTo(bStart.x, bStart.y)
                                        cubicTo(mid2X, bStart.y, mid2X, lEnd.y, lEnd.x, lEnd.y)
                                    }
                                    drawPath(
                                        path = p2,
                                        color = curveColor.copy(alpha = 0.65f),
                                        style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round),
                                    )
                                }
                            }
                        }

                        // Lớp 2: Nút Gốc (Level 0 — Root Node)
                        Box(
                            modifier = Modifier
                                .offset(x = 10.dp, y = with(density) { (rootCy - 24.dp.toPx()).toDp() })
                                .width(114.dp)
                                .height(48.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    Brush.linearGradient(
                                        listOf(StudyAiPalette.PrimaryCobalt, StudyAiPalette.Teal)
                                    )
                                )
                                .padding(horizontal = 8.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = courseName,
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }

                        // Lớp 3: Nhánh (Level 1) & Nút Lá (Level 2)
                        branchPositions.forEach { bp ->
                            val accent = branchAccentColor(bp.branch.tone)
                            val labelColor = branchTextColor(bp.branch.tone, isDark)
                            val softBg = branchSoftColor(bp.branch.tone, isDark)

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = softBg,
                                border = BorderStroke(1.2.dp, accent),
                                modifier = Modifier
                                    .offset(x = 164.dp, y = with(density) { (bp.cy - 18.dp.toPx()).toDp() })
                                    .width(126.dp)
                                    .height(36.dp),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Text(
                                        text = bp.branch.title,
                                        color = labelColor,
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f),
                                    )
                                    if (bp.branch.isNewlyAdded) {
                                        Text(
                                            text = "MỚI",
                                            color = Color.White,
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(accent)
                                                .padding(horizontal = 4.dp, vertical = 1.dp),
                                        )
                                    }
                                }
                            }

                            bp.leafPositions.forEach { lp ->
                                Surface(
                                    onClick = { onLeafClick(bp.branch, lp.leaf) },
                                    shape = RoundedCornerShape(12.dp),
                                    color = surfaceColor,
                                    border = BorderStroke(
                                        width = if (lp.leaf.isMastered) 1.2.dp else 1.dp,
                                        color = if (lp.leaf.isMastered) accent else borderColor,
                                    ),
                                    modifier = Modifier
                                        .offset(x = 326.dp, y = with(density) { (lp.cy - 21.dp.toPx()).toDp() })
                                        .width(162.dp)
                                        .height(42.dp),
                                ) {
                                    Column(
                                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                                        verticalArrangement = Arrangement.Center,
                                    ) {
                                        Text(
                                            text = if (lp.leaf.isMastered) "✓ ${lp.leaf.title}" else lp.leaf.title,
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            text = lp.leaf.slideRef,
                                            fontSize = 9.sp,
                                            fontFamily = FontFamily.Monospace,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ZoomChip(
    label: String,
    mono: Boolean = false,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier
            .height(26.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                fontSize = if (mono) 10.sp else 12.sp,
                fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

fun branchAccentColor(tone: BranchTone): Color = when (tone) {
    BranchTone.TEAL -> StudyAiPalette.Teal
    BranchTone.COBALT -> StudyAiPalette.PrimaryCobalt
    BranchTone.AMBER -> StudyAiPalette.Amber
    BranchTone.VIOLET -> StudyAiPalette.Violet
    BranchTone.ROSE -> StudyAiPalette.Rose
}

fun branchTextColor(tone: BranchTone, isDark: Boolean): Color = when (tone) {
    BranchTone.TEAL -> if (isDark) StudyAiPalette.TealTextDark else StudyAiPalette.TealTextLight
    BranchTone.COBALT -> if (isDark) StudyAiPalette.PrimaryTextDark else StudyAiPalette.PrimaryTextLight
    BranchTone.AMBER -> if (isDark) StudyAiPalette.AmberTextDark else StudyAiPalette.AmberTextLight
    BranchTone.VIOLET -> if (isDark) StudyAiPalette.VioletTextDark else StudyAiPalette.VioletTextLight
    BranchTone.ROSE -> if (isDark) StudyAiPalette.RoseTextDark else StudyAiPalette.RoseTextLight
}

fun branchSoftColor(tone: BranchTone, isDark: Boolean): Color = when (tone) {
    BranchTone.TEAL -> if (isDark) StudyAiPalette.TealSoftDark else StudyAiPalette.TealSoftLight
    BranchTone.COBALT -> if (isDark) StudyAiPalette.PrimarySoftDark else StudyAiPalette.PrimarySoftLight
    BranchTone.AMBER -> if (isDark) StudyAiPalette.AmberSoftDark else StudyAiPalette.AmberSoftLight
    BranchTone.VIOLET -> if (isDark) StudyAiPalette.VioletSoftDark else StudyAiPalette.VioletSoftLight
    BranchTone.ROSE -> if (isDark) StudyAiPalette.RoseSoftDark else StudyAiPalette.RoseSoftLight
}
