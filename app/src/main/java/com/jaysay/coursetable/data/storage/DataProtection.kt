package com.jaysay.coursetable.data.storage

/** 只读保护下所有普通持久化操作共用的异常，避免各入口遗漏判断。 */
class ReadOnlyModeException(message: String) : IllegalStateException(message)

/**
 * 进程内写入门控。只有成功落盘一份经过严格校验的完整备份后才允许解除保护。
 * 保持为纯 Kotlin，便于对“阻止写入 / 恢复解锁”做快速单元测试。
 */
class WriteProtectionGate {
    var reason: String? = null
        private set

    val isReadOnly: Boolean get() = reason != null

    fun lock(message: String) {
        reason = message
    }

    fun requireWritable() {
        reason?.let {
            throw ReadOnlyModeException("数据保护模式已启用：$it。请先在设置中恢复一份完整备份")
        }
    }

    fun unlockAfterValidatedRestore() {
        reason = null
    }
}
