package com.ksxkq.cmm_clicker.feature.debug

import android.content.Context
import com.ksxkq.cmm_clicker.core.runtime.RuntimeRunReport
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
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

data class RuntimeRunReportDetail(
    val reportId: String,
    val traceId: String,
    val source: String,
    val taskId: String?,
    val taskName: String?,
    val dryRun: Boolean,
    val status: String,
    val stepCount: Int,
    val message: String?,
    val errorCode: String?,
    val startedAtEpochMs: Long,
    val finishedAtEpochMs: Long,
    val durationMs: Long,
    val validationIssues: List<RuntimeRunReportValidationIssue>,
    val events: List<RuntimeRunReportTraceEvent>,
    val rawJson: String,
)

data class RuntimeRunReportValidationIssue(
    val severity: String,
    val code: String,
    val message: String,
    val flowId: String?,
    val nodeId: String?,
    val edgeId: String?,
)

data class RuntimeRunReportTraceEvent(
    val step: Int,
    val flowId: String,
    val nodeId: String,
    val nodeKind: String,
    val phase: String,
    val message: String?,
    val details: Map<String, String>,
    val timeMillis: Long,
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

    suspend fun listLatestSummaries(
        limit: Int = 20,
        taskId: String? = null,
    ): List<RuntimeRunReportSummary> = mutex.withLock {
        val safeLimit = limit.coerceAtLeast(1)
        val taskIdFilter = taskId?.takeIf { it.isNotBlank() }
        return@withLock readLinesLocked()
            .asReversed()
            .mapNotNull { line -> toSummary(line) }
            .filter { summary ->
                taskIdFilter == null || summary.taskId == taskIdFilter
            }
            .take(safeLimit)
    }

    suspend fun findDetailByReportId(reportId: String): RuntimeRunReportDetail? = mutex.withLock {
        if (reportId.isBlank()) {
            return@withLock null
        }
        val raw = readLinesLocked()
            .asReversed()
            .firstOrNull { line -> extractReportId(line) == reportId }
            ?: return@withLock null
        toDetail(raw)
    }

    suspend fun deleteByReportId(reportId: String): Boolean = mutex.withLock {
        if (reportId.isBlank()) {
            return@withLock false
        }
        val existing = readLinesLocked()
        val filtered = existing.filterNot { line ->
            extractReportId(line) == reportId
        }
        if (filtered.size == existing.size) {
            return@withLock false
        }
        val payload = if (filtered.isEmpty()) "" else filtered.joinToString(separator = "\n", postfix = "\n")
        file.parentFile?.mkdirs()
        file.writeText(payload)
        return@withLock true
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

    private fun toDetail(line: String): RuntimeRunReportDetail? {
        return runCatching {
            val json = JSONObject(line)
            val reportId = json.optString("reportId")
            val traceId = json.optString("traceId")
            val source = json.optString("source")
            val status = json.optString("status")
            if (reportId.isBlank() || traceId.isBlank() || source.isBlank() || status.isBlank()) {
                return@runCatching null
            }
            RuntimeRunReportDetail(
                reportId = reportId,
                traceId = traceId,
                source = source,
                taskId = json.optNullableString("taskId"),
                taskName = json.optNullableString("taskName"),
                dryRun = json.optBoolean("dryRun", false),
                status = status,
                stepCount = json.optInt("stepCount", 0),
                message = json.optNullableString("message"),
                errorCode = json.optNullableString("errorCode"),
                startedAtEpochMs = json.optLong("startedAtEpochMs", 0L),
                finishedAtEpochMs = json.optLong("finishedAtEpochMs", 0L),
                durationMs = json.optLong("durationMs", 0L),
                validationIssues = json.optJSONArray("validationIssues")
                    .toValidationIssues(),
                events = json.optJSONArray("traceEvents")
                    .toTraceEvents(),
                rawJson = line,
            )
        }.getOrNull()
    }

    private fun JSONArray?.toValidationIssues(): List<RuntimeRunReportValidationIssue> {
        if (this == null) {
            return emptyList()
        }
        val issues = mutableListOf<RuntimeRunReportValidationIssue>()
        for (index in 0 until length()) {
            val item = optJSONObject(index) ?: continue
            val severity = item.optString("severity")
            val code = item.optString("code")
            val message = item.optString("message")
            if (severity.isBlank() || code.isBlank() || message.isBlank()) {
                continue
            }
            issues += RuntimeRunReportValidationIssue(
                severity = severity,
                code = code,
                message = message,
                flowId = item.optNullableString("flowId"),
                nodeId = item.optNullableString("nodeId"),
                edgeId = item.optNullableString("edgeId"),
            )
        }
        return issues
    }

    private fun JSONArray?.toTraceEvents(): List<RuntimeRunReportTraceEvent> {
        if (this == null) {
            return emptyList()
        }
        val events = mutableListOf<RuntimeRunReportTraceEvent>()
        for (index in 0 until length()) {
            val item = optJSONObject(index) ?: continue
            val flowId = item.optString("flowId")
            val nodeId = item.optString("nodeId")
            val nodeKind = item.optString("nodeKind")
            val phase = item.optString("phase")
            if (flowId.isBlank() || nodeId.isBlank() || nodeKind.isBlank() || phase.isBlank()) {
                continue
            }
            events += RuntimeRunReportTraceEvent(
                step = item.optInt("step", 0),
                flowId = flowId,
                nodeId = nodeId,
                nodeKind = nodeKind,
                phase = phase,
                message = item.optNullableString("message"),
                details = item.optJSONObject("details").toStringMap(),
                timeMillis = item.optLong("timeMillis", 0L),
            )
        }
        return events
    }

    private fun JSONObject?.toStringMap(): Map<String, String> {
        if (this == null) {
            return emptyMap()
        }
        val result = linkedMapOf<String, String>()
        val keys = keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val value = optString(key)
            if (key.isNotBlank() && value.isNotBlank()) {
                result[key] = value
            }
        }
        return result
    }

    private fun JSONObject.optNullableString(key: String): String? {
        return optString(key).takeIf { it.isNotBlank() }
    }

    private companion object {
        const val FILE_NAME = "runtime_run_reports_v1.ndjson"
        const val MAX_REPORTS = 120
    }
}
