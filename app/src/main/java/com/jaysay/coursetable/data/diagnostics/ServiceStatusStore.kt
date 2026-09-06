package com.jaysay.coursetable.data.diagnostics

import android.content.Context
import androidx.core.content.edit

/** Device-local operational receipts; no course data, URI, exception text or backup payload. */
object ServiceStatusStore {
    const val FILE = "service_status"
    fun record(context: Context, operation: String, success: Boolean) {
        runCatching {
            context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit {
                putBoolean("${operation}_failed", !success)
                if (success) putLong("${operation}_success", System.currentTimeMillis())
            }
        }
    }
}
