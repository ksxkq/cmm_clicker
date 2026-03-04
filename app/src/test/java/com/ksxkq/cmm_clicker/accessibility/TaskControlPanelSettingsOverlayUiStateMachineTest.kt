package com.ksxkq.cmm_clicker.accessibility

import org.junit.Assert.assertEquals
import org.junit.Test

class TaskControlPanelSettingsOverlayUiStateMachineTest {

    @Test
    fun `reduceSettingsOverlayUiState show event resets sheet and dismiss flags`() {
        val current = SettingsOverlayUiState(
            visible = false,
            sheetVisible = true,
            dismissAnimating = true,
        )

        val next = reduceSettingsOverlayUiState(
            current = current,
            event = SettingsOverlayUiEvent.SHOW_OVERLAY,
        )

        assertEquals(true, next.visible)
        assertEquals(false, next.sheetVisible)
        assertEquals(false, next.dismissAnimating)
    }

    @Test
    fun `reduceSettingsOverlayUiState dismiss flow transitions to hidden`() {
        val current = SettingsOverlayUiState(
            visible = true,
            sheetVisible = true,
            dismissAnimating = false,
        )

        val started = reduceSettingsOverlayUiState(
            current = current,
            event = SettingsOverlayUiEvent.START_DISMISS_ANIMATION,
        )
        val finished = reduceSettingsOverlayUiState(
            current = started,
            event = SettingsOverlayUiEvent.FINISH_DISMISS_ANIMATION,
        )

        assertEquals(true, started.visible)
        assertEquals(false, started.sheetVisible)
        assertEquals(true, started.dismissAnimating)
        assertEquals(false, finished.visible)
        assertEquals(false, finished.sheetVisible)
        assertEquals(false, finished.dismissAnimating)
    }

    @Test
    fun `reduceSettingsOverlayUiState show sheet toggles sheet only`() {
        val current = SettingsOverlayUiState(
            visible = true,
            sheetVisible = false,
            dismissAnimating = false,
        )

        val next = reduceSettingsOverlayUiState(
            current = current,
            event = SettingsOverlayUiEvent.SHOW_SHEET,
        )

        assertEquals(true, next.visible)
        assertEquals(true, next.sheetVisible)
        assertEquals(false, next.dismissAnimating)
    }
}
