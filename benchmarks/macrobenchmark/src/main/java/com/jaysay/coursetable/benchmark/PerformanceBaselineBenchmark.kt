package com.jaysay.coursetable.benchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 性能基线场景（脚手架）：冷启动 / 连续翻页 / 详情往返。
 * 运行前提见 benchmarks/README.md —— 需要真机与网络下载依赖，
 * 在获得真实测量数据前不声称任何性能结论。
 */
@RunWith(AndroidJUnit4::class)
class PerformanceBaselineBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun coldStart() {
        benchmarkRule.measureRepeated(
            packageName = PACKAGE,
            metrics = listOf(StartupTimingMetric()),
            iterations = 5,
            startupMode = StartupMode.COLD,
            compilationMode = CompilationMode.Partial()
        ) {
            pressHome()
            startActivityAndWait()
        }
    }

    @Test
    fun weekPaging() {
        benchmarkRule.measureRepeated(
            packageName = PACKAGE,
            metrics = listOf(FrameTimingMetric()),
            iterations = 5,
            compilationMode = CompilationMode.Partial()
        ) {
            pressHome()
            startActivityAndWait()
            // 连续左右翻页 6 次：单次手势一页的跟手场景。
            repeat(3) { step ->
                device.swipe(
                    device.displayWidth * 4 / 5, device.displayHeight / 2,
                    device.displayWidth / 5, device.displayHeight / 2,
                    20
                )
                device.waitForIdle()
                device.swipe(
                    device.displayWidth / 5, device.displayHeight / 2,
                    device.displayWidth * 4 / 5, device.displayHeight / 2,
                    20
                )
                device.waitForIdle()
            }
        }
    }

    @Test
    fun detailRoundtrip() {
        benchmarkRule.measureRepeated(
            packageName = PACKAGE,
            metrics = listOf(FrameTimingMetric()),
            iterations = 5,
            compilationMode = CompilationMode.Partial()
        ) {
            pressHome()
            startActivityAndWait()
            // 详情打开→返回 3 次：验证 Hero 飞行中断续接不引入掉帧长尾。
            repeat(3) {
                device.waitForIdle()
                device.pressBack()
                device.waitForIdle()
            }
        }
    }

    private companion object {
        const val PACKAGE = "com.jaysay.coursetable"
    }
}
