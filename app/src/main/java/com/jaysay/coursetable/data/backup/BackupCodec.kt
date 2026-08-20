package com.jaysay.coursetable.data.backup

import com.jaysay.coursetable.data.preferences.AppPreferences
import com.jaysay.coursetable.data.preferences.ThemeMode
import com.jaysay.coursetable.data.preferences.DisplayDensity
import com.jaysay.coursetable.data.repository.TableData
import com.jaysay.coursetable.data.repository.TableDataJson
import org.json.JSONObject
import java.time.Instant

data class BackupData(
    val tables: List<TableData>,
    val preferences: AppPreferences
)

/** 可移植备份格式。不写入设备名、文件路径或应用内部标识。 */
object BackupCodec {
    private const val FORMAT = "jaysay-course-table-backup"
    private const val SCHEMA_VERSION = 1
    private const val MAX_TABLES = 50

    fun encode(data: BackupData, sanitized: Boolean = false): String {
        val tables = if (sanitized) data.tables.mapIndexed { tableIndex, table ->
            table.copy(courses = table.courses.mapIndexed { courseIndex, course ->
                course.copy(
                    courseId = "",
                    classNumber = "",
                    department = "",
                    teacher = "",
                    classroom = "",
                    notes = "",
                    // 不沿用由真实课程信息派生的系列 ID，避免脱敏副本保留关联指纹。
                    seriesId = "sanitized-$tableIndex-$courseIndex"
                )
            })
        } else data.tables

        return JSONObject()
            .put("format", FORMAT)
            .put("schemaVersion", SCHEMA_VERSION)
            .put("createdAt", Instant.now().toString())
            .put("sanitized", sanitized)
            .put("preferences", JSONObject()
                .put("themeMode", data.preferences.themeMode.name)
                .put("activeTableIndex", data.preferences.activeTableIndex)
                .put("reminderEnabled", data.preferences.reminderEnabled)
                .put("reminderMinutes", data.preferences.reminderMinutes)
                .put("displayDensity", data.preferences.displayDensity.name)
                .put("reduceMotion", data.preferences.reduceMotion)
                .put("highContrast", data.preferences.highContrast)
                .put("widgetHideDetails", data.preferences.widgetHideDetails))
            .put("tables", TableDataJson.toJson(tables))
            .toString(2)
    }

    fun decode(text: String): BackupData {
        require(text.toByteArray(Charsets.UTF_8).size <= 10 * 1024 * 1024) { "备份文件过大" }
        val root = JSONObject(text)
        require(root.optString("format") == FORMAT) { "不是 JaySay 课表备份" }
        require(root.optInt("schemaVersion", -1) == SCHEMA_VERSION) { "暂不支持此备份版本" }
        require(!root.optBoolean("sanitized", false)) { "脱敏副本仅用于分享，不能恢复" }

        val tablesArray = root.optJSONArray("tables") ?: error("备份缺少课表数据")
        require(tablesArray.length() in 1..MAX_TABLES) { "备份中的课表数量无效" }
        val tables = TableDataJson.fromJson(tablesArray, requireEveryRowValid = true)
        require(tables.sumOf { it.courses.size } <= 20_000) { "备份中的课程数量过多" }

        val prefsJson = root.optJSONObject("preferences") ?: JSONObject()
        val theme = runCatching { ThemeMode.valueOf(prefsJson.optString("themeMode")) }
            .getOrDefault(ThemeMode.SYSTEM)
        val activeIndex = prefsJson.optInt("activeTableIndex", 0).coerceIn(tables.indices)
        return BackupData(
            tables,
            AppPreferences(
                themeMode = theme,
                activeTableIndex = activeIndex,
                reminderEnabled = prefsJson.optBoolean("reminderEnabled", false),
                reminderMinutes = prefsJson.optInt("reminderMinutes", 10).coerceIn(1, 60),
                displayDensity = runCatching {
                    DisplayDensity.valueOf(prefsJson.optString("displayDensity", DisplayDensity.STANDARD.name))
                }.getOrDefault(DisplayDensity.STANDARD),
                reduceMotion = prefsJson.optBoolean("reduceMotion", false),
                highContrast = prefsJson.optBoolean("highContrast", false),
                widgetHideDetails = prefsJson.optBoolean("widgetHideDetails", false)
            )
        )
    }
}
