package com.jaysay.coursetable.ui.screen

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerSnapDistance
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import com.jaysay.coursetable.data.model.Course
import com.jaysay.coursetable.data.model.ScheduleDateException
import com.jaysay.coursetable.data.model.ScheduleDateResolver
import com.jaysay.coursetable.data.model.ScheduleViewMode
import com.jaysay.coursetable.data.preferences.PeriodTime
import com.jaysay.coursetable.ui.theme.Motion
import com.jaysay.coursetable.util.TimeUtils
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * 日视图的唯一导航模型。
 *
 * Pager 页码是当前日期的唯一事实来源；顶部星期、日期表头、课程内容都直接从同一个
 * [PagerState] 派生。定位今天、星期条和月视图跳转只提交一次导航命令，不再维护第二份
 * “预览日期/落定日期”状态，因此不存在双向回写和竞态覆盖。
 */
@Stable
internal class DayViewController internal constructor(
    val pagerState: PagerState,
    val semesterStartDate: LocalDate,
    val totalDays: Int
) {
    internal var requestedPage by mutableIntStateOf(pagerState.currentPage)
        private set
    internal var requestSerial by mutableIntStateOf(0)
        private set

    val displayPage: Int
        // currentPage 在跨过相邻页的临界点时立即更新；targetPage 只在 Pager
        // 判定完落点后才可靠，使用它会让顶部日期在快速滑动时明显滞后。
        get() = pagerState.currentPage.coerceIn(0, totalDays - 1)

    val displayDate: LocalDate
        get() = dateForPage(displayPage)

    val settledDate: LocalDate
        get() = dateForPage(pagerState.settledPage.coerceIn(0, totalDays - 1))

    fun dateForPage(page: Int): LocalDate =
        semesterStartDate.plusDays(page.coerceIn(0, totalDays - 1).toLong())

    fun pageForDate(date: LocalDate): Int =
        ChronoUnit.DAYS.between(semesterStartDate, date).toInt().coerceIn(0, totalDays - 1)

    fun clampDate(date: LocalDate): LocalDate = dateForPage(pageForDate(date))

    /** 新请求会直接打断旧动画，从当前屏幕位置继续向新目标收束。 */
    fun navigateTo(date: LocalDate) {
        requestedPage = pageForDate(date)
        requestSerial++
    }
}

@Composable
internal fun rememberDayViewController(
    semesterStart: String,
    totalWeeks: Int,
    currentWeek: Int,
    focusedDay: Int
): DayViewController? {
    val startDate = remember(semesterStart) { TimeUtils.semesterWeekStartOrNull(semesterStart) }
        ?: return null
    val totalDays = totalWeeks.coerceAtLeast(1) * 7
    val initialPage = (((currentWeek.coerceAtLeast(1) - 1) * 7) +
        (focusedDay.coerceIn(1, 7) - 1)).coerceIn(0, totalDays - 1)
    val pagerState = key(startDate, totalDays) {
        rememberPagerState(initialPage = initialPage) { totalDays }
    }
    return remember(pagerState, startDate, totalDays) {
        DayViewController(pagerState, startDate, totalDays)
    }
}

/**
 * 导航与落定副作用集中在一处：命令负责滚动，settledPage 只负责通知外层周次/星期。
 * 回写以效果启动时的落定页为基线，只有真实翻页才通知外层：主屏幕重回组合
 * （详情返回、切换视图）会让控制器重建，初始快照绝不能把共享的周次/星期状态
 * 拉回日视图上次的旧值，否则周/月视图会被外部同步效果拽到错误页面（乱跳根源）。
 */
