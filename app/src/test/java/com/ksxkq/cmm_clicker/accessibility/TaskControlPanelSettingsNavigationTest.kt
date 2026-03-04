package com.ksxkq.cmm_clicker.accessibility

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TaskControlPanelSettingsNavigationTest {

    @Test
    fun `settingsRouteActionLayerTitle returns expected labels`() {
        assertEquals("动作列表", settingsRouteActionLayerTitle(SettingsRoute.ActionList))
        assertEquals("本次执行历史", settingsRouteActionLayerTitle(SettingsRoute.RunHistory))
        assertEquals("历史记录", settingsRouteActionLayerTitle(SettingsRoute.ReportHistory))
        assertEquals(
            "历史记录详情",
            settingsRouteActionLayerTitle(SettingsRoute.ReportHistoryDetail("r-1")),
        )
    }

    @Test
    fun `settingsRouteOnActionLayerBack maps detail to history and others to task list`() {
        assertEquals(
            SettingsRoute.ReportHistory,
            settingsRouteOnActionLayerBack(SettingsRoute.ReportHistoryDetail("r-1")),
        )
        assertEquals(
            SettingsRoute.TaskList,
            settingsRouteOnActionLayerBack(SettingsRoute.ActionList),
        )
        assertEquals(
            SettingsRoute.TaskList,
            settingsRouteOnActionLayerBack(SettingsRoute.RunHistory),
        )
    }

    @Test
    fun `settingsRouteOnBackdropTap maps root to null and children to parent route`() {
        assertNull(settingsRouteOnBackdropTap(SettingsRoute.TaskList))
        assertEquals(
            SettingsRoute.TaskList,
            settingsRouteOnBackdropTap(SettingsRoute.ActionList),
        )
        assertEquals(
            SettingsRoute.TaskList,
            settingsRouteOnBackdropTap(SettingsRoute.ReportHistory),
        )
        assertEquals(
            SettingsRoute.ReportHistory,
            settingsRouteOnBackdropTap(SettingsRoute.ReportHistoryDetail("r-1")),
        )
        assertEquals(
            SettingsRoute.ActionList,
            settingsRouteOnBackdropTap(SettingsRoute.NodeEditor("n-1")),
        )
    }
}
