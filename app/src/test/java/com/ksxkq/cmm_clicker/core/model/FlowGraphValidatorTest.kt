package com.ksxkq.cmm_clicker.core.model

import org.junit.Assert.assertTrue
import org.junit.Test

class FlowGraphValidatorTest {
    @Test
    fun `should report error when jump target missing`() {
        val bundle = TaskBundle(
            bundleId = "b1",
            name = "bundle",
            schemaVersion = 1,
            entryFlowId = "flow_main",
            flows = listOf(
                TaskFlow(
                    flowId = "flow_main",
                    name = "main",
                    entryNodeId = "start",
                    nodes = listOf(
                        FlowNode(nodeId = "start", kind = NodeKind.START),
                        FlowNode(nodeId = "jump", kind = NodeKind.JUMP),
                        FlowNode(nodeId = "end", kind = NodeKind.END),
                    ),
                    edges = listOf(
                        FlowEdge(edgeId = "e1", fromNodeId = "start", toNodeId = "jump"),
                        FlowEdge(edgeId = "e2", fromNodeId = "jump", toNodeId = "end"),
                    ),
                ),
            ),
        )
        val issues = FlowGraphValidator().validate(bundle)
        assertTrue(issues.any { it.code == "jump_target_missing" && it.severity == ValidationSeverity.ERROR })
    }

    @Test
    fun `should accept jump target when params are non string values`() {
        val bundle = TaskBundle(
            bundleId = "b2",
            name = "bundle",
            schemaVersion = 1,
            entryFlowId = "flow_main",
            flows = listOf(
                TaskFlow(
                    flowId = "flow_main",
                    name = "main",
                    entryNodeId = "start",
                    nodes = listOf(
                        FlowNode(nodeId = "start", kind = NodeKind.START),
                        FlowNode(
                            nodeId = "jump",
                            kind = NodeKind.JUMP,
                            params = mapOf(
                                "targetFlowId" to "flow_main",
                                "targetNodeId" to 123,
                            ),
                        ),
                        FlowNode(nodeId = "123", kind = NodeKind.END),
                    ),
                    edges = listOf(
                        FlowEdge(edgeId = "e1", fromNodeId = "start", toNodeId = "jump"),
                        FlowEdge(edgeId = "e2", fromNodeId = "jump", toNodeId = "123"),
                    ),
                ),
            ),
        )
        val issues = FlowGraphValidator().validate(bundle)
        assertTrue(issues.none { it.code == "jump_target_missing" || it.code == "jump_target_invalid" })
    }
}
