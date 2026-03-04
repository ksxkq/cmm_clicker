package com.ksxkq.cmm_clicker.accessibility

internal sealed interface SettingsRoute {
    data object TaskList : SettingsRoute

    data object ActionList : SettingsRoute

    data object RunHistory : SettingsRoute

    data object ReportHistory : SettingsRoute

    data class ReportHistoryDetail(
        val reportId: String,
    ) : SettingsRoute

    data class NodeEditor(
        val nodeId: String,
    ) : SettingsRoute
}

internal fun settingsRouteActionLayerTitle(route: SettingsRoute): String {
    return when (route) {
        SettingsRoute.RunHistory -> "本次执行历史"
        SettingsRoute.ReportHistory -> "历史记录"
        is SettingsRoute.ReportHistoryDetail -> "历史记录详情"
        else -> "动作列表"
    }
}

internal fun settingsRouteOnActionLayerBack(route: SettingsRoute): SettingsRoute {
    return when (route) {
        is SettingsRoute.ReportHistoryDetail -> SettingsRoute.ReportHistory
        else -> SettingsRoute.TaskList
    }
}

internal fun settingsRouteOnBackdropTap(route: SettingsRoute): SettingsRoute? {
    return when (route) {
        SettingsRoute.TaskList -> null
        SettingsRoute.ActionList,
        SettingsRoute.RunHistory,
        SettingsRoute.ReportHistory,
        -> SettingsRoute.TaskList

        is SettingsRoute.ReportHistoryDetail -> SettingsRoute.ReportHistory
        is SettingsRoute.NodeEditor -> SettingsRoute.ActionList
    }
}
