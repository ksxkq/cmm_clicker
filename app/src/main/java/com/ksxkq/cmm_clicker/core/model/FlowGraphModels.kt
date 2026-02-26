package com.ksxkq.cmm_clicker.core.model

data class TaskBundle(
    val bundleId: String,
    val name: String,
    val schemaVersion: Int,
    val entryFlowId: String,
    val flows: List<TaskFlow>,
    val metadata: Map<String, String> = emptyMap(),
) {
    fun findFlow(flowId: String): TaskFlow? = flows.firstOrNull { it.flowId == flowId }

    fun findNode(pointer: NodePointer): FlowNode? {
        val flow = findFlow(pointer.flowId) ?: return null
        return flow.nodes.firstOrNull { it.nodeId == pointer.nodeId }
    }
}

data class TaskFlow(
    val flowId: String,
    val name: String,
    val entryNodeId: String,
    val nodes: List<FlowNode>,
    val edges: List<FlowEdge>,
) {
    fun findNode(nodeId: String): FlowNode? = nodes.firstOrNull { it.nodeId == nodeId }

    fun edgesFrom(nodeId: String): List<FlowEdge> = edges.filter { it.fromNodeId == nodeId }
}

data class FlowNode(
    val nodeId: String,
    val kind: NodeKind,
    val actionType: ActionType? = null,
    val pluginId: String? = null,
    val params: Map<String, Any?> = emptyMap(),
    val flags: NodeFlags = NodeFlags(),
)

data class FlowEdge(
    val edgeId: String,
    val fromNodeId: String,
    val toNodeId: String,
    val conditionType: EdgeConditionType = EdgeConditionType.ALWAYS,
    val conditionKey: String? = null,
    val priority: Int = 0,
)

data class NodeFlags(
    val enabled: Boolean = true,
    val active: Boolean = true,
)

data class NodePointer(
    val flowId: String,
    val nodeId: String,
)

enum class NodeKind {
    START,
    END,
    ACTION,
    BRANCH,
    JUMP,
    FOLDER_REF,
    SUB_TASK_REF,
}

enum class EdgeConditionType {
    ALWAYS,
    TRUE,
    FALSE,
    MATCH_KEY,
}
