package com.ksxkq.cmm_clicker.accessibility

import com.ksxkq.cmm_clicker.core.runtime.RuntimeExecutionResult
import com.ksxkq.cmm_clicker.core.runtime.RuntimeExecutionStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class TaskControlPanelRunExecutionPresentationTest {

    @Test
    fun `buildRunSummaryText renders fixed summary format`() {
        val result = RuntimeExecutionResult(
            status = RuntimeExecutionStatus.COMPLETED,
            traceId = "trace_1",
            stepCount = 6,
            finalPointer = null,
            message = "ok",
        )

        val summary = buildRunSummaryText(result)

        assertEquals("模式=REAL 状态=COMPLETED step=6 msg=ok", summary)
    }

    @Test
    fun `buildRunSummaryText falls back to dash when message missing`() {
        val result = RuntimeExecutionResult(
            status = RuntimeExecutionStatus.FAILED,
            traceId = "trace_2",
            stepCount = 2,
            finalPointer = null,
            message = null,
        )

        val summary = buildRunSummaryText(result)

        assertEquals("模式=REAL 状态=FAILED step=2 msg=-", summary)
    }

    @Test
    fun `buildRunStoppedUiModel returns stopped mapping`() {
        val uiModel = buildRunStoppedUiModel()

        assertEquals("用户已停止", uiModel.panelMessage)
        assertEquals(RuntimeExecutionStatus.STOPPED, uiModel.sessionStatus)
        assertEquals("user_requested_stop", uiModel.sessionMessage)
        assertEquals("", uiModel.sessionErrorCode)
        assertEquals("任务已停止", uiModel.statusText)
    }

    @Test
    fun `buildRunFailedUiModel maps unknown fallback`() {
        val uiModel = buildRunFailedUiModel(errorMessage = null)

        assertEquals("运行异常", uiModel.panelMessage)
        assertEquals(RuntimeExecutionStatus.FAILED, uiModel.sessionStatus)
        assertEquals("unknown", uiModel.sessionMessage)
        assertEquals("", uiModel.sessionErrorCode)
        assertEquals("运行失败: unknown", uiModel.statusText)
    }
}

