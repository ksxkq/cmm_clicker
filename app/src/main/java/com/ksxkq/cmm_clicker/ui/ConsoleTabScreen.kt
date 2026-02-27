package com.ksxkq.cmm_clicker.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ConsoleTabScreen(
    accessibilityEnabledInSettings: Boolean,
    accessibilityServiceConnected: Boolean,
    accessibilityEventCount: Int,
    canWriteSecureSettings: Boolean,
    autoEnableMessage: String,
    gestureStats: String,
    dryRun: Boolean,
    doSwipeBranch: Boolean,
    running: Boolean,
    activeTaskName: String,
    lastRunSummary: String,
    lastRunTrace: String,
    onOpenAccessibilitySettings: () -> Unit,
    onAutoEnableAccessibility: () -> Unit,
    onRefreshStatus: () -> Unit,
    onShowControlPanel: () -> Unit,
    onHideControlPanel: () -> Unit,
    onDryRunChanged: (Boolean) -> Unit,
    onDoSwipeBranchChanged: (Boolean) -> Unit,
    onRunCurrentTask: () -> Unit,
) {
    SectionCard(title = "辅助服务状态") {
        StatusLine(
            label = "系统设置",
            ok = accessibilityEnabledInSettings,
            detail = if (accessibilityEnabledInSettings) "已开启" else "未开启",
        )
        StatusLine(
            label = "服务连接",
            ok = accessibilityServiceConnected,
            detail = if (accessibilityServiceConnected) "已连接" else "未连接",
        )
        StatusLine(
            label = "WRITE_SECURE_SETTINGS",
            ok = canWriteSecureSettings,
            detail = if (canWriteSecureSettings) "已授权，可自动开启" else "未授权，需要 adb grant",
        )
        Text(
            text = "事件计数：$accessibilityEventCount",
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = "手势分发：$gestureStats",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (autoEnableMessage.isNotBlank()) {
            Text(
                text = "自动开启结果：$autoEnableMessage",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    SectionCard(title = "系统操作") {
        ActionButton(text = "自动开启辅助服务", enabled = canWriteSecureSettings, onClick = onAutoEnableAccessibility)
        ActionButton(text = "打开辅助服务设置", onClick = onOpenAccessibilitySettings)
        ActionButton(text = "刷新状态", onClick = onRefreshStatus)
        ActionButton(text = "打开操作面板", enabled = accessibilityServiceConnected, onClick = onShowControlPanel)
        ActionButton(text = "关闭操作面板", enabled = accessibilityServiceConnected, onClick = onHideControlPanel)
        SwitchRow(
            title = "Dry Run（不真实点击）",
            checked = dryRun,
            onCheckedChange = onDryRunChanged,
        )
        SwitchRow(
            title = "分支走 Swipe",
            checked = doSwipeBranch,
            onCheckedChange = onDoSwipeBranchChanged,
        )
    }

    SectionCard(title = "流程运行") {
        Text(
            text = "当前任务：$activeTaskName",
            style = MaterialTheme.typography.bodyMedium,
        )
        Button(
            onClick = onRunCurrentTask,
            enabled = !running,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text = if (running) "运行中..." else "运行当前任务")
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "最近运行：$lastRunSummary",
            style = MaterialTheme.typography.bodyMedium,
        )
        if (lastRunTrace.isNotBlank()) {
            Text(
                text = "Trace（最近10条）\n$lastRunTrace",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
