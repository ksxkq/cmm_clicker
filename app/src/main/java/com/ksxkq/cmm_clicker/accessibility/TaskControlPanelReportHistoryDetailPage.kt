package com.ksxkq.cmm_clicker.accessibility

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ksxkq.cmm_clicker.feature.debug.RuntimeRunReportDetail
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun TaskControlRuntimeReportDetailPage(
    detail: RuntimeRunReportDetail?,
    statusMessage: String,
    canOpenPrev: Boolean,
    canOpenNext: Boolean,
    onOpenPrev: () -> Unit,
    onOpenNext: () -> Unit,
) {
    if (statusMessage.isNotBlank()) {
        Text(
            text = statusMessage,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    if (detail == null) {
        Text(
            text = "历史记录详情不存在，请返回列表重试",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedButton(
            modifier = Modifier.weight(1f),
            enabled = canOpenPrev,
            onClick = onOpenPrev,
        ) {
            Text("上一条")
        }
        OutlinedButton(
            modifier = Modifier.weight(1f),
            enabled = canOpenNext,
            onClick = onOpenNext,
        ) {
            Text("下一条")
        }
    }

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
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "任务=${detail.taskName ?: detail.taskId ?: "-"} | 状态=${detail.status}",
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "step=${detail.stepCount} | 错误码=${detail.errorCode ?: "-"} | dryRun=${detail.dryRun}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "开始=${formatEpochMs(detail.startedAtEpochMs)} | 结束=${formatEpochMs(detail.finishedAtEpochMs)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "traceId=${detail.traceId} | source=${detail.source}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "摘要=${detail.message ?: "-"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }

    if (detail.validationIssues.isNotEmpty()) {
        Text(
            text = "校验问题 (${detail.validationIssues.size})",
            style = MaterialTheme.typography.bodyMedium,
        )
        detail.validationIssues.forEach { issue ->
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
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = "${issue.severity} | ${issue.code}",
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = issue.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "flow=${issue.flowId ?: "-"} | node=${issue.nodeId ?: "-"} | edge=${issue.edgeId ?: "-"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }

    if (detail.events.isEmpty()) {
        Text(
            text = "该记录没有步骤事件",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }

    Text(
        text = "步骤事件 (${detail.events.size})",
        style = MaterialTheme.typography.bodyMedium,
    )
    detail.events.forEach { event ->
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
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "Step ${event.step + 1} | ${event.phase}",
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "节点=${event.flowId}/${event.nodeId} | kind=${event.nodeKind}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "消息=${event.message?.takeIf { it.isNotBlank() } ?: "-"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "详情=${event.details.toDebugSummary()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "时间=${formatEpochMs(event.timeMillis)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private fun Map<String, String>.toDebugSummary(): String {
    if (isEmpty()) {
        return "-"
    }
    val preferredKeys = listOf(
        "actionType",
        "outcome",
        "nextFlowId",
        "nextNodeId",
        "errorCode",
        "postDelayMs",
    )
    val preferred = preferredKeys.mapNotNull { key ->
        this[key]?.takeIf { it.isNotBlank() && it != "-" }?.let { value ->
            "$key=$value"
        }
    }
    if (preferred.isNotEmpty()) {
        return preferred.joinToString(" | ")
    }
    return entries
        .asSequence()
        .take(4)
        .joinToString(" | ") { entry -> "${entry.key}=${entry.value}" }
        .ifBlank { "-" }
}

private fun formatEpochMs(value: Long?): String {
    val valid = value?.takeIf { it > 0L } ?: return "-"
    return runCatching {
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(Date(valid))
    }.getOrElse { "-" }
}
