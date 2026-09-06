# Macrobenchmark 性能测量

v3.4.13 已通过参数接入并独立编译。正常 Release / CI 不需要下载基准依赖。

## 构建与运行

使用项目要求的 JDK 17、Android SDK 35，并连接允许 USB 调试的真机：

```powershell
.\gradlew.bat -PenableBenchmarks=true :app:assembleBenchmark :benchmarks:macrobenchmark:assembleBenchmark
.\gradlew.bat -PenableBenchmarks=true :benchmarks:macrobenchmark:connectedBenchmarkAndroidTest
```

首次构建需能访问 Google Maven。无需修改 settings 或额外声明 Benchmark 插件；模块使用 com.android.test 与 AndroidX Macrobenchmark 库。

应用包名为 `com.jaysay.coursetable.benchmark`，与正式应用独立。只有 benchmark 变体包含虚构课程准备入口，入口会覆盖该测试包的数据并关闭提醒；正式 Release 不包含它。测试应用开启 R8、关闭 debuggable，允许 shell profiling；测试驱动 APK 使用调试构建。

## 场景

| 测试方法 | 数据与操作 | 指标 |
| --- | --- | --- |
| coldStart | 42 条虚构课程，冷启动 | timeToInitialDisplayMs |
| largeTableFirstDisplay | 2000 条虚构课程，冷启动 | timeToInitialDisplayMs |
| weekPaging | 42 条课程，周视图往返滑动 | frameDurationCpuMs / frameOverrunMs |
| dayPaging | 42 条课程，日视图往返滑动 | frameDurationCpuMs / frameOverrunMs |
| detailRoundtrip | 点击课程，等待详情出现，再返回；每轮 3 次 | frameDurationCpuMs / frameOverrunMs |

每个场景 3 次预热、5 次测量迭代，CompilationMode.Partial、BaselineProfileMode.Disable。数据准备在 setupBlock 中完成，不计入被测操作。详情测试先确认“编辑”按钮，再执行返回，避免把主页空转误当详情测量。

输出见 `macrobenchmark/build/outputs/connected_android_test_additional_output/` 和 `macrobenchmark/build/reports/androidTests/connected/`。

## 记录要求

保留设备型号、系统、刷新率、构建版本及代码状态、数据量、编译模式、全部迭代 JSON 与 trace。性能比较必须在同一真机、相同设置下运行前后两个版本。此次尚未连接真机，不以模拟器结果推断手机帧率、功耗或提升比例。

模拟器仅可用于验证场景能够运行；需显式传入 `-Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.suppressErrors=EMULATOR`，这不会使其成为有效的真实性能对比。

配置参考：[Android 官方 Macrobenchmark 指南](https://developer.android.com/topic/performance/benchmarking/macrobenchmark-overview)。

## 本轮验证

2026-09-06：独立模块编译通过；API 34 模拟器 coldStart 与 detailRoundtrip 两项实跑通过（各 3 次预热、5 次测量），0 失败、0 跳过。显式忽略 EMULATOR 限制，仅用于连通性检查；日/周翻页和 2000 条课程场景已实现但本轮未实跑，真实前后性能对比仍待真机。
