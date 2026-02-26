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
}
