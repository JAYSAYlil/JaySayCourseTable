package com.jaysay.coursetable

/** 应用内部页面与统一返回目的地；UI 按钮和系统返回手势共用这套规则。 */
internal enum class Screen {
    MAIN,
    SETTINGS,
    IMPORT_CONFIRM,
    TABLE_MANAGE,
    AGENDA,
    HISTORY,
    CALENDAR,
    COURSE_DETAIL
}

internal fun Screen.backDestination(
    detailOrigin: Screen = Screen.MAIN,
    calendarOrigin: Screen = Screen.SETTINGS
): Screen? = when (this) {
    Screen.MAIN -> null
    Screen.HISTORY -> Screen.SETTINGS
    Screen.CALENDAR -> calendarOrigin.takeIf { it == Screen.MAIN || it == Screen.SETTINGS } ?: Screen.SETTINGS
    Screen.COURSE_DETAIL -> detailOrigin.takeIf { it == Screen.MAIN || it == Screen.AGENDA } ?: Screen.MAIN
    Screen.SETTINGS, Screen.IMPORT_CONFIRM, Screen.TABLE_MANAGE, Screen.AGENDA -> Screen.MAIN
}
