package com.ksxkq.cmm_clicker.accessibility

internal data class SettingsOverlayUiState(
    val visible: Boolean = false,
    val sheetVisible: Boolean = false,
    val dismissAnimating: Boolean = false,
)

internal enum class SettingsOverlayUiEvent {
    SHOW_OVERLAY,
    SHOW_SHEET,
    START_DISMISS_ANIMATION,
    FINISH_DISMISS_ANIMATION,
    HIDE_IMMEDIATELY,
}

internal fun reduceSettingsOverlayUiState(
    current: SettingsOverlayUiState,
    event: SettingsOverlayUiEvent,
): SettingsOverlayUiState {
    return when (event) {
        SettingsOverlayUiEvent.SHOW_OVERLAY -> current.copy(
            visible = true,
            sheetVisible = false,
            dismissAnimating = false,
        )

        SettingsOverlayUiEvent.SHOW_SHEET -> {
            if (!current.visible || current.dismissAnimating) {
                current
            } else {
                current.copy(sheetVisible = true)
            }
        }

        SettingsOverlayUiEvent.START_DISMISS_ANIMATION -> current.copy(
            dismissAnimating = true,
            sheetVisible = false,
        )

        SettingsOverlayUiEvent.FINISH_DISMISS_ANIMATION,
        SettingsOverlayUiEvent.HIDE_IMMEDIATELY,
        -> current.copy(
            visible = false,
            sheetVisible = false,
            dismissAnimating = false,
        )
    }
}
