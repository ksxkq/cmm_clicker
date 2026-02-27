package com.ksxkq.cmm_clicker.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.ksxkq.cmm_clicker.feature.task.TaskRecord
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class TaskLibrarySegment(val label: String) {
    ALL("全部"),
    RECENT_RUN("最近运行"),
    RECENT_EDIT("最近编辑"),
}

@Composable
fun TaskTabScreen(
    tasks: List<TaskRecord>,
    running: Boolean,
    taskOperationMessage: String,
    onCreateTask: (String) -> Unit,
    onOpenTaskOverlay: (String) -> Unit,
    onRenameTask: (String, String) -> Unit,
    onDuplicateTask: (String) -> Unit,
    onDeleteTask: (String) -> Unit,
    onRunTask: (String) -> Unit,
) {
    var newTaskName by remember { mutableStateOf("") }
    var searchKeyword by remember { mutableStateOf("") }
    var segment by remember { mutableStateOf(TaskLibrarySegment.ALL) }
    var renameTaskId by remember { mutableStateOf<String?>(null) }
    var renameTaskName by remember { mutableStateOf("") }
    var pendingDeleteTaskId by remember { mutableStateOf<String?>(null) }
    var menuTaskId by remember { mutableStateOf<String?>(null) }
    val trimmedKeyword = searchKeyword.trim()
    val filteredTasks = remember(tasks, trimmedKeyword, segment) {
        val matchedByKeyword = tasks.filter { task ->
            trimmedKeyword.isBlank() ||
                task.name.contains(trimmedKeyword, ignoreCase = true) ||
                task.taskId.contains(trimmedKeyword, ignoreCase = true)
        }
        when (segment) {
            TaskLibrarySegment.ALL -> matchedByKeyword.sortedByDescending { it.updatedAtEpochMs }
            TaskLibrarySegment.RECENT_RUN -> matchedByKeyword
                .filter { (it.lastRunAtEpochMs ?: 0L) > 0L }
                .sortedByDescending { it.lastRunAtEpochMs ?: 0L }
            TaskLibrarySegment.RECENT_EDIT -> matchedByKeyword.sortedByDescending { it.updatedAtEpochMs }
        }
    }
    val renameCandidate = tasks.firstOrNull { it.taskId == renameTaskId }
    val deleteCandidate = tasks.firstOrNull { it.taskId == pendingDeleteTaskId }
    LaunchedEffect(renameCandidate?.taskId, renameCandidate?.name) {
        renameTaskName = renameCandidate?.name.orEmpty()
    }

    SectionCard(
        title = "快捷指令库",
        subtitle = if (trimmedKeyword.isBlank()) {
            "共 ${tasks.size} 个任务"
        } else {
            "匹配 ${filteredTasks.size} / ${tasks.size}"
        },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = newTaskName,
                onValueChange = { newTaskName = it },
                modifier = Modifier.weight(1f),
                label = { Text("新任务名称") },
                singleLine = true,
            )
            Button(
                onClick = {
                    onCreateTask(newTaskName)
                    newTaskName = ""
                },
            ) {
                Text("新建")
            }
        }
        OutlinedTextField(
            value = searchKeyword,
            onValueChange = { searchKeyword = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("搜索快捷指令") },
            singleLine = true,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TaskLibrarySegment.entries.forEach { item ->
                if (item == segment) {
                    Button(onClick = { segment = item }) {
                        Text(item.label)
                    }
                } else {
                    OutlinedButton(onClick = { segment = item }) {
                        Text(item.label)
                    }
                }
            }
        }

        if (filteredTasks.isEmpty()) {
            Text(
                text = if (tasks.isEmpty()) {
                    "暂无任务，请先新建"
                } else {
                    "没有匹配任务，请调整搜索条件"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            filteredTasks.forEach { task ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenTaskOverlay(task.taskId) },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outline,
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                ) {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                                .padding(bottom = 44.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text(
                                text = task.name,
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(end = 44.dp),
                            )
                            Text(
                                text = "更新时间: ${formatEpochMs(task.updatedAtEpochMs)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = "最近运行: ${formatEpochMs(task.lastRunAtEpochMs)} | ${task.lastRunStatus ?: "-"} | ${task.lastRunSummary ?: "-"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(12.dp),
                        ) {
                            CircleActionIconButton(
                                onClick = { menuTaskId = task.taskId },
                                icon = { tint ->
                                    Icon(
                                        imageVector = Icons.Rounded.MoreHoriz,
                                        contentDescription = "更多",
                                        tint = tint,
                                        modifier = Modifier.size(18.dp),
                                    )
                                },
                            )
                            AppDropdownMenu(
                                expanded = menuTaskId == task.taskId,
                                onDismissRequest = { menuTaskId = null },
                            ) {
                                AppDropdownMenuItem(
                                    text = "复制",
                                    onClick = {
                                        menuTaskId = null
                                        onDuplicateTask(task.taskId)
                                    },
                                )
                                AppDropdownMenuItem(
                                    text = "重命名",
                                    onClick = {
                                        menuTaskId = null
                                        renameTaskId = task.taskId
                                        renameTaskName = task.name
                                    },
                                )
                                AppDropdownMenuItem(
                                    text = "删除",
                                    destructive = true,
                                    onClick = {
                                        menuTaskId = null
                                        pendingDeleteTaskId = task.taskId
                                    },
                                )
                            }
                        }

                        CircleActionIconButton(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(12.dp),
                            enabled = !running,
                            filled = true,
                            onClick = { onRunTask(task.taskId) },
                            icon = { tint ->
                                Icon(
                                    imageVector = Icons.Rounded.PlayArrow,
                                    contentDescription = "运行",
                                    tint = tint,
                                    modifier = Modifier.size(16.dp),
                                )
                            },
                        )
                    }
                }
            }
        }
        if (taskOperationMessage.isNotBlank()) {
            Text(
                text = taskOperationMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (renameCandidate != null) {
        RenameTaskDialog(
            name = renameTaskName,
            onNameChange = { renameTaskName = it },
            onCancel = { renameTaskId = null },
            onConfirm = {
                onRenameTask(renameCandidate.taskId, renameTaskName)
                renameTaskId = null
            },
        )
    }
    if (deleteCandidate != null) {
        DeleteTaskConfirmDialog(
            taskName = deleteCandidate.name,
            onCancel = { pendingDeleteTaskId = null },
            onConfirm = {
                onDeleteTask(deleteCandidate.taskId)
                pendingDeleteTaskId = null
            },
        )
    }
}

@Composable
private fun DeleteTaskConfirmDialog(
    taskName: String,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    Dialog(onDismissRequest = onCancel) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "确认删除任务",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = "将删除任务：$taskName",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ActionButton(
                        text = "取消",
                        modifier = Modifier.weight(1f),
                        onClick = onCancel,
                    )
                    Button(
                        modifier = Modifier.weight(1f),
                        onClick = onConfirm,
                    ) {
                        Text("确认删除")
                    }
                }
            }
        }
    }
}

@Composable
private fun RenameTaskDialog(
    name: String,
    onNameChange: (String) -> Unit,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    Dialog(onDismissRequest = onCancel) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "重命名任务",
                    style = MaterialTheme.typography.titleMedium,
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = onNameChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("任务名称") },
                    singleLine = true,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ActionButton(
                        text = "取消",
                        modifier = Modifier.weight(1f),
                        onClick = onCancel,
                    )
                    Button(
                        modifier = Modifier.weight(1f),
                        onClick = onConfirm,
                    ) {
                        Text("保存")
                    }
                }
            }
        }
    }
}

private fun formatEpochMs(value: Long?): String {
    if (value == null || value <= 0L) {
        return "-"
    }
    return runCatching {
        SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault()).format(Date(value))
    }.getOrDefault("-")
}
