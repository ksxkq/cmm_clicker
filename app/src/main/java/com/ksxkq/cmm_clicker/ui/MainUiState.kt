package com.ksxkq.cmm_clicker.ui

import com.ksxkq.cmm_clicker.feature.debug.RuntimeRunReportSummary
import com.ksxkq.cmm_clicker.feature.task.TaskRecord
import com.ksxkq.cmm_clicker.ui.theme.AppThemeMode

data class MainUiState(
    val themeMode: AppThemeMode = AppThemeMode.MONO_LIGHT,
    val tasks: List<TaskRecord> = emptyList(),
    val selectedTaskId: String? = null,
    val taskOperationMessage: String = "",
    val accessibilityEnabledInSettings: Boolean = false,
    val accessibilityServiceConnected: Boolean = false,
    val accessibilityEventCount: Int = 0,
    val canWriteSecureSettings: Boolean = false,
    val autoEnableMessage: String = "",
    val gestureStats: String = "",
    val dryRun: Boolean = false,
    val doSwipeBranch: Boolean = false,
    val running: Boolean = false,
    val lastRunSummary: String = "未运行",
    val lastRunTrace: String = "",
    val runtimeReportMessage: String = "",
    val runtimeReportHistory: List<RuntimeRunReportSummary> = emptyList(),
)
