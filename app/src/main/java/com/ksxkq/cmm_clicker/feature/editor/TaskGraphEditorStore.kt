package com.ksxkq.cmm_clicker.feature.editor

import com.ksxkq.cmm_clicker.core.model.ActionType
import com.ksxkq.cmm_clicker.core.model.EdgeConditionType
import com.ksxkq.cmm_clicker.core.model.FlowEdge
import com.ksxkq.cmm_clicker.core.model.FlowGraphValidator
import com.ksxkq.cmm_clicker.core.model.FlowNode
import com.ksxkq.cmm_clicker.core.model.GraphValidationIssue
import com.ksxkq.cmm_clicker.core.model.NodeKind
import com.ksxkq.cmm_clicker.core.model.TaskBundle
import com.ksxkq.cmm_clicker.core.model.TaskFlow

data class TaskGraphEditorState(
    val bundle: TaskBundle,
    val selectedFlowId: String,
    val selectedNodeId: String?,
    val canUndo: Boolean,
    val canRedo: Boolean,
    val validationIssues: List<GraphValidationIssue>,
) {
    val selectedFlow: TaskFlow? = bundle.findFlow(selectedFlowId)
    val selectedNode: FlowNode? = selectedFlow?.findNode(selectedNodeId.orEmpty())
}

