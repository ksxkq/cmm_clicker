package com.ksxkq.cmm_clicker.accessibility

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ksxkq.cmm_clicker.core.model.ActionType
import com.ksxkq.cmm_clicker.core.model.FlowNode
import com.ksxkq.cmm_clicker.core.model.NodeKind
import com.ksxkq.cmm_clicker.core.model.TaskBundle
import com.ksxkq.cmm_clicker.feature.editor.EditorParamSchemaRegistry
import com.ksxkq.cmm_clicker.feature.editor.EditorParamValidator
import com.ksxkq.cmm_clicker.feature.editor.TaskGraphEditorStore
import com.ksxkq.cmm_clicker.feature.task.TaskRecord
import com.ksxkq.cmm_clicker.ui.AppDropdownMenu
import com.ksxkq.cmm_clicker.ui.AppDropdownMenuItem
import com.ksxkq.cmm_clicker.ui.CircleActionIconButton

@Composable
private fun rememberActionListUiState(
    taskId: String,
    flowId: String,
): ActionListUiState {
    return remember(taskId, flowId) {
        ActionListUiState()
    }
}

@Composable
private fun rememberJumpTargetPickerUiState(
    nodeId: String,
): JumpTargetPickerUiState {
    return remember(nodeId) {
        JumpTargetPickerUiState()
    }
}

@Composable
private fun rememberNodeEditorDraftUiState(
    nodeId: String,
): NodeEditorDraftUiState {
    return remember(nodeId) {
        NodeEditorDraftUiState()
    }
}

internal fun nodeSummaryText(node: FlowNode): String {
    return when (node.kind) {
        NodeKind.START -> "Start"
        NodeKind.END -> "End"
        NodeKind.ACTION -> "Action ${node.actionType?.name ?: "-"}"
        NodeKind.BRANCH -> "Branch"
        NodeKind.JUMP -> "Jump"
        NodeKind.FOLDER_REF -> "FolderRef"
        NodeKind.SUB_TASK_REF -> "SubTaskRef"
    }
}

internal fun jumpTargetSummaryText(
    node: FlowNode,
    bundle: TaskBundle,
    currentFlowId: String,
): String {
    val targetFlowId = node.params["targetFlowId"]?.toString()
        ?.takeIf { it.isNotBlank() }
        ?: currentFlowId
    val targetNodeId = node.params["targetNodeId"]?.toString().orEmpty()
    val flowName = bundle.findFlow(targetFlowId)?.name ?: targetFlowId
    return "跳转 -> $flowName/$targetNodeId"
}

internal fun nodeTimingText(node: FlowNode): String {
    val durationMs = when (node.kind) {
        NodeKind.ACTION -> parseLongParam(
            value = node.params["durationMs"],
            fallback = defaultActionDurationMs(node.actionType),
        )

        else -> parseLongParam(
            value = node.params["durationMs"],
            fallback = 0L,
        )
    }.coerceAtLeast(0L)
    val postDelayMs = parseLongParam(
        value = node.params["postDelayMs"],
        fallback = 0L,
    ).coerceAtLeast(0L)
    return "持续 ${durationMs}ms | 延迟 ${postDelayMs}ms"
}

internal fun parseDoubleParam(value: Any?, fallback: Double): Double {
    val parsed = when (value) {
        is Number -> value.toDouble()
        is String -> value.toDoubleOrNull()
        else -> null
    }
    return parsed ?: fallback
}

internal fun parseLongParam(value: Any?, fallback: Long): Long {
    val parsed = when (value) {
        is Number -> value.toLong()
        is String -> value.toLongOrNull()
        else -> null
    }
    return parsed ?: fallback
}

private fun defaultActionDurationMs(actionType: ActionType?): Long {
    return when (actionType) {
        ActionType.CLICK -> 60L
        ActionType.SWIPE -> 300L
        ActionType.RECORD -> 400L
        ActionType.DUP_CLICK -> 50L
        else -> 0L
    }
}

