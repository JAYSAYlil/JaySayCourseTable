package com.jaysay.coursetable.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.mapSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.jaysay.coursetable.R
import com.jaysay.coursetable.data.model.Course
import com.jaysay.coursetable.data.model.CourseReminderMode
import com.jaysay.coursetable.ui.theme.*
import com.jaysay.coursetable.util.TimeUtils
import java.util.UUID

/**
 * 课程编辑的保存范围：
 * - [CURRENT_WEEK] 只改被编辑的这一周（默认）；
 * - [FROM_CURRENT_WEEK] 改被编辑的这一周以及其后的所有周次；
 * - [ALL_WEEKS] 整门课程的全部周次一起改。
 * 三者互斥：界面上的两个开关打开一个，另一个自动关闭。
 */
enum class CourseEditScope { CURRENT_WEEK, FROM_CURRENT_WEEK, ALL_WEEKS }

/**
 * 课程编辑表单的完整快照。整个表单只占一个 rememberSaveable 槽位，
 * Activity 重建（旋转/进程回收）后已填写的所有字段原样恢复；
 * reminderMode 存枚举名，reminderMinutesOverride 用 -1 表示“跟随全局”。
 */
private data class CourseEditorForm(
    val stableSeriesId: String,
    val name: String,
    val teacher: String,
    val classroom: String,
    val day: Int,
    val startPeriod: Int,
    val endPeriod: Int,
    val startText: String,
    val endText: String,
    val weekStr: String,
    val creditsStr: String,
    val courseType: String,
    val courseCategory: String,
    val department: String,
    val courseId: String,
    val classNumber: String,
    val isOnline: Boolean,
    val assessmentMethod: String,
    val notes: String,
    val colorIndex: Int,
    val reminderModeName: String,
    val reminderMinutesOverrideValue: Int,
    val endReminderEnabled: Boolean,
    val scopeName: String
) {
    val reminderMode: CourseReminderMode
        get() = runCatching { CourseReminderMode.valueOf(reminderModeName) }
            .getOrDefault(CourseReminderMode.INHERIT)
    val reminderMinutesOverride: Int?
        get() = reminderMinutesOverrideValue.takeIf { it >= 0 }

    /** 保存范围：默认只改被编辑的这一周（见 [CourseEditScope]）。 */
    val scope: CourseEditScope
        get() = runCatching { CourseEditScope.valueOf(scopeName) }.getOrDefault(CourseEditScope.CURRENT_WEEK)

    companion object {
        const val NO_OVERRIDE = -1

        fun from(
            course: Course?,
            initialDay: Int,
            initialStartPeriod: Int,
            maxPeriods: Int,
            totalWeeks: Int
        ): CourseEditorForm {
            val start = course?.startPeriod ?: initialStartPeriod.coerceIn(1, maxPeriods.coerceAtLeast(1))
            return CourseEditorForm(
                stableSeriesId = course?.seriesKey ?: UUID.randomUUID().toString(),
                name = course?.courseName ?: "",
                teacher = course?.teacher ?: "",
                classroom = course?.classroom ?: "",
                day = course?.dayOfWeek ?: initialDay.coerceIn(1, 7),
                startPeriod = start,
                endPeriod = course?.endPeriod ?: (start + 1).coerceAtMost(maxPeriods.coerceAtLeast(1)),
                startText = start.toString(),
                endText = (course?.endPeriod ?: (start + 1).coerceAtMost(maxPeriods.coerceAtLeast(1))).toString(),
                weekStr = course?.let { TimeUtils.formatWeeks(it.weeks) } ?: "1-$totalWeeks",
                creditsStr = course?.credits?.let {
                    if (it == it.toLong().toFloat()) it.toLong().toString() else it.toString()
                } ?: "0",
                courseType = course?.courseType ?: "",
                courseCategory = course?.courseCategory ?: "",
                department = course?.department ?: "",
                courseId = course?.courseId ?: "",
                classNumber = course?.classNumber ?: "",
                isOnline = course?.isOnline ?: false,
                assessmentMethod = course?.assessmentMethod ?: "",
                notes = course?.notes ?: "",
                colorIndex = course?.customColor?.takeIf { it in CourseColors.indices } ?: -1,
                reminderModeName = (course?.reminderMode ?: CourseReminderMode.INHERIT).name,
                reminderMinutesOverrideValue = course?.reminderMinutesOverride ?: NO_OVERRIDE,
                endReminderEnabled = course?.endReminderEnabled ?: false,
                scopeName = CourseEditScope.CURRENT_WEEK.name
            )
        }

        private const val KEY_SERIES_ID = "seriesId"
        private const val KEY_NAME = "name"
        private const val KEY_TEACHER = "teacher"
        private const val KEY_CLASSROOM = "classroom"
        private const val KEY_DAY = "day"
        private const val KEY_START_PERIOD = "startPeriod"
        private const val KEY_END_PERIOD = "endPeriod"
        private const val KEY_START_TEXT = "startText"
        private const val KEY_END_TEXT = "endText"
        private const val KEY_WEEKS = "weeks"
        private const val KEY_CREDITS = "credits"
        private const val KEY_COURSE_TYPE = "courseType"
        private const val KEY_CATEGORY = "category"
        private const val KEY_DEPARTMENT = "department"
        private const val KEY_COURSE_ID = "courseId"
        private const val KEY_CLASS_NUMBER = "classNumber"
        private const val KEY_IS_ONLINE = "isOnline"
        private const val KEY_ASSESSMENT = "assessment"
        private const val KEY_NOTES = "notes"
        private const val KEY_COLOR_INDEX = "colorIndex"
        private const val KEY_REMINDER_MODE = "reminderMode"
        private const val KEY_REMINDER_MINUTES = "reminderMinutes"
        private const val KEY_END_REMINDER = "endReminder"
        private const val KEY_SCOPE = "scope"

        private const val FALLBACK_START_PERIOD = 1
        private const val FALLBACK_END_PERIOD = 2
        private const val FALLBACK_WEEK_RANGE = 20

        /**
         * mapSaver 允许空值；恢复阶段对每个字段给出与初始值一致的兜底，
         * 保证系统在极端情况下丢弃某个键时表单仍然可用。
         */
        val saver = mapSaver(
            save = { form ->
                mapOf(
                    KEY_SERIES_ID to form.stableSeriesId,
                    KEY_NAME to form.name,
                    KEY_TEACHER to form.teacher,
                    KEY_CLASSROOM to form.classroom,
                    KEY_DAY to form.day,
                    KEY_START_PERIOD to form.startPeriod,
                    KEY_END_PERIOD to form.endPeriod,
                    KEY_START_TEXT to form.startText,
                    KEY_END_TEXT to form.endText,
                    KEY_WEEKS to form.weekStr,
                    KEY_CREDITS to form.creditsStr,
                    KEY_COURSE_TYPE to form.courseType,
                    KEY_CATEGORY to form.courseCategory,
                    KEY_DEPARTMENT to form.department,
                    KEY_COURSE_ID to form.courseId,
                    KEY_CLASS_NUMBER to form.classNumber,
                    KEY_IS_ONLINE to form.isOnline,
                    KEY_ASSESSMENT to form.assessmentMethod,
                    KEY_NOTES to form.notes,
                    KEY_COLOR_INDEX to form.colorIndex,
                    KEY_REMINDER_MODE to form.reminderModeName,
                    KEY_REMINDER_MINUTES to form.reminderMinutesOverrideValue,
                    KEY_END_REMINDER to form.endReminderEnabled,
                    KEY_SCOPE to form.scopeName
                )
            },
            restore = { saved ->
                CourseEditorForm(
                    stableSeriesId = saved[KEY_SERIES_ID] as? String ?: UUID.randomUUID().toString(),
                    name = saved[KEY_NAME] as? String ?: "",
                    teacher = saved[KEY_TEACHER] as? String ?: "",
                    classroom = saved[KEY_CLASSROOM] as? String ?: "",
                    day = (saved[KEY_DAY] as? Number)?.toInt()?.coerceIn(1, 7) ?: 1,
                    startPeriod = (saved[KEY_START_PERIOD] as? Number)?.toInt()
                        ?.coerceIn(1, 30) ?: FALLBACK_START_PERIOD,
                    endPeriod = (saved[KEY_END_PERIOD] as? Number)?.toInt()
                        ?.coerceIn(1, 30) ?: FALLBACK_END_PERIOD,
                    startText = saved[KEY_START_TEXT] as? String ?: FALLBACK_START_PERIOD.toString(),
                    endText = saved[KEY_END_TEXT] as? String ?: FALLBACK_END_PERIOD.toString(),
                    weekStr = saved[KEY_WEEKS] as? String ?: "1-$FALLBACK_WEEK_RANGE",
                    creditsStr = saved[KEY_CREDITS] as? String ?: "0",
                    courseType = saved[KEY_COURSE_TYPE] as? String ?: "",
                    courseCategory = saved[KEY_CATEGORY] as? String ?: "",
                    department = saved[KEY_DEPARTMENT] as? String ?: "",
                    courseId = saved[KEY_COURSE_ID] as? String ?: "",
                    classNumber = saved[KEY_CLASS_NUMBER] as? String ?: "",
                    isOnline = saved[KEY_IS_ONLINE] as? Boolean ?: false,
                    assessmentMethod = saved[KEY_ASSESSMENT] as? String ?: "",
                    notes = saved[KEY_NOTES] as? String ?: "",
                    colorIndex = (saved[KEY_COLOR_INDEX] as? Number)?.toInt()
                        ?.takeIf { it in CourseColors.indices } ?: -1,
                    reminderModeName = saved[KEY_REMINDER_MODE] as? String
                        ?: CourseReminderMode.INHERIT.name,
                    reminderMinutesOverrideValue = (saved[KEY_REMINDER_MINUTES] as? Number)?.toInt()
                        ?: NO_OVERRIDE,
                    endReminderEnabled = saved[KEY_END_REMINDER] as? Boolean ?: false,
                    scopeName = saved[KEY_SCOPE] as? String ?: CourseEditScope.CURRENT_WEEK.name
                )
            }
        )
    }
}

