package com.ksxkq.cmm_clicker.accessibility

import org.junit.Assert.assertEquals
import org.junit.Test

class TaskControlPanelEditorUiStateTest {
    @Test
    fun nodeEditorDraft_shouldUpdateClickInputs() {
        val uiState = NodeEditorDraftUiState()

        uiState.setClickInputs(x = "120", y = "340")
        assertEquals("120", uiState.clickXInput)
        assertEquals("340", uiState.clickYInput)

        uiState.updateClickXInput("121")
        uiState.updateClickYInput("341")
        assertEquals("121", uiState.clickXInput)
        assertEquals("341", uiState.clickYInput)
    }

    @Test
    fun nodeEditorDraft_shouldUseFallbackWhenDraftMissing() {
        val uiState = NodeEditorDraftUiState()

        assertEquals("fallback", uiState.paramDraftOr("durationMs", "fallback"))

        uiState.setParamDraftFromModel("durationMs", "300")
        assertEquals("300", uiState.paramDraftOr("durationMs", "fallback"))

        uiState.updateParamDraft("durationMs", "450")
        assertEquals("450", uiState.paramDraftOr("durationMs", "fallback"))
    }

    @Test
    fun nodeEditorDraft_shouldRetainOnlySpecifiedKeys() {
        val uiState = NodeEditorDraftUiState()

        uiState.setParamDraftFromModel("durationMs", "300")
        uiState.setParamDraftFromModel("postDelayMs", "100")
        uiState.setParamDraftFromModel("x", "0.5")

        uiState.retainParamDraftKeys(setOf("durationMs", "x"))

        assertEquals("300", uiState.paramDraftOr("durationMs", "missing"))
        assertEquals("0.5", uiState.paramDraftOr("x", "missing"))
        assertEquals("missing", uiState.paramDraftOr("postDelayMs", "missing"))
    }
}