class TaskGraphEditorStore(
    initialBundle: TaskBundle,
    private val validator: FlowGraphValidator = FlowGraphValidator(),
) {
    private data class Snapshot(
        val bundle: TaskBundle,
        val selectedFlowId: String,
        val selectedNodeId: String?,
        val nextNodeSerial: Int,
        val nextEdgeSerial: Int,
    )

    private var bundle: TaskBundle = initialBundle
    private var selectedFlowId: String = initialBundle.findFlow(initialBundle.entryFlowId)?.flowId
        ?: initialBundle.flows.firstOrNull()?.flowId
        ?: ""
    private var selectedNodeId: String? = initialBundle.findFlow(selectedFlowId)?.entryNodeId
    private var nextNodeSerial: Int = detectNextNodeSerial(initialBundle)
    private var nextEdgeSerial: Int = detectNextEdgeSerial(initialBundle)
    private val undoStack = ArrayDeque<Snapshot>()
    private val redoStack = ArrayDeque<Snapshot>()

    fun state(): TaskGraphEditorState {
        return TaskGraphEditorState(
            bundle = bundle,
            selectedFlowId = selectedFlowId,
            selectedNodeId = selectedNodeId,
            canUndo = undoStack.isNotEmpty(),
            canRedo = redoStack.isNotEmpty(),
            validationIssues = validator.validate(bundle),
        )
    }

    fun reset(newBundle: TaskBundle) {
        bundle = newBundle
        selectedFlowId = newBundle.findFlow(newBundle.entryFlowId)?.flowId
            ?: newBundle.flows.firstOrNull()?.flowId
            ?: ""
        selectedNodeId = newBundle.findFlow(selectedFlowId)?.entryNodeId
        nextNodeSerial = detectNextNodeSerial(newBundle)
        nextEdgeSerial = detectNextEdgeSerial(newBundle)
        undoStack.clear()
        redoStack.clear()
    }

    fun selectFlow(flowId: String) {
        val flow = bundle.findFlow(flowId) ?: return
        selectedFlowId = flow.flowId
        if (flow.findNode(selectedNodeId.orEmpty()) == null) {
            selectedNodeId = flow.entryNodeId
        }
    }

    fun selectNode(nodeId: String) {
        val flow = bundle.findFlow(selectedFlowId) ?: return
        if (flow.findNode(nodeId) == null) {
            return
        }
        selectedNodeId = nodeId
    }

    fun renameBundle(name: String) {
        mutate {
            bundle = bundle.copy(name = name.ifBlank { bundle.name })
        }
    }

    fun addActionNode() {
        val flow = bundle.findFlow(selectedFlowId) ?: return
        val selectedIndex = flow.nodes.indexOfFirst { it.nodeId == selectedNodeId }.coerceAtLeast(0)
        val newId = generateNodeId(flow)
        val newNode = FlowNode(
            nodeId = newId,
            kind = NodeKind.ACTION,
            actionType = ActionType.CLICK,
            pluginId = "builtin.basic_gesture",
            params = mapOf(
                "x" to "0.5",
                "y" to "0.5",
                "durationMs" to "60",
            ),
        )
        mutate {
            val refreshedFlow = bundle.findFlow(selectedFlowId) ?: return@mutate
            val nodes = refreshedFlow.nodes.toMutableList()
            val insertAt = (selectedIndex + 1).coerceIn(0, nodes.size)
            nodes.add(insertAt, newNode)
            updateFlow(refreshedFlow.copy(nodes = nodes))
            selectedNodeId = newId
        }
    }

    fun removeSelectedNode(): Boolean {
        val flow = bundle.findFlow(selectedFlowId) ?: return false
        val nodeId = selectedNodeId ?: return false
        if (nodeId == flow.entryNodeId) {
            return false
        }
        if (flow.findNode(nodeId) == null) {
            return false
        }
        mutate {
            val refreshedFlow = bundle.findFlow(selectedFlowId) ?: return@mutate
            val updatedNodes = refreshedFlow.nodes.filterNot { it.nodeId == nodeId }
            val updatedEdges = refreshedFlow.edges.filterNot {
                it.fromNodeId == nodeId || it.toNodeId == nodeId
            }
            val fallbackNodeId = updatedNodes.firstOrNull()?.nodeId
            updateFlow(
                refreshedFlow.copy(
                    nodes = updatedNodes,
                    edges = updatedEdges,
                ),
            )
            selectedNodeId = fallbackNodeId
        }
        return true
    }

    fun moveSelectedNode(up: Boolean) {
        val flow = bundle.findFlow(selectedFlowId) ?: return
        val nodeId = selectedNodeId ?: return
        val index = flow.nodes.indexOfFirst { it.nodeId == nodeId }
        if (index < 0) {
            return
        }
        val target = if (up) index - 1 else index + 1
        if (target !in flow.nodes.indices) {
            return
        }
        mutate {
            val refreshedFlow = bundle.findFlow(selectedFlowId) ?: return@mutate
            val nodes = refreshedFlow.nodes.toMutableList()
            val node = nodes.removeAt(index)
            nodes.add(target, node)
            updateFlow(refreshedFlow.copy(nodes = nodes))
        }
    }

    fun updateSelectedNodeKind(kind: NodeKind) {
        if (kind == NodeKind.BRANCH) {
            updateSelectedNodeToBranch()
            return
        }
        updateSelectedNode { node ->
            when (kind) {
                NodeKind.ACTION -> {
                    val targetActionType = node.actionType ?: ActionType.CLICK
                    val nextParams = if (node.kind == NodeKind.ACTION) {
                        EditorParamSchemaRegistry.mergedParamsWithDefaults(
                            node.copy(actionType = targetActionType),
                        )
                    } else {
                        EditorParamSchemaRegistry.mergedParamsWithDefaults(
                            node.copy(
                                kind = NodeKind.ACTION,
                                actionType = targetActionType,
                                params = emptyMap(),
                            ),
                        )
                    }
                    node.copy(
                        kind = kind,
                        actionType = targetActionType,
                        pluginId = "builtin.basic_gesture",
                        params = nextParams,
                    )
                }

                NodeKind.JUMP,
                NodeKind.FOLDER_REF,
                NodeKind.SUB_TASK_REF,
                -> {
                    val flow = bundle.findFlow(selectedFlowId)
                    val fallbackTarget = flow?.entryNodeId ?: node.nodeId
                    val existingTargetFlowId = node.params["targetFlowId"]?.toString()?.takeIf { it.isNotBlank() }
                    val existingTargetNodeId = node.params["targetNodeId"]?.toString()?.takeIf { it.isNotBlank() }
                    node.copy(
                        kind = kind,
                        actionType = null,
                        pluginId = null,
                        params = mapOf(
                            "targetFlowId" to (existingTargetFlowId ?: selectedFlowId),
                            "targetNodeId" to (existingTargetNodeId ?: fallbackTarget),
                        ),
                    )
                }

                else -> node.copy(
                    kind = kind,
                    actionType = null,
                    pluginId = null,
                    params = emptyMap(),
                )
            }
        }
    }

    fun updateSelectedBranchVariableKey(variableKey: String) {
        updateSelectedNode { node ->
            if (node.kind != NodeKind.BRANCH) {
                node
            } else {
                node.copy(
                    params = node.params + ("variableKey" to variableKey.trim()),
                )
            }
        }
    }

    fun updateSelectedBranchTarget(
        conditionType: EdgeConditionType,
        targetNodeId: String,
    ) {
        if (conditionType != EdgeConditionType.TRUE && conditionType != EdgeConditionType.FALSE) {
            return
        }
        val flow = bundle.findFlow(selectedFlowId) ?: return
        val branchNodeId = selectedNodeId ?: return
        if (flow.findNode(branchNodeId)?.kind != NodeKind.BRANCH) {
            return
        }
        if (flow.findNode(targetNodeId) == null) {
            return
        }
        mutate {
            val refreshedFlow = bundle.findFlow(selectedFlowId) ?: return@mutate
            val cleanedEdges = refreshedFlow.edges.filterNot {
                it.fromNodeId == branchNodeId && it.conditionType == conditionType
            }
            val newEdge = FlowEdge(
                edgeId = generateEdgeId(refreshedFlow),
                fromNodeId = branchNodeId,
                toNodeId = targetNodeId,
                conditionType = conditionType,
                priority = 0,
            )
            updateFlow(
                refreshedFlow.copy(edges = cleanedEdges + newEdge),
            )
        }
    }

    fun updateSelectedNodeActionType(actionType: ActionType) {
        updateSelectedNode { node ->
            if (node.kind != NodeKind.ACTION) {
                node
            } else {
                val updated = node.copy(actionType = actionType)
                val previousFieldKeys = EditorParamSchemaRegistry.fieldsFor(node)
                    .map { it.key }
                    .toSet()
                val targetFieldKeys = EditorParamSchemaRegistry.fieldsFor(updated)
                    .map { it.key }
                    .toSet()
                val staleFieldKeys = previousFieldKeys - targetFieldKeys
                val mergedParams = EditorParamSchemaRegistry.mergedParamsWithDefaults(updated).toMutableMap()
                staleFieldKeys.forEach { staleKey ->
                    mergedParams.remove(staleKey)
                }
                updated.copy(
                    params = mergedParams,
                )
            }
        }
    }

    fun updateSelectedNodePluginId(pluginId: String) {
        updateSelectedNode { node ->
            if (node.kind != NodeKind.ACTION) {
                node
            } else {
                node.copy(pluginId = pluginId)
            }
        }
    }

    fun updateSelectedNodeEnabled(enabled: Boolean) {
        updateSelectedNode { node ->
            node.copy(flags = node.flags.copy(enabled = enabled))
        }
    }

    fun updateSelectedNodeActive(active: Boolean) {
        updateSelectedNode { node ->
            node.copy(flags = node.flags.copy(active = active))
        }
    }

    fun updateSelectedNodeParam(key: String, value: String) {
        updateSelectedNode { node ->
            val trimmed = value.trim()
            val nextParams = if (trimmed.isEmpty()) {
                node.params - key
            } else {
                node.params + (key to trimmed)
            }
            node.copy(params = nextParams)
        }
    }

    fun fillDefaultsForSelectedNode() {
        updateSelectedNode { node ->
            node.copy(params = EditorParamSchemaRegistry.mergedParamsWithDefaults(node))
        }
    }

    fun undo(): Boolean {
        val snapshot = undoStack.removeLastOrNull() ?: return false
        val current = snapshot()
        redoStack.addLast(current)
        restore(snapshot)
        return true
    }

    fun redo(): Boolean {
        val snapshot = redoStack.removeLastOrNull() ?: return false
        val current = snapshot()
        undoStack.addLast(current)
        restore(snapshot)
        return true
    }

    private fun updateSelectedNode(update: (FlowNode) -> FlowNode) {
        val flow = bundle.findFlow(selectedFlowId) ?: return
        val nodeId = selectedNodeId ?: return
        val node = flow.findNode(nodeId) ?: return
        mutate {
            val refreshedFlow = bundle.findFlow(selectedFlowId) ?: return@mutate
            val refreshedNode = refreshedFlow.findNode(node.nodeId) ?: return@mutate
            val updatedNode = update(refreshedNode)
            val nodes = refreshedFlow.nodes.map {
                if (it.nodeId == refreshedNode.nodeId) {
                    updatedNode
                } else {
                    it
                }
            }
            updateFlow(refreshedFlow.copy(nodes = nodes))
        }
    }

    private fun mutate(block: () -> Unit) {
        undoStack.addLast(snapshot())
        redoStack.clear()
        block()
    }

    private fun snapshot(): Snapshot {
        return Snapshot(
            bundle = bundle,
            selectedFlowId = selectedFlowId,
            selectedNodeId = selectedNodeId,
            nextNodeSerial = nextNodeSerial,
            nextEdgeSerial = nextEdgeSerial,
        )
    }

    private fun restore(snapshot: Snapshot) {
        bundle = snapshot.bundle
        selectedFlowId = snapshot.selectedFlowId
        selectedNodeId = snapshot.selectedNodeId
        nextNodeSerial = snapshot.nextNodeSerial
        nextEdgeSerial = snapshot.nextEdgeSerial
    }

    private fun updateFlow(updatedFlow: TaskFlow) {
        bundle = bundle.copy(
            flows = bundle.flows.map { flow ->
                if (flow.flowId == updatedFlow.flowId) {
                    updatedFlow
                } else {
                    flow
                }
            },
        )
    }

    private fun generateNodeId(flow: TaskFlow): String {
        var candidate: String
        do {
            candidate = "action_${nextNodeSerial++}"
        } while (flow.findNode(candidate) != null)
        return candidate
    }

    private fun generateEdgeId(flow: TaskFlow): String {
        var candidate: String
        do {
            candidate = "edge_${nextEdgeSerial++}"
        } while (flow.edges.any { it.edgeId == candidate })
        return candidate
    }

    private fun detectNextNodeSerial(bundle: TaskBundle): Int {
        var max = 0
        bundle.flows.forEach { flow ->
            flow.nodes.forEach { node ->
                if (node.nodeId.startsWith("action_")) {
                    val suffix = node.nodeId.removePrefix("action_").toIntOrNull() ?: 0
                    if (suffix > max) {
                        max = suffix
                    }
                }
            }
        }
        return max + 1
    }

    private fun detectNextEdgeSerial(bundle: TaskBundle): Int {
        var max = 0
        bundle.flows.forEach { flow ->
            flow.edges.forEach { edge ->
                if (edge.edgeId.startsWith("edge_")) {
                    val suffix = edge.edgeId.removePrefix("edge_").toIntOrNull() ?: 0
                    if (suffix > max) {
                        max = suffix
                    }
                }
            }
        }
        return max + 1
    }

    private fun updateSelectedNodeToBranch() {
        val flow = bundle.findFlow(selectedFlowId) ?: return
        val nodeId = selectedNodeId ?: return
        val node = flow.findNode(nodeId) ?: return

        mutate {
            val refreshedFlow = bundle.findFlow(selectedFlowId) ?: return@mutate
            val refreshedNode = refreshedFlow.findNode(nodeId) ?: return@mutate
            val updatedNode = refreshedNode.copy(
                kind = NodeKind.BRANCH,
                actionType = null,
                pluginId = null,
                params = buildMap {
                    put("variableKey", refreshedNode.params["variableKey"]?.toString() ?: "doSwipe")
                    refreshedNode.params["operator"]?.toString()?.takeIf { it.isNotBlank() }?.let { put("operator", it) }
                    refreshedNode.params["expectedValue"]?.toString()?.takeIf { it.isNotBlank() }?.let { put("expectedValue", it) }
                },
            )
            val updatedNodes = refreshedFlow.nodes.map {
                if (it.nodeId == refreshedNode.nodeId) updatedNode else it
            }

            val existingTrue = refreshedFlow.edges.firstOrNull {
                it.fromNodeId == refreshedNode.nodeId && it.conditionType == EdgeConditionType.TRUE
            }
            val existingFalse = refreshedFlow.edges.firstOrNull {
                it.fromNodeId == refreshedNode.nodeId && it.conditionType == EdgeConditionType.FALSE
            }

            val fallbackTarget = refreshedFlow.nodes
                .firstOrNull { it.nodeId != refreshedNode.nodeId }
                ?.nodeId
                ?: refreshedFlow.entryNodeId

            var nextEdges = refreshedFlow.edges.toMutableList()
            if (existingTrue == null) {
                nextEdges += FlowEdge(
                    edgeId = generateEdgeId(refreshedFlow),
                    fromNodeId = refreshedNode.nodeId,
                    toNodeId = fallbackTarget,
                    conditionType = EdgeConditionType.TRUE,
                    priority = 0,
                )
            }
            if (existingFalse == null) {
                nextEdges += FlowEdge(
                    edgeId = generateEdgeId(refreshedFlow),
                    fromNodeId = refreshedNode.nodeId,
                    toNodeId = fallbackTarget,
                    conditionType = EdgeConditionType.FALSE,
                    priority = 0,
                )
            }

            updateFlow(
                refreshedFlow.copy(
                    nodes = updatedNodes,
                    edges = nextEdges,
                ),
            )
        }
    }

}
