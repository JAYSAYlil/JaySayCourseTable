import io

# ── 1) MonthGrid：回调改为传完整日期 ──
p = r"app/src/main/java/com/jaysay/coursetable/ui/screen/MonthGrid.kt"
s = io.open(p, encoding="utf-8").read()
s = s.replace("    onDayClick: (semesterWeek: Int, dayOfWeek: Int) -> Unit\n) {",
              "    onDayClick: (date: java.time.LocalDate) -> Unit\n) {", 1)
s = s.replace("    onDayClick: (semesterWeek: Int, dayOfWeek: Int) -> Unit\n) {",
              "    onDayClick: (date: java.time.LocalDate) -> Unit\n) {", 1)
s = s.replace("                onDayClick(week, cell.dayOfWeek)",
              "                onDayClick(cell.date)", 1)
io.open(p, "w", encoding="utf-8", newline="").write(s)

# ── 2) CourseTableScreen：日期令牌 + 各跳转源改发日期 ──
p = r"app/src/main/java/com/jaysay/coursetable/ui/screen/CourseTableScreen.kt"
s = io.open(p, encoding="utf-8").read()

# 2a) 状态：日视图一次性跳转目标（0 = 无）
old = """    var sharedOriginKey by rememberSaveable { mutableStateOf<String?>(null) }"""
new = """    var sharedOriginKey by rememberSaveable { mutableStateOf<String?>(null) }
    // 日视图跳转令牌（epoch day，0 = 无）：定位今天/点星期条/月视图点日期都发"目标日期"，
    // 翻页器只认日期——杜绝用（周数, 星期数）两个异步更新的状态拼回日期的竞态
    // （表现为"定位到今天"跳到了显示周的同星期数那一天）。
    var dayJumpEpoch by rememberSaveable { androidx.compose.runtime.mutableLongStateOf(0L) }"""
assert old in s, "jump state"
s = s.replace(old, new, 1)

# 2b) 定位今天：日视图下发日期令牌；其他视图保持原逻辑
old = """                onLocateToday = onLocateToday,
                onAgendaClick = onAgendaClick,"""
new = """                onLocateToday = {
                    if (viewMode == ScheduleViewMode.DAY) {
                        dayJumpEpoch = LocalDate.now().toEpochDay()
                    } else {
                        onLocateToday()
                    }
                },
                onAgendaClick = onAgendaClick,"""
assert old in s, "locate today"
s = s.replace(old, new, 1)

# 2c) 表头点击：日视图下改为日期跳转（跳到当前周对应日期），其他模式保持原逻辑
old = """                    onDayClick = { day ->
                        dayOpenedFromMonth = false
                        onFocusedDayChange(day)
                        onViewModeChange(ScheduleViewMode.DAY)
                    }
                )"""
new = """                    onDayClick = { day ->
                        dayOpenedFromMonth = false
                        if (viewMode == ScheduleViewMode.DAY) {
                            semesterStartDate?.let { start ->
                                dayJumpEpoch = start.plusDays((currentWeek - 1L).coerceAtLeast(0) * 7L + (day - 1L)).toEpochDay()
                            }
                        }
                        onFocusedDayChange(day)
                        onViewModeChange(ScheduleViewMode.DAY)
                    }
                )"""
assert old in s, "header click"
s = s.replace(old, new, 1)

# 2d) 日视图星期条点击：发日期令牌
old = """                DayChipRow(
                    focusedDay = focusedDay,
                    todayDow = todayDow,
                    highlightToday = isTodayWeek,
                    onFocusedDayChange = onFocusedDayChange
                )"""
new = """                DayChipRow(
                    focusedDay = focusedDay,
                    todayDow = todayDow,
                    highlightToday = isTodayWeek,
                    onFocusedDayChange = { day ->
                        onFocusedDayChange(day)
                        semesterStartDate?.let { start ->
                            dayJumpEpoch = start.plusDays((currentWeek - 1L).coerceAtLeast(0) * 7L + (day - 1L)).toEpochDay()
                        }
                    }
                )"""
assert old in s, "chip click"
s = s.replace(old, new, 1)

