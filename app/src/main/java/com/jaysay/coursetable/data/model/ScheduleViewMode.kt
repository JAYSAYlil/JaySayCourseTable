package com.jaysay.coursetable.data.model

/** 每张课表独立保存的显示范围。 */
enum class ScheduleViewMode(val label: String) {
    WEEK("七天"),
    WORK_WEEK("五天"),
    DAY("单日")
}
