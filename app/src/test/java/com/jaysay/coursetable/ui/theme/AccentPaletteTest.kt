package com.jaysay.coursetable.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.jaysay.coursetable.data.preferences.ThemeAccent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 主题色调色板契约：默认色必须是原品牌青绿，且每套颜色的容器与其文字都保持可读对比度。 */
class AccentPaletteTest {
    private fun contrastRatio(background: Color, foreground: Color): Float {
        val lighter = maxOf(background.luminance(), foreground.luminance()) + 0.05f
        val darker = minOf(background.luminance(), foreground.luminance()) + 0.05f
        return lighter / darker
    }

    @Test
    fun defaultAccentKeepsTheOriginalBrandTeal() {
        val teal = accentPalette(ThemeAccent.TEAL)
        assertEquals(Color(0xFF0F8F82), teal.lightPrimary)
        assertEquals(Color(0xFFCFF2EC), teal.lightContainer)
        assertEquals(Color(0xFF3ADBC4), teal.darkPrimary)
    }

    @Test
    fun everyAccentIsDistinctInBothModes() {
        val palettes = ThemeAccent.entries.map(::accentPalette)
        assertEquals(ThemeAccent.entries.size, palettes.map { it.lightPrimary }.distinct().size)
        assertEquals(ThemeAccent.entries.size, palettes.map { it.darkPrimary }.distinct().size)
    }

    @Test
    fun containerTextStaysReadableOnEveryAccent() {
        ThemeAccent.entries.forEach { accent ->
            val palette = accentPalette(accent)
            assertTrue(
                "$accent 浅色容器文字对比度不足：" +
                    contrastRatio(palette.lightContainer, palette.lightOnContainer),
                contrastRatio(palette.lightContainer, palette.lightOnContainer) >= 4.5f
            )
            assertTrue(
                "$accent 深色容器文字对比度不足：" +
                    contrastRatio(palette.darkContainer, palette.darkOnContainer),
                contrastRatio(palette.darkContainer, palette.darkOnContainer) >= 4.5f
            )
        }
    }

    @Test
    fun primaryStaysVisibleOnItsOwnSurface() {
        ThemeAccent.entries.forEach { accent ->
            val palette = accentPalette(accent)
            assertTrue("$accent 浅色 primary 在白底上对比不足", contrastRatio(Color.White, palette.lightPrimary) >= 3f)
            assertTrue(
                "$accent 深色 primary 在深色底上对比不足",
                contrastRatio(Color(0xFF0B0C0D), palette.darkPrimary) >= 3f
            )
        }
    }

    @Test
    fun darkOnPrimaryStaysReadableOnItsOwnPrimary() {
        ThemeAccent.entries.forEach { accent ->
            val palette = accentPalette(accent)
            assertTrue(
                "$accent 深色 primary 上的文字对比度不足：" +
                    contrastRatio(palette.darkPrimary, palette.darkOnPrimary),
                contrastRatio(palette.darkPrimary, palette.darkOnPrimary) >= 4.5f
            )
        }
    }

    /** 深色端的文字色此前是共用的青绿近黑，换成别的主题色后会残留一层绿底。 */
    @Test
    fun darkOnPrimaryFollowsTheAccentInsteadOfStayingTeal() {
        val brand = accentPalette(ThemeAccent.TEAL)
        assertEquals(Color(0xFF00332C), brand.darkOnPrimary)
        ThemeAccent.entries.filter { it != ThemeAccent.TEAL }.forEach { accent ->
            assertNotEquals(
                "$accent 的深色文字色不应沿用青绿",
                brand.darkOnPrimary,
                accentPalette(accent).darkOnPrimary
            )
        }
        assertEquals(
            "每套主题色都要有自己的深色文字色",
            ThemeAccent.entries.size,
            ThemeAccent.entries.map { accentPalette(it).darkOnPrimary }.distinct().size
        )
    }

    @Test
    fun notificationColorFollowsEveryAccent() {
        val day = ThemeAccent.entries.map { notificationAccent(it, dark = false) }
        val night = ThemeAccent.entries.map { notificationAccent(it, dark = true) }
        assertEquals("浅色端通知色要能区分每个主题色", ThemeAccent.entries.size, day.distinct().size)
        assertEquals("深色端通知色要能区分每个主题色", ThemeAccent.entries.size, night.distinct().size)
        assertEquals(0xFF0F8F82.toInt(), notificationAccent(ThemeAccent.TEAL, dark = false))
        assertEquals(0xFF3ADBC4.toInt(), notificationAccent(ThemeAccent.TEAL, dark = true))
    }
}
