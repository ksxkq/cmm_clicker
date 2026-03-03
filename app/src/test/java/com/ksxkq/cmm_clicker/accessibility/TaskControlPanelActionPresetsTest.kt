package com.ksxkq.cmm_clicker.accessibility

import com.ksxkq.cmm_clicker.core.model.ActionType
import com.ksxkq.cmm_clicker.core.model.NodeKind
import com.ksxkq.cmm_clicker.core.runtime.SampleFlowBundleFactory
import com.ksxkq.cmm_clicker.feature.editor.TaskGraphEditorStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskControlPanelActionPresetsTest {
    @Test
    fun applyAddActionPreset_shouldCreateExpectedNodeType() {
        assertPreset(AddActionPreset.CLICK, expectedKind = NodeKind.ACTION, expectedActionType = ActionType.CLICK)
        assertPreset(AddActionPreset.SWIPE, expectedKind = NodeKind.ACTION, expectedActionType = ActionType.SWIPE)
        assertPreset(AddActionPreset.RECORD, expectedKind = NodeKind.ACTION, expectedActionType = ActionType.RECORD)
        assertPreset(AddActionPreset.DUP_CLICK, expectedKind = NodeKind.ACTION, expectedActionType = ActionType.DUP_CLICK)
        assertPreset(AddActionPreset.JUMP, expectedKind = NodeKind.JUMP, expectedActionType = null)
    }

    private fun assertPreset(
        preset: AddActionPreset,
        expectedKind: NodeKind,
        expectedActionType: ActionType?,
    ) {
        val store = TaskGraphEditorStore(
            initialBundle = SampleFlowBundleFactory.createSimpleDemoBundle(),
        )
        store.selectFlow("main")
        val beforeCount = store.state().selectedFlow!!.nodes.size

        applyAddActionPreset(
            store = store,
            preset = preset,
        )

        val state = store.state()
        val selectedNode = state.selectedNode
        assertTrue(selectedNode != null)
        assertEquals(beforeCount + 1, state.selectedFlow!!.nodes.size)
        assertEquals(expectedKind, selectedNode!!.kind)
        assertEquals(expectedActionType, selectedNode.actionType)
    }
}
