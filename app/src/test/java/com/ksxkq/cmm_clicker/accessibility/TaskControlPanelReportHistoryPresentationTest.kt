package com.ksxkq.cmm_clicker.accessibility

import com.ksxkq.cmm_clicker.feature.debug.RuntimeRunReportDetail
import com.ksxkq.cmm_clicker.feature.debug.RuntimeRunReportSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TaskControlPanelReportHistoryPresentationTest {

    @Test
    fun `buildRuntimeReportTaskScopeLabel returns null when task scope is empty`() {
        assertNull(buildRuntimeReportTaskScopeLabel(taskId = null, taskName = "Task A"))
        assertNull(buildRuntimeReportTaskScopeLabel(taskId = "", taskName = "Task A"))
    }

    @Test
    fun `buildRuntimeReportTaskScopeLabel prefers task name and falls back to task id`() {
        assertEquals(
            "仅显示任务：Task A",
            buildRuntimeReportTaskScopeLabel(taskId = "task-a", taskName = "Task A"),
        )
        assertEquals(
            "仅显示任务：task-a",
            buildRuntimeReportTaskScopeLabel(taskId = "task-a", taskName = ""),
        )
    }

    @Test
    fun `resolveCurrentRuntimeReportId prefers detail report id`() {
        val detail = RuntimeRunReportDetail(
            reportId = "report-from-detail",
            traceId = "trace-1",
            source = "control_panel_overlay",
            taskId = "task-a",
            taskName = "Task A",
            dryRun = false,
            status = "COMPLETED",
            stepCount = 5,
            message = "ok",
            errorCode = null,
            startedAtEpochMs = 1000L,
            finishedAtEpochMs = 1200L,
            durationMs = 200L,
            validationIssues = emptyList(),
            events = emptyList(),
            rawJson = "{}",
        )
        val route = SettingsRoute.ReportHistoryDetail(reportId = "report-from-route")

        assertEquals(
            "report-from-detail",
            resolveCurrentRuntimeReportId(detail = detail, route = route),
        )
    }

    @Test
    fun `computeRuntimeReportDetailPaging returns navigation flags`() {
        val history = listOf(
            reportSummary("report-1"),
            reportSummary("report-2"),
            reportSummary("report-3"),
        )

        val first = computeRuntimeReportDetailPaging(history, currentReportId = "report-1")
        val middle = computeRuntimeReportDetailPaging(history, currentReportId = "report-2")
        val missing = computeRuntimeReportDetailPaging(history, currentReportId = "missing")

        assertEquals(false, first.canOpenPrev)
        assertEquals(true, first.canOpenNext)
        assertEquals(true, middle.canOpenPrev)
        assertEquals(true, middle.canOpenNext)
        assertEquals(-1, missing.currentIndex)
        assertEquals(false, missing.canOpenPrev)
        assertEquals(false, missing.canOpenNext)
    }

    @Test
    fun `resolveAdjacentRuntimeReportId returns adjacent report ids`() {
        val history = listOf(
            reportSummary("report-1"),
            reportSummary("report-2"),
            reportSummary("report-3"),
        )

        assertEquals(
            "report-2",
            resolveAdjacentRuntimeReportId(
                history = history,
                currentReportId = "report-1",
                direction = 1,
            ),
        )
        assertEquals(
            "report-1",
            resolveAdjacentRuntimeReportId(
                history = history,
                currentReportId = "report-2",
                direction = -1,
            ),
        )
        assertNull(
            resolveAdjacentRuntimeReportId(
                history = history,
                currentReportId = "report-1",
                direction = -1,
            ),
        )
    }

    private fun reportSummary(reportId: String): RuntimeRunReportSummary {
        return RuntimeRunReportSummary(
            reportId = reportId,
            traceId = "trace-$reportId",
            source = "control_panel_overlay",
            taskId = "task-a",
            taskName = "Task A",
            status = "COMPLETED",
            errorCode = null,
            message = "ok",
            stepCount = 5,
            durationMs = 100L,
            finishedAtEpochMs = 1000L,
        )
    }
}
