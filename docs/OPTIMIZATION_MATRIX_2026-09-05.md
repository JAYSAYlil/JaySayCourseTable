# 2026-09-05 优化清单（v3.4.12）

基线：main 分支提交 `cb0d186`（v3.4.11 / versionCode 131），工作区干净后开始修改。
本轮改动以 v3.4.12 / versionCode 132 签名交付并发布 GitHub Release。
状态分类：**完成** / 部分完成 / 已修复无需改 / 待设备验证 / 未完成。

## 阶段一：确定性问题

### A. 课程卡片对比度及测试 — 完成
- **核验**：属实。旧测试 `courseTextKeepsReadableContrastWithCustomBackgrounds` 使用死常量
  `CourseTextColor/CourseSubTextColor`（渲染层无人引用），且未覆盖深色三段渐变；次级文字只要求 3:1。
- **修改**：`ui/theme/Color.kt` 新增 `courseCardFillStops`（渲染与测试共用的填充单一来源，深色高光按基色自适应、合成亮度封顶 0.15）、
  `courseCardUnderlays`（遮罩开/关极端底色）、`courseCardWorstTextBackplate`、最小混色派生 `courseCardTextColors`
  （标题 5.5:1、次级 4.5:1；增强对比度 7:1/5.5:1，经 `LocalEnhancedContrast` 贯通到卡片描边）；
  `ScheduleGrid` 改用共享 stops 绘制；删除 4 个死常量。
- **验证**：`CourseColorTest` 重写为真实渲染矩阵（浅/深 × 遮罩开/关 × 48 色全部 4.5:1，标题≥次级，高光上限，
  增强对比度实际改善，浅色标题不坍缩为纯墨色）13 项全过。测试中曾抓出派生方向写反的真 bug 并修复。
- **备注**：色相辨识保留（最小混色）；卡片不透明度深色+自定义背景 0.90→0.96（白字在纯白壁纸上可达 4.5:1 的最低值，遮罩关闭场景计入）。

### B. 跨午夜日期状态 — 完成
- **核验**：属实。`MonthGrid` 用 `remember { LocalDate.now() }`；`CourseTableScreen`/`DayScheduleView`/`AgendaScreen` 用裸 `LocalDate.now()`，组合存活时不跨日更新。
- **修改**：新增 `util/TodayState.kt`（可注入时钟、ON_RESUME 校准、ACTION_DATE_CHANGED/TIME_CHANGED/TIMEZONE_CHANGED 广播，值不变不重组）；
  月视图、主屏（today/todayWeek/定位目标/月锚点兜底）、日视图、日程列表接入；`rememberTodayAgenda` 改为接收 today 参数。
- **验证**：`TodayStateTest` 3 项（跨日更新、日期未变不更新、时区注入）通过；模拟器无法不改设备时间跨午夜，实际跨日待真机验收。
- **约束**：不新增轮询；不改变浏览周次/月份/滚动位置（未触碰锚点与 Pager 逻辑）。

### C. Hero 详情转场中断 — 完成（流畅度待真机验收）
- **核验**：属实。`remember(request) { Animatable(if (forward) 0f else 1f) }` 导致正向中途返回时从 1f 重新起飞。
- **修改**：`HeroRegistry` 登记实时进度 `lastProgress` 与 `lastFlightKey`；`seedProgress` 决定新请求起点
  （同键反向续接在途进度；正向完成后反向仍为 1f；换课从 0f；反向中途再进入同卡从当前进度折返）；
  overlay 用 snapshotFlow 持续登记进度；旋转重建后同样续接（registry 为进程级状态）。
- **验证**：`HeroTransitionSeedTest` 6 项（中断返回/完成返回/中途重入/换课/首飞）通过；
  `MainActivitySmokeTest.courseDetailBackRestoresTheExactScheduleScrollPosition` 等详情往返 UI 测试通过。
- **待验收**：视觉“无缝”结论需真机 120Hz/60Hz 实测，不在此声称。

## 阶段二：阅读效率与 UI

### D. 周视图信息层级 — 完成
- **核验**：属实。旧逻辑按 `courseName+teacher+classroom` 字符总数把标题压到 8sp、辅助压到 7sp。
- **修改**：`TableGrid` 改 BoxWithConstraints 计算实际列宽；`CourseCard` 按 `列宽 ÷ 字体缩放` 分三档
  （≥42dp：10.5/8.5sp；≥34dp：10/8.25sp；<34dp：9/8sp 下限），<34dp 或用户开启精简时教师进入详情；
  `TextOverflow.Clip`→`Ellipsis`；新增偏好 `weekCardCompactInfo`（默认关闭=旧行为）及设置开关。
- **验证**：320dp 模拟器截图（`visual-check/2026-09-05/01-week-light.png`）：列宽 39dp 命中中档，课程名/教师/教室完整可读；
  深色（10-week-dark.png）同样达标；`ViewPagingNavigationTest`/`DayViewDateNavigationTest` 通过。
- **未做**：窄屏额外“五天/日程”推荐入口——视图菜单已常驻且 48dp 可点击，强推引导有打扰风险，如需再做。

