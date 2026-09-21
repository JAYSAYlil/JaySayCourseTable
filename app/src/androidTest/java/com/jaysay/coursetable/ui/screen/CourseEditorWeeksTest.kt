package com.jaysay.coursetable.ui.screen

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.text.TextLayoutResult
import com.jaysay.coursetable.data.model.Course
import com.jaysay.coursetable.ui.theme.JaySayTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/**
 * 课程编辑器的保存范围契约：
 * “应用到全部周”默认关闭 → 编辑只作用于“被编辑的这一周这一次课”；
 * 打开后 → 同一门课在其它周次一起变。周次在这一周这一次课上同样可以改（用于把这次课挪到别的周）。
 */
class CourseEditorWeeksTest {
    @get:Rule val rule = createComposeRule()
    private val original = Course("test-weeks", "测试周次课程", "", "", 0f,
        listOf(1, 2, 3, 4), 1, 1, 2, "", "", "", "", false, "", seriesId = "test-weeks")
    private var saved: Pair<Course, CourseEditScope>? = null
    private var deleted: CourseEditScope? = null

    private fun open(course: Course? = original, withDelete: Boolean = false): StateRestorationTester {
        val restoration = StateRestorationTester(rule)
        restoration.setContent {
            JaySayTheme {
                CourseEditDialog(course, totalWeeks = 4, currentWeek = 1,
                    onSave = { updated, all -> saved = updated to all },
                    onDelete = if (withDelete) { { scope -> deleted = scope } } else null,
                    onDismiss = {})
            }
        }
        return restoration
    }

    @Test fun weeksStayEditableAndEditingWeekIsShown() {
        open()
        rule.onNodeWithText("学分").performScrollTo()
        rule.onNodeWithText("课程当前所在：第 1 周").assertExists()
        rule.onNodeWithTag("course-week-2").assertIsEnabled().performScrollTo().performClick().assertIsNotSelected()
        rule.onNodeWithTag("course-save-button").performClick()
        rule.runOnIdle {
            assertEquals(listOf(1, 3, 4), saved!!.first.weeks)
            assertEquals("默认只改这一周", CourseEditScope.CURRENT_WEEK, saved!!.second)
        }
    }

    @Test fun defaultScopeOnlySendsCurrentWeekScopeAndKeepsWeeks() {
        open()
        rule.onNodeWithTag("course-name-input").performTextReplacement("本周新标题")
        rule.onNodeWithTag("course-save-button").performClick()
        rule.runOnIdle {
            assertEquals("本周新标题", saved!!.first.courseName)
            assertEquals(listOf(1, 2, 3, 4), saved!!.first.weeks)
            assertEquals(CourseEditScope.CURRENT_WEEK, saved!!.second)
        }
    }

    @Test fun seriesScopeDefaultOffAndToggleKeepsWorking() {
        open()
        rule.onNodeWithTag("course-apply-to-all").performScrollTo().assertIsOff().performClick().assertIsOn()
        rule.onNodeWithTag("course-week-2").performScrollTo().performClick()
        rule.onNodeWithTag("course-save-button").performClick()
        rule.runOnIdle { assertEquals("用户打开开关后整周次生效", CourseEditScope.ALL_WEEKS, saved!!.second) }
    }

    @Test fun scopeSwitchesAreMutuallyExclusive() {
        open()
        rule.onNodeWithTag("course-apply-from-week").performScrollTo().performClick().assertIsOn()
        rule.onNodeWithTag("course-apply-to-all").assertIsOff()
        rule.onNodeWithTag("course-apply-to-all").performScrollTo().performClick().assertIsOn()
        rule.onNodeWithTag("course-apply-from-week").assertIsOff()
        rule.onNodeWithTag("course-save-button").performClick()
        rule.runOnIdle { assertEquals(CourseEditScope.ALL_WEEKS, saved!!.second) }
    }

    @Test fun applyingFromThisWeekOnwardSendsItsOwnScope() {
        open()
        rule.onNodeWithTag("course-apply-from-week").performScrollTo().performClick().assertIsOn()
        rule.onNodeWithTag("course-save-button").performClick()
        rule.runOnIdle { assertEquals(CourseEditScope.FROM_CURRENT_WEEK, saved!!.second) }
    }

    @Test fun deleteButtonFollowsTheDefaultScopeAndSendsItBack() {
        open(original, withDelete = true)
        rule.onNodeWithTag("course-delete-button").assertTextEquals("删除本周")
        rule.onNodeWithTag("course-delete-button").performClick()
        rule.onNodeWithText("只从第 1 周移除“测试周次课程”，其他周次不受影响。删除后可在提示条中撤销。").assertIsDisplayed()
        rule.onNodeWithText("确认删除").performClick()
        rule.runOnIdle { assertEquals(CourseEditScope.CURRENT_WEEK, deleted) }
    }

