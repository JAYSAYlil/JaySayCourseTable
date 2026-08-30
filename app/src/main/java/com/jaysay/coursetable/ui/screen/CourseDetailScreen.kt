package com.jaysay.coursetable.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jaysay.coursetable.R
import com.jaysay.coursetable.data.model.Course
import com.jaysay.coursetable.ui.components.AppPanel
import com.jaysay.coursetable.ui.components.AppTopBar
import com.jaysay.coursetable.ui.theme.*
import com.jaysay.coursetable.util.TimeUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseDetailScreen(
    course: Course,
    allCourses: List<Course> = listOf(course),
    onClose: () -> Unit,
    onEdit: ((Course) -> Unit)? = null,
    onDelete: (() -> Unit)? = null
) {
    BackHandler(onBack = onClose)
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.4f
    // 与课表网格共用同一配色映射，保证同一课程在两处的颜色一致。
    val courseColor = remember(course, allCourses, dark) { resolveCourseColor(allCourses, course, dark) }
    val headerTextColors = remember(courseColor, dark) { courseCardTextColors(courseColor, dark) }

    Scaffold(
        topBar = {
            AppTopBar(
                title = stringResource(R.string.detail_title),
                navigationIcon = {
                    IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.detail_back)) }
                },
                actions = {
                    if (onEdit != null) {
                        IconButton(onClick = { onEdit(course) }) { Icon(Icons.Default.Edit, stringResource(R.string.detail_edit)) }
                    }
                    if (onDelete != null) {
                        IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, stringResource(R.string.detail_delete), tint = MaterialTheme.colorScheme.error) }
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
        ) {
            // Course header card
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                shape = AppShapes.card,
                color = courseColor.copy(alpha = if (dark) 0.22f else 0.15f),
                border = BorderStroke(0.75.dp, courseCardBorderColor(courseColor, dark))
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    // Course name
                    Text(
                        text = course.courseName,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = headerTextColors.first
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // Key info chips
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        InfoChip(
                            icon = Icons.Default.Schedule,
                            text = stringResource(R.string.detail_schedule_chip, TimeUtils.getDayName(course.dayOfWeek), course.startPeriod, course.endPeriod)
                        )
                        InfoChip(
                            icon = Icons.Default.School,
                            text = stringResource(R.string.detail_credits_chip, course.credits)
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
                SectionTitle(stringResource(R.string.detail_section_basic))
                DetailCard {
                    DetailRow(stringResource(R.string.detail_course_id), course.courseId)
                    DetailRow(stringResource(R.string.detail_class_number), course.classNumber)
                    DetailRow(stringResource(R.string.detail_department), course.department)
                    DetailRow(stringResource(R.string.detail_course_category), course.courseCategory)
                }

                Spacer(modifier = Modifier.height(16.dp))

                SectionTitle(stringResource(R.string.detail_section_class))
                DetailCard {
                    DetailRow(stringResource(R.string.detail_teacher), course.teacher)
                    DetailRow(stringResource(R.string.detail_classroom), course.classroom)
                    DetailRow(
                        stringResource(R.string.detail_class_time),
                        stringResource(R.string.detail_class_time_value, TimeUtils.getDayName(course.dayOfWeek), TimeUtils.formatPeriodRange(course.startPeriod, course.endPeriod))
                    )
                    DetailRow(stringResource(R.string.detail_weeks), TimeUtils.formatWeeks(course.weeks))
                    if (course.isOnline) {
                        DetailRow(stringResource(R.string.detail_teaching_method), stringResource(R.string.detail_online_teaching))
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                SectionTitle(stringResource(R.string.detail_section_assessment))
                DetailCard {
                    DetailRow(stringResource(R.string.detail_assessment_method), course.assessmentMethod)
                }

                if (course.notes.isNotBlank()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    SectionTitle(stringResource(R.string.detail_notes))
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
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
            thickness = 0.5.dp
        )
    }
}

@Composable
private fun InfoChip(icon: ImageVector, text: String) {
    Surface(
        shape = AppShapes.panel,
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
