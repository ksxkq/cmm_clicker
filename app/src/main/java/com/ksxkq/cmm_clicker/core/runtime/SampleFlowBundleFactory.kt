package com.ksxkq.cmm_clicker.core.runtime

import com.ksxkq.cmm_clicker.core.model.ActionType
import com.ksxkq.cmm_clicker.core.model.EdgeConditionType
import com.ksxkq.cmm_clicker.core.model.FlowEdge
import com.ksxkq.cmm_clicker.core.model.FlowNode
import com.ksxkq.cmm_clicker.core.model.NodeKind
import com.ksxkq.cmm_clicker.core.model.TaskBundle
import com.ksxkq.cmm_clicker.core.model.TaskFlow

object SampleFlowBundleFactory {
    fun createSimpleDemoBundle(): TaskBundle {
        val mainFlow = TaskFlow(
            flowId = "main",
            name = "Main Flow",
            entryNodeId = "start",
            nodes = listOf(
                FlowNode(nodeId = "start", kind = NodeKind.START),
                FlowNode(
                    nodeId = "branch",
                    kind = NodeKind.BRANCH,
                    params = mapOf("variableKey" to "doSwipe"),
                ),
                FlowNode(
                    nodeId = "click",
                    kind = NodeKind.ACTION,
                    actionType = ActionType.CLICK,
                    pluginId = "builtin.basic_gesture",
                    params = mapOf(
                        "x" to 0.5,
                        "y" to 0.6,
                        "durationMs" to 60,
                    ),
                ),
                FlowNode(
                    nodeId = "swipe",
                    kind = NodeKind.ACTION,
                    actionType = ActionType.SWIPE,
                    pluginId = "builtin.basic_gesture",
                    params = mapOf(
                        "startX" to 0.5,
                        "startY" to 0.8,
                        "endX" to 0.5,
                        "endY" to 0.2,
                        "durationMs" to 300,
                    ),
                ),
                FlowNode(
                    nodeId = "jumpToSub",
                    kind = NodeKind.JUMP,
                    params = mapOf(
                        "targetFlowId" to "sub",
                        "targetNodeId" to "subClick",
                    ),
                ),
            ),
            edges = listOf(
                FlowEdge(edgeId = "e1", fromNodeId = "start", toNodeId = "branch"),
                FlowEdge(
                    edgeId = "e2",
                    fromNodeId = "branch",
                    toNodeId = "swipe",
                    conditionType = EdgeConditionType.TRUE,
                ),
                FlowEdge(
                    edgeId = "e3",
                    fromNodeId = "branch",
                    toNodeId = "click",
                    conditionType = EdgeConditionType.FALSE,
                ),
                FlowEdge(edgeId = "e4", fromNodeId = "swipe", toNodeId = "jumpToSub"),
                FlowEdge(edgeId = "e5", fromNodeId = "click", toNodeId = "jumpToSub"),
            ),
        )

        val subFlow = TaskFlow(
            flowId = "sub",
            name = "Sub Flow",
            entryNodeId = "subClick",
            nodes = listOf(
                FlowNode(
                    nodeId = "subClick",
                    kind = NodeKind.ACTION,
                    actionType = ActionType.CLICK,
                    pluginId = "builtin.basic_gesture",
                    params = mapOf(
                        "x" to 0.7,
                        "y" to 0.5,
                        "durationMs" to 60,
                    ),
                ),
                FlowNode(nodeId = "subEnd", kind = NodeKind.END),
            ),
            edges = listOf(
                FlowEdge(edgeId = "e6", fromNodeId = "subClick", toNodeId = "subEnd"),
            ),
        )

        return TaskBundle(
            bundleId = "bundle_demo_001",
            name = "Demo Bundle",
            schemaVersion = 1,
            entryFlowId = "main",
            flows = listOf(mainFlow, subFlow),
        )
    }
}
