# v3.2.0 十项优化逐项验收矩阵

| # | 优化项 | 落地内容 | 证据 |
|---:|---|---|---|
| 1 | Excel 兼容性 | 可见工作表选择、隐藏表回退、合并单元格、公式缓存、1900/1904 日期、`.xlsx/.xls` 分流；CI 提供 API 26/29/34 矩阵入口 | `ExcelParserTest`、`RealDeviceImportTest`、`.github/workflows/android-ci.yml` |
| 2 | 导入草稿竞态 | 版本令牌 + Mutex 串行写入，清空操作可使旧保存失效 | `ImportDraftWriteGateTest` |
| 3 | 导入错误提示 | 文件类型、权限、大小、压缩包、XML、截断、表头/必要列/行级格式错误分层提示 | `ExcelParserTest`、`RealSchoolFileDiagnosticTest` |
| 4 | `.xls` 体积取舍 | 保留旧版 `.xls`（POI core）兼容；`.xlsx` 不引入 POI OOXML；Release 包与原 v3.0.0 对比并记录体积 | `app/build.gradle.kts`、交付说明“体积说明” |
| 5 | 解析内存峰值 | 目标工作表改用 SAX 流式解析，仅 workbook/关系/共享字符串/样式保留 DOM；保留 20 MB 输入和 64 MB 解压护栏，并新增行/单行单元格上限 | `MinimalXlsxReader.kt`、解析器回归测试 |
| 6 | 历史快照写入 | 相同内容跳过写入；连续编辑 1 秒窗口合并快照；恢复操作强制留下回滚点；超过 10 份自动清理 | `CourseHistoryStoreTest`、`CourseRepositoryHistoryTest` |
| 7 | 大文件结构拆分 | 导入/备份/iCal 协调已独立为 `ImportExportCoordinator`，弹窗独立为 `CourseDialogs`，导航独立为 `AppNavigation`，更新检查条目独立为 `SettingsUpdateItem` | 对应源码文件 |
| 8 | 更新检查网络读取 | 校验 2xx、限制 256 KB、异常转友好提示；仅用户手动触发 | `UpdateChecker.kt`、设置页测试/文档 |
| 9 | 无障碍与大字体 | 课程、按钮、冲突项提供语义描述；补充 150% 字体的日程与导入确认页回归 | `AgendaScreenTest`、`ImportConfirmScreenTest`、`ScheduleOverviewBarTest` |
| 10 | 小组件跨日/时区 | 按显式 `LocalDate` 计算，课程结束边界精确过滤；补充跨午夜日期和切换 JVM 时区回归 | `WidgetPresentationTest` |

## 自动化矩阵

- 本地 API 34：`connectedDebugAndroidTest`。
- CI API 26、29：`ui-legacy` 手动/每周矩阵。
- CI API 34：`ui-api34` PR/手动/每周执行。
- Release：R8、资源收缩、APK 签名与体积审计。
