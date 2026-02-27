package com.ksxkq.cmm_clicker.accessibility

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import androidx.compose.animation.AnimatedVisibility as AnimatedVisibilityBox
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FiberManualRecord
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.AndroidUiDispatcher
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import androidx.core.view.ViewCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.ksxkq.cmm_clicker.core.model.ActionType
import com.ksxkq.cmm_clicker.core.model.BundleSchema
import com.ksxkq.cmm_clicker.core.model.EdgeConditionType
import com.ksxkq.cmm_clicker.core.model.FlowEdge
import com.ksxkq.cmm_clicker.core.model.FlowNode
import com.ksxkq.cmm_clicker.core.model.NodeKind
import com.ksxkq.cmm_clicker.core.model.TaskBundle
import com.ksxkq.cmm_clicker.core.runtime.FlowRuntimeEngine
import com.ksxkq.cmm_clicker.core.runtime.RuntimeEngineOptions
import com.ksxkq.cmm_clicker.feature.editor.TaskGraphEditorStore
import com.ksxkq.cmm_clicker.feature.task.LocalFileTaskRepository
import com.ksxkq.cmm_clicker.feature.task.TaskRecord
import com.ksxkq.cmm_clicker.ui.CircleActionIconButton
import com.ksxkq.cmm_clicker.ui.TaskLibraryPanel
import com.ksxkq.cmm_clicker.ui.theme.AppThemeMode
import com.ksxkq.cmm_clicker.ui.theme.CmmClickerTheme
import com.ksxkq.cmm_clicker.ui.theme.ThemePreferenceStore
import java.util.UUID
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.foundation.interaction.MutableInteractionSource

