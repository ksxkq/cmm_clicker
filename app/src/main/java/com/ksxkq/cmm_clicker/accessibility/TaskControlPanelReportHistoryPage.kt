package com.ksxkq.cmm_clicker.accessibility

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
    statusMessage: String,
    onRefresh: () -> Unit,
    onOpenDetail: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    var pendingDeleteReportId by remember { mutableStateOf<String?>(null) }
    val pendingDeleteItem = history.firstOrNull { it.reportId == pendingDeleteReportId }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedButton(
            modifier = Modifier.weight(1f),
            onClick = onRefresh,
        ) {
            Text("刷新")
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
        Text(
            text = taskScopeLabel,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

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
    } else {
        history.forEach { item ->
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
                            onClick = { pendingDeleteReportId = item.reportId },
                        ) {
                            Text("删除")
                        }
                    }
                }
            }
        }
    }

    if (pendingDeleteItem != null) {
        AlertDialog(
            onDismissRequest = { pendingDeleteReportId = null },
            title = { Text("删除历史记录") },
            text = { Text("确认删除这条历史记录吗？\n${pendingDeleteItem.reportId}") },
            confirmButton = {
                OutlinedButton(
                    onClick = {
                        val reportId = pendingDeleteItem.reportId
                        pendingDeleteReportId = null
                        onDelete(reportId)
                    },
                ) {
                    Text("确认删除")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { pendingDeleteReportId = null }) {
                    Text("取消")
                }
            },
        )
    }
}

private fun formatEpochMs(value: Long?): String {
    val valid = value?.takeIf { it > 0L } ?: return "-"
    return runCatching {
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(valid))
    }.getOrElse { "-" }
}
