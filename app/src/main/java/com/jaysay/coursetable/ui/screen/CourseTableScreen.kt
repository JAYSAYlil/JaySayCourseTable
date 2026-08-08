package com.jaysay.coursetable.ui.screen

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color.Companion.White
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jaysay.coursetable.data.model.Course
import com.jaysay.coursetable.data.preferences.PeriodTime
import com.jaysay.coursetable.ui.theme.*
import com.jaysay.coursetable.util.TimeUtils
import java.text.SimpleDateFormat
import java.util.*

// ============================================================
//  完全参照样板 - 青绿配色 + 纯白底 + 七天同屏
// ============================================================

private val dateFormat by lazy { SimpleDateFormat("M/d", Locale.getDefault()) }

private fun refDate(week: Int, dayOfWeek: Int, semesterStart: String): String {
    val cal = Calendar.getInstance()
    try {
        val parts = semesterStart.split("-")
        cal.set(parts[0].toInt(), parts[1].toInt() - 1, parts[2].toInt(), 0, 0, 0)
        cal.set(Calendar.MILLISECOND, 0)
    } catch (_: Exception) {
        cal.set(2026, Calendar.FEBRUARY, 23, 0, 0, 0)
        cal.set(Calendar.MILLISECOND, 0)
    }
    cal.add(Calendar.DAY_OF_YEAR, (week - 1) * 7 + (dayOfWeek - 1))
    return dateFormat.format(cal.time)
}

