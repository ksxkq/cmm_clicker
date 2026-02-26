package com.ksxkq.cmm_clicker.ui

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Surface
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
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
        refreshPermissionStatus(attemptAutoEnable = true)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    HomeScreen(
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
}

@Composable
private fun HomeScreen(
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
            .padding(horizontal = 20.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = "cmm_clicker",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "辅助服务：$serviceName",
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = if (accessibilityEnabledInSettings) "系统设置状态：已开启" else "系统设置状态：未开启",
            style = MaterialTheme.typography.bodyLarge,
            color = if (accessibilityEnabledInSettings) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
        )
        Text(
            text = if (accessibilityServiceConnected) "服务连接状态：已连接" else "服务连接状态：未连接",
            style = MaterialTheme.typography.bodyLarge,
            color = if (accessibilityServiceConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
        )
        Text(
            text = "事件计数：$accessibilityEventCount",
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            text = if (canWriteSecureSettings) {
                "WRITE_SECURE_SETTINGS：已授权（可自动开启辅助服务）"
            } else {
                "WRITE_SECURE_SETTINGS：未授权（需先执行 adb grant）"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = if (canWriteSecureSettings) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
        )
        if (autoEnableMessage.isNotBlank()) {
            Text(
                text = "自动开启结果：$autoEnableMessage",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Button(
            onClick = onAutoEnableAccessibility,
            enabled = canWriteSecureSettings,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text = "自动开启辅助服务")
        }
        Button(
            onClick = onOpenAccessibilitySettings,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text = "打开辅助服务设置")
        }
        Button(
            onClick = onRefreshStatus,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text = "刷新状态")
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(text = "Dry Run（不真实点击）")
            Switch(checked = dryRun, onCheckedChange = onDryRunChanged)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(text = "分支走 Swipe")
            Switch(checked = doSwipeBranch, onCheckedChange = onDoSwipeBranchChanged)
        }
        Button(
            onClick = onRunDemoFlow,
            enabled = !running,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text = if (running) "运行中..." else "运行测试流程")
        }
        Text(
            text = "最近运行：$lastRunSummary",
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = "手势分发：$gestureStats",
            style = MaterialTheme.typography.bodySmall,
        )
        if (lastRunTrace.isNotBlank()) {
            Text(
                text = "Trace（最近10条）\n$lastRunTrace",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Text(
            text = "说明：完成授权后返回本页，状态会自动刷新。",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
