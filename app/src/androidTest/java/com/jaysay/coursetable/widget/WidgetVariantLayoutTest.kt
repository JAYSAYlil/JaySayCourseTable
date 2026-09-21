package com.jaysay.coursetable.widget

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jaysay.coursetable.R
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
