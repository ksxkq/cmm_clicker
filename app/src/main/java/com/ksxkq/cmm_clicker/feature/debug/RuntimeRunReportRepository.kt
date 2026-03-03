package com.ksxkq.cmm_clicker.feature.debug

import android.content.Context
import com.ksxkq.cmm_clicker.core.runtime.RuntimeRunReport
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

data class RuntimeRunReportSummary(
    val reportId: String,
    val traceId: String,
    val source: String,
    val taskId: String?,
    val taskName: String?,
    val status: String,
    val errorCode: String?,
    val message: String?,
    val stepCount: Int,
    val durationMs: Long,
    val finishedAtEpochMs: Long,
)

class RuntimeRunReportRepository(
    context: Context,
) {
    private val file = context.getFileStreamPath(FILE_NAME)
    private val mutex = Mutex()

    suspend fun append(report: RuntimeRunReport) = mutex.withLock {
        val existing = readLinesLocked().toMutableList()
        existing += report.toJson()
        val trimmed = existing.takeLast(MAX_REPORTS)
        file.parentFile?.mkdirs()
        val payload = if (trimmed.isEmpty()) "" else trimmed.joinToString(separator = "\n", postfix = "\n")
        file.writeText(payload)
    }

    suspend fun latestJson(): String? = mutex.withLock {
        readLinesLocked().lastOrNull()
    }

    suspend fun findJsonByReportId(reportId: String): String? = mutex.withLock {
        if (reportId.isBlank()) {
            return@withLock null
        }
        readLinesLocked()
            .asReversed()
            .firstOrNull { line ->
                extractReportId(line) == reportId
            }
    }

    suspend fun listLatestSummaries(limit: Int = 20): List<RuntimeRunReportSummary> = mutex.withLock {
        val safeLimit = limit.coerceAtLeast(1)
        return@withLock readLinesLocked()
            .takeLast(safeLimit)
            .asReversed()
            .mapNotNull { line -> toSummary(line) }
    }

    private fun readLinesLocked(): List<String> {
        if (!file.exists()) {
            return emptyList()
        }
        return file
            .readLines()
            .asSequence()
            .map { line -> line.trim() }
            .filter { line -> line.isNotBlank() }
            .toList()
    }

    private fun extractReportId(line: String): String? {
        return runCatching {
            JSONObject(line).optString("reportId").takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    private fun toSummary(line: String): RuntimeRunReportSummary? {
        return runCatching {
            val json = JSONObject(line)
            val reportId = json.optString("reportId")
            val traceId = json.optString("traceId")
            val source = json.optString("source")
            val status = json.optString("status")
            if (reportId.isBlank() || traceId.isBlank() || source.isBlank() || status.isBlank()) {
                return@runCatching null
            }
            RuntimeRunReportSummary(
                reportId = reportId,
                traceId = traceId,
                source = source,
                taskId = json.optString("taskId").takeIf { it.isNotBlank() },
                taskName = json.optString("taskName").takeIf { it.isNotBlank() },
                status = status,
                errorCode = json.optString("errorCode").takeIf { it.isNotBlank() },
                message = json.optString("message").takeIf { it.isNotBlank() },
                stepCount = json.optInt("stepCount", 0),
                durationMs = json.optLong("durationMs", 0L),
                finishedAtEpochMs = json.optLong("finishedAtEpochMs", 0L),
            )
        }.getOrNull()
    }

    private companion object {
        const val FILE_NAME = "runtime_run_reports_v1.ndjson"
        const val MAX_REPORTS = 120
    }
}
