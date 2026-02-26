package com.ksxkq.cmm_clicker.ui

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.ksxkq.cmm_clicker.accessibility.AccessibilityPermissionChecker
import com.ksxkq.cmm_clicker.accessibility.TaskAccessibilityService
import com.ksxkq.cmm_clicker.core.runtime.FlowRuntimeEngine
import com.ksxkq.cmm_clicker.core.runtime.RuntimeEngineOptions
import com.ksxkq.cmm_clicker.core.runtime.SampleFlowBundleFactory
import com.ksxkq.cmm_clicker.ui.theme.AppThemeMode
import com.ksxkq.cmm_clicker.ui.theme.CmmClickerTheme
import com.ksxkq.cmm_clicker.ui.theme.ThemePreferenceStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private val themePreferenceStore by lazy { ThemePreferenceStore(applicationContext) }
    private var themeMode by mutableStateOf(AppThemeMode.MONO_LIGHT)
    private var accessibilityEnabledInSettings by mutableStateOf(false)
    private var accessibilityServiceConnected by mutableStateOf(false)
    private var accessibilityEventCount by mutableStateOf(0)
    private var canWriteSecureSettings by mutableStateOf(false)
    private var autoEnableMessage by mutableStateOf("")
    private var gestureStats by mutableStateOf(TaskAccessibilityService.gestureStatsText())
    private var dryRun by mutableStateOf(true)
    private var doSwipeBranch by mutableStateOf(false)
    private var running by mutableStateOf(false)
    private var lastRunSummary by mutableStateOf("未运行")
    private var lastRunTrace by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        lifecycleScope.launch {
            themeMode = themePreferenceStore.themeModeFlow.first()
        }
        refreshPermissionStatus(attemptAutoEnable = true)
        setContent {
            CmmClickerTheme(themeMode = themeMode) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    HomeScreen(
                        themeMode = themeMode,
                        accessibilityEnabledInSettings = accessibilityEnabledInSettings,
                        accessibilityServiceConnected = accessibilityServiceConnected,
                        accessibilityEventCount = accessibilityEventCount,
                        canWriteSecureSettings = canWriteSecureSettings,
                        autoEnableMessage = autoEnableMessage,
                        gestureStats = gestureStats,
                        dryRun = dryRun,
                        doSwipeBranch = doSwipeBranch,
                        running = running,
                        lastRunSummary = lastRunSummary,
                        lastRunTrace = lastRunTrace,
                        onThemeModeToggle = { toggleThemeMode() },
                        onOpenAccessibilitySettings = { openAccessibilitySettings() },
                        onAutoEnableAccessibility = { autoEnableAccessibilityService() },
                        onRefreshStatus = { refreshPermissionStatus(attemptAutoEnable = true) },
                        onDryRunChanged = { dryRun = it },
                        onDoSwipeBranchChanged = { doSwipeBranch = it },
                        onRunDemoFlow = { runDemoFlow() },
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshPermissionStatus(attemptAutoEnable = true)
    }

    private fun refreshPermissionStatus(attemptAutoEnable: Boolean = false) {
        canWriteSecureSettings = AccessibilityPermissionChecker.hasWriteSecureSettingsPermission(this)
        accessibilityEnabledInSettings = AccessibilityPermissionChecker.isServiceEnabled(
            context = this,
            serviceClass = TaskAccessibilityService::class.java,
        )
        if (attemptAutoEnable && canWriteSecureSettings && !accessibilityEnabledInSettings) {
            val result = AccessibilityPermissionChecker.tryEnableServiceBySecureSettings(
                context = this,
                serviceClass = TaskAccessibilityService::class.java,
            )
            autoEnableMessage = result.message
            accessibilityEnabledInSettings = AccessibilityPermissionChecker.isServiceEnabled(
                context = this,
                serviceClass = TaskAccessibilityService::class.java,
            )
        }
        accessibilityServiceConnected = TaskAccessibilityService.isConnected
        accessibilityEventCount = TaskAccessibilityService.eventCount()
        gestureStats = TaskAccessibilityService.gestureStatsText()
    }

    private fun openAccessibilitySettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    private fun autoEnableAccessibilityService() {
        val result = AccessibilityPermissionChecker.tryEnableServiceBySecureSettings(
            context = this,
            serviceClass = TaskAccessibilityService::class.java,
        )
        autoEnableMessage = result.message
        refreshPermissionStatus(attemptAutoEnable = false)
    }

    private fun runDemoFlow() {
        if (running) {
            return
        }
        running = true
        lastRunSummary = "运行中..."
        lifecycleScope.launch {
            try {
                val bundle = SampleFlowBundleFactory.createSimpleDemoBundle()
                val result = withContext(Dispatchers.Default) {
                    FlowRuntimeEngine(
                        options = RuntimeEngineOptions(
                            dryRun = dryRun,
                            maxSteps = 200,
                            stopOnValidationError = true,
                        ),
                    ).execute(
                        bundle = bundle,
                        initialVariables = mapOf("doSwipe" to doSwipeBranch),
                    )
                }
                val summary = buildString {
                    append("状态=${result.status} ")
                    append("step=${result.stepCount} ")
                    append("msg=${result.message ?: "-"}")
                }
                val tracePreview = result.traceEvents
                    .takeLast(10)
                    .joinToString(separator = "\n") { event ->
                        "${event.step}. ${event.phase} ${event.flowId}/${event.nodeId} ${event.message ?: ""}".trim()
                    }
                lastRunSummary = summary
                lastRunTrace = tracePreview
            } catch (e: Exception) {
                lastRunSummary = "运行异常: ${e.message ?: "unknown"}"
                lastRunTrace = ""
            } finally {
                running = false
                refreshPermissionStatus(attemptAutoEnable = false)
            }
        }
    }

    private fun toggleThemeMode() {
        val nextMode = themeMode.next()
        themeMode = nextMode
        lifecycleScope.launch {
            themePreferenceStore.saveThemeMode(nextMode)
        }
    }
}

