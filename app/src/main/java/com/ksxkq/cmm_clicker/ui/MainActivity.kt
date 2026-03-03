package com.ksxkq.cmm_clicker.ui

import android.content.Intent
import android.content.ClipData
import android.content.ClipboardManager
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.ksxkq.cmm_clicker.accessibility.AccessibilityPermissionChecker
import com.ksxkq.cmm_clicker.accessibility.TaskAccessibilityService
import com.ksxkq.cmm_clicker.core.runtime.FlowRuntimeEngine
import com.ksxkq.cmm_clicker.core.runtime.RuntimeEngineOptions
import com.ksxkq.cmm_clicker.core.runtime.RuntimeRunReport
import com.ksxkq.cmm_clicker.core.runtime.SampleFlowBundleFactory
import com.ksxkq.cmm_clicker.feature.debug.RuntimeRunReportRepository
import com.ksxkq.cmm_clicker.feature.debug.RuntimeRunReportSummary
import com.ksxkq.cmm_clicker.feature.editor.TaskGraphEditorStore
import com.ksxkq.cmm_clicker.feature.task.LocalFileTaskRepository
import com.ksxkq.cmm_clicker.feature.task.TaskRecord
import com.ksxkq.cmm_clicker.ui.theme.AppThemeMode
import com.ksxkq.cmm_clicker.ui.theme.CmmClickerTheme
import com.ksxkq.cmm_clicker.ui.theme.ThemePreferenceStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private val themePreferenceStore by lazy { ThemePreferenceStore(applicationContext) }
    private val taskRepository by lazy { LocalFileTaskRepository(applicationContext) }
    private val runtimeRunReportRepository by lazy { RuntimeRunReportRepository(applicationContext) }
    private val editorStore by lazy {
        TaskGraphEditorStore(
            initialBundle = SampleFlowBundleFactory.createSimpleDemoBundle(),
        )
    }

    private var themeMode by mutableStateOf(AppThemeMode.MONO_LIGHT)
    private var taskRecords by mutableStateOf<List<TaskRecord>>(emptyList())
    private var selectedTaskId by mutableStateOf<String?>(null)
    private var taskOperationMessage by mutableStateOf("")
    private var accessibilityEnabledInSettings by mutableStateOf(false)
    private var accessibilityServiceConnected by mutableStateOf(false)
    private var accessibilityEventCount by mutableStateOf(0)
    private var canWriteSecureSettings by mutableStateOf(false)
    private var autoEnableMessage by mutableStateOf("")
    private var gestureStats by mutableStateOf(TaskAccessibilityService.gestureStatsText())
    private var dryRun by mutableStateOf(false)
    private var doSwipeBranch by mutableStateOf(false)
    private var running by mutableStateOf(false)
    private var lastRunSummary by mutableStateOf("未运行")
    private var lastRunTrace by mutableStateOf("")
    private var runtimeReportMessage by mutableStateOf("")
    private var runtimeReportHistory by mutableStateOf<List<RuntimeRunReportSummary>>(emptyList())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        lifecycleScope.launch {
            themeMode = themePreferenceStore.themeModeFlow.first()
        }
        lifecycleScope.launch {
            loadTasksFromRepository()
        }
        refreshRuntimeRunReports()
        refreshPermissionStatus(attemptAutoEnable = true)
        setContent {
            CmmClickerTheme(themeMode = themeMode) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MainTabsRoute(
                        themeMode = themeMode,
                        tasks = taskRecords,
                        taskOperationMessage = taskOperationMessage,
                        running = running,
                        activeTaskName = taskRecords.firstOrNull { it.taskId == selectedTaskId }?.name ?: "-",
                        accessibilityEnabledInSettings = accessibilityEnabledInSettings,
                        accessibilityServiceConnected = accessibilityServiceConnected,
                        accessibilityEventCount = accessibilityEventCount,
                        canWriteSecureSettings = canWriteSecureSettings,
                        autoEnableMessage = autoEnableMessage,
                        gestureStats = gestureStats,
                        dryRun = dryRun,
                        doSwipeBranch = doSwipeBranch,
                        lastRunSummary = lastRunSummary,
                        lastRunTrace = lastRunTrace,
                        runtimeReportMessage = runtimeReportMessage,
                        runtimeReportHistory = runtimeReportHistory,
                        onThemeModeToggle = { toggleThemeMode() },
                        onCreateTask = { name -> createTask(name) },
                        onOpenTaskOverlay = { taskId -> openTaskOverlay(taskId) },
                        onRenameTask = { taskId, name -> renameTask(taskId, name) },
                        onDuplicateTask = { taskId -> duplicateTask(taskId) },
                        onDeleteTask = { taskId -> deleteTask(taskId) },
                        onRunTask = { taskId ->
                            selectTask(taskId = taskId)
                            runTaskById(taskId)
                        },
                        onOpenAccessibilitySettings = { openAccessibilitySettings() },
                        onAutoEnableAccessibility = { autoEnableAccessibilityService() },
                        onRefreshStatus = { refreshPermissionStatus(attemptAutoEnable = true) },
                        onShowControlPanel = {
                            val opened = TaskAccessibilityService.instance?.showTaskControlPanelOverlay() == true
                            taskOperationMessage = if (opened) {
                                "操作面板已打开"
                            } else {
                                "辅助服务未连接，无法打开操作面板"
                            }
                        },
                        onHideControlPanel = {
                            TaskAccessibilityService.instance?.hideTaskControlPanelOverlay()
                            taskOperationMessage = "操作面板已关闭"
                        },
                        onDryRunChanged = { dryRun = it },
                        onDoSwipeBranchChanged = { doSwipeBranch = it },
                        onRunCurrentTask = { runCurrentTask() },
                        onCopyLatestRunReport = { copyLatestRunReportToClipboard() },
                        onCopyRunReport = { reportId -> copyRunReportToClipboard(reportId) },
                        onRefreshRunReports = { refreshRuntimeRunReports() },
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshPermissionStatus(attemptAutoEnable = true)
        refreshRuntimeRunReports()
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

    private suspend fun loadTasksFromRepository(
        preferredTaskId: String? = selectedTaskId,
    ) {
        val tasks = withContext(Dispatchers.IO) {
            taskRepository.listTasks()
        }
        taskRecords = tasks
        if (tasks.isEmpty()) {
            selectedTaskId = null
            taskOperationMessage = "暂无任务"
            return
        }
        val selected = tasks.firstOrNull { it.taskId == preferredTaskId } ?: tasks.first()
        selectedTaskId = selected.taskId
        editorStore.reset(selected.bundle)
    }

    private fun createTask(name: String) {
        lifecycleScope.launch {
            val created = withContext(Dispatchers.IO) {
                taskRepository.createTask(name = name, withTemplate = true)
            }
            taskRecords = (taskRecords + created).sortedByDescending { it.updatedAtEpochMs }
            selectTask(taskId = created.taskId)
            taskOperationMessage = "已新建任务：${created.name}"
        }
    }

    private fun openTaskOverlay(taskId: String) {
        if (taskId.isNotBlank()) {
            selectTask(taskId = taskId)
        }
        val service = TaskAccessibilityService.instance
        if (service == null || !TaskAccessibilityService.isConnected) {
            taskOperationMessage = "辅助服务未连接，无法打开浮窗任务列表"
            return
        }
        val preferredTaskId = taskId.takeIf { it.isNotBlank() }
        val opened = service.showTaskListOverlay(preferredTaskId = preferredTaskId)
        if (opened) {
            taskOperationMessage = "浮窗任务列表已打开，请在浮窗中编辑任务"
        } else {
            taskOperationMessage = "打开浮窗任务列表失败"
        }
    }

    private fun selectTask(taskId: String) {
        if (selectedTaskId != null && selectedTaskId != taskId) {
            saveSelectedTaskNow(showMessage = false)
        }
        val selected = taskRecords.firstOrNull { it.taskId == taskId } ?: return
        selectedTaskId = selected.taskId
        editorStore.reset(selected.bundle)
    }

    private fun renameTask(taskId: String, name: String) {
        lifecycleScope.launch {
            val updated = withContext(Dispatchers.IO) {
                taskRepository.renameTask(taskId = taskId, name = name)
            } ?: return@launch
            updateTaskRecordInState(updated)
            if (selectedTaskId == taskId) {
                editorStore.reset(updated.bundle)
            }
            taskOperationMessage = "已重命名：${updated.name}"
        }
    }

    private fun duplicateTask(taskId: String) {
        lifecycleScope.launch {
            val duplicated = withContext(Dispatchers.IO) {
                taskRepository.duplicateTask(taskId = taskId)
            } ?: return@launch
            taskRecords = (taskRecords + duplicated).sortedByDescending { it.updatedAtEpochMs }
            taskOperationMessage = "已复制任务：${duplicated.name}"
        }
    }

    private fun deleteTask(taskId: String) {
        lifecycleScope.launch {
            val deleted = withContext(Dispatchers.IO) {
                taskRepository.deleteTask(taskId = taskId)
            }
            if (!deleted) {
                return@launch
            }
            val remaining = taskRecords.filterNot { it.taskId == taskId }
            taskRecords = remaining
            if (selectedTaskId == taskId) {
                val fallback = remaining.firstOrNull()
                if (fallback != null) {
                    selectTask(taskId = fallback.taskId)
                } else {
                    selectedTaskId = null
                }
            }
            taskOperationMessage = "已删除任务"
        }
    }

    private fun saveSelectedTaskNow(showMessage: Boolean) {
        val taskId = selectedTaskId ?: return
        val current = editorStore.state().bundle
        val taskName = taskRecords.firstOrNull { it.taskId == taskId }?.name ?: current.name
        val bundleToSave = current.copy(
            bundleId = taskId,
            name = taskName,
        )
        lifecycleScope.launch {
            val updated = withContext(Dispatchers.IO) {
                taskRepository.updateTaskBundle(taskId = taskId, bundle = bundleToSave)
            } ?: return@launch
            updateTaskRecordInState(updated)
            if (showMessage) {
                taskOperationMessage = "已保存任务：${updated.name}"
            }
        }
    }

    private fun updateTaskRecordInState(record: TaskRecord) {
        val exists = taskRecords.any { it.taskId == record.taskId }
        taskRecords = if (exists) {
            taskRecords.map { item ->
                if (item.taskId == record.taskId) record else item
            }
        } else {
            taskRecords + record
        }.sortedByDescending { it.updatedAtEpochMs }
    }

    private fun runCurrentTask() {
        val taskId = selectedTaskId ?: return
        runTaskById(taskId)
    }

    private fun runTaskById(taskId: String) {
        val record = taskRecords.firstOrNull { it.taskId == taskId } ?: return
        val bundle = if (selectedTaskId == taskId) {
            editorStore.state().bundle.copy(
                bundleId = taskId,
                name = record.name,
            )
        } else {
            record.bundle
        }
        runTaskBundle(taskId = taskId, bundle = bundle)
    }

    private fun runTaskBundle(taskId: String, bundle: com.ksxkq.cmm_clicker.core.model.TaskBundle) {
        if (running) {
            return
        }
        running = true
        lastRunSummary = "运行中..."
        lifecycleScope.launch {
            try {
                val startedAtMs = System.currentTimeMillis()
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
                val finishedAtMs = System.currentTimeMillis()
                val summary = buildString {
                    append("模式=${if (dryRun) "DRY_RUN" else "REAL"} ")
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
                val report = RuntimeRunReport.fromExecution(
                    source = "main_console",
                    taskId = taskId,
                    taskName = taskRecords.firstOrNull { it.taskId == taskId }?.name ?: bundle.name,
                    dryRun = dryRun,
                    startedAtEpochMs = startedAtMs,
                    finishedAtEpochMs = finishedAtMs,
                    result = result,
                )
                val updated = withContext(Dispatchers.IO) {
                    runCatching { runtimeRunReportRepository.append(report) }
                    taskRepository.updateTaskRunInfo(
                        taskId = taskId,
                        status = result.status.name,
                        summary = summary,
                    )
                }
                if (updated != null) {
                    updateTaskRecordInState(updated)
                }
                refreshRuntimeRunReports()
            } catch (e: Exception) {
                lastRunSummary = "运行异常: ${e.message ?: "unknown"}"
                lastRunTrace = ""
            } finally {
                running = false
                refreshPermissionStatus(attemptAutoEnable = false)
            }
        }
    }

    private fun copyLatestRunReportToClipboard() {
        lifecycleScope.launch {
            val latest = withContext(Dispatchers.IO) {
                runtimeRunReportRepository.latestJson()
            }
            if (latest.isNullOrBlank()) {
                runtimeReportMessage = "暂无可复制的运行报告"
                return@launch
            }
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("runtime_run_report", latest))
            runtimeReportMessage = "已复制最近运行报告(JSON)"
        }
    }

    private fun copyRunReportToClipboard(reportId: String) {
        lifecycleScope.launch {
            val raw = withContext(Dispatchers.IO) {
                runtimeRunReportRepository.findJsonByReportId(reportId)
            }
            if (raw.isNullOrBlank()) {
                runtimeReportMessage = "目标运行报告不存在或已过期"
                return@launch
            }
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("runtime_run_report", raw))
            runtimeReportMessage = "已复制运行报告(JSON): $reportId"
        }
    }

    private fun refreshRuntimeRunReports() {
        lifecycleScope.launch {
            val history = withContext(Dispatchers.IO) {
                runtimeRunReportRepository.listLatestSummaries(limit = 10)
            }
            runtimeReportHistory = history
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