# 2e) 月视图点日期：回调签名已改为 date，直接发令牌
old = """                        onDayClick = { semesterWeek, dayOfWeek ->
                            // 点击某天：跳到该天所在周并切到单日视图；记住来源月供返回使用。
                            dayOpenedFromMonth = true
                            monthAnchorEpoch = pageMonthStart.toEpochDay()
                            onWeekChange(semesterWeek)
                            onFocusedDayChange(dayOfWeek)
                            onViewModeChange(ScheduleViewMode.DAY)
                        }"""
new = """                        onDayClick = { date ->
                            // 点击某天：跳到该天所在周并切到单日视图；记住来源月供返回使用。
                            dayOpenedFromMonth = true
                            monthAnchorEpoch = pageMonthStart.toEpochDay()
                            dayJumpEpoch = date.toEpochDay()
                            onWeekChange(TimeUtils.semesterWeekOrNull(semesterStart, totalWeeks, date) ?: 1)
                            onFocusedDayChange(date.dayOfWeek.value)
                            onViewModeChange(ScheduleViewMode.DAY)
                        }"""
assert old in s, "month tap"
s = s.replace(old, new, 1)

# 2f) DayPagerSection 调用：传令牌 + 消费回调
old = """                onCourseClick = onCourseClickShared,
                sharedOriginKey = sharedOriginKey,
                onEmptyCellClick = if (readOnlyMessage == null) onAddCourseAt else ({ _, _ -> }),
                periodTimes = periodTimes,
                visibleDays = visibleDays,"""
new = """                onCourseClick = onCourseClickShared,
                sharedOriginKey = sharedOriginKey,
                jumpTargetEpoch = dayJumpEpoch,
                onJumpConsumed = { dayJumpEpoch = 0L },
                onEmptyCellClick = if (readOnlyMessage == null) onAddCourseAt else ({ _, _ -> }),
                periodTimes = periodTimes,
                visibleDays = visibleDays,"""
assert old in s, "day call params"
s = s.replace(old, new, 1)

# 2g) DayPagerSection 签名
old = """private fun DayPagerSection(
    modifier: Modifier,
    sharedOriginKey: String?,
    displayedCourses: List<Course>,"""
new = """private fun DayPagerSection(
    modifier: Modifier,
    sharedOriginKey: String?,
    jumpTargetEpoch: Long,
    onJumpConsumed: () -> Unit,
    displayedCourses: List<Course>,"""
assert old in s, "day sig"
s = s.replace(old, new, 1)

# 2h) DayPagerSection 同步逻辑：外部(week,day)回正 → 日期令牌跳转
old = """    // 外部状态变化（定位今天/表头点击/月视图跳天/返回自月视图）时滚动到对应页。
    LaunchedEffect(currentWeek, focusedDay, semesterStart) {
        val target = semesterStartDate
            .plusDays((currentWeek - 1L).coerceAtLeast(0) * 7L + (focusedDay - 1).coerceAtLeast(0))
        val index = indexOf(target).coerceIn(0, totalDays - 1)
        if (pagerState.settledPage != index && !pagerState.isScrollInProgress) {
            pagerState.animateScrollToPage(index)
        }
    }"""
new = """    // 外部跳转（定位今天/星期条/月视图点日期）：以日期为一次性目标，精确跳页。
    // 不再从（周数, 星期数）重建日期——两个状态异步到达会把中间态拼成错误的日期，
    // 且旧实现的滚动守卫会丢弃随后到达的正确跳转。
    LaunchedEffect(jumpTargetEpoch) {
        if (jumpTargetEpoch != 0L) {
            val index = indexOf(LocalDate.ofEpochDay(jumpTargetEpoch)).coerceIn(0, totalDays - 1)
            reportedEpoch = jumpTargetEpoch
            if (pagerState.settledPage != index) {
                pagerState.animateScrollToPage(index)
            }
            onJumpConsumed()
        }
    }"""
assert old in s, "day sync"
s = s.replace(old, new, 1)

io.open(p, "w", encoding="utf-8", newline="").write(s)
print("day jump token implemented")
