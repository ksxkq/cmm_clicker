package com.ksxkq.cmm_clicker.accessibility

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskControlPanelPanelVisibilityStateMachineTest {

    @Test
    fun `reducePanelVisibilityState updates display mode`() {
        val state = PanelVisibilityState(
            displayMode = PanelDisplayMode.FULL,
            hideReasons = setOf(PanelHideReason.SETTINGS_OPEN),
        )

        val next = reducePanelVisibilityState(
            current = state,
            event = PanelVisibilityEvent.SetDisplayMode(PanelDisplayMode.MINI),
        )

        assertEquals(PanelDisplayMode.MINI, next.displayMode)
        assertEquals(setOf(PanelHideReason.SETTINGS_OPEN), next.hideReasons)
    }

    @Test
    fun `reducePanelVisibilityState toggles hide reason on and off`() {
        val initial = PanelVisibilityState()
        val hidden = reducePanelVisibilityState(
            current = initial,
            event = PanelVisibilityEvent.SetHideReason(
                reason = PanelHideReason.RUNNING_TEMP,
                hidden = true,
            ),
        )
        val shown = reducePanelVisibilityState(
            current = hidden,
            event = PanelVisibilityEvent.SetHideReason(
                reason = PanelHideReason.RUNNING_TEMP,
                hidden = false,
            ),
        )

        assertTrue(hidden.hideReasons.contains(PanelHideReason.RUNNING_TEMP))
        assertTrue(shown.hideReasons.isEmpty())
    }

    @Test
    fun `reducePanelVisibilityState clears all hide reasons`() {
        val state = PanelVisibilityState(
            hideReasons = setOf(
                PanelHideReason.SETTINGS_OPEN,
                PanelHideReason.RECORDING_INTERACTION,
            ),
        )

        val next = reducePanelVisibilityState(
            current = state,
            event = PanelVisibilityEvent.ClearHideReasons,
        )

        assertTrue(next.hideReasons.isEmpty())
        assertEquals(state.displayMode, next.displayMode)
    }
}
