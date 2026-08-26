package com.jaysay.coursetable

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon

/** 不含课程正文的动态快捷方式，可由桌面长按应用图标进入。 */
object AppShortcuts {
    const val ACTION_TODAY = "com.jaysay.coursetable.action.TODAY"
    const val ACTION_ADD_COURSE = "com.jaysay.coursetable.action.ADD_COURSE"

    fun install(context: Context) {
        val manager = context.getSystemService(ShortcutManager::class.java)
        manager.dynamicShortcuts = listOf(
            shortcut(context, "today", "查看今天", ACTION_TODAY, android.R.drawable.ic_menu_today),
            shortcut(context, "add_course", "新增课程", ACTION_ADD_COURSE, android.R.drawable.ic_input_add)
        )
    }

    /** 让桌面启动器按真实使用频率排序快捷方式，不记录任何课程信息。 */
    fun reportUsed(context: Context, action: String) {
        val shortcutId = when (action) {
            ACTION_TODAY -> "today"
            ACTION_ADD_COURSE -> "add_course"
            else -> return
        }
        context.getSystemService(ShortcutManager::class.java).reportShortcutUsed(shortcutId)
    }

    private fun shortcut(context: Context, id: String, label: String, action: String, icon: Int): ShortcutInfo =
        ShortcutInfo.Builder(context, id)
            .setShortLabel(label)
            .setLongLabel(label)
            .setIcon(Icon.createWithResource(context, icon))
            .setIntent(Intent(context, MainActivity::class.java).setAction(action))
            .build()
}
