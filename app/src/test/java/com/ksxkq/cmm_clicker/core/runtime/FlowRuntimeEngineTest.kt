package com.ksxkq.cmm_clicker.core.runtime

import com.ksxkq.cmm_clicker.core.model.ActionType
import com.ksxkq.cmm_clicker.core.model.EdgeConditionType
import com.ksxkq.cmm_clicker.core.model.FlowEdge
import com.ksxkq.cmm_clicker.core.model.FlowNode
import com.ksxkq.cmm_clicker.core.model.NodeKind
import com.ksxkq.cmm_clicker.core.model.TaskBundle
import com.ksxkq.cmm_clicker.core.model.TaskFlow
import com.ksxkq.cmm_clicker.core.runSuspend
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FlowRuntimeEngineTest {
    @Test
    fun `should run jump across flow`() {
        val mainFlow = TaskFlow(
            flowId = "main",
            name = "main",
            entryNodeId = "start",
            nodes = listOf(
                FlowNode(nodeId = "start", kind = NodeKind.START),
                FlowNode(nodeId = "click1", kind = NodeKind.ACTION, actionType = ActionType.CLICK),
                FlowNode(
                    nodeId = "jumpToSub",
                    kind = NodeKind.JUMP,
                    params = mapOf(
                        "targetFlowId" to "sub",
                        "targetNodeId" to "subStart",
                    ),
                ),
            ),
            edges = listOf(
                FlowEdge(edgeId = "e1", fromNodeId = "start", toNodeId = "click1"),
                FlowEdge(edgeId = "e2", fromNodeId = "click1", toNodeId = "jumpToSub"),
            ),
        )
        val subFlow = TaskFlow(
            flowId = "sub",
            name = "sub",
            entryNodeId = "subStart",
            nodes = listOf(
                FlowNode(nodeId = "subStart", kind = NodeKind.ACTION, actionType = ActionType.SWIPE),
                FlowNode(nodeId = "end", kind = NodeKind.END),
            ),
            edges = listOf(
                FlowEdge(edgeId = "e3", fromNodeId = "subStart", toNodeId = "end"),
            ),
        )

        val bundle = TaskBundle(
            bundleId = "bundle_1",
            name = "runtime test",
            schemaVersion = 1,
            entryFlowId = "main",
            flows = listOf(mainFlow, subFlow),
        )

        val traceCollector = InMemoryRuntimeTraceCollector()
        val result = runSuspend {
            FlowRuntimeEngine(
                options = RuntimeEngineOptions(dryRun = true),
            ).execute(
                bundle = bundle,
                traceCollector = traceCollector,
            )
        }

        assertEquals(RuntimeExecutionStatus.COMPLETED, result.status)
        assertTrue(result.traceEvents.any { it.flowId == "sub" && it.nodeId == "subStart" })
    }

    @Test
    fun `should choose branch true edge by variable`() {
        val flow = TaskFlow(
            flowId = "main",
            name = "main",
            entryNodeId = "start",
            nodes = listOf(
                FlowNode(nodeId = "start", kind = NodeKind.START),
                FlowNode(
                    nodeId = "branch",
                    kind = NodeKind.BRANCH,
                    params = mapOf("variableKey" to "isVip"),
                ),
                FlowNode(nodeId = "trueNode", kind = NodeKind.ACTION, actionType = ActionType.CLICK),
                FlowNode(nodeId = "falseNode", kind = NodeKind.ACTION, actionType = ActionType.SWIPE),
                FlowNode(nodeId = "end", kind = NodeKind.END),
            ),
            edges = listOf(
                FlowEdge(edgeId = "e1", fromNodeId = "start", toNodeId = "branch"),
                FlowEdge(
                    edgeId = "e2",
                    fromNodeId = "branch",
                    toNodeId = "trueNode",
                    conditionType = EdgeConditionType.TRUE,
                ),
                FlowEdge(
                    edgeId = "e3",
                    fromNodeId = "branch",
                    toNodeId = "falseNode",
                    conditionType = EdgeConditionType.FALSE,
                ),
                FlowEdge(edgeId = "e4", fromNodeId = "trueNode", toNodeId = "end"),
                FlowEdge(edgeId = "e5", fromNodeId = "falseNode", toNodeId = "end"),
            ),
        )
        val bundle = TaskBundle(
            bundleId = "bundle_2",
            name = "branch test",
            schemaVersion = 1,
            entryFlowId = "main",
            flows = listOf(flow),
        )

        val result = runSuspend {
            FlowRuntimeEngine(
                options = RuntimeEngineOptions(dryRun = true),
            ).execute(
                bundle = bundle,
                initialVariables = mapOf("isVip" to true),
            )
        }

        assertEquals(RuntimeExecutionStatus.COMPLETED, result.status)
        val startedNodes = result.traceEvents
            .filter { it.phase == RuntimeTracePhase.NODE_START }
            .map { it.nodeId }
        assertTrue(startedNodes.contains("trueNode"))
        assertTrue(!startedNodes.contains("falseNode"))
    }

    @Test
    fun `should return to caller after folder ref flow end`() {
        val mainFlow = TaskFlow(
            flowId = "main",
            name = "main",
            entryNodeId = "start",
            nodes = listOf(
                FlowNode(nodeId = "start", kind = NodeKind.START),
                FlowNode(
                    nodeId = "folderRef",
                    kind = NodeKind.FOLDER_REF,
                    params = mapOf(
                        "targetFlowId" to "folder_flow",
                        "targetNodeId" to "folderStart",
                    ),
                ),
                FlowNode(nodeId = "afterFolder", kind = NodeKind.ACTION, actionType = ActionType.CLICK),
                FlowNode(nodeId = "end", kind = NodeKind.END),
            ),
            edges = listOf(
                FlowEdge(edgeId = "e1", fromNodeId = "start", toNodeId = "folderRef"),
                FlowEdge(edgeId = "e2", fromNodeId = "folderRef", toNodeId = "afterFolder"),
                FlowEdge(edgeId = "e3", fromNodeId = "afterFolder", toNodeId = "end"),
            ),
        )
        val folderFlow = TaskFlow(
            flowId = "folder_flow",
            name = "folder",
            entryNodeId = "folderStart",
            nodes = listOf(
                FlowNode(nodeId = "folderStart", kind = NodeKind.ACTION, actionType = ActionType.SWIPE),
                FlowNode(nodeId = "folderEnd", kind = NodeKind.END),
            ),
            edges = listOf(
                FlowEdge(edgeId = "e4", fromNodeId = "folderStart", toNodeId = "folderEnd"),
            ),
        )
        val bundle = TaskBundle(
            bundleId = "bundle_3",
            name = "folder return test",
            schemaVersion = 1,
            entryFlowId = "main",
            flows = listOf(mainFlow, folderFlow),
        )

        val result = runSuspend {
            FlowRuntimeEngine(
                options = RuntimeEngineOptions(dryRun = true),
            ).execute(bundle = bundle)
        }

        assertEquals(RuntimeExecutionStatus.COMPLETED, result.status)
        val startedNodes = result.traceEvents
            .filter { it.phase == RuntimeTracePhase.NODE_START }
            .map { "${it.flowId}/${it.nodeId}" }
        assertTrue(startedNodes.contains("folder_flow/folderStart"))
        assertTrue(startedNodes.contains("main/afterFolder"))
    }

    @Test
    fun `should evaluate branch by eq operator`() {
        val flow = TaskFlow(
            flowId = "main",
            name = "main",
            entryNodeId = "start",
            nodes = listOf(
                FlowNode(nodeId = "start", kind = NodeKind.START),
                FlowNode(
                    nodeId = "branch",
                    kind = NodeKind.BRANCH,
                    params = mapOf(
                        "variableKey" to "stage",
                        "operator" to "eq",
                        "expectedValue" to "vip",
                    ),
                ),
                FlowNode(nodeId = "trueNode", kind = NodeKind.ACTION, actionType = ActionType.CLICK),
                FlowNode(nodeId = "falseNode", kind = NodeKind.ACTION, actionType = ActionType.SWIPE),
                FlowNode(nodeId = "end", kind = NodeKind.END),
            ),
            edges = listOf(
                FlowEdge(edgeId = "e1", fromNodeId = "start", toNodeId = "branch"),
                FlowEdge(edgeId = "e2", fromNodeId = "branch", toNodeId = "trueNode", conditionType = EdgeConditionType.TRUE),
                FlowEdge(edgeId = "e3", fromNodeId = "branch", toNodeId = "falseNode", conditionType = EdgeConditionType.FALSE),
                FlowEdge(edgeId = "e4", fromNodeId = "trueNode", toNodeId = "end"),
                FlowEdge(edgeId = "e5", fromNodeId = "falseNode", toNodeId = "end"),
            ),
        )
        val bundle = TaskBundle(
            bundleId = "bundle_4",
            name = "branch eq test",
            schemaVersion = 1,
            entryFlowId = "main",
            flows = listOf(flow),
        )

        val result = runSuspend {
            FlowRuntimeEngine(
                options = RuntimeEngineOptions(dryRun = true),
            ).execute(
                bundle = bundle,
                initialVariables = mapOf("stage" to "vip"),
            )
        }

        val startedNodes = result.traceEvents
            .filter { it.phase == RuntimeTracePhase.NODE_START }
            .map { it.nodeId }
        assertTrue(startedNodes.contains("trueNode"))
        assertTrue(!startedNodes.contains("falseNode"))
    }
}
