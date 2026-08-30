package com.jaysay.coursetable.data.preferences

import android.content.Context
import androidx.compose.runtime.Immutable
import com.jaysay.coursetable.data.storage.AtomicFileStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

@Immutable
data class PeriodTime(val start: String, val end: String)

fun defaultPeriodTimes() = listOf(
    PeriodTime("08:00", "08:45"), PeriodTime("08:55", "09:40"),
    PeriodTime("10:10", "10:55"), PeriodTime("11:05", "11:50"),
    PeriodTime("14:30", "15:15"), PeriodTime("15:25", "16:10"),
    PeriodTime("16:20", "17:05"), PeriodTime("17:15", "18:00"),
    PeriodTime("18:40", "19:25"), PeriodTime("19:35", "20:20"),
    PeriodTime("20:30", "21:15"), PeriodTime("21:15", "22:00")
)

enum class ThemeMode { LIGHT, DARK, SYSTEM }

/** 全局设置只保存真正跨课表的状态；学期与节次属于各自课表。 */
@Immutable
data class AppPreferences(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val activeTableIndex: Int = 0,
    /** 是否启用上课提醒。 */
    val reminderEnabled: Boolean = false,
    /** 上课前提前提醒分钟数（5/10/15/30）。 */
    val reminderMinutes: Int = 10,
    val reduceMotion: Boolean = false,
    val highContrast: Boolean = false,
    /** Android 12+ 跟随系统壁纸取色；低版本或关闭时使用内置青绿主题。 */
    val dynamicColor: Boolean = false,
    /** 0 表示使用默认背景；正数同时用作图片缓存刷新标记。 */
    val customBackgroundRevision: Long = 0L,
    /** 自定义背景上是否叠加全屏可读遮罩；旧版数据默认开启以保持原显示效果。 */
    val customBackgroundOverlayEnabled: Boolean = true
) {
    companion object {
        fun defaultPeriods() = defaultPeriodTimes()
    }
}

class PreferencesManager(context: Context) {
    private val store = AtomicFileStore(File(context.filesDir, "preferences.json"))

    suspend fun load(): AppPreferences = withContext(Dispatchers.IO) {
        store.read(::parse) ?: AppPreferences()
    }

    suspend fun save(prefs: AppPreferences) = withContext(Dispatchers.IO) {
        val obj = JSONObject()
            .put("schemaVersion", SCHEMA_VERSION)
            .put("themeMode", prefs.themeMode.name)
            .put("activeTableIndex", prefs.activeTableIndex.coerceAtLeast(0))
            .put("reminderEnabled", prefs.reminderEnabled)
            .put("reminderMinutes", prefs.reminderMinutes.coerceIn(1, 60))
            .put("reduceMotion", prefs.reduceMotion)
            .put("highContrast", prefs.highContrast)
            .put("dynamicColor", prefs.dynamicColor)
            .put("customBackgroundRevision", prefs.customBackgroundRevision.coerceAtLeast(0L))
            .put("customBackgroundOverlayEnabled", prefs.customBackgroundOverlayEnabled)
        store.write(obj.toString(2))
    }

    private fun parse(text: String): AppPreferences {
        val obj = JSONObject(text)
        val theme = runCatching { ThemeMode.valueOf(obj.optString("themeMode")) }
            .getOrDefault(ThemeMode.SYSTEM)
        return AppPreferences(
            themeMode = theme,
            activeTableIndex = obj.optInt("activeTableIndex", 0).coerceAtLeast(0),
            reminderEnabled = obj.optBoolean("reminderEnabled", false),
            reminderMinutes = obj.optInt("reminderMinutes", 10).coerceIn(1, 60),
            reduceMotion = obj.optBoolean("reduceMotion", false),
            highContrast = obj.optBoolean("highContrast", false),
            dynamicColor = obj.optBoolean("dynamicColor", false),
            customBackgroundRevision = obj.optLong("customBackgroundRevision", 0L).coerceAtLeast(0L),
            customBackgroundOverlayEnabled = obj.optBoolean("customBackgroundOverlayEnabled", true)
        )
    }

    private companion object {
        const val SCHEMA_VERSION = 4
    }
}
