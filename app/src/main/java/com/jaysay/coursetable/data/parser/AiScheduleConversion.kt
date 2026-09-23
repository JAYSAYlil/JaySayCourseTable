package com.jaysay.coursetable.data.parser

import com.jaysay.coursetable.data.model.Course
import com.jaysay.coursetable.util.TimeUtils
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class AiScheduleResult(
    val courses: List<Course>,
    val workbookBytes: ByteArray,
    val generatedCourseIds: Int,
    val maximumWeek: Int
)

object AiScheduleConversion {
    private val allowedFields = setOf(
        "course_id", "course_name", "class_number", "department", "credits", "day_of_week",
        "start_period", "end_period", "weeks", "teacher", "classroom", "course_type",
        "course_category", "is_online", "assessment_method", "needs_review", "source_rows"
    )

    fun parseAndVerifyResult(json: String, source: WorkbookTextExtractor.Extracted): List<Course> {
        return parseResult(json, source).first
    }

    private fun parseResult(json: String, source: WorkbookTextExtractor.Extracted): Pair<List<Course>, Int> {
        if (json.length > AiChatCompletionsClient.MAX_RESPONSE_BYTES) {
            throw AiConversionException("AI 转换结果超过 1 MB，已拒绝处理")
        }
        val root = runCatching { StrictJson.parse(json).asObject() }
            .getOrElse { throw AiConversionException("AI 返回内容不是有效 JSON") }
        if (root.keys != setOf("courses")) throw AiConversionException("AI 返回 JSON 字段不符合约定")
        val rows = root["courses"].asArray()
        if (rows.isEmpty()) throw AiConversionException("没有识别到课程，请检查文件或更换模型")
        if (rows.size > 500) throw AiConversionException("识别课程超过 500 条，请拆分工作表")
        val existingRows = source.cells.map(WorkbookTextExtractor.CellText::row).toSet()
        var generatedIds = 0
        val courses = rows.mapIndexed { index, item ->
            val fields = item.asObject()
            if (fields.keys.any { it !in allowedFields }) throw AiConversionException("AI 结果包含不支持的字段")
            if ((fields["needs_review"] as? JsonValue.Bool)?.value != false) {
                throw AiConversionException("AI 标记了无法确认的课程信息，请检查原表后重新转换")
            }
            val evidenceRows = fields["source_rows"].asArray().map(::strictInt)
            if (evidenceRows.isEmpty() || evidenceRows.any { it !in existingRows }) {
                throw AiConversionException("AI 结果缺少有效的来源行，请重试")
            }
            val name = strictString(fields, "course_name", required = true)
            if (name.isBlank() || name.length > 160) throw AiConversionException("课程名称为空或过长")
            val idValue = fields["course_id"]
            val id = when (idValue) {
                null, JsonValue.Null -> ""
                is JsonValue.Str -> idValue.value.trim()
                else -> throw AiConversionException("课程号必须是文本")
            }
            if (id.length > 120) throw AiConversionException("课程号过长")
            if (id.isBlank()) generatedIds++
            val day = strictInt(fields["day_of_week"])
            val start = strictInt(fields["start_period"])
            val end = strictInt(fields["end_period"])
            val weeks = fields["weeks"].asArray().map(::strictInt)
            if (day !in 1..7 || start !in 1..30 || end !in start..30 || weeks.isEmpty() ||
                weeks.any { it !in 1..30 } || weeks != weeks.distinct().sorted()
            ) throw AiConversionException("课程星期、节次或周次超出范围或格式不明确")
            val credits = when (val value = fields["credits"]) {
                null -> 0f
                is JsonValue.Num -> value.value.toFloatOrNull()?.takeIf { it.isFinite() && it in 0f..100f }
                else -> null
            } ?: throw AiConversionException("学分必须是 0 到 100 的数字")
            val online = when (val value = fields["is_online"]) {
                null -> false
                is JsonValue.Bool -> value.value
                else -> throw AiConversionException("线上教学标记必须是布尔值")
            }
            fun optional(key: String): String = strictString(fields, key, required = false).also {
                if (it.length > 200) throw AiConversionException("课程可选文本过长")
            }
            Course(
                courseId = id.ifBlank { "AI-${(index + 1).toString().padStart(3, '0')}" },
                courseName = name.trim(),
                classNumber = optional("class_number"),
                department = optional("department"),
                credits = credits,
                weeks = weeks,
                dayOfWeek = day,
                startPeriod = start,
                endPeriod = end,
                teacher = optional("teacher"),
                classroom = optional("classroom"),
                courseType = optional("course_type"),
                courseCategory = optional("course_category"),
                isOnline = online,
                assessmentMethod = optional("assessment_method")
            )
        }
        if (courses.map(Course::uniqueKey).distinct().size != courses.size) {
            throw AiConversionException("AI 结果中有重复课程，请检查工作表后重新转换")
        }
        return courses to generatedIds
    }

