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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.mapSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.jaysay.coursetable.R
import com.jaysay.coursetable.data.model.Course
import com.jaysay.coursetable.data.model.CourseReminderMode
import com.jaysay.coursetable.ui.theme.*
import com.jaysay.coursetable.util.TimeUtils
import java.util.UUID

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
    val customColorIndex: Int,
    val reminderModeName: String,
    val reminderMinutesOverrideValue: Int,
    val endReminderEnabled: Boolean,
    val applyToAll: Boolean
) {
    val reminderMode: CourseReminderMode
        get() = runCatching { CourseReminderMode.valueOf(reminderModeName) }
            .getOrDefault(CourseReminderMode.INHERIT)
    val reminderMinutesOverride: Int?
        get() = reminderMinutesOverrideValue.takeIf { it >= 0 }

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
                customColorIndex = course?.customColor?.takeIf { it in 0..14 } ?: -1,
                reminderModeName = (course?.reminderMode ?: CourseReminderMode.INHERIT).name,
                reminderMinutesOverrideValue = course?.reminderMinutesOverride ?: NO_OVERRIDE,
                endReminderEnabled = course?.endReminderEnabled ?: false,
                applyToAll = false
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
        private const val KEY_APPLY_TO_ALL = "applyToAll"

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
                    KEY_COLOR_INDEX to form.customColorIndex,
                    KEY_REMINDER_MODE to form.reminderModeName,
                    KEY_REMINDER_MINUTES to form.reminderMinutesOverrideValue,
                    KEY_END_REMINDER to form.endReminderEnabled,
                    KEY_APPLY_TO_ALL to form.applyToAll
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
                    customColorIndex = (saved[KEY_COLOR_INDEX] as? Number)?.toInt()?.takeIf { it in 0..14 } ?: -1,
                    reminderModeName = saved[KEY_REMINDER_MODE] as? String
                        ?: CourseReminderMode.INHERIT.name,
                    reminderMinutesOverrideValue = (saved[KEY_REMINDER_MINUTES] as? Number)?.toInt()
                        ?: NO_OVERRIDE,
                    endReminderEnabled = saved[KEY_END_REMINDER] as? Boolean ?: false,
                    applyToAll = saved[KEY_APPLY_TO_ALL] as? Boolean ?: false
                )
            }
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
    onSave: (Course, applyToAll: Boolean) -> Unit,
    onDelete: ((applyToAll: Boolean) -> Unit)?,
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

    key(editorSlotKey) {
        var form by rememberSaveable(stateSaver = CourseEditorForm.saver) {
            mutableStateOf(CourseEditorForm.from(course, initialDay, initialStartPeriod, maxPeriods, totalWeeks))
        }

        Dialog(onDismissRequest = onDismiss) {
            Surface(
                modifier = Modifier.fillMaxWidth().fillMaxHeight(0.92f).testTag("course-edit-dialog"),
                shape = RoundedCornerShape(20.dp),
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
                            fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, stringResource(R.string.edit_close)) }
                    }
                    HorizontalDivider()

                    // 错误提示条
                    if (errorMsg != null) {
                        Surface(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(errorMsg!!, color = MaterialTheme.colorScheme.onErrorContainer,
                                fontSize = 13.sp, modifier = Modifier.padding(12.dp))
                        }
                    }

                    // 表单
                    Column(
                        modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // 课程名称
                        OutlinedTextField(value = form.name, onValueChange = { form = form.copy(name = it); errorMsg = null },
                            label = { Text(stringResource(R.string.edit_label_course_name)) }, singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("course-name-input"))

                        // 教师 + 教室
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(value = form.teacher, onValueChange = { form = form.copy(teacher = it) },
                                label = { Text(stringResource(R.string.edit_label_teacher)) }, singleLine = true, modifier = Modifier.weight(1f))
                            OutlinedTextField(value = form.classroom, onValueChange = { form = form.copy(classroom = it) },
                                label = { Text(stringResource(R.string.edit_label_classroom)) }, singleLine = true, modifier = Modifier.weight(1f))
                        }

                        // 星期选择
                        Text(stringResource(R.string.edit_label_day), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            for (d in 1..7) {
                                val sel = d == form.day
                                Box(
                                    modifier = Modifier.weight(1f).height(48.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(
                                            if (sel) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.surfaceVariant
                                        )
                                        .clickable { form = form.copy(day = d) }
                                        .semantics { selected = sel; contentDescription = TimeUtils.getDayName(d) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(TimeUtils.getDayName(d).replace("周", ""),
                                        fontSize = 11.sp,
                                        fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                                        color = if (sel) MaterialTheme.colorScheme.onPrimary
                                                else MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }

                        // 节次
                        Text(stringResource(R.string.edit_label_period), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.edit_period_prefix), fontSize = 14.sp)
                            OutlinedTextField(value = form.startText, onValueChange = { v ->
                                val f = v.filter { it.isDigit() }.take(2)
                                form = form.copy(startText = f)
                                f.toIntOrNull()?.let { form = form.copy(startPeriod = it.coerceIn(1, maxPeriods.coerceAtLeast(1))) }
                                errorMsg = null
                            }, modifier = Modifier.width(60.dp).testTag("course-start-period-input"), singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                            Text(stringResource(R.string.edit_period_range), fontSize = 14.sp)
                            OutlinedTextField(value = form.endText, onValueChange = { v ->
                                val f = v.filter { it.isDigit() }.take(2)
                                form = form.copy(endText = f)
                                f.toIntOrNull()?.let { form = form.copy(endPeriod = it.coerceIn(1, maxPeriods.coerceAtLeast(1))) }
                                errorMsg = null
                            }, modifier = Modifier.width(60.dp).testTag("course-end-period-input"), singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                            Text(stringResource(R.string.edit_period_suffix), fontSize = 14.sp)
                        }
                        if (form.endPeriod < form.startPeriod) {
                            Text(stringResource(R.string.edit_error_end_before_start), fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.error)
                        }

                        // 周次
                        OutlinedTextField(value = form.weekStr, onValueChange = { form = form.copy(weekStr = it) },
                            label = { Text(stringResource(R.string.edit_label_weeks)) }, singleLine = true,
                            modifier = Modifier.fillMaxWidth())

                        // 学分
                        OutlinedTextField(value = form.creditsStr, onValueChange = { form = form.copy(creditsStr = it) },
                            label = { Text(stringResource(R.string.edit_label_credits)) }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.width(120.dp))

                        // 课程性质 + 线上
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(value = form.courseType, onValueChange = { form = form.copy(courseType = it) },
                                label = { Text(stringResource(R.string.edit_label_course_type)) }, singleLine = true, modifier = Modifier.weight(1f))
                            Text(stringResource(R.string.edit_label_online), fontSize = 13.sp)
                            Switch(checked = form.isOnline, onCheckedChange = { form = form.copy(isOnline = it) })
                        }

                        // 课程类别 + 考核方式
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(value = form.courseCategory, onValueChange = { form = form.copy(courseCategory = it) },
                                label = { Text(stringResource(R.string.edit_label_course_category)) }, singleLine = true, modifier = Modifier.weight(1f))
                            OutlinedTextField(value = form.assessmentMethod, onValueChange = { form = form.copy(assessmentMethod = it) },
                                label = { Text(stringResource(R.string.edit_label_assessment)) }, singleLine = true, modifier = Modifier.weight(1f))
                        }

                        // 课程号 + 课序号
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(value = form.courseId, onValueChange = { form = form.copy(courseId = it) },
                                label = { Text(stringResource(R.string.edit_label_course_id)) }, singleLine = true, modifier = Modifier.weight(1f))
                            OutlinedTextField(value = form.classNumber, onValueChange = { form = form.copy(classNumber = it) },
                                label = { Text(stringResource(R.string.edit_label_class_number)) }, singleLine = true, modifier = Modifier.weight(1f))
                        }

                        // 开课单位
                        OutlinedTextField(value = form.department, onValueChange = { form = form.copy(department = it) },
                            label = { Text(stringResource(R.string.edit_label_department)) }, singleLine = true,
                            modifier = Modifier.fillMaxWidth())

                        // 自定义颜色
                        Text(stringResource(R.string.edit_label_color), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            CourseColors.forEachIndexed { idx, clr ->
                                val colorDesc = stringResource(R.string.edit_color_option_desc, idx + 1)
                                Box(modifier = Modifier.size(48.dp).padding(7.dp)
                                    .background(clr, RoundedCornerShape(15.dp))
                                    .border(if (idx == form.customColorIndex) 2.dp else 0.dp,
                                        if (idx == form.customColorIndex) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                        RoundedCornerShape(15.dp))
                                    .clickable { form = form.copy(customColorIndex = if (idx == form.customColorIndex) -1 else idx) }
                                    .semantics {
                                        selected = idx == form.customColorIndex
                                        role = Role.RadioButton
                                        contentDescription = colorDesc
                                    })
                            }
                        }

                        // 备注
                        OutlinedTextField(value = form.notes, onValueChange = { form = form.copy(notes = it) },
                            label = { Text(stringResource(R.string.edit_label_notes)) }, maxLines = 3,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp))

                        Text(stringResource(R.string.edit_label_reminder), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                                    selected = form.reminderMode == mode,
                                    onClick = { form = form.copy(reminderModeName = mode.name) },
                                    label = { Text(label) }
                                )
                            }
                        }
                        if (form.reminderMode != CourseReminderMode.DISABLED) {
                            Text(stringResource(R.string.edit_label_advance_minutes), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(
                                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilterChip(
                                    selected = form.reminderMinutesOverride == null,
                                    onClick = { form = form.copy(reminderMinutesOverrideValue = CourseEditorForm.NO_OVERRIDE) },
                                    label = { Text(stringResource(R.string.edit_reminder_inherit)) }
                                )
                                listOf(5, 10, 15, 30).forEach { minutes ->
                                    FilterChip(
                                        selected = form.reminderMinutesOverride == minutes,
                                        onClick = { form = form.copy(reminderMinutesOverrideValue = minutes) },
                                        label = { Text(stringResource(R.string.edit_reminder_minutes, minutes)) }
                                    )
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(stringResource(R.string.edit_label_end_reminder), fontSize = 14.sp)
                                    Text(stringResource(R.string.edit_end_reminder_desc), fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Switch(checked = form.endReminderEnabled, onCheckedChange = { form = form.copy(endReminderEnabled = it) })
                            }
                        }

                        // 应用到全部周（仅编辑模式）
                        if (!isNew) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(stringResource(R.string.edit_apply_to_all), fontSize = 14.sp)
                                Switch(checked = form.applyToAll, onCheckedChange = { form = form.copy(applyToAll = it) })
                            }
                        }

                        Spacer(modifier = Modifier.height(80.dp))
                    }

                    // 底部按钮
                    HorizontalDivider()
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (!isNew && onDelete != null) {
                            OutlinedButton(
                                onClick = { showDeleteConfirm = true },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error)) {
                                Text(if (form.applyToAll) stringResource(R.string.edit_delete_all) else stringResource(R.string.edit_delete_week))
                            }
                        }
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(stringResource(R.string.edit_button_cancel))
                        }
                        Button(onClick = {
                            // 校验
                            if (form.name.isBlank()) { errorMsg = errNameRequired; return@Button }
                            if (form.endPeriod < form.startPeriod) { errorMsg = errEndBeforeStart; return@Button }
                            // 解析周次；空白表示整学期，错误或越界输入必须明确提示。
                            val parsedWeeks = TimeUtils.parseWeeks(form.weekStr)
                            if (form.weekStr.isNotBlank() && parsedWeeks.isEmpty()) {
                                errorMsg = errWeeksInvalid
                                return@Button
                            }
                            if (parsedWeeks.any { it !in 1..totalWeeks }) {
                                errorMsg = errWeeksRange
                                return@Button
                            }
                            val weeks = parsedWeeks.ifEmpty { (1..totalWeeks).toList() }
                            // 存储颜色索引（0-14），随深浅色模式自动切换
                            val selColor = if (form.customColorIndex in 0..14) form.customColorIndex else null
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
                            ), form.applyToAll)
                        }, modifier = Modifier.weight(1f).testTag("course-save-button"), shape = RoundedCornerShape(12.dp)) {
                            Text(stringResource(R.string.edit_save), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        if (showDeleteConfirm && onDelete != null) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirm = false },
                icon = { Icon(Icons.Default.DeleteOutline, null, tint = MaterialTheme.colorScheme.error) },
                title = { Text(stringResource(R.string.edit_delete_confirm_title)) },
                text = {
                    Text(
                        if (form.applyToAll) stringResource(R.string.edit_delete_all_weeks_message, form.name)
                        else stringResource(R.string.edit_delete_week_message, currentWeek, form.name)
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        showDeleteConfirm = false
                        onDelete(form.applyToAll)
                    }) { Text(stringResource(R.string.edit_delete_confirm), color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirm = false }) { Text(stringResource(R.string.edit_button_cancel)) }
                }
            )
        }
    }
}
