package com.ksxkq.cmm_clicker.ui

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.ksxkq.cmm_clicker.ui.theme.CmmClickerTheme

class MainActivity : ComponentActivity() {
    private val viewModel by viewModels<MainViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val uiState = viewModel.uiState
            CmmClickerTheme(themeMode = uiState.themeMode) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MainTabsRoute(
                        themeMode = uiState.themeMode,
                        taskOperationMessage = uiState.taskOperationMessage,
                        running = uiState.running,
                        activeTaskName = uiState.tasks.firstOrNull { it.taskId == uiState.selectedTaskId }?.name ?: "-",
                        accessibilityEnabledInSettings = uiState.accessibilityEnabledInSettings,
                        accessibilityServiceConnected = uiState.accessibilityServiceConnected,
                        accessibilityEventCount = uiState.accessibilityEventCount,
                        canWriteSecureSettings = uiState.canWriteSecureSettings,
                        autoEnableMessage = uiState.autoEnableMessage,
                        gestureStats = uiState.gestureStats,
                        dryRun = uiState.dryRun,
                        doSwipeBranch = uiState.doSwipeBranch,
                        lastRunSummary = uiState.lastRunSummary,
                        lastRunTrace = uiState.lastRunTrace,
                        runtimeReportMessage = uiState.runtimeReportMessage,
                        runtimeReportHistory = uiState.runtimeReportHistory,
                        onThemeModeToggle = viewModel::toggleThemeMode,
                        onOpenTaskOverlay = viewModel::openTaskOverlay,
                        onOpenAccessibilitySettings = { openAccessibilitySettings() },
                        onAutoEnableAccessibility = viewModel::autoEnableAccessibilityService,
                        onRefreshStatus = { viewModel.refreshPermissionStatus(attemptAutoEnable = true) },
                        onShowControlPanel = viewModel::showControlPanel,
                        onHideControlPanel = viewModel::hideControlPanel,
                        onDryRunChanged = viewModel::setDryRun,
                        onDoSwipeBranchChanged = viewModel::setDoSwipeBranch,
                        onRunCurrentTask = viewModel::runCurrentTask,
                        onCopyLatestRunReport = viewModel::copyLatestRunReportToClipboard,
                        onCopyRunReport = viewModel::copyRunReportToClipboard,
                        onRefreshRunReports = viewModel::refreshRuntimeRunReports,
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.onResumeRefresh()
    }

    private fun openAccessibilitySettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }
}
