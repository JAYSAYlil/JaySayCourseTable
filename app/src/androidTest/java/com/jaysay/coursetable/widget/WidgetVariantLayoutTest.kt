package com.jaysay.coursetable.widget

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.RemoteViews
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jaysay.coursetable.R
import com.jaysay.coursetable.data.preferences.ThemeAccent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 毛玻璃小组件与实体版必须“功能一致、只有材质不同”：
 * 两套布局的控件 id 集合必须完全相同，根节点背景都必须存在。
 */
@RunWith(AndroidJUnit4::class)
class WidgetVariantLayoutTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun idsOf(layoutRes: Int): Set<Int> {
        val root = LayoutInflater.from(context).inflate(layoutRes, null) as ViewGroup
        val ids = mutableSetOf<Int>()
        fun walk(view: View) {
            if (view.id != View.NO_ID) ids.add(view.id)
            if (view is ViewGroup) for (index in 0 until view.childCount) walk(view.getChildAt(index))
        }
        walk(root)
        return ids
    }

    @Test
    fun frostedWidgetExposesExactlyTheSameControlsAsTheSolidOne() {
        assertEquals(idsOf(WidgetVariant.SOLID.layoutRes), idsOf(WidgetVariant.FROSTED.layoutRes))
    }

    @Test
    fun accentColorReachesEveryAccentTextInTheWidget() {
        val accent = WidgetAccent.textColor(ThemeAccent.PINK, night = false)
        assertTrue(
            "非默认主题色必须与默认青绿不同",
            accent != WidgetAccent.textColor(ThemeAccent.TEAL, night = false)
        )
        val views = RemoteViews(context.packageName, WidgetVariant.SOLID.layoutRes)
        WidgetAccent.apply(views, accent)
        val inflated = views.apply(context, FrameLayout(context))
        assertEquals(accent, inflated.findViewById<TextView>(R.id.widget_weekday).currentTextColor)
        assertEquals(accent, inflated.findViewById<TextView>(R.id.widget_today_title).currentTextColor)
        assertEquals(accent, inflated.findViewById<TextView>(R.id.widget_tomorrow_title).currentTextColor)
    }

    @Test
    fun accentColorReachesTheCourseItemTimeLabel() {
        val accent = WidgetAccent.textColor(ThemeAccent.VIOLET, night = true)
        val row = WidgetCourseRow(0, "series", "示例课程", "示例教室", "示例教师", "08:00–09:35")
        val item = WidgetCourseItemViews.create(
            context, row, WidgetWidthMode.EXPANDED, WidgetVariant.FROSTED.itemLayoutRes, accent
        ).apply(context, FrameLayout(context))
        assertEquals(accent, item.findViewById<TextView>(R.id.widget_item_time).currentTextColor)
    }

    @Test
    fun frostedWidgetKeepsItsOwnMaterialBackground() {
        val frosted = LayoutInflater.from(context).inflate(WidgetVariant.FROSTED.layoutRes, null)
        assertNotNull("根节点必须有毛玻璃背景", frosted.findViewById<View>(R.id.widget_root).background)
        assertTrue(
            "毛玻璃必须使用独立的根布局资源",
            WidgetVariant.FROSTED.layoutRes != WidgetVariant.SOLID.layoutRes
        )
    }

    @Test
    fun frostedCourseItemsUseTheirOwnLayout() {
        assertEquals(idsOf(WidgetVariant.SOLID.itemLayoutRes), idsOf(WidgetVariant.FROSTED.itemLayoutRes))
        val item = LayoutInflater.from(context).inflate(WidgetVariant.FROSTED.itemLayoutRes, null)
        assertNotNull("课程条目必须有毛玻璃背景", item.background)
    }
}
