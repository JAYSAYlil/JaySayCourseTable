package com.jaysay.coursetable.data.transfer

import android.content.Context
import com.jaysay.coursetable.data.parser.ExcelParser
import com.jaysay.coursetable.data.repository.TableData
import com.jaysay.coursetable.data.repository.TableDataJson
import com.jaysay.coursetable.data.storage.AtomicFileStore
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** 小型本地导入草稿，允许进程被回收后继续确认；确认或取消后立即清除。 */
class ImportDraftStore private constructor(private val file: File) {
    constructor(context: Context) : this(File(context.filesDir, FILE_NAME))

    fun load(): ExcelParser.ParseResult? = AtomicFileStore(file).read(::decode)

    fun save(result: ExcelParser.ParseResult) {
        require(result.courses.size <= MAX_COURSES) { "导入草稿课程过多" }
        val table = TableData(name = "导入草稿", courses = result.courses)
        val text = JSONObject()
            .put("schemaVersion", 1)
            .put("tables", TableDataJson.toJson(listOf(table)))
            .put("warnings", JSONArray(result.errors.take(MAX_WARNINGS)))
            .toString(2)
        AtomicFileStore(file).write(text)
    }

    fun clear() {
        listOf(file, File(file.parentFile, "${file.name}.bak"), File(file.parentFile, "${file.name}.tmp"))
            .forEach { candidate -> if (candidate.exists() && !candidate.delete()) error("无法清除导入草稿") }
    }

    private fun decode(text: String): ExcelParser.ParseResult {
        require(text.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "导入草稿过大" }
        val root = JSONObject(text)
        require(root.optInt("schemaVersion", -1) == 1) { "导入草稿版本不受支持" }
        val tableArray = root.optJSONArray("tables") ?: error("导入草稿缺少课程")
        val table = TableDataJson.fromJson(tableArray, requireEveryRowValid = true).single()
        require(table.courses.size <= MAX_COURSES) { "导入草稿课程过多" }
        val warningArray = root.optJSONArray("warnings") ?: JSONArray()
        val warnings = (0 until warningArray.length().coerceAtMost(MAX_WARNINGS)).map(warningArray::optString)
        return ExcelParser.ParseResult(table.courses, warnings)
    }

    companion object {
        private const val FILE_NAME = "import-draft.json"
        private const val MAX_BYTES = 5 * 1024 * 1024
        private const val MAX_COURSES = 5_000
        private const val MAX_WARNINGS = 1_000

        fun forTest(file: File) = ImportDraftStore(file)
    }
}
