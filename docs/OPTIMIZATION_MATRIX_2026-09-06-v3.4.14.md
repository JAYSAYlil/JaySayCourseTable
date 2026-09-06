# v3.4.14 优化与交付验证（界面专项）

日期：2026-09-07。起点：v3.4.13，Git `45cdf3b`，开始时工作区干净。当前版本：3.4.14 / 134。触发：用户真机实测反馈五项界面问题。

## 实现与边界

| 反馈 | 实现 | 验证方式 |
|---|---|---|
| 功能键布局错位 | DayChipRow 重做：选中态内缩胶囊（percent-50 圆角）、今日圆点锚定文字下方，基线不再被顶高；顶栏功能键统一 44dp、行尾 6dp 对齐 16dp 视觉边距；窄屏摘要行定高 44dp 居中，回到今天按钮与上方按键同列 | ScheduleOverviewBarTest 三用例保持绿；DAY/WEEK 浅深色截图人工核对 |
| 渐变强化 | courseCardFillStops 浅/深统一三段式：顶部增密/高光、0.42 中段基色、底部压暗（浅 0.10 / 深 0.26）；渲染层统一 Brush.verticalGradient | CourseColorTest 新契约 lightCardUsesThreeStopFrostedGradient；对比度矩阵全绿 |
| 毛玻璃透明 | 无自定义背景浅色卡 0.82→0.76、深色卡 0.94→0.90；自定义背景浅色卡保持 0.74（脚本验证 <0.74 时墨色锚点对纯黑壁纸无法达 4.5:1）；CustomBackgroundImage 整体 blur(14dp)，API 31+ 生效、旧系统回退 | Python 复刻 luminance 合成扫过 24 预设色 × 透明度求约束边界；对比度回归全绿 |
| 设置页排序布局 | 分区重排：通用→提醒→学期→备份与恢复→导入与导出→数据诊断→数据与版本；卡片间距 12→16dp；开关行 15sp/12dp 与操作行规格统一 | 全部设置 UI 测试绿；settings 截图人工核对 |
| 节次时间可折叠 | 学期设置内折叠条目：头部显示"共 N 节 · 首末时间"摘要 + 展开箭头；AnimatedVisibility 展开/收起；搜索命中自动展开保证可达 | 新增 periodTimesCollapseByDefaultAndExpandFromHeader 契约测试 |
| 卡片风格统一 | ServiceStatusCard 重构为分组卡样式（panel 形状 + 0.75dp 描边 + 0.5dp 分隔线 + 图标/标题/行尾操作节奏）；清理 3 个重构后无引用字符串 | settings 截图核对；Lint 0 错误、19 警告与基线持平 |

## 约束与不变量

- 数据格式仍为 schemaVersion 4；权限集合与 v3.4.13 完全一致。
- 文字对比度体系（标题 5.5:1 / 次级 4.5:1，增强对比度 7/5.5）在新渐变下全部成立；透明度下限由"最不利合成底色"决定，未以弱化断言换取通过。
- 自定义背景模糊只在 Android 12+ 生效：minSdk 26 的旧设备自动退化为原图，卡片自身透明度不变，无崩溃路径。

## 验证记录

- `:app:testDebugUnitTest --offline`：161 项 0 失败。
- `:app:connectedDebugAndroidTest --offline`（testavd API 34, 320dp）：61 项 0 失败；首跑 1 次 DayViewDateNavigationTest 收尾 DESTROYED 等待超时，属模拟器生命周期抖动，单跑复通过后全量重跑绿，未放宽断言。
- `:app:lintRelease :app:assembleRelease --offline`：0 错误、19 警告、1 提示；Release 3.4.14/134 构建通过。
- 签名交付：v2/v3 通过、证书 SHA-256 与兼容证书一致、2,324,386 字节。
- API 34 模拟器：卸载后装 v3.4.13 → 覆盖升级 v3.4.14 → 启动正常。
- 六张虚构数据截图存交付记录截图目录。

## 交付范围

本轮提供本地签名 APK、SHA-256、验证日志、截图与变更记录；未推送远程或创建 GitHub Release。真实壁纸下的毛玻璃观感与高刷转场手感待真机确认。
