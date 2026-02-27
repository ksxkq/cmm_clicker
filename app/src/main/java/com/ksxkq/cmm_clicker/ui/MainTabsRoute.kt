package com.ksxkq.cmm_clicker.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ksxkq.cmm_clicker.feature.task.TaskRecord
import com.ksxkq.cmm_clicker.ui.theme.AppThemeMode

enum class MainTab(val label: String) {
    TASKS("任务"),
    CONSOLE("控制台"),
}

@Composable
fun MainTabsRoute(
    themeMode: AppThemeMode,
    tasks: List<TaskRecord>,
    taskOperationMessage: String,
    running: Boolean,
    activeTaskName: String,
    accessibilityEnabledInSettings: Boolean,
    accessibilityServiceConnected: Boolean,
    accessibilityEventCount: Int,
    canWriteSecureSettings: Boolean,
    autoEnableMessage: String,
    gestureStats: String,
    dryRun: Boolean,
    doSwipeBranch: Boolean,
    lastRunSummary: String,
    lastRunTrace: String,
    onThemeModeToggle: () -> Unit,
    onCreateTask: (String) -> Unit,
    onOpenTaskOverlay: (String) -> Unit,
    onRenameTask: (String, String) -> Unit,
    onDuplicateTask: (String) -> Unit,
    onDeleteTask: (String) -> Unit,
    onRunTask: (String) -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onAutoEnableAccessibility: () -> Unit,
    onRefreshStatus: () -> Unit,
    onShowControlPanel: () -> Unit,
    onHideControlPanel: () -> Unit,
    onDryRunChanged: (Boolean) -> Unit,
    onDoSwipeBranchChanged: (Boolean) -> Unit,
    onRunCurrentTask: () -> Unit,
) {
    var tab by rememberSaveable { mutableStateOf(MainTab.TASKS) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            IosTabBar(
                selected = tab,
                onSelect = { tab = it },
            )
        },
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "cmm_clicker",
                        style = MaterialTheme.typography.headlineMedium,
                    )
                    Text(
                        text = "黑白极简任务自动化",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OutlinedButton(onClick = onThemeModeToggle) {
                    Text(text = themeMode.displayName)
                }
            }

            when (tab) {
                MainTab.TASKS -> TaskTabScreen(
                    tasks = tasks,
                    running = running,
                    taskOperationMessage = taskOperationMessage,
                    onCreateTask = onCreateTask,
                    onOpenTaskOverlay = onOpenTaskOverlay,
                    onRenameTask = onRenameTask,
                    onDuplicateTask = onDuplicateTask,
                    onDeleteTask = onDeleteTask,
                    onRunTask = onRunTask,
                )

                MainTab.CONSOLE -> ConsoleTabScreen(
                    accessibilityEnabledInSettings = accessibilityEnabledInSettings,
                    accessibilityServiceConnected = accessibilityServiceConnected,
                    accessibilityEventCount = accessibilityEventCount,
                    canWriteSecureSettings = canWriteSecureSettings,
                    autoEnableMessage = autoEnableMessage,
                    gestureStats = gestureStats,
                    dryRun = dryRun,
                    doSwipeBranch = doSwipeBranch,
                    running = running,
                    activeTaskName = activeTaskName,
                    lastRunSummary = lastRunSummary,
                    lastRunTrace = lastRunTrace,
                    onOpenAccessibilitySettings = onOpenAccessibilitySettings,
                    onAutoEnableAccessibility = onAutoEnableAccessibility,
                    onRefreshStatus = onRefreshStatus,
                    onShowControlPanel = onShowControlPanel,
                    onHideControlPanel = onHideControlPanel,
                    onDryRunChanged = onDryRunChanged,
                    onDoSwipeBranchChanged = onDoSwipeBranchChanged,
                    onRunCurrentTask = onRunCurrentTask,
                )
            }

            Text(
                text = "说明：授权后回到本页会自动刷新状态。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun IosTabBar(
    selected: MainTab,
    onSelect: (MainTab) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 18.dp, vertical = 8.dp),
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                MainTab.entries.forEach { tab ->
                    IosTabBarItem(
                        tab = tab,
                        selected = selected == tab,
                        onClick = { onSelect(tab) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun IosTabBarItem(
    tab: MainTab,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bgColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
        animationSpec = spring(dampingRatio = 0.9f, stiffness = 500f),
        label = "ios_tab_bg",
    )
    val fgColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = spring(dampingRatio = 0.9f, stiffness = 500f),
        label = "ios_tab_fg",
    )

    Row(
        modifier = modifier
            .height(46.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(color = bgColor, shape = RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MainTabIcon(
            tab = tab,
            tint = fgColor,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = tab.label,
            color = fgColor,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}

@Composable
private fun MainTabIcon(
    tab: MainTab,
    tint: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        if (tab == MainTab.TASKS) {
            val cell = size.minDimension * 0.32f
            val gap = size.minDimension * 0.08f
            val startX = (size.width - (cell * 2f + gap)) / 2f
            val startY = (size.height - (cell * 2f + gap)) / 2f
            repeat(2) { row ->
                repeat(2) { col ->
                    drawRoundRect(
                        color = tint,
                        topLeft = Offset(
                            x = startX + col * (cell + gap),
                            y = startY + row * (cell + gap),
                        ),
                        size = Size(cell, cell),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(cell * 0.24f, cell * 0.24f),
                        style = Stroke(width = size.minDimension * 0.08f),
                    )
                }
            }
        } else {
            val left = size.width * 0.18f
            val right = size.width * 0.82f
            val ys = listOf(0.28f, 0.5f, 0.72f)
            ys.forEachIndexed { index, ratio ->
                val y = size.height * ratio
                drawLine(
                    color = tint,
                    start = Offset(left, y),
                    end = Offset(right, y),
                    strokeWidth = size.minDimension * 0.1f,
                    cap = StrokeCap.Round,
                )
                val knobX = if (index % 2 == 0) size.width * 0.34f else size.width * 0.66f
                drawCircle(
                    color = tint,
                    radius = size.minDimension * 0.095f,
                    center = Offset(knobX, y),
                )
            }
        }
    }
}
