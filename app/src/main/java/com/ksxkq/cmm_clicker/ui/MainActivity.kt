package com.ksxkq.cmm_clicker.ui

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.lifecycleScope
import com.ksxkq.cmm_clicker.accessibility.AccessibilityPermissionChecker
import com.ksxkq.cmm_clicker.accessibility.TaskAccessibilityService
import com.ksxkq.cmm_clicker.core.model.ActionType
import com.ksxkq.cmm_clicker.core.model.EdgeConditionType
import com.ksxkq.cmm_clicker.core.model.GraphValidationIssue
import com.ksxkq.cmm_clicker.core.model.NodeKind
import com.ksxkq.cmm_clicker.core.model.TaskFlow
import com.ksxkq.cmm_clicker.core.runtime.FlowRuntimeEngine
import com.ksxkq.cmm_clicker.core.runtime.RuntimeEngineOptions
import com.ksxkq.cmm_clicker.core.runtime.SampleFlowBundleFactory
import com.ksxkq.cmm_clicker.feature.editor.EditorParamSchemaRegistry
import com.ksxkq.cmm_clicker.feature.editor.EditorActionTypeCatalog
import com.ksxkq.cmm_clicker.feature.editor.EditorParamValidator
import com.ksxkq.cmm_clicker.feature.editor.ParamFieldDefinition
import com.ksxkq.cmm_clicker.feature.editor.ParamFieldInputType
import com.ksxkq.cmm_clicker.feature.editor.TaskGraphEditorState
import com.ksxkq.cmm_clicker.feature.editor.TaskGraphEditorStore
import com.ksxkq.cmm_clicker.feature.task.LocalFileTaskRepository
import com.ksxkq.cmm_clicker.feature.task.TaskRecord
import com.ksxkq.cmm_clicker.ui.theme.AppThemeMode
import com.ksxkq.cmm_clicker.ui.theme.CmmClickerTheme
import com.ksxkq.cmm_clicker.ui.theme.ThemePreferenceStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

private enum class HomePageMode(val label: String) {
    TASKS("任务"),
    EDITOR("编辑器"),
    CONSOLE("控制台"),
}

private enum class TaskListSortMode(val label: String) {
    UPDATED_DESC("最近更新"),
    LAST_RUN_DESC("最近运行"),
    NAME_ASC("名称 A-Z"),
}

class MainActivity : ComponentActivity() {
    private val themePreferenceStore by lazy { ThemePreferenceStore(applicationContext) }
    private val taskRepository by lazy { LocalFileTaskRepository(applicationContext) }
    private val editorStore by lazy {
        TaskGraphEditorStore(
            initialBundle = SampleFlowBundleFactory.createSimpleDemoBundle(),
        )
    }

