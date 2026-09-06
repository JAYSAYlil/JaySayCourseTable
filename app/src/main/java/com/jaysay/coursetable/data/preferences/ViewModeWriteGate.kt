package com.jaysay.coursetable.data.preferences

/** Main-thread request identities: an old completion must never undo a newer selection. */
internal class ViewModeWriteGate {
    private var serial = 0L
    private val latest = mutableMapOf<Int, Long>()

    fun begin(tableIndex: Int): Long = (++serial).also { latest[tableIndex] = it }
    fun isCurrent(tableIndex: Int, request: Long): Boolean = latest[tableIndex] == request
    fun finish(tableIndex: Int, request: Long): Boolean {
        if (!isCurrent(tableIndex, request)) return false
        latest.remove(tableIndex)
        return true
    }
    fun invalidateAll() { latest.clear() }
}
