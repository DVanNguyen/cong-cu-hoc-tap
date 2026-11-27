package com.studyai.ui.course

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.studyai.ui.theme.StudyAiExtras
import com.studyai.ui.theme.StudyAiPalette

data class CourseUiItem(
    val id: Long,
    val name: String,
    val code: String = "",
    val lecturer: String = "",
    val slideCount: Int = 0,
)

/**
 * BottomSheet quản lý & chọn môn học trong 1 Sheet duy nhất (không lồng Dialog thứ 2).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseManagerBottomSheet(
    courses: List<CourseUiItem>,
    activeCourseId: Long?,
    onDismiss: () -> Unit,
    onSelectCourse: (Long) -> Unit,
    onAddCourse: (name: String, code: String, lecturer: String) -> Unit,
    onUpdateCourse: (id: Long, name: String, code: String, lecturer: String) -> Unit,
    onDeleteCourse: (Long) -> Unit,
    chaptersCount: Int = 0,
    onOpenLecturePicker: (() -> Unit)? = null,
    isDark: Boolean = StudyAiExtras.isDark,
    onToggleDarkTheme: (() -> Unit)? = null,
    onOpenServerSettings: (() -> Unit)? = null,
) {
    var showForm by remember { mutableStateOf(false) }
    var editingCourse by remember { mutableStateOf<CourseUiItem?>(null) }
    var confirmDeleteId by remember { mutableStateOf<Long?>(null) }

    var nameInput by remember { mutableStateOf("") }
    var codeInput by remember { mutableStateOf("") }
    var lecturerInput by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Header + nút "+ Thêm môn"
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Quản lý môn học",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "Chọn, thêm mới, chỉnh sửa hoặc xóa môn",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = {
                        editingCourse = null
                        nameInput = ""
                        codeInput = ""
                        lecturerInput = ""
                        confirmDeleteId = null
                        showForm = true
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = StudyAiPalette.PrimaryCobalt,
                        contentColor = Color.White,
                    ),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Thêm môn", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }
            }

            // Form Thêm / Sửa nội tuyến
            AnimatedVisibility(visible = showForm) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = if (editingCourse == null) "Thêm môn học mới" else "Chỉnh sửa môn học",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        OutlinedTextField(
                            value = nameInput,
                            onValueChange = { nameInput = it },
                            label = { Text("Tên môn học") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = codeInput,
                                onValueChange = { codeInput = it },
                                label = { Text("Mã môn") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = lecturerInput,
                                onValueChange = { lecturerInput = it },
                                label = { Text("Giảng viên") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TextButton(onClick = { showForm = false; editingCourse = null }) {
                                Text("Hủy")
                            }
                            Spacer(Modifier.width(4.dp))
                            Button(
                                onClick = {
                                    val cleanName = nameInput.trim()
                                    if (cleanName.isNotEmpty()) {
                                        val cleanCode = codeInput.trim().ifEmpty { "NEW-101" }
                                        val cleanLec = lecturerInput.trim().ifEmpty { "Giảng viên VKU" }
                                        val editTarget = editingCourse
                                        if (editTarget != null) {
                                            onUpdateCourse(editTarget.id, cleanName, cleanCode, cleanLec)
                                        } else {
                                            onAddCourse(cleanName, cleanCode, cleanLec)
                                        }
                                        showForm = false
                                        editingCourse = null
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = StudyAiPalette.PrimaryCobalt,
                                    contentColor = Color.White,
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Lưu môn học")
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Danh sách môn học kèm nút Sửa (bút chì) + Xóa (xác nhận ngay trong thẻ)
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 340.dp)
            ) {
                items(courses, key = { it.id }) { course ->
                    val isActive = course.id == activeCourseId
                    val isConfirmDel = confirmDeleteId == course.id
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (isActive) {
                            if (isDark) StudyAiPalette.PrimarySoftDark else StudyAiPalette.PrimarySoftLight
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        },
                        border = BorderStroke(
                            1.dp,
                            if (isActive) StudyAiPalette.PrimaryCobalt else MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable {
                                            onSelectCourse(course.id)
                                            onDismiss()
                                        }
                                ) {
                                    Text(
                                        text = course.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    val metaText = buildString {
                                        if (course.code.isNotBlank()) append(course.code)
                                        if (course.lecturer.isNotBlank()) {
                                            if (isNotEmpty()) append(" • ")
                                            append(course.lecturer)
                                        }
                                        if (isEmpty()) append("${course.slideCount} slide")
                                    }
                                    Text(
                                        text = metaText,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        editingCourse = course
                                        nameInput = course.name
                                        codeInput = course.code
                                        lecturerInput = course.lecturer
                                        confirmDeleteId = null
                                        showForm = true
                                    },
                                    modifier = Modifier.size(36.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "Sửa",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                                IconButton(
                                    onClick = { confirmDeleteId = if (isConfirmDel) null else course.id },
                                    modifier = Modifier.size(36.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Xóa",
                                        tint = StudyAiPalette.Rose,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                            if (isConfirmDel) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Xác nhận xóa môn này?",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = StudyAiPalette.Rose,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        TextButton(onClick = { confirmDeleteId = null }) {
                                            Text("Hủy")
                                        }
                                        Button(
                                            onClick = {
                                                onDeleteCourse(course.id)
                                                confirmDeleteId = null
                                            },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = StudyAiPalette.Rose,
                                                contentColor = Color.White,
                                            ),
                                            shape = RoundedCornerShape(10.dp),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                        ) {
                                            Text("Xóa luôn", style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (onOpenLecturePicker != null || onToggleDarkTheme != null || onOpenServerSettings != null) {
                HorizontalDivider(
                    modifier = Modifier.padding(top = 12.dp, bottom = 10.dp),
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (onOpenLecturePicker != null) {
                        OutlinedButton(
                            onClick = onOpenLecturePicker,
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.MenuBook,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = if (chaptersCount > 0) "Bài giảng ($chaptersCount)" else "Bài giảng",
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    if (onToggleDarkTheme != null) {
                        OutlinedButton(
                            onClick = onToggleDarkTheme,
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                        ) {
                            Icon(
                                imageVector = if (isDark) Icons.Default.LightMode else Icons.Default.DarkMode,
                                contentDescription = if (isDark) "Chuyển Sáng" else "Chuyển Tối",
                                modifier = Modifier.size(15.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = if (isDark) "Sáng" else "Tối",
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                            )
                        }
                    }
                    if (onOpenServerSettings != null) {
                        OutlinedButton(
                            onClick = onOpenServerSettings,
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Dns,
                                contentDescription = "Cấu hình Server URL",
                                modifier = Modifier.size(15.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "Server",
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                            )
                        }
                    }
                }
            } else {
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}
