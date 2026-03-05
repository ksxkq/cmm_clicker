package com.ksxkq.cmm_clicker.accessibility

import com.ksxkq.cmm_clicker.core.runtime.RuntimeExecutionResult
import com.ksxkq.cmm_clicker.core.runtime.RuntimeExecutionStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class TaskControlPanelRunExecutionPersistenceTest {

    @Test
    fun `buildRunExecutionPersistencePayload builds summary and report`() {
        val result = RuntimeExecutionResult(
            status = RuntimeExecutionStatus.COMPLETED,
            traceId = "trace_persist",
            stepCount = 5,
            finalPointer = null,
            message = "done",
        )

        val payload = buildRunExecutionPersistencePayload(
            taskId = "task_1",
            taskName = "任务1",
            result = result,
            startedAtEpochMs = 1000L,
            finishedAtEpochMs = 1800L,
        )

        assertEquals("模式=REAL 状态=COMPLETED step=5 msg=done", payload.summary)
        assertEquals(RUN_REPORT_SOURCE_CONTROL_PANEL, payload.report.source)
        assertEquals("task_1", payload.report.taskId)
        assertEquals("任务1", payload.report.taskName)
        assertEquals("COMPLETED", payload.report.status)
        assertEquals(5, payload.report.stepCount)
        assertEquals(800L, payload.report.durationMs)
    }

    @Test
    fun `buildRunPostPersistStatusText keeps summary when all persisted`() {
        val text = buildRunPostPersistStatusText(
            summary = "模式=REAL 状态=COMPLETED step=5 msg=done",
            reportPersisted = true,
            taskRunInfoPersisted = true,
        )

        assertEquals("模式=REAL 状态=COMPLETED step=5 msg=done", text)
    }

    @Test
    fun `buildRunPostPersistStatusText appends warning when persist fails`() {
        val text = buildRunPostPersistStatusText(
            summary = "模式=REAL 状态=COMPLETED step=5 msg=done",
            reportPersisted = false,
            taskRunInfoPersisted = true,
        )

        assertEquals("模式=REAL 状态=COMPLETED step=5 msg=done（部分记录写入失败）", text)
    }
}
