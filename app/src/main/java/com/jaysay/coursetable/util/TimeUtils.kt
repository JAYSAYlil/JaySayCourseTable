
package com.jaysay.coursetable.util

import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit

object TimeUtils {

    /** 今天的日期，格式 YYYY-MM-DD */
    fun todayDate(): String = LocalDate.now().toString()

    /** 根据开学日期计算今天是第几周，超出范围则夹紧 */
    fun todayWeek(semesterStart: String, totalWeeks: Int): Int {
        val safeTotalWeeks = totalWeeks.coerceAtLeast(1)
        val start = try {
            LocalDate.parse(semesterStart)
        } catch (_: DateTimeParseException) {
            return 1
        }
        val days = ChronoUnit.DAYS.between(start, LocalDate.now())
        return if (days >= 0) (days / 7L + 1L).toInt().coerceIn(1, safeTotalWeeks) else 1
    }

    /** Parse day string like "星期一" or "周一" to Int 1-7 */
    fun parseDayOfWeekOrNull(dayStr: String): Int? {
        val s = dayStr.trim()
        return when {
            s == "1" || s.contains("一") -> 1
            s == "2" || s.contains("二") -> 2
            s == "3" || s.contains("三") -> 3
            s == "4" || s.contains("四") -> 4
            s == "5" || s.contains("五") -> 5
            s == "6" || s.contains("六") -> 6
            s == "7" || s.contains("日") || s.contains("天") -> 7
            else -> null
        }
    }

    fun parseDayOfWeek(dayStr: String): Int = parseDayOfWeekOrNull(dayStr) ?: 1

    /** Parse period string like "第7小节" to Int */
    fun parsePeriodOrNull(periodStr: String): Int? {
        val digits = periodStr.filter { it.isDigit() }
        return digits.toIntOrNull()?.takeIf { it > 0 }
    }

    fun parsePeriod(periodStr: String): Int = parsePeriodOrNull(periodStr) ?: 1

    /** Parse week string to list of week numbers.
     *  Examples: "7-8周", "6周,9周", "2周,4-5周,7周,10周,17周", "1-3周(单),6周", "1-18周".
     *  "(单)" 表示仅单周，"(双)" 表示仅双周。
     */
    fun parseWeeks(weekStr: String): List<Int> {
        val weeks = mutableSetOf<Int>()
        val normalized = weekStr
            .replace('–', '-')
            .replace('—', '-')
            .replace('~', '-')
            .replace('～', '-')
            .replace("至", "-")
        val tokens = normalized.split(",", "，", "、", ";", "；", " ")
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        for (token in tokens) {
            val oddOnly = token.contains("单")
            val evenOnly = token.contains("双")
            val range = token.filter { it.isDigit() || it == '-' }
            if (range.isEmpty()) continue

            val dash = range.indexOf('-')
            val start = if (dash > 0) range.substring(0, dash).toIntOrNull() else range.toIntOrNull()
            val end = if (dash > 0) range.substring(dash + 1).toIntOrNull() ?: start else start
            if (start == null || end == null || end < start) continue

            for (i in start..end.coerceAtMost(99)) {
                if (oddOnly && i % 2 == 0) continue
                if (evenOnly && i % 2 == 1) continue
                weeks.add(i)
            }
        }

        return weeks.sorted()
    }

    /** Get day name in Chinese */
    fun getDayName(dayOfWeek: Int): String {
        return when (dayOfWeek) {
            1 -> "周一"; 2 -> "周二"; 3 -> "周三"; 4 -> "周四"
            5 -> "周五"; 6 -> "周六"; 7 -> "周日"
            else -> ""
        }
    }

    /** Format period range for display */
    fun formatPeriodRange(start: Int, end: Int): String {
        return if (start == end) "第${start}节" else "第${start}-${end}节"
    }

    /** Format weeks for display */
    fun formatWeeks(weeks: List<Int>): String {
        val normalized = weeks.filter { it > 0 }.distinct().sorted()
        if (normalized.isEmpty()) return ""
        val ranges = mutableListOf<String>()
        var start = normalized[0]
        var end = normalized[0]
        for (i in 1 until normalized.size) {
            if (normalized[i] == end + 1) {
                end = normalized[i]
            } else {
                ranges.add(if (start == end) "${start}周" else "${start}-${end}周")
                start = normalized[i]
                end = normalized[i]
            }
        }
        ranges.add(if (start == end) "${start}周" else "${start}-${end}周")
        return ranges.joinToString("，")
    }
}
