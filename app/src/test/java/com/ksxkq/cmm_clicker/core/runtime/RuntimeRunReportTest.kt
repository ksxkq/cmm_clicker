package com.ksxkq.cmm_clicker.core.runtime

import com.ksxkq.cmm_clicker.core.model.GraphValidationIssue
import com.ksxkq.cmm_clicker.core.model.NodeKind
import com.ksxkq.cmm_clicker.core.model.ValidationSeverity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeRunReportTest {
    @Test
    fun fromExecution_shouldExtractErrorCodeFromFailedMessage() {
        val result = RuntimeExecutionResult(
            status = RuntimeExecutionStatus.FAILED,
            traceId = "trace_failed",
            stepCount = 2,
            finalPointer = null,
            message = "jump_target_missing:main/end",
            validationIssues = listOf(
                GraphValidationIssue(
                    severity = ValidationSeverity.ERROR,
                    code = "jump_target_missing",
                    message = "target missing",
                    flowId = "main",
                    nodeId = "jump1",
                ),
            ),
            traceEvents = listOf(
                RuntimeTraceEvent(
                    traceId = "trace_failed",
                    step = 1,
                    flowId = "main",
                    nodeId = "jump1",
                    nodeKind = NodeKind.JUMP,
                    phase = RuntimeTracePhase.NODE_ERROR,
                    message = "jump_target_missing:main/end",
                    timeMillis = 1001L,
                ),
            ),
        )

        val report = RuntimeRunReport.fromExecution(
            source = "unit_test",
            taskId = "task_1",
            taskName = "测试任务",
            dryRun = false,
            startedAtEpochMs = 1000L,
            finishedAtEpochMs = 1200L,
            result = result,
        )

        assertEquals("jump_target_missing", report.errorCode)
        assertEquals(200L, report.durationMs)
        assertEquals(1, report.validationIssues.size)
        assertEquals(1, report.traceEvents.size)
    }

    @Test
    fun toJson_shouldContainCoreFieldsAndEscapedContent() {
        val report = RuntimeRunReport(
            reportId = "r1",
            traceId = "t1",
            source = "unit_test",
            taskId = "task_1",
            taskName = "任务\"A\"",
            dryRun = true,
            status = "COMPLETED",
            stepCount = 3,
            message = "done\nok",
            errorCode = null,
            startedAtEpochMs = 1L,
            finishedAtEpochMs = 4L,
            durationMs = 3L,
            validationIssues = emptyList(),
            traceEvents = listOf(
                RuntimeRunTraceEvent(
                    step = 1,
                    flowId = "main",
                    nodeId = "start",
                    nodeKind = "START",
                    phase = "NODE_START",
                    message = null,
                    details = mapOf("enabled" to "true"),
                    timeMillis = 1L,
                ),
            ),
        )

        val json = report.toJson()

        assertTrue(json.contains("\"reportId\":\"r1\""))
        assertTrue(json.contains("\"taskName\":\"任务\\\"A\\\"\""))
        assertTrue(json.contains("\"message\":\"done\\nok\""))
        assertTrue(json.contains("\"traceEventCount\":1"))
        assertTrue(json.contains("\"details\":{\"enabled\":\"true\"}"))
    }
}
