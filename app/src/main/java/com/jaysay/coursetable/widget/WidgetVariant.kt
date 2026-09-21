package com.jaysay.coursetable.widget

import androidx.annotation.LayoutRes
import com.jaysay.coursetable.R

/**
 * 小组件材质变体：只决定用哪套布局与背景资源。
 * 数据、渲染、尺寸自适应与刷新逻辑由 [CourseWidgetProvider] 全量共用。
 *
 * [tag] 通过 Intent extra（[CourseWidgetProvider.EXTRA_VARIANT]）传给集合服务，
 * 服务据此选择条目布局，不依赖应用的任何全局状态。
 */
internal enum class WidgetVariant(
    val tag: String,
    @LayoutRes val layoutRes: Int,
    @LayoutRes val itemLayoutRes: Int
) {
    /** 现有实心材质（纯白/浅灰表面）。 */
    SOLID("solid", R.layout.widget_course, R.layout.widget_course_item),

    /** 毛玻璃材质（半透明分层表面），功能与 [SOLID] 完全一致。 */
    FROSTED("frosted", R.layout.widget_course_frosted, R.layout.widget_course_item_frosted);

    companion object {
        /** 标记缺失或未知时一律回退实心材质，保证服务被单独拉起时行为不变。 */
        fun fromTag(tag: String?): WidgetVariant = entries.firstOrNull { it.tag == tag } ?: SOLID
    }
}
