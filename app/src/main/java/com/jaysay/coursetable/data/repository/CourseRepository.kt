package com.jaysay.coursetable.data.repository

import android.content.Context
import androidx.compose.runtime.Immutable
import com.jaysay.coursetable.data.model.Course
import com.jaysay.coursetable.data.model.CourseSeriesIds
import com.jaysay.coursetable.data.model.ScheduleViewMode
import com.jaysay.coursetable.data.preferences.PeriodTime
import com.jaysay.coursetable.data.preferences.defaultPeriodTimes
import com.jaysay.coursetable.data.storage.AtomicFileStore
import com.jaysay.coursetable.util.TimeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

@Immutable
data class TableData(
    val name: String,
    val courses: List<Course>,
    val periods: List<PeriodTime> = defaultPeriods(),
    val semesterStart: String = TimeUtils.todayDate(),
    val totalWeeks: Int = 20,
    val viewMode: ScheduleViewMode = ScheduleViewMode.WEEK
) {
    companion object {
        fun defaultPeriods() = defaultPeriodTimes()

        /** 活动课表下标越界时的占位课表。 */
        fun placeholder(name: String = "课表1") =
            TableData(name, emptyList(), semesterStart = TimeUtils.todayDate())
    }
}

class CourseRepository(context: Context) {
    private val store = AtomicFileStore(File(context.filesDir, "tables.json"))

    suspend fun loadAllTables(): List<TableData> = withContext(Dispatchers.IO) {
        store.read(::parseTables)?.let(TableDataJson::normalize) ?: listOf(TableData.placeholder())
    }

    suspend fun saveAllTables(tables: List<TableData>) = withContext(Dispatchers.IO) {
        store.write(encodeTables(tables))
    }

    /** 仅供已经由 [com.jaysay.coursetable.data.backup.BackupCodec] 严格校验的完整备份恢复。 */
    suspend fun restoreValidatedTables(tables: List<TableData>) = withContext(Dispatchers.IO) {
        store.replaceWithValidated(encodeTables(tables))
    }

    suspend fun deleteTable(index: Int): List<TableData> {
        val tables = loadAllTables().toMutableList()
        if (tables.size > 1 && index in tables.indices) tables.removeAt(index)
        saveAllTables(tables)
        return tables
    }

    private fun parseTables(text: String): List<TableData> {
        val trimmed = text.trimStart()
        val array = if (trimmed.startsWith("[")) {
            // 兼容 2.2.2 及更早版本的顶层数组格式。
            JSONArray(text)
        } else {
            JSONObject(text).optJSONArray("tables") ?: error("课表数据缺少 tables 数组")
        }
        require(array.length() > 0) { "课表数据不能为空" }
        return TableDataJson.fromJson(array, requireEveryRowValid = true)
    }

    private fun encodeTables(tables: List<TableData>): String {
        val normalized = TableDataJson.normalize(tables)
        return JSONObject()
            .put("schemaVersion", SCHEMA_VERSION)
            .put("tables", TableDataJson.toJson(normalized))
            .toString(2)
    }

    private companion object {
        const val SCHEMA_VERSION = 3
    }
}

/** 课表 JSON 的唯一编解码入口，应用存储和用户备份共用，避免格式逐渐分叉。 */
object TableDataJson {
    private const val MAX_WEEKS = 30
    private const val MAX_PERIODS = 30
    private val TIME_PATTERN = Regex("^(?:[01]\\d|2[0-3]):[0-5]\\d$")

    fun fromJson(array: JSONArray, requireEveryRowValid: Boolean = false): List<TableData> {
        val decoded = (0 until array.length()).mapNotNull { index ->
            runCatching { parseTable(array.getJSONObject(index), requireEveryRowValid) }.getOrNull()
        }
        require(!requireEveryRowValid || decoded.size == array.length()) { "备份中存在损坏的课表" }
        val normalized = normalize(decoded)
        require(!requireEveryRowValid || normalized == decoded) { "备份数据超出应用支持范围" }
        return normalized
    }

    fun toJson(tables: List<TableData>): JSONArray = JSONArray().apply {
        normalize(tables).forEach { put(tableToJson(it)) }
    }

