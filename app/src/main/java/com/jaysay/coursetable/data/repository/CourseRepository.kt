package com.jaysay.coursetable.data.repository

import android.content.Context
import androidx.compose.runtime.Immutable
import com.jaysay.coursetable.data.history.CourseHistoryStore
import com.jaysay.coursetable.data.history.CourseSnapshotDiff
import com.jaysay.coursetable.data.history.CourseSnapshotDiffer
import com.jaysay.coursetable.data.history.CourseSnapshotSummary
import com.jaysay.coursetable.data.model.Course
import com.jaysay.coursetable.data.model.CourseReminderMode
import com.jaysay.coursetable.data.model.CourseSeriesIds
import com.jaysay.coursetable.data.model.ScheduleViewMode
import com.jaysay.coursetable.data.model.ScheduleDateException
import com.jaysay.coursetable.data.model.ScheduleDateResolver
import com.jaysay.coursetable.data.model.ScheduleExceptionType
import com.jaysay.coursetable.data.preferences.PeriodTime
import com.jaysay.coursetable.data.preferences.defaultPeriodTimes
import com.jaysay.coursetable.data.storage.AtomicFileStore
import com.jaysay.coursetable.data.storage.DataCorruptionException
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
    val viewMode: ScheduleViewMode = ScheduleViewMode.WEEK,
    /** 校历停课周（节假日/考试周），这些周不显示课程、不触发提醒。 */
    val excludedWeeks: List<Int> = emptyList(),
    /** 精确到自然日的停课、单课取消和补课。 */
    val dateExceptions: List<ScheduleDateException> = emptyList(),
    /** 可选的周标签，例如“考试周”。 */
    val weekLabels: Map<Int, String> = emptyMap(),
    /** 归档课表仍保留全部数据，但不参与日常提醒与小组件。 */
    val archived: Boolean = false,
    val archivedAt: String? = null
) {
    companion object {
        fun defaultPeriods() = defaultPeriodTimes()

        /** 活动课表下标越界时的占位课表。 */
        fun placeholder(name: String = "课表1") =
            TableData(name, emptyList(), semesterStart = TimeUtils.todayDate())
    }
}

