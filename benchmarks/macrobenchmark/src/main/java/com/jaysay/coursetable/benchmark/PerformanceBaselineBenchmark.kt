package com.jaysay.coursetable.benchmark

import android.content.Intent
import androidx.benchmark.macro.*
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PerformanceBaselineBenchmark {
    @get:Rule val benchmarkRule = MacrobenchmarkRule()

    private fun MacrobenchmarkScope.prepare(mode: String = "WEEK", count: Int = 42) {
        startActivityAndWait(Intent().setClassName(PACKAGE, "com.jaysay.coursetable.BenchmarkSetupActivity")
            .putExtra("mode", mode).putExtra("count", count))
        check(device.wait(Until.hasObject(By.descStartsWith("虚构性能课程")), 10_000)) { "Fixture did not open a populated schedule" }
    }

    @Test fun coldStart() = startup(42)
    @Test fun largeTableFirstDisplay() = startup(2000)
    private fun startup(count: Int) = benchmarkRule.measureRepeated(
        packageName = PACKAGE, metrics = listOf(StartupTimingMetric()), iterations = 5,
        startupMode = StartupMode.COLD, compilationMode = CompilationMode.Partial(baselineProfileMode = BaselineProfileMode.Disable, warmupIterations = 3),
        setupBlock = { prepare(count = count); pressHome() }
    ) { startActivityAndWait() }

    @Test fun weekPaging() = paging("WEEK")
    @Test fun dayPaging() = paging("DAY")
    private fun paging(mode: String) = benchmarkRule.measureRepeated(
        packageName = PACKAGE, metrics = listOf(FrameTimingMetric()), iterations = 5,
        compilationMode = CompilationMode.Partial(baselineProfileMode = BaselineProfileMode.Disable, warmupIterations = 3),
        setupBlock = { prepare(mode) }
    ) {
        repeat(3) {
            device.swipe(device.displayWidth * 4 / 5, device.displayHeight / 2,
                device.displayWidth / 5, device.displayHeight / 2, 20)
            device.waitForIdle()
            device.swipe(device.displayWidth / 5, device.displayHeight / 2,
                device.displayWidth * 4 / 5, device.displayHeight / 2, 20)
            device.waitForIdle()
        }
    }

    @Test fun detailRoundtrip() = benchmarkRule.measureRepeated(
        packageName = PACKAGE, metrics = listOf(FrameTimingMetric()), iterations = 5,
        compilationMode = CompilationMode.Partial(baselineProfileMode = BaselineProfileMode.Disable, warmupIterations = 3),
        setupBlock = { prepare("DAY") }
    ) {
        repeat(3) {
            val card = device.wait(Until.findObject(By.descStartsWith("虚构性能课程")), 5000)
            assertNotNull("Course card missing", card)
            card.click()
            check(device.wait(Until.hasObject(By.desc("编辑")), 5000)) { "Details did not open" }
            device.pressBack()
            check(device.wait(Until.hasObject(By.descStartsWith("虚构性能课程")), 5000)) { "Schedule did not return" }
        }
    }
    private companion object { const val PACKAGE = "com.jaysay.coursetable.benchmark" }
}
