# JaySay 课程表维护说明

## 项目边界

- Android 应用模块：`app/`
- 包名与 applicationId：`com.jaysay.coursetable`
- 最低/目标系统：Android 8.0（API 26）/ Android 15（API 35）
- 当前版本：3.4.11，versionCode 131
- 构建栈：Gradle 9.1.0 / AGP 8.13.0 / Kotlin 2.2.0（内置 Compose 编译器）/ Compose BOM 2025.06.01
- 应用仅在用户手动检查更新时访问 GitHub Releases；不包含常驻联网客户端，课程、备份、提醒和日历均在本机处理。
- 全部 UI 用户可见文案位于 `res/values/strings.xml`；数据层错误消息保持纯 Kotlin 数据（不依赖 Context）。

## 主要结构

- `MainViewModel.kt`：界面状态、串行写入、多课表和暂存导入。
- `data/repository/`：课表 JSON、规范化与原子持久化。
- `data/preferences/`：主题、活动课表、提醒偏好与私有自定义背景存储。
- `data/backup/`：完整备份、脱敏副本与严格恢复校验。
- `data/history/`：最近 10 次本机课表快照、差异与恢复。
- `data/diagnostics/`：固定白名单字段的脱敏诊断报告。
- `data/parser/`：Excel 与粘贴文本导入。
- `data/reminder/`：提醒计算、AlarmManager 调度、通知、系统事件恢复、权限/渠道状态诊断（ReminderPermissions）与自启动引导（AutostartHelper）。
- `data/ical/`：标准 `.ics` 日历导出。
- `data/model/AcademicCalendarStatus.kt`：把停课周、周标签和具体日期调整转换为课表/小组件共用的展示状态；新增入口不得另写一套校历解释逻辑。
- `widget/`：3/4/5 列响应式桌面小组件、今日/明日集合数据与状态边界刷新；Android 12+ 使用 `RemoteCollectionItems`，Android 8–11 保留 `RemoteViewsService`。
- `ui/`：课表、详情、编辑、设置、导入确认和公共组件；`ui/components/CourseDialogs.kt` 集中承载删除、冲突确认、备份密码与粘贴导入等弹窗。
- `ui/screen/ScheduleGrid.kt`：课表网格、课程卡片、当前时间线与透明度/排版视觉契约；`CourseTableScreen.kt` 负责页面控制和周次/视图状态。

## 本地数据与兼容性

- `files/tables.json`：schemaVersion 4，包含多课表、课程、节次、学期、视图模式、日期例外、周标签和归档状态。
- `files/preferences.json`：schemaVersion 4，包含主题、活动课表、提醒、增强对比度、减少动画、背景缓存版本标记和可读遮罩开关；旧数据缺少遮罩字段时默认开启。
- `files/appearance/custom_background.jpg`：经限尺寸、方向校正和去元数据重编码的本机背景；故意不写入完整备份或脱敏副本。
- `files/course_history/`：最多 10 份自动快照；文件名不含课程正文。
- `files/import_draft.json`：待确认导入草稿，确认或取消后删除。
- 文件写入通过 `AtomicFileStore` 执行，保留上一有效副本；主文件和副本均损坏时进入只读保护。
- 旧数据缺少 `seriesId` 时由 `CourseSeriesIds` 自动生成稳定匿名 ID，保证“应用到全部周”可用。
- 旧数据中的 `semesterStart` 无论落在周几，读取时都会归一化为当周周一；所有日期消费者必须继续通过 `TimeUtils` 的学期日期入口计算，避免升级后星期偏移。
- Android 系统云备份和设备迁移均禁用；换机必须使用应用内完整备份。

## 隐私与发布安全

- 完整备份包含课程原文，只用于本人恢复；脱敏副本不可恢复，并清除课程号、班号、院系、教师、教室、备注和真实系列关联。
- 完整备份与脱敏副本使用两个独立 Activity Result 回调，禁止改回共享布尔状态。
- `.gitignore` 必须继续排除密钥、本机 SDK 配置、APK/AAB、Excel、真实课表和备份。
- 发布前运行 `scripts/pre-release-audit.ps1`；脚本只检查仓库内部，不访问网络，并会校验版本号、SDK/Gradle 文档与实际构建配置是否同步。
- 不要把 `local.properties`、签名文件、真实课表、完整备份或本机日志提交到 Git。

## 构建与验证

```powershell
pwsh -File .\scripts\build.ps1
.\gradlew.bat :app:compileDebugAndroidTestKotlin
pwsh -File .\scripts\pre-release-audit.ps1 -AllowDirty
```

`scripts/build.ps1` 固定校验 JDK 17，并只为本次 Gradle 子进程使用该 JDK，不会修改系统环境；维护者可用 `-JavaHome` 为当前构建显式指定 JDK 17。

发布前还应在 API 34 模拟器执行 `:app:connectedDebugAndroidTest`，并至少手工检查：

1. 七天、五天、单日视图切换及重启常驻。
2. 新增、编辑、仅本周/全部周删除、撤销和冲突确认。
3. Excel/文本/手动列映射导入、完整/加密备份恢复、历史恢复、脱敏导出和 iCal 导出。
4. 全局/单课/下课提醒、通知快捷暂停、停课周完整选择与左右切周、课表提示条直达学期安排、周标签/日期停课/取消/补课的新增编辑及其在课表和小组件的同步显示、活动课表切换和重启恢复。
5. 日程列表、复制/归档、深浅色、增强对比度、减少动画、窄屏、大字体和 TalkBack。
6. 小组件默认 3×2、横向 4×2/5×2、今日/明日双栏、两栏独立滚动、长课程名/教室/教师完整展示及点击直达。
7. 今日小组件已结束课程过滤，当前时间红线顶层显示，自定义背景选择/更换/恢复默认、可读遮罩开关持久化以及深浅色可读性。

Release 默认启用 R8、资源收缩和中文语言资源裁剪。签名材料由发布者在仓库外保管，源码仓库和 CI 均不应读取。

GitHub Actions 在普通推送中运行 JVM 测试、Lint 与 Debug/Release 构建；API 34 UI 测试在拉取请求、手动触发和每周计划中运行，API 26/29 UI 测试在每周计划或手动完整矩阵中运行。

## 发布检查清单（每次发版必须逐项执行，防止文档与包脱节）

1. `app/build.gradle.kts`：versionCode 加一，versionName 更新。
2. `CHANGELOG.md`：文件顶部新增本版条目，保持版本倒序排列。
3. `README.md`：更新“当前版本”行；“主要能力”如受本版影响（视图、权限、导入格式、小组件等）同步改写。
4. `docs/DEVELOPMENT_STATUS.md`：新增“本轮”段落，更新“当前版本”与“历史发布”两行。
5. `docs/MAINTENANCE.md`：更新“当前版本”。
6. 全量验证：JVM 单元测试、API 34 UI 测试（本地模拟器）、Release Lint 全部通过；`assembleRelease` 后再运行签名交付脚本（脚本只签名不构建，切勿交付旧包）。
7. 经用户确认后：`git push origin main --tags`，通过 GitHub API 创建 Release 并上传 APK。
8. 发布后核对：Release 页 APK 字节数与本地交付成品一致（SHA-256 可交叉验证）。
