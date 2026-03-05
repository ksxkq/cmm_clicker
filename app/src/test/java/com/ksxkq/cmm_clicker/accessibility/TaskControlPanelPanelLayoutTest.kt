package com.ksxkq.cmm_clicker.accessibility

import org.junit.Assert.assertEquals
import org.junit.Test

class TaskControlPanelPanelLayoutTest {

    @Test
    fun `resolvePanelCardWidthDp returns unified mini width`() {
        assertEquals(
            28,
            resolvePanelCardWidthDp(
                panelMode = PanelMode.RUNNING,
                panelDisplayMode = PanelDisplayMode.MINI,
                runningMiniActive = true,
            ),
        )
        assertEquals(
            28,
            resolvePanelCardWidthDp(
                panelMode = PanelMode.NORMAL,
                panelDisplayMode = PanelDisplayMode.MINI,
                runningMiniActive = false,
            ),
        )
    }

    @Test
    fun `resolvePanelCardHeightDp returns unified mini height`() {
        assertEquals(
            56,
            resolvePanelCardHeightDp(
                panelMode = PanelMode.RUNNING,
                panelDisplayMode = PanelDisplayMode.MINI,
                runningMiniActive = true,
            ),
        )
        assertEquals(
            56,
            resolvePanelCardHeightDp(
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
