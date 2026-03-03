package com.ksxkq.cmm_clicker.accessibility

import com.ksxkq.cmm_clicker.core.model.FlowNode
import com.ksxkq.cmm_clicker.core.model.NodeKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class TaskControlPanelEditorInteractionLogicTest {
    @Test
    fun resolveJumpTargetFlowId_shouldFallbackToCurrentFlow() {
        val node = FlowNode(
            nodeId = "jump_1",
            kind = NodeKind.JUMP,
            params = emptyMap(),
        )

        val resolved = resolveJumpTargetFlowId(node, currentFlowId = "main_flow")

        assertEquals("main_flow", resolved)
    }

    @Test
    fun resolveJumpTargetNodeId_shouldFallbackToFirstNonStartNode() {
        val node = FlowNode(
            nodeId = "jump_1",
            kind = NodeKind.JUMP,
            params = emptyMap(),
        )
        val selectableNodes = listOf(
            FlowNode(nodeId = "start", kind = NodeKind.START),
            FlowNode(nodeId = "a1", kind = NodeKind.ACTION),
            FlowNode(nodeId = "a2", kind = NodeKind.ACTION),
        )

        val resolved = resolveJumpTargetNodeId(
            node = node,
            selectableNodes = selectableNodes,
        )

        assertEquals("a1", resolved)
    }

    @Test
    fun pixelInputToRatioOrNull_shouldClampAndConvert() {
        val lower = pixelInputToRatioOrNull(input = "-10", screenSizePx = 100)
        val upper = pixelInputToRatioOrNull(input = "999", screenSizePx = 100)
        val mid = pixelInputToRatioOrNull(input = "50", screenSizePx = 100)
        assertNotNull(lower)
        assertNotNull(upper)
        assertNotNull(mid)
        assertEquals(0.0, lower ?: 0.0, 1e-9)
        assertEquals(1.0, upper ?: 0.0, 1e-9)
        assertEquals(50.0 / 99.0, mid ?: 0.0, 1e-9)
        assertNull(pixelInputToRatioOrNull(input = "abc", screenSizePx = 100))
    }

    @Test
    fun ratioToPixel_shouldClampAndRound() {
        assertEquals(0, ratioToPixel(ratio = -1.0, screenSizePx = 100))
        assertEquals(99, ratioToPixel(ratio = 2.0, screenSizePx = 100))
        assertEquals(50, ratioToPixel(ratio = 0.505, screenSizePx = 100))
    }
}
