package com.ksxkq.cmm_clicker.feature.editor

import com.ksxkq.cmm_clicker.core.model.ActionType
import com.ksxkq.cmm_clicker.core.model.EdgeConditionType
import com.ksxkq.cmm_clicker.core.model.NodeKind
import com.ksxkq.cmm_clicker.core.runtime.SampleFlowBundleFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskGraphEditorStoreTest {
    @Test
    fun addNodeAndUndoRedo_shouldWork() {
        val store = TaskGraphEditorStore(
            initialBundle = SampleFlowBundleFactory.createSimpleDemoBundle(),
        )
        val beforeCount = store.state().selectedFlow!!.nodes.size

        store.addActionNode()
        val afterAdd = store.state()
        assertEquals(beforeCount + 1, afterAdd.selectedFlow!!.nodes.size)
        assertTrue(afterAdd.canUndo)
        assertTrue(store.undo())

        val afterUndo = store.state()
        assertEquals(beforeCount, afterUndo.selectedFlow!!.nodes.size)
        assertTrue(store.redo())
        assertEquals(beforeCount + 1, store.state().selectedFlow!!.nodes.size)
    }

    @Test
    fun removeEntryNode_shouldReject() {
        val store = TaskGraphEditorStore(
            initialBundle = SampleFlowBundleFactory.createSimpleDemoBundle(),
        )
        val entryNodeId = store.state().selectedFlow!!.entryNodeId
        store.selectNode(entryNodeId)

        assertFalse(store.removeSelectedNode())
    }

    @Test
    fun updateNodeKindAndParams_shouldApply() {
        val store = TaskGraphEditorStore(
            initialBundle = SampleFlowBundleFactory.createSimpleDemoBundle(),
        )
        store.selectFlow("main")
        store.selectNode("click")

        store.updateSelectedNodeKind(NodeKind.JUMP)
        val jumpNode = store.state().selectedNode
        assertNotNull(jumpNode)
        assertEquals(NodeKind.JUMP, jumpNode!!.kind)
        assertEquals("main", jumpNode.params["targetFlowId"])
        assertTrue(jumpNode.params["x"] == null)
        assertTrue(jumpNode.params["y"] == null)

        store.updateSelectedNodeKind(NodeKind.ACTION)
        store.updateSelectedNodeActionType(ActionType.SWIPE)
        store.updateSelectedNodeParam("startX", "0.2")
        val actionNode = store.state().selectedNode
        assertEquals(NodeKind.ACTION, actionNode!!.kind)
        assertEquals(ActionType.SWIPE, actionNode.actionType)
        assertEquals("0.2", actionNode.params["startX"])
        assertTrue(actionNode.params["targetFlowId"] == null)
        assertTrue(actionNode.params["targetNodeId"] == null)
    }

    @Test
    fun branchTargetEditing_shouldUpdateEdges() {
        val store = TaskGraphEditorStore(
            initialBundle = SampleFlowBundleFactory.createSimpleDemoBundle(),
        )
        store.selectFlow("main")
        store.selectNode("branch")

        store.updateSelectedBranchVariableKey("myFlag")
        store.updateSelectedBranchTarget(EdgeConditionType.TRUE, "click")
        store.updateSelectedBranchTarget(EdgeConditionType.FALSE, "swipe")

        val state = store.state()
        val flow = state.selectedFlow!!
        val branch = flow.findNode("branch")!!
        assertEquals("myFlag", branch.params["variableKey"])

        val trueEdge = flow.edges.firstOrNull {
            it.fromNodeId == "branch" && it.conditionType == EdgeConditionType.TRUE
        }
        val falseEdge = flow.edges.firstOrNull {
            it.fromNodeId == "branch" && it.conditionType == EdgeConditionType.FALSE
        }
        assertEquals("click", trueEdge?.toNodeId)
        assertEquals("swipe", falseEdge?.toNodeId)
    }

    @Test
    fun actionTypeSwitch_shouldPruneStaleParamsAndKeepCommonDefaults() {
        val store = TaskGraphEditorStore(
            initialBundle = SampleFlowBundleFactory.createSimpleDemoBundle(),
        )
        store.selectFlow("main")
        store.selectNode("click")

        store.updateSelectedNodeActionType(ActionType.SWIPE)
        val swipeNode = store.state().selectedNode!!
        assertEquals(ActionType.SWIPE, swipeNode.actionType)
        assertTrue(swipeNode.params["x"] == null)
        assertTrue(swipeNode.params["y"] == null)
        assertEquals("0.5", swipeNode.params["startX"])
        assertEquals("60", swipeNode.params["durationMs"])

        store.updateSelectedNodeActionType(ActionType.CLICK)
        val clickNode = store.state().selectedNode!!
        assertEquals(ActionType.CLICK, clickNode.actionType)
        assertTrue(clickNode.params["startX"] == null)
        assertTrue(clickNode.params["startY"] == null)
        assertEquals("0.5", clickNode.params["x"])
        assertEquals("60", clickNode.params["durationMs"])
    }
}