class CourseRepository private constructor(
    private val store: AtomicFileStore,
    private val historyStore: CourseHistoryStore
) {
    private val storageLock = Any()

    constructor(context: Context) : this(context.filesDir)

    internal constructor(filesDir: File) : this(
        AtomicFileStore(File(filesDir, "tables.json")),
        CourseHistoryStore(File(filesDir, "course_history"))
    )

    suspend fun loadAllTables(): List<TableData> = withContext(Dispatchers.IO) {
        synchronized(storageLock) {
            store.read(::parseTables)?.let(TableDataJson::normalize) ?: listOf(TableData.placeholder())
        }
    }

    suspend fun saveAllTables(tables: List<TableData>) = withContext(Dispatchers.IO) {
        synchronized(storageLock) {
            persistWithHistory(encodeTables(tables), replaceWithValidated = false)
        }
    }

    /** 仅供已经由 [com.jaysay.coursetable.data.backup.BackupCodec] 严格校验的完整备份恢复。 */
    suspend fun restoreValidatedTables(tables: List<TableData>) = withContext(Dispatchers.IO) {
        synchronized(storageLock) {
            persistWithHistory(
                content = encodeTables(tables),
                replaceWithValidated = true,
                allowCorruptCurrent = true
            )
        }
    }

    suspend fun listSnapshots(): List<CourseSnapshotSummary> = withContext(Dispatchers.IO) {
        synchronized(storageLock) { historyStore.listSnapshots(::parseTables) }
    }

    suspend fun previewSnapshot(id: String): CourseSnapshotDiff = withContext(Dispatchers.IO) {
        synchronized(storageLock) {
            val current = store.read(::parseTables)?.let(TableDataJson::normalize)
                ?: listOf(TableData.placeholder())
            val target = historyStore.loadSnapshot(id, ::parseTables).tables
            CourseSnapshotDiffer.compare(current, target)
        }
    }

    /** 恢复前也保存当前有效状态；损坏主数据不会被写入历史。 */
    suspend fun restoreSnapshot(id: String): List<TableData> = withContext(Dispatchers.IO) {
        synchronized(storageLock) {
            val target = historyStore.loadSnapshot(id, ::parseTables).tables
            persistWithHistory(
                content = encodeTables(target),
                replaceWithValidated = true,
                allowCorruptCurrent = true
            )
            target
        }
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

    private fun persistWithHistory(
        content: String,
        replaceWithValidated: Boolean,
        allowCorruptCurrent: Boolean = false
    ) {
        val currentTables = try {
            store.read(::parseTables)?.let(TableDataJson::normalize)
        } catch (error: DataCorruptionException) {
            if (!allowCorruptCurrent) throw error
            null
        }
        val currentContent = currentTables?.let(::encodeTables)
        if (currentTables != null && currentContent != null && currentContent != content) {
            historyStore.createSnapshot(currentContent, currentTables)
        }
        if (replaceWithValidated) store.replaceWithValidated(content) else store.write(content)
    }

    private companion object {
        const val SCHEMA_VERSION = 4
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
        val excludedWeeks = obj.optJSONArray("excludedWeeks")?.let { array ->
            if (strict) {
                (0 until array.length()).map(array::getInt)
            } else {
                (0 until array.length()).mapNotNull { index ->
                    runCatching { array.getInt(index) }.getOrNull()
                }
            }
        } ?: emptyList()
        val dateExceptions = obj.optJSONArray("dateExceptions")?.let { array ->
            (0 until array.length()).mapNotNull { index ->
                runCatching { parseDateException(array.getJSONObject(index)) }.getOrNull()
            }.also { require(!strict || it.size == array.length()) { "备份中存在损坏的日期例外" } }
        } ?: emptyList()
        val weekLabels = obj.optJSONObject("weekLabels")?.let { labels ->
            labels.keys().asSequence().mapNotNull { key ->
                key.toIntOrNull()?.let { week -> week to labels.optString(key) }
            }.toMap()
        } ?: emptyMap()
        return TableData(
            name = obj.optString("name", "课表"),
            courses = courses,
            periods = periods,
            semesterStart = obj.optString("semesterStart", TimeUtils.todayDate()),
            totalWeeks = obj.optInt("totalWeeks", 20),
            viewMode = viewMode,
            excludedWeeks = excludedWeeks,
            dateExceptions = dateExceptions,
            weekLabels = weekLabels,
            archived = obj.optBoolean("archived", false),
            archivedAt = obj.optString("archivedAt").takeIf(String::isNotBlank)
        )
    }

    private fun parseDateException(obj: JSONObject): ScheduleDateException {
        val type = ScheduleExceptionType.valueOf(obj.getString("type"))
        return ScheduleDateException(
            id = obj.getString("id"),
            date = obj.getString("date"),
            type = type,
            courseSeriesKey = obj.optString("courseSeriesKey").takeIf(String::isNotBlank),
            makeupCourse = obj.optJSONObject("makeupCourse")?.let(::parseCourse),
            title = obj.optString("title")
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
            seriesId = obj.optString("seriesId"),
            reminderMode = runCatching {
                CourseReminderMode.valueOf(obj.optString("reminderMode", CourseReminderMode.INHERIT.name))
            }.getOrDefault(CourseReminderMode.INHERIT),
            reminderMinutesOverride = if (obj.has("reminderMinutesOverride") && !obj.isNull("reminderMinutesOverride")) {
                obj.optInt("reminderMinutesOverride").takeIf { it in 1..60 }
            } else null,
            endReminderEnabled = obj.optBoolean("endReminderEnabled", false)
        )
    }

    private fun tableToJson(table: TableData) = JSONObject().apply {
        put("name", table.name)
        put("semesterStart", table.semesterStart)
        put("totalWeeks", table.totalWeeks)
        put("viewMode", table.viewMode.name)
        if (table.excludedWeeks.isNotEmpty()) {
            put("excludedWeeks", JSONArray(table.excludedWeeks))
        }
        if (table.dateExceptions.isNotEmpty()) {
            put("dateExceptions", JSONArray().apply {
                table.dateExceptions.forEach { put(dateExceptionToJson(it)) }
            })
        }
        if (table.weekLabels.isNotEmpty()) {
            put("weekLabels", JSONObject().apply {
                table.weekLabels.forEach { (week, label) -> put(week.toString(), label) }
            })
        }
        if (table.archived) put("archived", true)
        table.archivedAt?.let { put("archivedAt", it) }
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
        if (course.reminderMode != CourseReminderMode.INHERIT) put("reminderMode", course.reminderMode.name)
        course.reminderMinutesOverride?.let { put("reminderMinutesOverride", it.coerceIn(1, 60)) }
        if (course.endReminderEnabled) put("endReminderEnabled", true)
        course.customColor?.let { put("customColor", it) }
        if (course.notes.isNotBlank()) put("notes", course.notes)
    }

    private fun dateExceptionToJson(item: ScheduleDateException) = JSONObject().apply {
        put("id", item.id)
        put("date", item.date)
        put("type", item.type.name)
        item.courseSeriesKey?.let { put("courseSeriesKey", it) }
        item.makeupCourse?.let { put("makeupCourse", courseToJson(it)) }
        if (item.title.isNotBlank()) put("title", item.title)
    }

    fun normalize(tables: List<TableData>): List<TableData> {
        if (tables.isEmpty()) return listOf(TableData.placeholder())
        val normalized = tables.mapIndexed { index, table ->
            val periods = table.periods
                .filter { TIME_PATTERN.matches(it.start) && TIME_PATTERN.matches(it.end) }
                .take(MAX_PERIODS)
                .ifEmpty { TableData.defaultPeriods() }
            table.copy(
                name = table.name.trim().ifEmpty { "课表${index + 1}" },
                periods = periods,
                totalWeeks = table.totalWeeks.coerceIn(1, MAX_WEEKS),
                excludedWeeks = table.excludedWeeks.filter { it in 1..MAX_WEEKS }.distinct().sorted(),
                dateExceptions = ScheduleDateResolver.normalize(table.dateExceptions),
                weekLabels = table.weekLabels.mapNotNull { (week, label) ->
                    label.trim().take(30).takeIf { week in 1..MAX_WEEKS && it.isNotBlank() }?.let { week to it }
                }.toMap(),
                archivedAt = table.archivedAt?.takeIf { value ->
                    runCatching { java.time.Instant.parse(value) }.isSuccess
                },
                courses = CourseSeriesIds.ensure(table.courses.mapNotNull { course ->
                    val weeks = course.weeks.filter { it in 1..MAX_WEEKS }.distinct().sorted()
                    if (course.courseName.isBlank() || course.dayOfWeek !in 1..7 ||
                        course.startPeriod !in 1..MAX_PERIODS || course.endPeriod !in course.startPeriod..MAX_PERIODS
                    ) null else course.copy(
                        weeks = weeks,
                        reminderMinutesOverride = course.reminderMinutesOverride?.coerceIn(1, 60)
                    )
                })
            )
        }
        return if (normalized.all(TableData::archived)) {
            normalized.toMutableList().also { it[0] = it[0].copy(archived = false, archivedAt = null) }
        } else normalized
    }

}
