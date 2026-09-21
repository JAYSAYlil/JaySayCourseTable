package com.jaysay.coursetable.widget

/**
 * 毛玻璃桌面小组件：与 [CourseWidgetProvider] 功能完全一致
 * （今日+明日课程、教室/教师、点击打开应用、数据变化刷新、3/4/5 列宽自适应），
 * 只把实心表面换成半透明毛玻璃布局，逻辑零重复。
 */
class CourseWidgetFrostedProvider : CourseWidgetProvider() {
    internal override val variant: WidgetVariant = WidgetVariant.FROSTED
}