### E. 月视图空间分配 — 完成
- **核验**：属实。日期格 10sp、课程文字用彩色圆点底色派生而文字实际位于中性单元格背景。
- **修改**：日期数字 11sp；课程行文字改为 `onSurface` 派色（圆点承载色相）；行高 <64dp 隐藏农历、
  <52dp 课程名降级为“N 门课”（新字符串 `month_course_summary`），停课/节假日/补课状态任何档位保留；
  “+N 节”与头部“共 N 节课”节数口径未动。
- **验证**：320dp 月视图截图（03-month-light.png）：农历/状态/课程名层次清晰，今日描边正常；
  `MonthGridNavigationTest` 4 项通过。六行月+超大字体场景依赖行高分档逻辑，待真机抽查。

### F. 顶栏操作排序 — 完成
- **核验**：属实。紧凑宽度常驻搜索/新增/导入/更多四按钮，定位今天藏在菜单。
- **修改**：紧凑宽度导入移入更多菜单（新增 `import-course-menu-item`），空课表状态保留明显导入按钮（原有）；
  浏览非今日日期（周/五天：`currentWeek != todayWeek`；月：锚点月≠今日月；日：预览日≠今日）时“回到今天”直达按钮出现；
  所有交互目标保持 48dp；TalkBack 标签保留。
- **验证**：`ScheduleOverviewBarTest` 更新为 3 项（菜单收纳+标签、深色可见性、离开今日直达）全过；
  `MonthGridNavigationTest.locateTodayMovesMonthPagerBackToCurrentMonth` 更新为新契约后通过；
  模拟器截图 11-moremenu-dark.png（今日周：定位在菜单）与 03（离开今日：直达按钮出现）对照验证。

### G. 收敛视觉层级 — 完成
- **修改**：普通课程阴影 深 4dp→3dp、浅 2dp→1.5dp；正在上课 6dp→8dp 并保持品牌描边过渡（强调对比更清楚）；
  卡片圆角 12/14dp 归入 `AppShapes.small`/`AppShapes.input`；青绿品牌色、浅色纯白、深色渐变、自定义背景与遮罩开关未动。
- **验证**：视觉截图浅/深两模式无回归；全部 JVM/UI 测试通过。

### H. 动效一致性 — 部分完成
- **已一致**：按压反馈统一走 `pressScale`（临界阻尼、按下即缩）；当前课描边 `animateColorAsState` 轻量过渡；
  翻页弹簧令牌未动；导入成功反馈为非阻塞 Toast（原有，含新增/合并/跳过计数）。
- **修改**：搜索框展开第一帧即持焦（`FocusRequester`），输入立即可用。
- **未加过渡的说明**：搜索展开未加动画——顶栏两侧均为 `weight(1f)`，交叉淡入淡出期间双节点并存会造成布局跳动，
  瞬时切换 + 立即聚焦在 320dp 实测体验优于短过渡。
- **待验证**：Compose 动画对系统“移除动画/动画时长缩放”的遵从性需真机开关对照；未因此新增独立开关（按任务要求先验证再判定）。

## 阶段三：性能

### I. 月视图颜色映射重复构建 — 完成
- **核验**：属实。`MonthGrid` 对每门去重课程调用 `resolveCourseColor`，其内部每次重建整表映射（O(n²)）。
- **修改**：`Color.kt` 新增 `buildResolvedCourseColorMap`（基础映射一次构建 + 自定义色覆盖，语义与 `resolveCourseColor` 一致）；
  月视图、主屏 colorMap、详情页统一改用；周视图卡片不再逐卡判断 customColor（映射已含）。
- **验证**：`CourseColorTest.resolvedColorMapMatchesPerCourseResolution`（浅/深 × 自定义覆盖/非法下标回退）通过；
  小课表（<10 门）实际收益微小，如实说明；大课表（数百门）月视图翻页减少明显重复编码。

### J. 视图切换持久化成本 — 完成（采用 J.7 允许的较小优化）
- **核验**：属实。`setScheduleViewMode` 经 `mutateTable` 整表编码保存后才更新 UI，且产生一条与上一次课程内容相同的无意义历史快照。
- **修改**：UI 状态立即切换，写入仍走串行互斥区；失败时若展示状态仍停留在失败模式则回滚并回调 onError（提示不变）；
  `CourseRepository.persistWithHistory` 对“仅视图模式差异”的变化跳过课程历史快照（课程变更照常快照）。
- **权衡**：视图模式仍存于 tables.json、仍整表编码写入——保持单一存储文件、备份/复制/归档/重启行为与 schema 完全不变；
  未迁移存储架构，未以列表下标建持久化关联（字段仍在各表内）。
- **验证**：`CourseRepositoryHistoryTest.viewModeOnlyChangePersistsWithoutCourseSnapshot`（持久化生效 + 0 快照 + 课程变更仍有快照）通过；
  `MainActivitySmokeTest.selectedViewModeSurvivesActivityRecreation` 通过；模拟器实测切换视图/重启后视图模式保留。

