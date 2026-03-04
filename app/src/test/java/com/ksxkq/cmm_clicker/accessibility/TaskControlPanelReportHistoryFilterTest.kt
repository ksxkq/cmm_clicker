package com.ksxkq.cmm_clicker.accessibility

import com.ksxkq.cmm_clicker.feature.debug.RuntimeRunReportSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskControlPanelReportHistoryFilterTest {

    @Test
    fun `filterRuntimeReportHistory filters by status`() {
        val history = sampleHistory()

        val result = filterRuntimeReportHistory(
            history = history,
            searchQuery = "",
            statusFilter = ReportHistoryStatusFilterOption.FAILED,
            visibleCount = REPORT_HISTORY_PAGE_SIZE,
        )

        assertEquals(2, result.filteredHistory.size)
        assertTrue(result.filteredHistory.all { it.status == "FAILED" })
    }

    @Test
    fun `filterRuntimeReportHistory matches keyword across fields`() {
        val history = sampleHistory()

        val result = filterRuntimeReportHistory(
            history = history,
            searchQuery = "trace-3",
            statusFilter = ReportHistoryStatusFilterOption.ALL,
            visibleCount = REPORT_HISTORY_PAGE_SIZE,
        )

        assertEquals(1, result.filteredHistory.size)
        assertEquals("report-3", result.filteredHistory.first().reportId)
    }

    @Test
    fun `filterRuntimeReportHistory paginates visible items`() {
        val history = (1..25).map { index ->
            RuntimeRunReportSummary(
                reportId = "report-$index",
                traceId = "trace-$index",
                source = "control_panel_overlay",
                taskId = "task-a",
                taskName = "Task A",
                status = if (index % 2 == 0) "COMPLETED" else "FAILED",
                errorCode = null,
                message = "message-$index",
                stepCount = index,
                durationMs = index * 10L,
                finishedAtEpochMs = 1000L + index,
            )
        }

        val result = filterRuntimeReportHistory(
            history = history,
            searchQuery = "",
            statusFilter = ReportHistoryStatusFilterOption.ALL,
            visibleCount = 20,
            pageSize = 20,
        )

        assertEquals(25, result.filteredHistory.size)
        assertEquals(20, result.visibleHistory.size)
        assertTrue(result.canLoadMore)
    }

    @Test
    fun `filterRuntimeReportHistory no more when visible reaches filtered size`() {
        val history = sampleHistory()

        val result = filterRuntimeReportHistory(
            history = history,
            searchQuery = "",
            statusFilter = ReportHistoryStatusFilterOption.ALL,
            visibleCount = 99,
            pageSize = 20,
        )

        assertEquals(history.size, result.visibleHistory.size)
        assertFalse(result.canLoadMore)
    }

    private fun sampleHistory(): List<RuntimeRunReportSummary> {
        return listOf(
            RuntimeRunReportSummary(
                reportId = "report-1",
                traceId = "trace-1",
                source = "control_panel_overlay",
                taskId = "task-a",
                taskName = "Task A",
                status = "COMPLETED",
                errorCode = null,
                message = "ok",
                stepCount = 10,
                durationMs = 100L,
                finishedAtEpochMs = 1000L,
            ),
            RuntimeRunReportSummary(
                reportId = "report-2",
                traceId = "trace-2",
                source = "control_panel_overlay",
                taskId = "task-a",
                taskName = "Task A",
                status = "FAILED",
                errorCode = "E_ACTION",
                message = "action failed",
                stepCount = 3,
                durationMs = 80L,
                finishedAtEpochMs = 2000L,
            ),
            RuntimeRunReportSummary(
                reportId = "report-3",
                traceId = "trace-3",
                source = "main_activity",
                taskId = "task-b",
                taskName = "Task B",
                status = "FAILED",
                errorCode = "E_NODE",
                message = "node failed",
                stepCount = 2,
                durationMs = 50L,
                finishedAtEpochMs = 3000L,
            ),
        )
    }
}
