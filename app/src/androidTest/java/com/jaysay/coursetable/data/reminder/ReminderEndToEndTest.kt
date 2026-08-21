package com.jaysay.coursetable.data.reminder

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jaysay.coursetable.data.model.Course
import com.jaysay.coursetable.data.model.TodayAgendaCalculator
import com.jaysay.coursetable.data.preferences.AppPreferences
import com.jaysay.coursetable.data.preferences.PreferencesManager
import com.jaysay.coursetable.data.repository.CourseRepository
import com.jaysay.coursetable.data.repository.TableData
import com.jaysay.coursetable.util.TimeUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/**
 * 上课提醒端到端回归：
 * 1. 验证 USE_EXACT_ALARM 使精确闹钟在 Android 14+ 模拟器上可用（修复“到点不提醒”的根因之一）；
 * 2. 验证“闹钟触发 → 接收器校验 → 通知出现”全链路，防止通知被静默丢弃。
 */
@RunWith(AndroidJUnit4::class)
class ReminderEndToEndTest {

    private fun grantNotificationPermission() {
        InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(
            ApplicationProvider.getApplicationContext<Context>().packageName,
            Manifest.permission.POST_NOTIFICATIONS
        )
    }

    @Test
    fun useExactAlarmPermissionAllowsExactScheduling() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertTrue(
            "精确闹钟权限应可用（USE_EXACT_ALARM 自动授予）",
            ReminderPermissions.exactAlarmsAllowed(context)
        )
    }

    @Test
    fun alarmBroadcastPostsVisibleNotification() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        grantNotificationPermission()
        ReminderSuppression.clear(context)

        val today = LocalDate.now()
        val semesterStart = TimeUtils.currentWeekStartDate()
        val week = TodayAgendaCalculator.semesterWeek(semesterStart, 20, today) ?: 1
        val course = Course(
            courseId = "REMINDER-E2E",
            courseName = "提醒验证课程",
            classNumber = "01",
            department = "验证学院",
            credits = 2f,
            weeks = listOf(week),
            dayOfWeek = today.dayOfWeek.value,
            startPeriod = 1,
            endPeriod = 2,
            teacher = "验证教师",
            classroom = "验证教室",
            courseType = "必修",
            courseCategory = "专业课",
            isOnline = false,
            assessmentMethod = "考试"
        )
        val table = TableData(
            name = "提醒验证课表",
            courses = listOf(course),
            semesterStart = semesterStart,
            totalWeeks = 20
        )
        CourseRepository(context).saveAllTables(listOf(table))
        val preferences = AppPreferences(activeTableIndex = 0, reminderEnabled = true, reminderMinutes = 5)
        PreferencesManager(context).save(preferences)
        ReminderScheduler.rescheduleAll(context, listOf(table), preferences)

        // 复刻调度器构造的广播，模拟系统闹钟触发。
        val trigger = Intent(context, CourseReminderReceiver::class.java).apply {
            putExtra(ReminderScheduler.EXTRA_TABLE_INDEX, 0)
            putExtra(ReminderScheduler.EXTRA_SERIES_KEY, course.seriesKey)
            putExtra(ReminderScheduler.EXTRA_WEEK, week)
            putExtra(ReminderScheduler.EXTRA_DAY_OF_WEEK, course.dayOfWeek)
            putExtra(ReminderScheduler.EXTRA_DATE, today.toString())
            putExtra(ReminderScheduler.EXTRA_TITLE, course.courseName)
            putExtra(ReminderScheduler.EXTRA_INFO, "验证教师 · 验证教室")
            putExtra(ReminderScheduler.EXTRA_START_MINUTE, 8 * 60)
            putExtra(ReminderScheduler.EXTRA_END_MINUTE, 9 * 60 + 40)
            putExtra(ReminderScheduler.EXTRA_EVENT_KIND, ReminderEventKind.START.name)
        }
        context.sendBroadcast(trigger)

        val notificationManager = context.getSystemService(NotificationManager::class.java)
        val deadline = System.currentTimeMillis() + 8_000
        var found = false
        while (System.currentTimeMillis() < deadline && !found) {
            found = notificationManager.activeNotifications.any {
                it.notification.extras.getString(Notification.EXTRA_TITLE) == course.courseName
            }
            if (!found) delay(200)
        }
        assertTrue("闹钟触发后应出现课程提醒通知", found)
    }

    @Test
    fun testNotificationSendsAndRecordsDiagnostics() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        grantNotificationPermission()
        ReminderScheduler.ensureChannel(context)

        val failure = ReminderDiagnostics.sendTestNotification(context)

        assertTrue("测试通知应发送成功，实际失败：" + (failure ?: "无"), failure == null)
        val events = ReminderDiagnostics.recent(context)
        assertTrue("应记录测试通知事件", events.any { it.contains("测试通知已发送") })
    }

    @Test
    fun keepAliveServiceStartsWhenReminderEnabledAndStopsWhenDisabled() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        ReminderSuppression.clear(context)

        val enabled = AppPreferences(activeTableIndex = 0, reminderEnabled = true, reminderMinutes = 5)
        ReminderScheduler.rescheduleAll(context, emptyList(), enabled)
        assertTrue("提醒开启后保活服务应运行", isServiceRunning(context))

        val disabled = enabled.copy(reminderEnabled = false)
        ReminderScheduler.rescheduleAll(context, emptyList(), disabled)
        // 服务停止是异步信号，短暂等待后检查。
        delay(800)
        assertTrue("提醒关闭后保活服务应停止", !isServiceRunning(context))
    }

    @Test
    fun muteSetsSuppressionAndSettingsRestoreClearsIt() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        ReminderSuppression.clear(context)
        val week = TodayAgendaCalculator.semesterWeek(TimeUtils.currentWeekStartDate(), 20, LocalDate.now()) ?: 1

        // 模拟用户点击提醒通知上的“今天不再提醒”。
        context.sendBroadcast(
            Intent(context, CourseReminderReceiver::class.java).apply {
                action = "com.jaysay.coursetable.action.MUTE_REMINDERS_TODAY"
                putExtra(ReminderScheduler.EXTRA_TABLE_INDEX, 0)
                putExtra(ReminderScheduler.EXTRA_WEEK, week)
            }
        )
        val deadline = System.currentTimeMillis() + 8_000
        var suppressed = false
        while (System.currentTimeMillis() < deadline && !suppressed) {
            suppressed = ReminderSuppression.isSuppressed(context, 0, week, LocalDate.now())
            if (!suppressed) delay(200)
        }
        assertTrue("暂停后应处于抑制状态", suppressed)

        // 模拟设置页“恢复提醒”按钮的等价逻辑：清除暂停并重新调度。
        ReminderSuppression.clear(context)
        ReminderScheduler.rescheduleAll(
            context,
            CourseRepository(context).loadAllTables(),
            PreferencesManager(context).load()
        )
        assertTrue("设置页恢复后应解除暂停", !ReminderSuppression.isSuppressed(context, 0, week, LocalDate.now()))
    }

    private fun isServiceRunning(context: Context): Boolean {
        val manager = context.getSystemService(android.app.ActivityManager::class.java)
        return manager.getRunningServices(100)
            .any { it.service.className == ReminderKeepAliveService::class.java.name }
    }
}
