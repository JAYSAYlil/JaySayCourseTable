# AGENTS.md — JaySay 课程表

面向在本仓库工作的 AI 代理与自动化脚本。这里只写**硬规则、边界和速查**；详细机制、数据格式和发布流程见 `docs/`（指针见文末）。

## 项目一句话

本地优先的 Android 课程表应用（Kotlin + Compose，单 Activity）。数据全部存本机 JSON，不联网，仅在用户手动“检查更新”时访问 GitHub Releases。

## 红线（违反即事故）

1. **绝不提交**：`local.properties`、`*.keystore`/`*.jks`、真实课表 `.xls/.xlsx`、完整备份 JSON、`*.apk/*.aab`。`.gitignore` 必须继续排除它们。唯一允许的表格文件是内置模板 `app/src/main/assets/import_template.xlsx`。
2. **不得更换升级身份**：包名 `com.jaysay.coursetable` 与签名证书固定不变（证书指纹由发布者在本机维护资料中核对，不写入本仓库），否则老用户无法覆盖升级。
3. **数据格式只增不减**：`tables.json` / `preferences.json` 保持 `schemaVersion 4`；新增字段必须给默认值，旧字段不得删除，必须能直接读取旧版本数据。
4. **不得弱化这些能力**：`.xls/.xlsx` 导入、旧数据迁移（含 `seriesId`、`semesterStart` 归一化）、`AtomicFileStore` 原子写入与 `.bak` 恢复、主副文件均损坏时的只读保护。
5. **不要重新引入 POI OOXML**：`.xlsx` 由 `data/parser/MinimalXlsxReader.kt` 自行解析，Apache POI 只为旧版 `.xls` 保留。这是 Release 包维持约 2.3 MB 的前提。
6. **文案只在资源里**：用户可见文字写在 `app/src/main/res/values/strings.xml`（需要保留首尾空格时把值用引号包裹）；数据层错误保持纯 Kotlin 数据，不依赖 Context。

## 环境与命令

需要 JDK 17；Android SDK 路径来自 `local.properties`。

```powershell
pwsh -File .\scripts\build.ps1          # JDK17 预检 + JVM 测试 + Lint + Debug/Release 构建
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:lintRelease :app:assembleRelease
.\gradlew.bat :app:connectedDebugAndroidTest   # 需要本地 API 34 模拟器
pwsh -File .\scripts\pre-release-audit.ps1 -AllowDirty
```

- Windows 中文路径下**不要**用 `org.gradle.jvmargs` 强制 `-Dfile.encoding`（会让测试类路径被转码）；只在 Java 编译任务声明 UTF-8。
- 构建日志、截图等临时产物不要留在仓库根目录。
- 基准测试按需接入：`-PenableBenchmarks=true`，见 `benchmarks/README.md`。

## 代码地图

| 位置 | 职责 |
|---|---|
| `MainActivity.kt` | 单 Activity、页面切换、Activity Result（导入/导出/备份/背景）、弹窗编排 |
| `MainViewModel.kt` | 界面状态、串行写入、多课表、暂存导入 |
| `data/repository/` | 课表 JSON 读写、规范化、原子持久化 |
| `data/preferences/` | 主题、活动课表、提醒偏好、自定义背景私有存储 |
| `data/parser/` | Excel（自研 xlsx + 旧版 xls）与文本/列映射导入 |
| `data/model/` | 课程、校历状态、日程列表、冲突分析（纯 Kotlin，可 JVM 测试） |
| `data/reminder/` | 提醒计算、AlarmManager 调度、通知、系统事件恢复、权限与自启动引导 |
| `data/history/`、`data/backup/`、`data/diagnostics/` | 历史快照与差异、完整/加密/脱敏备份、脱敏诊断报告 |
| `ui/screen/` | 课表（周/日/月视图）、详情、编辑、设置、导入确认、历史、学期安排 |
| `ui/components/` | 顶栏、今日摘要、弹窗、Hero 转场、自定义背景 |
| `widget/` | 桌面小组件（3/4/5 列；Android 12+ 用 `RemoteCollectionItems`，8–11 用服务） |
| `util/` | `TimeUtils`（学期日期唯一入口）、`TodayState`、农历、更新检查 |

## 改动约定（不变量）

- **学期/日期计算只走 `util/TimeUtils`** 的学期入口；旧 `semesterStart` 无论落在周几，读侧一律归一化为当周周一。
- **校历展示语义只走 `data/model/AcademicCalendarStatus.kt`**（停课周、周标签、取消、补课）；课表与小组件必须共用同一解析结果，不要另写判断。
- **课程配色按课程唯一键派生**，网格、月视图、详情页必须一致。
- **编辑课程周次**时：保持 v3.4.24 的三档 CourseEditScope（本次／本周及以后／全部周），不得因周次变化擅自升级保存范围；**删除必须与保存共用同一范围**（删除本周／删除本周起／删除全部），不得固定成只删当前周。调课保留目标周原有课程；空选不得回退为全学期（新增课程默认全学期）。
- 视图模式写入走 `data/preferences/ViewModeWriteGate.kt`，切换要即时生效、后台落盘。
- “今天”的判断走 `util/TodayState.kt`（可注入时钟 + 回前台/系统广播校准），不要直接散用 `LocalDate.now()` 驱动 UI 状态。
- 详情转场用 `ui/components/HeroTransition.kt` 的手动 overlay 方案，不要改回 `SharedTransitionLayout`。

## 发版

流程与检查清单见 `docs/MAINTENANCE.md`。发版必须同步这几处：

`app/build.gradle.kts` 版本号 → `CHANGELOG.md` → `README.md` 当前版本行 → `docs/DEVELOPMENT_STATUS.md` → `docs/MAINTENANCE.md`，随后全量测试、Lint、Release 构建与签名校验。

## 文档指针

| 想知道什么 | 看哪里 |
|---|---|
| 架构、数据格式、兼容性、发布清单 | `docs/MAINTENANCE.md` |
| 当前版本状态、历史轮次与验证结论 | `docs/DEVELOPMENT_STATUS.md` |
| 逐版本变更说明 | `CHANGELOG.md` |
| 单轮优化的证据与取舍 | `docs/OPTIMIZATION_MATRIX*.md`、`docs/BUGFIX_*.md` |
| 面向使用者的能力与隐私说明 | `README.md` |
