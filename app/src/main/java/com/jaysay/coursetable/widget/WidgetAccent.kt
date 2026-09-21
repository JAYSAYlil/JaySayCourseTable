package com.jaysay.coursetable.widget

import android.content.Context
import android.content.res.Configuration
import android.widget.RemoteViews
import androidx.compose.ui.graphics.toArgb
import com.jaysay.coursetable.R
import com.jaysay.coursetable.data.preferences.PreferencesManager
import com.jaysay.coursetable.data.preferences.ThemeAccent
import com.jaysay.coursetable.ui.theme.accentPalette

/**
 * 小组件跟随应用主题色。
 *
 * RemoteViews 读不到 Compose 主题，所以由 Provider 从偏好里取强调色、换算成具体色值再下发：
 * 浅色端用深色调、深色端用浅色调，保证在纯色表面与半透明毛玻璃上都可读。
 * 服务被系统单独拉起、没拿到下发色值时，自己走同一条换算补齐，
 * 不停在布局资源里的默认青绿上（青绿只是未换过主题色时的默认值）。
 */
internal object WidgetAccent {
    fun isNight(context: Context): Boolean =
        (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

    fun textColor(accent: ThemeAccent, night: Boolean): Int {
        val palette = accentPalette(accent)
        return (if (night) palette.darkOnContainer else palette.lightOnContainer).toArgb()
    }

    /** 按当前偏好与外观换算强调色；偏好不可读时按默认青绿，与新建应用的观感一致。 */
    suspend fun textColorOf(context: Context): Int =
        runCatching { PreferencesManager(context).load().themeAccent }
            .getOrDefault(ThemeAccent.TEAL)
            .let { textColor(it, isNight(context)) }

    /** 星期徽标、今日/明日小节标题。 */
    fun apply(views: RemoteViews, color: Int) {
        views.setTextColor(R.id.widget_weekday, color)
        views.setTextColor(R.id.widget_today_title, color)
        views.setTextColor(R.id.widget_tomorrow_title, color)
    }
}
