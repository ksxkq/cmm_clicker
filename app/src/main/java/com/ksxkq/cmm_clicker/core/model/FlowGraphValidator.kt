package com.ksxkq.cmm_clicker.core.model

class FlowGraphValidator {
    fun validate(bundle: TaskBundle): List<GraphValidationIssue> {
        val issues = mutableListOf<GraphValidationIssue>()
        val flowIdSet = mutableSetOf<String>()
        for (flow in bundle.flows) {
            if (!flowIdSet.add(flow.flowId)) {
                issues += issue(
                    severity = ValidationSeverity.ERROR,
                    code = "duplicate_flow_id",
                    message = "Duplicate flowId: ${flow.flowId}",
                    flowId = flow.flowId,
                )
            }
        }

        val entryFlow = bundle.findFlow(bundle.entryFlowId)
        if (entryFlow == null) {
            issues += issue(
                severity = ValidationSeverity.ERROR,
                code = "entry_flow_missing",
                message = "Entry flow not found: ${bundle.entryFlowId}",
                flowId = bundle.entryFlowId,
            )
            return issues
        }

        bundle.flows.forEach { flow ->
            validateFlow(bundle, flow, issues)
        }
        return issues
    }

    private fun validateFlow(
        bundle: TaskBundle,
        flow: TaskFlow,
        issues: MutableList<GraphValidationIssue>,
    ) {
        val nodeIdSet = mutableSetOf<String>()
        flow.nodes.forEach { node ->
            if (!nodeIdSet.add(node.nodeId)) {
                issues += issue(
                    severity = ValidationSeverity.ERROR,
                    code = "duplicate_node_id",
                    message = "Duplicate nodeId in flow ${flow.flowId}: ${node.nodeId}",
                    flowId = flow.flowId,
                    nodeId = node.nodeId,
                )
            }
        }

        if (flow.findNode(flow.entryNodeId) == null) {
            issues += issue(
                severity = ValidationSeverity.ERROR,
                code = "entry_node_missing",
                message = "Entry node not found: ${flow.entryNodeId}",
                flowId = flow.flowId,
                nodeId = flow.entryNodeId,
            )
        }

        flow.edges.forEach { edge ->
            if (flow.findNode(edge.fromNodeId) == null) {
                issues += issue(
                    severity = ValidationSeverity.ERROR,
                    code = "edge_from_missing",
                    message = "Edge fromNode missing: ${edge.fromNodeId}",
                    flowId = flow.flowId,
                    edgeId = edge.edgeId,
                    nodeId = edge.fromNodeId,
                )
            }
            if (flow.findNode(edge.toNodeId) == null) {
                issues += issue(
                    severity = ValidationSeverity.ERROR,
                    code = "edge_to_missing",
                    message = "Edge toNode missing: ${edge.toNodeId}",
                    flowId = flow.flowId,
                    edgeId = edge.edgeId,
                    nodeId = edge.toNodeId,
                )
            }
        }

        flow.nodes.forEach { node ->
            validateNode(bundle, flow, node, issues)
        }
    }

    private fun validateNode(
        bundle: TaskBundle,
        flow: TaskFlow,
        node: FlowNode,
        issues: MutableList<GraphValidationIssue>,
    ) {
        when (node.kind) {
            NodeKind.ACTION -> {
                if (node.actionType == null) {
                    issues += issue(
                        severity = ValidationSeverity.ERROR,
                        code = "action_type_missing",
                        message = "Action node missing actionType",
                        flowId = flow.flowId,
                        nodeId = node.nodeId,
                    )
                }
            }

            NodeKind.BRANCH -> {
                val edges = flow.edgesFrom(node.nodeId)
                val hasTrueEdge = edges.any { it.conditionType == EdgeConditionType.TRUE }
                val hasFalseEdge = edges.any { it.conditionType == EdgeConditionType.FALSE }
                if (!hasTrueEdge || !hasFalseEdge) {
                    issues += issue(
                        severity = ValidationSeverity.WARNING,
                        code = "branch_edge_incomplete",
                        message = "Branch node should provide both TRUE and FALSE edges",
                        flowId = flow.flowId,
                        nodeId = node.nodeId,
                    )
                }
            }

            NodeKind.JUMP,
            NodeKind.FOLDER_REF,
            NodeKind.SUB_TASK_REF,
            -> {
                val target = parseTargetPointer(node, flow.flowId)
                if (target == null) {
                    issues += issue(
                        severity = ValidationSeverity.ERROR,
                        code = "jump_target_missing",
                        message = "Target pointer missing. Expect params.targetNodeId",
                        flowId = flow.flowId,
                        nodeId = node.nodeId,
                    )
                } else {
                    val targetFlow = bundle.findFlow(target.flowId)
                    if (targetFlow == null || targetFlow.findNode(target.nodeId) == null) {
                        issues += issue(
                            severity = ValidationSeverity.ERROR,
                            code = "jump_target_invalid",
                            message = "Target pointer not found: ${target.flowId}/${target.nodeId}",
                            flowId = flow.flowId,
                            nodeId = node.nodeId,
                        )
                    }
                }
            }

            NodeKind.START,
            NodeKind.END,
            -> Unit
        }
    }

    private fun parseTargetPointer(node: FlowNode, defaultFlowId: String): NodePointer? {
        val targetNodeId = node.params["targetNodeId"]?.toString()?.trim().orEmpty()
        val targetFlowId = node.params["targetFlowId"]?.toString()?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: defaultFlowId
        if (targetFlowId.isBlank() || targetNodeId.isBlank()) {
            return null
        }
        return NodePointer(flowId = targetFlowId, nodeId = targetNodeId)
    }

    private fun issue(
        severity: ValidationSeverity,
        code: String,
        message: String,
        flowId: String? = null,
        nodeId: String? = null,
        edgeId: String? = null,
    ): GraphValidationIssue {
        return GraphValidationIssue(
            severity = severity,
            code = code,
            message = message,
            flowId = flowId,
            nodeId = nodeId,
            edgeId = edgeId,
        )
    }
}

data class GraphValidationIssue(
    val severity: ValidationSeverity,
    val code: String,
    val message: String,
    val flowId: String? = null,
    val nodeId: String? = null,
    val edgeId: String? = null,
)

enum class ValidationSeverity {
    WARNING,
    ERROR,
}
