package com.ksxkq.cmm_clicker.accessibility

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ksxkq.cmm_clicker.feature.debug.RuntimeRunReportSummary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun TaskControlRuntimeReportHistoryPage(
    history: List<RuntimeRunReportSummary>,
    taskScopeLabel: String?,
    isTaskScoped: Boolean,
    statusMessage: String,
    onRefresh: () -> Unit,
    onClearTaskScope: () -> Unit,
    onOpenDetail: (String) -> Unit,
    onDeleteRequest: (String) -> Unit,
) {
    val stateResetKey = remember(taskScopeLabel, history.firstOrNull()?.reportId) {
        "${taskScopeLabel.orEmpty()}|${history.firstOrNull()?.reportId.orEmpty()}"
    }
    var searchQuery by rememberSaveable(stateResetKey) { mutableStateOf("") }
    var statusFilter by rememberSaveable(stateResetKey) { mutableStateOf(ReportHistoryStatusFilterOption.ALL.name) }
    var visibleCount by rememberSaveable(stateResetKey) { mutableIntStateOf(REPORT_HISTORY_PAGE_SIZE) }

    val activeStatusFilter = remember(statusFilter) {
        ReportHistoryStatusFilterOption.fromName(statusFilter)
    }
    val filterResult = remember(history, activeStatusFilter, searchQuery, visibleCount) {
        filterRuntimeReportHistory(
            history = history,
            searchQuery = searchQuery,
            statusFilter = activeStatusFilter,
            visibleCount = visibleCount,
            pageSize = REPORT_HISTORY_PAGE_SIZE,
        )
    }
    val filteredHistory = filterResult.filteredHistory
    val visibleHistory = filterResult.visibleHistory
    val canLoadMore = filterResult.canLoadMore

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedButton(
            modifier = Modifier.weight(1f),
            onClick = {
                visibleCount = REPORT_HISTORY_PAGE_SIZE
                onRefresh()
            },
        ) {
            Text("刷新")
        }
        OutlinedButton(
            modifier = Modifier.weight(1f),
            onClick = {
                searchQuery = ""
                statusFilter = ReportHistoryStatusFilterOption.ALL.name
                visibleCount = REPORT_HISTORY_PAGE_SIZE
            },
        ) {
            Text("清空筛选")
        }
    }

    OutlinedTextField(
        modifier = Modifier.fillMaxWidth(),
        value = searchQuery,
        onValueChange = { next ->
            searchQuery = next
            visibleCount = REPORT_HISTORY_PAGE_SIZE
        },
        singleLine = true,
        label = { Text("搜索任务/状态/错误码") },
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ReportHistoryStatusFilterOption.entries.forEach { option ->
            val selected = option == activeStatusFilter
            if (selected) {
                Button(
                    onClick = {},
                    enabled = false,
                ) {
                    Text(option.label)
                }
            } else {
                OutlinedButton(
                    onClick = {
                        statusFilter = option.name
                        visibleCount = REPORT_HISTORY_PAGE_SIZE
                    },
                ) {
                    Text(option.label)
                }
            }
        }
    }

    if (statusMessage.isNotBlank()) {
        Text(
            text = statusMessage,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (!taskScopeLabel.isNullOrBlank()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                modifier = Modifier.weight(1f),
                text = taskScopeLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (isTaskScoped) {
                OutlinedButton(onClick = onClearTaskScope) {
                    Text("查看全部")
                }
            }
        }
    }
    Text(
        text = "显示 ${visibleHistory.size} / ${filteredHistory.size} 条（总 ${history.size} 条）",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    if (history.isEmpty()) {
        Text(
            text = if (taskScopeLabel.isNullOrBlank()) {
                "暂无历史记录，请先执行一次任务"
            } else {
                "该任务暂无历史记录，请先执行一次任务"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else if (filteredHistory.isEmpty()) {
        Text(
            text = "没有匹配的历史记录，请调整搜索词或筛选条件",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        visibleHistory.forEach { item ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = "${item.status} | ${item.taskName ?: item.taskId ?: "-"}",
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "时间=${formatEpochMs(item.finishedAtEpochMs)} | 时长=${item.durationMs}ms | step=${item.stepCount}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "来源=${item.source} | 错误码=${item.errorCode ?: "-"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "摘要=${item.message ?: "-"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            modifier = Modifier.weight(2f),
                            onClick = { onOpenDetail(item.reportId) },
                        ) {
                            Text("详情")
                        }
                        OutlinedButton(
                            modifier = Modifier.weight(1f),
                            onClick = { onDeleteRequest(item.reportId) },
                        ) {
                            Text("删除")
                        }
                    }
                }
            }
        }
        if (canLoadMore) {
            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    visibleCount += REPORT_HISTORY_PAGE_SIZE
                },
            ) {
                Text("加载更多")
            }
        }
    }
}

private fun formatEpochMs(value: Long?): String {
    val valid = value?.takeIf { it > 0L } ?: return "-"
    return runCatching {
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(valid))
    }.getOrElse { "-" }
}
