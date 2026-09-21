package com.jaysay.coursetable.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent

/**
 * 全部小组件变体的统一入口：新增材质变体只需在 [providerClasses] 登记一次，
 * 主应用的数据变化刷新与「桌面是否有小组件」判断自动覆盖所有变体。
 */
object CourseWidgetProviders {
    /** 已注册的 Provider，须与 AndroidManifest 中的 receiver 保持一致。 */
    val providerClasses: List<Class<*>> = listOf(
        CourseWidgetProvider::class.java,
        CourseWidgetFrostedProvider::class.java
    )

    /** 数据变化后刷新所有变体的小组件（实心 + 毛玻璃）。 */
    fun requestUpdate(context: Context) {
        providerClasses.forEach { provider ->
            context.sendBroadcast(
                Intent(context, provider).setAction(CourseWidgetProvider.ACTION_UPDATE)
            )
        }
    }

    /** 任意变体的小组件存在于桌面时为 true，供主应用判断是否提示小组件能力。 */
    fun anyWidgetPresent(context: Context): Boolean {
        val manager = AppWidgetManager.getInstance(context)
        return providerClasses.any { provider ->
            manager.getAppWidgetIds(ComponentName(context, provider)).isNotEmpty()
        }
    }
}
