package com.ksxkq.cmm_clicker.accessibility

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ksxkq.cmm_clicker.core.runtime.RuntimeTraceEvent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal data class TaskControlRunHistorySnapshot(
    val traceId: String,
    val taskId: String,
    val taskName: String,
    val status: String,
    val stepCount: Int,
    val startedAtEpochMs: Long,
    val finishedAtEpochMs: Long?,
    val message: String,
    val errorCode: String,
    val events: List<RuntimeTraceEvent>,
)

@Composable
internal fun TaskControlRunHistoryPage(
    snapshot: TaskControlRunHistorySnapshot?,
) {
    if (snapshot == null) {
        Text(
            text = "暂无本次执行记录，请先运行一次任务",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
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
                text = "任务=${snapshot.taskName.ifBlank { snapshot.taskId.ifBlank { "-" } }} | 状态=${snapshot.status.ifBlank { "-" }}",
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "step=${snapshot.stepCount} | 错误码=${snapshot.errorCode.ifBlank { "-" }}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "开始=${formatEpochMs(snapshot.startedAtEpochMs)} | 结束=${formatEpochMs(snapshot.finishedAtEpochMs)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "traceId=${snapshot.traceId.ifBlank { "-" }}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "摘要=${snapshot.message.ifBlank { "-" }}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }

    if (snapshot.events.isEmpty()) {
        Text(
            text = "本次执行暂未产生步骤事件",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }

    snapshot.events.forEach { event ->
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
