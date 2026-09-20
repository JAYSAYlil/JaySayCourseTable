package com.jaysay.coursetable.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CourseSeriesOperationsTest {
    @Test
    fun legacyWeekSplitsReceiveSameSeriesAndDeleteAllWorksFirstTime() {
        val migrated = CourseSeriesIds.ensure(
            listOf(course(weeks = (1..9).toList()), course(weeks = listOf(10)))
        )

        assertEquals(migrated[0].seriesKey, migrated[1].seriesKey)
        assertTrue(CourseSeriesOperations.deleteAll(migrated, migrated[0].seriesKey).isEmpty())
    }

    @Test
    fun legacySplitMovedToAnotherDayIsStillRecognized() {
        val migrated = CourseSeriesIds.ensure(
            listOf(course(weeks = (1..9).toList()), course(weeks = listOf(10), day = 3))
        )

        assertEquals(migrated[0].seriesKey, migrated[1].seriesKey)
    }

    @Test
    fun overlappingMeetingsOfSameCourseRemainSeparateSeries() {
        val migrated = CourseSeriesIds.ensure(
            listOf(course(weeks = (1..10).toList()), course(weeks = (1..10).toList(), day = 3))
        )

        assertNotEquals(migrated[0].seriesKey, migrated[1].seriesKey)
    }

    @Test
    fun applyAllReplacesEverySplitWithOneUnifiedCourse() {
        val split = CourseSeriesIds.ensure(
            listOf(course(weeks = (1..9).toList()), course(weeks = listOf(10)))
        )
        val replacement = split[0].copy(classroom = "新教室", weeks = (1..10).toList())

        val result = CourseSeriesOperations.replaceAll(split, split[0].seriesKey, replacement)

        assertEquals(1, result.size)
        assertEquals((1..10).toList(), result.single().weeks)
        assertEquals("新教室", result.single().classroom)
        assertEquals(split[0].seriesKey, result.single().seriesKey)
    }

    @Test
    fun targetedUndoPreservesUnrelatedChanges() {
        val selected = course(weeks = listOf(1, 2), seriesId = "selected")
        val unrelated = course(id = "C002", name = "另一门课", weeks = listOf(1), seriesId = "other")
        val laterAddition = course(id = "C003", name = "后来新增", weeks = listOf(1), seriesId = "later")
        val before = listOf(selected, unrelated)
        val after = CourseSeriesOperations.deleteWeek(before, "selected", 1)
        val undo = CourseSeriesUndo.capture(before, after, "selected")

        val restored = undo.restore(after + laterAddition)

        assertEquals(listOf(selected, unrelated, laterAddition), restored)
    }

    @Test
    fun sameCourseNameAcrossWeekRangesSharesOneSeriesEvenWithDifferentCourseIds() {
        // 同一门课在不同周次可能带不同课程号/教师/教室，必须仍然算同一门课。
        val migrated = CourseSeriesIds.ensure(
            listOf(
                course(id = "A-1", weeks = (1..8).toList(), seriesId = "first"),
                course(id = "A-2", weeks = (9..16).toList(), seriesId = "second")
            )
        )

        assertEquals(migrated[0].seriesKey, migrated[1].seriesKey)
    }

    @Test
    fun differentCourseNamesAtTheSameSlotStaySeparate() {
        val migrated = CourseSeriesIds.ensure(
            listOf(
                course(name = "课程甲", weeks = listOf(1), seriesId = "a"),
                course(name = "课程乙", weeks = listOf(1), seriesId = "b")
            )
        )

        assertNotEquals(migrated[0].seriesKey, migrated[1].seriesKey)
    }

    @Test
    fun unchangedWeeksOnlyRewriteTheEditedWeek() {
        val series = CourseSeriesIds.ensure(listOf(course(weeks = (1..4).toList(), seriesId = "s")))
        val edited = series[0].copy(classroom = "新教室")

        val result = CourseSeriesOperations.replaceCurrentWeekInstance(
            series, "s", week = 2, edited = edited,
            selectedWeeks = setOf(1, 2, 3, 4), existingWeeks = setOf(1, 2, 3, 4)
        )

        assertEquals(2, result.size)
        val current = result.single { 2 in it.weeks }
        assertEquals(listOf(2), current.weeks)
        assertEquals("新教室", current.classroom)
        val others = result.single { 2 !in it.weeks }
        assertEquals(listOf(1, 3, 4), others.weeks)
        assertEquals("教室", others.classroom)
    }

    @Test
    fun movingTheEditedWeekKeepsTheTargetWeekOriginalInstanceAndCarriesTheEdit() {
        // 用户场景：这门课排在第 1-4 周周四 3-4 节；把第 1 周这一次调到第 2 周 9-10 节。
        val series = CourseSeriesIds.ensure(
            listOf(course(weeks = (1..4).toList(), seriesId = "s").copy(startPeriod = 3, endPeriod = 4))
        )
        val edited = series[0].copy(startPeriod = 9, endPeriod = 10)

        val result = CourseSeriesOperations.replaceCurrentWeekInstance(
            series, "s", week = 1, edited = edited,
            selectedWeeks = setOf(2), existingWeeks = setOf(1, 2, 3, 4)
        )

        assertEquals(2, result.size)
        // 第 2 周原本那一节（3-4 节）必须保留，且仍在第 2-4 周
        val original = result.first { it.startPeriod == 3 }
        assertEquals(listOf(2, 3, 4), original.weeks)
        // 被调过来的这一次课落在第 2 周 9-10 节
        val moved = result.first { it.startPeriod == 9 }
        assertEquals(listOf(2), moved.weeks)
        assertEquals(10, moved.endPeriod)
        assertTrue("第 1 周不再有这门课", result.none { 1 in it.weeks })
    }

    @Test
    fun applyingFromThisWeekOnwardOnlyRewritesThatWeekAndLater() {
        // 第 1-6 周周一 1-2 节；从第 4 周起改到周六 3-4 节 → 第 1-3 周保持原样，第 4-6 周用新值
        val series = CourseSeriesIds.ensure(listOf(course(weeks = (1..6).toList(), seriesId = "s")))
        val edited = series[0].copy(dayOfWeek = 6, startPeriod = 3, endPeriod = 4)

        val result = CourseSeriesOperations.replaceFromWeekOnward(
            series, "s", week = 4, edited = edited, selectedWeeks = setOf(1, 2, 3, 4, 5, 6)
        )

        val earlier = result.single { it.weeks.any { current -> current < 4 } }
        assertEquals(listOf(1, 2, 3), earlier.weeks)
        assertEquals(1, earlier.dayOfWeek)
        val onward = result.single { it.weeks.all { current -> current >= 4 } }
        assertEquals(listOf(4, 5, 6), onward.weeks)
        assertEquals(6, onward.dayOfWeek)
        assertEquals(3, onward.startPeriod)
    }

    @Test
    fun applyingFromThisWeekOnwardKeepsUncheckedLaterWeeksOut() {
        // 在第 4 周取消勾选第 6 周 → 第 6 周不再排这门课，第 1-3 周仍然保留
        val series = CourseSeriesIds.ensure(listOf(course(weeks = (1..6).toList(), seriesId = "s")))

        val result = CourseSeriesOperations.replaceFromWeekOnward(
            series, "s", week = 4, edited = series[0].copy(dayOfWeek = 6), selectedWeeks = setOf(1, 2, 3, 4, 5)
        )

        assertEquals(listOf(1, 2, 3), result.single { it.weeks.any { current -> current < 4 } }.weeks)
        assertEquals(listOf(4, 5), result.single { it.weeks.all { current -> current >= 4 } }.weeks)
    }

    @Test
    fun editingTheOnlyWeekNeverRemovesTheCourse() {
        val series = CourseSeriesIds.ensure(listOf(course(weeks = listOf(2), seriesId = "s")))

        val result = CourseSeriesOperations.replaceCurrentWeekInstance(
            series, "s", week = 2, edited = series[0].copy(classroom = "新教室"),
            selectedWeeks = setOf(2), existingWeeks = setOf(2)
        )

        assertEquals(1, result.size)
        assertEquals(listOf(2), result.single().weeks)
        assertEquals("新教室", result.single().classroom)
    }

    @Test
    fun movingTheOnlyWeekAlsoKeepsTheCourse() {
        val series = CourseSeriesIds.ensure(listOf(course(weeks = listOf(1), seriesId = "s")))

        val result = CourseSeriesOperations.replaceCurrentWeekInstance(
            series, "s", week = 1, edited = series[0].copy(startPeriod = 9, endPeriod = 10),
            selectedWeeks = setOf(2), existingWeeks = setOf(1)
        )

        assertEquals(1, result.size)
        assertEquals(listOf(2), result.single().weeks)
        assertEquals(9, result.single().startPeriod)
    }

    @Test
    fun deletingFromThisWeekOnwardKeepsOnlyEarlierWeeksOfThatSeries() {
        // 第 1-8 周的课：从第 5 周起删除 → 只剩第 1-4 周，别的课程一根汗毛都不动。
        val series = listOf(course(weeks = (1..8).toList(), seriesId = "s"))
        val unrelated = course(id = "C002", name = "另一门课", weeks = (1..8).toList(), seriesId = "other")

        val result = CourseSeriesOperations.deleteFromWeekOnward(series + unrelated, "s", week = 5)

        assertEquals(listOf(1, 2, 3, 4), result.single { it.seriesKey == "s" }.weeks)
        assertEquals((1..8).toList(), result.single { it.seriesKey == "other" }.weeks)
    }

    @Test
    fun deletingFromThisWeekOnwardDropsSplitRecordsThatLoseEveryWeek() {
        // 系列被拆成两条记录（第 1-9 周与第 10 周）：从第 9 周起删除 → 第二条整条消失，第一条只剩 1-8。
        val split = listOf(
            course(id = "C001", weeks = (1..9).toList(), seriesId = "split"),
            course(id = "C002", weeks = listOf(10), seriesId = "split", day = 3)
        )

        val result = CourseSeriesOperations.deleteFromWeekOnward(split, "split", week = 9)

        assertEquals(1, result.size)
        assertEquals((1..8).toList(), result.single().weeks)
    }

    @Test
    fun deletingFromThisWeekOnwardRemovesTheCourseWhenEveryWeekIsOnward() {
        val series = listOf(course(weeks = listOf(6, 7), seriesId = "s"))

        assertTrue(
            "全部周次都在锚点之后时，这门课整体消失",
            CourseSeriesOperations.deleteFromWeekOnward(series, "s", week = 6).isEmpty()
        )
    }

    @Test
    fun deletingCurrentWeekStillOnlyRemovesThatWeek() {
        val series = listOf(course(weeks = (1..4).toList(), seriesId = "s"))

        val result = CourseSeriesOperations.deleteWeek(series, "s", week = 2)

        assertEquals(listOf(1, 3, 4), result.single().weeks)
    }

    @Test
    fun targetedUndoRestoresFromWeekOnwardDeletion() {
        val selected = course(weeks = (1..6).toList(), seriesId = "selected")
        val before = listOf(
            selected,
            course(id = "C002", name = "另一门课", weeks = listOf(1), seriesId = "other")
        )
        val after = CourseSeriesOperations.deleteFromWeekOnward(before, "selected", week = 4)
        val undo = CourseSeriesUndo.capture(before, after, "selected")

        assertEquals(before, undo.restore(after))
    }

    private fun course(
        id: String = "C001",
        name: String = "课程",
        weeks: List<Int>,
        day: Int = 1,
        seriesId: String = ""
    ) = Course(
        courseId = id,
        courseName = name,
        classNumber = "01",
        department = "学院",
        credits = 2f,
        weeks = weeks,
        dayOfWeek = day,
        startPeriod = 1,
        endPeriod = 2,
        teacher = "教师",
        classroom = "教室",
        courseType = "必修",
        courseCategory = "专业",
        isOnline = false,
        assessmentMethod = "考试",
        seriesId = seriesId
    )
}