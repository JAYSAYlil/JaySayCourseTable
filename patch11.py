import io

p = r"app/src/main/java/com/jaysay/coursetable/ui/screen/CourseTableScreen.kt"
s = io.open(p, encoding="utf-8").read()

# ── 1) 状态：dayJumpEpoch（一次性令牌）→ dayViewDateEpoch（单一事实来源） ──
old = """    // 日视图跳转令牌（epoch day，0 = 无）：定位今天/点星期条/月视图点日期都发"目标日期"，
    // 翻页器只认日期——杜绝用（周数, 星期数）两个异步更新的状态拼回日期的竞态
    // （表现为"定位到今天"跳到了显示周的同星期数那一天）。
    var dayJumpEpoch by rememberSaveable { androidx.compose.runtime.mutableLongStateOf(0L) }"""
new = """    // 日视图单一事实来源：当前显示的日期（epoch day）。
    // 初始 = 钳制进学期范围的今天（今天在学期外时落在最近的学期边界，
    // 而不是从（周数, 星期数）错误重建出的日期）。所有跳转源都直接改这个日期，
    // 星期条/日期表头/周胶囊全部从它派生——不存在多套并行状态。
    var dayViewDateEpoch by rememberSaveable {
        val start = TimeUtils.semesterWeekStartOrNull(semesterStart)
        val end = start?.plusDays((totalWeeks.coerceAtLeast(1) * 7L) - 1L)
        val t = LocalDate.now()
        val clamped = if (start != null && end != null) t.coerceIn(start, end) else t
        androidx.compose.runtime.mutableLongStateOf(clamped.toEpochDay())
    }"""
assert old in s, "state"
s = s.replace(old, new, 1)

# ── 2) 定位今天 ──
old = """                if (viewMode == ScheduleViewMode.DAY) {
                    dayJumpEpoch = today.toEpochDay()
                }"""
new = """                if (viewMode == ScheduleViewMode.DAY) {
                    val start = TimeUtils.semesterWeekStartOrNull(semesterStart)
                    val end = start?.plusDays((totalWeeks.coerceAtLeast(1) * 7L) - 1L)
                    dayViewDateEpoch = if (start != null && end != null) {
                        today.coerceIn(start, end)
                    } else today
                }.toEpochDay().let { }"""
# 上面写法复杂化错了，直接简化：
new = """                if (viewMode == ScheduleViewMode.DAY) {
                    val start = TimeUtils.semesterWeekStartOrNull(semesterStart)
                    val end = start?.plusDays((totalWeeks.coerceAtLeast(1) * 7L) - 1L)
                    dayViewDateEpoch = (if (start != null && end != null) today.coerceIn(start, end) else today).toEpochDay()
                }"""
assert old in s, "locate"
s = s.replace(old, new, 1)

# ── 3) 星期条：从显示日期派生选中态，点击改显示日期 ──
old = """            if (viewMode == ScheduleViewMode.DAY) {
                DayChipRow(
                    focusedDay = focusedDay,
                    todayDow = todayDow,
                    highlightToday = isTodayWeek,
                    onFocusedDayChange = { day ->
                        onFocusedDayChange(day)
                        semesterStartDate?.let { start ->
                            dayJumpEpoch = start.plusDays((currentWeek - 1L).coerceAtLeast(0) * 7L + (day - 1L)).toEpochDay()
                        }
                    }
                )
            }"""
new = """            if (viewMode == ScheduleViewMode.DAY) {
                val displayedDate = LocalDate.ofEpochDay(dayViewDateEpoch)
                DayChipRow(
                    focusedDay = displayedDate.dayOfWeek.value,
                    todayDow = todayDow,
                    highlightToday = displayedDate.let { d ->
                        val w = TimeUtils.semesterWeekOrNull(semesterStart, totalWeeks, d)
                        w != null && w == todayWeek
                    },
                    onFocusedDayChange = { day ->
                        // 跳到显示周的同星期数那一天（星期条是周内导航）。
                        val displayed = LocalDate.ofEpochDay(dayViewDateEpoch)
                        val weekStart = displayed.minusDays((displayed.dayOfWeek.value - 1).toLong())
                        dayViewDateEpoch = weekStart.plusDays((day - 1).toLong()).toEpochDay()
                        onFocusedDayChange(day)
                    }
                )
            }"""
assert old in s, "chips"
s = s.replace(old, new, 1)

