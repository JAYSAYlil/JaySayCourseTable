package com.jaysay.coursetable.data.transfer

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicLong

/**
 * 串行化导入草稿写入，并让过时的保存/清除操作自动失效。
 *
 * 暂存和确认通常是连续的两个 UI 事件；仅使用两个独立的 IO 协程会让旧保存
 * 在清除之后落盘。调用方为每次操作领取 revision，真正写盘前再次校验它。
 */
internal class ImportDraftWriteGate {
    private val mutex = Mutex()
    private val revision = AtomicLong(0L)

    fun nextRevision(): Long = revision.incrementAndGet()

    suspend fun runIfCurrent(token: Long, action: suspend () -> Unit): Boolean = mutex.withLock {
        if (revision.get() != token) return@withLock false
        action()
        true
    }
}