@Composable
private fun HomeScreen(
    themeMode: AppThemeMode,
    accessibilityEnabledInSettings: Boolean,
    accessibilityServiceConnected: Boolean,
    accessibilityEventCount: Int,
    canWriteSecureSettings: Boolean,
    autoEnableMessage: String,
    gestureStats: String,
    dryRun: Boolean,
    doSwipeBranch: Boolean,
    running: Boolean,
    lastRunSummary: String,
    lastRunTrace: String,
    onThemeModeToggle: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onAutoEnableAccessibility: () -> Unit,
    onRefreshStatus: () -> Unit,
    onDryRunChanged: (Boolean) -> Unit,
    onDoSwipeBranchChanged: (Boolean) -> Unit,
    onRunDemoFlow: () -> Unit,
) {
    val context = LocalContext.current
    val serviceName = context.getString(com.ksxkq.cmm_clicker.R.string.accessibility_service_name)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "cmm_clicker",
                    style = MaterialTheme.typography.headlineMedium,
                )
                Text(
                    text = "黑白极简控制台",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedButton(onClick = onThemeModeToggle) {
                Text(text = themeMode.displayName)
            }
        }

        SectionCard(
            title = "辅助服务状态",
            subtitle = serviceName,
        ) {
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
            Button(
                onClick = onRunDemoFlow,
                enabled = !running,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = if (running) "运行中..." else "运行测试流程")
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

        Text(
            text = "说明：授权后回到本页会自动刷新状态。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun SectionCard(
    title: String,
    subtitle: String? = null,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            content()
        }
    }
}

@Composable
private fun StatusLine(
    label: String,
    ok: Boolean,
    detail: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = if (ok) "[OK]" else "[OFF]",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
        )
    }
    Text(
        text = detail,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun ActionButton(
    text: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Text(text = text)
    }
}

@Composable
private fun SwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                checkedBorderColor = MaterialTheme.colorScheme.primary,
                uncheckedThumbColor = MaterialTheme.colorScheme.onSurface,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                uncheckedBorderColor = MaterialTheme.colorScheme.outline,
            ),
        )
    }
}
