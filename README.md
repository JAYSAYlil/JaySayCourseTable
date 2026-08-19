# JaySay 课程表

一款本地优先的 Android 课程表应用，支持 Excel 导入、多课表管理、单双周显示、课程编辑、完整备份恢复和脱敏副本导出。

## 主要能力

- 支持 `.xls` 与 `.xlsx`，可识别常见表头别名和前置说明行。
- 重复导入时合并周次，并保留用户设置的颜色与备注。
- 课表、节次时间、学期设置、当前课表和每张课表的视图模式均持久化到本机。
- 使用原子文件替换、上一版本备份和串行写入降低数据损坏风险。
- 主文件与回退副本同时损坏时进入只读保护，只有严格校验的完整备份恢复成功后才解除。
- 支持完整 JSON 备份、严格校验后的恢复以及不可恢复的脱敏副本。
- 提供七天、五天和单日三种响应式视图，可点按空白节次快速添加课程。
- 顶部提供不占用网格空间的今日/下一节课摘要，并可按课程名、教师或教室本地搜索。
- 支持方向明确的周切换与详情动画，删除课程前确认，并可在提示条中撤销。
- 导入前按新增、合并、重复和冲突分类，可逐条选择；手工新增或编辑发生时间冲突时会二次确认。
- 不声明网络权限，所有课程数据均在设备本地处理。

## 环境要求

- Android 8.0（API 26）或更高版本
- JDK 17
- Android SDK 34
- Gradle 8.7（项目已包含 Wrapper）

## 构建与测试

复制 `local.properties.example` 为 `local.properties`，填入本机 Android SDK 路径，然后运行：

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleRelease :app:lintRelease
pwsh -File .\scripts\pre-release-audit.ps1 -AllowDirty
```

Release 构建默认启用 R8、资源收缩和语言资源裁剪。仓库不会提供签名材料，发布者应使用自己安全保管的密钥签名。

Windows 中文路径下不要通过 `org.gradle.jvmargs` 强制覆盖整个 Gradle 进程的 `file.encoding`；项目仅在 Java 编译任务中指定 UTF-8，Kotlin 源码由编译器按 UTF-8 读取，从而避免测试类路径被错误转码。

## Excel 必要列

课程号、课程名、上课星期、开始节次、结束节次和上课周次。解析器也支持“课程代码”“课程名称”“星期”“周次”“教师”“教室”等常见别名。

## 隐私与安全

- 仓库只包含源码和完全虚构的测试数据。
- `.gitignore` 排除签名密钥、本机 SDK 配置、构建产物、个人课表、备份和 Excel 文件。
- Android 系统云备份和设备迁移均排除应用数据；换机请使用应用内“导出完整备份”。
- 请勿在 Issue、日志或提交中上传真实课表、完整备份、签名密钥或本机路径。

当前开发版本：`2.7.0`（versionCode 80）；公开稳定版仍为 `2.6.1`。

## 自动化测试策略

- 每次推送和拉取请求运行 JVM 测试、Lint、Debug 构建及 API 34 UI 测试。
- API 26/29 UI 测试每周运行，也可在 GitHub Actions 手动勾选完整旧版矩阵，兼顾兼容性与运行成本。
- CI 不读取或引用签名密钥，只生成无签名/调试构建产物。

## 下载

正式签名 APK、版本说明和 SHA-256 校验值请从 [GitHub Releases](https://github.com/JAYSAYlil/JaySayCourseTable/releases) 获取。

版本变更记录见 [CHANGELOG.md](CHANGELOG.md)。
