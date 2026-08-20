# JaySay 课程表维护说明

## 项目边界

- Android 应用模块：`app/`
- 包名与 applicationId：`com.jaysay.coursetable`
- 最低/目标系统：Android 8.0（API 26）/ Android 14（API 34）
- 当前候选版本：2.12.0，versionCode 85
- 应用不声明 `INTERNET` 权限，不包含联网客户端；课程、备份、提醒和日历均在本机处理。

## 主要结构

- `MainViewModel.kt`：界面状态、串行写入、多课表和暂存导入。
- `data/repository/`：课表 JSON、规范化与原子持久化。
- `data/preferences/`：主题、活动课表、提醒偏好与私有自定义背景存储。
- `data/backup/`：完整备份、脱敏副本与严格恢复校验。
- `data/history/`：最近 10 次本机课表快照、差异与恢复。
- `data/diagnostics/`：固定白名单字段的脱敏诊断报告。
- `data/parser/`：Excel 与粘贴文本导入。
- `data/reminder/`：提醒计算、AlarmManager 调度、通知和系统事件恢复。
- `data/ical/`：标准 `.ics` 日历导出。
- `widget/`：3/4/5 列响应式桌面小组件、今日/明日集合数据与状态边界刷新。
- `ui/`：课表、详情、编辑、设置、导入确认和公共组件。

## 本地数据与兼容性

- `files/tables.json`：schemaVersion 4，包含多课表、课程、节次、学期、视图模式、日期例外、周标签和归档状态。
- `files/preferences.json`：schemaVersion 4，包含主题、活动课表、提醒、增强对比度、减少动画和背景缓存版本标记；旧版密度与小组件隐私字段会被忽略。
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
- 发布前运行 `scripts/pre-release-audit.ps1`；脚本只检查仓库内部，不访问网络。
- 不要把 `local.properties`、签名文件、真实课表、完整备份或本机日志提交到 Git。

## 构建与验证

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin
.\gradlew.bat :app:lintRelease :app:assembleDebug :app:assembleRelease
pwsh -File .\scripts\pre-release-audit.ps1 -AllowDirty
```

发布前还应在 API 34 模拟器执行 `:app:connectedDebugAndroidTest`，并至少手工检查：

1. 七天、五天、单日视图切换及重启常驻。
2. 新增、编辑、仅本周/全部周删除、撤销和冲突确认。
3. Excel/文本/手动列映射导入、完整/加密备份恢复、历史恢复、脱敏导出和 iCal 导出。
4. 全局/单课/下课提醒、通知快捷暂停、日期停课/补课、活动课表切换和重启恢复。
5. 日程列表、复制/归档、深浅色、增强对比度、减少动画、窄屏、大字体和 TalkBack。
6. 小组件默认 3×2、横向 4×2/5×2、今日/明日双栏、两栏独立滚动、长课程名/教室/教师完整展示及点击直达。
7. 今日小组件已结束课程过滤，当前时间红线顶层显示，自定义背景选择/更换/恢复默认以及深浅色可读性。

Release 默认启用 R8、资源收缩和中文语言资源裁剪。签名材料由发布者在仓库外保管，源码仓库和 CI 均不应读取。
