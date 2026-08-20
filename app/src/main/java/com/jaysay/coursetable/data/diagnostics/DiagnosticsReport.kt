package com.jaysay.coursetable.data.diagnostics

import com.jaysay.coursetable.data.model.Course
import com.jaysay.coursetable.data.preferences.AppPreferences
import com.jaysay.coursetable.data.repository.TableData
import org.json.JSONObject

/**
 * 由宿主提供的、已经限定为安全字段的运行环境摘要。
 * 不接受设备型号、序列号、构建指纹、文件路径或账号信息。
 */
data class DiagnosticsEnvironment(
    val appVersionName: String,
    val appVersionCode: Int,
    val androidApiLevel: Int,
    val isDebugBuild: Boolean
)

data class DiagnosticsSettings(
    val themeMode: String,
    val reminderEnabled: Boolean,
    val reminderMinutes: Int,
    val activeTableIndexValid: Boolean
)

data class DiagnosticsScheduleSummary(
    val tableCount: Int,
    val courseCount: Int,
    val emptyTableCount: Int,
    val periodCount: Int,
    val excludedWeekCount: Int,
    val tablesWithCoursesCount: Int
)

data class DiagnosticsAnomalies(
    val invalidDayOfWeekCount: Int,
    val invalidPeriodRangeCount: Int,
    val emptyWeekListCount: Int,
    val weeksOutsideTableRangeCount: Int,
    val invalidExcludedWeekCount: Int,
    val invalidPeriodTimeCount: Int,
    val duplicateCourseKeyCount: Int
) {
    val totalCount: Int
        get() = invalidDayOfWeekCount + invalidPeriodRangeCount + emptyWeekListCount +
            weeksOutsideTableRangeCount + invalidExcludedWeekCount + invalidPeriodTimeCount +
            duplicateCourseKeyCount
}

/**
 * 可安全分享的诊断报告。该类型只包含固定的统计字段，不包含任何课程或课表文本。
 */
data class DiagnosticsReport(
    val environment: DiagnosticsEnvironment,
    val schedule: DiagnosticsScheduleSummary,
    val settings: DiagnosticsSettings,
    val anomalies: DiagnosticsAnomalies
) {
    /** 稳定、适合复制给开发者的纯文本格式。 */
    fun toText(): String = buildString {
        appendLine("JaySay 课程表诊断报告")
        appendLine("应用版本：${environment.appVersionName} (${environment.appVersionCode})")
        appendLine("Android API：${environment.androidApiLevel}")
        appendLine("构建类型：${if (environment.isDebugBuild) "调试" else "正式"}")
        appendLine("课表数量：${schedule.tableCount}")
        appendLine("课程数量：${schedule.courseCount}")
        appendLine("空课表数量：${schedule.emptyTableCount}")
        appendLine("节次数量：${schedule.periodCount}")
        appendLine("停课周数量：${schedule.excludedWeekCount}")
        appendLine("含课程课表数量：${schedule.tablesWithCoursesCount}")
        appendLine("主题模式：${settings.themeMode}")
        appendLine("提醒开关：${if (settings.reminderEnabled) "开启" else "关闭"}")
        appendLine("提醒提前分钟：${settings.reminderMinutes}")
        appendLine("当前课表下标有效：${settings.activeTableIndexValid}")
        appendLine("异常总数：${anomalies.totalCount}")
        appendLine("异常-星期范围：${anomalies.invalidDayOfWeekCount}")
        appendLine("异常-节次范围：${anomalies.invalidPeriodRangeCount}")
        appendLine("异常-空周次：${anomalies.emptyWeekListCount}")
        appendLine("异常-超出总周数：${anomalies.weeksOutsideTableRangeCount}")
        appendLine("异常-停课周范围：${anomalies.invalidExcludedWeekCount}")
        appendLine("异常-时间格式：${anomalies.invalidPeriodTimeCount}")
        appendLine("异常-重复课程键：${anomalies.duplicateCourseKeyCount}")
    }

    /** JSON 字段同样是固定白名单，便于未来 UI 或分享功能调用。 */
    fun toJson(): String = JSONObject()
        .put("environment", JSONObject()
            .put("appVersionName", environment.appVersionName)
            .put("appVersionCode", environment.appVersionCode)
            .put("androidApiLevel", environment.androidApiLevel)
            .put("isDebugBuild", environment.isDebugBuild))
        .put("schedule", JSONObject()
            .put("tableCount", schedule.tableCount)
            .put("courseCount", schedule.courseCount)
            .put("emptyTableCount", schedule.emptyTableCount)
            .put("periodCount", schedule.periodCount)
            .put("excludedWeekCount", schedule.excludedWeekCount)
            .put("tablesWithCoursesCount", schedule.tablesWithCoursesCount))
        .put("settings", JSONObject()
            .put("themeMode", settings.themeMode)
            .put("reminderEnabled", settings.reminderEnabled)
            .put("reminderMinutes", settings.reminderMinutes)
            .put("activeTableIndexValid", settings.activeTableIndexValid))
        .put("anomalies", JSONObject()
            .put("totalCount", anomalies.totalCount)
            .put("invalidDayOfWeekCount", anomalies.invalidDayOfWeekCount)
            .put("invalidPeriodRangeCount", anomalies.invalidPeriodRangeCount)
            .put("emptyWeekListCount", anomalies.emptyWeekListCount)
            .put("weeksOutsideTableRangeCount", anomalies.weeksOutsideTableRangeCount)
            .put("invalidExcludedWeekCount", anomalies.invalidExcludedWeekCount)
            .put("invalidPeriodTimeCount", anomalies.invalidPeriodTimeCount)
            .put("duplicateCourseKeyCount", anomalies.duplicateCourseKeyCount))
        .toString()
}

