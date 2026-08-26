package com.jaysay.coursetable.data.reminder

import android.content.Intent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderRestoreReceiverTest {
    @Test
    fun acceptsOnlyRegisteredSystemRestoreActions() {
        listOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED
        ).forEach { action ->
            assertTrue("应接受系统恢复事件 $action", shouldRestoreReminders(action))
        }
    }

    @Test
    fun rejectsMissingOrUnexpectedActions() {
        assertFalse(shouldRestoreReminders(null))
        assertFalse(shouldRestoreReminders("com.example.UNEXPECTED"))
    }
}
