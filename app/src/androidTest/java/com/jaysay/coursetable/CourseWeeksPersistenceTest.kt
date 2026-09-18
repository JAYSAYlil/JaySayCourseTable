package com.jaysay.coursetable

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import com.jaysay.coursetable.data.model.Course
import com.jaysay.coursetable.data.model.ScheduleViewMode
import com.jaysay.coursetable.data.repository.CourseRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean

/**
 * “这一次课”编辑的真实落盘回归：
 * 1. 周次没动 → 只改这一周，其它周次的课程原样保留，课程不会消失；
 * 2. 周次改了 → 这一次课整体挪到所选周次，编辑内容跟着一起走；
 * 3. 系列被拆成多条记录后，详情页必须命中点中的那一条。
 */
class CourseWeeksPersistenceTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private fun model() = ViewModelProvider(rule.activity)[MainViewModel::class.java]

    private fun seedCourses(vararg courses: Course) {
        val ready = AtomicBoolean(false)
        rule.runOnIdle {
            val model = model()
            model.setScheduleViewMode(ScheduleViewMode.WEEK) { throw it }
            model.updateCourses({ it + courses.toList() }, onComplete = { ready.set(true) }, onError = { throw it })
        }
        rule.waitUntil(20_000) { ready.get() }
    }

    private fun openEditorFromCard(courseName: String) {
        rule.waitUntil(20_000) { rule.onAllNodesWithTag("course-table-screen").fetchSemanticsNodes().isNotEmpty() }
        val card = hasContentDescription("$courseName，点击查看详情") and hasAnyAncestor(hasTestTag("schedule-scroll"))
        rule.onNode(card).performScrollTo().performClick()
        rule.onNodeWithContentDescription("编辑").performClick()
        rule.onNodeWithText("学分").performScrollTo()
    }

    private fun saveEditor() {
        rule.onNodeWithTag("course-save-button").performClick()
        rule.waitUntil(20_000) {
            rule.onAllNodesWithText("检测到课程冲突").fetchSemanticsNodes().isNotEmpty() ||
                rule.onAllNodesWithTag("course-edit-dialog").fetchSemanticsNodes().isEmpty()
        }
        if (rule.onAllNodesWithText("检测到课程冲突").fetchSemanticsNodes().isNotEmpty()) {
            rule.onNodeWithText("仍然保存").performClick()
        }
        rule.waitUntil(20_000) { rule.onAllNodesWithTag("course-edit-dialog").fetchSemanticsNodes().isEmpty() }
    }

    /** 滚动到位后直接用语义动作点击，避免命中被横向滚动容器吃掉或被相邻芯片抢走。 */
    private fun toggleWeekChip(week: Int) {
        val chip = rule.onNodeWithTag("course-week-$week")
        chip.performScrollTo()
        rule.waitForIdle()
        chip.performSemanticsAction(SemanticsActions.OnClick)
        rule.waitForIdle()
    }

    private fun persistedRecords(seriesId: String): List<Course> =
        runBlocking { CourseRepository(rule.activity).loadAllTables() }
            .flatMap { it.courses }.filter { it.seriesKey == seriesId }

    @Test
    fun editingWithoutTouchingWeeksKeepsTheCourseInThatWeek() {
        val seriesId = "weeks-keep-${System.nanoTime()}"
        val name = "本周编辑${System.nanoTime() % 100000}"
        val week = model().state.currentWeek
        seedCourses(Course(seriesId, name, "", "", 0f, listOf(week), 1, 1, 2,
            "", "", "", "", false, "", seriesId = seriesId))
        openEditorFromCard(name)
        rule.onNodeWithTag("course-name-input").performTextReplacement("改名后")

        saveEditor()

        val records = persistedRecords(seriesId)
        assertTrue("课程不能消失", records.isNotEmpty())
        assertEquals(listOf(week), records.single().weeks)
        assertEquals("改名后", records.single().courseName)
    }

    @Test
    fun changingWeeksMovesThisOccurrenceAndCarriesTheEdit() {
        val seriesId = "weeks-move-${System.nanoTime()}"
        val name = "搬周次${System.nanoTime() % 100000}"
        val week = model().state.currentWeek
        val target = if (week == 2) 3 else 2
        seedCourses(Course(seriesId, name, "", "", 0f, listOf(week), 1, 1, 2,
            "", "", "", "", false, "", seriesId = seriesId))
        openEditorFromCard(name)
        rule.onNodeWithTag("course-name-input").performTextReplacement("搬过来的课")
        // 把“这一次课”从当前周挪到目标周：取消当前周、勾选目标周（用户的操作）。
        toggleWeekChip(week)
        rule.onNodeWithTag("course-week-$week").assertIsNotSelected()
        toggleWeekChip(target)
        rule.onNodeWithTag("course-week-$target").assertIsSelected()

        saveEditor()

        val records = persistedRecords(seriesId)
        assertEquals("课程必须挪到目标周而不是消失", 1, records.size)
        assertEquals(listOf(target), records.single().weeks)
        assertEquals("编辑内容要跟着这一次课走", "搬过来的课", records.single().courseName)
    }

    @Test
    fun splitSeriesDetailShowsTheTappedWeekInsteadOfTheFirstRecord() {
        val seriesId = "weeks-split-${System.nanoTime()}"
        val name = "拆周定位${System.nanoTime() % 100000}"
        val otherWeeks = Course(seriesId, name, "", "", 0f, listOf(3, 4), 1, 3, 4,
            "", "", "", "", false, "", seriesId = seriesId)
        val secondWeek = otherWeeks.copy(weeks = listOf(2), startPeriod = 5, endPeriod = 6)
        seedCourses(otherWeeks, secondWeek)
        rule.waitUntil(20_000) { rule.onAllNodesWithTag("course-table-screen").fetchSemanticsNodes().isNotEmpty() }
        rule.runOnIdle { model().setWeek(3) }

        val card = hasContentDescription("$name，点击查看详情") and hasAnyAncestor(hasTestTag("schedule-scroll"))
        rule.onNode(card).performScrollTo().performClick()

        rule.onNodeWithText("课程详情").assertIsDisplayed()
        rule.onNodeWithText("3-4周").assertIsDisplayed()
        // 详情转场是手动 overlay 动画：等它跑完再结束用例，避免拆除 Activity 时打断转场。
        rule.waitForIdle()
    }
}