@Composable
internal fun TaskControlSettingsNodeEditorPage(
    store: TaskGraphEditorStore?,
    nodeId: String,
    currentScreenSizePx: () -> Pair<Int, Int>,
    mutateSettingsEditor: (String, (TaskGraphEditorStore) -> Unit) -> Unit,
    openClickPositionPicker: (nodeId: String, xRatio: Double, yRatio: Double) -> Unit,
) {
    if (store == null) {
        Text("编辑器不可用，请返回重试")
        return
    }
    val state = store.state()
    if (state.selectedNode?.nodeId != nodeId && nodeId.isNotBlank()) {
        store.selectNode(nodeId)
    }
    val node = store.state().selectedNode
    if (node == null) {
        Text("节点不存在，请返回动作列表")
        return
    }
    val nodeEditorDraftUiState = rememberNodeEditorDraftUiState(nodeId = node.nodeId)
    Column(
        modifier = Modifier
            .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = "节点：${node.nodeId}",
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = "类型：${node.kind}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (node.kind == NodeKind.JUMP) {
            val resolvedTargetFlowId = resolveJumpTargetFlowId(
                node = node,
                currentFlowId = state.selectedFlowId,
            )
            val targetFlow = state.bundle.findFlow(resolvedTargetFlowId)
                ?: state.selectedFlow
            val selectableNodes = targetFlow?.nodes
                ?.filterNot { it.kind == NodeKind.START }
                .orEmpty()
            val resolvedTargetNodeId = resolveJumpTargetNodeId(
                node = node,
                selectableNodes = selectableNodes,
            )
            val targetNode = selectableNodes.firstOrNull { it.nodeId == resolvedTargetNodeId }
            val jumpTargetPickerUiState = rememberJumpTargetPickerUiState(nodeId = node.nodeId)

            Text(
                text = "跳转目标",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = "targetFlowId=目标流程，targetNodeId=该流程中的目标动作",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { jumpTargetPickerUiState.flowMenuExpanded = true },
                    ) {
                        Text(targetFlow?.name ?: resolvedTargetFlowId)
                    }
                    AppDropdownMenu(
                        expanded = jumpTargetPickerUiState.flowMenuExpanded,
                        onDismissRequest = { jumpTargetPickerUiState.flowMenuExpanded = false },
                    ) {
                        state.bundle.flows.forEach { flow ->
                            AppDropdownMenuItem(
                                text = "流程: ${flow.name}",
                                onClick = {
                                    jumpTargetPickerUiState.flowMenuExpanded = false
                                    mutateSettingsEditor("已更新跳转流程") {
                                        it.updateSelectedNodeParam("targetFlowId", flow.flowId)
                                        it.updateSelectedNodeParam(
                                            "targetNodeId",
                                            defaultJumpTargetNodeId(flow.nodes),
                                        )
                                    }
                                },
                            )
                        }
                    }
                }
                Box(modifier = Modifier.weight(1f)) {
                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { jumpTargetPickerUiState.nodeMenuExpanded = true },
                    ) {
                        Text(targetNode?.nodeId ?: "选择动作")
                    }
                    AppDropdownMenu(
                        expanded = jumpTargetPickerUiState.nodeMenuExpanded,
                        onDismissRequest = { jumpTargetPickerUiState.nodeMenuExpanded = false },
                    ) {
                        if (selectableNodes.isEmpty()) {
                            AppDropdownMenuItem(
                                text = "无可选动作",
                                onClick = { jumpTargetPickerUiState.nodeMenuExpanded = false },
                            )
                        } else {
                            selectableNodes.forEach { candidate ->
                                AppDropdownMenuItem(
                                    text = "${candidate.nodeId} · ${nodeSummaryText(candidate)}",
                                    onClick = {
                                        jumpTargetPickerUiState.nodeMenuExpanded = false
                                        mutateSettingsEditor("已更新跳转动作") {
                                            it.updateSelectedNodeParam(
                                                "targetFlowId",
                                                targetFlow?.flowId ?: resolvedTargetFlowId,
                                            )
                                            it.updateSelectedNodeParam("targetNodeId", candidate.nodeId)
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
        if (node.kind == NodeKind.ACTION) {
            Text(
                text = "动作类型：${node.actionType?.name ?: "-"}（创建后固定）",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (node.kind == NodeKind.ACTION && node.actionType == ActionType.CLICK) {
            val (screenWidth, screenHeight) = currentScreenSizePx()
            val xRatio = parseDoubleParam(node.params["x"], 0.5)
            val yRatio = parseDoubleParam(node.params["y"], 0.5)
            val xPxCurrent = ratioToPixel(
                ratio = xRatio,
                screenSizePx = screenWidth,
            )
            val yPxCurrent = ratioToPixel(
                ratio = yRatio,
                screenSizePx = screenHeight,
            )
            LaunchedEffect(node.nodeId, node.params["x"], node.params["y"], screenWidth, screenHeight) {
                nodeEditorDraftUiState.setClickInputs(
                    x = xPxCurrent.toString(),
                    y = yPxCurrent.toString(),
                )
            }
            Text(
                text = "点击坐标（像素）",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = nodeEditorDraftUiState.clickXInput,
                    onValueChange = { next ->
                        nodeEditorDraftUiState.updateClickXInput(next)
                        val ratio = pixelInputToRatioOrNull(
                            input = next,
                            screenSizePx = screenWidth,
                        ) ?: return@OutlinedTextField
                        mutateSettingsEditor("已更新参数 x") {
                            it.updateSelectedNodeParam("x", ratio.toString())
                        }
                    },
                    modifier = Modifier.weight(1f),
                    label = { Text("X(px)") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = nodeEditorDraftUiState.clickYInput,
                    onValueChange = { next ->
                        nodeEditorDraftUiState.updateClickYInput(next)
                        val ratio = pixelInputToRatioOrNull(
                            input = next,
                            screenSizePx = screenHeight,
                        ) ?: return@OutlinedTextField
                        mutateSettingsEditor("已更新参数 y") {
                            it.updateSelectedNodeParam("y", ratio.toString())
                        }
                    },
                    modifier = Modifier.weight(1f),
                    label = { Text("Y(px)") },
                    singleLine = true,
                )
            }
            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    openClickPositionPicker(
                        node.nodeId,
                        xRatio,
                        yRatio,
                    )
                },
            ) {
                Text("屏幕拖动调整点击位置")
            }
        }
        val paramDefinitions = EditorParamSchemaRegistry
            .fieldsFor(node)
            .associateBy { it.key }
        val paramsSnapshot = buildNodeEditorParamsSnapshot(node)
        LaunchedEffect(node.nodeId, paramsSnapshot.keys) {
            nodeEditorDraftUiState.retainParamDraftKeys(paramsSnapshot.keys)
        }
        val groupedParams = groupNodeEditorParams(paramsSnapshot)
        if (paramsSnapshot.isEmpty()) {
            Text(
                text = "当前节点没有参数",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            ParamEditorGroup.entries.forEach { group ->
                val entries = groupedParams[group].orEmpty()
                if (entries.isEmpty()) {
                    return@forEach
                }
                Text(
                    text = group.title,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        entries.forEach { (key, value) ->
                            val definition = paramDefinitions[key]
                            val rawValue = value?.toString().orEmpty()
                            LaunchedEffect(node.nodeId, key, rawValue) {
                                nodeEditorDraftUiState.setParamDraftFromModel(
                                    key = key,
                                    value = rawValue,
                                )
                            }
                            val draft = nodeEditorDraftUiState.paramDraftOr(
                                key = key,
                                fallback = rawValue,
                            )
                            val validationError = remember(definition, draft) {
                                EditorParamValidator.validate(
                                    definition = definition,
                                    value = draft,
                                )
                            }
                            OutlinedTextField(
                                value = draft,
                                onValueChange = { next ->
                                    nodeEditorDraftUiState.updateParamDraft(
                                        key = key,
                                        value = next,
                                    )
                                    val nextError = EditorParamValidator.validate(
                                        definition = definition,
                                        value = next,
                                    )
                                    if (nextError == null) {
                                        mutateSettingsEditor("已更新参数 $key") {
                                            it.updateSelectedNodeParam(key, next)
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text(definition?.label ?: key) },
                                supportingText = {
                                    when {
                                        !validationError.isNullOrBlank() -> Text(validationError)
                                        !definition?.helperText.isNullOrBlank() -> Text(definition?.helperText.orEmpty())
                                    }
                                },
                                isError = !validationError.isNullOrBlank(),
                                singleLine = true,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun TaskControlSettingsActionListPage(
    store: TaskGraphEditorStore?,
    task: TaskRecord?,
    maxVisibleJumpLanes: Int,
    persistSettingsEditor: () -> Unit,
    mutateSettingsEditor: (String, (TaskGraphEditorStore) -> Unit) -> Unit,
    openNodeEditor: (flowId: String, nodeId: String) -> Unit,
) {
    if (store == null || task == null) {
        Text(
            text = "任务加载失败，请返回任务列表重试",
            style = MaterialTheme.typography.bodySmall,
        )
        return
    }
    val state = store.state()
    val actionListUiState = rememberActionListUiState(
        taskId = task.taskId,
        flowId = state.selectedFlowId,
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box {
            OutlinedButton(
                onClick = { actionListUiState.addActionMenuExpanded = true },
            ) {
                Text("添加动作")
            }
            AppDropdownMenu(
                expanded = actionListUiState.addActionMenuExpanded,
                onDismissRequest = { actionListUiState.addActionMenuExpanded = false },
            ) {
                AddActionPreset.entries.forEach { preset ->
                    AppDropdownMenuItem(
                        text = preset.menuLabel,
                        onClick = {
                            actionListUiState.addActionMenuExpanded = false
                            mutateSettingsEditor(preset.successMessage) { editor ->
                                applyAddActionPreset(
                                    store = editor,
                                    preset = preset,
                                )
                            }
                        },
                    )
                }
            }
        }
        OutlinedButton(
            onClick = persistSettingsEditor,
        ) {
            Text("保存")
        }
    }
    Text(
        text = "任务：${task.name} | flow=${state.selectedFlowId}",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    val flow = state.selectedFlow
    if (flow == null) {
        Text("当前流程不存在")
        return
    }
    val editableNodes = flow.nodes.filterNot { node ->
        node.kind == NodeKind.START || node.kind == NodeKind.END
    }
    val pendingDeleteNode = editableNodes.firstOrNull { it.nodeId == actionListUiState.pendingDeleteNodeId }
    val rowAnchors = remember(task.taskId, state.selectedFlowId) { mutableStateMapOf<String, RowAnchor>() }
    LaunchedEffect(editableNodes.map { it.nodeId }) {
        val validIds = editableNodes.map { it.nodeId }.toSet()
        rowAnchors.keys.toList().forEach { key ->
            if (key !in validIds) {
                rowAnchors.remove(key)
            }
        }
    }
    val jumpLayout = remember(flow, editableNodes, maxVisibleJumpLanes) {
        buildJumpConnectionLayout(
            flow = flow,
            actionNodes = editableNodes,
            maxVisibleLanes = maxVisibleJumpLanes,
        )
    }
    val baseGutterEndPaddingDp = connectionGutterWidthDp(
        laneCount = maxVisibleJumpLanes,
    )
    Column(
        modifier = Modifier
            .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (editableNodes.isEmpty()) {
            Text(
                text = "当前流程暂无可编辑动作",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            if (jumpLayout.overflowCount > 0) {
                Text(
                    text = "连线较多，已折叠显示 +${jumpLayout.overflowCount}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    editableNodes.forEach { node ->
                        val disabled = !node.flags.enabled
                        Card(
                            onClick = { openNodeEditor(state.selectedFlowId, node.nodeId) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(end = baseGutterEndPaddingDp.dp)
                                .graphicsLayer(alpha = if (disabled) 0.68f else 1f)
                                .onGloballyPositioned { coordinates ->
                                    rowAnchors[node.nodeId] = RowAnchor(
                                        centerY = coordinates.positionInParent().y + coordinates.size.height / 2f,
                                        rightX = coordinates.positionInParent().x + coordinates.size.width,
                                    )
                                },
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        ) {
                            Box(modifier = Modifier.fillMaxWidth()) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp)
                                        .padding(end = 40.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = node.nodeId,
                                            style = MaterialTheme.typography.bodyMedium,
                                        )
                                        if (disabled) {
                                            Text(
                                                text = "已禁用",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                    Text(
                                        text = nodeSummaryText(node),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    if (
                                        node.kind == NodeKind.JUMP ||
                                        node.kind == NodeKind.FOLDER_REF ||
                                        node.kind == NodeKind.SUB_TASK_REF
                                    ) {
                                        Text(
                                            text = jumpTargetSummaryText(
                                                node = node,
                                                bundle = state.bundle,
                                                currentFlowId = state.selectedFlowId,
                                            ),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    Text(
                                        text = nodeTimingText(node),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(top = 8.dp, end = 8.dp),
                                ) {
                                    CircleActionIconButton(
                                        onClick = { actionListUiState.actionMenuNodeId = node.nodeId },
                                        icon = { tint ->
                                            Icon(
                                                imageVector = Icons.Rounded.MoreHoriz,
                                                contentDescription = "更多操作",
                                                tint = tint,
                                                modifier = Modifier.size(18.dp),
                                            )
                                        },
                                    )
                                    AppDropdownMenu(
                                        expanded = actionListUiState.actionMenuNodeId == node.nodeId,
                                        onDismissRequest = { actionListUiState.actionMenuNodeId = null },
                                    ) {
                                        AppDropdownMenuItem(
                                            text = "编辑",
                                            onClick = {
                                                actionListUiState.actionMenuNodeId = null
                                                openNodeEditor(state.selectedFlowId, node.nodeId)
                                            },
                                        )
                                        AppDropdownMenuItem(
                                            text = "复制",
                                            onClick = {
                                                actionListUiState.actionMenuNodeId = null
                                                mutateSettingsEditor("已复制动作") {
                                                    it.duplicateNode(node.nodeId)
                                                }
                                            },
                                        )
                                        AppDropdownMenuItem(
                                            text = if (disabled) "启用" else "禁用",
                                            onClick = {
                                                actionListUiState.actionMenuNodeId = null
                                                mutateSettingsEditor(if (disabled) "已启用动作" else "已禁用动作") {
                                                    it.updateNodeEnabled(
                                                        nodeId = node.nodeId,
                                                        enabled = disabled,
                                                    )
                                                }
                                            },
                                        )
                                        AppDropdownMenuItem(
                                            text = "删除",
                                            destructive = true,
                                            onClick = {
                                                actionListUiState.actionMenuNodeId = null
                                                actionListUiState.pendingDeleteNodeId = node.nodeId
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                if (jumpLayout.connections.isNotEmpty()) {
                    JumpConnectionCanvas(
                        rowAnchors = rowAnchors,
                        connections = jumpLayout.connections,
                        laneCount = jumpLayout.laneCount,
                        onConnectionTap = null,
                        modifier = Modifier.matchParentSize(),
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
    if (pendingDeleteNode != null) {
        AlertDialog(
            onDismissRequest = { actionListUiState.pendingDeleteNodeId = null },
            title = { Text("删除动作") },
            text = {
                Text("确认删除动作 ${pendingDeleteNode.nodeId} 吗？")
            },
            confirmButton = {
                OutlinedButton(
                    onClick = {
                        val nodeId = pendingDeleteNode.nodeId
                        actionListUiState.pendingDeleteNodeId = null
                        mutateSettingsEditor("已删除动作") {
                            it.removeNode(nodeId)
                        }
                    },
                ) {
                    Text("确认删除")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { actionListUiState.pendingDeleteNodeId = null }) {
                    Text("取消")
                }
            },
        )
    }
}
