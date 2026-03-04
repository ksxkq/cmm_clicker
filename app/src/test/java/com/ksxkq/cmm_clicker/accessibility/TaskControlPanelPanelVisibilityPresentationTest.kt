package com.ksxkq.cmm_clicker.accessibility

import org.junit.Assert.assertEquals
import org.junit.Test

class TaskControlPanelPanelVisibilityPresentationTest {

    @Test
    fun `computePanelRenderVisibility shows full panel when no hide reason exists`() {
        val visibility = computePanelRenderVisibility(
            panelEntered = true,
            panelDismissAnimating = false,
            panelVisibilityState = PanelVisibilityState(
                displayMode = PanelDisplayMode.FULL,
                hideReasons = emptySet(),
            ),
            recordingSaveDialogVisible = false,
            hasSettingsModal = false,
        )

        assertEquals(false, visibility.runningMiniActive)
        assertEquals(false, visibility.miniBlockedByOtherHideReasons)
        assertEquals(true, visibility.fullPanelVisible)
        assertEquals(false, visibility.miniPanelVisible)
        assertEquals(true, visibility.panelVisible)
    }

    @Test
    fun `computePanelRenderVisibility shows running mini when only running temp hide reason exists`() {
        val visibility = computePanelRenderVisibility(
            panelEntered = true,
            panelDismissAnimating = false,
            panelVisibilityState = PanelVisibilityState(
                displayMode = PanelDisplayMode.MINI,
                hideReasons = setOf(PanelHideReason.RUNNING_TEMP),
            ),
            recordingSaveDialogVisible = false,
            hasSettingsModal = false,
        )

        assertEquals(true, visibility.runningMiniActive)
        assertEquals(false, visibility.miniBlockedByOtherHideReasons)
        assertEquals(false, visibility.fullPanelVisible)
        assertEquals(true, visibility.miniPanelVisible)
        assertEquals(true, visibility.panelVisible)
    }

    @Test
    fun `computePanelRenderVisibility hides mini when blocked by non running hide reason`() {
        val visibility = computePanelRenderVisibility(
            panelEntered = true,
            panelDismissAnimating = false,
            panelVisibilityState = PanelVisibilityState(
                displayMode = PanelDisplayMode.MINI,
                hideReasons = setOf(PanelHideReason.SETTINGS_OPEN),
            ),
            recordingSaveDialogVisible = false,
            hasSettingsModal = false,
        )

        assertEquals(false, visibility.runningMiniActive)
        assertEquals(true, visibility.miniBlockedByOtherHideReasons)
        assertEquals(false, visibility.fullPanelVisible)
        assertEquals(false, visibility.miniPanelVisible)
        assertEquals(false, visibility.panelVisible)
    }
}
