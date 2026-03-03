package com.ksxkq.cmm_clicker.ui

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ksxkq.cmm_clicker.accessibility.AccessibilityPermissionChecker
import com.ksxkq.cmm_clicker.accessibility.TaskAccessibilityService
import com.ksxkq.cmm_clicker.core.model.TaskBundle
import com.ksxkq.cmm_clicker.core.runtime.FlowRuntimeEngine
import com.ksxkq.cmm_clicker.core.runtime.RuntimeEngineOptions
import com.ksxkq.cmm_clicker.core.runtime.RuntimeRunReport
import com.ksxkq.cmm_clicker.core.runtime.SampleFlowBundleFactory
import com.ksxkq.cmm_clicker.feature.debug.RuntimeRunReportRepository
import com.ksxkq.cmm_clicker.feature.editor.TaskGraphEditorStore
import com.ksxkq.cmm_clicker.feature.task.LocalFileTaskRepository
import com.ksxkq.cmm_clicker.feature.task.TaskRecord
import com.ksxkq.cmm_clicker.ui.theme.ThemePreferenceStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val appContext = application.applicationContext
    private val themePreferenceStore = ThemePreferenceStore(appContext)
    private val taskRepository = LocalFileTaskRepository(appContext)
    private val runtimeRunReportRepository = RuntimeRunReportRepository(appContext)
    private val editorStore = TaskGraphEditorStore(
        initialBundle = SampleFlowBundleFactory.createSimpleDemoBundle(),
    )

    var uiState by mutableStateOf(MainUiState())
        private set

    init {
        viewModelScope.launch {
            uiState = uiState.copy(themeMode = themePreferenceStore.themeModeFlow.first())
        }
        viewModelScope.launch {
            loadTasksFromRepository()
        }
        refreshRuntimeRunReports()
        refreshPermissionStatus(attemptAutoEnable = true)
    }

    fun onResumeRefresh() {
        refreshPermissionStatus(attemptAutoEnable = true)
        refreshRuntimeRunReports()
    }

    fun toggleThemeMode() {
        val nextMode = uiState.themeMode.next()
        uiState = uiState.copy(themeMode = nextMode)
        viewModelScope.launch {
            themePreferenceStore.saveThemeMode(nextMode)
        }
    }

    fun openTaskOverlay(taskId: String) {
        if (taskId.isNotBlank()) {
            selectTask(taskId = taskId)
        }
        val service = TaskAccessibilityService.instance
        if (service == null || !TaskAccessibilityService.isConnected) {
            uiState = uiState.copy(taskOperationMessage = "辅助服务未连接，无法打开浮窗任务列表")
            return
        }
        val preferredTaskId = taskId.takeIf { it.isNotBlank() }
        val opened = service.showTaskListOverlay(preferredTaskId = preferredTaskId)
        uiState = uiState.copy(
            taskOperationMessage = if (opened) {
                "浮窗任务列表已打开，请在浮窗中编辑任务"
            } else {
                "打开浮窗任务列表失败"
            },
        )
    }

    fun showControlPanel() {
        val opened = TaskAccessibilityService.instance?.showTaskControlPanelOverlay() == true
        uiState = uiState.copy(
            taskOperationMessage = if (opened) {
                "操作面板已打开"
            } else {
                "辅助服务未连接，无法打开操作面板"
            },
        )
    }

    fun hideControlPanel() {
        TaskAccessibilityService.instance?.hideTaskControlPanelOverlay()
        uiState = uiState.copy(taskOperationMessage = "操作面板已关闭")
    }

    fun setDryRun(enabled: Boolean) {
        uiState = uiState.copy(dryRun = enabled)
    }

    fun setDoSwipeBranch(enabled: Boolean) {
        uiState = uiState.copy(doSwipeBranch = enabled)
    }

    fun runCurrentTask() {
        val taskId = uiState.selectedTaskId ?: return
        runTaskById(taskId)
    }

    fun runTask(taskId: String) {
        selectTask(taskId = taskId)
        runTaskById(taskId)
    }

    fun copyLatestRunReportToClipboard() {
        viewModelScope.launch {
            val latest = withContext(Dispatchers.IO) {
                runtimeRunReportRepository.latestJson()
            }
            if (latest.isNullOrBlank()) {
                uiState = uiState.copy(runtimeReportMessage = "暂无可复制的运行报告")
                return@launch
            }
            val clipboard = appContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("runtime_run_report", latest))
            uiState = uiState.copy(runtimeReportMessage = "已复制最近运行报告(JSON)")
        }
    }

    fun copyRunReportToClipboard(reportId: String) {
        viewModelScope.launch {
            val raw = withContext(Dispatchers.IO) {
                runtimeRunReportRepository.findJsonByReportId(reportId)
            }
            if (raw.isNullOrBlank()) {
                uiState = uiState.copy(runtimeReportMessage = "目标运行报告不存在或已过期")
                return@launch
            }
            val clipboard = appContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("runtime_run_report", raw))
            uiState = uiState.copy(runtimeReportMessage = "已复制运行报告(JSON): $reportId")
        }
    }

    fun refreshRuntimeRunReports() {
        viewModelScope.launch {
            val history = withContext(Dispatchers.IO) {
                runtimeRunReportRepository.listLatestSummaries(limit = 10)
            }
            uiState = uiState.copy(runtimeReportHistory = history)
        }
    }

    fun refreshPermissionStatus(attemptAutoEnable: Boolean = false) {
        val canWriteSecureSettings = AccessibilityPermissionChecker.hasWriteSecureSettingsPermission(appContext)
        var accessibilityEnabledInSettings = AccessibilityPermissionChecker.isServiceEnabled(
            context = appContext,
            serviceClass = TaskAccessibilityService::class.java,
        )
        var autoEnableMessage = uiState.autoEnableMessage
        if (attemptAutoEnable && canWriteSecureSettings && !accessibilityEnabledInSettings) {
            val result = AccessibilityPermissionChecker.tryEnableServiceBySecureSettings(
                context = appContext,
                serviceClass = TaskAccessibilityService::class.java,
            )
            autoEnableMessage = result.message
            accessibilityEnabledInSettings = AccessibilityPermissionChecker.isServiceEnabled(
                context = appContext,
                serviceClass = TaskAccessibilityService::class.java,
            )
        }
        uiState = uiState.copy(
            canWriteSecureSettings = canWriteSecureSettings,
            accessibilityEnabledInSettings = accessibilityEnabledInSettings,
            autoEnableMessage = autoEnableMessage,
            accessibilityServiceConnected = TaskAccessibilityService.isConnected,
            accessibilityEventCount = TaskAccessibilityService.eventCount(),
            gestureStats = TaskAccessibilityService.gestureStatsText(),
        )
    }

    fun autoEnableAccessibilityService() {
        val result = AccessibilityPermissionChecker.tryEnableServiceBySecureSettings(
            context = appContext,
            serviceClass = TaskAccessibilityService::class.java,
        )
        uiState = uiState.copy(autoEnableMessage = result.message)
        refreshPermissionStatus(attemptAutoEnable = false)
    }

    private suspend fun loadTasksFromRepository(preferredTaskId: String? = uiState.selectedTaskId) {
        val tasks = withContext(Dispatchers.IO) {
            taskRepository.listTasks()
        }
        if (tasks.isEmpty()) {
            uiState = uiState.copy(
                tasks = tasks,
                selectedTaskId = null,
                taskOperationMessage = "暂无任务",
            )
            return
        }
        val selected = tasks.firstOrNull { it.taskId == preferredTaskId } ?: tasks.first()
        editorStore.reset(selected.bundle)
        uiState = uiState.copy(
            tasks = tasks,
            selectedTaskId = selected.taskId,
        )
    }

    private fun selectTask(taskId: String) {
        if (uiState.selectedTaskId != null && uiState.selectedTaskId != taskId) {
            saveSelectedTaskNow(showMessage = false)
        }
        val selected = uiState.tasks.firstOrNull { it.taskId == taskId } ?: return
        editorStore.reset(selected.bundle)
        uiState = uiState.copy(selectedTaskId = selected.taskId)
    }

    private fun runTaskById(taskId: String) {
        val record = uiState.tasks.firstOrNull { it.taskId == taskId } ?: return
        val bundle = if (uiState.selectedTaskId == taskId) {
            editorStore.state().bundle.copy(
                bundleId = taskId,
                name = record.name,
            )
        } else {
            record.bundle
        }
        runTaskBundle(taskId = taskId, bundle = bundle)
    }

    private fun runTaskBundle(taskId: String, bundle: TaskBundle) {
        if (uiState.running) {
            return
        }
        val dryRun = uiState.dryRun
        val doSwipeBranch = uiState.doSwipeBranch
        uiState = uiState.copy(
            running = true,
            lastRunSummary = "运行中...",
        )
        viewModelScope.launch {
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
                uiState = uiState.copy(
                    lastRunSummary = summary,
                    lastRunTrace = tracePreview,
                )
                val report = RuntimeRunReport.fromExecution(
                    source = "main_console",
                    taskId = taskId,
                    taskName = uiState.tasks.firstOrNull { it.taskId == taskId }?.name ?: bundle.name,
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
                uiState = uiState.copy(
                    lastRunSummary = "运行异常: ${e.message ?: "unknown"}",
                    lastRunTrace = "",
                )
            } finally {
                uiState = uiState.copy(running = false)
                refreshPermissionStatus(attemptAutoEnable = false)
            }
        }
    }

    private fun saveSelectedTaskNow(showMessage: Boolean) {
        val taskId = uiState.selectedTaskId ?: return
        val current = editorStore.state().bundle
        val taskName = uiState.tasks.firstOrNull { it.taskId == taskId }?.name ?: current.name
        val bundleToSave = current.copy(
            bundleId = taskId,
            name = taskName,
        )
        viewModelScope.launch {
            val updated = withContext(Dispatchers.IO) {
                taskRepository.updateTaskBundle(taskId = taskId, bundle = bundleToSave)
            } ?: return@launch
            updateTaskRecordInState(updated)
            if (showMessage) {
                uiState = uiState.copy(taskOperationMessage = "已保存任务：${updated.name}")
            }
        }
    }

    private fun updateTaskRecordInState(record: TaskRecord) {
        val exists = uiState.tasks.any { it.taskId == record.taskId }
        val nextTasks = if (exists) {
            uiState.tasks.map { item ->
                if (item.taskId == record.taskId) record else item
            }
        } else {
            uiState.tasks + record
        }.sortedByDescending { it.updatedAtEpochMs }
        uiState = uiState.copy(tasks = nextTasks)
    }
}