    @Test fun deleteFromThisWeekOnwardLabelFitsAndSendsTheScope() {
        open(original, withDelete = true)
        rule.onNodeWithTag("course-apply-from-week").performScrollTo().performClick().assertIsOn()
        rule.onNodeWithTag("course-delete-button").assertTextEquals("删除本周起")
        // 最长的删除文案在窄屏弹窗里必须完整显示，不能被裁掉。
        val layouts = mutableListOf<TextLayoutResult>()
        val labelNode = rule.onNodeWithText("删除本周起", useUnmergedTree = true)
        labelNode.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertTrue("删除按钮文案必须能被测量", layouts.isNotEmpty())
        val deleteLabel = layouts.single()
        assertEquals("最长的删除文案必须保持单行", 1, deleteLabel.lineCount)
        assertTrue(
            "删除文案宽度必须落在按钮可用宽度内：size=" + deleteLabel.size +
                ", maxWidth=" + deleteLabel.layoutInput.constraints.maxWidth,
            deleteLabel.size.width <= deleteLabel.layoutInput.constraints.maxWidth
        )
        val labelBounds = labelNode.getUnclippedBoundsInRoot()
        val buttonBounds = rule.onNodeWithTag("course-delete-button").getUnclippedBoundsInRoot()
        assertTrue(
            "删除文案必须完整落在删除按钮内：label=" + labelBounds + ", button=" + buttonBounds,
            labelBounds.left >= buttonBounds.left && labelBounds.right <= buttonBounds.right
        )
        rule.onNodeWithTag("course-delete-button").performClick()
        rule.onNodeWithText("将从第 1 周起移除“测试周次课程”的后续周次，第 1 周之前的周次不受影响。删除后可在提示条中撤销。")
            .assertIsDisplayed()
        rule.onNodeWithText("确认删除").performClick()
        rule.runOnIdle { assertEquals(CourseEditScope.FROM_CURRENT_WEEK, deleted) }
    }

    @Test fun deleteAllLabelFollowsTheSeriesScope() {
        open(original, withDelete = true)
        rule.onNodeWithTag("course-apply-to-all").performScrollTo().performClick().assertIsOn()
        rule.onNodeWithTag("course-delete-button").assertTextEquals("删除全部")
        rule.onNodeWithTag("course-delete-button").performClick()
        rule.onNodeWithText("将删除“测试周次课程”的全部周次，删除后可在提示条中撤销。").assertIsDisplayed()
        rule.onNodeWithText("确认删除").performClick()
        rule.runOnIdle { assertEquals(CourseEditScope.ALL_WEEKS, deleted) }
    }

    @Test fun newCourseHasNoDeleteAction() {
        open(null, withDelete = true)
        rule.onNodeWithTag("course-delete-button").assertDoesNotExist()
    }

    @Test fun clearingAllWeeksStaysEmptyAndCannotSave() {
        val restoration = open()
        rule.onNodeWithText("学分").performScrollTo()
        for (week in 1..4) rule.onNodeWithTag("course-week-$week").performScrollTo().performClick()
        for (week in 1..4) rule.onNodeWithTag("course-week-$week").assertIsNotSelected()
        restoration.emulateSavedInstanceStateRestore()
        for (week in 1..4) rule.onNodeWithTag("course-week-$week").assertIsNotSelected()
        rule.onNodeWithTag("course-save-button").performClick()
        rule.onNodeWithText("请至少选择一个上课周次").assertIsDisplayed()
        rule.runOnIdle { assertNull(saved) }
        rule.onNodeWithText("学分").performScrollTo()
        rule.onNodeWithTag("course-week-3").performScrollTo().performClick().assertIsSelected()
        rule.onNodeWithTag("course-save-button").performClick()
        rule.runOnIdle { assertEquals(listOf(3), saved!!.first.weeks) }
    }

    /** 一键清空全部周次：省去逐个取消，且清空是未完成的编辑，不得悄悄回退成整学期。 */
    @Test fun clearButtonEmptiesEveryWeekInOneTap() {
        open()
        rule.onNodeWithText("学分").performScrollTo()
        rule.onNodeWithTag("course-weeks-clear").performScrollTo().assertIsNotSelected()
        rule.onNodeWithTag("course-weeks-clear").performClick()
        for (week in 1..4) rule.onNodeWithTag("course-week-$week").assertIsNotSelected()
        rule.onNodeWithTag("course-weeks-clear").assertIsSelected()
        rule.onNodeWithTag("course-save-button").performClick()
        rule.onNodeWithText("请至少选择一个上课周次").assertIsDisplayed()
        rule.runOnIdle { assertNull(saved) }
        rule.onNodeWithText("全学期").performScrollTo().performClick().assertIsSelected()
        rule.onNodeWithTag("course-weeks-clear").assertIsNotSelected()
        rule.onNodeWithTag("course-save-button").performClick()
        rule.runOnIdle { assertEquals(listOf(1, 2, 3, 4), saved!!.first.weeks) }
    }

    @Test fun oddEvenShortcutsSaveTheirExactWeeks() {        open()
        rule.onNodeWithText("学分").performScrollTo()
        rule.onNodeWithText("单周").performScrollTo().performClick()
        rule.onNodeWithTag("course-save-button").performClick()
        rule.runOnIdle { assertEquals(listOf(1, 3), saved!!.first.weeks) }
        rule.onNodeWithText("双周").performScrollTo().performClick()
        rule.onNodeWithTag("course-save-button").performClick()
        rule.runOnIdle { assertEquals(listOf(2, 4), saved!!.first.weeks) }
    }

    @Test fun newCourseStillStartsWithWholeSemesterSelected() {
        open(null)
        rule.onNodeWithTag("course-name-input").performTextInput("新增周次课程")
        rule.onNodeWithTag("course-save-button").performClick()
        rule.runOnIdle { assertEquals(listOf(1, 2, 3, 4), saved!!.first.weeks) }
    }
}