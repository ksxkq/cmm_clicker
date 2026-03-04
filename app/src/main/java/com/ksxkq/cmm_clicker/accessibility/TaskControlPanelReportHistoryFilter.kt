package com.ksxkq.cmm_clicker.accessibility

import com.ksxkq.cmm_clicker.feature.debug.RuntimeRunReportSummary

internal const val REPORT_HISTORY_PAGE_SIZE = 20

internal enum class ReportHistoryStatusFilterOption(
    val code: String?,
    val label: String,
) {
    ALL(code = null, label = "全部"),
    COMPLETED(code = "COMPLETED", label = "完成"),
    FAILED(code = "FAILED", label = "失败"),
    STOPPED(code = "STOPPED", label = "停止"),
    ;

    companion object {
        fun fromName(raw: String): ReportHistoryStatusFilterOption {
            return entries.firstOrNull { it.name == raw } ?: ALL
        }
    }
}

internal data class ReportHistoryFilterResult(
    val filteredHistory: List<RuntimeRunReportSummary>,
    val visibleHistory: List<RuntimeRunReportSummary>,
    val canLoadMore: Boolean,
)

internal fun filterRuntimeReportHistory(
    history: List<RuntimeRunReportSummary>,
    searchQuery: String,
    statusFilter: ReportHistoryStatusFilterOption,
    visibleCount: Int,
    pageSize: Int = REPORT_HISTORY_PAGE_SIZE,
): ReportHistoryFilterResult {
    val normalizedHistory = when (statusFilter) {
        ReportHistoryStatusFilterOption.ALL -> history
        else -> history.filter { item -> item.status == statusFilter.code }
    }
    val keyword = searchQuery.trim().lowercase()
    val filteredHistory = if (keyword.isBlank()) {
        normalizedHistory
    } else {
        normalizedHistory.filter { item -> item.matchesKeyword(keyword) }
    }
    val safePageSize = pageSize.coerceAtLeast(1)
    val safeVisibleCount = visibleCount.coerceAtLeast(safePageSize)
    val visibleHistory = filteredHistory.take(safeVisibleCount)
    return ReportHistoryFilterResult(
        filteredHistory = filteredHistory,
        visibleHistory = visibleHistory,
        canLoadMore = visibleHistory.size < filteredHistory.size,
    )
}

private fun RuntimeRunReportSummary.matchesKeyword(keyword: String): Boolean {
    return buildString {
        append(status)
        append(' ')
        append(taskName ?: taskId ?: "")
        append(' ')
        append(errorCode ?: "")
        append(' ')
        append(message ?: "")
        append(' ')
        append(source)
        append(' ')
        append(traceId)
        append(' ')
        append(reportId)
    }.lowercase().contains(keyword)
}
