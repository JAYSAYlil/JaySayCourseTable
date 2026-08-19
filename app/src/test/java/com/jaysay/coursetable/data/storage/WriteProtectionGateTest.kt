package com.jaysay.coursetable.data.storage

import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class WriteProtectionGateTest {
    @Test
    fun ordinaryWritesAreBlockedUntilValidatedRestoreUnlocksProtection() {
        val gate = WriteProtectionGate()
        gate.lock("课表数据损坏")

        assertTrue(gate.isReadOnly)
        assertThrows(ReadOnlyModeException::class.java) { gate.requireWritable() }

        gate.unlockAfterValidatedRestore()

        assertFalse(gate.isReadOnly)
        gate.requireWritable()
    }
}
