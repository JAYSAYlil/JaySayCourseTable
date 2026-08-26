package com.jaysay.coursetable.widget

import com.jaysay.coursetable.data.model.Course
import com.jaysay.coursetable.data.model.ScheduleDateException
import com.jaysay.coursetable.data.model.ScheduleExceptionType
import com.jaysay.coursetable.data.repository.TableData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class WidgetPresentationTest {
    @Test
    fun collectionItemIdsAreStablePerCourseDateAndTime() {
        val date = LocalDate.parse("2026-08-20")
        val row = WidgetCourseRow(0, "series", "课程", "教室", "教师", "08:00–09:35")

        assertEquals(row.stableId(date), row.copy(tableIndex = 3).stableId(date))
        assertTrue(row.stableId(date) != row.copy(timeLabel = "10:00–11:35").stableId(date))
        assertTrue(row.stableId(date) != row.stableId(date.plusDays(1)))
    }

    @Test
    fun widthModesMatchThreeFourAndFiveColumnLayouts() {
        assertEquals(WidgetWidthMode.COMPACT, WidgetWidthMode.fromMinWidth(180))
        assertEquals(WidgetWidthMode.COMPACT, WidgetWidthMode.fromMinWidth(219))
        assertEquals(WidgetWidthMode.MEDIUM, WidgetWidthMode.fromMinWidth(220))
        assertEquals(WidgetWidthMode.MEDIUM, WidgetWidthMode.fromMinWidth(269))
        assertEquals(WidgetWidthMode.EXPANDED, WidgetWidthMode.fromMinWidth(270))
        assertEquals(WidgetWidthMode.EXPANDED, WidgetWidthMode.fromMinWidth(320))
    }

    @Test
    fun scheduleKeepsCompleteImportantFieldsAndSortsByPeriod() {
        val longName = "移动应用程序设计与跨平台开发综合实践课程"
        val longClassroom = "博学楼东区十二层智慧互动实验室A1208"
        val longTeacher = "欧阳示例教师与联合授课教师"
        val table = table(
            courses = listOf(
                course("later", "第二节课", startPeriod = 3, endPeriod = 4),
                course(
                    "first",
                    longName,
                    startPeriod = 1,
                    endPeriod = 2,
                    classroom = longClassroom,
                    teacher = longTeacher
                )
            )
        )

        val result = WidgetScheduleBuilder.build(table, tableIndex = 2, date = LocalDate.parse("2026-08-20"))

        assertEquals(listOf(longName, "第二节课"), result.courses.map { it.courseName })
        assertEquals(longClassroom, result.courses.first().classroom)
        assertEquals(longTeacher, result.courses.first().teacher)
        assertEquals("08:00–09:35", result.courses.first().timeLabel)
        assertEquals(2, result.courses.first().tableIndex)
    }

    @Test
    fun missingImportantFieldsUseVisibleFallbacks() {
        val result = WidgetScheduleBuilder.build(
            table(courses = listOf(course("blank", " ", classroom = "", teacher = "  "))),
            tableIndex = 0,
            date = LocalDate.parse("2026-08-20")
        ).courses.single()

        assertEquals("未命名课程", result.courseName)
        assertEquals("未填写教室", result.classroom)
        assertEquals("未填写教师", result.teacher)
    }

    @Test
    fun dateExceptionsAreReflectedInWidgetSchedule() {
        val date = LocalDate.parse("2026-08-20")
        val regular = course("regular", "常规课程")
        val makeup = course("makeup", "补课课程", startPeriod = 5, endPeriod = 6)
        val table = table(
            courses = listOf(regular),
            exceptions = listOf(
                ScheduleDateException(date = date.toString(), type = ScheduleExceptionType.DAY_OFF),
                ScheduleDateException(
                    date = date.toString(),
                    type = ScheduleExceptionType.MAKEUP,
                    makeupCourse = makeup
                )
            )
        )

        assertEquals(
            listOf("补课课程"),
            WidgetScheduleBuilder.build(table, 0, date).courses.map { it.courseName }
        )
    }

    @Test
    fun widgetExplainsWeekLabelsSuspensionsAndDateAdjustments() {
        val date = LocalDate.parse("2026-08-26")
        val schedule = WidgetScheduleBuilder.build(
            table(
                courses = listOf(course("regular", "常规课程")),
                exceptions = listOf(
                    ScheduleDateException(
                        date = date.toString(),
                        type = ScheduleExceptionType.DAY_OFF,
                        title = "校庆"
                    )
                ),
                excludedWeeks = listOf(2),
                weekLabels = mapOf(2 to "实践周")
            ),
            tableIndex = 0,
            date = date
        )

        assertEquals("星期三 · 实践周", WidgetCalendarPresentation.headerBadge("星期三", schedule))
        assertEquals(
            "今日 · 停课周",
            WidgetCalendarPresentation.sectionTitle("今日课程", date, 0, WidgetWidthMode.COMPACT, schedule)
        )
        assertEquals("停课周\n本日无课程", WidgetCalendarPresentation.emptyText("今日无课", schedule))
    }

    @Test
    fun todayScheduleHidesEndedCoursesButKeepsCurrentAndUpcomingCourses() {
        val table = table(
            courses = listOf(
                course("ended", "已结束", startPeriod = 1, endPeriod = 2),
                course("current", "进行中", startPeriod = 3, endPeriod = 4),
                course("upcoming", "未开始", startPeriod = 5, endPeriod = 6)
            )
        )

        val result = WidgetScheduleBuilder.build(
            table,
            tableIndex = 0,
            date = LocalDate.parse("2026-08-20"),
            afterMinute = 10 * 60
        )

        assertEquals(listOf("进行中", "未开始"), result.courses.map { it.courseName })
    }

    @Test
    fun courseDisappearsExactlyAtItsEndTime() {
        val table = table(courses = listOf(course("ended", "已结束", startPeriod = 1, endPeriod = 2)))

        val result = WidgetScheduleBuilder.build(
            table,
            tableIndex = 0,
            date = LocalDate.parse("2026-08-20"),
            afterMinute = 9 * 60 + 35
        )

        assertEquals(emptyList<WidgetCourseRow>(), result.courses)
    }

    private fun table(
        courses: List<Course>,
        exceptions: List<ScheduleDateException> = emptyList(),
        excludedWeeks: List<Int> = emptyList(),
        weekLabels: Map<Int, String> = emptyMap()
    ) = TableData(
        name = "测试课表",
        courses = courses,
        periods = TableData.defaultPeriods().mapIndexed { index, period ->
            if (index == 0) period.copy(start = "08:00")
            else if (index == 1) period.copy(end = "09:35")
            else period
        },
        semesterStart = "2026-08-17",
        totalWeeks = 20,
        excludedWeeks = excludedWeeks,
        dateExceptions = exceptions,
        weekLabels = weekLabels
    )

    private fun course(
        series: String,
        name: String,
        startPeriod: Int = 1,
        endPeriod: Int = 2,
        classroom: String = "示例教室",
        teacher: String = "示例教师"
    ) = Course(
        courseId = series,
        courseName = name,
        classNumber = "",
        department = "",
        credits = 0f,
        weeks = listOf(1),
        dayOfWeek = 4,
        startPeriod = startPeriod,
        endPeriod = endPeriod,
        teacher = teacher,
        classroom = classroom,
        courseType = "",
        courseCategory = "",
        isOnline = false,
        assessmentMethod = "",
        seriesId = series
    )
}
