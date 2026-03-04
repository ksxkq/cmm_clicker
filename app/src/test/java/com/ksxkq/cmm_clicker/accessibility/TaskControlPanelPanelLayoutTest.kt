package com.ksxkq.cmm_clicker.accessibility

import org.junit.Assert.assertEquals
import org.junit.Test

class TaskControlPanelPanelLayoutTest {

    @Test
    fun `resolvePanelCardWidthDp returns mini width for running and non running mini`() {
        assertEquals(
            216,
            resolvePanelCardWidthDp(
                panelMode = PanelMode.RUNNING,
                panelDisplayMode = PanelDisplayMode.MINI,
                runningMiniActive = true,
            ),
        )
        assertEquals(
            150,
            resolvePanelCardWidthDp(
                panelMode = PanelMode.NORMAL,
                panelDisplayMode = PanelDisplayMode.MINI,
                runningMiniActive = false,
            ),
        )
    }

    @Test
    fun `resolvePanelCardWidthDp returns expected full widths by panel mode`() {
        assertEquals(
            186,
            resolvePanelCardWidthDp(
                panelMode = PanelMode.NORMAL,
                panelDisplayMode = PanelDisplayMode.FULL,
                runningMiniActive = false,
            ),
        )
        assertEquals(
            222,
            resolvePanelCardWidthDp(
                panelMode = PanelMode.RECORDING,
                panelDisplayMode = PanelDisplayMode.FULL,
                runningMiniActive = false,
            ),
        )
        assertEquals(
            264,
            resolvePanelCardWidthDp(
                panelMode = PanelMode.RUNNING,
                panelDisplayMode = PanelDisplayMode.FULL,
                runningMiniActive = true,
            ),
        )
    }
}
