package com.ksxkq.cmm_clicker.accessibility

internal enum class PanelDisplayMode {
    FULL,
    MINI,
}

internal enum class PanelHideReason {
    SETTINGS_OPEN,
    RECORDING_INTERACTION,
    RUNNING_TEMP,
}

internal data class PanelVisibilityState(
    val displayMode: PanelDisplayMode = PanelDisplayMode.FULL,
    val hideReasons: Set<PanelHideReason> = emptySet(),
)

internal sealed interface PanelVisibilityEvent {
    data class SetDisplayMode(
        val mode: PanelDisplayMode,
    ) : PanelVisibilityEvent

    data class SetHideReason(
        val reason: PanelHideReason,
        val hidden: Boolean,
    ) : PanelVisibilityEvent

    data object ClearHideReasons : PanelVisibilityEvent
}

internal fun reducePanelVisibilityState(
    current: PanelVisibilityState,
    event: PanelVisibilityEvent,
): PanelVisibilityState {
    return when (event) {
        is PanelVisibilityEvent.SetDisplayMode -> current.copy(
            displayMode = event.mode,
        )

        is PanelVisibilityEvent.SetHideReason -> {
            val nextReasons = current.hideReasons.toMutableSet()
            if (event.hidden) {
                nextReasons += event.reason
            } else {
                nextReasons -= event.reason
            }
            current.copy(hideReasons = nextReasons)
        }

        PanelVisibilityEvent.ClearHideReasons -> current.copy(hideReasons = emptySet())
    }
}
