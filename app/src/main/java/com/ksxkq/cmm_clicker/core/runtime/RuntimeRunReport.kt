package com.ksxkq.cmm_clicker.core.runtime

import com.ksxkq.cmm_clicker.core.model.GraphValidationIssue

data class RuntimeRunReport(
    val schemaVersion: Int = 1,
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
    val validationIssues: List<RuntimeRunValidationIssue>,
    val traceEvents: List<RuntimeRunTraceEvent>,
) {
    fun toJson(): String {
        return RuntimeJsonEncoder.encodeObject(toMap())
    }

    private fun toMap(): Map<String, Any?> {
        return linkedMapOf(
            "schemaVersion" to schemaVersion,
            "reportId" to reportId,
            "traceId" to traceId,
            "source" to source,
            "taskId" to taskId,
            "taskName" to taskName,
            "dryRun" to dryRun,
            "status" to status,
            "stepCount" to stepCount,
            "message" to message,
            "errorCode" to errorCode,
            "startedAtEpochMs" to startedAtEpochMs,
            "finishedAtEpochMs" to finishedAtEpochMs,
            "durationMs" to durationMs,
            "validationIssueCount" to validationIssues.size,
            "traceEventCount" to traceEvents.size,
            "validationIssues" to validationIssues.map { issue -> issue.toMap() },
            "traceEvents" to traceEvents.map { event -> event.toMap() },
        )
    }

    companion object {
        fun fromExecution(
            source: String,
            taskId: String?,
            taskName: String?,
            dryRun: Boolean,
            startedAtEpochMs: Long,
            finishedAtEpochMs: Long,
            result: RuntimeExecutionResult,
        ): RuntimeRunReport {
            val safeFinishedAt = finishedAtEpochMs.coerceAtLeast(startedAtEpochMs)
            val derivedErrorCode = inferErrorCode(result)
            return RuntimeRunReport(
                reportId = "${result.traceId}_$safeFinishedAt",
                traceId = result.traceId,
                source = source,
                taskId = taskId,
                taskName = taskName,
                dryRun = dryRun,
                status = result.status.name,
                stepCount = result.stepCount,
                message = result.message,
                errorCode = derivedErrorCode,
                startedAtEpochMs = startedAtEpochMs,
                finishedAtEpochMs = safeFinishedAt,
                durationMs = safeFinishedAt - startedAtEpochMs,
                validationIssues = result.validationIssues.map { it.toReportIssue() },
                traceEvents = result.traceEvents.map { it.toReportEvent() },
            )
        }

        private fun inferErrorCode(result: RuntimeExecutionResult): String? {
            if (result.status != RuntimeExecutionStatus.FAILED) {
                return null
            }
            val raw = result.message?.trim().orEmpty()
            if (raw.isBlank()) {
                return null
            }
            val primary = raw.substringBefore(':').substringBefore('|').trim()
            return primary.ifBlank { raw }
        }
    }
}

data class RuntimeRunValidationIssue(
    val severity: String,
    val code: String,
    val message: String,
    val flowId: String?,
    val nodeId: String?,
    val edgeId: String?,
) {
    fun toMap(): Map<String, Any?> {
        return linkedMapOf(
            "severity" to severity,
            "code" to code,
            "message" to message,
            "flowId" to flowId,
            "nodeId" to nodeId,
            "edgeId" to edgeId,
        )
    }
}

data class RuntimeRunTraceEvent(
    val step: Int,
    val flowId: String,
    val nodeId: String,
    val nodeKind: String,
    val phase: String,
    val message: String?,
    val details: Map<String, String> = emptyMap(),
    val timeMillis: Long,
) {
    fun toMap(): Map<String, Any?> {
        return linkedMapOf(
            "step" to step,
            "flowId" to flowId,
            "nodeId" to nodeId,
            "nodeKind" to nodeKind,
            "phase" to phase,
            "message" to message,
            "details" to details,
            "timeMillis" to timeMillis,
        )
    }
}

private fun GraphValidationIssue.toReportIssue(): RuntimeRunValidationIssue {
    return RuntimeRunValidationIssue(
        severity = severity.name,
        code = code,
        message = message,
        flowId = flowId,
        nodeId = nodeId,
        edgeId = edgeId,
    )
}

private fun RuntimeTraceEvent.toReportEvent(): RuntimeRunTraceEvent {
    return RuntimeRunTraceEvent(
        step = step,
        flowId = flowId,
        nodeId = nodeId,
        nodeKind = nodeKind.name,
        phase = phase.name,
        message = message,
        details = details,
        timeMillis = timeMillis,
    )
}

private object RuntimeJsonEncoder {
    fun encodeObject(map: Map<String, Any?>): String {
        return buildString {
            append('{')
            var first = true
            map.forEach { (key, value) ->
                if (!first) {
                    append(',')
                }
                first = false
                appendQuoted(key)
                append(':')
                appendAny(value)
            }
            append('}')
        }
    }

    private fun StringBuilder.appendAny(value: Any?) {
        when (value) {
            null -> append("null")
            is String -> appendQuoted(value)
            is Number -> append(value)
            is Boolean -> append(value)
            is Enum<*> -> appendQuoted(value.name)
            is Map<*, *> -> {
                @Suppress("UNCHECKED_CAST")
                append(encodeObject(value as Map<String, Any?>))
            }
            is Iterable<*> -> {
                append('[')
                var first = true
                value.forEach { item ->
                    if (!first) {
                        append(',')
                    }
                    first = false
                    appendAny(item)
                }
                append(']')
            }
            else -> appendQuoted(value.toString())
        }
    }

    private fun StringBuilder.appendQuoted(raw: String) {
        append('"')
        raw.forEach { ch ->
            when (ch) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(ch)
            }
        }
        append('"')
    }
}