@Composable
fun CourseTableScreen(
    courses: List<Course>, currentWeek: Int,
    onImportClick: () -> Unit, onCourseClick: (Course) -> Unit,
    onWeekChange: (Int) -> Unit,
    onSettingsClick: () -> Unit = {},
    onAddCourseClick: () -> Unit = {},
    onLocateToday: () -> Unit = {},
    onTableMenuClick: () -> Unit = {},
    tableName: String = "课表1",
    periodTimes: List<PeriodTime> = com.jaysay.coursetable.data.preferences.AppPreferences.defaultPeriods(),
    semesterStart: String = TimeUtils.todayDate(),
    totalWeeks: Int = 20
) {
    val vSc = rememberScrollState()
    val swDp = LocalConfiguration.current.screenWidthDp
    val (tw, dw, ch) = remember(swDp) {
        val sw = swDp.dp
        val tw = 32.dp
        val dw = ((sw - tw - 4.dp) / 7f).coerceIn(40.dp, 48.dp)
        Triple(tw, dw, 80.dp)
    }

    val weekCourses = remember(courses, currentWeek) { courses.filter { currentWeek in it.weeks } }

    // 用MaterialTheme判断深浅（而非isSystemInDarkTheme，避免与用户手动选择冲突）
    val dark = MaterialTheme.colorScheme.background == DarkBackground
    val bgColor = MaterialTheme.colorScheme.background
    val cTextColor = if (dark) DarkCourseTextColor else CourseTextColor
    val cSubColor = if (dark) DarkCourseSubTextColor else CourseSubTextColor

    val themeColorMap = remember(courses, dark) {
        val m = mutableMapOf<String, Color>()
        val colors = if (dark) DarkCourseColors else CourseColors
        courses.distinctBy { it.courseName }.forEachIndexed { i, course ->
            m[course.courseName] = colors[i % colors.size]
        }
        m
    }

    // 计算今天在学期中的位置（基于开学日期）
    val (todayWeek, todayDow) = remember(semesterStart) {
        val semCal = Calendar.getInstance().apply {
            try { val p = semesterStart.split("-"); set(p[0].toInt(), p[1].toInt() - 1, p[2].toInt()) }
            catch (_: Exception) { set(2026, Calendar.FEBRUARY, 23) }
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val todayClean = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val daysDiff = ((todayClean.timeInMillis - semCal.timeInMillis) / (1000 * 60 * 60 * 24)).toInt()
        if (daysDiff >= 0) (daysDiff / 7 + 1) to (daysDiff % 7 + 1) else -1 to -1
    }

    val arrowTint = MaterialTheme.colorScheme.primary
    val arrowDisabled = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)
    val isTodayWeek = currentWeek == todayWeek

    val dateTextColor = if (dark) Color(0xFF777777) else Color(0xFF999999)

    Column(
        modifier = Modifier.fillMaxSize().background(bgColor).statusBarsPadding()
    ) {
        // ===== 工具栏 =====
        Row(
            modifier = Modifier.fillMaxWidth()
                .padding(start = 16.dp, end = 6.dp, top = 10.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { onTableMenuClick() }) {
                Text(tableName, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Icon(Icons.Default.ArrowDropDown, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(26.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onAddCourseClick, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Default.AddCircleOutline, "加课", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                }
                IconButton(onClick = onImportClick, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Default.FileOpen, "导入", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                }
                IconButton(onClick = onLocateToday, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Default.MyLocation, "今天", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                }
                IconButton(onClick = onSettingsClick, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Default.Settings, "设置", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
                }
            }
        }

        // ===== 分割线：工具栏 ↓ 导航区 =====
        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

        if (courses.isNotEmpty()) {
            // ===== 固定导航区：胶囊 + 圆点 + 表头 拼在一起 =====
            // 周导航
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { if (currentWeek > 1) onWeekChange(currentWeek - 1) }, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Outlined.ChevronLeft, null, modifier = Modifier.size(20.dp),
                        tint = if (currentWeek > 1) arrowTint else arrowDisabled)
                }
                Box(
                    modifier = Modifier
                        .animateContentSize()
                        .background(
                            if (isTodayWeek) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer,
                            RoundedCornerShape(50)
                        ).padding(horizontal = 18.dp, vertical = 5.dp)
                ) {
                    Text(
                        if (isTodayWeek) "● 第 ${currentWeek} 周 · ${weekCourses.size} 节"
                        else "第 ${currentWeek} 周 · ${weekCourses.size} 节",
                        fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                        color = if (isTodayWeek) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                IconButton(onClick = { if (currentWeek < totalWeeks) onWeekChange(currentWeek + 1) }, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Outlined.ChevronRight, null, modifier = Modifier.size(20.dp),
                        tint = if (currentWeek < totalWeeks) arrowTint else arrowDisabled)
                }
            }
            // 圆点
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                val maxDots = minOf(totalWeeks, 25)
                for (w in 1..maxDots) {
                    Box(
                        modifier = Modifier.padding(horizontal = 2.dp)
                            .size(if (w == currentWeek) 7.dp else 4.5.dp)
                            .clip(RoundedCornerShape(50))
                            .background(
                                if (w == currentWeek) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            ).clickable { onWeekChange(w) }
                    )
                }
            }
            // 表头：周一~周日 + 日期（和上面拼一起，不用横线隔开）
            Row(modifier = Modifier.height(32.dp).padding(start = tw)) {
                for (d in 1..7) { key(d) {
                    val isToday = isTodayWeek && d == todayDow
                    Box(
                        modifier = Modifier.width(dw).fillMaxHeight()
                            .background(if (isToday) MaterialTheme.colorScheme.primary.copy(alpha = if(dark) 0.15f else 0.07f) else Color.Transparent),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(TimeUtils.getDayName(d), fontSize=12.sp,
                                fontWeight=if(isToday) FontWeight.Bold else FontWeight.Normal,
                                color=if(isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                lineHeight=14.sp)
                            Text(refDate(currentWeek,d,semesterStart), fontSize=10.sp,
                                color=if(isToday) MaterialTheme.colorScheme.primary.copy(alpha=0.7f) else dateTextColor,
                                lineHeight=12.sp)
                        }
                    }
                }}
            }
            // 分割线：导航区 ↓ 课表
            HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        }

        if (courses.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("📚", fontSize = 52.sp)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("还没有课程数据", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("点击下方导入Excel课表", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(28.dp))
                    Button(onClick = onImportClick, shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary), modifier = Modifier.height(48.dp)) {
                        Icon(Icons.Default.FileOpen, null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("导入课表", fontSize = 16.sp)
                    }
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(vSc)
                    .pointerInput(currentWeek) {
                        detectHorizontalDragGestures { _, dragAmount ->
                            if (dragAmount < -50f && currentWeek < totalWeeks) onWeekChange(currentWeek + 1)
                            else if (dragAmount > 50f && currentWeek > 1) onWeekChange(currentWeek - 1)
                        }
                    }
            ) {
                TableGrid(courses = weekCourses, colorMap = themeColorMap, tw = tw, ch = ch, currentWeek = currentWeek, onCourseClick = onCourseClick, periodTimes = periodTimes, dark = dark, cTextColor = cTextColor, cSubColor = cSubColor, todayWeek = todayWeek, todayDow = todayDow)
            }
        }
    }
}

