package com.jaysay.coursetable.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import java.time.LocalDate

/**
 * 可注入时钟、生命周期感知的“今天”状态。
 *
 * 组合持续存活时 LocalDate.now() 不会自行更新，跨午夜后今日高亮、周次徽标
 * 等派生展示会停留在旧日期。本状态持有者只在日期真正变化时发布新值：
 * - [refresh] 由回到前台（ON_RESUME）与系统日期/时区/时间变化广播触发；
 * - 应用保留的每分钟刷新（rememberCurrentMinute）继续作为组合内的时钟脉冲，
 *   不新增任何高频轮询；
 * - 只更新“今天”这一事实，不驱动任何页面导航、周次切换或滚动位置变化。
 *
 * 时钟以 [clock] 注入，跨日测试无需改动设备系统时间。
 */
@Stable
class TodayState internal constructor(private val clock: () -> LocalDate) : State<LocalDate> {
    private val state = mutableStateOf(clock())

    override val value: LocalDate
        get() = state.value

    /** 日期或时区可能发生变化时调用；日期未变时不产生重组。 */
    fun refresh() {
        val now = clock()
        if (now != state.value) state.value = now
    }
}

/**
 * 提供当前自然日：跨午夜、回到前台、系统日期或时区变化后自动校准。
 * 展示组件读取返回的 [State] 即可在日期变化时重组；请勿用它驱动导航。
 */
@Composable
fun rememberToday(clock: () -> LocalDate = LocalDate::now): State<LocalDate> {
    val todayState = remember(clock) { TodayState(clock) }
    // 后台跨午夜可能收不到广播，回到前台时统一校准一次。
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, clock) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) todayState.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    // 应用在前台期间跨午夜、用户改动系统时间或时区时，系统会发出以下受保护广播。
    val context = LocalContext.current
    DisposableEffect(context, clock) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context?, intent: Intent?) {
                todayState.refresh()
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_DATE_CHANGED)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose { context.unregisterReceiver(receiver) }
    }
    return todayState
}
