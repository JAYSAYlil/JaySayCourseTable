package com.jaysay.coursetable.ui.screen
import com.jaysay.coursetable.ui.components.AppIconButton as IconButton

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.animation.core.Animatable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.offset
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import com.jaysay.coursetable.ui.components.HeroRegistry
import com.jaysay.coursetable.util.TimeUtils

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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
    // 与课表网格共用同一“最终色”映射算法，保证同一课程在两处的颜色一致。
    val courseColorMap = remember(allCourses, dark) { buildResolvedCourseColorMap(allCourses, dark) }
    val courseColor = courseColorMap[course.uniqueKey]
        ?: courseColorMap[course.courseName]
        ?: coursePalette(dark).first()
    val headerTextColors = remember(courseColor, dark) { courseCardTextColors(courseColor, dark) }
    val density = LocalDensity.current
    val currentOnClose by rememberUpdatedState(onClose)
    // 阈值用 dp 表达（跨密度一致），数值对齐 v3.4.27 的手感：240px / 800px·s⁻¹ 在 2.75x 密度下
    // 约等于 88dp / 300dp·s⁻¹。之前写成 120dp / 800dp·s⁻¹，等于行程 1.4 倍、甩动门槛 3 倍，明显更难关闭。
    val dismissDistance = with(density) { 88.dp.toPx() }
    val dismissVelocity = with(density) { 300.dp.toPx() }
    var dismissRaw by remember { mutableFloatStateOf(0f) }
    var dismissDragging by remember { mutableStateOf(false) }
    val returnOffset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var returnJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    val velocityTracker = remember { androidx.compose.ui.input.pointer.util.VelocityTracker() }
    val dismissOffset = if (dismissDragging) dismissRaw else returnOffset.value
    fun returnToRest(velocity: Float = 0f) {
        returnJob?.cancel()
        returnJob = scope.launch {
            returnOffset.snapTo(dismissRaw)
            dismissDragging = false
            returnOffset.animateTo(0f, Motion.interactive(), initialVelocity = velocity)
        }
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = stringResource(R.string.detail_title),
                navigationIcon = {
                    IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.detail_back)) }
                },
                actions = {
                    if (onEdit != null) {
                        IconButton(onClick = { onEdit(course) }) { Icon(Icons.Rounded.Edit, stringResource(R.string.detail_edit)) }
                    }
                    if (onDelete != null) {
                        IconButton(onClick = onDelete) { Icon(Icons.Rounded.Delete, stringResource(R.string.detail_delete), tint = MaterialTheme.colorScheme.error) }
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .offset { androidx.compose.ui.unit.IntOffset(0, dismissOffset.roundToInt()) }
                .verticalScroll(rememberScrollState())
        ) {
            // Track the finger directly; animate only the return after release.
            // Hero 转场详情端：头部完成测量后回写 bounds，正向飞行的终点由此获得。
            Surface(
                modifier = Modifier
                    .onGloballyPositioned { coords ->
                        HeroRegistry.headerBounds = coords.boundsInRoot()
                    }
                    // 只以阈值为 key：父级每次重组都会新建 onClose，把它当 key 会在拖动中途重建
                    // 手势处理器（且不会回调 onDragCancel），导致内容跳回或卡在拖到一半的位置。
                    .pointerInput(dismissDistance, dismissVelocity) {
                        detectVerticalDragGestures(
                            onDragStart = {
                                returnJob?.cancel()
                                dismissRaw = returnOffset.value
                                dismissDragging = true
                                velocityTracker.resetTracking()
                            },
                            onVerticalDrag = { change, dragAmount ->
                                change.consume()
                                // The content itself moves: track in the stationary parent space.
                                velocityTracker.addPosition(change.uptimeMillis,
                                    change.position + androidx.compose.ui.geometry.Offset(0f, dismissRaw))
                                dismissRaw = (dismissRaw + dragAmount).coerceAtLeast(0f)
                            },
                            onDragEnd = {
                                val velocity = velocityTracker.calculateVelocity().y
                                velocityTracker.resetTracking()
                                if (dismissRaw > dismissDistance || (dismissRaw > 0f && velocity > dismissVelocity)) {
                                    currentOnClose()
                                } else returnToRest(velocity)
                            },
                            onDragCancel = {
                                velocityTracker.resetTracking()
                                returnToRest()
                            }
                        )
                    }
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
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        InfoChip(
                            icon = Icons.Rounded.Schedule,
                            text = stringResource(R.string.detail_schedule_chip, TimeUtils.getDayName(course.dayOfWeek), course.startPeriod, course.endPeriod)
                        )
                        InfoChip(
                            icon = Icons.Rounded.School,
                            text = stringResource(R.string.detail_credits_chip, course.credits)
                        )
                    }
                    if (course.courseType.isNotBlank() || course.assessmentMethod.isNotBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (course.courseType.isNotBlank()) {
                                InfoChip(
                                    icon = Icons.Rounded.Bookmark,
                                    text = course.courseType
                                )
                            }
                            if (course.assessmentMethod.isNotBlank()) InfoChip(
                                icon = Icons.Rounded.Assessment,
                                text = course.assessmentMethod
                            )
                        }
                    }
                }
            }

            // Detail sections
            Column(
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                if (course.courseId.isNotBlank() || course.classNumber.isNotBlank() ||
                    course.department.isNotBlank() || course.courseCategory.isNotBlank()) {
                    SectionTitle(stringResource(R.string.detail_section_basic))
                    DetailCard {
                        DetailRow(stringResource(R.string.detail_course_id), course.courseId)
                        DetailRow(stringResource(R.string.detail_class_number), course.classNumber)
                        DetailRow(stringResource(R.string.detail_department), course.department)
                        DetailRow(stringResource(R.string.detail_course_category), course.courseCategory)
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

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

                if (course.assessmentMethod.isNotBlank()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    SectionTitle(stringResource(R.string.detail_section_assessment))
                    DetailCard {
                        DetailRow(stringResource(R.string.detail_assessment_method), course.assessmentMethod)
                    }
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
        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.65f),
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