# ── 4) DayHeader：日视图从显示日期派生 ──
old = """            // 月视图不展示课程表头（星期标题由月历自带），周导航胶囊与进度条仍然保留。
            if (viewMode != ScheduleViewMode.MONTH) {
                DayHeader(
                    visibleDays = visibleDays,
                    timeWidth = timeWidth,
                    currentWeek = currentWeek,
                    semesterStart = semesterStart,"""
new = """            // 月视图不展示课程表头（星期标题由月历自带），周导航胶囊与进度条仍然保留。
            if (viewMode != ScheduleViewMode.MONTH) {
                val dayDisplayedDate = LocalDate.ofEpochDay(dayViewDateEpoch)
                val dayDisplayedWeek = TimeUtils.semesterWeekOrNull(semesterStart, totalWeeks, dayDisplayedDate) ?: currentWeek
                DayHeader(
                    visibleDays = if (viewMode == ScheduleViewMode.DAY) listOf(dayDisplayedDate.dayOfWeek.value) else visibleDays,
                    timeWidth = timeWidth,
                    currentWeek = if (viewMode == ScheduleViewMode.DAY) dayDisplayedWeek else currentWeek,
                    semesterStart = semesterStart,"""
assert old in s, "day header"
s = s.replace(old, new, 1)

# ── 5) 表头点击（日视图下跳显示周对应日）──
old = """                    onDayClick = { day ->
                        dayOpenedFromMonth = false
                        if (viewMode == ScheduleViewMode.DAY) {
                            semesterStartDate?.let { start ->
                                dayJumpEpoch = start.plusDays((currentWeek - 1L).coerceAtLeast(0) * 7L + (day - 1L)).toEpochDay()
                            }
                        }
                        onFocusedDayChange(day)
                        onViewModeChange(ScheduleViewMode.DAY)
                    }"""
new = """                    onDayClick = { day ->
                        dayOpenedFromMonth = false
                        if (viewMode == ScheduleViewMode.DAY) {
                            val displayed = LocalDate.ofEpochDay(dayViewDateEpoch)
                            val weekStart = TimeUtils.semesterWeekStartOrNull(semesterStart)
                                ?.plusDays((TimeUtils.semesterWeekOrNull(semesterStart, totalWeeks, displayed) ?: 1) - 1L)
                                ?: displayed
                            dayViewDateEpoch = weekStart.plusDays((day - 1).toLong()).toEpochDay()
                        }
                        onFocusedDayChange(day)
                        onViewModeChange(ScheduleViewMode.DAY)
                    }"""
assert old in s, "header click"
s = s.replace(old, new, 1)

# ── 6) 月视图点日期 ──
old = """                            dayOpenedFromMonth = true
                            monthAnchorEpoch = pageMonthStart.toEpochDay()
                            dayJumpEpoch = date.toEpochDay()"""
new = """                            dayOpenedFromMonth = true
                            monthAnchorEpoch = pageMonthStart.toEpochDay()
                            dayViewDateEpoch = date.toEpochDay()"""
old = """                            dayOpenedFromMonth = true
                            monthAnchorEpoch = pageMonthStart.toEpochDay()
                            dayJumpEpoch = date.toEpochDay()"""
assert old in s, "month tap v2"
s = s.replace(old, new, 1)

# ── 7) DayPagerSection 调用与实现：以 displayedDate 为单一来源 ──
old = """                onCourseClick = onCourseClick,
                jumpTargetEpoch = dayJumpEpoch,
                onJumpConsumed = { dayJumpEpoch = 0L },"""
new = """                onCourseClick = onCourseClick,
                displayedDateEpoch = dayViewDateEpoch,
                onDisplayedDateChange = { dayViewDateEpoch = it },"""
assert old in s, "call"
s = s.replace(old, new, 1)

old = """private fun DayPagerSection(
    modifier: Modifier,
    jumpTargetEpoch: Long,
    onJumpConsumed: () -> Unit,
    displayedCourses: List<Course>,"""
new = """private fun DayPagerSection(
    modifier: Modifier,
    displayedDateEpoch: Long,
    onDisplayedDateChange: (Long) -> Unit,
    displayedCourses: List<Course>,"""
assert old in s, "sig"
s = s.replace(old, new, 1)

