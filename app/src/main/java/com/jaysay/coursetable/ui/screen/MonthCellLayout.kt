package com.jaysay.coursetable.ui.screen

internal data class MonthCellLayout(val courseLines: Int, val summary: Boolean, val lunar: Boolean)

/** All dimensions are measured pixels, including the user's non-linear font scaling. */
internal fun monthCellLayout(
    available: Float, dateHeight: Float, detailHeight: Float, gap: Float,
    hasStatus: Boolean, courseCount: Int
): MonthCellLayout {
    var remaining = available - dateHeight - if (hasStatus) detailHeight + gap else 0f
    val line = detailHeight + gap
    val names = courseCount.coerceAtMost(2)
    val needed = names + if (courseCount > names) 1 else 0
    val showNames = courseCount > 0 && remaining >= needed * line
    val summary = courseCount > 0 && !showNames && remaining >= line
    remaining -= when { showNames -> needed * line; summary -> line; else -> 0f }
    return MonthCellLayout(if (showNames) names else 0, summary, remaining >= line)
}
