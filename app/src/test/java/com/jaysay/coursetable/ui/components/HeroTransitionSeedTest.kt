package com.jaysay.coursetable.ui.components

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Hero 转场中断续接的种子进度测试：
 * 反向/重入必须从当前在途进度继续，而不是先跳到端点再重新起飞。
 */
class HeroTransitionSeedTest {

    @Before
    fun resetFlightState() {
        HeroRegistry.lastProgress = 0f
        HeroRegistry.lastFlightKey = null
    }

    private fun request(key: String, forward: Boolean) = HeroRegistry.HeroRequest(
        key = key,
        color = Color.White,
        fromCard = Rect.Zero,
        toHeader = null,
        forward = forward
    )

    @Test
    fun reverseMidFlightContinuesFromVisibleProgress() {
        // 正向飞到一半时用户按下返回：反向应从当前进度继续。
        HeroRegistry.lastFlightKey = "高数-1-2"
        HeroRegistry.lastProgress = 0.37f
        val seed = HeroRegistry.seedProgress(request("高数-1-2", forward = false))
        assertEquals(0.37f, seed, 0.0001f)
    }

    @Test
    fun reverseAfterCompletedForwardStartsAtOne() {
        HeroRegistry.lastFlightKey = "高数-1-2"
        HeroRegistry.lastProgress = 1f
        val seed = HeroRegistry.seedProgress(request("高数-1-2", forward = false))
        assertEquals(1f, seed, 0.0001f)
    }

    @Test
    fun forwardMidFlightSameCourseContinues() {
        // 反向飞到一半时再次点击同一张卡：正向从当前进度折返。
        HeroRegistry.lastFlightKey = "高数-1-2"
        HeroRegistry.lastProgress = 0.62f
        val seed = HeroRegistry.seedProgress(request("高数-1-2", forward = true))
        assertEquals(0.62f, seed, 0.0001f)
    }

    @Test
    fun forwardAfterCompletedFlightStartsAtZero() {
        HeroRegistry.lastFlightKey = "高数-1-2"
        HeroRegistry.lastProgress = 1f
        val seed = HeroRegistry.seedProgress(request("高数-1-2", forward = true))
        assertEquals(0f, seed, 0.0001f)
    }

    @Test
    fun forwardForDifferentCourseStartsAtZero() {
        // 上一次飞行属于另一门课：新课程从标准起点起飞，不继承旧进度。
        HeroRegistry.lastFlightKey = "高数-1-2"
        HeroRegistry.lastProgress = 0.5f
        val seed = HeroRegistry.seedProgress(request("英语-3-4", forward = true))
        assertEquals(0f, seed, 0.0001f)
    }

    @Test
    fun firstFlightStartsAtZero() {
        val seed = HeroRegistry.seedProgress(request("高数-1-2", forward = true))
        assertEquals(0f, seed, 0.0001f)
    }
}
