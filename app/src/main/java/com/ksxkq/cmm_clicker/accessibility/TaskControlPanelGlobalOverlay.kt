package com.ksxkq.cmm_clicker.accessibility

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.os.SystemClock
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import androidx.compose.animation.AnimatedVisibility as AnimatedVisibilityBox
import androidx.compose.animation.Crossfade
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
import androidx.compose.material.icons.rounded.Pause
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
import androidx.compose.runtime.mutableLongStateOf
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
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

    private data class RecordedStroke(
        val points: List<Pair<Double, Double>>,
        val timestampsMs: List<Long>,
        val startDelayMs: Long,
        val durationMs: Long,
    )

    private sealed interface RecordedGesture {
        data class Click(val xRatio: Double, val yRatio: Double, val durationMs: Long) : RecordedGesture

        data class MultiPath(
            val strokes: List<RecordedStroke>,
            val durationMs: Long,
        ) : RecordedGesture
    }

    private data class PointerTrack(
        val pointerId: Int,
        val downTimeMs: Long,
        val points: MutableList<Pair<Float, Float>> = mutableListOf(),
        val timestampsMs: MutableList<Long> = mutableListOf(),
        var upTimeMs: Long = downTimeMs,
    )

    private enum class PanelMode {
        NORMAL,
        RECORDING,
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
    private var recordingTickerJob: Job? = null

    private var captureView: View? = null
    private var captureTrailView: RecordingTrailOverlayView? = null
    private val activePointerTracks = linkedMapOf<Int, PointerTrack>()
    private val completedPointerTracks = mutableListOf<PointerTrack>()
    private var gestureSessionStartTimeMs = 0L
    private var captureLayoutParams: WindowManager.LayoutParams? = null
    private var recordingStartedAtMs = 0L
    private var recordingPausedAtMs = 0L
    private var recordingPausedAccumulatedMs = 0L

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
    private var recordingPaused by mutableStateOf(false)
    private var replayingGesture by mutableStateOf(false)
    private var panelMode by mutableStateOf(PanelMode.NORMAL)
    private var recordingSaveDialogVisible by mutableStateOf(false)
    private var recordingSaveTaskName by mutableStateOf("")
    private var recordedStepCount by mutableIntStateOf(0)
    private var recordingElapsedMs by mutableLongStateOf(0L)
    private var statusText by mutableStateOf("")
    private var uiRevision by mutableIntStateOf(0)
    private var panelAnimationToken by mutableIntStateOf(0)
    private var panelDismissAnimating by mutableStateOf(false)
    private var panelNeedsEntryAnimation by mutableStateOf(true)
    private var panelOffsetX by mutableIntStateOf(dp(18))
    private var panelOffsetY by mutableIntStateOf(dp(220))
    private var selectedTaskId by mutableStateOf(prefs.getString(KEY_SELECTED_TASK_ID, null))
    private var lastStartedTaskId by mutableStateOf(prefs.getString(KEY_LAST_STARTED_TASK_ID, null))
    private val recordedGestures = mutableListOf<RecordedGesture>()

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
            recordingSaveDialogVisible = false
            recordingSaveTaskName = ""
            panelMode = PanelMode.NORMAL
            recordingPaused = false
            replayingGesture = false
            recordedGestures.clear()
            recordedStepCount = 0
            stopRecordingTicker(resetElapsed = true)
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
        stopRecordingTicker(resetElapsed = true)
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
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
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
        recordingSaveDialogVisible = false
        recordingSaveTaskName = ""
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
        recordingPaused = false
        replayingGesture = false
        panelMode = PanelMode.NORMAL
        recordingSaveDialogVisible = false
        recordingSaveTaskName = ""
        recordedGestures.clear()
        recordedStepCount = 0
        stopRecordingTicker(resetElapsed = true)
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
        if (settingsVisible || recordingSaveDialogVisible || panelMode == PanelMode.RECORDING) {
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
            settingsVisible = false
            settingsSheetVisible = false
            settingsDismissAnimating = false
            settingsRoute = SettingsRoute.TaskList
            settingsTask = null
            settingsEditorStore = null
            if (!recordingSaveDialogVisible) {
                removeSettingsOverlay()
            }
            afterClosed?.invoke()
        }
    }

    private fun onAuxOverlayBackdropTap() {
        if (recordingSaveDialogVisible) {
            discardRecordingSession("已取消保存")
            return
        }
        if (!settingsVisible) {
            return
        }
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
        val panelVisible = panelEntered && !panelDismissAnimating && !settingsVisible && !recordingSaveDialogVisible
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
                .width(if (panelMode == PanelMode.RECORDING) 222.dp else 186.dp)
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
            Column(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Crossfade(
                    targetState = panelMode,
                    animationSpec = tween(durationMillis = 80, easing = FastOutSlowInEasing),
                    label = "control_panel_mode",
                ) { mode ->
                    if (mode == PanelMode.RECORDING) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    CircleActionIconButton(
                                        enabled = recording,
                                        filled = !recordingPaused,
                                        onClick = { toggleRecordingPause() },
                                        icon = { tint ->
                                            Icon(
                                                imageVector = if (recordingPaused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                                                contentDescription = if (recordingPaused) "继续录制" else "暂停录制",
                                                tint = tint,
                                                modifier = Modifier.size(18.dp),
                                            )
                                        },
                                    )
                                    CircleActionIconButton(
                                        enabled = recording || recordedStepCount > 0,
                                        filled = true,
                                        onClick = { stopRecordingSessionAndPromptSave() },
                                        icon = { tint ->
                                            Icon(
                                                imageVector = Icons.Rounded.Stop,
                                                contentDescription = "停止并保存",
                                                tint = tint,
                                                modifier = Modifier.size(18.dp),
                                            )
                                        },
                                    )
                                }
                                Text(
                                    text = formatRecordingDuration(recordingElapsedMs),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                text = if (recordingPaused) {
                                    "录制已暂停 · $recordedStepCount 步"
                                } else if (replayingGesture) {
                                    "正在回放 · $recordedStepCount 步"
                                } else {
                                    "录制中 · $recordedStepCount 步"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircleActionIconButton(
                                enabled = !settingsVisible && !running,
                                onClick = {
                                    if (panelDismissAnimating || settingsVisible || running) {
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
                                filled = false,
                                onClick = {
                                    if (panelDismissAnimating || settingsVisible || running) {
                                        return@CircleActionIconButton
                                    }
                                    startRecordingSession()
                                },
                                icon = { tint ->
                                    Icon(
                                        imageVector = Icons.Rounded.FiberManualRecord,
                                        contentDescription = "录制",
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
                }
            }
        }

        @Suppress("UNUSED_VARIABLE")
        val consumeRevision = revision
    }

    @Composable
    private fun SettingsOverlayContent() {
        if (!settingsVisible && !recordingSaveDialogVisible) {
            return
        }
        val route = settingsRoute
        val sheetVisible = settingsVisible && settingsSheetVisible
        val scrimTargetAlpha = when {
            sheetVisible -> OverlayStackMotion.SHEET_SCRIM_ALPHA
            recordingSaveDialogVisible -> 0.5f
            else -> 0f
        }
        val settingsScrimAlpha by animateFloatAsState(
            targetValue = scrimTargetAlpha,
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
                    .clickable { onAuxOverlayBackdropTap() },
            )
            AnimatedVisibilityBox(
                visible = sheetVisible,
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
            AnimatedVisibilityBox(
                visible = recordingSaveDialogVisible,
                enter = fadeIn(animationSpec = tween(durationMillis = 180)) + slideInVertically(
                    animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
                    initialOffsetY = { fullHeight -> (fullHeight * 0.1f).roundToInt() },
                ),
                exit = fadeOut(animationSpec = tween(durationMillis = 140)) + slideOutVertically(
                    animationSpec = tween(durationMillis = 140, easing = FastOutSlowInEasing),
                    targetOffsetY = { fullHeight -> (fullHeight * 0.06f).roundToInt() },
                ),
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 18.dp)
                    .fillMaxWidth()
                    .widthIn(max = 420.dp),
            ) {
                RecordingSaveDialogCard()
            }
        }
    }

    @Composable
    private fun RecordingSaveDialogCard() {
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "保存录制任务",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = "已录制 $recordedStepCount 步动作，保存为新任务。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = recordingSaveTaskName,
                    onValueChange = { recordingSaveTaskName = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("任务名称") },
                    singleLine = true,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        onClick = { discardRecordingSession("已取消保存") },
                    ) {
                        Text("丢弃")
                    }
                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        onClick = { confirmSaveRecordingSession() },
                    ) {
                        Text("保存")
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

    private fun startRecordingTicker() {
        recordingTickerJob?.cancel()
        recordingStartedAtMs = System.currentTimeMillis()
        recordingPausedAtMs = 0L
        recordingPausedAccumulatedMs = 0L
        recordingElapsedMs = 0L
        recordingTickerJob = scope.launch {
            while (isActive && panelMode == PanelMode.RECORDING) {
                updateRecordingElapsed()
                delay(120)
            }
        }
    }

    private fun stopRecordingTicker(resetElapsed: Boolean) {
        recordingTickerJob?.cancel()
        recordingTickerJob = null
        recordingPausedAtMs = 0L
        recordingPausedAccumulatedMs = 0L
        if (resetElapsed) {
            recordingElapsedMs = 0L
        }
    }

    private fun updateRecordingElapsed() {
        if (recordingStartedAtMs <= 0L) {
            recordingElapsedMs = 0L
            return
        }
        val now = System.currentTimeMillis()
        val activePausedDuration = if (recordingPaused && recordingPausedAtMs > 0L) {
            (now - recordingPausedAtMs).coerceAtLeast(0L)
        } else {
            0L
        }
        recordingElapsedMs = (
            now - recordingStartedAtMs - recordingPausedAccumulatedMs - activePausedDuration
            ).coerceAtLeast(0L)
    }

    private fun formatRecordingDuration(ms: Long): String {
        val totalSeconds = (ms / 1000L).coerceAtLeast(0L)
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L
        return if (hours > 0L) {
            String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
        }
    }

    private fun startRecordingSession() {
        if (recording || running) {
            return
        }
        recordingPaused = false
        replayingGesture = false
        recordedGestures.clear()
        recordedStepCount = 0
        recordingSaveDialogVisible = false
        recordingSaveTaskName = ""
        statusText = "录制已开始，请在屏幕上执行手势"
        touchUi()
        startCaptureOverlay()
    }

    private fun toggleRecordingPause() {
        if (!recording) {
            return
        }
        recordingPaused = !recordingPaused
        val now = System.currentTimeMillis()
        if (recordingPaused) {
            recordingPausedAtMs = now
        } else if (recordingPausedAtMs > 0L) {
            recordingPausedAccumulatedMs += (now - recordingPausedAtMs).coerceAtLeast(0L)
            recordingPausedAtMs = 0L
        }
        updateRecordingElapsed()
        statusText = if (recordingPaused) {
            "录制已暂停，已记录 $recordedStepCount 步"
        } else {
            "继续录制，已记录 $recordedStepCount 步"
        }
        touchUi()
    }

    private fun stopRecordingSessionAndPromptSave() {
        if (panelMode != PanelMode.RECORDING) {
            return
        }
        stopCaptureOverlay()
        if (recordedGestures.isEmpty()) {
            panelMode = PanelMode.NORMAL
            stopRecordingTicker(resetElapsed = true)
            statusText = "未录制到任何动作"
            touchUi()
            return
        }
        stopRecordingTicker(resetElapsed = false)
        recordingSaveTaskName = buildDefaultRecordedTaskName()
        recordingSaveDialogVisible = true
        ensureSettingsOverlayView()
        touchUi()
    }

    private fun discardRecordingSession(message: String) {
        stopCaptureOverlay()
        panelMode = PanelMode.NORMAL
        recordingPaused = false
        replayingGesture = false
        recordingSaveDialogVisible = false
        recordingSaveTaskName = ""
        recordedGestures.clear()
        recordedStepCount = 0
        stopRecordingTicker(resetElapsed = true)
        if (!settingsVisible) {
            removeSettingsOverlay()
        }
        statusText = message
        touchUi()
    }

    private fun confirmSaveRecordingSession() {
        if (!recordingSaveDialogVisible || recordedGestures.isEmpty()) {
            discardRecordingSession("未录制到任何动作")
            return
        }
        val targetName = recordingSaveTaskName.trim().ifEmpty { buildDefaultRecordedTaskName() }
        val gestures = recordedGestures.toList()
        scope.launch {
            val saved = withContext(Dispatchers.IO) {
                val created = taskRepository.createTask(name = targetName, withTemplate = false)
                var nextBundle = created.bundle
                gestures.forEach { gesture ->
                    nextBundle = appendRecordedGesture(nextBundle, gesture)
                }
                taskRepository.updateTaskBundle(created.taskId, nextBundle)
            }
            if (saved == null) {
                statusText = "录制保存失败"
                touchUi()
                return@launch
            }
            selectedTaskId = saved.taskId
            lastStartedTaskId = saved.taskId
            persistIds()
            loadTasks()
            panelMode = PanelMode.NORMAL
            recordingPaused = false
            replayingGesture = false
            recordingSaveDialogVisible = false
            recordingSaveTaskName = ""
            recordedGestures.clear()
            recordedStepCount = 0
            stopRecordingTicker(resetElapsed = true)
            if (!settingsVisible) {
                removeSettingsOverlay()
            }
            statusText = "已保存录制任务：${saved.name}"
            touchUi()
        }
    }

    private fun startCaptureOverlay() {
        if (recording) {
            return
        }
        val hintView = FrameLayout(context).apply {
            setBackgroundColor(Color.parseColor("#12000000"))
            isClickable = true
            isFocusable = true
        }
        val trailView = RecordingTrailOverlayView(context)
        hintView.addView(
            trailView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )
        val hintText = TextView(context).apply {
            text = "录制中：支持单指/多指、长按后拖动，松手后自动回放"
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
            val actionIndex = event.actionIndex.coerceAtLeast(0)
            val (eventX, eventY) = eventPosition(event, actionIndex)
            if (event.actionMasked == MotionEvent.ACTION_DOWN &&
                isTouchWithinPanel(eventX, eventY) &&
                activePointerTracks.isEmpty()
            ) {
                return@setOnTouchListener false
            }
            if (!recording || recordingPaused || replayingGesture) {
                return@setOnTouchListener true
            }
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    resetCaptureGestureState()
                    gestureSessionStartTimeMs = event.eventTime
                    addPointerTrack(event, actionIndex, forceSample = true)
                    true
                }

                MotionEvent.ACTION_POINTER_DOWN -> {
                    addPointerTrack(event, actionIndex, forceSample = true)
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    updatePointerTracks(event)
                    true
                }

                MotionEvent.ACTION_POINTER_UP -> {
                    liftPointerTrack(event, actionIndex)
                    true
                }

                MotionEvent.ACTION_UP -> {
                    liftPointerTrack(event, actionIndex)
                    buildGestureFromTracks()?.let { gesture ->
                        recordedGestures += gesture
                        recordedStepCount = recordedGestures.size
                        statusText = "已录制 $recordedStepCount 步，正在回放"
                        touchUi()
                        scope.launch {
                            replayRecordedGesture(gesture)
                        }
                    }
                    resetCaptureGestureState()
                    true
                }

                MotionEvent.ACTION_CANCEL -> {
                    resetCaptureGestureState()
                    true
                }

                else -> false
            }
        }

        runCatching {
            windowManager.addView(hintView, overlayParams)
            captureView = hintView
            captureTrailView = trailView
            captureLayoutParams = overlayParams
            recording = true
            recordingPaused = false
            resetCaptureGestureState()
            panelNeedsEntryAnimation = false
            panelMode = PanelMode.RECORDING
            startRecordingTicker()
            statusText = "录制中，请在屏幕上执行手势"
            touchUi()
            restackControlPanelAboveCapture()
        }.onFailure {
            Log.e(TAG, "add record overlay failed", it)
            recording = false
            recordingPaused = false
            panelMode = PanelMode.NORMAL
            stopRecordingTicker(resetElapsed = true)
            statusText = "开启录制失败"
            touchUi()
        }
    }

    private fun restackControlPanelAboveCapture() {
        val panel = overlayView ?: return
        val params = layoutParams ?: return
        if (!panel.isAttachedToWindow) {
            return
        }
        runCatching {
            windowManager.removeView(panel)
            windowManager.addView(panel, params)
        }.onFailure {
            Log.w(TAG, "restackControlPanelAboveCapture failed", it)
        }
    }

    private fun stopCaptureOverlay() {
        captureView?.let { view ->
            runCatching { windowManager.removeView(view) }
        }
        captureView = null
        captureTrailView = null
        captureLayoutParams = null
        recording = false
        recordingPaused = false
        resetCaptureGestureState()
        touchUi()
    }

    private fun resetCaptureGestureState() {
        activePointerTracks.clear()
        completedPointerTracks.clear()
        gestureSessionStartTimeMs = 0L
        captureTrailView?.clearAll()
    }

    private fun eventPosition(event: MotionEvent, index: Int): Pair<Float, Float> {
        return event.getX(index).coerceAtLeast(0f) to event.getY(index).coerceAtLeast(0f)
    }

    private fun addPointerTrack(
        event: MotionEvent,
        pointerIndex: Int,
        forceSample: Boolean = false,
    ) {
        if (pointerIndex < 0 || pointerIndex >= event.pointerCount) {
            return
        }
        val pointerId = event.getPointerId(pointerIndex)
        val existing = activePointerTracks[pointerId]
        val track = existing ?: PointerTrack(
            pointerId = pointerId,
            downTimeMs = event.eventTime,
        ).also {
            activePointerTracks[pointerId] = it
            if (gestureSessionStartTimeMs <= 0L) {
                gestureSessionStartTimeMs = event.eventTime
            }
        }
        val (x, y) = eventPosition(event, pointerIndex)
        appendTrackSample(track, x, y, event.eventTime, force = forceSample)
        if (existing == null) {
            captureTrailView?.onPointerDown(pointerId, x, y)
        }
    }

    private fun updatePointerTracks(event: MotionEvent) {
        for (index in 0 until event.pointerCount) {
            val pointerId = event.getPointerId(index)
            val track = activePointerTracks[pointerId] ?: continue
            val (x, y) = eventPosition(event, index)
            appendTrackSample(track, x, y, event.eventTime, force = false)
            captureTrailView?.onPointerMove(pointerId, x, y)
        }
    }

    private fun liftPointerTrack(event: MotionEvent, pointerIndex: Int) {
        if (pointerIndex < 0 || pointerIndex >= event.pointerCount) {
            return
        }
        val pointerId = event.getPointerId(pointerIndex)
        val track = activePointerTracks.remove(pointerId) ?: return
        val (x, y) = eventPosition(event, pointerIndex)
        appendTrackSample(track, x, y, event.eventTime, force = true)
        track.upTimeMs = event.eventTime
        completedPointerTracks += track
        captureTrailView?.onPointerUp(pointerId, x, y)
    }

    private fun appendTrackSample(
        track: PointerTrack,
        x: Float,
        y: Float,
        eventTimeMs: Long,
        force: Boolean,
    ) {
        val lastIndex = track.points.lastIndex
        if (!force && lastIndex >= 0) {
            val lastTime = track.timestampsMs[lastIndex]
            if (eventTimeMs == lastTime) {
                val (lastX, lastY) = track.points[lastIndex]
                val isSamePoint = hypot(
                    (x - lastX).toDouble(),
                    (y - lastY).toDouble(),
                ) < 0.5
                if (isSamePoint) {
                    return
                }
            }
        }
        track.points += x to y
        track.timestampsMs += eventTimeMs
        track.upTimeMs = eventTimeMs
    }

    private fun buildGestureFromTracks(): RecordedGesture? {
        val tracks = completedPointerTracks
            .ifEmpty { activePointerTracks.values.toList() }
            .filter { it.points.isNotEmpty() }
            .sortedBy { it.downTimeMs }
        if (tracks.isEmpty()) {
            return null
        }
        val metrics = windowManager.currentWindowMetrics.bounds
        val width = metrics.width().coerceAtLeast(1)
        val height = metrics.height().coerceAtLeast(1)
        val sessionStart = gestureSessionStartTimeMs
            .takeIf { it > 0L }
            ?: tracks.minOf { it.downTimeMs }
        tracks.forEachIndexed { index, track ->
            val firstTs = track.timestampsMs.firstOrNull() ?: track.downTimeMs
            val lastTs = track.timestampsMs.lastOrNull() ?: track.upTimeMs
            val rawDuration = (track.upTimeMs - track.downTimeMs).coerceAtLeast(0L)
            val tsDuration = (lastTs - firstTs).coerceAtLeast(0L)
            Log.d(
                TAG,
                "record track[$index] pointer=${track.pointerId} points=${track.points.size} rawDuration=${rawDuration}ms tsDuration=${tsDuration}ms down=${track.downTimeMs} up=${track.upTimeMs}",
            )
        }
        val normalizedStrokes = tracks.mapNotNull { track ->
            toRecordedStroke(
                track = track,
                sessionStartMs = sessionStart,
                width = width,
                height = height,
            )
        }
        if (normalizedStrokes.isEmpty()) {
            return null
        }
        normalizedStrokes.forEachIndexed { index, stroke ->
            val tsLast = stroke.timestampsMs.lastOrNull() ?: 0L
            Log.d(
                TAG,
                "record stroke[$index] points=${stroke.points.size} startDelay=${stroke.startDelayMs}ms duration=${stroke.durationMs}ms tsLast=${tsLast}ms",
            )
        }
        if (normalizedStrokes.size == 1 && isClickStroke(normalizedStrokes.first())) {
            val stroke = normalizedStrokes.first()
            val firstPoint = stroke.points.firstOrNull() ?: return null
            Log.d(
                TAG,
                "record gesture=click duration=${stroke.durationMs}ms",
            )
            return RecordedGesture.Click(
                xRatio = firstPoint.first,
                yRatio = firstPoint.second,
                durationMs = stroke.durationMs.coerceIn(50L, 2000L),
            )
        }
        val totalDuration = normalizedStrokes.maxOfOrNull { it.startDelayMs + it.durationMs } ?: 300L
        val pauseHints = normalizedStrokes.sumOf { stroke ->
            val ts = stroke.timestampsMs
            if (ts.size < 2) {
                0
            } else {
                var pauses = 0
                for (i in 1 until ts.size) {
                    if ((ts[i] - ts[i - 1]) >= 300L) {
                        pauses++
                    }
                }
                pauses
            }
        }
        Log.d(
            TAG,
            "record gesture=multi strokes=${normalizedStrokes.size} totalDuration=${totalDuration}ms pauseHints=$pauseHints",
        )
        return RecordedGesture.MultiPath(
            strokes = normalizedStrokes,
            durationMs = totalDuration.coerceIn(120L, 60_000L),
        )
    }

    private suspend fun replayRecordedGesture(gesture: RecordedGesture) {
        if (replayingGesture) {
            return
        }
        replayingGesture = true
        when (gesture) {
            is RecordedGesture.Click -> {
                Log.d(
                    TAG,
                    "replay start click duration=${gesture.durationMs}ms at=(${gesture.xRatio},${gesture.yRatio})",
                )
            }

            is RecordedGesture.MultiPath -> {
                val minStart = gesture.strokes.minOfOrNull { it.startDelayMs } ?: 0L
                val maxStart = gesture.strokes.maxOfOrNull { it.startDelayMs } ?: 0L
                Log.d(
                    TAG,
                    "replay start multi strokes=${gesture.strokes.size} totalDuration=${gesture.durationMs}ms startSpread=${maxStart - minStart}ms",
                )
                gesture.strokes.forEachIndexed { index, stroke ->
                    val tsLast = stroke.timestampsMs.lastOrNull() ?: 0L
                    val absStart = stroke.startDelayMs
                    val absEnd = stroke.startDelayMs + stroke.durationMs
                    Log.d(
                        TAG,
                        "replay stroke[$index] points=${stroke.points.size} startDelay=${stroke.startDelayMs}ms duration=${stroke.durationMs}ms tsLast=${tsLast}ms abs=[$absStart,$absEnd]",
                    )
                }
            }
        }
        val panel = overlayView
        val capture = captureView
        panel?.visibility = View.INVISIBLE
        capture?.visibility = View.INVISIBLE
        setCaptureTouchEnabled(enabled = false)
        try {
            delay(80)
            val success = when (gesture) {
                is RecordedGesture.Click -> AccessibilityGestureExecutor.performClick(
                    xRatio = gesture.xRatio,
                    yRatio = gesture.yRatio,
                    durationMs = gesture.durationMs.coerceAtLeast(40L),
                )

                is RecordedGesture.MultiPath -> AccessibilityGestureExecutor.performRecordStrokes(
                    strokes = gesture.strokes.map { stroke ->
                        AccessibilityGestureExecutor.GestureStroke(
                            points = stroke.points,
                            timestampsMs = stroke.timestampsMs,
                            startDelayMs = stroke.startDelayMs,
                            durationMs = stroke.durationMs,
                        )
                    },
                )
            }
            delay(100)
            val gestureStats = TaskAccessibilityService.gestureStatsText()
            Log.d(TAG, "replay result success=$success stats=$gestureStats")
            statusText = if (success) {
                "回放完成，已录制 $recordedStepCount 步"
            } else {
                "回放失败，已录制 $recordedStepCount 步"
            }
        } finally {
            setCaptureTouchEnabled(enabled = true)
            capture?.visibility = View.VISIBLE
            panel?.visibility = View.VISIBLE
            replayingGesture = false
            touchUi()
        }
    }

    private fun setCaptureTouchEnabled(enabled: Boolean) {
        val view = captureView ?: return
        val params = captureLayoutParams ?: return
        val nextFlags = if (enabled) {
            params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        } else {
            params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        }
        if (params.flags == nextFlags) {
            return
        }
        params.flags = nextFlags
        runCatching {
            windowManager.updateViewLayout(view, params)
        }.onFailure {
            Log.w(TAG, "setCaptureTouchEnabled failed: enabled=$enabled", it)
        }
    }

    private fun toRecordedStroke(
        track: PointerTrack,
        sessionStartMs: Long,
        width: Int,
        height: Int,
    ): RecordedStroke? {
        val allPoints = track.points
        val allTimestamps = track.timestampsMs
        if (allPoints.isEmpty() || allTimestamps.isEmpty() || allPoints.size != allTimestamps.size) {
            return null
        }
        val points = allPoints.toMutableList()
        val timestamps = allTimestamps.toMutableList()
        if (points.size == 1) {
            points += points.first()
            timestamps += (timestamps.first() + 1L)
        }
        val start = track.downTimeMs
        val duration = (track.upTimeMs - start).coerceAtLeast(1L)
        return RecordedStroke(
            points = points.map { (x, y) ->
                (x / width.toFloat()).coerceIn(0f, 1f).toDouble() to
                    (y / height.toFloat()).coerceIn(0f, 1f).toDouble()
            },
            timestampsMs = timestamps.map { (it - start).coerceAtLeast(0L) },
            startDelayMs = (start - sessionStartMs).coerceAtLeast(0L),
            durationMs = duration,
        )
    }

    private fun isClickStroke(stroke: RecordedStroke): Boolean {
        val points = stroke.points
        if (points.isEmpty()) {
            return false
        }
        val first = points.first()
        val last = points.last()
        val distance = hypot(
            (last.first - first.first).toDouble(),
            (last.second - first.second).toDouble(),
        )
        val bounds = windowManager.currentWindowMetrics.bounds
        val baseSize = minOf(bounds.width(), bounds.height()).coerceAtLeast(1)
        val clickDistanceRatio = dp(18).toDouble() / baseSize.toDouble()
        return distance <= clickDistanceRatio
    }

    private fun isTouchWithinPanel(rawX: Float, rawY: Float): Boolean {
        val panelWidth = (overlayView?.width ?: dp(188)).coerceAtLeast(1)
        val panelHeight = (overlayView?.height ?: dp(72)).coerceAtLeast(1)
        val left = panelOffsetX
        val top = panelOffsetY
        val right = left + panelWidth
        val bottom = top + panelHeight
        return rawX >= left && rawX <= right && rawY >= top && rawY <= bottom
    }

    private fun buildDefaultRecordedTaskName(): String {
        val formatter = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        return "录制任务 ${formatter.format(Date())}"
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

            is RecordedGesture.MultiPath -> FlowNode(
                nodeId = nodeId,
                kind = NodeKind.ACTION,
                actionType = ActionType.RECORD,
                pluginId = "builtin.basic_gesture",
                params = mapOf(
                    "points" to gesture.strokes.firstOrNull()?.points?.map { (x, y) ->
                        mapOf("x" to x, "y" to y)
                    }.orEmpty(),
                    "strokes" to gesture.strokes.map { stroke ->
                        mapOf(
                            "points" to stroke.points.map { (x, y) ->
                                mapOf("x" to x, "y" to y)
                            },
                            "timestampsMs" to stroke.timestampsMs,
                            "startDelayMs" to stroke.startDelayMs,
                            "durationMs" to stroke.durationMs,
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

private class RecordingTrailOverlayView(
    context: Context,
) : View(context) {
    private data class CompletedTrail(
        val path: Path,
        val createdAtMs: Long,
    )

    private val activePaths = linkedMapOf<Int, Path>()
    private val activePoints = linkedMapOf<Int, Pair<Float, Float>>()
    private val completedTrails = mutableListOf<CompletedTrail>()
    private val trailFadeMs = 260L

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#33FF6D00")
        style = Paint.Style.STROKE
        strokeWidth = 8f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val corePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#88FFA000")
        style = Paint.Style.FILL
    }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#66FFFFFF")
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    fun onPointerDown(pointerId: Int, x: Float, y: Float) {
        val path = Path().apply { moveTo(x, y) }
        activePaths[pointerId] = path
        activePoints[pointerId] = x to y
        invalidate()
    }

    fun onPointerMove(pointerId: Int, x: Float, y: Float) {
        val path = activePaths[pointerId] ?: return
        path.lineTo(x, y)
        activePoints[pointerId] = x to y
        invalidate()
    }

    fun onPointerUp(pointerId: Int, x: Float, y: Float) {
        val path = activePaths.remove(pointerId) ?: return
        path.lineTo(x, y)
        activePoints.remove(pointerId)
        completedTrails += CompletedTrail(
            path = Path(path),
            createdAtMs = SystemClock.uptimeMillis(),
        )
        invalidate()
    }

    fun clearAll() {
        activePaths.clear()
        activePoints.clear()
        completedTrails.clear()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val now = SystemClock.uptimeMillis()
        val iterator = completedTrails.iterator()
        while (iterator.hasNext()) {
            val trail = iterator.next()
            val elapsed = now - trail.createdAtMs
            if (elapsed >= trailFadeMs) {
                iterator.remove()
                continue
            }
            val alpha = ((1f - elapsed.toFloat() / trailFadeMs.toFloat()) * 255f).toInt().coerceIn(0, 255)
            val oldLineAlpha = linePaint.alpha
            linePaint.alpha = alpha
            canvas.drawPath(trail.path, linePaint)
            linePaint.alpha = oldLineAlpha
        }

        activePaths.values.forEach { path ->
            canvas.drawPath(path, linePaint)
        }
        activePoints.values.forEach { (x, y) ->
            canvas.drawCircle(x, y, 14f, corePaint)
            canvas.drawCircle(x, y, 20f, ringPaint)
        }

        if (completedTrails.isNotEmpty()) {
            postInvalidateOnAnimation()
        }
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
