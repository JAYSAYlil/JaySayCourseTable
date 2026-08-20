package com.jaysay.coursetable.widget

import com.jaysay.coursetable.data.model.AgendaCourseSlot
import com.jaysay.coursetable.data.model.Course
import com.jaysay.coursetable.data.model.TodayAgenda
import com.jaysay.coursetable.data.model.TodayAgendaPhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.time.LocalDate

class WidgetPresentationTest {
    @Test
    fun nextCourseUsesCompactStructuredFieldsAndRespectsPrivacy() {
        val presentation = WidgetPresentation.create(
            tableName = "绿色课表",
            agenda = TodayAgenda(
                phase = TodayAgendaPhase.BEFORE_FIRST,
                week = 1,
                next = AgendaCourseSlot(course(), 10 * 60 + 10, 11 * 60 + 55)
            ),
            future = null,
            hideDetails = true,
            today = LocalDate.parse("2026-08-20")
        )

        assertEquals("8月20日 · 周四", presentation.dateLabel)
        assertEquals("下一节", presentation.status)
        assertEquals("虚构课程", presentation.courseName)
        assertEquals("10:10 开始", presentation.timeLabel)
        assertEquals("课程详情已隐藏", presentation.detail)
        assertFalse(presentation.detail.contains("A101"))
    }

    private fun course() = Course(
        courseId = "fictional", courseName = "虚构课程", classNumber = "", department = "", credits = 0f,
        weeks = listOf(1), dayOfWeek = 4, startPeriod = 1, endPeriod = 2, teacher = "虚构教师",
        classroom = "A101", courseType = "", courseCategory = "", isOnline = false,
        assessmentMethod = "", seriesId = "fictional"
    )
}
