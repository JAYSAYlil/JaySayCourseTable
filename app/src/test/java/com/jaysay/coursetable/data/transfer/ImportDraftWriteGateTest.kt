package com.jaysay.coursetable.data.transfer

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportDraftWriteGateTest {
    @Test
    fun staleOperationIsSkippedAfterNewerRevision() = runBlocking {
        val gate = ImportDraftWriteGate()
        val oldRevision = gate.nextRevision()
        val newRevision = gate.nextRevision()
        var oldRan = false
        var newRan = false

        assertFalse(gate.runIfCurrent(oldRevision) { oldRan = true })
        assertTrue(gate.runIfCurrent(newRevision) { newRan = true })
        assertFalse(oldRan)
        assertTrue(newRan)
    }
}
