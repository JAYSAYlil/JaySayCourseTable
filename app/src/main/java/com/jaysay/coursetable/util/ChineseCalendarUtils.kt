package com.jaysay.coursetable.util

import android.icu.util.ChineseCalendar
import java.time.LocalDate
import java.time.ZoneId

/** 月视图使用的轻量农历/节日格式化，不引入第三方历法库。 */
data class ChineseCalendarLabel(val lunar: String, val holiday: String? = null)

object ChineseCalendarUtils {
    private val lunarMonthNames = arrayOf("正月", "二月", "三月", "四月", "五月", "六月", "七月", "八月", "九月", "十月", "冬月", "腊月")
    private val lunarDayNames = arrayOf("初一", "初二", "初三", "初四", "初五", "初六", "初七", "初八", "初九", "初十", "十一", "十二", "十三", "十四", "十五", "十六", "十七", "十八", "十九", "二十", "廿一", "廿二", "廿三", "廿四", "廿五", "廿六", "廿七", "廿八", "廿九", "三十")

    fun label(date: LocalDate): ChineseCalendarLabel {
        val millis = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val calendar = ChineseCalendar().apply { timeInMillis = millis }
        val month = (calendar.get(ChineseCalendar.MONTH) + 1).coerceIn(1, 12)
        val day = calendar.get(ChineseCalendar.DAY_OF_MONTH).coerceIn(1, lunarDayNames.size)
        // 常见日历写法：每月初一显示月份，其余日期只显示日名，窄格里也能完整读出。
        val lunar = if (day == 1) lunarMonthNames[month - 1] else lunarDayNames[day - 1]
        return ChineseCalendarLabel(lunar, holidayOf(date, month, day))
    }

    private fun holidayOf(date: LocalDate, lunarMonth: Int, lunarDay: Int): String? {
        val solar = "${date.monthValue.toString().padStart(2, '0')}-${date.dayOfMonth.toString().padStart(2, '0')}"
        return when (solar) {
            "01-01" -> "元旦"
            "04-04", "04-05" -> "清明"
            "05-01" -> "劳动节"
            "10-01" -> "国庆节"
            else -> when (lunarMonth to lunarDay) {
                1 to 1 -> "春节"
                1 to 15 -> "元宵"
                5 to 5 -> "端午"
                7 to 7 -> "七夕"
                8 to 15 -> "中秋"
                9 to 9 -> "重阳"
                12 to 8 -> "腊八"
                12 to 23 -> "小年"
                else -> null
            }
        }
    }
}
