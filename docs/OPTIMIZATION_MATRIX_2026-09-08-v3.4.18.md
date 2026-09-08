# v3.4.18 小窗标题栏避让

用户反馈：荣耀 Magic6 Pro 小窗系统控件覆盖课表标题与操作按钮。

## 原因与修改

课表根内容使用 statusBarsPadding 和 navigationBarsPadding，遗漏自由窗口的 captionBar。改为 safeDrawingPadding，统一处理系统上报的标题栏、状态栏、导航栏与刘海区域。背景图保留在避让区域外，继续铺满窗口。

## 回归证据

- WindowCaptionInsetsTest 直接对实际 MainActivity 的 Compose owner 分发平台 WindowInsets，依次模拟标题栏 0、96、144、0 像素。
- 旧实现失败：caption=96，actionTop=12.0，按钮落在系统标题栏内。
- 修复后通过，标题栏变化可动态响应，回到全屏后无残留顶部占位。
- 全量 JVM 测试 161 项通过；API 34 Android 测试 62/62 通过；Release Lint 与 assembleRelease 通过。
- 签名 v2/v3 验证通过，模拟器覆盖安装成功，系统报告 3.4.18 / 138。

## 成品

- 文件：JaySayCourseTable-v3.4.18-release.apk
- 大小：2336674 字节
- SHA-256：DADFEAACB68FDF220CB10D8374C709B9EC4004944E9FD107268530C858A0BC71

自动化验证覆盖系统标准窗口边距处理。交付后用户反馈实测无问题并授权公开发布；未声称穷尽全部厂商与系统版本。