### K. 体验性能基线 — 部分完成（无网络/无真机，无数值）
- **现状**：仓库此前无 Macrobenchmark/Baseline Profile 设施。
- **新增**：`app` 增加 `benchmark` 变体（R8 同 Release，debuggable=false，不影响 debug/release 与 CI）；
  `benchmarks/macrobenchmark/` 模块脚手架（冷启动/连续翻页/详情往返三场景 + 记录规范）与 `benchmarks/README.md`；
  因当前环境无法访问 Google Maven（已实测超时），模块**未接入 settings.gradle.kts**，未编译验证，未取得任何数值。
- **保留**：分钟刷新作用域收敛、背景降采样、轻量 Excel 解析等既有优化未触碰。
- **待办**：网络 + 真机条件下按 README 接入并测量；Baseline Profile 是否引入以首次测量结果判断。

## 阶段四：可维护性

### L. 按职责拆分 — 部分完成
- **完成**：`ui/screen/CourseTableSections.kt`（新文件）收编主屏无状态区块（星期条、日期表头、周进度标尺、校历提示条、空课表），
  `CourseTableScreen.kt` 1355→1055 行，行为不变（提取前后 JVM/UI 测试全过）；日视图控制器此前已独立于 `DayScheduleView.kt`，未复制第二份状态。
- **未完成**：`MainActivity.kt`（导航协调）、`SettingsScreen.kt`（设置分组）拆分——两者与弹窗/结果器状态耦合深，
  在未发版的本轮强行提取会放大审查面；建议下一轮按“导航协调器 / 设置分组数据驱动”分别提取（先行为不变提取、再交互调整）。

### M. 回归矩阵 — 部分完成
- **更新**：`ScheduleOverviewBarTest`（+直达回今天用例）、`MainActivitySmokeTest`（导入契约）、`MonthGridNavigationTest`（定位契约）。
- **新增 JVM**：对比度真实渲染矩阵 13 项、`TodayStateTest` 3 项、Hero 种子 6 项、视图模式免快照 1 项、映射一致性 1 项。
- **复跑说明**：首次全量 49/51 → 修正 2 项断言 → 顶栏宽屏去重按钮修复 → `pm clear` 清理注入数据后全量绿跑 51/51（2026-09-05 11:34，XML 见 androidTest-results）。
- **既有覆盖确认**：连续翻页/跨周/学期钳制（`DayViewDateNavigationTest`、`ViewPagingNavigationTest`）、月→日→返回（`MonthGridNavigationTest`）、
  详情滚动恢复与旋转（Smoke）、备份恢复/只读保护（`WriteProtectionGateTest` 等）、导入合并（`CourseMergerTest`）、提醒（`Reminder*Test`）。
- **缺口**：时区变化广播的 UI 级测试、遮罩开关的截图对照、小组件跨日刷新 UI 测试——均需稳定模拟器时序控制，留待下一轮。

### N. 文档同步 — 完成
- `docs/DEVELOPMENT_STATUS.md` 新增本轮段落（不改写历史）；`docs/MAINTENANCE.md` 偏好字段描述修正
  （删除不存在的“减少动画”表述，补充 `weekCardCompactInfo`）；本矩阵即本轮优化清单。
- 版本号未动，README/CHANGELOG/版本行无需变更，CI 同步检查不受影响。
- 历史交付记录（`交付记录/`）原样保留。

## 本次运行结果汇总（2026-09-05，均在本机执行）

| 检查 | 命令 | 结果 |
| --- | --- | --- |
| 基线 JVM | `:app:testDebugUnitTest --rerun-tasks` | 138 项，0 失败 |
| 改动后 JVM | `:app:testDebugUnitTest` | 154 项，0 失败 |
| UI（API 34 模拟器 320dp） | `:app:connectedDebugAndroidTest` | 最终代码状态单次完整运行：51 项，0 失败（期间一次单例失败经查为注入的虚构数据污染测试环境，清理 `pm clear` 后全量绿跑复现） |
| 视觉验证 | adb screencap（虚构数据） | 11 张截图，浅/深 × 周/日/月 × 菜单 |
| Lint / Release | `:app:lintRelease :app:assembleRelease` | Lint 0 错误（阻断级）；Release APK 2,280,263 字节（约 2.28 MB，与 v3.4.11 的约 2.30 MB 相当）；顶栏修复后 JVM+Release 复跑通过（日志 final-gate.log） |

## 发布前仍需真机验收

1. Hero 转场：打开即返回、快速连续进出、滚动后打开、旋转——视觉无缝与掉帧。
2. 跨午夜：放置到 23:59 后校验今日高亮/周次徽标/摘要自动更新（广播 + 回前台两条路径）。
3. 系统动画关闭（开发者选项-动画时长缩放=0）下全应用动效终态。
4. 自定义背景 + 遮罩开/关 × 深浅色的实机观感（卡片 0.96 不透明度的观感确认）。
5. 真机 Macrobenchmark 三场景首轮测量（需网络接入 benchmark 模块）。