@Composable
internal fun DayViewControllerEffects(
    controller: DayViewController,
    semesterStart: String,
    totalWeeks: Int,
    onWeekChange: (Int) -> Unit,
    onFocusedDayChange: (Int) -> Unit
) {
    val onWeekChangeState by rememberUpdatedState(onWeekChange)
    val onFocusedDayChangeState by rememberUpdatedState(onFocusedDayChange)
    val hapticView = LocalView.current

    LaunchedEffect(controller, controller.requestSerial) {
        val target = controller.requestedPage
        if (controller.pagerState.currentPage != target || controller.pagerState.isScrollInProgress) {
            // animateScrollToPage 使用 MutatorMutex：新的定位请求会中断当前拖拽/动画，
            // 从屏幕正在显示的位置继续，定位不会再被旧页面落定结果覆盖。
            controller.pagerState.animateScrollToPage(
                page = target,
                animationSpec = Motion.interactive()
            )
        }
    }

    LaunchedEffect(controller) {
        val pagerState = controller.pagerState
        var lastReportedPage = pagerState.settledPage
        snapshotFlow { pagerState.settledPage }
            .collect { page ->
                if (page == lastReportedPage) return@collect
                lastReportedPage = page
                val date = controller.dateForPage(page)
                TimeUtils.semesterWeekOrNull(semesterStart, totalWeeks, date)?.let(onWeekChangeState)
                onFocusedDayChangeState(date.dayOfWeek.value)
                hapticView.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            }
    }
}

@Composable
internal fun DaySchedulePager(
    modifier: Modifier,
    controller: DayViewController,
    displayedCourses: List<Course>,
    colorMap: Map<String, Color>,
    timeWidth: Dp,
    cellHeight: Dp,
    totalWeeks: Int,
    onCourseClick: (Course) -> Unit,
    onEmptyCellClick: (Int, Int) -> Unit,
    periodTimes: List<PeriodTime>,
    dark: Boolean,
    hasCustomBackground: Boolean,
    semesterStart: String,
    excludedWeekSet: Set<Int>,
    dateExceptions: List<ScheduleDateException>
) {
    val today = LocalDate.now()
    val todayWeek = TimeUtils.semesterWeekOrNull(semesterStart, totalWeeks, today) ?: -1
    // 翻页共享同一纵向滚动状态：翻到相邻一天时停在上一天的阅读位置，与周视图一致；
    // 离开主界面（课程详情/设置）再返回同样恢复原位置。
    val scrollState = rememberSaveable(saver = ScrollState.Saver) { ScrollState(0) }
    val dayFling = PagerDefaults.flingBehavior(
        state = controller.pagerState,
        pagerSnapDistance = PagerSnapDistance.atMost(1),
        // 甩动是动量手势：沿用项目的轻微惯性弹簧；定位按钮仍使用无过冲交互弹簧。
        snapAnimationSpec = Motion.momentum(),
        decayAnimationSpec = exponentialDecay(frictionMultiplier = 12f)
    )

    HorizontalPager(
        state = controller.pagerState,
        flingBehavior = dayFling,
        modifier = modifier
            .testTag("day-swipe-area")
            .semantics { stateDescription = controller.settledDate.toString() },
        // 日页包含完整的节次网格；预加载相邻页会同时创建两套重型网格，
        // 在中低端设备上会把横向手势拖入布局预算。Pager 仍会绘制当前可见的
        // 邻页部分，但不再额外保留视口外页面。
        beyondViewportPageCount = 0,
        key = { page -> controller.dateForPage(page).toEpochDay() }
    ) { page ->
        val date = controller.dateForPage(page)
        val week = TimeUtils.semesterWeekOrNull(semesterStart, totalWeeks, date) ?: 1
        val dayCourses = remember(
            displayedCourses,
            date,
            semesterStart,
            totalWeeks,
            excludedWeekSet,
            dateExceptions
        ) {
            ScheduleDateResolver.coursesOn(
                displayedCourses,
                semesterStart,
                totalWeeks,
                excludedWeekSet,
                dateExceptions,
                date
            ).map { it.course.copy(dayOfWeek = date.dayOfWeek.value) }
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .testTag("day-scroll")
        ) {
            TableGrid(
                courses = dayCourses,
                colorMap = colorMap,
                visibleDays = listOf(date.dayOfWeek.value),
                timeWidth = timeWidth,
                cellHeight = cellHeight,
                currentWeek = week,
                onCourseClick = onCourseClick,
                onEmptyCellClick = onEmptyCellClick,
                periodTimes = periodTimes,
                dark = dark,
                todayWeek = todayWeek,
                todayDow = today.dayOfWeek.value,
                viewMode = ScheduleViewMode.DAY,
                hasCustomBackground = hasCustomBackground
            )
        }
    }
}
