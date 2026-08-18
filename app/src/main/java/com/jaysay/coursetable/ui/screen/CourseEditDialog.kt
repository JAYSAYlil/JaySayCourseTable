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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.jaysay.coursetable.data.model.Course
import com.jaysay.coursetable.ui.theme.*
import com.jaysay.coursetable.util.TimeUtils
import java.util.UUID

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
    val initialKey = "$initialDay-$initialStartPeriod-$maxPeriods"
    val stableSeriesId = remember(course) { course?.seriesKey ?: UUID.randomUUID().toString() }
    var applyToAll by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember(course) { mutableStateOf(false) }
    // key = course 确保切换编辑对象时状态重置
    var name by remember(course) { mutableStateOf(course?.courseName ?: "") }
    var teacher by remember(course) { mutableStateOf(course?.teacher ?: "") }
    var classroom by remember(course) { mutableStateOf(course?.classroom ?: "") }
    var day by remember(course, initialKey) { mutableIntStateOf(course?.dayOfWeek ?: initialDay.coerceIn(1, 7)) }
    var startPeriod by remember(course, initialKey) {
        mutableIntStateOf(course?.startPeriod ?: initialStartPeriod.coerceIn(1, maxPeriods.coerceAtLeast(1)))
    }
    var endPeriod by remember(course, initialKey) {
        val start = course?.startPeriod ?: initialStartPeriod.coerceIn(1, maxPeriods.coerceAtLeast(1))
        mutableIntStateOf(course?.endPeriod ?: (start + 1).coerceAtMost(maxPeriods.coerceAtLeast(1)))
    }
    var weekStr by remember(course) { mutableStateOf(course?.let { TimeUtils.formatWeeks(it.weeks) } ?: "1-$totalWeeks") }
    var creditsStr by remember(course) { mutableStateOf(course?.credits?.let { if (it == it.toLong().toFloat()) it.toLong().toString() else it.toString() } ?: "0") }
    var courseType by remember(course) { mutableStateOf(course?.courseType ?: "") }
    var notes by remember(course) { mutableStateOf(course?.notes ?: "") }
    var courseId by remember(course) { mutableStateOf(course?.courseId ?: "") }
    var classNum by remember(course) { mutableStateOf(course?.classNumber ?: "") }
    var dept by remember(course) { mutableStateOf(course?.department ?: "") }
    var courseCat by remember(course) { mutableStateOf(course?.courseCategory ?: "") }
    var isOnline by remember(course) { mutableStateOf(course?.isOnline ?: false) }
    var assessMethod by remember(course) { mutableStateOf(course?.assessmentMethod ?: "") }
    // 颜色索引：-1=不选，0-14=对应 CourseColors/DarkCourseColors
    var colorIdx by remember(course) { mutableIntStateOf(
        course?.customColor?.let { cc ->
            if (cc in 0..14) cc else -1 // 新数据用索引；旧 ARGB 数据无法匹配则 -1
        } ?: -1
    ) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

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
                    Text(if (isNew) "添加课程" else "编辑课程",
                        fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "关闭") }
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
                    OutlinedTextField(value = name, onValueChange = { name = it; errorMsg = null },
                        label = { Text("课程名称 *") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("course-name-input"))

                    // 教师 + 教室
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = teacher, onValueChange = { teacher = it },
                            label = { Text("教师") }, singleLine = true, modifier = Modifier.weight(1f))
                        OutlinedTextField(value = classroom, onValueChange = { classroom = it },
                            label = { Text("教室") }, singleLine = true, modifier = Modifier.weight(1f))
                    }

                    // 星期选择
                    Text("上课星期", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        for (d in 1..7) {
                            val sel = d == day
                            Box(
                                modifier = Modifier.weight(1f).height(48.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (sel) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.surfaceVariant
                                    )
                                    .clickable { day = d },
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
                    Text("上课节次 *", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("第 ", fontSize = 14.sp)
                        var sText by remember(course, initialKey) { mutableStateOf(startPeriod.toString()) }
                        OutlinedTextField(value = sText, onValueChange = { v ->
                            val f = v.filter { it.isDigit() }.take(2)
                            sText = f; f.toIntOrNull()?.let { startPeriod = it.coerceIn(1, maxPeriods.coerceAtLeast(1)) }
                            errorMsg = null
                        }, modifier = Modifier.width(60.dp), singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                        Text(" 节 ～ 第 ", fontSize = 14.sp)
                        var eText by remember(course, initialKey) { mutableStateOf(endPeriod.toString()) }
                        OutlinedTextField(value = eText, onValueChange = { v ->
                            val f = v.filter { it.isDigit() }.take(2)
                            eText = f; f.toIntOrNull()?.let { endPeriod = it.coerceIn(1, maxPeriods.coerceAtLeast(1)) }
                            errorMsg = null
                        }, modifier = Modifier.width(60.dp), singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                        Text(" 节", fontSize = 14.sp)
                    }
                    if (endPeriod < startPeriod) {
                        Text("结束节次不能小于开始节次", fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.error)
                    }

                    // 周次
                    OutlinedTextField(value = weekStr, onValueChange = { weekStr = it },
                        label = { Text("上课周次（如 1-18 或 1,3,5-8）") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth())

                    // 学分
                    OutlinedTextField(value = creditsStr, onValueChange = { creditsStr = it },
                        label = { Text("学分") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.width(120.dp))

                    // 课程性质 + 线上
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(value = courseType, onValueChange = { courseType = it },
                            label = { Text("课程性质") }, singleLine = true, modifier = Modifier.weight(1f))
                        Text("线上", fontSize = 13.sp)
                        Switch(checked = isOnline, onCheckedChange = { isOnline = it })
                    }

                    // 课程类别 + 考核方式
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = courseCat, onValueChange = { courseCat = it },
                            label = { Text("课程类别") }, singleLine = true, modifier = Modifier.weight(1f))
                        OutlinedTextField(value = assessMethod, onValueChange = { assessMethod = it },
                            label = { Text("考核方式") }, singleLine = true, modifier = Modifier.weight(1f))
                    }

                    // 课程号 + 课序号
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = courseId, onValueChange = { courseId = it },
                            label = { Text("课程号") }, singleLine = true, modifier = Modifier.weight(1f))
                        OutlinedTextField(value = classNum, onValueChange = { classNum = it },
                            label = { Text("课序号") }, singleLine = true, modifier = Modifier.weight(1f))
                    }

                    // 开课单位
                    OutlinedTextField(value = dept, onValueChange = { dept = it },
                        label = { Text("开课单位") }, singleLine = true, modifier = Modifier.fillMaxWidth())

                    // 自定义颜色
                    Text("卡片颜色", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        CourseColors.forEachIndexed { idx, clr ->
                            Box(modifier = Modifier.size(48.dp).padding(7.dp)
                                .background(clr, RoundedCornerShape(15.dp))
                                .border(if (idx == colorIdx) 2.dp else 0.dp,
                                    if (idx == colorIdx) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                    RoundedCornerShape(15.dp))
                                .clickable { colorIdx = if (colorIdx == idx) -1 else idx })
                        }
                    }

                    // 备注
                    OutlinedTextField(value = notes, onValueChange = { notes = it },
                        label = { Text("备注") }, maxLines = 3,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp))

                    // 应用到全部周（仅编辑模式）
                    if (!isNew) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("应用到全部周", fontSize = 14.sp)
                            Switch(checked = applyToAll, onCheckedChange = { applyToAll = it })
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
                            Text(if (applyToAll) "删除全部" else "删除本周")
                        }
                    }
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("取消")
                    }
                    Button(onClick = {
                        // 校验
                        if (name.isBlank()) { errorMsg = "课程名称不能为空"; return@Button }
                        if (endPeriod < startPeriod) { errorMsg = "结束节次不能小于开始节次"; return@Button }
                        // 解析周次；空白表示整学期，错误或越界输入必须明确提示。
                        val parsedWeeks = TimeUtils.parseWeeks(weekStr)
                        if (weekStr.isNotBlank() && parsedWeeks.isEmpty()) {
                            errorMsg = "无法识别周次，请输入如 1-16周 或 1-15周(单)"
                            return@Button
                        }
                        if (parsedWeeks.any { it !in 1..totalWeeks }) {
                            errorMsg = "周次必须在 1-$totalWeeks 之间"
                            return@Button
                        }
                        val weeks = parsedWeeks.ifEmpty { (1..totalWeeks).toList() }
                        // 存储颜色索引（0-14），随深浅色模式自动切换
                        val selColor = if (colorIdx in 0..14) colorIdx else null
                        onSave(Course(
                            courseId = courseId.ifBlank { "M${System.currentTimeMillis()}" },
                            courseName = name.trim(), classNumber = classNum.trim(),
                            department = dept.trim(),
                            credits = creditsStr.toFloatOrNull() ?: 0f,
                            weeks = weeks, dayOfWeek = day,
                            startPeriod = startPeriod, endPeriod = endPeriod,
                            teacher = teacher.trim(), classroom = classroom.trim(),
                            courseType = courseType.trim(), courseCategory = courseCat.trim(),
                            isOnline = isOnline, assessmentMethod = assessMethod.trim(),
                            customColor = selColor, notes = notes.trim(),
                            seriesId = stableSeriesId
                        ), applyToAll)
                    }, modifier = Modifier.weight(1f).testTag("course-save-button"), shape = RoundedCornerShape(12.dp)) {
                        Text("保存", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    if (showDeleteConfirm && onDelete != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            icon = { Icon(Icons.Default.DeleteOutline, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("确认删除课程？") },
            text = {
                Text(
                    if (applyToAll) "将删除“$name”的全部周次，删除后可在提示条中撤销。"
                    else "只从第 $currentWeek 周移除“$name”，其他周次不受影响。删除后可在提示条中撤销。"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    onDelete(applyToAll)
                }) { Text("确认删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("取消") }
            }
        )
    }
}
