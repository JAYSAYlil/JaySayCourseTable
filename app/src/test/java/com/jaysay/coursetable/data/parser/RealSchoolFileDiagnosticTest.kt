package com.jaysay.coursetable.data.parser

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.inputStream
import org.junit.Test

/**
 * 本机诊断入口：直接解析项目根目录下的真实学校课表文件，打印每份文件的解析结果。
 * 仅本机运行（依赖用户目录），不作为常规回归；仓库不提交用户数据。
 */
class RealSchoolFileDiagnosticTest {
    /** 工作区根目录（源码仓库的上一级）；不写死任何本机路径与文件名。 */
    private val root: Path =
        Paths.get(System.getProperty("user.dir")).parent?.parent ?: Paths.get(System.getProperty("user.dir"))

    private fun localSamples(): List<Path> {
        if (!Files.isDirectory(root)) return emptyList()
        return Files.list(root).use { stream ->
            stream.filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".xlsx", ignoreCase = true) }
                .sorted()
                .toList()
        }
    }

    @Test
    fun dumpRealSchoolFiles() {
        for (path in localSamples()) {
            val name = path.fileName.toString()
            if (!Files.exists(path)) continue
            val result = path.inputStream().use(ExcelParser::parse)
            println("===== $name =====")
            println("courses=${result.courses.size} errors=${result.errors}")
            result.courses.take(3).forEach {
                println("  sample: ${it.courseName} 周${it.dayOfWeek} P${it.startPeriod}-${it.endPeriod} weeks=${it.weeks.take(5)}...")
            }
        }
    }

    @Test
    fun dumpRawGridOfRealFile() {
        val path = localSamples().firstOrNull() ?: return
        if (!Files.exists(path)) return
        val grid = path.inputStream().use { MinimalXlsxReader.read(it) }
        println("lastRowNum=${grid.lastRowNum}")
        for (r in 0..minOf(grid.lastRowNum, 12)) {
            println("row $r -> " + (grid.row(r)?.entries?.joinToString(" | ") { (c, v) -> "[$c]$v" } ?: "<null>"))
        }
    }
}
