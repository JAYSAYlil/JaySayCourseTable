package com.jaysay.coursetable.data.repository

import com.jaysay.coursetable.data.model.Course
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * 全量 JSON 保存的性能护栏与瓶颈评估。
 *
 * 背景：每次编辑都会重写整个 tables.json。文档设定的上限是 2 万门课程，
 * 本测试按上限构造数据，测量编码与原子写入耗时，防止未来性能退化，
 * 也为“是否值得改为增量写入”提供实测依据（当前上限数据在预算内，维持全量写入）。
 */
class TableDataSavePerformanceTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun saveAndReload20000CoursesWithinTimeBudget() {
        val tables = buildTables(courseCount = 20_000)
        val repo = CourseRepository(temporaryFolder.root)

        val saveMillis = measureMillis { runBlocking { repo.saveAllTables(tables) } }
        val reloadMillis = measureMillis { runBlocking { repo.loadAllTables() } }

        println("TableDataSavePerformance: save=$saveMillis" + "ms reload=$reloadMillis" + "ms (20000 courses)")
        assertTrue("保存 2 万课程耗时 $saveMillis ms，超出预算", saveMillis < 5_000)
        assertTrue("载入 2 万课程耗时 $reloadMillis ms，超出预算", reloadMillis < 8_000)
    }

    private fun buildTables(courseCount: Int): List<TableData> {
        val perTable = 1_000
        val tableCount = courseCount / perTable
        return (0 until tableCount).map { t ->
            TableData(
                name = "压测课表$t",
                courses = (0 until perTable).map { c ->
                    Course(
                        courseId = "C$t-$c",
                        courseName = "性能压测课程$c",
                        classNumber = "班${c % 12 + 1}",
                        department = "测试学院",
                        credits = 2f,
                        weeks = (1..16).toList(),
                        dayOfWeek = c % 7 + 1,
                        startPeriod = c % 10 + 1,
                        endPeriod = c % 10 + 2,
                        teacher = "教师${c % 50}",
                        classroom = "教学楼${c % 20}栋${c % 60}室",
                        courseType = "必修",
                        courseCategory = "专业课",
                        isOnline = false,
                        assessmentMethod = "考试",
                        notes = "压力测试数据"
                    )
                }
            )
        }
    }

    private inline fun measureMillis(block: () -> Unit): Long {
        val start = System.nanoTime()
        block()
        return (System.nanoTime() - start) / 1_000_000
    }
}
