package com.jaysay.coursetable.util

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * 手动触发的版本检查：只访问 GitHub Releases API 一次，无任何常驻联网。
 * 阻塞式实现，调用方负责放到 IO 线程。
 */
object UpdateChecker {
    private const val LATEST_URL =
        "https://api.github.com/repos/JAYSAYlil/JaySayCourseTable/releases/latest"

    data class Result(
        val latestVersion: String?,
        val releaseUrl: String?,
        val error: String?
    )

    fun check(): Result {
        return try {
            val connection = URL(LATEST_URL).openConnection() as HttpURLConnection
            connection.connectTimeout = 8_000
            connection.readTimeout = 8_000
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("User-Agent", "JaySayCourseTable")
            connection.inputStream.use { stream ->
                val payload = JSONObject(stream.readBytes().toString(Charsets.UTF_8))
                Result(
                    latestVersion = payload.optString("tag_name").removePrefix("v"),
                    releaseUrl = payload.optString("html_url").takeIf(String::isNotBlank),
                    error = null
                )
            }
        } catch (e: Exception) {
            Result(null, null, e.message ?: e.javaClass.simpleName)
        }
    }

    /** 按数字段比较版本号；无法解析的段按 0 处理。 */
    fun isNewer(candidate: String, current: String): Boolean {
        fun parts(v: String) = v.split('.', '-', '_').map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
        val a = parts(candidate)
        val b = parts(current)
        for (index in 0 until maxOf(a.size, b.size)) {
            val left = a.getOrElse(index) { 0 }
            val right = b.getOrElse(index) { 0 }
            if (left != right) return left > right
        }
        return false
    }
}