    private fun parseTable(obj: JSONObject, strict: Boolean): TableData {
        val coursesArray = obj.optJSONArray("courses") ?: JSONArray()
        val courses = CourseSeriesIds.ensure((0 until coursesArray.length()).mapNotNull { index ->
            runCatching { parseCourse(coursesArray.getJSONObject(index)) }.getOrNull()
        })
        require(!strict || courses.size == coursesArray.length()) { "备份中存在损坏的课程" }
        val periodsArray = obj.optJSONArray("periods")
        val periods = if (periodsArray != null && periodsArray.length() > 0) {
            (0 until periodsArray.length()).mapNotNull { index ->
                runCatching {
                    val period = periodsArray.getJSONObject(index)
                    PeriodTime(period.getString("start"), period.getString("end"))
                }.getOrNull()
            }.also {
                require(!strict || it.size == periodsArray.length()) { "备份中存在损坏的节次" }
            }.ifEmpty { TableData.defaultPeriods() }
        } else {
            TableData.defaultPeriods()
        }
        val viewModeName = obj.optString("viewMode", ScheduleViewMode.WEEK.name)
        val viewMode = runCatching { ScheduleViewMode.valueOf(viewModeName) }.getOrElse {
            require(!strict) { "备份中的视图模式无效" }
            ScheduleViewMode.WEEK
        }
        return TableData(
            name = obj.optString("name", "课表"),
            courses = courses,
            periods = periods,
            semesterStart = obj.optString("semesterStart", TimeUtils.todayDate()),
            totalWeeks = obj.optInt("totalWeeks", 20),
            viewMode = viewMode
        )
    }

    private fun parseCourse(obj: JSONObject): Course {
        val weeksArray = obj.optJSONArray("weeks") ?: JSONArray()
        val weeks = (0 until weeksArray.length()).mapNotNull { index ->
            runCatching { weeksArray.getInt(index) }.getOrNull()
        }
        return Course(
            courseId = obj.optString("courseId"), courseName = obj.optString("courseName"),
            classNumber = obj.optString("classNumber"), department = obj.optString("department"),
            credits = obj.optDouble("credits", 0.0).toFloat(), weeks = weeks,
            dayOfWeek = obj.optInt("dayOfWeek"), startPeriod = obj.optInt("startPeriod"),
            endPeriod = obj.optInt("endPeriod"), teacher = obj.optString("teacher"),
            classroom = obj.optString("classroom"), courseType = obj.optString("courseType"),
            courseCategory = obj.optString("courseCategory"),
            isOnline = obj.optBoolean("isOnline", false),
            assessmentMethod = obj.optString("assessmentMethod"),
            customColor = if (obj.has("customColor") && !obj.isNull("customColor")) obj.optInt("customColor") else null,
            notes = obj.optString("notes"),
            seriesId = obj.optString("seriesId")
        )
    }

    private fun tableToJson(table: TableData) = JSONObject().apply {
        put("name", table.name)
        put("semesterStart", table.semesterStart)
        put("totalWeeks", table.totalWeeks)
        put("viewMode", table.viewMode.name)
        put("periods", JSONArray().apply {
            table.periods.forEach { period ->
                put(JSONObject().put("start", period.start).put("end", period.end))
            }
        })
        put("courses", JSONArray().apply { table.courses.forEach { put(courseToJson(it)) } })
    }

    private fun courseToJson(course: Course) = JSONObject().apply {
        put("courseId", course.courseId)
        put("courseName", course.courseName)
        put("classNumber", course.classNumber)
        put("department", course.department)
        put("credits", course.credits.toDouble())
        put("weeks", JSONArray(course.weeks))
        put("dayOfWeek", course.dayOfWeek)
        put("startPeriod", course.startPeriod)
        put("endPeriod", course.endPeriod)
        put("teacher", course.teacher)
        put("classroom", course.classroom)
        put("courseType", course.courseType)
        put("courseCategory", course.courseCategory)
        put("isOnline", course.isOnline)
        put("assessmentMethod", course.assessmentMethod)
        put("seriesId", course.seriesKey)
        course.customColor?.let { put("customColor", it) }
        if (course.notes.isNotBlank()) put("notes", course.notes)
    }

    fun normalize(tables: List<TableData>): List<TableData> =
        if (tables.isEmpty()) listOf(TableData.placeholder()) else tables.mapIndexed { index, table ->
            val periods = table.periods
                .filter { TIME_PATTERN.matches(it.start) && TIME_PATTERN.matches(it.end) }
                .take(MAX_PERIODS)
                .ifEmpty { TableData.defaultPeriods() }
            table.copy(
                name = table.name.trim().ifEmpty { "课表${index + 1}" },
                periods = periods,
                totalWeeks = table.totalWeeks.coerceIn(1, MAX_WEEKS),
                courses = CourseSeriesIds.ensure(table.courses.mapNotNull { course ->
                    val weeks = course.weeks.filter { it in 1..MAX_WEEKS }.distinct().sorted()
                    if (course.courseName.isBlank() || course.dayOfWeek !in 1..7 ||
                        course.startPeriod !in 1..MAX_PERIODS || course.endPeriod !in course.startPeriod..MAX_PERIODS
                    ) null else course.copy(weeks = weeks)
                })
            )
        }

}