class TaskControlPanelGlobalOverlay(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    companion object {
        private const val TAG = "TaskControlOverlay"
        private const val PREF_NAME = "task_control_overlay"
        private const val KEY_SELECTED_TASK_ID = "selected_task_id"
        private const val KEY_LAST_STARTED_TASK_ID = "last_started_task_id"
    }

    private sealed interface RecordedGesture {
        data class Click(val xRatio: Double, val yRatio: Double, val durationMs: Long) : RecordedGesture

        data class Path(val points: List<Pair<Double, Double>>, val durationMs: Long) : RecordedGesture
    }

    private sealed interface SettingsRoute {
        data object TaskList : SettingsRoute

        data object ActionList : SettingsRoute

        data class NodeEditor(
            val nodeId: String,
        ) : SettingsRoute
    }

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val taskRepository = LocalFileTaskRepository(context)
    private val themePreferenceStore = ThemePreferenceStore(context)
    private val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    private var overlayView: ComposeView? = null
    private var composeOwner: ControlOverlayComposeOwner? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var overlayRecomposer: Recomposer? = null
    private var overlayRecomposerJob: Job? = null
    private var settingsOverlayView: ComposeView? = null
    private var settingsComposeOwner: ControlOverlayComposeOwner? = null
    private var settingsRecomposer: Recomposer? = null
    private var settingsRecomposerJob: Job? = null
    private var themeSyncJob: Job? = null

    private var captureView: View? = null
    private var captureStartX = 0f
    private var captureStartY = 0f
    private var captureStartTime = 0L
    private val capturePath = mutableListOf<Pair<Float, Float>>()

    private var themeMode by mutableStateOf(AppThemeMode.MONO_LIGHT)
    private var tasks by mutableStateOf<List<TaskRecord>>(emptyList())
    private var overlayVisible by mutableStateOf(false)
    private var settingsVisible by mutableStateOf(false)
    private var settingsSheetVisible by mutableStateOf(false)
    private var settingsDismissAnimating by mutableStateOf(false)
    private var settingsRoute by mutableStateOf<SettingsRoute>(SettingsRoute.TaskList)
    private var settingsTask by mutableStateOf<TaskRecord?>(null)
    private var settingsEditorStore by mutableStateOf<TaskGraphEditorStore?>(null)
    private var running by mutableStateOf(false)
    private var recording by mutableStateOf(false)
    private var statusText by mutableStateOf("")
    private var uiRevision by mutableIntStateOf(0)
    private var panelAnimationToken by mutableIntStateOf(0)
    private var panelDismissAnimating by mutableStateOf(false)
    private var panelNeedsEntryAnimation by mutableStateOf(true)
    private var panelOffsetX by mutableIntStateOf(dp(18))
    private var panelOffsetY by mutableIntStateOf(dp(220))
    private var selectedTaskId by mutableStateOf(prefs.getString(KEY_SELECTED_TASK_ID, null))
    private var lastStartedTaskId by mutableStateOf(prefs.getString(KEY_LAST_STARTED_TASK_ID, null))

    fun show() {
        scope.launch {
            val mode = runCatching { themePreferenceStore.themeModeFlow.first() }
                .getOrDefault(AppThemeMode.MONO_LIGHT)
            themeMode = mode
            loadTasks()
            overlayVisible = true
            settingsVisible = false
            settingsSheetVisible = false
            settingsDismissAnimating = false
            settingsRoute = SettingsRoute.TaskList
            settingsTask = null
            settingsEditorStore = null
            panelDismissAnimating = false
            panelNeedsEntryAnimation = true
            panelAnimationToken++
            ensureOverlayView()
            updateOverlayLayout()
            startThemeSync()
            touchUi()
        }
    }

    fun hide() {
        themeSyncJob?.cancel()
        themeSyncJob = null
        stopCaptureOverlay()
        removeSettingsOverlay()
        removeOverlay()
    }

    fun isShowing(): Boolean = overlayView != null

    private fun startThemeSync() {
        themeSyncJob?.cancel()
        themeSyncJob = scope.launch {
            themePreferenceStore.themeModeFlow.collect { mode ->
                themeMode = mode
            }
        }
    }

    private fun ensureOverlayView() {
        if (overlayView != null) {
            return
        }
        val owner = ControlOverlayComposeOwner().apply { attach() }
        val compose = ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setViewTreeLifecycleOwner(owner)
            setViewTreeViewModelStoreOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
            ViewCompat.setAccessibilityPaneTitle(this, "TaskControlOverlay")
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            panelWindowFlags(),
            PixelFormat.RGBA_8888,
        ).apply {
            title = "TaskControlOverlay"
            gravity = Gravity.TOP or Gravity.START
            x = panelOffsetX
            y = panelOffsetY
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        try {
            windowManager.addView(compose, params)
            overlayView = compose
            composeOwner = owner
            layoutParams = params
            val recomposerContext = AndroidUiDispatcher.CurrentThread
            val recomposer = Recomposer(recomposerContext)
            val recomposerJob = scope.launch(recomposerContext, start = CoroutineStart.UNDISPATCHED) {
                recomposer.runRecomposeAndApplyChanges()
            }
            overlayRecomposer = recomposer
            overlayRecomposerJob = recomposerJob
            compose.setParentCompositionContext(recomposer)
            compose.setContent {
                CmmClickerTheme(themeMode = themeMode) {
                    OverlayContent()
                }
            }
            compose.post {
                if (overlayView !== compose || composeOwner !== owner || owner.isDestroyed()) {
                    return@post
                }
                runCatching { compose.createComposition() }
                owner.start()
                owner.resume()
                Log.d(TAG, "overlay lifecycle moved to RESUMED")
            }
        } catch (e: Exception) {
            Log.e(TAG, "addView failed", e)
            owner.destroy()
        }
    }

    private fun ensureSettingsOverlayView() {
        if (settingsOverlayView != null) {
            return
        }
        val owner = ControlOverlayComposeOwner().apply { attach() }
        val compose = ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setViewTreeLifecycleOwner(owner)
            setViewTreeViewModelStoreOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
            ViewCompat.setAccessibilityPaneTitle(this, "TaskControlSettingsOverlay")
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            fullscreenWindowFlags(),
            PixelFormat.RGBA_8888,
        ).apply {
            title = "TaskControlSettingsOverlay"
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        try {
            windowManager.addView(compose, params)
            settingsOverlayView = compose
            settingsComposeOwner = owner
            val recomposerContext = AndroidUiDispatcher.CurrentThread
            val recomposer = Recomposer(recomposerContext)
            val recomposerJob = scope.launch(recomposerContext, start = CoroutineStart.UNDISPATCHED) {
                recomposer.runRecomposeAndApplyChanges()
            }
            settingsRecomposer = recomposer
            settingsRecomposerJob = recomposerJob
            compose.setParentCompositionContext(recomposer)
            compose.setContent {
                CmmClickerTheme(themeMode = themeMode) {
                    SettingsOverlayContent()
                }
            }
            compose.post {
                if (settingsOverlayView !== compose || settingsComposeOwner !== owner || owner.isDestroyed()) {
                    return@post
                }
                runCatching { compose.createComposition() }
                owner.start()
                owner.resume()
                Log.d(TAG, "settings overlay lifecycle moved to RESUMED")
            }
        } catch (e: Exception) {
            Log.e(TAG, "add settings overlay failed", e)
            owner.destroy()
        }
    }

    private fun removeSettingsOverlay() {
        val view = settingsOverlayView
        if (view != null) {
            runCatching { windowManager.removeView(view) }
        }
        settingsOverlayView = null
        settingsRecomposer?.cancel()
        settingsRecomposer = null
        settingsRecomposerJob?.cancel()
        settingsRecomposerJob = null
        settingsComposeOwner?.destroy()
        settingsComposeOwner = null
        settingsVisible = false
        settingsSheetVisible = false
        settingsDismissAnimating = false
        settingsRoute = SettingsRoute.TaskList
        settingsTask = null
        settingsEditorStore = null
    }

    private fun removeOverlay() {
        val view = overlayView
        if (view != null) {
            runCatching { windowManager.removeView(view) }
        }
        overlayView = null
        layoutParams = null
        overlayRecomposer?.cancel()
        overlayRecomposer = null
        overlayRecomposerJob?.cancel()
        overlayRecomposerJob = null
        composeOwner?.destroy()
        composeOwner = null
        overlayVisible = false
        settingsVisible = false
        settingsSheetVisible = false
        settingsDismissAnimating = false
        panelDismissAnimating = false
        panelNeedsEntryAnimation = true
        running = false
        recording = false
    }

    private fun panelWindowFlags(): Int {
        return WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
    }

    private fun fullscreenWindowFlags(): Int {
        return WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
    }

    private fun updateOverlayLayout() {
        val view = overlayView ?: return
        val params = layoutParams ?: return
        params.width = WindowManager.LayoutParams.WRAP_CONTENT
        params.height = WindowManager.LayoutParams.WRAP_CONTENT
        params.gravity = Gravity.TOP or Gravity.START
        params.x = panelOffsetX
        params.y = panelOffsetY
        params.flags = panelWindowFlags()
        runCatching {
            windowManager.updateViewLayout(view, params)
        }.onFailure {
            Log.e(TAG, "updateViewLayout failed", it)
        }
    }

    private fun touchUi() {
        uiRevision++
    }

    private fun onPanelDragged(deltaX: Float, deltaY: Float) {
        if (settingsVisible || panelDismissAnimating) {
            return
        }
        val view = overlayView ?: return
        val params = layoutParams ?: return
        if (params.gravity != (Gravity.TOP or Gravity.START)) {
            return
        }
        val bounds = windowManager.currentWindowMetrics.bounds
        val panelWidth = if (view.width > 0) view.width else dp(188)
        val panelHeight = if (view.height > 0) view.height else dp(64)
        val maxX = (bounds.width() - panelWidth).coerceAtLeast(0)
        val maxY = (bounds.height() - panelHeight).coerceAtLeast(0)
        panelOffsetX = (panelOffsetX + deltaX.roundToInt()).coerceIn(0, maxX)
        panelOffsetY = (panelOffsetY + deltaY.roundToInt()).coerceIn(0, maxY)
        params.x = panelOffsetX
        params.y = panelOffsetY
        runCatching {
            windowManager.updateViewLayout(view, params)
        }.onFailure {
            Log.e(TAG, "panel drag update failed", it)
        }
    }

    private fun dismissPanelWithAnimation() {
        if (panelDismissAnimating) {
            return
        }
        panelDismissAnimating = true
        touchUi()
        scope.launch {
            delay(220)
            hide()
        }
    }

    private suspend fun loadTasks() {
        val loaded = withContext(Dispatchers.IO) { taskRepository.listTasks() }
        tasks = loaded
        if (selectedTaskId == null || loaded.none { it.taskId == selectedTaskId }) {
            selectedTaskId = loaded.firstOrNull()?.taskId
            persistIds()
        }
    }

    private fun persistIds() {
        prefs.edit()
            .putString(KEY_SELECTED_TASK_ID, selectedTaskId)
            .putString(KEY_LAST_STARTED_TASK_ID, lastStartedTaskId)
            .apply()
    }

    private fun openSettingsPanel() {
        if (settingsVisible) {
            return
        }
        panelNeedsEntryAnimation = false
        settingsVisible = true
        settingsDismissAnimating = false
        settingsSheetVisible = false
        settingsRoute = SettingsRoute.TaskList
        settingsTask = null
        settingsEditorStore = null
        ensureSettingsOverlayView()
        touchUi()
        scope.launch {
            delay(16)
            settingsSheetVisible = true
            touchUi()
        }
    }

    private fun closeSettingsPanel(afterClosed: (() -> Unit)? = null) {
        if (!settingsVisible || settingsDismissAnimating) {
            return
        }
        settingsDismissAnimating = true
        settingsSheetVisible = false
        touchUi()
        scope.launch {
            delay(200)
            removeSettingsOverlay()
            afterClosed?.invoke()
        }
    }

    private fun onSettingsBackdropTap() {
        when (settingsRoute) {
            SettingsRoute.TaskList -> closeSettingsPanel()
            SettingsRoute.ActionList -> {
                settingsRoute = SettingsRoute.TaskList
                touchUi()
            }

            is SettingsRoute.NodeEditor -> {
                settingsRoute = SettingsRoute.ActionList
                touchUi()
            }
        }
    }

    private fun openTaskEditorInSettings(taskId: String) {
        scope.launch {
            val task = withContext(Dispatchers.IO) {
                taskRepository.getTask(taskId)
            }
            if (task == null) {
                statusText = "任务不存在"
                touchUi()
                return@launch
            }
            selectedTaskId = task.taskId
            persistIds()
            settingsTask = task
            settingsEditorStore = TaskGraphEditorStore(initialBundle = task.bundle)
            settingsRoute = SettingsRoute.ActionList
            statusText = "正在编辑：${task.name}"
            touchUi()
        }
    }

    private fun mutateSettingsEditor(
        message: String,
        mutation: (TaskGraphEditorStore) -> Unit,
    ) {
        val store = settingsEditorStore ?: return
        mutation(store)
        touchUi()
        persistSettingsEditor(message)
    }

    private fun persistSettingsEditor(message: String) {
        val store = settingsEditorStore ?: return
        val task = settingsTask ?: return
        val bundle = store.state().bundle.copy(
            bundleId = task.taskId,
            name = task.name,
        )
        statusText = message
        touchUi()
        scope.launch {
            val updated = withContext(Dispatchers.IO) {
                taskRepository.updateTaskBundle(
                    taskId = task.taskId,
                    bundle = bundle,
                )
            }
            if (updated != null) {
                settingsTask = updated
                if (selectedTaskId == updated.taskId) {
                    selectedTaskId = updated.taskId
                }
                loadTasks()
            }
            touchUi()
        }
    }

    @Composable
    private fun OverlayContent() {
        val revision = uiRevision
        if (!overlayVisible) {
            return
        }
        val animationToken = panelAnimationToken
        var panelEntered by remember(animationToken, panelNeedsEntryAnimation) {
            mutableStateOf(!panelNeedsEntryAnimation)
        }
        LaunchedEffect(animationToken) {
            if (!panelNeedsEntryAnimation) {
                panelEntered = true
                return@LaunchedEffect
            }
            panelEntered = false
            delay(18)
            panelEntered = true
            panelNeedsEntryAnimation = false
        }
        val panelVisible = panelEntered && !panelDismissAnimating && !settingsVisible
        val panelAlpha by animateFloatAsState(
            targetValue = if (panelVisible) 1f else 0f,
            animationSpec = tween(durationMillis = 180),
            label = "control_panel_alpha",
        )
        val panelScale by animateFloatAsState(
            targetValue = if (panelVisible) 1f else 0.98f,
            animationSpec = tween(durationMillis = 180),
            label = "control_panel_scale",
        )
        val panelTranslateY by animateFloatAsState(
            targetValue = if (panelVisible) 0f else 6f,
            animationSpec = tween(durationMillis = 180),
            label = "control_panel_translate_y",
        )
        val panelDragModifier = if (settingsVisible) {
            Modifier
        } else {
            Modifier.pointerInput(animationToken) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onPanelDragged(
                        deltaX = dragAmount.x,
                        deltaY = dragAmount.y,
                    )
                }
            }
        }

        Card(
            modifier = Modifier
                .width(186.dp)
                .graphicsLayer {
                    alpha = panelAlpha
                    scaleX = panelScale
                    scaleY = panelScale
                    translationY = panelTranslateY
                }
                .then(panelDragModifier),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircleActionIconButton(
                    enabled = !settingsVisible,
                    onClick = {
                        if (panelDismissAnimating || settingsVisible) {
                            return@CircleActionIconButton
                        }
                        openSettingsPanel()
                    },
                    icon = { tint ->
                        Icon(
                            imageVector = Icons.Rounded.Settings,
                            contentDescription = "设置",
                            tint = tint,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                )
                CircleActionIconButton(
                    enabled = !running && !settingsVisible,
                    filled = recording,
                    onClick = {
                        if (panelDismissAnimating || settingsVisible) {
                            return@CircleActionIconButton
                        }
                        if (recording) {
                            stopCaptureOverlay()
                            recording = false
                            statusText = "录制已取消"
                            touchUi()
                        } else {
                            startCaptureOverlay()
                        }
                    },
                    icon = { tint ->
                        Icon(
                            imageVector = if (recording) Icons.Rounded.Stop else Icons.Rounded.FiberManualRecord,
                            contentDescription = if (recording) "停止录制" else "录制",
                            tint = tint,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                )
                CircleActionIconButton(
                    enabled = !running && !settingsVisible,
                    filled = true,
                    onClick = {
                        if (panelDismissAnimating || settingsVisible) {
                            return@CircleActionIconButton
                        }
                        startLastTask()
                    },
                    icon = { tint ->
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = if (running) "运行中" else "开始",
                            tint = tint,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                )
                CircleActionIconButton(
                    enabled = !settingsVisible,
                    onClick = {
                        if (!settingsVisible) {
                            dismissPanelWithAnimation()
                        }
                    },
                    icon = { tint ->
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "移除面板",
                            tint = tint,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                )
            }
        }

        @Suppress("UNUSED_VARIABLE")
        val consumeRevision = revision
    }

    @Composable
    private fun SettingsOverlayContent() {
        if (!settingsVisible) {
            return
        }
        val route = settingsRoute
        val settingsScrimAlpha by animateFloatAsState(
            targetValue = if (settingsSheetVisible) OverlayStackMotion.SHEET_SCRIM_ALPHA else 0f,
            animationSpec = tween(durationMillis = 220),
            label = "control_settings_scrim_alpha",
        )
        val showActionLayer = route != SettingsRoute.TaskList
        val showNodeLayer = route is SettingsRoute.NodeEditor
        val showBaseLayer = !showNodeLayer
        Box(
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = settingsScrimAlpha))
                    .clickable { onSettingsBackdropTap() },
            )
            AnimatedVisibilityBox(
                visible = settingsSheetVisible,
                enter = fadeIn(
                    animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
                ) + slideInVertically(
                    animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
                    initialOffsetY = { fullHeight -> (fullHeight * 0.22f).roundToInt() },
                ),
                exit = fadeOut(
                    animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
                ) + slideOutVertically(
                    animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
                    targetOffsetY = { fullHeight -> (fullHeight * 0.12f).roundToInt() },
                ),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 14.dp, vertical = 18.dp)
                    .navigationBarsPadding()
                    .fillMaxWidth()
                    .fillMaxHeight(OverlayStackMotion.SHEET_HEIGHT_FRACTION)
                    .widthIn(max = OverlayStackMotion.SHEET_MAX_WIDTH_DP.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    ),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {},
                        ),
                ) {
                    val baseScale by animateFloatAsState(
                        targetValue = if (showActionLayer) OverlayStackMotion.PREVIOUS_LAYER_SCALE else 1f,
                        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
                        label = "settings_base_scale",
                    )
                    val baseTranslateY by animateFloatAsState(
                        targetValue = if (showActionLayer) OverlayStackMotion.PREVIOUS_LAYER_TRANSLATE_Y else 0f,
                        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
                        label = "settings_base_translate",
                    )
                    androidx.compose.animation.AnimatedVisibility(
                        visible = showBaseLayer,
                        enter = fadeIn(animationSpec = tween(durationMillis = 140)),
                        exit = fadeOut(animationSpec = tween(durationMillis = 120)),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        Card(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    scaleX = baseScale
                                    scaleY = baseScale
                                    translationY = baseTranslateY
                                },
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        ) {
                            Box(modifier = Modifier.fillMaxSize()) { SettingsTaskListLayer() }
                        }
                    }

                    androidx.compose.animation.AnimatedVisibility(
                        visible = showActionLayer,
                        enter = fadeIn(
                            animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
                        ) + slideInVertically(
                            animationSpec = spring(
                                dampingRatio = 0.9f,
                                stiffness = Spring.StiffnessLow,
                            ),
                            initialOffsetY = { fullHeight ->
                                (fullHeight * OverlayStackMotion.ENTER_OFFSET_RATIO).roundToInt()
                            },
                        ),
                        exit = fadeOut(
                            animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
                        ) + slideOutVertically(
                            animationSpec = spring(
                                dampingRatio = 0.95f,
                                stiffness = Spring.StiffnessMediumLow,
                            ),
                            targetOffsetY = { fullHeight ->
                                (fullHeight * OverlayStackMotion.EXIT_OFFSET_RATIO).roundToInt()
                            },
                        ),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        val actionScale by animateFloatAsState(
                            targetValue = if (showNodeLayer) OverlayStackMotion.PREVIOUS_LAYER_SCALE else 1f,
                            animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
                            label = "settings_action_scale",
                        )
                        val actionTranslateY by animateFloatAsState(
                            targetValue = if (showNodeLayer) OverlayStackMotion.PREVIOUS_LAYER_TRANSLATE_Y else 0f,
                            animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
                            label = "settings_action_translate",
                        )
                        val actionTopInset by animateDpAsState(
                            targetValue = if (showNodeLayer) {
                                OverlayStackMotion.BACKGROUND_LAYER_TOP_INSET_DP.dp
                            } else {
                                OverlayStackMotion.FOREGROUND_LAYER_TOP_INSET_DP.dp
                            },
                            animationSpec = spring(
                                dampingRatio = 0.95f,
                                stiffness = Spring.StiffnessMediumLow,
                            ),
                            label = "settings_action_top_inset",
                        )
                        Card(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(top = actionTopInset.coerceAtLeast(0.dp))
                                .graphicsLayer {
                                    scaleX = actionScale
                                    scaleY = actionScale
                                    translationY = actionTranslateY
                                },
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
                        ) {
                            Box(modifier = Modifier.fillMaxSize()) { SettingsActionListLayer() }
                        }
                    }

                    androidx.compose.animation.AnimatedVisibility(
                        visible = showNodeLayer,
                        enter = fadeIn(
                            animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
                        ) + slideInVertically(
                            animationSpec = spring(
                                dampingRatio = 0.9f,
                                stiffness = Spring.StiffnessLow,
                            ),
                            initialOffsetY = { fullHeight ->
                                (fullHeight * OverlayStackMotion.ENTER_OFFSET_RATIO).roundToInt()
                            },
                        ),
                        exit = fadeOut(
                            animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
                        ) + slideOutVertically(
                            animationSpec = spring(
                                dampingRatio = 0.95f,
                                stiffness = Spring.StiffnessMediumLow,
                            ),
                            targetOffsetY = { fullHeight ->
                                (fullHeight * OverlayStackMotion.EXIT_OFFSET_RATIO).roundToInt()
                            },
                        ),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        val nodeId = (route as? SettingsRoute.NodeEditor)?.nodeId.orEmpty()
                        Card(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(top = OverlayStackMotion.FOREGROUND_LAYER_TOP_INSET_DP.dp),
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
                        ) {
                            SettingsNodeEditorLayer(nodeId = nodeId)
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun SettingsPageHeader(
        title: String,
        showBack: Boolean,
        onBack: () -> Unit,
        onClose: () -> Unit,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showBack) {
                OutlinedButton(onClick = onBack) {
                    Text("返回")
                }
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(onClick = onClose) {
                Text("关闭")
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
    }

    @Composable
    private fun SettingsTaskListLayer() {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SettingsPageHeader(
                title = "任务设置",
                showBack = false,
                onBack = {},
                onClose = { closeSettingsPanel() },
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
            ) {
                SettingsTaskListPage()
            }
        }
    }

    @Composable
    private fun SettingsActionListLayer() {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SettingsPageHeader(
                title = "动作列表",
                showBack = true,
                onBack = {
                    settingsRoute = SettingsRoute.TaskList
                    touchUi()
                },
                onClose = { closeSettingsPanel() },
            )
            SettingsActionListPage()
        }
    }

    @Composable
    private fun SettingsNodeEditorLayer(nodeId: String) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SettingsPageHeader(
                title = "编辑动作",
                showBack = true,
                onBack = {
                    settingsRoute = SettingsRoute.ActionList
                    touchUi()
                },
                onClose = { closeSettingsPanel() },
            )
            SettingsNodeEditorPage(nodeId = nodeId)
        }
    }

    @Composable
    private fun SettingsTaskListPage() {
        TaskLibraryPanel(
            tasks = tasks,
            running = running,
            taskOperationMessage = statusText,
            onCreateTask = { name ->
                scope.launch {
                    withContext(Dispatchers.IO) {
                        taskRepository.createTask(name = name, withTemplate = true)
                    }
                    loadTasks()
                    statusText = "已新建任务"
                    touchUi()
                }
            },
            onTaskCardClick = { taskId ->
                openTaskEditorInSettings(taskId)
            },
            onRenameTask = { taskId, name ->
                scope.launch {
                    withContext(Dispatchers.IO) {
                        taskRepository.renameTask(taskId, name)
                    }
                    loadTasks()
                    statusText = "已重命名"
                    touchUi()
                }
            },
            onDuplicateTask = { taskId ->
                scope.launch {
                    withContext(Dispatchers.IO) {
                        taskRepository.duplicateTask(taskId)
                    }
                    loadTasks()
                    statusText = "已复制任务"
                    touchUi()
                }
            },
            onDeleteTask = { taskId ->
                scope.launch {
                    withContext(Dispatchers.IO) {
                        taskRepository.deleteTask(taskId)
                    }
                    if (selectedTaskId == taskId) {
                        selectedTaskId = null
                    }
                    loadTasks()
                    persistIds()
                    statusText = "已删除任务"
                    touchUi()
                }
            },
            onRunTask = { },
            showRunAction = false,
            showManageMenu = true,
            showCreateControls = true,
        )
    }

    @Composable
    private fun SettingsActionListPage() {
        val store = settingsEditorStore
        val task = settingsTask
        if (store == null || task == null) {
            Text(
                text = "任务加载失败，请返回任务列表重试",
                style = MaterialTheme.typography.bodySmall,
            )
            return
        }
        val state = store.state()
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = {
                    mutateSettingsEditor("已添加动作") { it.addActionNode() }
                },
            ) {
                Text("添加动作")
            }
            OutlinedButton(
                onClick = { persistSettingsEditor("已保存任务") },
            ) {
                Text("保存")
            }
        }
        Text(
            text = "任务：${task.name} | flow=${state.selectedFlowId}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val flow = state.selectedFlow
        if (flow == null) {
            Text("当前流程不存在")
            return
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            flow.nodes.forEach { node ->
                Card(
                    onClick = {
                        store.selectNode(node.nodeId)
                        settingsRoute = SettingsRoute.NodeEditor(node.nodeId)
                        touchUi()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = node.nodeId,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = nodeSummaryText(node),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }

    @Composable
    private fun SettingsNodeEditorPage(nodeId: String) {
        val store = settingsEditorStore
        if (store == null) {
            Text("编辑器不可用，请返回重试")
            return
        }
        val state = store.state()
        if (state.selectedNode?.nodeId != nodeId && nodeId.isNotBlank()) {
            store.selectNode(nodeId)
        }
        val node = store.state().selectedNode
        if (node == null) {
            Text("节点不存在，请返回动作列表")
            return
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "节点：${node.nodeId}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = "类型：${node.kind}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (node.kind == NodeKind.ACTION) {
                Text(
                    text = "动作类型",
                    style = MaterialTheme.typography.bodySmall,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf(
                        ActionType.CLICK,
                        ActionType.SWIPE,
                        ActionType.RECORD,
                        ActionType.DUP_CLICK,
                    ).forEach { type ->
                        val selected = node.actionType == type
                        if (selected) {
                            OutlinedButton(
                                onClick = { },
                            ) {
                                Text(type.name)
                            }
                        } else {
                            OutlinedButton(
                                onClick = {
                                    mutateSettingsEditor("已更新动作类型") {
                                        it.updateSelectedNodeActionType(type)
                                    }
                                },
                            ) {
                                Text(type.name)
                            }
                        }
                    }
                }
            }
            val paramsSnapshot = node.params.toSortedMap()
            if (paramsSnapshot.isEmpty()) {
                Text(
                    text = "当前节点没有参数",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                paramsSnapshot.forEach { (key, value) ->
                    OutlinedTextField(
                        value = value?.toString().orEmpty(),
                        onValueChange = { next ->
                            mutateSettingsEditor("已更新参数 $key") {
                                it.updateSelectedNodeParam(key, next)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(key) },
                        singleLine = true,
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = {
                        mutateSettingsEditor("已填充默认值") {
                            it.fillDefaultsForSelectedNode()
                        }
                    },
                ) {
                    Text("填充默认值")
                }
                OutlinedButton(
                    onClick = {
                        val removed = store.removeSelectedNode()
                        if (removed) {
                            persistSettingsEditor("已删除动作")
                            settingsRoute = SettingsRoute.ActionList
                            touchUi()
                        }
                    },
                ) {
                    Text("删除动作")
                }
            }
        }
    }

    private fun nodeSummaryText(node: FlowNode): String {
        return when (node.kind) {
            NodeKind.START -> "Start"
            NodeKind.END -> "End"
            NodeKind.ACTION -> "Action ${node.actionType?.name ?: "-"}"
            NodeKind.BRANCH -> "Branch"
            NodeKind.JUMP -> "Jump"
            NodeKind.FOLDER_REF -> "FolderRef"
            NodeKind.SUB_TASK_REF -> "SubTaskRef"
        }
    }

    private fun startLastTask() {
        if (running) {
            return
        }
        scope.launch {
            val candidateTaskId = lastStartedTaskId
                ?: selectedTaskId
                ?: tasks.firstOrNull()?.taskId
            if (candidateTaskId == null) {
                statusText = "没有可执行任务"
                touchUi()
                return@launch
            }
            val task = withContext(Dispatchers.IO) { taskRepository.getTask(candidateTaskId) }
            if (task == null) {
                statusText = "任务不存在，无法执行"
                touchUi()
                return@launch
            }
            selectedTaskId = task.taskId
            lastStartedTaskId = task.taskId
            persistIds()
            running = true
            statusText = "运行中..."
            touchUi()
            val result = runCatching {
                withContext(Dispatchers.Default) {
                    FlowRuntimeEngine(
                        options = RuntimeEngineOptions(
                            dryRun = false,
                            maxSteps = 200,
                            stopOnValidationError = true,
                        ),
                    ).execute(task.bundle)
                }
            }.getOrElse { error ->
                running = false
                statusText = "运行失败: ${error.message ?: "unknown"}"
                touchUi()
                return@launch
            }
            val summary = buildString {
                append("模式=REAL ")
                append("状态=${result.status} ")
                append("step=${result.stepCount} ")
                append("msg=${result.message ?: "-"}")
            }
            withContext(Dispatchers.IO) {
                taskRepository.updateTaskRunInfo(
                    taskId = task.taskId,
                    status = result.status.name,
                    summary = summary,
                )
            }
            loadTasks()
            running = false
            statusText = summary
            touchUi()
        }
    }

    private fun startCaptureOverlay() {
        if (recording) {
            return
        }
        if (selectedTaskId == null && tasks.isEmpty()) {
            statusText = "暂无任务，无法录制"
            touchUi()
            return
        }
        val hintView = FrameLayout(context).apply {
            setBackgroundColor(Color.parseColor("#22000000"))
            isClickable = true
            isFocusable = true
        }
        val hintText = TextView(context).apply {
            text = "录制中：点击记录单击，拖动记录轨迹，松手后自动保存"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#CC000000"))
            setPadding(dp(12), dp(8), dp(12), dp(8))
            textSize = 13f
        }
        val textParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            topMargin = dp(36)
        }
        hintView.addView(hintText, textParams)

        val overlayParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        hintView.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    captureStartX = event.rawX
                    captureStartY = event.rawY
                    captureStartTime = event.eventTime
                    capturePath.clear()
                    capturePath += event.rawX to event.rawY
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    capturePath += event.rawX to event.rawY
                    true
                }

                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL,
                -> {
                    capturePath += event.rawX to event.rawY
                    val duration = (event.eventTime - captureStartTime).coerceAtLeast(60L)
                    val metrics = windowManager.currentWindowMetrics.bounds
                    val width = metrics.width().coerceAtLeast(1)
                    val height = metrics.height().coerceAtLeast(1)
                    val endX = event.rawX
                    val endY = event.rawY
                    val distance = hypot(
                        (endX - captureStartX).toDouble(),
                        (endY - captureStartY).toDouble(),
                    )
                    val gesture = if (distance <= dp(18).toDouble()) {
                        RecordedGesture.Click(
                            xRatio = (captureStartX / width.toFloat()).coerceIn(0f, 1f).toDouble(),
                            yRatio = (captureStartY / height.toFloat()).coerceIn(0f, 1f).toDouble(),
                            durationMs = 60L,
                        )
                    } else {
                        val points = capturePath
                            .filterIndexed { index, _ ->
                                index == 0 || index == capturePath.lastIndex || index % 2 == 0
                            }
                            .map { (x, y) ->
                                (x / width.toFloat()).coerceIn(0f, 1f).toDouble() to
                                    (y / height.toFloat()).coerceIn(0f, 1f).toDouble()
                            }
                            .distinct()
                        RecordedGesture.Path(
                            points = points.ifEmpty {
                                listOf(
                                    (captureStartX / width.toFloat()).coerceIn(0f, 1f).toDouble() to
                                        (captureStartY / height.toFloat()).coerceIn(0f, 1f).toDouble(),
                                    (endX / width.toFloat()).coerceIn(0f, 1f).toDouble() to
                                        (endY / height.toFloat()).coerceIn(0f, 1f).toDouble(),
                                )
                            },
                            durationMs = duration.coerceIn(120L, 2400L),
                        )
                    }
                    stopCaptureOverlay()
                    handleRecordedGesture(gesture)
                    true
                }

                else -> false
            }
        }

        runCatching {
            windowManager.addView(hintView, overlayParams)
            captureView = hintView
            recording = true
            statusText = "录制中，请在屏幕上执行手势"
            touchUi()
        }.onFailure {
            Log.e(TAG, "add record overlay failed", it)
            recording = false
            statusText = "开启录制失败"
            touchUi()
        }
    }

    private fun stopCaptureOverlay() {
        captureView?.let { view ->
            runCatching { windowManager.removeView(view) }
        }
        captureView = null
        recording = false
        touchUi()
    }

    private fun handleRecordedGesture(gesture: RecordedGesture) {
        scope.launch {
            val targetTaskId = selectedTaskId
                ?: lastStartedTaskId
                ?: tasks.firstOrNull()?.taskId
            if (targetTaskId == null) {
                statusText = "没有可保存的任务"
                touchUi()
                return@launch
            }
            val updated = withContext(Dispatchers.IO) {
                val record = taskRepository.getTask(targetTaskId) ?: return@withContext null
                val nextBundle = appendRecordedGesture(record.bundle, gesture)
                taskRepository.updateTaskBundle(targetTaskId, nextBundle)
            }
            if (updated == null) {
                statusText = "录制保存失败"
                touchUi()
                return@launch
            }
            selectedTaskId = updated.taskId
            persistIds()
            loadTasks()
            statusText = "已录制并保存到: ${updated.name}"
            touchUi()
        }
    }

    private fun appendRecordedGesture(
        bundle: TaskBundle,
        gesture: RecordedGesture,
    ): TaskBundle {
        val flow = bundle.findFlow(bundle.entryFlowId) ?: return bundle
        val endNode = flow.nodes.firstOrNull { it.kind == NodeKind.END } ?: return bundle
        val endNodeId = endNode.nodeId
        val previousEdge = flow.edges.lastOrNull {
            it.toNodeId == endNodeId && it.conditionType == EdgeConditionType.ALWAYS
        }
        val previousNodeId = previousEdge?.fromNodeId
            ?: flow.nodes.lastOrNull { it.nodeId != endNodeId }?.nodeId
            ?: flow.entryNodeId
        val safePreviousNodeId = if (previousNodeId == endNodeId) flow.entryNodeId else previousNodeId

        val nodeId = "record_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(4)}"
        val edgeAId = "edge_${UUID.randomUUID().toString().take(8)}"
        val edgeBId = "edge_${UUID.randomUUID().toString().take(8)}"

        val actionNode = when (gesture) {
            is RecordedGesture.Click -> FlowNode(
                nodeId = nodeId,
                kind = NodeKind.ACTION,
                actionType = ActionType.CLICK,
                pluginId = "builtin.basic_gesture",
                params = mapOf(
                    "x" to gesture.xRatio,
                    "y" to gesture.yRatio,
                    "durationMs" to gesture.durationMs,
                ),
            )

            is RecordedGesture.Path -> FlowNode(
                nodeId = nodeId,
                kind = NodeKind.ACTION,
                actionType = ActionType.RECORD,
                pluginId = "builtin.basic_gesture",
                params = mapOf(
                    "points" to gesture.points.map { (x, y) ->
                        mapOf(
                            "x" to x,
                            "y" to y,
                        )
                    },
                    "durationMs" to gesture.durationMs,
                ),
            )
        }

        val retainedEdges = flow.edges.filterNot {
            it.fromNodeId == safePreviousNodeId &&
                it.toNodeId == endNodeId &&
                it.conditionType == EdgeConditionType.ALWAYS
        }
        val updatedFlow = flow.copy(
            nodes = flow.nodes + actionNode,
            edges = retainedEdges +
                FlowEdge(
                    edgeId = edgeAId,
                    fromNodeId = safePreviousNodeId,
                    toNodeId = nodeId,
                    conditionType = EdgeConditionType.ALWAYS,
                ) +
                FlowEdge(
                    edgeId = edgeBId,
                    fromNodeId = nodeId,
                    toNodeId = endNodeId,
                    conditionType = EdgeConditionType.ALWAYS,
                ),
        )
        return bundle.copy(
            schemaVersion = BundleSchema.CURRENT_VERSION,
            flows = bundle.flows.map { item ->
                if (item.flowId == updatedFlow.flowId) {
                    updatedFlow
                } else {
                    item
                }
            },
        )
    }

    private fun dp(value: Int): Int {
        return (value * context.resources.displayMetrics.density).roundToInt()
    }
}

private class ControlOverlayComposeOwner : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    private val store = ViewModelStore()
    private var destroyed = false

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    override val viewModelStore: ViewModelStore
        get() = store

    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    fun attach() {
        if (destroyed) {
            return
        }
        savedStateRegistryController.performAttach()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    fun start() {
        if (destroyed || lifecycleRegistry.currentState == Lifecycle.State.DESTROYED) {
            return
        }
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
    }

    fun resume() {
        if (destroyed || lifecycleRegistry.currentState == Lifecycle.State.DESTROYED) {
            return
        }
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    fun destroy() {
        if (destroyed) {
            return
        }
        destroyed = true
        if (lifecycleRegistry.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        }
        if (lifecycleRegistry.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        }
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        store.clear()
    }

    fun isDestroyed(): Boolean = destroyed || lifecycleRegistry.currentState == Lifecycle.State.DESTROYED
}
