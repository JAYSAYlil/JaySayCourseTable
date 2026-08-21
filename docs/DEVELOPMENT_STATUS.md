# v2.13.0 开发交接状态

更新时间：2026-08-21

## 版本与安全基线

- 当前开发分支：upgrade/v2.12.4（本轮继续在此分支开发，未新建分支）
- 当前候选版：2.13.0 / versionCode 92
- 本轮没有访问 GitHub，也没有修改代理、端口、DNS 或其他系统网络配置。
- 改动前已完整备份 v2.12.6：交付记录/v2.12.6-优化前备份/（Git 历史 bundle、源码 zip、正式 APK、维护资料与真实课表，含恢复说明）。

## v2.13.0 调整内容

- 构建栈升级：Gradle 9.1.0、AGP 8.13.0、Kotlin 2.2.0（内置 Compose 编译器插件，移除 composeOptions）、Compose BOM 2025.06.01、core-ktx 1.16.0、lifecycle 2.9.0、activity-compose 1.10.1、coroutines 1.10.1、Apache POI 5.3.0。
- compileSdk/targetSdk 升至 35：启用 enableEdgeToEdge 官方入口，移除已弃用的窗口状态栏/导航栏颜色 API；页面栏位视觉与 v2.12.6 保持一致。
- MainActivity 内联弹窗（删除确认、冲突确认、加密备份导出/导入密码、备份恢复确认、粘贴导入）拆分到 ui/components/CourseDialogs.kt，MainActivity 由 905 行缩减至约 700 行；行为与文案不变。
- 当前分钟刷新订阅从课表屏幕顶层下移到今日摘要、课程卡片和时间线覆盖层内部，每 30 秒不再触发整屏与网格重组。
- UI 层全部用户可见文案（344 条）抽取到 strings.xml；数据层错误消息保持纯 Kotlin 数据不变。
- AtomicFileStore 恢复写入前自动清理残留的备份临时文件，修复一次故障后后续恢复持续失败的问题。
- 新增 2 万课程保存/载入性能压测（实测 save≈1.0s、reload≈1.2s）与 2 项恢复故障注入回归测试。

## 数据与兼容性

- tables.json 仍为 schemaVersion 4，preferences.json 仍为 schemaVersion 4；数据格式未变化。
- applicationId 与签名证书保持不变，可从 v2.12.x 直接覆盖升级。
- 应用仍不声明 INTERNET 或媒体库广泛读取权限，系统云备份与设备迁移数据提取继续关闭。
- 正式 Release 包保持不可调试。
- 注意：targetSdk 35 起系统强制预测性返回，Android 15+ 设备上系统返回窗口动画由平台接管，应用内 Compose 转场不受影响；Android 14 及以下设备行为与 v2.12.6 完全一致。

## 验证结果

- JVM 回归测试 114 项全部通过，0 失败、0 错误、0 跳过。
- API 34 UI 测试 16 项全部通过，0 失败、0 错误、0 跳过。
- Release/R8 与资源压缩构建成功。
- Release Lint 0 错误。
- 2 万课程上限压测：save≈1.0s、reload≈1.2s，全量写入方案维持不变。
- 正式 APK 完成 v2/v3 签名校验；签名证书 SHA-256 为 23410387a26c9ca5b9a1552ba06be642d905fd07d2791bc3bf4db9461ba7d132。

## 交付边界

- 最终签名 APK、SHA-256、安装说明、审计结果、源码快照和本文件副本放在项目外层的 v2.13.0 交付目录。
- 签名材料不复制进仓库、不进入源码快照或交付记录。
- 不自动推送 GitHub；仅在用户之后明确要求时执行公开发布流程。
