package com.jaysay.coursetable.widget

import androidx.compose.ui.graphics.luminance
import com.jaysay.coursetable.data.preferences.ThemeAccent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 小组件取色契约：每个主题色都要给出不同色值，浅色/深色端各自可读，默认仍是青绿。 */
class WidgetAccentTest {
    @Test
    fun defaultAccentKeepsTheBrandTeal() {
        assertEquals(0xFF0B6E64.toInt(), WidgetAccent.textColor(ThemeAccent.TEAL, night = false))
        assertEquals(0xFF6FEADD.toInt(), WidgetAccent.textColor(ThemeAccent.TEAL, night = true))
    }

    @Test
    fun everyAccentGetsItsOwnWidgetColor() {
        val day = ThemeAccent.entries.map { WidgetAccent.textColor(it, night = false) }
        val night = ThemeAccent.entries.map { WidgetAccent.textColor(it, night = true) }
        assertEquals(ThemeAccent.entries.size, day.distinct().size)
        assertEquals(ThemeAccent.entries.size, night.distinct().size)
    }

    @Test
    fun dayAndNightTonesDifferAndStayDarkOrLight() {
        ThemeAccent.entries.forEach { accent ->
            val day = WidgetAccent.textColor(accent, night = false)
            val night = WidgetAccent.textColor(accent, night = true)
            assertNotEquals("$accent 深浅色端不应同色", day, night)
            // 浅色端给深色字、深色端给浅色字，否则半透明表面上会读不清。
            assertTrue("$accent 浅色端文字应为暗色", luminance(day) < 0.35f)
            assertTrue("$accent 深色端文字应为亮色", luminance(night) > 0.45f)
        }
    }

    private fun luminance(argb: Int): Float {
        val color = androidx.compose.ui.graphics.Color(argb)
        return color.luminance()
    }
}
