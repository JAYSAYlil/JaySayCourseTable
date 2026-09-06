package com.jaysay.coursetable.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.jaysay.coursetable.ui.theme.JaySayTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class MonthLargeTextTest {
    @get:Rule val rule = createComposeRule()
    @Test fun sixRowMonthAtDoubleFontScrollsToLastDateAndRetainsNavigation() {
        var selected: LocalDate? = null
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                JaySayTheme {
                    MonthGrid(Modifier.width(320.dp).height(360.dp), emptyList(),
                        LocalDate.of(2030, 9, 1), 20, "2030-08-26", emptySet(), emptyList(), emptyMap(), false,
                        onDayClick = { selected = it })
                }
            }
        }
        rule.onNodeWithContentDescription("2030-09-30", substring = true).performScrollTo().assertIsDisplayed().performClick()
        rule.runOnIdle { assertEquals(LocalDate.of(2030, 9, 30), selected) }
    }
}
