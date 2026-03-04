package com.ksxkq.cmm_clicker.accessibility

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskControlPanelSettingsOverlayLifecycleTest {

    @Test
    fun `shouldRenderSettingsOverlay returns false only when all flags are false`() {
        val hidden = SettingsOverlayLifecycleState(
            settingsVisible = false,
            recordingDialogVisible = false,
            recordingDialogAnimatingOut = false,
            hasModal = false,
            clickPickerVisible = false,
            pendingRemoval = false,
        )
        val withModal = hidden.copy(hasModal = true)

        assertFalse(shouldRenderSettingsOverlay(hidden))
        assertTrue(shouldRenderSettingsOverlay(withModal))
    }

    @Test
    fun `canRemoveSettingsOverlayWhenIdle requires no blocking flags`() {
        val idle = SettingsOverlayLifecycleState(
            settingsVisible = false,
            recordingDialogVisible = false,
            recordingDialogAnimatingOut = false,
            hasModal = false,
            clickPickerVisible = true,
            pendingRemoval = true,
        )
        val blockedByDialog = idle.copy(recordingDialogVisible = true)
        val blockedByModal = idle.copy(hasModal = true)

        assertTrue(canRemoveSettingsOverlayWhenIdle(idle))
        assertFalse(canRemoveSettingsOverlayWhenIdle(blockedByDialog))
        assertFalse(canRemoveSettingsOverlayWhenIdle(blockedByModal))
    }

    @Test
    fun `settingsOverlayRemovalBlockSignature returns expected text`() {
        val blocked = SettingsOverlayLifecycleState(
            settingsVisible = true,
            recordingDialogVisible = false,
            recordingDialogAnimatingOut = true,
            hasModal = false,
            clickPickerVisible = false,
            pendingRemoval = true,
        )
        val idle = blocked.copy(
            settingsVisible = false,
            recordingDialogAnimatingOut = false,
            pendingRemoval = false,
        )

        assertEquals(
            "settings=true,dialog=false,anim=true,modal=false",
            settingsOverlayRemovalBlockSignature(blocked),
        )
        assertEquals(null, settingsOverlayRemovalBlockSignature(idle))
    }
}
