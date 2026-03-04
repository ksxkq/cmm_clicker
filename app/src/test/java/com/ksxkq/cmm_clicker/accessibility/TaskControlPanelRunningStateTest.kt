package com.ksxkq.cmm_clicker.accessibility

import com.ksxkq.cmm_clicker.core.model.NodeKind
import com.ksxkq.cmm_clicker.core.runtime.RuntimeExecutionResult
import com.ksxkq.cmm_clicker.core.runtime.RuntimeExecutionStatus
import com.ksxkq.cmm_clicker.core.runtime.RuntimeTraceEvent
import com.ksxkq.cmm_clicker.core.runtime.RuntimeTracePhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskControlPanelRunningStateTest {

    @Test
    fun `startWithTask initializes running snapshot`() {
        val state = RunningPanelState().startWithTask("")

        assertEquals("未命名任务", state.taskName)
        assertEquals(0, state.stepCount)
        assertEquals("-", state.currentFlowId)
        assertEquals("-", state.currentNodeId)
        assertEquals("等待执行...", state.lastMessage)
        assertEquals("", state.lastErrorCode)
        assertFalse(state.paused)
    }

    @Test
    fun `applyTrace updates step pointer and error code`() {
        val state = RunningPanelState(taskName = "Task A").applyTrace(
            RuntimeTraceEvent(
                traceId = "trace-1",
                step = 2,
                flowId = "flow-1",
                nodeId = "node-9",
                nodeKind = NodeKind.ACTION,
                phase = RuntimeTracePhase.NODE_ERROR,
                message = "failed",
                details = mapOf("errorCode" to "E_ACTION"),
            ),
        )

        assertEquals(3, state.stepCount)
        assertEquals("flow-1", state.currentFlowId)
        assertEquals("node-9", state.currentNodeId)
        assertEquals("failed", state.lastMessage)
        assertEquals("E_ACTION", state.lastErrorCode)
    }

    @Test
    fun `togglePause flips paused flag and message`() {
        val paused = RunningPanelState().togglePause()
        val resumed = paused.togglePause()

        assertTrue(paused.paused)
        assertEquals("任务已暂停", paused.lastMessage)
        assertFalse(resumed.paused)
        assertEquals("任务继续执行", resumed.lastMessage)
    }

    @Test
    fun `applyResult maps status and clears paused`() {
        val result = RuntimeExecutionResult(
            status = RuntimeExecutionStatus.COMPLETED,
            traceId = "trace-2",
            stepCount = 12,
            finalPointer = null,
            message = null,
            traceEvents = emptyList(),
        )
        val state = RunningPanelState(
            taskName = "Task B",
            stepCount = 8,
            paused = true,
        ).applyResult(result)

        assertEquals(12, state.stepCount)
        assertEquals("执行完成", state.lastMessage)
        assertFalse(state.paused)
    }
}
