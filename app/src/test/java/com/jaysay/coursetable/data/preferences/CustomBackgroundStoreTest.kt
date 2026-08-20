package com.jaysay.coursetable.data.preferences

import org.junit.Assert.assertEquals
import org.junit.Test

class CustomBackgroundStoreTest {
    @Test
    fun largePortraitImageIsScaledWithinMemoryBoundWithoutChangingAspectRatio() {
        assertEquals(1215 to 2160, CustomBackgroundStore.scaledSize(3024, 5376))
    }

    @Test
    fun smallImageKeepsOriginalDimensions() {
        assertEquals(1080 to 1920, CustomBackgroundStore.scaledSize(1080, 1920))
    }

    @Test
    fun decoderSamplingUsesPowersOfTwoAndAvoidsUnnecessarilyLargeDecode() {
        assertEquals(2, CustomBackgroundStore.calculateInSampleSize(6000, 4000))
        assertEquals(1, CustomBackgroundStore.calculateInSampleSize(2160, 1080))
    }
}
