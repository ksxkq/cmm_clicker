package com.ksxkq.cmm_clicker.accessibility

internal fun resolvePanelCardWidthDp(
    panelMode: PanelMode,
    panelDisplayMode: PanelDisplayMode,
    runningMiniActive: Boolean,
): Int {
    return when (panelDisplayMode) {
        PanelDisplayMode.MINI -> {
            if (runningMiniActive) {
                216
            } else {
                150
            }
        }

        PanelDisplayMode.FULL -> {
            when (panelMode) {
                PanelMode.RECORDING -> 222
                PanelMode.RUNNING -> 264
                PanelMode.NORMAL -> 186
            }
        }
    }
}

internal fun resolvePanelCardHeightDp(
    panelMode: PanelMode,
    panelDisplayMode: PanelDisplayMode,
    runningMiniActive: Boolean,
): Int {
    return when (panelDisplayMode) {
        PanelDisplayMode.MINI -> {
            if (runningMiniActive) {
                98
            } else {
                52
            }
        }

        PanelDisplayMode.FULL -> {
            when (panelMode) {
                PanelMode.RECORDING -> 76
                PanelMode.RUNNING -> 138
                PanelMode.NORMAL -> 50
            }
        }
    }
}
