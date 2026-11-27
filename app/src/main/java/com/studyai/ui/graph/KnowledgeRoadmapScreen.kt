package com.studyai.ui.graph

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.studyai.ui.roadmap.CourseMindmapCard
import com.studyai.ui.roadmap.CourseMindmapUiState
import com.studyai.ui.roadmap.MasteryFilter
import com.studyai.ui.roadmap.MindmapBranch
import com.studyai.ui.roadmap.MindmapLeafNode
import com.studyai.ui.roadmap.branchAccentColor
import com.studyai.ui.roadmap.branchSoftColor
import com.studyai.ui.roadmap.branchTextColor
import com.studyai.ui.theme.StudyAiExtras
import com.studyai.ui.theme.StudyAiPalette

enum class RoadmapViewMode {
    MINDMAP,
    LAYERED,
}

/**
 * Màn hình 3: Lộ Trình & Mindmap Kiến Thức (StudyAI) theo đặc tả screen3_compose_design_spec.md.
 */
@Composable
fun KnowledgeRoadmapScreen(
    courseTitle: String = "Môn học",
    mindmapState: CourseMindmapUiState = CourseMindmapUiState(courseId = 1L, courseName = courseTitle),
    showSearchBar: Boolean = false,
    onCloseSearchBar: () -> Unit = {},
    onSearchQueryChange: (String) -> Unit = {},
    onFilterChange: (MasteryFilter) -> Unit = {},
    onZoomChange: (Float) -> Unit = {},
    onToggleLeafMastery: (String) -> Unit = {},
    onMarkBranchSeen: (String) -> Unit = {},
    onNavigateToLectureSlide: (lectureId: Int, slideNo: Int, timestampSec: Float) -> Unit = { _, _, _ -> },
) {
    val isDark = StudyAiExtras.isDark
    val primaryText = if (isDark) StudyAiPalette.PrimaryTextDark else StudyAiPalette.PrimaryTextLight
    val surfaceSubtle = MaterialTheme.colorScheme.surfaceVariant

    var viewMode by remember { mutableStateOf(RoadmapViewMode.MINDMAP) }
    var selectedLeafRef by remember { mutableStateOf<Pair<String, String>?>(null) }

    val filteredBranches = remember(
        mindmapState.branches,
        mindmapState.selectedFilter,
        mindmapState.searchQuery,
    ) {
        val query = mindmapState.searchQuery.trim()
        mindmapState.branches.mapNotNull { branch ->
            val branchMatches = query.isNotBlank() && branch.title.contains(query, ignoreCase = true)
            val matchedLeaves = branch.leaves.filter { leaf ->
                val masteryMatches = when (mindmapState.selectedFilter) {
                    MasteryFilter.ALL -> true
                    MasteryFilter.MASTERED -> leaf.isMastered
                    MasteryFilter.REVIEW -> !leaf.isMastered
                }
                val textMatches = query.isBlank() ||
                    branchMatches ||
                    leaf.title.contains(query, ignoreCase = true) ||
                    leaf.summary.contains(query, ignoreCase = true) ||
                    leaf.slideRef.contains(query, ignoreCase = true)
                masteryMatches && textMatches
            }
            if (matchedLeaves.isEmpty()) null else branch.copy(leaves = matchedLeaves)
        }
    }

    val openLeafDetail: (MindmapBranch, MindmapLeafNode) -> Unit = { branch, leaf ->
        if (branch.isNewlyAdded) {
            onMarkBranchSeen(branch.id)
        }
        selectedLeafRef = branch.id to leaf.id
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        AnimatedVisibility(visible = showSearchBar) {
            OutlinedTextField(
                value = mindmapState.searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = {
                    Text(
                        text = "Tìm nhánh bài giảng hoặc khái niệm...",
                        style = MaterialTheme.typography.bodySmall,
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                },
                trailingIcon = {
                    IconButton(
                        onClick = {
                            if (mindmapState.searchQuery.isNotEmpty()) {
                                onSearchQueryChange("")
                            } else {
                                onCloseSearchBar()
                            }
                        },
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Đóng tìm kiếm",
                            modifier = Modifier.size(16.dp),
                        )
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 8.dp),
            )
        }

        // Hàng 2: Segmented Control 2 chế độ (Sơ đồ Mindmap mặc định | Lộ trình phân tầng)
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = surfaceSubtle,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 8.dp),
        ) {
            Row(
                modifier = Modifier.padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                RoadmapModeSegmentButton(
                    text = "Sơ đồ Mindmap",
                    selected = viewMode == RoadmapViewMode.MINDMAP,
                    primaryTextColor = primaryText,
                    onClick = { viewMode = RoadmapViewMode.MINDMAP },
                    modifier = Modifier.weight(1f),
                )
                RoadmapModeSegmentButton(
                    text = "Lộ trình phân tầng",
                    selected = viewMode == RoadmapViewMode.LAYERED,
                    primaryTextColor = primaryText,
                    onClick = { viewMode = RoadmapViewMode.LAYERED },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // Hàng 3: 3 chip lọc gọn (KHÔNG hiển thị banner/chip cảnh báo vàng)
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
        ) {
            val filters = listOf(
                MasteryFilter.ALL to "Tất cả",
                MasteryFilter.MASTERED to "Đã vững",
                MasteryFilter.REVIEW to "Cần ôn",
            )
            items(filters, key = { it.first.name }) { (filter, label) ->
                val isSelected = mindmapState.selectedFilter == filter
                Surface(
                    onClick = { onFilterChange(filter) },
                    shape = RoundedCornerShape(999.dp),
                    color = if (isSelected) StudyAiPalette.PrimaryCobalt else MaterialTheme.colorScheme.surface,
                    border = if (isSelected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                ) {
                    Text(
                        text = label,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 13.dp, vertical = 6.dp),
                    )
                }
            }
        }

        // Nội dung chính theo chế độ đã chọn
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (filteredBranches.isEmpty()) {
                item(key = "mindmap_empty") {
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.AccountTree,
                                contentDescription = null,
                                tint = StudyAiPalette.PrimaryCobalt,
                                modifier = Modifier.size(32.dp),
                            )
                            Text(
                                text = if (mindmapState.branches.isEmpty()) {
                                    "Chưa có Mindmap kiến thức cho môn học này"
                                } else {
                                    "Không có khái niệm phù hợp bộ lọc"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center,
                            )
                            Text(
                                text = if (mindmapState.branches.isEmpty()) {
                                    "Hãy bấm \"+ Thêm slide\" hoặc phân tích bài giảng ở tab Thu âm để StudyAI tự động dựng cây Mindmap 3 cấp."
                                } else {
                                    "Chạm \"Tất cả\" hoặc xóa từ khóa tìm kiếm để xem lại toàn bộ nhánh bài giảng."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            } else if (viewMode == RoadmapViewMode.MINDMAP) {
                item(key = "mindmap_canvas_card") {
                    CourseMindmapCard(
                        courseName = mindmapState.courseName.ifBlank { courseTitle },
                        branches = filteredBranches,
                        zoomScale = mindmapState.zoomScale,
                        onZoomChange = onZoomChange,
                        onLeafClick = openLeafDetail,
                    )
                }
            } else {
                items(filteredBranches, key = { it.id }) { branch ->
                    LayeredBranchCard(
                        branch = branch,
                        isDark = isDark,
                        onLeafClick = { leaf -> openLeafDetail(branch, leaf) },
                    )
                }
            }

            item(key = "roadmap_bottom_spacer") {
                Spacer(Modifier.height(12.dp))
            }
        }
    }

    selectedLeafRef?.let { (branchId, leafId) ->
        val liveBranch = mindmapState.branches.find { it.id == branchId }
        val liveLeaf = liveBranch?.leaves?.find { it.id == leafId }
        if (liveBranch != null && liveLeaf != null) {
            MindmapNodeDetailSheet(
                branch = liveBranch,
                leaf = liveLeaf,
                isDark = isDark,
                onDismiss = { selectedLeafRef = null },
                onToggleMastery = { onToggleLeafMastery(liveLeaf.id) },
                onJumpToSlide = {
                    selectedLeafRef = null
                    onNavigateToLectureSlide(
                        liveLeaf.lectureId.toInt(),
                        liveLeaf.slideNumber,
                        liveLeaf.timestampSec.toFloat(),
                    )
                },
            )
        }
    }
}

@Composable
private fun LayeredBranchCard(
    branch: MindmapBranch,
    isDark: Boolean,
    onLeafClick: (MindmapLeafNode) -> Unit,
) {
    val accent = branchAccentColor(branch.tone)
    val badgeText = branchTextColor(branch.tone, isDark)
    val softBg = branchSoftColor(branch.tone, isDark)
    val masteredCount = branch.leaves.count { it.isMastered }
    val totalCount = branch.leaves.size.coerceAtLeast(1)

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = softBg,
                        border = BorderStroke(1.dp, accent.copy(alpha = 0.6f)),
                    ) {
                        Text(
                            text = branch.title,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = badgeText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                    if (branch.isNewlyAdded) {
                        Text(
                            text = "MỚI",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(accent)
                                .padding(horizontal = 5.dp, vertical = 2.dp),
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "$masteredCount/$totalCount vững",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            LinearProgressIndicator(
                progress = { (masteredCount.toFloat() / totalCount.toFloat()).coerceIn(0f, 1f) },
                color = accent,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(999.dp)),
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                branch.leaves.forEach { leaf ->
                    Surface(
                        onClick = { onLeafClick(leaf) },
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        border = BorderStroke(
                            width = 1.dp,
                            color = if (leaf.isMastered) accent.copy(alpha = 0.65f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (leaf.isMastered) softBg else MaterialTheme.colorScheme.surface),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (leaf.isMastered) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Đã vững",
                                        tint = badgeText,
                                        modifier = Modifier.size(18.dp),
                                    )
                                } else {
                                    Text(
                                        text = "S${leaf.slideNumber}",
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = badgeText,
                                    )
                                }
                            }

                            Spacer(Modifier.width(10.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = leaf.title,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = buildString {
                                        append(leaf.slideRef)
                                        if (leaf.summary.isNotBlank() && leaf.summary != leaf.title) {
                                            append(" • ")
                                            append(leaf.summary)
                                        }
                                    },
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }

                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = "Chi tiết",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MindmapNodeDetailSheet(
    branch: MindmapBranch,
    leaf: MindmapLeafNode,
    isDark: Boolean,
    onDismiss: () -> Unit,
    onToggleMastery: () -> Unit,
    onJumpToSlide: () -> Unit,
) {
    val accent = branchAccentColor(branch.tone)
    val badgeFg = branchTextColor(branch.tone, isDark)
    val badgeBg = branchSoftColor(branch.tone, isDark)
    val tealSoft = if (isDark) StudyAiPalette.TealSoftDark else StudyAiPalette.TealSoftLight
    val tealText = if (isDark) StudyAiPalette.TealTextDark else StudyAiPalette.TealTextLight

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = badgeBg,
                    border = BorderStroke(1.dp, accent.copy(alpha = 0.6f)),
                ) {
                    Text(
                        text = branch.title,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = badgeFg,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }

                if (leaf.isMastered) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = tealSoft,
                    ) {
                        Text(
                            text = "Đã vững ✓",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = tealText,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = leaf.title,
                    fontSize = 17.sp,
                    lineHeight = 23.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                val locationMeta = buildString {
                    if (leaf.lectureId > 0L) {
                        append("Bài ${leaf.lectureId} • ")
                    }
                    append(leaf.slideRef)
                    if (leaf.timestampSec > 0) {
                        val m = leaf.timestampSec / 60
                        val s = leaf.timestampSec % 60
                        append(" • ▶ %02d:%02d".format(m, s))
                    }
                }
                Text(
                    text = locationMeta,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = leaf.summary.ifBlank { "Khái niệm nằm trong ${leaf.slideRef} thuộc nhánh ${branch.title}." },
                    style = MaterialTheme.typography.bodySmall,
                    lineHeight = 20.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(14.dp),
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(
                    onClick = onToggleMastery,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(
                        1.dp,
                        if (leaf.isMastered) StudyAiPalette.Teal else MaterialTheme.colorScheme.outline,
                    ),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (leaf.isMastered) tealSoft else Color.Transparent,
                        contentColor = if (leaf.isMastered) tealText else MaterialTheme.colorScheme.onSurface,
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp),
                ) {
                    Text(
                        text = if (leaf.isMastered) "Đã vững ✓" else "Đánh dấu đã vững",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                    )
                }

                Button(
                    onClick = onJumpToSlide,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = StudyAiPalette.PrimaryCobalt,
                        contentColor = Color.White,
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "Nhảy tới Slide",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun RoadmapModeSegmentButton(
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
