# JaySay 课程表维护说明

## 项目边界

- Android 应用模块：`app/`
- 包名与 applicationId：`com.jaysay.coursetable`
- 最低/目标系统：Android 8.0（API 26）/ Android 14（API 34）
- 当前候选版本：2.8.0，versionCode 81
- 应用不声明 `INTERNET` 权限，不包含联网客户端；课程、备份、提醒和日历均在本机处理。

## 主要结构

- `MainViewModel.kt`：界面状态、串行写入、多课表和暂存导入。
- `data/repository/`：课表 JSON、规范化与原子持久化。
- `data/preferences/`：主题、活动课表和提醒偏好。
- `data/backup/`：完整备份、脱敏副本与严格恢复校验。
- `data/parser/`：Excel 与粘贴文本导入。
- `data/reminder/`：提醒计算、AlarmManager 调度、通知和系统事件恢复。
- `data/ical/`：标准 `.ics` 日历导出。
- `widget/`：桌面小组件与状态边界刷新。
- `ui/`：课表、详情、编辑、设置、导入确认和公共组件。

## 本地数据与兼容性

- `files/tables.json`：schemaVersion 3，包含多课表、课程、节次、学期、视图模式和停课周。
- `files/preferences.json`：schemaVersion 2，包含主题、活动课表和提醒设置。
- 文件写入通过 `AtomicFileStore` 执行，保留上一有效副本；主文件和副本均损坏时进入只读保护。
- 旧数据缺少 `seriesId` 时由 `CourseSeriesIds` 自动生成稳定匿名 ID，保证“应用到全部周”可用。
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
3. Excel/文本导入、完整备份恢复、脱敏导出和 iCal 导出。
4. 提醒开关、通知权限拒绝后的安全降级、活动课表切换和重启恢复。
5. 深浅色、窄屏、最后节次安全边距、TalkBack 空白节次入口和桌面小组件。

Release 默认启用 R8、资源收缩和中文语言资源裁剪。签名材料由发布者在仓库外保管，源码仓库和 CI 均不应读取。
