package com.jaysay.coursetable

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NavigationPolicyTest {
    @Test
    fun everyTopLevelAndNestedScreenHasTheExpectedBackDestination() {
        assertNull(Screen.MAIN.backDestination())
        assertEquals(Screen.MAIN, Screen.SETTINGS.backDestination())
        assertEquals(Screen.MAIN, Screen.IMPORT_CONFIRM.backDestination())
        assertEquals(Screen.MAIN, Screen.TABLE_MANAGE.backDestination())
        assertEquals(Screen.MAIN, Screen.AGENDA.backDestination())
        assertEquals(Screen.SETTINGS, Screen.HISTORY.backDestination())
        assertEquals(Screen.SETTINGS, Screen.CALENDAR.backDestination())
    }

    @Test
    fun courseDetailReturnsToItsActualSourceAndRejectsInvalidOrigins() {
        assertEquals(Screen.MAIN, Screen.COURSE_DETAIL.backDestination(Screen.MAIN))
        assertEquals(Screen.AGENDA, Screen.COURSE_DETAIL.backDestination(Screen.AGENDA))
        assertEquals(Screen.MAIN, Screen.COURSE_DETAIL.backDestination(Screen.SETTINGS))
        assertEquals(Screen.MAIN, Screen.COURSE_DETAIL.backDestination(Screen.COURSE_DETAIL))
    }
}
