package com.ksxkq.cmm_clicker.accessibility

internal data class SettingsOverlayLifecycleState(
    val settingsVisible: Boolean,
    val recordingDialogVisible: Boolean,
    val recordingDialogAnimatingOut: Boolean,
    val hasModal: Boolean,
    val clickPickerVisible: Boolean,
    val pendingRemoval: Boolean,
    val retainTransientOverlay: Boolean,
)

internal fun shouldRenderSettingsOverlay(
    state: SettingsOverlayLifecycleState,
): Boolean {
    return state.settingsVisible ||
        state.recordingDialogVisible ||
        state.recordingDialogAnimatingOut ||
        state.hasModal ||
        state.clickPickerVisible ||
        state.pendingRemoval ||
        state.retainTransientOverlay
}

internal fun canRemoveSettingsOverlayWhenIdle(
    state: SettingsOverlayLifecycleState,
): Boolean {
    return !state.settingsVisible &&
        !state.recordingDialogVisible &&
        !state.recordingDialogAnimatingOut &&
        !state.hasModal &&
        !state.retainTransientOverlay
}

internal fun settingsOverlayRemovalBlockSignature(
    state: SettingsOverlayLifecycleState,
): String? {
    if (canRemoveSettingsOverlayWhenIdle(state)) {
        return null
    }
    return "settings=${state.settingsVisible}," +
        "dialog=${state.recordingDialogVisible}," +
        "anim=${state.recordingDialogAnimatingOut}," +
        "modal=${state.hasModal}," +
        "retain=${state.retainTransientOverlay}"
}
