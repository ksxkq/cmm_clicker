package com.ksxkq.cmm_clicker.accessibility

import com.ksxkq.cmm_clicker.core.model.NodeKind
import com.ksxkq.cmm_clicker.core.runtime.RuntimeTraceEvent
import com.ksxkq.cmm_clicker.core.runtime.RuntimeTracePhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskControlPanelCurrentRunSessionStateTest {

    @Test
    fun `begin initializes current run session`() {
        val state = CurrentRunSessionState().begin(
            taskId = "task-1",
            taskName = "Task A",
            startedAtEpochMs = 1000L,
        )

        assertTrue(state.hasSession())
        assertEquals("task-1", state.taskId)
        assertEquals("Task A", state.taskName)
        assertEquals("RUNNING", state.status)
        assertEquals(0, state.stepCount)
        assertEquals(1000L, state.startedAtEpochMs)
        assertEquals(0L, state.finishedAtEpochMs)
        assertEquals("running", state.message)
        assertEquals("", state.errorCode)
        assertTrue(state.events.isEmpty())
    }

    @Test
    fun `updateFromTrace sets trace id once and updates step count`() {
        val first = RuntimeTraceEvent(
            traceId = "trace-1",
            step = 1,
            flowId = "flow-a",
            nodeId = "node-a",
            nodeKind = NodeKind.ACTION,
            phase = RuntimeTracePhase.NODE_START,
        )
        val second = RuntimeTraceEvent(
            traceId = "trace-2",
            step = 3,
            flowId = "flow-a",
            nodeId = "node-b",
            nodeKind = NodeKind.ACTION,
            phase = RuntimeTracePhase.NODE_END,
        )

        val state = CurrentRunSessionState()
            .begin(taskId = "task-1", taskName = "Task A", startedAtEpochMs = 1000L)
            .updateFromTrace(first)
            .updateFromTrace(second)

        assertEquals("trace-1", state.traceId)
        assertEquals(4, state.stepCount)
        assertEquals(2, state.events.size)
    }

    @Test
    fun `finalize keeps max step count and clamps finished time`() {
        val state = CurrentRunSessionState()
            .begin(taskId = "task-1", taskName = "Task A", startedAtEpochMs = 2000L)
            .updateFromTrace(
                RuntimeTraceEvent(
                    traceId = "trace-1",
                    step = 5,
                    flowId = "flow-a",
                    nodeId = "node-a",
                    nodeKind = NodeKind.ACTION,
                    phase = RuntimeTracePhase.NODE_END,
                ),
            )
            .finalize(
                status = "FAILED",
                message = "error",
                errorCode = "E_ACTION",
                stepCount = 2,
                finishedAtEpochMs = 1500L,
            )

        assertEquals("FAILED", state.status)
        assertEquals("error", state.message)
        assertEquals("E_ACTION", state.errorCode)
        assertEquals(6, state.stepCount)
        assertEquals(2000L, state.finishedAtEpochMs)
    }

    @Test
    fun `reset clears state`() {
        val state = CurrentRunSessionState()
            .begin(taskId = "task-1", taskName = "Task A", startedAtEpochMs = 1000L)
            .reset()

        assertFalse(state.hasSession())
        assertEquals("", state.taskId)
        assertEquals("", state.taskName)
        assertEquals("", state.status)
        assertEquals(0, state.stepCount)
        assertEquals(0L, state.startedAtEpochMs)
        assertEquals(0L, state.finishedAtEpochMs)
    }

    @Test
    fun `toHistorySnapshot returns null when no session exists`() {
        val snapshot = CurrentRunSessionState().toHistorySnapshot(
            runningPanelState = RunningPanelState(),
            running = false,
        )

        assertEquals(null, snapshot)
    }

    @Test
    fun `toHistorySnapshot applies running panel fallback fields`() {
        val snapshot = CurrentRunSessionState(
            traceId = "trace-1",
            taskId = "task-1",
            startedAtEpochMs = 1000L,
            finishedAtEpochMs = 0L,
            stepCount = 2,
        ).toHistorySnapshot(
            runningPanelState = RunningPanelState(
                taskName = "Task A",
                stepCount = 6,
                lastMessage = "执行中",
                lastErrorCode = "E_NODE",
            ),
            running = true,
        )

        assertEquals("Task A", snapshot?.taskName)
        assertEquals("RUNNING", snapshot?.status)
        assertEquals("执行中", snapshot?.message)
        assertEquals("E_NODE", snapshot?.errorCode)
        assertEquals(6, snapshot?.stepCount)
        assertEquals(null, snapshot?.finishedAtEpochMs)
    }
}
