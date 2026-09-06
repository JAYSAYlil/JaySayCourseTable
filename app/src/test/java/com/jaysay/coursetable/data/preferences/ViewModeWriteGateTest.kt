package com.jaysay.coursetable.data.preferences

import org.junit.Assert.*
import org.junit.Test

class ViewModeWriteGateTest {
    @Test fun repeatedModeStillHasNewRequestIdentity() {
        val gate = ViewModeWriteGate()
        val firstDay = gate.begin(0)
        gate.begin(0) // month
        val lastDay = gate.begin(0)
        assertFalse(gate.finish(0, firstDay))
        assertTrue(gate.isCurrent(0, lastDay))
        assertTrue(gate.finish(0, lastDay))
    }
    @Test fun changingTableStructureInvalidatesQueuedRequests() {
        val gate = ViewModeWriteGate()
        val deleted = gate.begin(1)
        gate.invalidateAll()
        val replacement = gate.begin(1)
        assertFalse(gate.finish(1, deleted))
        assertTrue(gate.isCurrent(1, replacement))
    }
    @Test fun tableRequestsAreIndependent() {
        val gate = ViewModeWriteGate()
        val a = gate.begin(0)
        val b = gate.begin(1)
        assertTrue(gate.finish(0, a))
        assertTrue(gate.isCurrent(1, b))
    }
}
