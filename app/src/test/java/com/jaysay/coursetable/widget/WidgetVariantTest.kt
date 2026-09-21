package com.jaysay.coursetable.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** 小组件变体解析契约：未知或缺失的标记必须回落到实体版，两个变体不能共用同一套布局。 */
class WidgetVariantTest {
    @Test
    fun unknownOrMissingTagsFallBackToSolid() {
        assertEquals(WidgetVariant.SOLID, WidgetVariant.fromTag(null))
        assertEquals(WidgetVariant.SOLID, WidgetVariant.fromTag(""))
        assertEquals(WidgetVariant.SOLID, WidgetVariant.fromTag("glassy"))
    }

    @Test
    fun tagsAreStableAndRoundTrip() {
        WidgetVariant.entries.forEach { variant ->
            assertEquals(variant, WidgetVariant.fromTag(variant.tag))
        }
    }

    @Test
    fun variantsUseTheirOwnLayouts() {
        assertNotEquals(WidgetVariant.SOLID.layoutRes, WidgetVariant.FROSTED.layoutRes)
        assertNotEquals(WidgetVariant.SOLID.itemLayoutRes, WidgetVariant.FROSTED.itemLayoutRes)
    }
}
