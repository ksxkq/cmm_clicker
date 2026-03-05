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
            retainTransientOverlay = false,
        )
        val withModal = hidden.copy(hasModal = true)
        val withTransient = hidden.copy(retainTransientOverlay = true)

        assertFalse(shouldRenderSettingsOverlay(hidden))
        assertTrue(shouldRenderSettingsOverlay(withModal))
        assertTrue(shouldRenderSettingsOverlay(withTransient))
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
            retainTransientOverlay = false,
        )
        val blockedByDialog = idle.copy(recordingDialogVisible = true)
        val blockedByModal = idle.copy(hasModal = true)
        val blockedByTransient = idle.copy(retainTransientOverlay = true)

        assertTrue(canRemoveSettingsOverlayWhenIdle(idle))
        assertFalse(canRemoveSettingsOverlayWhenIdle(blockedByDialog))
        assertFalse(canRemoveSettingsOverlayWhenIdle(blockedByModal))
        assertFalse(canRemoveSettingsOverlayWhenIdle(blockedByTransient))
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
            retainTransientOverlay = false,
        )
        val idle = blocked.copy(
            settingsVisible = false,
            recordingDialogAnimatingOut = false,
            pendingRemoval = false,
            retainTransientOverlay = false,
        )

        assertEquals(
            "settings=true,dialog=false,anim=true,modal=false,retain=false",
            settingsOverlayRemovalBlockSignature(blocked),
        )
        assertEquals(null, settingsOverlayRemovalBlockSignature(idle))
    }
}
