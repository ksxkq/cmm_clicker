package com.ksxkq.cmm_clicker.accessibility

import com.ksxkq.cmm_clicker.core.model.ActionType
import com.ksxkq.cmm_clicker.core.model.FlowNode
import com.ksxkq.cmm_clicker.core.model.NodeKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskControlPanelEditorParamSectionsTest {
    @Test
    fun buildNodeEditorParamsSnapshot_shouldHideClickXYForClickAction() {
        val node = FlowNode(
            nodeId = "click_1",
            kind = NodeKind.ACTION,
            actionType = ActionType.CLICK,
            params = mapOf(
                "x" to "0.2",
                "y" to "0.4",
                "durationMs" to "120",
            ),
        )

        val snapshot = buildNodeEditorParamsSnapshot(node)

        assertFalse(snapshot.containsKey("x"))
        assertFalse(snapshot.containsKey("y"))
        assertTrue(snapshot.containsKey("durationMs"))
    }

    @Test
    fun buildNodeEditorParamsSnapshot_shouldHideJumpTargetsForJumpNode() {
        val node = FlowNode(
            nodeId = "jump_1",
            kind = NodeKind.JUMP,
            params = mapOf(
                "targetFlowId" to "main",
                "targetNodeId" to "node_3",
                "postDelayMs" to "100",
            ),
        )

        val snapshot = buildNodeEditorParamsSnapshot(node)

        assertFalse(snapshot.containsKey("targetFlowId"))
        assertFalse(snapshot.containsKey("targetNodeId"))
        assertTrue(snapshot.containsKey("postDelayMs"))
    }

    @Test
    fun groupNodeEditorParams_shouldMapKeysToExpectedGroups() {
        val grouped = groupNodeEditorParams(
            linkedMapOf(
                "x" to "0.2",
                "durationMs" to "120",
                "targetNodeId" to "node_2",
                "variableKey" to "doSwipe",
                "customKey" to "v",
            ),
        )

        assertEquals(ParamEditorGroup.POSITION, paramEditorGroupForKey("x"))
        assertEquals(ParamEditorGroup.TIMING, paramEditorGroupForKey("durationMs"))
        assertEquals(ParamEditorGroup.TARGET, paramEditorGroupForKey("targetNodeId"))
        assertEquals(ParamEditorGroup.BEHAVIOR, paramEditorGroupForKey("variableKey"))
        assertEquals(ParamEditorGroup.ADVANCED, paramEditorGroupForKey("customKey"))

        assertEquals(1, grouped[ParamEditorGroup.POSITION]?.size)
        assertEquals(1, grouped[ParamEditorGroup.TIMING]?.size)
        assertEquals(1, grouped[ParamEditorGroup.TARGET]?.size)
        assertEquals(1, grouped[ParamEditorGroup.BEHAVIOR]?.size)
        assertEquals(1, grouped[ParamEditorGroup.ADVANCED]?.size)
    }
}