// ============================
// ============================
// 每节次独立一格，不合并
// ============================

private data class Section(val name: String, val periods: List<Int>)

private fun buildSections(periodTimes: List<PeriodTime>): List<Section> {
    val total = periodTimes.size
    val morningEnd = minOf(4, total)
    val afternoonEnd = minOf(8, total)
    val result = mutableListOf<Section>()
    result.add(Section("上午", (1..morningEnd).toList()))
    if (afternoonEnd > morningEnd) result.add(Section("下午", (morningEnd+1..afternoonEnd).toList()))
    if (total > afternoonEnd) result.add(Section("晚上", (afternoonEnd+1..total).toList()))
    return result
}

@Composable
private fun TableGrid(
    courses: List<Course>, colorMap: Map<String, Color>,
    tw: Dp, ch: Dp, currentWeek: Int,
    onCourseClick: (Course) -> Unit,
    periodTimes: List<PeriodTime>,
    dark: Boolean, cTextColor: Color, cSubColor: Color,
    todayWeek: Int, todayDow: Int
) {
    val sections = remember(periodTimes) { buildSections(periodTimes) }

    // 主题色
    val gridBg = if (dark) DarkBackground else Color.White
    val timeText = if (dark) Color(0xFF888888) else Color(0xFF888888)
    val timeSub = if (dark) Color(0xFF666666) else Color(0xFFAAAAAA)
    val sectionBg = if (dark) Color(0xFF1A1F1C) else Color(0xFFF5F5F5)
    val sectionText = if (dark) DarkPrimaryDark.copy(alpha=0.5f) else PrimaryDark.copy(alpha=0.6f)

    val isTodayVisible = currentWeek == todayWeek

    // 按天分组课程，去重（每个课程只在首格出现一次）
    val dayCourses = remember(courses) {
        val map = mutableMapOf<Int, MutableList<Course>>()
        for (d in 1..7) map[d] = mutableListOf()
        courses.forEach { c -> map[c.dayOfWeek]?.add(c) }
        map
    }
    val totalH = remember(periodTimes, ch) { (ch * periodTimes.size) + (20.dp * sections.size) }

    Row(modifier = Modifier.fillMaxWidth().background(gridBg)) {
        // ===== 时间列 =====
        Column(modifier = Modifier.width(tw)) {
            for (si in sections.indices) {
                val section = sections[si]
                // 分段标签
                Box(modifier = Modifier.fillMaxWidth().height(20.dp).background(sectionBg),
                    contentAlignment = Alignment.Center) {
                    Text(section.name, fontSize=10.sp, fontWeight=FontWeight.Medium,
                        color=sectionText, lineHeight=14.sp)
                }
                for (p in section.periods) {
                    val pt = periodTimes.getOrNull(p - 1) ?: continue
                    Box(modifier = Modifier.fillMaxWidth().height(ch).padding(top=3.dp),
                        contentAlignment = Alignment.TopCenter) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(p.toString(), fontSize=12.sp, fontWeight=FontWeight.Medium,
                                color=timeText, lineHeight=16.sp)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(pt.start, fontSize=9.sp, color=timeSub, lineHeight=12.sp)
                            Text(pt.end, fontSize=9.sp, color=timeSub, lineHeight=12.sp)
                        }
                    }
                }
            }
        }

        // ===== 7 天列（Box 绝对定位课程卡片） =====
        for (d in 1..7) { key(d) {
            val isTodayCol = isTodayVisible && d == todayDow
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(totalH)
                    .background(if(isTodayCol) MaterialTheme.colorScheme.primary.copy(alpha = if(dark) 0.04f else 0.02f) else Color.Transparent)
            ) {
                // 分段标签横条
                for (si in sections.indices) {
                    var labelY = 0.dp
                    for (j in 0 until si) {
                        labelY += ch * sections[j].periods.size + 20.dp
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth().height(20.dp)
                            .offset(y = labelY)
                            .background(sectionBg.copy(alpha = 0.7f))
                    )
                }

                // 课程卡片
                val list = dayCourses[d] ?: emptyList()
                for (c in list) {
                    var cardY = 0.dp
                    for (si in 0 until sections.size) {
                        val sec = sections[si]
                        if (c.startPeriod > sec.periods.last()) {
                            cardY += ch * sec.periods.size + 20.dp
                        } else {
                            cardY += 20.dp
                            cardY += ch * (c.startPeriod - sec.periods.first())
                            break
                        }
                    }
                    val cardH = ch * c.periodSpan
                    val palette = if (dark) DarkCourseColors else CourseColors
                    val cardColor = when {
                        c.customColor != null && c.customColor in 0..14 -> palette[c.customColor]
                        c.customColor != null -> Color(c.customColor) // 旧 ARGB 兼容
                        else -> colorMap[c.courseName] ?: palette[0]
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .offset(y = cardY)
                            .height(cardH)
                            .shadow(2.dp, RoundedCornerShape(14.dp), clip = false)
                            .background(cardColor.copy(alpha = 0.78f), RoundedCornerShape(14.dp))
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(White.copy(alpha = 0.25f), White.copy(alpha = 0f)),
                                    start = Offset(0f, 0f),
                                    end = Offset(0f, 200f)
                                ),
                                RoundedCornerShape(14.dp)
                            )
                            .clickable { onCourseClick(c) }
                            .padding(start=6.dp, end=4.dp, top=4.dp, bottom=3.dp),
                        contentAlignment = Alignment.TopStart
                    ) {
                        if (c.periodSpan == 1) {
                            Column {
                                Text(c.courseName, fontWeight=FontWeight.Bold, fontSize=10.sp,
                                    maxLines=2, softWrap=true,
                                    color=cTextColor, lineHeight=13.sp)
                                if (c.teacher.isNotBlank())
                                    Text(c.teacher, fontSize=9.sp, maxLines=1, softWrap=true,
                                        color=cSubColor, lineHeight=12.sp)
                                if (c.classroom.isNotBlank())
                                    Text(c.classroom, fontSize=9.sp, maxLines=2, softWrap=true,
                                        color=cSubColor, lineHeight=12.sp)
                            }
                        } else {
                            Column {
                                Text(c.courseName, fontWeight=FontWeight.Bold, fontSize=13.sp,
                                    maxLines=6, softWrap=true,
                                    color=cTextColor, lineHeight=15.sp)
                                if (c.teacher.isNotBlank())
                                    Text(c.teacher, fontSize=10.sp, maxLines=1, softWrap=true,
                                        color=cSubColor, lineHeight=13.sp)
                                if (c.classroom.isNotBlank())
                                    Text(c.classroom, fontSize=10.sp, maxLines=6, softWrap=true,
                                        color=cSubColor, lineHeight=13.sp)
                            }
                        }
                    }
                }
            }
        }}
    }
}
