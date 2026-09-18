package com.jaysay.coursetable

import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import com.jaysay.coursetable.data.model.Course
import com.jaysay.coursetable.data.model.CourseSeriesIds
import com.jaysay.coursetable.data.model.CourseSeriesOperations
import com.jaysay.coursetable.data.model.ScheduleViewMode
import com.jaysay.coursetable.data.parser.ExcelParser
import com.jaysay.coursetable.data.repository.CourseRepository
import com.jaysay.coursetable.util.TimeUtils
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 真实课表回归：仅在本机把一份真实课表样本推到设备路径 real-schedule.xlsx 后运行，
 * CI 没有该样本时自动跳过。测试只按课程自身的属性选课（不写任何真实课程名），
 * 样本数据不进仓库、不进 APK，用例结束会清空它写入的课程。
 */
class RealScheduleRegressionTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private fun model() = ViewModelProvider(rule.activity)[MainViewModel::class.java]

    private fun realCourses(): List<Course> {
        val descriptor = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
            .uiAutomation.executeShellCommand("cat /sdcard/Download/real-schedule.xlsx")
        val bytes = ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() }
        Assume.assumeTrue("缺少本机真实课表样本", bytes.size > 1_000)
        val target = File(rule.activity.cacheDir, "real-schedule.xlsx")
        target.writeBytes(bytes)
        return CourseSeriesIds.ensure(ExcelParser.parse(rule.activity, Uri.fromFile(target)).courses)
    }

    /** 按属性挑一场课：默认挑周次最多的一场，保证有“之前/之后”可区分。 */
    private fun pickMeeting(courses: List<Course>, day: Int? = null): Course {
        val pool = if (day == null) courses else courses.filter { it.dayOfWeek == day }
        assertTrue("真实样本里应能挑到可编辑的课", pool.isNotEmpty())
        return pool.maxByOrNull { it.weeks.size }!!
    }

    @Test
    fun realFileParsesIntoConsistentMeetings() {
        val courses = realCourses()
        assertTrue("真实课表应解析出多场课", courses.size >= 10)
        assertTrue("每场课都应有周次", courses.all { it.weeks.isNotEmpty() })
        assertTrue("每场课都应有星期与节次", courses.all { it.dayOfWeek in 1..7 && it.endPeriod >= it.startPeriod })
        // 同名不同时段的课是不同场次，不能因为同名而合成一条记录
        courses.groupBy { it.courseName }.values.filter { it.size > 1 }.forEach { sameName ->
            assertEquals("同名课的两场必须各自独立", sameName.size, sameName.map { it.seriesKey }.distinct().size)
        }
    }

    @Test
    fun everyRealMeetingReschedulesCleanly() {
        val courses = realCourses()
        courses.forEach { record ->
            val source = record.weeks.first()
            val target = if (source >= 18) source - 1 else source + 1
            val moved = record.copy(startPeriod = 11, endPeriod = 12)

            val result = CourseSeriesOperations.replaceCurrentWeekInstance(
                courses, record.seriesKey, week = source, edited = moved,
                selectedWeeks = setOf(target), existingWeeks = record.weeks.toSet()
            )

            val label = "day" + record.dayOfWeek + " " + record.startPeriod + "-" + record.endPeriod
            assertTrue(
                "[$label] 目标周应出现调过去的这一次课",
                result.any { it.seriesKey == record.seriesKey && it.weeks == listOf(target) && it.startPeriod == 11 }
            )
            assertTrue(
                "[$label] 原来那一周不该再有这场课",
                result.none { it.seriesKey == record.seriesKey && source in it.weeks }
            )
            assertEquals(
                "[$label] 除来源周以外其它周次必须原样保留",
                (record.weeks - source + target).distinct().sorted(),
                result.filter { it.seriesKey == record.seriesKey }.flatMap { it.weeks }.distinct().sorted()
            )
            if (target in record.weeks) {
                assertTrue(
                    "[$label] 目标周原本那一节不能被抹掉",
                    result.any {
                        it.seriesKey == record.seriesKey && target in it.weeks &&
                            it.startPeriod == record.startPeriod && it.endPeriod == record.endPeriod
                    }
                )
            }
            assertEquals(
                "[$label] 其它课程不得被改动",
                courses.filter { it.seriesKey != record.seriesKey },
                result.filter { it.seriesKey != record.seriesKey }
            )
        }
    }

    @Test
    fun everyRealMeetingMovesToSaturdayWhenSeriesScopeIsOn() {
        val courses = realCourses()
        courses.forEach { record ->
            val result = CourseSeriesOperations.replaceAll(courses, record.seriesKey, record.copy(dayOfWeek = 6))

            val series = result.filter { it.seriesKey == record.seriesKey }
            assertEquals("整门课应合并成一条记录", 1, series.size)
            assertEquals("应落在周六", "周六", TimeUtils.getDayName(series.single().dayOfWeek))
            assertEquals("周次不得变化", record.weeks, series.single().weeks)
            assertEquals("节次不得变化", record.startPeriod, series.single().startPeriod)
            assertEquals(
                "其它课程不得被改动",
                courses.filter { it.seriesKey != record.seriesKey },
                result.filter { it.seriesKey != record.seriesKey }
            )
        }
    }

    private fun seedSingleRealCourse(record: Course, anchorWeek: Int? = null) {
        val ready = AtomicBoolean(false)
        rule.runOnIdle {
            val model = model()
            model.setScheduleViewMode(ScheduleViewMode.WEEK) { throw it }
            model.updateCourses({ listOf(record) }, onComplete = { ready.set(true) }, onError = { throw it })
            model.setWeek(anchorWeek ?: record.weeks.first())
        }
        rule.waitUntil(20_000) { ready.get() }
        rule.waitUntil(20_000) {
            rule.onAllNodes(hasContentDescription(record.courseName, substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
        val card = hasContentDescription(record.courseName, substring = true) and
            hasAnyAncestor(hasTestTag("schedule-scroll"))
        rule.onAllNodes(card)[0].performScrollTo().performClick()
        rule.onNodeWithContentDescription("编辑").performClick()
        rule.onNodeWithText("学分").performScrollTo()
    }

    private fun toggleChip(tag: String) {
        val chip = rule.onNodeWithTag(tag)
        chip.performScrollTo()
        rule.waitForIdle()
        chip.performSemanticsAction(SemanticsActions.OnClick)
        rule.waitForIdle()
    }

    private fun saveEditorAndReadPersisted(): List<Course> {
        rule.onNodeWithTag("course-save-button").performClick()
        rule.waitUntil(20_000) {
            rule.onAllNodesWithText("检测到课程冲突").fetchSemanticsNodes().isNotEmpty() ||
                rule.onAllNodesWithTag("course-edit-dialog").fetchSemanticsNodes().isEmpty()
        }
        if (rule.onAllNodesWithText("检测到课程冲突").fetchSemanticsNodes().isNotEmpty()) {
            rule.onNodeWithText("仍然保存").performClick()
        }
        rule.waitUntil(20_000) { rule.onAllNodesWithTag("course-edit-dialog").fetchSemanticsNodes().isEmpty() }
        return runBlocking { CourseRepository(rule.activity).loadAllTables() }.flatMap { it.courses }
    }

    private fun clearSeededCourses() {
        val cleared = AtomicBoolean(false)
        rule.runOnIdle {
            model().updateCourses({ emptyList() }, onComplete = { cleared.set(true) }, onError = { throw it })
        }
        rule.waitUntil(20_000) { cleared.get() }
    }

    /** 真实编辑器：把这一场课从它的第一周调到另一个周次，编辑内容跟着走，目标周原有那一节保留。 */
    @Test
    fun realRecordReschedulesThroughTheEditorAndKeepsOtherWeeks() {
        val record = pickMeeting(realCourses())
        val source = record.weeks.first()
        val target = if (source == 2) 3 else 2
        seedSingleRealCourse(record)
        toggleChip("course-start-period-9")
        toggleChip("course-end-period-10")
        toggleChip("course-week-$source")
        toggleChip("course-week-$target")

        val persisted = saveEditorAndReadPersisted()
        val moved = persisted.first { it.startPeriod == 9 }
        assertEquals(listOf(target), moved.weeks)
        assertEquals(10, moved.endPeriod)
        assertTrue("原来那一周不该再有这场课", persisted.none { source in it.weeks })
        val original = persisted.first { it.startPeriod == record.startPeriod }
        assertEquals(record.weeks.filter { it != source }, original.weeks)
        clearSeededCourses()
    }

    /** 星期选择器用应用自己的星期名作为无障碍描述，这里按名字点击，不写死任何真实课名。 */
    private fun selectDay(day: Int) {
        val chip = rule.onNodeWithContentDescription(TimeUtils.getDayName(day))
        chip.performScrollTo()
        rule.waitForIdle()
        chip.performSemanticsAction(SemanticsActions.OnClick)
        rule.waitForIdle()
    }

    /** 真实编辑器：改到周六并打开“应用到全部周”→ 整门课所有周次一起搬。 */
    @Test
    fun movingToSaturdayWithSeriesScopeOnMovesEveryWeek() {
        val record = pickMeeting(realCourses())
        seedSingleRealCourse(record)
        selectDay(6)
        rule.onNodeWithTag("course-apply-to-all").performScrollTo().performClick().assertIsOn()

        val persisted = saveEditorAndReadPersisted()
        val moved = persisted.filter { it.courseName == record.courseName }
        assertEquals("整门课应只剩一条记录", 1, moved.size)
        assertEquals("应落在周六", TimeUtils.getDayName(6), TimeUtils.getDayName(moved.single().dayOfWeek))
        assertEquals("所有周次都要跟着走", record.weeks, moved.single().weeks)
        assertEquals("节次不变", record.startPeriod, moved.single().startPeriod)
        clearSeededCourses()
    }

    /** 对照：同样改到周六，但开关保持关闭 → 只有当前这一周那一次课搬走。 */
    @Test
    fun movingToSaturdayWithCurrentWeekScopeMovesOnlyThatWeek() {
        val record = pickMeeting(realCourses())
        val week = record.weeks.first()
        seedSingleRealCourse(record)
        selectDay(6)
        rule.onNodeWithTag("course-apply-to-all").performScrollTo().assertIsOff()

        val persisted = saveEditorAndReadPersisted()
        val onSaturday = persisted.filter { TimeUtils.getDayName(it.dayOfWeek) == TimeUtils.getDayName(6) }
        assertEquals("周六只应有当前这一周那一次课", listOf(week), onSaturday.single().weeks)
        val stillOriginal = persisted.filter { it.dayOfWeek == record.dayOfWeek }
        assertEquals("其余周次仍留在原星期，且不含当前周", record.weeks.filter { it != week }, stillOriginal.single().weeks)
        clearSeededCourses()
    }

    /** 新范围：从中间某一周起改到周六 → 之前不动，之后整段搬走。 */
    @Test
    fun applyingFromThisWeekOnwardKeepsEarlierWeeks() {
        val record = pickMeeting(realCourses())
        assertTrue("样本里应有一场跨度不少于 3 周的课", record.weeks.size >= 3)
        val anchor = record.weeks[record.weeks.size / 2]
        seedSingleRealCourse(record, anchorWeek = anchor)
        selectDay(6)
        toggleChip("course-start-period-9")
        toggleChip("course-end-period-10")
        rule.onNodeWithTag("course-apply-from-week").performScrollTo().performClick().assertIsOn()
        rule.onNodeWithTag("course-apply-to-all").assertIsOff()

        val persisted = saveEditorAndReadPersisted()
        val earlier = persisted.filter { it.dayOfWeek == record.dayOfWeek }
        assertEquals("该周之前必须原样保留", record.weeks.filter { it < anchor }, earlier.single().weeks)
        val onward = persisted.filter { TimeUtils.getDayName(it.dayOfWeek) == TimeUtils.getDayName(6) }
        assertEquals("该周及以后整段搬到周六", record.weeks.filter { it >= anchor }, onward.single().weeks)
        assertEquals(9, onward.single().startPeriod)
        clearSeededCourses()
    }
}