    /** Writes the existing 19-column template contract using inline strings and no OOXML library. */
    fun writeAndVerify(courses: List<Course>): ByteArray {
        require(courses.isNotEmpty()) { "没有可导出的课程" }
        val bytes = writeWorkbook(courses)
        val parsed = ByteArrayInputStream(bytes).use(ExcelParser::parse)
        if (parsed.errors.isNotEmpty() || parsed.courses.size != courses.size) {
            throw AiConversionException("生成的 Excel 模板未能通过本地回读校验")
        }
        courses.zip(parsed.courses).forEach { (before, after) ->
            if (before.courseId != after.courseId || before.courseName != after.courseName ||
                before.classNumber != after.classNumber || before.department != after.department ||
                before.credits != after.credits || before.dayOfWeek != after.dayOfWeek ||
                before.startPeriod != after.startPeriod || before.endPeriod != after.endPeriod ||
                before.weeks != after.weeks || before.teacher != after.teacher ||
                before.classroom != after.classroom || before.courseType != after.courseType ||
                before.courseCategory != after.courseCategory || before.isOnline != after.isOnline ||
                before.assessmentMethod != after.assessmentMethod
            ) throw AiConversionException("生成的 Excel 课程字段未通过本地回读校验")
        }
        return bytes
    }

    fun convert(json: String, source: WorkbookTextExtractor.Extracted): AiScheduleResult {
        val (courses, generatedIds) = parseResult(json, source)
        val bytes = writeAndVerify(courses)
        return AiScheduleResult(
            courses,
            bytes,
            generatedIds,
            courses.maxOf { it.weeks.maxOrNull() ?: 0 }
        )
    }

    private fun strictString(fields: Map<String, JsonValue>, key: String, required: Boolean): String {
        val value = fields[key]
        if (value == null && !required) return ""
        return (value as? JsonValue.Str)?.value
            ?: throw AiConversionException("AI 结果中的文本字段格式不正确")
    }

    private fun strictInt(value: JsonValue?): Int {
        val number = (value as? JsonValue.Num)?.value
            ?: throw AiConversionException("AI 结果中的数字字段必须是整数")
        return number.toIntOrNull() ?: throw AiConversionException("AI 结果中的数字字段必须是整数")
    }

    private fun writeWorkbook(courses: List<Course>): ByteArray {
        val headers = listOf(
            "课程号", "课程名", "课序号", "开课单位", "学分", "上课周次", "上课星期", "开始节次", "结束节次",
            "上课教师", "教室名称", "课程性质", "课程类别", "校公选课类别", "是否线上教学", "授课平台", "平台信息", "建群信息", "重修重考"
        )
        val rows = mutableListOf(headers)
        courses.forEach { course ->
            rows += listOf(
                course.courseId, course.courseName, course.classNumber, course.department,
                course.credits.toString(), course.weeks.joinToString(","), TimeUtils.getDayName(course.dayOfWeek),
                course.startPeriod.toString(), course.endPeriod.toString(), course.teacher, course.classroom,
                course.courseType, course.courseCategory, "", if (course.isOnline) "是" else "否",
                "", "", "", course.assessmentMethod
            )
        }
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry("[Content_Types].xml"))
            zip.write("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/></Types>""".toByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("_rels/.rels"))
            zip.write("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>""".toByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("xl/workbook.xml"))
            zip.write("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="课表" sheetId="1" r:id="rId1"/></sheets></workbook>""".toByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("xl/_rels/workbook.xml.rels"))
            zip.write("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/></Relationships>""".toByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("xl/worksheets/sheet1.xml"))
            val sheet = buildString {
                append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><dimension ref="A1:S${rows.size}"/><sheetData>""")
                rows.forEachIndexed { rowIndex, values ->
                    append("<row r=\"${rowIndex + 1}\">")
                    values.forEachIndexed { columnIndex, value ->
                        if (value.isNotEmpty()) append("<c r=\"${columnName(columnIndex + 1)}${rowIndex + 1}\" t=\"inlineStr\"><is><t xml:space=\"preserve\">${xmlEscape(value)}</t></is></c>")
                    }
                    append("</row>")
                }
                append("</sheetData></worksheet>")
            }
            zip.write(sheet.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
        return output.toByteArray()
    }

    private fun columnName(index: Int): String {
        var value = index
        val result = StringBuilder()
        while (value > 0) {
            val digit = (value - 1) % 26
            result.append(('A'.code + digit).toChar())
            value = (value - 1) / 26
        }
        return result.reverse().toString()
    }

    private fun xmlEscape(value: String): String = buildString(value.length) {
        value.forEach { c ->
            when (c) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&apos;")
                else -> if (c == '\t' || c == '\n' || c == '\r' || c.code >= 0x20) append(c)
            }
        }
    }
}
