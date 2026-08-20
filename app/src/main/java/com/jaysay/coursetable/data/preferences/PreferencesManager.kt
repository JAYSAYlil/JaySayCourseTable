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
enum class DisplayDensity { COMPACT, STANDARD, COMFORTABLE }

/** 全局设置只保存真正跨课表的状态；学期与节次属于各自课表。 */
@Immutable
data class AppPreferences(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val activeTableIndex: Int = 0,
    /** 是否启用上课提醒。 */
    val reminderEnabled: Boolean = false,
    /** 上课前提前提醒分钟数（5/10/15/30）。 */
    val reminderMinutes: Int = 10,
    val displayDensity: DisplayDensity = DisplayDensity.STANDARD,
    val reduceMotion: Boolean = false,
    val highContrast: Boolean = false,
    val widgetHideDetails: Boolean = false
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
            .put("displayDensity", prefs.displayDensity.name)
            .put("reduceMotion", prefs.reduceMotion)
            .put("highContrast", prefs.highContrast)
            .put("widgetHideDetails", prefs.widgetHideDetails)
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
            displayDensity = runCatching {
                DisplayDensity.valueOf(obj.optString("displayDensity", DisplayDensity.STANDARD.name))
            }.getOrDefault(DisplayDensity.STANDARD),
            reduceMotion = obj.optBoolean("reduceMotion", false),
            highContrast = obj.optBoolean("highContrast", false),
            widgetHideDetails = obj.optBoolean("widgetHideDetails", false)
        )
    }

    private companion object {
        const val SCHEMA_VERSION = 3
    }
}