    private var themeMode by mutableStateOf(AppThemeMode.MONO_LIGHT)
    private var homePageMode by mutableStateOf(HomePageMode.TASKS)
    private var editorRevision by mutableIntStateOf(0)
    private var taskRecords by mutableStateOf<List<TaskRecord>>(emptyList())
    private var selectedTaskId by mutableStateOf<String?>(null)
    private var taskOperationMessage by mutableStateOf("")
    private var editingTaskOverlayId by mutableStateOf<String?>(null)
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
    private var pendingTaskSaveJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        lifecycleScope.launch {
            themeMode = themePreferenceStore.themeModeFlow.first()
        }
        lifecycleScope.launch {
            loadTasksFromRepository()
        }
        refreshPermissionStatus(attemptAutoEnable = true)
        setContent {
            val currentEditorRevision = editorRevision
            val editorState = editorStore.state()
            CmmClickerTheme(themeMode = themeMode) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    HomeScreen(
                        editorRevision = currentEditorRevision,
                        pageMode = homePageMode,
                        themeMode = themeMode,
                        editorState = editorState,
                        tasks = taskRecords,
                        selectedTaskId = selectedTaskId,
                        taskOperationMessage = taskOperationMessage,
                        editingTaskOverlayId = editingTaskOverlayId,
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
                        onPageModeChanged = { homePageMode = it },
                        onOpenAccessibilitySettings = { openAccessibilitySettings() },
                        onAutoEnableAccessibility = { autoEnableAccessibilityService() },
                        onRefreshStatus = { refreshPermissionStatus(attemptAutoEnable = true) },
                        onDryRunChanged = { dryRun = it },
                        onDoSwipeBranchChanged = { doSwipeBranch = it },
                        onRunSelectedTask = { runSelectedTask() },
                        onCreateTask = { name -> createTask(name) },
                        onSelectTask = { taskId, openEditor -> selectTask(taskId, openEditor) },
                        onOpenTaskOverlay = { taskId -> openTaskOverlay(taskId) },
                        onCloseTaskOverlay = { closeTaskOverlay() },
                        onRenameTask = { taskId, name -> renameTask(taskId, name) },
                        onDuplicateTask = { taskId -> duplicateTask(taskId) },
                        onDeleteTask = { taskId -> deleteTask(taskId) },
                        onRunTask = { taskId -> runTaskById(taskId) },
                        onEditorSelectFlow = { flowId ->
                            editorStore.selectFlow(flowId)
                            touchEditor()
                        },
                        onEditorSelectNode = { nodeId ->
                            editorStore.selectNode(nodeId)
                            touchEditor()
                        },
                        onEditorUndo = {
                            if (editorStore.undo()) {
                                touchEditor(modified = true)
                            }
                        },
                        onEditorRedo = {
                            if (editorStore.redo()) {
                                touchEditor(modified = true)
                            }
                        },
                        onEditorAddActionNode = {
                            editorStore.addActionNode()
                            touchEditor(modified = true)
                        },
                        onEditorRemoveSelectedNode = {
                            editorStore.removeSelectedNode()
                            touchEditor(modified = true)
                        },
                        onEditorMoveNodeUp = {
                            editorStore.moveSelectedNode(up = true)
                            touchEditor(modified = true)
                        },
                        onEditorMoveNodeDown = {
                            editorStore.moveSelectedNode(up = false)
                            touchEditor(modified = true)
                        },
                        onEditorUpdateNodeKind = { kind ->
                            editorStore.updateSelectedNodeKind(kind)
                            touchEditor(modified = true)
                        },
                        onEditorUpdateActionType = { actionType ->
                            editorStore.updateSelectedNodeActionType(actionType)
                            touchEditor(modified = true)
                        },
                        onEditorUpdatePluginId = { pluginId ->
                            editorStore.updateSelectedNodePluginId(pluginId)
                            touchEditor(modified = true)
                        },
                        onEditorUpdateEnabled = { enabled ->
                            editorStore.updateSelectedNodeEnabled(enabled)
                            touchEditor(modified = true)
                        },
                        onEditorUpdateActive = { active ->
                            editorStore.updateSelectedNodeActive(active)
                            touchEditor(modified = true)
                        },
                        onEditorUpdateParam = { key, value ->
                            editorStore.updateSelectedNodeParam(key, value)
                            touchEditor(modified = true)
                        },
                        onEditorFillDefaults = {
                            editorStore.fillDefaultsForSelectedNode()
                            touchEditor(modified = true)
                        },
                        onEditorUpdateBranchTarget = { condition, targetNodeId ->
                            editorStore.updateSelectedBranchTarget(condition, targetNodeId)
                            touchEditor(modified = true)
                        },
                        onEditorReset = {
                            editorStore.reset(SampleFlowBundleFactory.createSimpleDemoBundle())
                            touchEditor(modified = true)
                        },
                        onEditorSave = { saveSelectedTaskNow(showMessage = true) },
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

    private suspend fun loadTasksFromRepository(
        preferredTaskId: String? = selectedTaskId,
        openEditor: Boolean = false,
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
        touchEditor()
        if (openEditor) {
            homePageMode = HomePageMode.EDITOR
        }
    }

    private fun createTask(name: String) {
        lifecycleScope.launch {
            val created = withContext(Dispatchers.IO) {
                taskRepository.createTask(name = name, withTemplate = true)
            }
            taskRecords = (taskRecords + created).sortedByDescending { it.updatedAtEpochMs }
            selectTask(taskId = created.taskId, openEditor = false)
            taskOperationMessage = "已新建任务：${created.name}"
        }
    }

    private fun openTaskOverlay(taskId: String) {
        selectTask(taskId = taskId, openEditor = false)
        val service = TaskAccessibilityService.instance
        if (service == null || !TaskAccessibilityService.isConnected) {
            taskOperationMessage = "辅助服务未连接，无法打开全局浮窗"
            return
        }
        val opened = service.showTaskEditorOverlay(taskId)
        if (opened) {
            taskOperationMessage = "全局浮窗已打开，可切到其它 App 编辑"
        } else {
            taskOperationMessage = "打开全局浮窗失败"
        }
        editingTaskOverlayId = null
        homePageMode = HomePageMode.TASKS
    }

    private fun closeTaskOverlay() {
        TaskAccessibilityService.instance?.hideTaskEditorOverlay()
        editingTaskOverlayId = null
    }

    private fun selectTask(taskId: String, openEditor: Boolean) {
        if (selectedTaskId != null && selectedTaskId != taskId) {
            saveSelectedTaskNow(showMessage = false)
        }
        val selected = taskRecords.firstOrNull { it.taskId == taskId } ?: return
        selectedTaskId = selected.taskId
        editorStore.reset(selected.bundle)
        touchEditor()
        if (openEditor) {
            homePageMode = HomePageMode.EDITOR
        }
    }

    private fun renameTask(taskId: String, name: String) {
        lifecycleScope.launch {
            val updated = withContext(Dispatchers.IO) {
                taskRepository.renameTask(taskId = taskId, name = name)
            } ?: return@launch
            updateTaskRecordInState(updated)
            if (selectedTaskId == taskId) {
                editorStore.reset(updated.bundle)
                touchEditor()
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
                    selectTask(taskId = fallback.taskId, openEditor = false)
                } else {
                    selectedTaskId = null
                }
            }
            if (editingTaskOverlayId == taskId) {
                editingTaskOverlayId = null
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

    private fun scheduleAutoSaveForSelectedTask() {
        val taskId = selectedTaskId ?: return
        pendingTaskSaveJob?.cancel()
        pendingTaskSaveJob = lifecycleScope.launch {
            delay(350)
            if (selectedTaskId == taskId) {
                saveSelectedTaskNow(showMessage = false)
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

    private fun runSelectedTask() {
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
                val updated = withContext(Dispatchers.IO) {
                    taskRepository.updateTaskRunInfo(
                        taskId = taskId,
                        status = result.status.name,
                        summary = summary,
                    )
                }
                if (updated != null) {
                    updateTaskRecordInState(updated)
                }
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

    private fun touchEditor(modified: Boolean = false) {
        editorRevision++
        if (modified) {
            scheduleAutoSaveForSelectedTask()
        }
    }
}

@Composable
private fun HomeScreen(
    editorRevision: Int,
    pageMode: HomePageMode,
    themeMode: AppThemeMode,
    editorState: TaskGraphEditorState,
    tasks: List<TaskRecord>,
    selectedTaskId: String?,
    taskOperationMessage: String,
    editingTaskOverlayId: String?,
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
    onPageModeChanged: (HomePageMode) -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onAutoEnableAccessibility: () -> Unit,
    onRefreshStatus: () -> Unit,
    onDryRunChanged: (Boolean) -> Unit,
    onDoSwipeBranchChanged: (Boolean) -> Unit,
    onRunSelectedTask: () -> Unit,
    onCreateTask: (String) -> Unit,
    onSelectTask: (String, Boolean) -> Unit,
    onOpenTaskOverlay: (String) -> Unit,
    onCloseTaskOverlay: () -> Unit,
    onRenameTask: (String, String) -> Unit,
    onDuplicateTask: (String) -> Unit,
    onDeleteTask: (String) -> Unit,
    onRunTask: (String) -> Unit,
    onEditorSelectFlow: (String) -> Unit,
    onEditorSelectNode: (String) -> Unit,
    onEditorUndo: () -> Unit,
    onEditorRedo: () -> Unit,
    onEditorAddActionNode: () -> Unit,
    onEditorRemoveSelectedNode: () -> Unit,
    onEditorMoveNodeUp: () -> Unit,
    onEditorMoveNodeDown: () -> Unit,
    onEditorUpdateNodeKind: (NodeKind) -> Unit,
    onEditorUpdateActionType: (ActionType) -> Unit,
    onEditorUpdatePluginId: (String) -> Unit,
    onEditorUpdateEnabled: (Boolean) -> Unit,
    onEditorUpdateActive: (Boolean) -> Unit,
    onEditorUpdateParam: (String, String) -> Unit,
    onEditorFillDefaults: () -> Unit,
    onEditorUpdateBranchTarget: (EdgeConditionType, String) -> Unit,
    onEditorReset: () -> Unit,
    onEditorSave: () -> Unit,
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
                    text = "黑白极简任务自动化",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedButton(onClick = onThemeModeToggle) {
                Text(text = themeMode.displayName)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            HomePageMode.entries
                .filter { it != HomePageMode.EDITOR }
                .forEach { mode ->
                if (mode == pageMode) {
                    Button(onClick = { onPageModeChanged(mode) }) {
                        Text(mode.label)
                    }
                } else {
                    OutlinedButton(onClick = { onPageModeChanged(mode) }) {
                        Text(mode.label)
                    }
                }
            }
        }

        if (pageMode == HomePageMode.TASKS) {
            TaskListPanel(
                tasks = tasks,
                selectedTaskId = selectedTaskId,
                running = running,
                taskOperationMessage = taskOperationMessage,
                onCreateTask = onCreateTask,
                onSelectTask = onSelectTask,
                onOpenTaskOverlay = onOpenTaskOverlay,
                onRenameTask = onRenameTask,
                onDuplicateTask = onDuplicateTask,
                onDeleteTask = onDeleteTask,
                onRunTask = onRunTask,
            )
        } else if (pageMode == HomePageMode.CONSOLE) {
            ConsolePanel(
                serviceName = serviceName,
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
                onOpenAccessibilitySettings = onOpenAccessibilitySettings,
                onAutoEnableAccessibility = onAutoEnableAccessibility,
                onRefreshStatus = onRefreshStatus,
                onDryRunChanged = onDryRunChanged,
                onDoSwipeBranchChanged = onDoSwipeBranchChanged,
                onRunSelectedTask = onRunSelectedTask,
            )
        } else {
            EditorPanel(
                editorRevision = editorRevision,
                state = editorState,
                onSelectFlow = onEditorSelectFlow,
                onSelectNode = onEditorSelectNode,
                onUndo = onEditorUndo,
                onRedo = onEditorRedo,
                onAddActionNode = onEditorAddActionNode,
                onRemoveSelectedNode = onEditorRemoveSelectedNode,
                onMoveNodeUp = onEditorMoveNodeUp,
                onMoveNodeDown = onEditorMoveNodeDown,
                onUpdateNodeKind = onEditorUpdateNodeKind,
                onUpdateActionType = onEditorUpdateActionType,
                onUpdatePluginId = onEditorUpdatePluginId,
                onUpdateEnabled = onEditorUpdateEnabled,
                onUpdateActive = onEditorUpdateActive,
                onUpdateParam = onEditorUpdateParam,
                onFillDefaults = onEditorFillDefaults,
                onUpdateBranchTarget = onEditorUpdateBranchTarget,
                onReset = onEditorReset,
                onSave = onEditorSave,
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

@Composable
private fun TaskListPanel(
    tasks: List<TaskRecord>,
    selectedTaskId: String?,
    running: Boolean,
    taskOperationMessage: String,
    onCreateTask: (String) -> Unit,
    onSelectTask: (String, Boolean) -> Unit,
    onOpenTaskOverlay: (String) -> Unit,
    onRenameTask: (String, String) -> Unit,
    onDuplicateTask: (String) -> Unit,
    onDeleteTask: (String) -> Unit,
    onRunTask: (String) -> Unit,
) {
    val selectedTask = tasks.firstOrNull { it.taskId == selectedTaskId }
    var newTaskName by remember { mutableStateOf("") }
    var renameTaskName by remember { mutableStateOf("") }
    var searchKeyword by remember { mutableStateOf("") }
    var sortMode by remember { mutableStateOf(TaskListSortMode.UPDATED_DESC) }
    var pendingDeleteTaskId by remember { mutableStateOf<String?>(null) }
    val trimmedKeyword = searchKeyword.trim()
    val filteredTasks = remember(tasks, trimmedKeyword, sortMode) {
        val matched = tasks.filter { task ->
            trimmedKeyword.isBlank() ||
                task.name.contains(trimmedKeyword, ignoreCase = true) ||
                task.taskId.contains(trimmedKeyword, ignoreCase = true)
        }
        when (sortMode) {
            TaskListSortMode.UPDATED_DESC -> matched.sortedByDescending { it.updatedAtEpochMs }
            TaskListSortMode.LAST_RUN_DESC -> matched.sortedByDescending { it.lastRunAtEpochMs ?: -1L }
            TaskListSortMode.NAME_ASC -> matched.sortedBy { it.name.lowercase(Locale.ROOT) }
        }
    }
    val deleteCandidate = tasks.firstOrNull { it.taskId == pendingDeleteTaskId }
    LaunchedEffect(selectedTaskId, selectedTask?.name) {
        renameTaskName = selectedTask?.name.orEmpty()
    }

    SectionCard(title = "任务操作", subtitle = selectedTask?.name ?: "未选择任务") {
        OutlinedTextField(
            value = newTaskName,
            onValueChange = { newTaskName = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("新任务名称") },
            singleLine = true,
        )
        ActionButton(
            text = "新建任务（默认 start->click->end）",
            onClick = {
                onCreateTask(newTaskName)
                newTaskName = ""
            },
        )
        if (selectedTask != null) {
            OutlinedTextField(
                value = renameTaskName,
                onValueChange = { renameTaskName = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("重命名当前任务") },
                singleLine = true,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ActionButton(
                    text = "保存名称",
                    modifier = Modifier.weight(1f),
                    onClick = { onRenameTask(selectedTask.taskId, renameTaskName) },
                )
                ActionButton(
                    text = "复制任务",
                    modifier = Modifier.weight(1f),
                    onClick = { onDuplicateTask(selectedTask.taskId) },
                )
                ActionButton(
                    text = "删除任务",
                    modifier = Modifier.weight(1f),
                    onClick = { pendingDeleteTaskId = selectedTask.taskId },
                )
            }
        }
        if (taskOperationMessage.isNotBlank()) {
            Text(
                text = taskOperationMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    SectionCard(
        title = "任务列表",
        subtitle = if (trimmedKeyword.isBlank()) {
            "共 ${tasks.size} 个"
        } else {
            "匹配 ${filteredTasks.size} / ${tasks.size}"
        },
    ) {
        OutlinedTextField(
            value = searchKeyword,
            onValueChange = { searchKeyword = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("搜索任务（名称或 ID）") },
            singleLine = true,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TaskListSortMode.entries.forEach { mode ->
                if (mode == sortMode) {
                    Button(onClick = { sortMode = mode }) {
                        Text(mode.label)
                    }
                } else {
                    OutlinedButton(onClick = { sortMode = mode }) {
                        Text(mode.label)
                    }
                }
            }
        }

        if (filteredTasks.isEmpty()) {
            Text(
                text = if (tasks.isEmpty()) {
                    "暂无任务，请先新建"
                } else {
                    "没有匹配任务，请调整搜索条件"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            filteredTasks.forEach { task ->
                val isSelected = task.taskId == selectedTaskId
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = task.name,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = "更新时间: ${formatEpochMs(task.updatedAtEpochMs)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "最近运行: ${formatEpochMs(task.lastRunAtEpochMs)} | ${task.lastRunStatus ?: "-"} | ${task.lastRunSummary ?: "-"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (isSelected) {
                            Button(
                                modifier = Modifier.weight(1f),
                                onClick = { onSelectTask(task.taskId, false) },
                            ) {
                                Text("已选中")
                            }
                        } else {
                            OutlinedButton(
                                modifier = Modifier.weight(1f),
                                onClick = { onSelectTask(task.taskId, false) },
                            ) {
                                Text("选择")
                            }
                        }
                        ActionButton(
                            text = "编辑",
                            modifier = Modifier.weight(1f),
                            onClick = { onOpenTaskOverlay(task.taskId) },
                        )
                        ActionButton(
                            text = if (running) "运行中..." else "运行",
                            modifier = Modifier.weight(1f),
                            enabled = !running,
                            onClick = { onRunTask(task.taskId) },
                        )
                    }
                }
            }
        }
    }

    if (deleteCandidate != null) {
        DeleteTaskConfirmDialog(
            taskName = deleteCandidate.name,
            onCancel = { pendingDeleteTaskId = null },
            onConfirm = {
                onDeleteTask(deleteCandidate.taskId)
                pendingDeleteTaskId = null
            },
        )
    }
}

@Composable
private fun DeleteTaskConfirmDialog(
    taskName: String,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    Dialog(onDismissRequest = onCancel) {
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
                    text = "确认删除任务",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = "将删除任务：$taskName",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ActionButton(
                        text = "取消",
                        modifier = Modifier.weight(1f),
                        onClick = onCancel,
                    )
                    Button(
                        modifier = Modifier.weight(1f),
                        onClick = onConfirm,
                    ) {
                        Text("确认删除")
                    }
                }
            }
        }
    }
}

@Composable
private fun TaskActionOverlayDialog(
    state: TaskGraphEditorState,
    onDismiss: () -> Unit,
    onSelectNode: (String) -> Unit,
    onAddActionNode: () -> Unit,
    onRemoveSelectedNode: () -> Unit,
    onUpdateNodeKind: (NodeKind) -> Unit,
    onUpdateActionType: (ActionType) -> Unit,
    onUpdatePluginId: (String) -> Unit,
    onUpdateEnabled: (Boolean) -> Unit,
    onUpdateActive: (Boolean) -> Unit,
    onUpdateParam: (String, String) -> Unit,
    onFillDefaults: () -> Unit,
    onUpdateBranchTarget: (EdgeConditionType, String) -> Unit,
    onSave: () -> Unit,
) {
    val flow = state.selectedFlow
    var showPreview by remember { mutableStateOf(false) }
    var editingNodeId by remember { mutableStateOf<String?>(null) }
    val actionNodes = flow?.nodes
        ?.filter { it.kind != NodeKind.START && it.kind != NodeKind.END }
        ?: emptyList()

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.88f),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "动作详情浮窗",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    OutlinedButton(onClick = onDismiss) {
                        Text("关闭")
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ActionButton(
                        text = "添加动作",
                        modifier = Modifier.weight(1f),
                        onClick = onAddActionNode,
                    )
                    ActionButton(
                        text = if (showPreview) "隐藏预览" else "查看预览",
                        modifier = Modifier.weight(1f),
                        onClick = { showPreview = !showPreview },
                    )
                    ActionButton(
                        text = "保存",
                        modifier = Modifier.weight(1f),
                        onClick = onSave,
                    )
                }
                if (showPreview) {
                    SectionCard(title = "当前流程预览") {
                        if (flow == null) {
                            Text(
                                text = "未选择 flow",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            FlowGraphPreview(
                                flow = flow,
                                selectedNodeId = state.selectedNodeId,
                                onSelectNode = onSelectNode,
                            )
                        }
                    }
                }
                SectionCard(
                    title = "动作列表",
                    subtitle = "仅展示动作摘要，点击编辑进入详情界面",
                ) {
                    if (actionNodes.isEmpty()) {
                        Text(
                            text = "当前没有动作节点",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        actionNodes.forEachIndexed { index, node ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "${index + 1}. ${nodeSummaryText(node)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.weight(1f),
                                )
                                OutlinedButton(
                                    onClick = {
                                        onSelectNode(node.nodeId)
                                        editingNodeId = node.nodeId
                                    },
                                ) {
                                    Text("编辑")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (editingNodeId != null && state.selectedNodeId == editingNodeId && state.selectedNode != null) {
        NodeEditDialog(
            state = state,
            onDismiss = { editingNodeId = null },
            onRemoveCurrentNode = {
                onRemoveSelectedNode()
                editingNodeId = null
            },
            onUpdateNodeKind = onUpdateNodeKind,
            onUpdateActionType = onUpdateActionType,
            onUpdatePluginId = onUpdatePluginId,
            onUpdateEnabled = onUpdateEnabled,
            onUpdateActive = onUpdateActive,
            onUpdateParam = onUpdateParam,
            onFillDefaults = onFillDefaults,
            onUpdateBranchTarget = onUpdateBranchTarget,
            onSave = onSave,
        )
    }
}

@Composable
private fun NodeEditDialog(
    state: TaskGraphEditorState,
    onDismiss: () -> Unit,
    onRemoveCurrentNode: () -> Unit,
    onUpdateNodeKind: (NodeKind) -> Unit,
    onUpdateActionType: (ActionType) -> Unit,
    onUpdatePluginId: (String) -> Unit,
    onUpdateEnabled: (Boolean) -> Unit,
    onUpdateActive: (Boolean) -> Unit,
    onUpdateParam: (String, String) -> Unit,
    onFillDefaults: () -> Unit,
    onUpdateBranchTarget: (EdgeConditionType, String) -> Unit,
    onSave: () -> Unit,
) {
    val flow = state.selectedFlow
    val node = state.selectedNode ?: return
    val schemaFields = EditorParamSchemaRegistry.fieldsFor(node)
    val schemaByKey = schemaFields.associateBy { it.key }
    val hiddenParamKeys = buildSet {
        if (node.kind == NodeKind.JUMP || node.kind == NodeKind.FOLDER_REF || node.kind == NodeKind.SUB_TASK_REF) {
            add("targetFlowId")
            add("targetNodeId")
        }
    }
    val paramKeys = (schemaFields.map { it.key } + node.params.keys)
        .distinct()
        .filterNot { it in hiddenParamKeys }
    val isJumpLike = node.kind == NodeKind.JUMP ||
        node.kind == NodeKind.FOLDER_REF ||
        node.kind == NodeKind.SUB_TASK_REF
    val targetFlowId = node.params["targetFlowId"]?.toString()
        ?.takeIf { it.isNotBlank() }
        ?: state.selectedFlowId
    val targetFlow = state.bundle.findFlow(targetFlowId)
    val targetNodeId = node.params["targetNodeId"]?.toString().orEmpty()
    val trueTargetNodeId = flow?.edges
        ?.firstOrNull {
            it.fromNodeId == node.nodeId && it.conditionType == EdgeConditionType.TRUE
        }
        ?.toNodeId
        .orEmpty()
    val falseTargetNodeId = flow?.edges
        ?.firstOrNull {
            it.fromNodeId == node.nodeId && it.conditionType == EdgeConditionType.FALSE
        }
        ?.toNodeId
        .orEmpty()

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "编辑动作: ${node.nodeId}",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    OutlinedButton(onClick = onDismiss) {
                        Text("完成")
                    }
                }
                Text(
                    text = "Kind",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    NodeKind.entries.forEach { kind ->
                        if (kind == node.kind) {
                            Button(onClick = { onUpdateNodeKind(kind) }) {
                                Text(kind.name)
                            }
                        } else {
                            OutlinedButton(onClick = { onUpdateNodeKind(kind) }) {
                                Text(kind.name)
                            }
                        }
                    }
                }
                SwitchRow(
                    title = "enabled",
                    checked = node.flags.enabled,
                    onCheckedChange = onUpdateEnabled,
                )
                SwitchRow(
                    title = "active",
                    checked = node.flags.active,
                    onCheckedChange = onUpdateActive,
                )

                if (node.kind == NodeKind.ACTION) {
                    OutlinedTextField(
                        value = node.pluginId.orEmpty(),
                        onValueChange = onUpdatePluginId,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("pluginId") },
                        singleLine = true,
                    )
                    Text(
                        text = "ActionType",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        EditorActionTypeCatalog.availableActionTypes.forEach { actionType ->
                            if (actionType == node.actionType) {
                                Button(onClick = { onUpdateActionType(actionType) }) {
                                    Text(actionType.raw)
                                }
                            } else {
                                OutlinedButton(onClick = { onUpdateActionType(actionType) }) {
                                    Text(actionType.raw)
                                }
                            }
                        }
                    }
                }

                if (isJumpLike) {
                    Text(
                        text = "Jump Target Flow",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        state.bundle.flows.forEach { item ->
                            if (item.flowId == targetFlowId) {
                                Button(onClick = { onUpdateParam("targetFlowId", item.flowId) }) {
                                    Text(item.flowId)
                                }
                            } else {
                                OutlinedButton(onClick = { onUpdateParam("targetFlowId", item.flowId) }) {
                                    Text(item.flowId)
                                }
                            }
                        }
                    }
                    Text(
                        text = "Jump Target Node",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        targetFlow?.nodes?.forEach { item ->
                            if (item.nodeId == targetNodeId) {
                                Button(onClick = { onUpdateParam("targetNodeId", item.nodeId) }) {
                                    Text(item.nodeId)
                                }
                            } else {
                                OutlinedButton(onClick = { onUpdateParam("targetNodeId", item.nodeId) }) {
                                    Text(item.nodeId)
                                }
                            }
                        }
                    }
                }

                if (node.kind == NodeKind.BRANCH) {
                    Text(
                        text = "Branch TRUE Target",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        flow?.nodes?.forEach { item ->
                            if (item.nodeId == trueTargetNodeId) {
                                Button(onClick = { onUpdateBranchTarget(EdgeConditionType.TRUE, item.nodeId) }) {
                                    Text(item.nodeId)
                                }
                            } else {
                                OutlinedButton(onClick = { onUpdateBranchTarget(EdgeConditionType.TRUE, item.nodeId) }) {
                                    Text(item.nodeId)
                                }
                            }
                        }
                    }
                    Text(
                        text = "Branch FALSE Target",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        flow?.nodes?.forEach { item ->
                            if (item.nodeId == falseTargetNodeId) {
                                Button(onClick = { onUpdateBranchTarget(EdgeConditionType.FALSE, item.nodeId) }) {
                                    Text(item.nodeId)
                                }
                            } else {
                                OutlinedButton(onClick = { onUpdateBranchTarget(EdgeConditionType.FALSE, item.nodeId) }) {
                                    Text(item.nodeId)
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Params",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                    )
                    OutlinedButton(onClick = onFillDefaults) {
                        Text("填充默认值")
                    }
                }
                if (paramKeys.isEmpty()) {
                    Text(
                        text = "当前节点没有可编辑参数",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    paramKeys.forEach { key ->
                        ParamFieldEditor(
                            key = key,
                            value = node.params[key]?.toString().orEmpty(),
                            definition = schemaByKey[key],
                            onValueChange = { onUpdateParam(key, it) },
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ActionButton(
                        text = "删除当前动作",
                        modifier = Modifier.weight(1f),
                        onClick = onRemoveCurrentNode,
                    )
                    ActionButton(
                        text = "保存",
                        modifier = Modifier.weight(1f),
                        onClick = onSave,
                    )
                }
            }
        }
    }
}

private fun nodeSummaryText(node: com.ksxkq.cmm_clicker.core.model.FlowNode): String {
    return when (node.kind) {
        NodeKind.ACTION -> "${node.actionType?.raw ?: "action"} (${node.nodeId})"
        NodeKind.BRANCH -> "branch (${node.nodeId})"
        NodeKind.JUMP -> {
            val tf = node.params["targetFlowId"]?.toString().orEmpty()
            val tn = node.params["targetNodeId"]?.toString().orEmpty()
            "jump -> $tf/$tn"
        }
        NodeKind.FOLDER_REF -> "folderRef (${node.nodeId})"
        NodeKind.SUB_TASK_REF -> "subTaskRef (${node.nodeId})"
        NodeKind.START -> "start (${node.nodeId})"
        NodeKind.END -> "end (${node.nodeId})"
    }
}

@Composable
private fun ConsolePanel(
    serviceName: String,
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
    onRunSelectedTask: () -> Unit,
) {
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
            onClick = onRunSelectedTask,
            enabled = !running,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text = if (running) "运行中..." else "运行当前选中任务")
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

@Composable
private fun EditorPanel(
    editorRevision: Int,
    state: TaskGraphEditorState,
    onSelectFlow: (String) -> Unit,
    onSelectNode: (String) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onAddActionNode: () -> Unit,
    onRemoveSelectedNode: () -> Unit,
    onMoveNodeUp: () -> Unit,
    onMoveNodeDown: () -> Unit,
    onUpdateNodeKind: (NodeKind) -> Unit,
    onUpdateActionType: (ActionType) -> Unit,
    onUpdatePluginId: (String) -> Unit,
    onUpdateEnabled: (Boolean) -> Unit,
    onUpdateActive: (Boolean) -> Unit,
    onUpdateParam: (String, String) -> Unit,
    onFillDefaults: () -> Unit,
    onUpdateBranchTarget: (EdgeConditionType, String) -> Unit,
    onReset: () -> Unit,
    onSave: () -> Unit,
) {
    val flow = state.selectedFlow
    val node = state.selectedNode
    val schemaFields = node?.let { EditorParamSchemaRegistry.fieldsFor(it) } ?: emptyList()
    val hiddenParamKeys = buildSet {
        if (node?.kind == NodeKind.JUMP || node?.kind == NodeKind.FOLDER_REF || node?.kind == NodeKind.SUB_TASK_REF) {
            add("targetFlowId")
            add("targetNodeId")
        }
    }
    val schemaByKey = schemaFields.associateBy { it.key }
    val paramKeys = (schemaFields.map { it.key } + (node?.params?.keys ?: emptySet()))
        .distinct()
        .filterNot { it in hiddenParamKeys }

    SectionCard(title = "编辑器状态", subtitle = "rev=$editorRevision") {
        Text(
            text = "Bundle: ${state.bundle.name}  |  Flow: ${flow?.flowId ?: "-"}  |  Node: ${node?.nodeId ?: "-"}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ActionButton(text = "Undo", modifier = Modifier.weight(1f), enabled = state.canUndo, onClick = onUndo)
            ActionButton(text = "Redo", modifier = Modifier.weight(1f), enabled = state.canRedo, onClick = onRedo)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ActionButton(text = "添加动作节点", modifier = Modifier.weight(1f), onClick = onAddActionNode)
            ActionButton(text = "删除当前节点", modifier = Modifier.weight(1f), enabled = node != null, onClick = onRemoveSelectedNode)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ActionButton(text = "上移", modifier = Modifier.weight(1f), enabled = node != null, onClick = onMoveNodeUp)
            ActionButton(text = "下移", modifier = Modifier.weight(1f), enabled = node != null, onClick = onMoveNodeDown)
            ActionButton(text = "重置示例图", modifier = Modifier.weight(1f), onClick = onReset)
        }
        ActionButton(
            text = "保存任务",
            onClick = onSave,
        )
    }

    SectionCard(title = "Flow 列表") {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            state.bundle.flows.forEach { item ->
                val selected = item.flowId == state.selectedFlowId
                if (selected) {
                    Button(onClick = { onSelectFlow(item.flowId) }) {
                        Text(item.flowId)
                    }
                } else {
                    OutlinedButton(onClick = { onSelectFlow(item.flowId) }) {
                        Text(item.flowId)
                    }
                }
            }
        }
    }

    val nodeListPanel: @Composable () -> Unit = {
        SectionCard(
            title = "节点列表",
            subtitle = flow?.name ?: "未选择 flow",
        ) {
            if (flow == null) {
                Text(
                    text = "当前没有可编辑 flow",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                flow.nodes.forEach { item ->
                    val jumpHint = if (
                        item.kind == NodeKind.JUMP ||
                        item.kind == NodeKind.FOLDER_REF ||
                        item.kind == NodeKind.SUB_TASK_REF
                    ) {
                        val tf = item.params["targetFlowId"]?.toString().orEmpty()
                        val tn = item.params["targetNodeId"]?.toString().orEmpty()
                        if (tf.isNotBlank() || tn.isNotBlank()) " -> $tf/$tn" else ""
                    } else {
                        ""
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "${item.nodeId}  [${item.kind}]  ${item.actionType?.raw ?: ""}$jumpHint".trim(),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        if (item.nodeId == state.selectedNodeId) {
                            Button(onClick = { onSelectNode(item.nodeId) }) {
                                Text("已选中")
                            }
                        } else {
                            OutlinedButton(onClick = { onSelectNode(item.nodeId) }) {
                                Text("选择")
                            }
                        }
                    }
                }
            }
        }
    }
    val graphPanel: @Composable () -> Unit = {
        SectionCard(title = "流程图预览") {
            if (flow == null) {
                Text(
                    text = "未选择 flow",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                FlowGraphPreview(
                    flow = flow,
                    selectedNodeId = state.selectedNodeId,
                    onSelectNode = onSelectNode,
                )
            }
        }
        SectionCard(title = "连线预览") {
            if (flow == null) {
                Text(
                    text = "未选择 flow",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                flow.edges.forEach { edge ->
                    Text(
                        text = "${edge.fromNodeId} --${edge.conditionType.name}--> ${edge.toNodeId}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                flow.nodes
                    .filter {
                        it.kind == NodeKind.JUMP ||
                            it.kind == NodeKind.FOLDER_REF ||
                            it.kind == NodeKind.SUB_TASK_REF
                    }
                    .forEach { jumpNode ->
                        val tf = jumpNode.params["targetFlowId"]?.toString().orEmpty()
                        val tn = jumpNode.params["targetNodeId"]?.toString().orEmpty()
                        Text(
                            text = "${jumpNode.nodeId} --${jumpNode.kind.name}--> $tf/$tn",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
            }
        }
    }
    BoxWithConstraints(
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (maxWidth >= 900.dp) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    nodeListPanel()
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    graphPanel()
                }
            }
        } else {
            nodeListPanel()
            graphPanel()
        }
    }

    SectionCard(
        title = "节点属性",
        subtitle = node?.nodeId ?: "未选择节点",
    ) {
        if (node == null) {
            Text(
                text = "请先在节点列表选择一个节点",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            val isJumpLike = node.kind == NodeKind.JUMP ||
                node.kind == NodeKind.FOLDER_REF ||
                node.kind == NodeKind.SUB_TASK_REF
            val targetFlowId = node.params["targetFlowId"]?.toString()
                ?.takeIf { it.isNotBlank() }
                ?: state.selectedFlowId
            val targetFlow = state.bundle.findFlow(targetFlowId)
            val targetNodeId = node.params["targetNodeId"]?.toString().orEmpty()
            val trueTargetNodeId = flow?.edges
                ?.firstOrNull {
                    it.fromNodeId == node.nodeId && it.conditionType == EdgeConditionType.TRUE
                }
                ?.toNodeId
                .orEmpty()
            val falseTargetNodeId = flow?.edges
                ?.firstOrNull {
                    it.fromNodeId == node.nodeId && it.conditionType == EdgeConditionType.FALSE
                }
                ?.toNodeId
                .orEmpty()

            Text(
                text = "Kind",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                NodeKind.entries.forEach { kind ->
                    if (kind == node.kind) {
                        Button(onClick = { onUpdateNodeKind(kind) }) {
                            Text(kind.name)
                        }
                    } else {
                        OutlinedButton(onClick = { onUpdateNodeKind(kind) }) {
                            Text(kind.name)
                        }
                    }
                }
            }

            if (isJumpLike) {
                Text(
                    text = "Jump Target Flow",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    state.bundle.flows.forEach { item ->
                        if (item.flowId == targetFlowId) {
                            Button(onClick = { onUpdateParam("targetFlowId", item.flowId) }) {
                                Text(item.flowId)
                            }
                        } else {
                            OutlinedButton(onClick = { onUpdateParam("targetFlowId", item.flowId) }) {
                                Text(item.flowId)
                            }
                        }
                    }
                }
                Text(
                    text = "Jump Target Node",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    targetFlow?.nodes?.forEach { item ->
                        if (item.nodeId == targetNodeId) {
                            Button(onClick = { onUpdateParam("targetNodeId", item.nodeId) }) {
                                Text(item.nodeId)
                            }
                        } else {
                            OutlinedButton(onClick = { onUpdateParam("targetNodeId", item.nodeId) }) {
                                Text(item.nodeId)
                            }
                        }
                    }
                }
            }

            if (node.kind == NodeKind.BRANCH) {
                Text(
                    text = "Branch TRUE Target",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    flow?.nodes?.forEach { item ->
                        if (item.nodeId == trueTargetNodeId) {
                            Button(onClick = { onUpdateBranchTarget(EdgeConditionType.TRUE, item.nodeId) }) {
                                Text(item.nodeId)
                            }
                        } else {
                            OutlinedButton(onClick = { onUpdateBranchTarget(EdgeConditionType.TRUE, item.nodeId) }) {
                                Text(item.nodeId)
                            }
                        }
                    }
                }
                Text(
                    text = "Branch FALSE Target",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    flow?.nodes?.forEach { item ->
                        if (item.nodeId == falseTargetNodeId) {
                            Button(onClick = { onUpdateBranchTarget(EdgeConditionType.FALSE, item.nodeId) }) {
                                Text(item.nodeId)
                            }
                        } else {
                            OutlinedButton(onClick = { onUpdateBranchTarget(EdgeConditionType.FALSE, item.nodeId) }) {
                                Text(item.nodeId)
                            }
                        }
                    }
                }
            }

            SwitchRow(
                title = "enabled",
                checked = node.flags.enabled,
                onCheckedChange = onUpdateEnabled,
            )
            SwitchRow(
                title = "active",
                checked = node.flags.active,
                onCheckedChange = onUpdateActive,
            )

            if (node.kind == NodeKind.ACTION) {
                OutlinedTextField(
                    value = node.pluginId.orEmpty(),
                    onValueChange = onUpdatePluginId,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("pluginId") },
                    singleLine = true,
                )
                Text(
                    text = "ActionType",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    EditorActionTypeCatalog.availableActionTypes.forEach { actionType ->
                        if (actionType == node.actionType) {
                            Button(onClick = { onUpdateActionType(actionType) }) {
                                Text(actionType.raw)
                            }
                        } else {
                            OutlinedButton(onClick = { onUpdateActionType(actionType) }) {
                                Text(actionType.raw)
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Params",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                )
                OutlinedButton(onClick = onFillDefaults) {
                    Text("填充默认值")
                }
            }
            if (paramKeys.isEmpty()) {
                Text(
                    text = "当前节点没有默认参数，可以通过切换 kind/actionType 自动出现参数。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                paramKeys.forEach { key ->
                    ParamFieldEditor(
                        key = key,
                        value = node.params[key]?.toString().orEmpty(),
                        definition = schemaByKey[key],
                        onValueChange = { onUpdateParam(key, it) },
                    )
                }
            }
        }
    }

    SectionCard(title = "图校验") {
        ValidationIssueList(issues = state.validationIssues)
    }

}

@Composable
private fun ValidationIssueList(
    issues: List<GraphValidationIssue>,
) {
    if (issues.isEmpty()) {
        Text(
            text = "无校验问题",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    Text(
        text = "issues=${issues.size}",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    issues.take(20).forEach { issue ->
        Text(
            text = "[${issue.severity}] ${issue.code} ${issue.flowId ?: ""}/${issue.nodeId ?: ""} ${issue.message}".trim(),
            style = MaterialTheme.typography.bodySmall,
        )
    }
    if (issues.size > 20) {
        Text(
            text = "仅显示前20条",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private data class FlowGraphPoint(
    val xRatio: Float,
    val yRatio: Float,
)

@Composable
private fun FlowGraphPreview(
    flow: TaskFlow,
    selectedNodeId: String?,
    onSelectNode: (String) -> Unit,
) {
    val nodePoints = calculateGraphPoints(flow)
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val outlineColor = MaterialTheme.colorScheme.outline
    val onSurfaceVariantColor = MaterialTheme.colorScheme.onSurfaceVariant
    val surfaceColor = MaterialTheme.colorScheme.surface
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(280.dp)
            .pointerInput(flow, selectedNodeId) {
                detectTapGestures { tapOffset ->
                    val closestNodeId = findClosestNodeId(
                        points = nodePoints,
                        tapOffset = tapOffset,
                        width = size.width.toFloat(),
                        height = size.height.toFloat(),
                    )
                    if (closestNodeId != null) {
                        onSelectNode(closestNodeId)
                    }
                }
            },
    ) {
        val contentWidth = size.width
        val contentHeight = size.height
        val toOffset: (FlowGraphPoint) -> Offset = { point ->
            Offset(
                x = point.xRatio * contentWidth,
                y = point.yRatio * contentHeight,
            )
        }

        flow.edges.forEach { edge ->
            val from = nodePoints[edge.fromNodeId] ?: return@forEach
            val to = nodePoints[edge.toNodeId] ?: return@forEach
            val fromOffset = toOffset(from)
            val toOffsetValue = toOffset(to)
            val isHighlighted = selectedNodeId != null &&
                (edge.fromNodeId == selectedNodeId || edge.toNodeId == selectedNodeId)
            val edgeColor = when (edge.conditionType) {
                EdgeConditionType.TRUE -> primaryColor
                EdgeConditionType.FALSE -> onSurfaceVariantColor
                EdgeConditionType.MATCH_KEY -> secondaryColor
                EdgeConditionType.ALWAYS -> outlineColor
            }
            drawLine(
                color = edgeColor.copy(alpha = if (selectedNodeId == null || isHighlighted) 1f else 0.28f),
                start = fromOffset,
                end = toOffsetValue,
                strokeWidth = if (isHighlighted) 4f else 2.5f,
                cap = StrokeCap.Round,
            )
        }

        val dashEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f)
        flow.nodes
            .filter {
                it.kind == NodeKind.JUMP ||
                    it.kind == NodeKind.FOLDER_REF ||
                    it.kind == NodeKind.SUB_TASK_REF
            }
            .forEach { node ->
                val fromPoint = nodePoints[node.nodeId] ?: return@forEach
                val targetFlowId = node.params["targetFlowId"]?.toString()?.takeIf { it.isNotBlank() } ?: flow.flowId
                val targetNodeId = node.params["targetNodeId"]?.toString()?.takeIf { it.isNotBlank() } ?: return@forEach
                if (targetFlowId != flow.flowId) {
                    return@forEach
                }
                val toPoint = nodePoints[targetNodeId] ?: return@forEach
                val isHighlighted = selectedNodeId != null &&
                    (selectedNodeId == node.nodeId || selectedNodeId == targetNodeId)
                drawLine(
                    color = primaryColor.copy(alpha = if (selectedNodeId == null || isHighlighted) 0.95f else 0.32f),
                    start = toOffset(fromPoint),
                    end = toOffset(toPoint),
                    strokeWidth = if (isHighlighted) 3.8f else 2f,
                    cap = StrokeCap.Round,
                    pathEffect = dashEffect,
                )
            }

        flow.nodes.forEach { node ->
            val point = nodePoints[node.nodeId] ?: return@forEach
            val center = toOffset(point)
            val isSelected = node.nodeId == selectedNodeId
            val fillColor = if (isSelected) {
                primaryColor
            } else {
                surfaceColor
            }
            val strokeColor = if (isSelected) {
                primaryColor
            } else {
                outlineColor
            }
            drawCircle(
                color = fillColor,
                radius = if (isSelected) 13f else 11f,
                center = center,
            )
            drawCircle(
                color = strokeColor,
                radius = if (isSelected) 13f else 11f,
                center = center,
                style = Stroke(width = if (isSelected) 2.8f else 2f),
            )
        }
    }
}

private fun calculateGraphPoints(flow: TaskFlow): Map<String, FlowGraphPoint> {
    if (flow.nodes.isEmpty()) {
        return emptyMap()
    }
    val trueTargets = flow.edges
        .filter { it.conditionType == EdgeConditionType.TRUE }
        .map { it.toNodeId }
        .toSet()
    val falseTargets = flow.edges
        .filter { it.conditionType == EdgeConditionType.FALSE }
        .map { it.toNodeId }
        .toSet()

    val denominator = max(flow.nodes.size - 1, 1)
    val result = linkedMapOf<String, FlowGraphPoint>()
    flow.nodes.forEachIndexed { index, node ->
        val baseX = when {
            node.nodeId in trueTargets && node.nodeId !in falseTargets -> 0.28f
            node.nodeId in falseTargets && node.nodeId !in trueTargets -> 0.72f
            else -> 0.5f
        }
        val y = 0.1f + (0.8f * (index.toFloat() / denominator.toFloat()))
        result[node.nodeId] = FlowGraphPoint(
            xRatio = min(max(baseX, 0.12f), 0.88f),
            yRatio = min(max(y, 0.08f), 0.92f),
        )
    }
    return result
}

private fun findClosestNodeId(
    points: Map<String, FlowGraphPoint>,
    tapOffset: Offset,
    width: Float,
    height: Float,
): String? {
    if (points.isEmpty()) {
        return null
    }
    var closestNodeId: String? = null
    var closestDistance = Float.MAX_VALUE
    points.forEach { (nodeId, point) ->
        val centerX = point.xRatio * width
        val centerY = point.yRatio * height
        val distance = hypot(
            tapOffset.x - centerX,
            tapOffset.y - centerY,
        )
        if (distance < closestDistance) {
            closestDistance = distance
            closestNodeId = nodeId
        }
    }
    return if (closestDistance <= 48f) closestNodeId else null
}

private fun formatEpochMs(value: Long?): String {
    if (value == null || value <= 0L) {
        return "-"
    }
    return runCatching {
        SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault()).format(Date(value))
    }.getOrDefault("-")
}

@Composable
private fun ParamFieldEditor(
    key: String,
    value: String,
    definition: ParamFieldDefinition?,
    onValueChange: (String) -> Unit,
) {
    val label = definition?.label ?: key
    val options = definition?.options.orEmpty()
    val helperText = definition?.helperText
    val errorText = EditorParamValidator.validate(definition, value)
    if (options.isNotEmpty()) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            options.forEach { option ->
                if (option == value) {
                    Button(onClick = { onValueChange(option) }) {
                        Text(option)
                    }
                } else {
                    OutlinedButton(onClick = { onValueChange(option) }) {
                        Text(option)
                    }
                }
            }
        }
        if (!errorText.isNullOrBlank()) {
            Text(
                text = errorText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        } else if (!helperText.isNullOrBlank()) {
            Text(
                text = helperText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    val keyboardType = if (definition?.inputType == ParamFieldInputType.NUMBER) {
        KeyboardType.Number
    } else {
        KeyboardType.Text
    }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        placeholder = {
            definition?.defaultValue?.let { default ->
                Text("默认: $default")
            }
        },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        isError = !errorText.isNullOrBlank(),
        supportingText = {
            when {
                !errorText.isNullOrBlank() -> Text(errorText)
                !helperText.isNullOrBlank() -> Text(helperText)
            }
        },
        singleLine = true,
    )
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
    modifier: Modifier = Modifier.fillMaxWidth(),
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
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
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
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
