package com.ksxkq.cmm_clicker.accessibility

import com.ksxkq.cmm_clicker.feature.debug.RuntimeRunReportSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskControlPanelSettingsModalTest {

    @Test
    fun `resolveSettingsModalAction maps confirm start actions`() {
        val modal = SettingsModal.ConfirmStartTask(
            taskId = "task-1",
            taskName = "Task A",
        )

        assertEquals(
            SettingsModalAction.Dismiss,
            resolveSettingsModalAction(modal, MODAL_ACTION_CANCEL),
        )
        assertEquals(
            SettingsModalAction.StartTask("task-1"),
            resolveSettingsModalAction(modal, MODAL_ACTION_CONFIRM),
        )
        assertNull(resolveSettingsModalAction(modal, "unknown"))
    }

    @Test
    fun `resolveSettingsModalAction maps confirm delete actions`() {
        val modal = SettingsModal.ConfirmDeleteRuntimeReport(reportId = "report-1")

        assertEquals(
            SettingsModalAction.Dismiss,
            resolveSettingsModalAction(modal, MODAL_ACTION_CANCEL),
        )
        assertEquals(
            SettingsModalAction.DeleteRuntimeReport("report-1"),
            resolveSettingsModalAction(modal, MODAL_ACTION_CONFIRM),
        )
        assertNull(resolveSettingsModalAction(modal, "unknown"))
    }

    @Test
    fun `resolveSettingsModalAction maps confirm delete task actions`() {
        val modal = SettingsModal.ConfirmDeleteTask(
            taskId = "task-1",
            taskName = "Task A",
        )

        assertEquals(
            SettingsModalAction.Dismiss,
            resolveSettingsModalAction(modal, MODAL_ACTION_CANCEL),
        )
        assertEquals(
            SettingsModalAction.DeleteTask("task-1"),
            resolveSettingsModalAction(modal, MODAL_ACTION_CONFIRM),
        )
        assertNull(resolveSettingsModalAction(modal, "unknown"))
    }

    @Test
    fun `resolveSettingsModalAction maps confirm delete action node actions`() {
        val modal = SettingsModal.ConfirmDeleteActionNode(
            flowId = "flow-1",
            nodeId = "node-1",
        )

        assertEquals(
            SettingsModalAction.Dismiss,
            resolveSettingsModalAction(modal, MODAL_ACTION_CANCEL),
        )
        assertEquals(
            SettingsModalAction.DeleteActionNode(flowId = "flow-1", nodeId = "node-1"),
            resolveSettingsModalAction(modal, MODAL_ACTION_CONFIRM),
        )
        assertNull(resolveSettingsModalAction(modal, "unknown"))
    }

    @Test
    fun `resolveSettingsModalAction closes feedback modals on any action`() {
        assertEquals(
            SettingsModalAction.Dismiss,
            resolveSettingsModalAction(
                SettingsModal.Success(title = "完成", message = "ok"),
                actionKey = "any",
            ),
        )
        assertEquals(
            SettingsModalAction.Dismiss,
            resolveSettingsModalAction(
                SettingsModal.Failure(title = "失败", message = "error"),
                actionKey = "any",
            ),
        )
    }

    @Test
    fun `buildSettingsModalModel returns delete model only when report exists`() {
        val history = listOf(
            RuntimeRunReportSummary(
                reportId = "report-1",
                traceId = "trace-1",
                source = "control_panel_overlay",
                taskId = "task-a",
                taskName = "Task A",
                status = "FAILED",
                errorCode = "E_ACTION",
                message = "failed",
                stepCount = 2,
                durationMs = 80L,
                finishedAtEpochMs = 1000L,
            ),
        )

        val deleteModel = buildSettingsModalModel(
            modal = SettingsModal.ConfirmDeleteRuntimeReport(reportId = "report-1"),
            runtimeReportHistory = history,
        )
        val missingModel = buildSettingsModalModel(
            modal = SettingsModal.ConfirmDeleteRuntimeReport(reportId = "missing"),
            runtimeReportHistory = history,
        )

        assertEquals("删除历史记录", deleteModel?.title)
        assertTrue(deleteModel?.message?.contains("report-1") == true)
        assertNull(missingModel)
    }

    @Test
    fun `buildSettingsModalModel maps confirm start to default modal`() {
        val model = buildSettingsModalModel(
            modal = SettingsModal.ConfirmStartTask(
                taskId = "task-1",
                taskName = "",
            ),
            runtimeReportHistory = emptyList(),
        )

        assertEquals("确认开始任务", model?.title)
        assertTrue(model?.message?.contains("未命名任务") == true)
        assertEquals(TaskControlModalTone.DEFAULT, model?.tone)
        assertFalse(model?.dismissOnBackdropTap ?: true)
        assertEquals(2, model?.actions?.size)
    }

    @Test
    fun `buildSettingsModalModel maps confirm delete task to warning modal`() {
        val model = buildSettingsModalModel(
            modal = SettingsModal.ConfirmDeleteTask(
                taskId = "task-1",
                taskName = "",
            ),
            runtimeReportHistory = emptyList(),
        )

        assertEquals("删除任务", model?.title)
        assertTrue(model?.message?.contains("未命名任务") == true)
        assertEquals(TaskControlModalTone.WARNING, model?.tone)
        assertTrue(model?.dismissOnBackdropTap == true)
        assertEquals(2, model?.actions?.size)
    }

    @Test
    fun `buildSettingsModalModel maps confirm delete action node to warning modal`() {
        val model = buildSettingsModalModel(
            modal = SettingsModal.ConfirmDeleteActionNode(
                flowId = "flow-1",
                nodeId = "node-1",
            ),
            runtimeReportHistory = emptyList(),
        )

        assertEquals("删除动作", model?.title)
        assertTrue(model?.message?.contains("node-1") == true)
        assertEquals(TaskControlModalTone.WARNING, model?.tone)
        assertTrue(model?.dismissOnBackdropTap == true)
        assertEquals(2, model?.actions?.size)
    }
}