object DiagnosticsReportGenerator {
    fun generate(
        environment: DiagnosticsEnvironment,
        tables: List<TableData>,
        preferences: AppPreferences
    ): DiagnosticsReport {
        val courseStats = tables.flatMap { table -> table.courses.map { table to it } }
        val duplicateKeys = tables.sumOf { table ->
            // 仅用于统计重复数量，绝不写入报告。
            table.courses.groupingBy { it.uniqueKey }.eachCount().count { it.value > 1 }
        }

        val anomalies = courseStats.fold(AnomalyAccumulator()) { acc, (table, course) ->
            acc.recordCourse(table, course)
        }
            .recordTables(tables)
            .toAnomalies(duplicateKeys)

        return DiagnosticsReport(
            environment = environment.copy(
                appVersionName = environment.appVersionName.safeVersion(),
                appVersionCode = environment.appVersionCode.coerceAtLeast(0),
                androidApiLevel = environment.androidApiLevel.coerceAtLeast(0)
            ),
            schedule = DiagnosticsScheduleSummary(
                tableCount = tables.size,
                courseCount = courseStats.size,
                emptyTableCount = tables.count { it.courses.isEmpty() },
                periodCount = tables.sumOf { it.periods.size },
                excludedWeekCount = tables.sumOf { it.excludedWeeks.size },
                tablesWithCoursesCount = tables.count { it.courses.isNotEmpty() }
            ),
            settings = DiagnosticsSettings(
                themeMode = preferences.themeMode.name,
                reminderEnabled = preferences.reminderEnabled,
                reminderMinutes = preferences.reminderMinutes.coerceIn(1, 60),
                activeTableIndexValid = preferences.activeTableIndex in tables.indices
            ),
            anomalies = anomalies
        )
    }

    private fun String.safeVersion(): String =
        replace(Regex("[^A-Za-z0-9._+\\-]"), "_").take(64).ifBlank { "unknown" }

    private class AnomalyAccumulator(
        var invalidDay: Int = 0,
        var invalidPeriod: Int = 0,
        var emptyWeeks: Int = 0,
        var outsideWeeks: Int = 0,
        var invalidExcludedWeeks: Int = 0,
        var invalidPeriodTimes: Int = 0
    ) {
        fun recordCourse(table: TableData, course: Course): AnomalyAccumulator {
            if (course.dayOfWeek !in 1..7) invalidDay++
            if (course.startPeriod !in 1..table.periods.size ||
                course.endPeriod !in course.startPeriod..table.periods.size
            ) invalidPeriod++
            if (course.weeks.isEmpty()) emptyWeeks++
            outsideWeeks += course.weeks.count { it !in 1..table.totalWeeks.coerceAtLeast(1) }
            return this
        }

        fun recordTables(tables: List<TableData>): AnomalyAccumulator {
            tables.forEach { table ->
                invalidExcludedWeeks += table.excludedWeeks.count { it !in 1..table.totalWeeks.coerceAtLeast(1) }
                invalidPeriodTimes += table.periods.count {
                    !TIME_PATTERN.matches(it.start) || !TIME_PATTERN.matches(it.end)
                }
            }
            return this
        }

        fun toAnomalies(duplicateKeys: Int) = DiagnosticsAnomalies(
            invalidDay, invalidPeriod, emptyWeeks, outsideWeeks,
            invalidExcludedWeeks, invalidPeriodTimes, duplicateKeys
        )
    }

    private val TIME_PATTERN = Regex("^(?:[01]\\d|2[0-3]):[0-5]\\d$")
}
