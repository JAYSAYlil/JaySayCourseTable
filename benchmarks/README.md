# Macrobenchmark 性能测量模块（脚手架，待接入）

状态：**脚手架已就绪，尚未接入构建**。当前环境无法访问 Google Maven（2026-09-05 验证），
无法下载 `androidx.benchmark` 依赖并编译验证，因此本模块刻意**不加入**
`settings.gradle.kts`，避免破坏可构建性。网络可用后按下方步骤接入。

## 接入步骤（网络可用 + 真机）

1. 根目录 `settings.gradle.kts` 追加：

   ```kotlin
   include(":benchmarks:macrobenchmark")
   ```

2. 根目录 `build.gradle.kts`（或 Version Catalog）声明插件
   `androidx.benchmark.macro.junit4`（版本 ≥ 1.3.x），本模块 `build.gradle.kts`
   已按该插件编写。

3. 连接真机（开发者选项 + USB 调试），然后：

   ```powershell
   .\gradlew :app:assembleBenchmark
   .\gradlew :benchmarks:macrobenchmark:connectedCheck
   ```

   `app` 已新增 `benchmark` 变体（R8 开启、debuggable=false，同 Release 配置），
   不改动正式签名设置；CI 不会组装该变体。

4. 结果输出在 `benchmarks/macrobenchmark/build/outputs/connected_android_test_additional_output/`。

## 本模块测量的场景

| 场景 | 指标 | 对应要求 |
| --- | --- | --- |
| `StartupBenchmark` cold/demand | timeToInitialDisplayMs | 冷启动 |
| `PagingBenchmark` 周视图连续翻页 | frameDurationCpuMs / frameOverrunMs | 连续日/周翻页 |
| `DetailRoundtripBenchmark` 详情往返 | frameDurationCpuMs | 详情打开/返回中断续接 |

## 记录规范（每次测量必须完整填写，禁止只记数字）

- 设备型号 / 系统版本 / 刷新率（含是否开启强制 GPU 渲染）
- 构建类型（benchmark）、代码提交哈希、数据量（课程条数、课表数）
- 采样方法（iterations、startup mode、编译状态 `CompilationMode.Partial`）
- 优化前后各测一轮，对比时给出中位数与置信区间

## 诚实声明

截至 2026-09-05：**未获得任何真实测量数值**（无可用真机 + 无网络下载依赖）。
本文档不承诺任何提升百分比；Baseline Profile 是否值得引入，
以首次真机测量结果判断（若 cold start P50 显著高于 500ms 或帧超时率高，
优先为 `MainActivity`/网格热路径引入 Baseline Profile 后复测）。