# 实现：initial/token/align 三套全删，换单一同步
old = """    val semesterEndDate = semesterStartDate?.plusDays(totalDays - 1L)
    // 初始显示日期 = 钳制进学期范围的"今天"：今天在学期内显示今天，
    // 在学期外（开学前/学期结束后）落在最近的学期边界。
    // 不从（周数, 星期数）重建——那会把"开学前的周二"错误重建为"开学后第一周的周二"。
    val initialEpoch = today.let { raw ->
        if (semesterStartDate != null && semesterEndDate != null) {
            raw.coerceIn(semesterStartDate, semesterEndDate)
        } else raw
    }.toEpochDay()
    var reportedEpoch by rememberSaveable { androidx.compose.runtime.mutableLongStateOf(initialEpoch) }
    val pagerState = rememberPagerState(
        initialPage = indexOf(LocalDate.ofEpochDay(reportedEpoch)).coerceIn(0, totalDays - 1)
    ) { totalDays }
    var lastSettledDayPage by rememberSaveable { androidx.compose.runtime.mutableIntStateOf(pagerState.currentPage) }

    // 外部跳转（定位今天/星期条/月视图点日期）：以日期为一次性目标，精确跳页。
    // 目标钳制进学期范围：定位今天在学期外时落在最近的学期边界。
    // 不从（周数, 星期数）重建日期——两个状态异步到达会拼出错误的中间日期。
    LaunchedEffect(jumpTargetEpoch) {
        if (jumpTargetEpoch != 0L) {
            val targetDate = LocalDate.ofEpochDay(jumpTargetEpoch).let { raw ->
                if (semesterStartDate != null && semesterEndDate != null) {
                    raw.coerceIn(semesterStartDate, semesterEndDate)
                } else raw
            }
            val index = indexOf(targetDate).coerceIn(0, totalDays - 1)
            reportedEpoch = targetDate.toEpochDay()
            if (pagerState.settledPage != index) {
                pagerState.animateScrollToPage(index)
            }
            onJumpConsumed()
        }
    }

    // 首次组合对齐：头部周次/聚焦日回写为初始页（钳制后的今天）。
    LaunchedEffect(Unit) {
        if (jumpTargetEpoch == 0L) {
            val d = dateOf(pagerState.currentPage)
            val week = TimeUtils.semesterWeekOrNull(semesterStart, totalWeeks, d)
            if (week != null) onWeekChange(week)
            onFocusedDayChange(d.dayOfWeek.value)
        }
    }

    // 滑动落定：把页码换算回周次与聚焦日，回写视图状态（头部胶囊/日选择条随之刷新）。
    val hapticView = LocalView.current
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            val date = dateOf(page)
            val pageChanged = page != lastSettledDayPage
            lastSettledDayPage = page
            if (date.toEpochDay() != reportedEpoch) {
                reportedEpoch = date.toEpochDay()
                val week = TimeUtils.semesterWeekOrNull(semesterStart, totalWeeks, date)
                if (week != null) {
                    onWeekChange(week)
                    onFocusedDayChange(date.dayOfWeek.value)
                }
            }
            if (pageChanged) hapticView.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
        }
    }"""
new = """    // 显示日期由外部单一代码路径驱动：翻页落定 → 回写 displayedDateEpoch；
    // 外部跳转（定位今天/星期条/月视图）改 displayedDateEpoch → 此处滚动跟随。
    // 单一状态、双向同步，无并行通道。
    val displayedDate = LocalDate.ofEpochDay(displayedDateEpoch)
    val pagerState = rememberPagerState(
        initialPage = indexOf(displayedDate).coerceIn(0, totalDays - 1)
    ) { totalDays }

    LaunchedEffect(displayedDateEpoch) {
        val index = indexOf(LocalDate.ofEpochDay(displayedDateEpoch)).coerceIn(0, totalDays - 1)
        if (pagerState.settledPage != index && !pagerState.isScrollInProgress) {
            pagerState.animateScrollToPage(index)
        }
    }

    val hapticView = LocalView.current
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            val date = dateOf(page)
            if (date.toEpochDay() != displayedDateEpoch) {
                onDisplayedDateChange(date.toEpochDay())
                val week = TimeUtils.semesterWeekOrNull(semesterStart, totalWeeks, date)
                if (week != null) {
                    onWeekChange(week)
                    onFocusedDayChange(date.dayOfWeek.value)
                }
                hapticView.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
            }
        }
    }"""
assert old in s, "pager impl"
s = s.replace(old, new, 1)

# 页内 TableGrid 用页面自身日期（已有）；todayWeek 引用检查
s = s.replace("    val todayWeek = todayWeekOf(today, semesterStart, totalWeeks)\n    HorizontalPager(",
              "    val todayWeek = todayWeekOf(today, semesterStart, totalWeeks)\n    HorizontalPager(", 1)

io.open(p, "w", encoding="utf-8", newline="").write(s)
print("single-source refactor applied")
