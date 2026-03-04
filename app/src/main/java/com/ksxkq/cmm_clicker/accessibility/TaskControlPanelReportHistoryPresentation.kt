package com.ksxkq.cmm_clicker.accessibility

import com.ksxkq.cmm_clicker.feature.debug.RuntimeRunReportDetail
import com.ksxkq.cmm_clicker.feature.debug.RuntimeRunReportSummary

internal data class RuntimeReportDetailPaging(
    val currentIndex: Int = -1,
    val canOpenPrev: Boolean = false,
    val canOpenNext: Boolean = false,
)

internal fun buildRuntimeReportTaskScopeLabel(
    taskId: String?,
    taskName: String,
): String? {
    if (taskId.isNullOrBlank()) {
        return null
    }
    return "仅显示任务：${taskName.ifBlank { taskId }}"
}

internal fun isRuntimeReportTaskScoped(taskId: String?): Boolean = !taskId.isNullOrBlank()

internal fun resolveCurrentRuntimeReportId(
    detail: RuntimeRunReportDetail?,
    route: SettingsRoute,
): String? {
    return detail?.reportId ?: (route as? SettingsRoute.ReportHistoryDetail)?.reportId
}

internal fun computeRuntimeReportDetailPaging(
    history: List<RuntimeRunReportSummary>,
    currentReportId: String?,
): RuntimeReportDetailPaging {
    if (currentReportId.isNullOrBlank()) {
        return RuntimeReportDetailPaging()
    }
    val currentIndex = history.indexOfFirst { it.reportId == currentReportId }
    if (currentIndex < 0) {
        return RuntimeReportDetailPaging()
    }
    return RuntimeReportDetailPaging(
        currentIndex = currentIndex,
        canOpenPrev = currentIndex > 0,
        canOpenNext = currentIndex in 0 until history.lastIndex,
    )
}

internal fun resolveAdjacentRuntimeReportId(
    history: List<RuntimeRunReportSummary>,
    currentReportId: String?,
    direction: Int,
): String? {
    val paging = computeRuntimeReportDetailPaging(
        history = history,
        currentReportId = currentReportId,
    )
    if (paging.currentIndex < 0) {
        return null
    }
    val targetIndex = paging.currentIndex + direction
    if (targetIndex !in history.indices) {
        return null
    }
    return history[targetIndex].reportId
}