@Composable
private fun RequiredLabel(text: String) {
    Text(
        buildAnnotatedString {
            append(text)
            append(" ")
            withStyle(SpanStyle(color = MaterialTheme.colorScheme.error)) { append("*") }
        },
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/** 选择类控件统一使用一行、居中的文本，避免不同语言或数字宽度造成视觉漂移。 */
@Composable
private fun SelectorChipLabel(text: String) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Text(
            text,
            maxLines = 1,
            softWrap = false,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            overflow = androidx.compose.ui.text.style.TextOverflow.Clip
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseEditDialog(
    course: Course?,
    totalWeeks: Int,
    currentWeek: Int = 1,
    maxPeriods: Int = 30,
    initialDay: Int = 1,
    initialStartPeriod: Int = 1,
    onSave: (Course, CourseEditScope) -> Unit,
    onDelete: ((scope: CourseEditScope) -> Unit)?,
    onDismiss: () -> Unit
) {
    val isNew = course == null
    // 按“编辑目标 + 预设条件”隔离槽位：切换编辑对象或换预设点开新增时，表单回到全新初始值。
    val editorSlotKey = "${course?.seriesKey}|$initialDay|$initialStartPeriod|$maxPeriods"
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var showDeleteConfirm by rememberSaveable { mutableStateOf(false) }
    val errNameRequired = stringResource(R.string.edit_error_name_required)
    val errEndBeforeStart = stringResource(R.string.edit_error_end_before_start)
    val errWeeksInvalid = stringResource(R.string.edit_error_weeks_invalid)
    val errWeeksRange = stringResource(R.string.edit_error_weeks_range, totalWeeks)
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.4f

    key(editorSlotKey) {
        var form by rememberSaveable(stateSaver = CourseEditorForm.saver) {
            mutableStateOf(CourseEditorForm.from(course, initialDay, initialStartPeriod, maxPeriods, totalWeeks))
        }
        val selectedWeeks = remember(form.weekStr) { TimeUtils.parseWeeks(form.weekStr).toSet() }
        // 周次是否被改动过：只用于提示本次保存的实际作用范围，
        // 保存范围始终由用户自己的“应用到全部周”开关决定，界面不得代为放大。
        val weeksChanged = course != null && selectedWeeks != course.weeks.toSet()

        Dialog(onDismissRequest = onDismiss) {
            Surface(
                modifier = Modifier.fillMaxWidth(0.96f).widthIn(max = 560.dp).fillMaxHeight(0.92f)
                    .testTag("course-edit-dialog"),
                shape = AppShapes.sheet,
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(
                    0.8.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f)
                )
            ) {
                Column {
                    // 标题栏
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(if (isNew) stringResource(R.string.edit_title_add) else stringResource(R.string.edit_title_edit),
                            style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, stringResource(R.string.edit_close)) }
                    }
                    HorizontalDivider()

                    // 错误提示条
                    if (errorMsg != null) {
                        Surface(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = AppShapes.small
                        ) {
                            Text(errorMsg!!, color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(12.dp))
                        }
                    }

                    // 表单
                    Column(
                        modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // 课程名称
                        OutlinedTextField(value = form.name, onValueChange = { form = form.copy(name = it); errorMsg = null },
                            label = { RequiredLabel(stringResource(R.string.edit_label_course_name)) }, singleLine = true,
                            shape = AppShapes.input,
                            modifier = Modifier.fillMaxWidth().testTag("course-name-input"))

                        // 教师 + 教室
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(value = form.teacher, onValueChange = { form = form.copy(teacher = it) },
                                label = { Text(stringResource(R.string.edit_label_teacher)) }, singleLine = true,
                                shape = AppShapes.input, modifier = Modifier.weight(1f))
                            OutlinedTextField(value = form.classroom, onValueChange = { form = form.copy(classroom = it) },
                                label = { Text(stringResource(R.string.edit_label_classroom)) }, singleLine = true,
                                shape = AppShapes.input, modifier = Modifier.weight(1f))
                        }

                        // 星期选择
                        Text(stringResource(R.string.edit_label_day), style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            for (d in 1..7) {
                                val sel = d == form.day
                                Box(
                                    modifier = Modifier.weight(1f).height(40.dp)
                                        .clip(AppShapes.small)
                                        .background(
                                            if (sel) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.surfaceVariant
                                        )
                                        .clickable { form = form.copy(day = d) }
                                        .semantics { selected = sel; contentDescription = TimeUtils.getDayName(d) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(TimeUtils.getDayName(d).replace("周", ""),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                                        color = if (sel) MaterialTheme.colorScheme.onPrimary
                                                else MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }

                        // 节次：与星期一样使用点击选择，避免短输入框与正文控件上下不齐。
                        RequiredLabel(stringResource(R.string.edit_label_period))
                        Text("开始第 ${form.startPeriod} 节", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            (1..maxPeriods.coerceAtLeast(1)).forEach { period ->
                                FilterChip(modifier = Modifier.width(48.dp).height(40.dp).testTag("course-start-period-$period"), selected = period == form.startPeriod, onClick = {
                                    val adjustedEnd = maxOf(form.endPeriod, period)
                                    form = form.copy(
                                        startPeriod = period,
                                        startText = period.toString(),
                                        endPeriod = adjustedEnd,
                                        endText = adjustedEnd.toString()
                                    )
                                    errorMsg = null
                                }, label = {
                                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                        Text(period.toString(), maxLines = 1, softWrap = false, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                                    }
                                })
                            }
                        }
                        Text("结束第 ${form.endPeriod} 节", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            (1..maxPeriods.coerceAtLeast(1)).forEach { period ->
                                FilterChip(modifier = Modifier.width(48.dp).height(40.dp).testTag("course-end-period-$period"), selected = period == form.endPeriod, onClick = {
                                    val adjustedStart = minOf(form.startPeriod, period)
                                    form = form.copy(
                                        startPeriod = adjustedStart,
                                        startText = adjustedStart.toString(),
                                        endPeriod = period,
                                        endText = period.toString()
                                    )
                                    errorMsg = null
                                }, label = {
                                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                        Text(period.toString(), maxLines = 1, softWrap = false, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                                    }
                                })
                            }
                        }
                        if (form.endPeriod < form.startPeriod) {
                            Text(stringResource(R.string.edit_error_end_before_start),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error)
                        }

                        // 周次：使用全选/单双周和具体周次的同一组芯片，不再要求记忆输入格式。
                        fun selectWeeks(weeks: Set<Int>) {
                            form = form.copy(weekStr = if (weeks.size >= totalWeeks) "1-$totalWeeks" else TimeUtils.formatWeeks(weeks.sorted()))
                            errorMsg = null
                        }
                        // 周次同时承担两件事：这门课排在哪几周（芯片选中态）＋ 当前正在编辑的是哪一周。
                        // 关闭“应用到全部周”时，这一次课的周次就是“这一次课”自己的排课：
                        // 不动周次＝只改这一周；把周次改成别的周＝把这一次课整体挪过去。
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(stringResource(R.string.edit_label_weeks), style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (!isNew) {
                                Text(stringResource(R.string.edit_weeks_editing_week, currentWeek),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary)
                            }
                        }
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(modifier = Modifier.width(72.dp).height(40.dp), selected = selectedWeeks.size == totalWeeks, onClick = { selectWeeks((1..totalWeeks).toSet()) }, label = { SelectorChipLabel("全学期") })
                            FilterChip(modifier = Modifier.width(72.dp).height(40.dp), selected = selectedWeeks == (1..totalWeeks step 2).toSet(), onClick = { selectWeeks((1..totalWeeks step 2).toSet()) }, label = { SelectorChipLabel("单周") })
                            FilterChip(modifier = Modifier.width(72.dp).height(40.dp), selected = selectedWeeks == (2..totalWeeks step 2).toSet(), onClick = { selectWeeks((2..totalWeeks step 2).toSet()) }, label = { SelectorChipLabel("双周") })
                        }
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            (1..totalWeeks).forEach { week ->
                                FilterChip(modifier = Modifier.width(48.dp).height(40.dp).testTag("course-week-$week"), selected = week in selectedWeeks, onClick = {
                                    val next = selectedWeeks.toMutableSet().apply { if (!remove(week)) add(week) }
                                    selectWeeks(next)
                                // 数字周次与节次采用同一视觉规则；避免窄芯片中“周”字被裁切。
                                }, label = { SelectorChipLabel(week.toString()) })
                            }
                        }

                        // 学分
                        OutlinedTextField(value = form.creditsStr, onValueChange = { form = form.copy(creditsStr = it) },
                            label = { Text(stringResource(R.string.edit_label_credits)) }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            shape = AppShapes.input,
                            modifier = Modifier.fillMaxWidth())

                        // 课程性质 + 线上
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(value = form.courseType, onValueChange = { form = form.copy(courseType = it) },
                                label = { Text(stringResource(R.string.edit_label_course_type)) }, singleLine = true,
                                shape = AppShapes.input, modifier = Modifier.weight(1f))
                            Row(
                                modifier = Modifier.weight(1f).height(56.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Text(stringResource(R.string.edit_label_online), style = MaterialTheme.typography.bodyMedium)
                                Spacer(Modifier.width(8.dp))
                                Switch(checked = form.isOnline, onCheckedChange = { form = form.copy(isOnline = it) })
                            }
                        }

                        // 课程类别 + 考核方式
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(value = form.courseCategory, onValueChange = { form = form.copy(courseCategory = it) },
                                label = { Text(stringResource(R.string.edit_label_course_category)) }, singleLine = true,
                                shape = AppShapes.input, modifier = Modifier.weight(1f))
                            OutlinedTextField(value = form.assessmentMethod, onValueChange = { form = form.copy(assessmentMethod = it) },
                                label = { Text(stringResource(R.string.edit_label_assessment)) }, singleLine = true,
                                shape = AppShapes.input, modifier = Modifier.weight(1f))
                        }

                        // 课程号 + 课序号
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(value = form.courseId, onValueChange = { form = form.copy(courseId = it) },
                                label = { Text(stringResource(R.string.edit_label_course_id)) }, singleLine = true,
                                shape = AppShapes.input, modifier = Modifier.weight(1f))
                            OutlinedTextField(value = form.classNumber, onValueChange = { form = form.copy(classNumber = it) },
                                label = { Text(stringResource(R.string.edit_label_class_number)) }, singleLine = true,
                                shape = AppShapes.input, modifier = Modifier.weight(1f))
                        }

                        // 开课单位
                        OutlinedTextField(value = form.department, onValueChange = { form = form.copy(department = it) },
                            label = { Text(stringResource(R.string.edit_label_department)) }, singleLine = true,
                            shape = AppShapes.input,
                            modifier = Modifier.fillMaxWidth())

                        // 预设课程颜色：统一维护深浅模式对应关系，避免用户自行适配。
                        Text(stringResource(R.string.edit_label_color), style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            coursePalette(dark).forEachIndexed { idx, clr ->
                                val colorDesc = stringResource(R.string.edit_color_option_desc, idx + 1)
                                Box(modifier = Modifier.size(48.dp).padding(7.dp)
                                    .background(clr, RoundedCornerShape(15.dp))
                                    .border(if (idx == form.colorIndex) 2.dp else 0.dp,
                                        if (idx == form.colorIndex) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                        RoundedCornerShape(15.dp))
                                    .clickable {
                                        form = form.copy(
                                            colorIndex = if (idx == form.colorIndex) -1 else idx
                                        )
                                    }
                                    .semantics {
                                        selected = idx == form.colorIndex
                                        role = Role.RadioButton
                                        contentDescription = colorDesc
                                    })
                            }
                        }

                        // 备注
                        OutlinedTextField(value = form.notes, onValueChange = { form = form.copy(notes = it) },
                            label = { Text(stringResource(R.string.edit_label_notes)) }, maxLines = 3,
                            shape = AppShapes.input,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp))

                        Text(stringResource(R.string.edit_label_reminder), style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(
                                CourseReminderMode.INHERIT to stringResource(R.string.edit_reminder_inherit),
                                CourseReminderMode.ENABLED to stringResource(R.string.edit_reminder_enabled),
                                CourseReminderMode.DISABLED to stringResource(R.string.edit_reminder_disabled)
                            ).forEach { (mode, label) ->
                                FilterChip(
                                    modifier = Modifier.width(96.dp).height(40.dp),
                                    selected = form.reminderMode == mode,
                                    onClick = { form = form.copy(reminderModeName = mode.name) },
                                    label = { SelectorChipLabel(label) }
                                )
                            }
                        }
                        if (form.reminderMode != CourseReminderMode.DISABLED) {
                            Text(stringResource(R.string.edit_label_advance_minutes),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(
                                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilterChip(
                                    modifier = Modifier.width(96.dp).height(40.dp),
                                    selected = form.reminderMinutesOverride == null,
                                    onClick = { form = form.copy(reminderMinutesOverrideValue = CourseEditorForm.NO_OVERRIDE) },
                                    label = { SelectorChipLabel(stringResource(R.string.edit_reminder_inherit)) }
                                )
                                listOf(5, 10, 15, 30).forEach { minutes ->
                                    FilterChip(
                                        modifier = Modifier.width(72.dp).height(40.dp),
                                        selected = form.reminderMinutesOverride == minutes,
                                        onClick = { form = form.copy(reminderMinutesOverrideValue = minutes) },
                                        label = { SelectorChipLabel(stringResource(R.string.edit_reminder_minutes, minutes)) }
                                    )
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(stringResource(R.string.edit_label_end_reminder), style = MaterialTheme.typography.bodyMedium)
                                    Text(stringResource(R.string.edit_end_reminder_desc),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Switch(checked = form.endReminderEnabled, onCheckedChange = { form = form.copy(endReminderEnabled = it) })
                            }
                        }

                        // 保存范围（仅编辑模式）：默认“只改这一周”，可扩到本周及以后或整门课程。
                        // 两个开关共用同一个 scope，因此天然互斥——打开一个另一个会自动关闭。
                        if (!isNew) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(stringResource(R.string.edit_apply_to_all),
                                    modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                Switch(checked = form.scope == CourseEditScope.ALL_WEEKS,
                                    modifier = Modifier.testTag("course-apply-to-all"),
                                    onCheckedChange = { checked ->
                                        form = form.copy(
                                            scopeName = if (checked) CourseEditScope.ALL_WEEKS.name
                                            else CourseEditScope.CURRENT_WEEK.name
                                        )
                                    })
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(stringResource(R.string.edit_apply_from_this_week),
                                    modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                Switch(checked = form.scope == CourseEditScope.FROM_CURRENT_WEEK,
                                    modifier = Modifier.testTag("course-apply-from-week"),
                                    onCheckedChange = { checked ->
                                        form = form.copy(
                                            scopeName = if (checked) CourseEditScope.FROM_CURRENT_WEEK.name
                                            else CourseEditScope.CURRENT_WEEK.name
                                        )
                                    })
                            }
                        }

                        Spacer(modifier = Modifier.height(80.dp))
                    }

                    // 底部按钮
                    HorizontalDivider()
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // 三个按钮同高 44dp：删除按钮文案随保存范围变化（删除本周／删除本周起／删除全部），
                        // 因此它占更宽的一份，取消与保存保持等宽；文字超长时省略号收尾，不静默裁切。
                        if (!isNew && onDelete != null) {
                            OutlinedButton(
                                onClick = { showDeleteConfirm = true },
                                modifier = Modifier.weight(1.6f).height(44.dp).testTag("course-delete-button"),
                                shape = AppShapes.small,
                                contentPadding = PaddingValues(horizontal = 4.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error)) {
                                Text(
                                    when (form.scope) {
                                        CourseEditScope.ALL_WEEKS -> stringResource(R.string.edit_delete_all)
                                        CourseEditScope.FROM_CURRENT_WEEK -> stringResource(R.string.edit_delete_from_week)
                                        CourseEditScope.CURRENT_WEEK -> stringResource(R.string.edit_delete_week)
                                    },
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                            }
                        }
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier.weight(0.85f).height(44.dp),
                            shape = AppShapes.small,
                            contentPadding = PaddingValues(horizontal = 4.dp)
                        ) {
                            Text(stringResource(R.string.edit_button_cancel), maxLines = 1)
                        }
                        Button(onClick = {
                            // 校验
                            if (form.name.isBlank()) { errorMsg = errNameRequired; return@Button }
                            if (form.endPeriod < form.startPeriod) { errorMsg = errEndBeforeStart; return@Button }
                            // Empty selection is an unfinished edit, never an implicit whole semester.
                            val parsedWeeks = TimeUtils.parseWeeks(form.weekStr)
                            if (parsedWeeks.isEmpty()) {
                                errorMsg = errWeeksInvalid
                                return@Button
                            }
                            if (parsedWeeks.any { it !in 1..totalWeeks }) {
                                errorMsg = errWeeksRange
                                return@Button
                            }
                            val weeks = parsedWeeks
                            // 只存预设颜色索引，深浅模式可使用同一索引稳定切换。
                            val selColor = form.colorIndex.takeIf { it in CourseColors.indices }
                            onSave(Course(
                                // 手工新增时用随机 ID，避免同一毫秒添加两门课程时时间戳碰撞
                                courseId = form.courseId.ifBlank { UUID.randomUUID().toString() },
                                courseName = form.name.trim(), classNumber = form.classNumber.trim(),
                                department = form.department.trim(),
                                credits = form.creditsStr.toFloatOrNull() ?: 0f,
                                weeks = weeks, dayOfWeek = form.day,
                                startPeriod = form.startPeriod, endPeriod = form.endPeriod,
                                teacher = form.teacher.trim(), classroom = form.classroom.trim(),
                                courseType = form.courseType.trim(), courseCategory = form.courseCategory.trim(),
                                isOnline = form.isOnline, assessmentMethod = form.assessmentMethod.trim(),
                                customColor = selColor, notes = form.notes.trim(),
                                seriesId = form.stableSeriesId,
                                reminderMode = form.reminderMode,
                                reminderMinutesOverride = form.reminderMinutesOverride,
                                endReminderEnabled = form.endReminderEnabled
                            ), form.scope)
                        }, modifier = Modifier.weight(0.85f).height(44.dp).testTag("course-save-button"), shape = AppShapes.small, contentPadding = PaddingValues(horizontal = 4.dp)) {
                            Text(stringResource(R.string.edit_save), fontWeight = FontWeight.Bold, maxLines = 1)
                        }
                    }
                }
            }
        }

        if (showDeleteConfirm && onDelete != null) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirm = false },
                icon = { Icon(Icons.Rounded.DeleteOutline, null, tint = MaterialTheme.colorScheme.error) },
                title = { Text(stringResource(R.string.edit_delete_confirm_title)) },
                text = {
                    Text(
                        when (form.scope) {
                            CourseEditScope.ALL_WEEKS ->
                                stringResource(R.string.edit_delete_all_weeks_message, form.name)
                            CourseEditScope.FROM_CURRENT_WEEK ->
                                stringResource(R.string.edit_delete_from_week_message, currentWeek, form.name)
                            CourseEditScope.CURRENT_WEEK ->
                                stringResource(R.string.edit_delete_week_message, currentWeek, form.name)
                        }
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        showDeleteConfirm = false
                        onDelete(form.scope)
                    }) { Text(stringResource(R.string.edit_delete_confirm), color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirm = false }) { Text(stringResource(R.string.edit_button_cancel)) }
                }
            )
        }
    }
}
