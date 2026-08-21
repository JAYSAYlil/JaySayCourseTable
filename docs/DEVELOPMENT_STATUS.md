# v2.14.0 开发交接状态

更新时间：2026-08-21

## 版本与安全基线

- 当前开发分支：upgrade/v2.12.4（继续沿用，未新建分支）
- 当前候选版：2.14.0 / versionCode 93
- 本轮没有访问 GitHub，也没有修改代理、端口、DNS 或其他系统网络配置。
- 改动前已完整备份 v2.13.0：交付记录/v2.13.0-优化前备份/（Git 历史 bundle、源码 zip、正式 APK、维护资料与真实课表，含恢复说明）。

## v2.14.0 调整内容

### 上课提醒修复（用户反馈"到时间不通知"）
- 根因一（精确性）：Android 14+ 对 SCHEDULE_EXACT_ALARM 默认拒绝，精确闹钟被降级为近似闹钟；新增 USE_EXACT_ALARM 权限（Android 13+ 日历类应用自动授予、不可撤销），canScheduleExactAlarms() 在 API 34 实测为 true。
- 根因二（可见性）：Android 13+ 用户拒绝通知权限后应用重要性为 NONE，提醒被静默丢弃且无任何提示；设置页提醒区新增三项状态检查（通知权限 / 精确闹钟 / 通知渠道）与对应一键修复引导，正常时显示确认提示，从系统设置返回后自动刷新。
- 新增 data/reminder/ReminderPermissions.kt 统一诊断权限与渠道状态。

### 体积与冗余
- R8 排除 POI 中课表用不到的 PowerPoint（sl/xslf/hslf）、Word（hwpf/xwpf）、Visio/邮件（hdgf/hmef）与文档数字签名（dsig）模块类；packaging 排除幻灯片形状定义等非 Excel 资源。
- APK：6,984,274 → 约 6,484,000 字节（-7.2%）。
- 语言资源裁剪迁移到 androidResources.localeFilters（resourceConfigurations 弃用）。

### 测试
- 新增 ReminderEndToEndTest（精确闹钟可用性、闹钟广播→通知全链路）与 SettingsReminderSectionTest（提醒区与权限警告渲染）共 5 项 instrumentation 测试。
- 模拟器真实时间实测：注册的精确闹钟在课前 5 分钟准时触发，“即将上课”通知正常弹出（含暂停操作按钮）；Excel 导入在 release 混淆包上解析真实 40 条课表成功。

## 数据与兼容性

- tables.json 与 preferences.json 仍为 schemaVersion 4；数据格式未变化，applicationId 与签名证书不变，可从 v2.13.x 直接覆盖升级。
- 新增权限：USE_EXACT_ALARM（系统自动授予，无需用户操作）；POST_NOTIFICATIONS 仍为运行时权限。
- 应用仍不声明 INTERNET；系统云备份与设备迁移继续关闭；Release 不可调试。

## 验证结果

- JVM 回归测试 114 项全部通过。
- API 34 UI 测试 21 项全部通过。
- Release/R8 与资源压缩构建成功；Lint 0 错误、30 条非阻断警告。
- 真实闹钟端到端：AlarmManager 精确触发 → Receiver 校验 → 通知显示全链路通过。
- Release 包 Excel 导入真实课表（40 条）通过。

## 交付边界

- 最终签名 APK、SHA-256、安装说明、审计结果、源码快照和本文件副本放在项目外层的 v2.14.0 交付目录。
- 签名材料不复制进仓库、不进入源码快照或交付记录。
- 不自动推送 GitHub；仅在用户之后明确要求时执行公开发布流程。
