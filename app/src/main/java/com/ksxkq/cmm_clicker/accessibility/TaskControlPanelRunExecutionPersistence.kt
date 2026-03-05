package com.ksxkq.cmm_clicker.accessibility

import com.ksxkq.cmm_clicker.core.runtime.RuntimeExecutionResult
import com.ksxkq.cmm_clicker.core.runtime.RuntimeRunReport

internal const val RUN_REPORT_SOURCE_CONTROL_PANEL = "control_panel_overlay"

internal data class RunExecutionPersistencePayload(
    val summary: String,
    val report: RuntimeRunReport,
)

internal fun buildRunExecutionPersistencePayload(
    taskId: String,
    taskName: String,
    result: RuntimeExecutionResult,
    startedAtEpochMs: Long,
    finishedAtEpochMs: Long,
): RunExecutionPersistencePayload {
    val summary = buildRunSummaryText(result)
    val report = RuntimeRunReport.fromExecution(
        source = RUN_REPORT_SOURCE_CONTROL_PANEL,
        taskId = taskId,
        taskName = taskName,
        dryRun = false,
        startedAtEpochMs = startedAtEpochMs,
        finishedAtEpochMs = finishedAtEpochMs,
        result = result,
    )
    return RunExecutionPersistencePayload(
        summary = summary,
        report = report,
    )
}

