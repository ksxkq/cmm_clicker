package com.ksxkq.cmm_clicker.accessibility

internal data class PanelRenderVisibility(
    val runningMiniActive: Boolean,
    val miniBlockedByOtherHideReasons: Boolean,
    val fullPanelVisible: Boolean,
    val miniPanelVisible: Boolean,
    val panelVisible: Boolean,
)

internal fun computePanelRenderVisibility(
    panelEntered: Boolean,
    panelDismissAnimating: Boolean,
    panelVisibilityState: PanelVisibilityState,
    recordingSaveDialogVisible: Boolean,
    hasSettingsModal: Boolean,
): PanelRenderVisibility {
    val runningMiniActive = panelVisibilityState.displayMode == PanelDisplayMode.MINI &&
        panelVisibilityState.hideReasons.contains(PanelHideReason.RUNNING_TEMP)
    val miniBlockedByOtherHideReasons = panelVisibilityState.hideReasons.any {
        it != PanelHideReason.RUNNING_TEMP
    }
    val fullPanelVisible = panelEntered &&
        !panelDismissAnimating &&
        panelVisibilityState.displayMode == PanelDisplayMode.FULL &&
        !recordingSaveDialogVisible &&
        !hasSettingsModal &&
        panelVisibilityState.hideReasons.isEmpty()
    val miniPanelVisible = panelEntered &&
        !panelDismissAnimating &&
        panelVisibilityState.displayMode == PanelDisplayMode.MINI &&
        !miniBlockedByOtherHideReasons
    return PanelRenderVisibility(
        runningMiniActive = runningMiniActive,
        miniBlockedByOtherHideReasons = miniBlockedByOtherHideReasons,
        fullPanelVisible = fullPanelVisible,
        miniPanelVisible = miniPanelVisible,
        panelVisible = fullPanelVisible || miniPanelVisible,
    )
}
