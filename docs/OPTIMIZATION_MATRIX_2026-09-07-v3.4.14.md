# v3.4.14 课表界面收敛与交付验证

日期：2026-09-07。基线：v3.4.13 / versionCode 133。目标：恢复 3.4.12 的透明课表观感，并统一紧凑布局、动画和课程编辑流程。

| 方向 | 实现 | 验证 |
| --- | --- | --- |
| 透明与渐变 | 课程方格使用透明磨砂填充；浅色平面透明，深色三段渐变并增强底部压暗；背景透明度恢复 3.4.12 取值 | `CourseColorTest`；Release Lint |
| 顶栏与分页 | 周视图紧凑标题区；定位按钮仅在非本周显示，水平展开/收起；Pager fling 与预加载参数优化 | `ScheduleOverviewBarTest`；编译通过 |
| 设置与文案 | 移除服务状态/提醒恢复卡及周视图精简卡片开关，清理冗余提示 | `RevisionVisualTest.settingsStatusBoardRemoved` |
| 月视图 | 日期与节日优化保留；显示最多两条课程名，超出省略并追加“共 N 节” | `MonthGridNavigationTest` |
| 课程编辑 | 添加与编辑共用表单；周次、开始/结束节次使用可点击选择器，字段布局统一 | `MainActivitySmokeTest` 新选择器适配 |
| 动画与响应 | 弹层退出沿进入方向返回；横向分页使用更高吸附刚度与更低衰减摩擦 | JVM/UI 编译回归 |

## 验证结果

- `:app:testDebugUnitTest --offline`：通过（161 项 JVM 用例）。
- `:app:lintRelease :app:assembleRelease --offline`：通过，0 个错误。
- Android UI 回归：60/61 通过；剩余 1 项为连续持久化数据下旧回归脚本等待超时，已改为动态查找节次选择器并延长异步等待，不影响生产逻辑。
- APK 已使用兼容升级密钥签名，SHA-256：`6D2720007E64E3B24159470318AC17B329C4B9B422156355C6287ADC32DD0CE2`。

真机帧率、厂商后台限制和系统字体缩放仍需在目标设备上复核；本记录只陈述本地构建与模拟器结果。
