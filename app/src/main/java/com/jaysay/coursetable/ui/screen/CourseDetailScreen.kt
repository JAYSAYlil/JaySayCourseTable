package com.jaysay.coursetable.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jaysay.coursetable.data.model.Course
import com.jaysay.coursetable.ui.components.AppPanel
import com.jaysay.coursetable.ui.components.AppTopBar
import com.jaysay.coursetable.ui.theme.*
import com.jaysay.coursetable.util.TimeUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseDetailScreen(course: Course, onClose: () -> Unit, onEdit: ((Course) -> Unit)? = null, onDelete: (() -> Unit)? = null) {
    BackHandler(onBack = onClose)
    val dark = MaterialTheme.colorScheme.background == DarkBackground
    val colors = if (dark) DarkCourseColors else CourseColors
    val cc = course.customColor
    val courseColor = when {
        cc != null && cc in 0..14 -> colors[cc]
        cc != null -> Color(cc)
        // 避免 Math.abs(Int.MIN_VALUE) 仍为负数导致数组越界
        else -> colors[course.courseName.hashCode().and(Int.MAX_VALUE) % colors.size]
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = "课程详情",
                navigationIcon = {
                    IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
                },
                actions = {
                    if (onEdit != null) {
                        IconButton(onClick = { onEdit(course) }) { Icon(Icons.Default.Edit, "编辑") }
                    }
                    if (onDelete != null) {
                        IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "删除", tint = MaterialTheme.colorScheme.error) }
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
        ) {
            // Course header card
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                shape = RoundedCornerShape(18.dp),
                color = courseColor.copy(alpha = if (dark) 0.22f else 0.15f),
                border = BorderStroke(0.9.dp, courseColor.copy(alpha = if (dark) 0.66f else 0.48f))
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    // Course name
                    Text(
                        text = course.courseName,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // Key info chips
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        InfoChip(
                            icon = Icons.Default.Schedule,
                            text = "${TimeUtils.getDayName(course.dayOfWeek)} ${course.startPeriod}-${course.endPeriod}节"
                        )
                        InfoChip(
                            icon = Icons.Default.School,
                            text = "${course.credits}学分"
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (course.courseType.isNotBlank()) {
                            InfoChip(
                                icon = Icons.Default.Bookmark,
                                text = course.courseType
                            )
                        }
                        InfoChip(
                            icon = Icons.Default.Assessment,
                            text = course.assessmentMethod
                        )
                    }
                }
            }

            // Detail sections
            Column(
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                SectionTitle("基本信息")
                DetailCard {
                    DetailRow("课程号", course.courseId)
                    DetailRow("课序号", course.classNumber)
                    DetailRow("开课单位", course.department)
                    DetailRow("课程类别", course.courseCategory)
                }

                Spacer(modifier = Modifier.height(16.dp))

                SectionTitle("上课信息")
                DetailCard {
                    DetailRow("上课教师", course.teacher)
                    DetailRow("教室名称", course.classroom)
                    DetailRow(
                        "上课时间",
                        "${TimeUtils.getDayName(course.dayOfWeek)} ${TimeUtils.formatPeriodRange(course.startPeriod, course.endPeriod)}"
                    )
                    DetailRow("上课周次", TimeUtils.formatWeeks(course.weeks))
                    if (course.isOnline) {
                        DetailRow("教学方式", "线上教学")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                SectionTitle("考核信息")
                DetailCard {
                    DetailRow("考核方式", course.assessmentMethod)
                }

                if (course.notes.isNotBlank()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    SectionTitle("备注")
                    DetailCard {
                        Text(
                            text = course.notes,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

@Composable
private fun DetailCard(content: @Composable ColumnScope.() -> Unit) {
    AppPanel {
        Column(
            modifier = Modifier.padding(16.dp),
            content = content
        )
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    if (value.isNotBlank()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.Top
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(80.dp)
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
        }
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
            thickness = 0.5.dp
        )
    }
}

@Composable
private fun InfoChip(icon: ImageVector, text: String) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f),
        border = BorderStroke(
            0.6.dp,
            MaterialTheme.colorScheme.primary.copy(alpha = 0.32f)
        ),
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
