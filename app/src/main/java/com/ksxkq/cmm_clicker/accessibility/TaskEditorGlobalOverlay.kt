package com.ksxkq.cmm_clicker.accessibility

import android.content.Context
import android.graphics.PixelFormat
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.AndroidUiDispatcher
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
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
import com.ksxkq.cmm_clicker.core.model.EdgeConditionType
import com.ksxkq.cmm_clicker.core.model.FlowNode
import com.ksxkq.cmm_clicker.core.model.NodeKind
import com.ksxkq.cmm_clicker.core.model.TaskFlow
import com.ksxkq.cmm_clicker.feature.editor.EditorActionTypeCatalog
import com.ksxkq.cmm_clicker.feature.editor.EditorParamSchemaRegistry
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

class TaskEditorGlobalOverlay(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    private object OverlayMotion {
        const val SCRIM_DURATION_MS = 220
        const val SHEET_ENTER_DURATION_MS = 280
        const val SHEET_EXIT_DURATION_MS = 180
        const val SHEET_ENTER_OFFSET_PX = 220
        const val DETAIL_EXIT_DURATION_MS = 240
    }

    companion object {
        private const val TAG = "TaskEditorOverlay"
    }
    private data class OverlayAction(
        val text: String,
        val style: OverlayButtonStyle = OverlayButtonStyle.OUTLINE,
        val onClick: () -> Unit,
    )

    private sealed interface OverlayRoute {
        data object ActionList : OverlayRoute
        data object FlowManager : OverlayRoute

        data class NodeEditor(
            val nodeId: String,
        ) : OverlayRoute
    }

    private enum class OverlayButtonStyle {
        SOLID,
        OUTLINE,
        TONAL,
    }

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val themePreferenceStore = ThemePreferenceStore(context)
    private val taskRepository = LocalFileTaskRepository(context)

    private var overlayView: ComposeView? = null
    private var composeOwner: OverlayComposeOwner? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var overlayRecomposer: Recomposer? = null
    private var overlayRecomposerJob: Job? = null
    private var overlayDismissJob: Job? = null

    private var currentTask by mutableStateOf<TaskRecord?>(null)
    private var editorStore by mutableStateOf<TaskGraphEditorStore?>(null)
    private var themeMode by mutableStateOf(AppThemeMode.MONO_LIGHT)
    private var overlayVisible by mutableStateOf(false)
    private var routeStack by mutableStateOf(listOf<OverlayRoute>(OverlayRoute.ActionList))
    private var previewVisible by mutableStateOf(false)
    private var statusText by mutableStateOf("")
    private var editorRevision by mutableIntStateOf(0)

    private var themeSyncJob: Job? = null

    fun show(taskId: String) {
        scope.launch {
            val (task, currentThemeMode) = withContext(Dispatchers.IO) {
                val loadedTask = taskRepository.getTask(taskId)
                val loadedTheme = runCatching { themePreferenceStore.themeModeFlow.first() }
                    .getOrDefault(AppThemeMode.MONO_LIGHT)
                loadedTask to loadedTheme
            }
            if (task == null) {
                return@launch
            }
            themeMode = currentThemeMode
            currentTask = task
            editorStore = TaskGraphEditorStore(initialBundle = task.bundle)
            routeStack = listOf(OverlayRoute.ActionList)
            previewVisible = false
            statusText = "全局浮窗已打开"
            overlayDismissJob?.cancel()
            overlayDismissJob = null
            overlayVisible = true
            touchEditor()
            ensureOverlayView()
            startThemeSync()
        }
    }

    fun hide(animate: Boolean = true) {
        themeSyncJob?.cancel()
        themeSyncJob = null
        if (!animate || overlayView == null) {
            removeOverlay()
            return
        }
        if (!overlayVisible) {
            return
        }
        overlayVisible = false
        touchEditor()
        overlayDismissJob?.cancel()
        overlayDismissJob = scope.launch {
            delay((OverlayMotion.SHEET_EXIT_DURATION_MS + 40).toLong())
            removeOverlay()
        }
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
            overlayDismissJob?.cancel()
            overlayDismissJob = null
            if (!overlayVisible) {
                overlayVisible = true
                touchEditor()
            }
            resetOverlayLayout()
            return
        }
        val owner = OverlayComposeOwner().apply {
            attach()
        }
        val compose = ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setViewTreeLifecycleOwner(owner)
            setViewTreeViewModelStoreOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
            ViewCompat.setAccessibilityPaneTitle(this, "TaskEditorOverlay")
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.RGBA_8888,
        ).apply {
            title = "TaskEditorOverlay"
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
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
            recomposerJob.invokeOnCompletion { throwable ->
                if (throwable != null) {
                    Log.e(TAG, "recomposer job finished with error", throwable)
                } else {
                    Log.d(TAG, "recomposer job finished")
                }
            }
            overlayRecomposer = recomposer
            overlayRecomposerJob = recomposerJob
            compose.setParentCompositionContext(recomposer)
            Log.d(TAG, "addView success size=${params.width}x${params.height} pos=(${params.x},${params.y})")
            compose.setContent {
                Log.d(TAG, "compose lambda entered")
                SideEffect {
                    Log.d(TAG, "compose side effect committed")
                }
                LaunchedEffect(Unit) {
                    Log.d(TAG, "compose content started")
                }
                LaunchedEffect(Unit) {
                    Log.d(TAG, "overlay state task=${currentTask?.taskId} revision=$editorRevision")
                }
                CmmClickerTheme(themeMode = themeMode) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = Color.Transparent,
                    ) {
                        OverlayContent()
                    }
                }
            }
            Log.d(TAG, "setContent invoked")
            compose.post {
                if (overlayView !== compose || composeOwner !== owner || owner.isDestroyed()) {
                    Log.d(
                        TAG,
                        "skip composition start because overlay already disposed " +
                            "sameView=${overlayView === compose} sameOwner=${composeOwner === owner} " +
                            "ownerDestroyed=${owner.isDestroyed()} attached=${compose.isAttachedToWindow}",
                    )
                    return@post
                }
                runCatching {
                    compose.createComposition()
                }.onSuccess {
                    Log.d(TAG, "createComposition invoked")
                }.onFailure { error ->
                    Log.e(TAG, "createComposition failed", error)
                }
                if (overlayView !== compose || composeOwner !== owner || owner.isDestroyed()) {
                    Log.d(
                        TAG,
                        "skip lifecycle resume because overlay already disposed " +
                            "sameView=${overlayView === compose} sameOwner=${composeOwner === owner} " +
                            "ownerDestroyed=${owner.isDestroyed()} attached=${compose.isAttachedToWindow}",
                    )
                    return@post
                }
                owner.start()
                owner.resume()
                Log.d(TAG, "overlay lifecycle moved to RESUMED")
            }
            scope.launch(Dispatchers.Main.immediate) {
                delay(800)
                Log.d(
                    TAG,
                    "compose monitor attached=${compose.isAttachedToWindow} " +
                        "recomposerActive=${recomposerJob.isActive} " +
                        "state=${recomposer.currentState.value}",
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "addView failed", e)
            owner.destroy()
        }
    }

    private fun resetOverlayLayout() {
        val params = layoutParams ?: return
        val view = overlayView ?: return
        params.width = WindowManager.LayoutParams.MATCH_PARENT
        params.height = WindowManager.LayoutParams.MATCH_PARENT
        params.x = 0
        params.y = 0
        try {
            windowManager.updateViewLayout(view, params)
            Log.d(TAG, "reset layout size=${params.width}x${params.height} pos=(${params.x},${params.y})")
        } catch (e: Exception) {
            Log.e(TAG, "reset layout failed", e)
        }
    }

    private fun removeOverlay() {
        overlayDismissJob?.cancel()
        overlayDismissJob = null
        val view = overlayView
        if (view != null) {
            try {
                windowManager.removeView(view)
            } catch (_: Exception) {
                // ignore
            }
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
        routeStack = listOf(OverlayRoute.ActionList)
    }

    private fun touchEditor() {
        editorRevision++
    }

    private fun pushRoute(route: OverlayRoute) {
        routeStack = routeStack + route
        touchEditor()
    }

    private fun popRoute() {
        if (routeStack.size <= 1) {
            return
        }
        routeStack = routeStack.dropLast(1)
        touchEditor()
    }

    private fun dismissByBackdrop() {
        if (routeStack.size > 1) {
            popRoute()
        } else {
            hide()
        }
    }

    private fun popToRouteDepth(targetDepth: Int) {
        val maxDepth = routeStack.lastIndex
        if (maxDepth <= 0) {
            return
        }
        val depth = targetDepth.coerceIn(0, maxDepth)
        if (depth == maxDepth) {
            return
        }
        routeStack = routeStack.take(depth + 1)
        touchEditor()
    }

    private data class BreadcrumbItem(
        val label: String,
        val routeDepth: Int,
    )

    private fun buildBreadcrumbs(
        taskName: String,
        state: TaskGraphEditorState,
    ): List<BreadcrumbItem> {
        val breadcrumbs = mutableListOf(
            BreadcrumbItem(
                label = taskName.ifBlank { "任务" },
                routeDepth = 0,
            ),
        )
        routeStack.forEachIndexed { index, route ->
            when (route) {
                OverlayRoute.ActionList -> breadcrumbs += BreadcrumbItem(
                    label = "动作列表",
                    routeDepth = index,
                )

                OverlayRoute.FlowManager -> breadcrumbs += BreadcrumbItem(
                    label = "流程管理",
                    routeDepth = index,
                )

                is OverlayRoute.NodeEditor -> {
                    val node = state.selectedFlow?.findNode(route.nodeId)
                        ?: state.bundle.flows.asSequence()
                            .mapNotNull { it.findNode(route.nodeId) }
                            .firstOrNull()
                    breadcrumbs += BreadcrumbItem(
                        label = if (node != null) {
                            "编辑 ${nodeSummaryText(node)}"
                        } else {
                            "编辑 ${route.nodeId}"
                        },
                        routeDepth = index,
                    )
                }
            }
        }
        return breadcrumbs
    }

    private fun persistCurrentTask(message: String) {
        val store = editorStore ?: return
        val task = currentTask ?: return
        val bundle = store.state().bundle.copy(
            bundleId = task.taskId,
            name = task.name,
        )
        statusText = message
        touchEditor()
        scope.launch {
            val updated = withContext(Dispatchers.IO) {
                taskRepository.updateTaskBundle(taskId = task.taskId, bundle = bundle)
            }
            if (updated != null) {
                currentTask = updated
            }
            touchEditor()
        }
    }

    private fun repairSelectedJumpTarget(store: TaskGraphEditorStore) {
        val current = store.state()
        val fallbackFlowId = current.selectedFlowId
        val fallbackNodeId = current.selectedFlow?.entryNodeId
            ?: current.selectedNodeId
            ?: "start"
        store.updateSelectedNodeParam("targetFlowId", fallbackFlowId)
        store.updateSelectedNodeParam("targetNodeId", fallbackNodeId)
    }

    private fun mutateStore(
        message: String,
        mutation: (TaskGraphEditorStore) -> Unit,
    ) {
        val store = editorStore ?: return
        mutation(store)
        touchEditor()
        persistCurrentTask(message)
    }

    @Composable
    private fun OverlayContent() {
        val revision = editorRevision
        val store = editorStore
        val task = currentTask
        if (store == null || task == null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text("overlay store unavailable")
            }
            return
        }
        val state = store.state()
        val currentRoute = routeStack.lastOrNull() ?: OverlayRoute.ActionList
        val editingNodeId = (currentRoute as? OverlayRoute.NodeEditor)?.nodeId
        val breadcrumbs = buildBreadcrumbs(taskName = task.name, state = state)
        var sheetVisible by remember(task.taskId) { mutableStateOf(false) }
        var activeDetailRoute by remember(task.taskId) { mutableStateOf<OverlayRoute?>(null) }
        var detailLayerVisible by remember(task.taskId) { mutableStateOf(false) }
        LaunchedEffect(task.taskId) {
            sheetVisible = false
            activeDetailRoute = null
            detailLayerVisible = false
            delay(16)
            if (overlayVisible) {
                sheetVisible = true
            }
        }
        LaunchedEffect(overlayVisible) {
            if (!overlayVisible) {
                sheetVisible = false
            } else {
                sheetVisible = true
            }
        }
        LaunchedEffect(currentRoute) {
            if (currentRoute == OverlayRoute.ActionList) {
                detailLayerVisible = false
            } else {
                activeDetailRoute = currentRoute
                detailLayerVisible = true
            }
        }
        LaunchedEffect(detailLayerVisible, activeDetailRoute) {
            if (!detailLayerVisible && activeDetailRoute != null) {
                delay(OverlayMotion.DETAIL_EXIT_DURATION_MS.toLong())
                if (!detailLayerVisible) {
                    activeDetailRoute = null
                }
            }
        }
        val scrimAlpha by animateFloatAsState(
            targetValue = if (sheetVisible) OverlayStackMotion.SHEET_SCRIM_ALPHA else 0f,
            animationSpec = tween(
                durationMillis = OverlayMotion.SCRIM_DURATION_MS,
                easing = FastOutSlowInEasing,
            ),
            label = "overlay_scrim_alpha",
        )
        LaunchedEffect(editingNodeId, revision) {
            val selectedId = editingNodeId ?: return@LaunchedEffect
            if (state.selectedNode?.nodeId != selectedId) {
                store.selectNode(selectedId)
                touchEditor()
            }
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = scrimAlpha))
                .pointerInput(overlayVisible) {
                    detectTapGestures(
                        onTap = {
                            if (overlayVisible) {
                                dismissByBackdrop()
                            }
                        },
                    )
                }
                .imePadding()
                .padding(horizontal = 14.dp, vertical = 18.dp),
            contentAlignment = Alignment.BottomCenter,
        ) {
            AnimatedVisibility(
                visible = sheetVisible,
                enter = fadeIn(
                    animationSpec = tween(
                        durationMillis = OverlayMotion.SHEET_ENTER_DURATION_MS,
                        easing = FastOutSlowInEasing,
                    ),
                ) + slideInVertically(
                    animationSpec = tween(
                        durationMillis = OverlayMotion.SHEET_ENTER_DURATION_MS,
                        easing = FastOutSlowInEasing,
                    ),
                    initialOffsetY = { OverlayMotion.SHEET_ENTER_OFFSET_PX },
                ),
                exit = fadeOut(
                    animationSpec = tween(
                        durationMillis = OverlayMotion.SHEET_EXIT_DURATION_MS,
                        easing = FastOutSlowInEasing,
                    ),
                ) + slideOutVertically(
                    animationSpec = tween(
                        durationMillis = OverlayMotion.SHEET_EXIT_DURATION_MS,
                        easing = FastOutSlowInEasing,
                    ),
                    targetOffsetY = { OverlayMotion.SHEET_ENTER_OFFSET_PX / 2 },
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(OverlayStackMotion.SHEET_HEIGHT_FRACTION)
                    .widthIn(max = OverlayStackMotion.SHEET_MAX_WIDTH_DP.dp),
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
                    val detailVisible = detailLayerVisible
                    val baseScale by animateFloatAsState(
                        targetValue = if (detailVisible) OverlayStackMotion.PREVIOUS_LAYER_SCALE else 1f,
                        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
                        label = "stack_base_scale",
                    )
                    val baseTranslateY by animateFloatAsState(
                        targetValue = if (detailVisible) OverlayStackMotion.PREVIOUS_LAYER_TRANSLATE_Y else 0f,
                        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
                        label = "stack_base_translate_y",
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = baseScale
                                scaleY = baseScale
                                translationY = baseTranslateY
                            },
                    ) {
                        ActionListScreen(
                            breadcrumbs = breadcrumbs,
                            taskName = task.name,
                            state = state,
                            onAddAction = {
                                mutateStore("已添加动作") { it.addActionNode() }
                            },
                            onTogglePreview = {
                                previewVisible = !previewVisible
                                touchEditor()
                            },
                            onSave = {
                                persistCurrentTask("已保存")
                            },
                            onEditNode = { nodeId ->
                                store.selectNode(nodeId)
                                pushRoute(OverlayRoute.NodeEditor(nodeId))
                            },
                            onRepairJumpTargetFromPreview = { nodeId ->
                                mutateStore("已修复跳转目标") {
                                    it.selectNode(nodeId)
                                    repairSelectedJumpTarget(it)
                                }
                            },
                            onOpenFlowManager = {
                                pushRoute(OverlayRoute.FlowManager)
                            },
                            onNavigateByBreadcrumb = { depth ->
                                popToRouteDepth(depth)
                            },
                            onClose = { hide() },
                        )
                    }

                    AnimatedVisibility(
                        visible = detailLayerVisible && activeDetailRoute != null,
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
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = {},
                                ),
                        ) {
                            when (val displayedRoute = activeDetailRoute) {
                                null,
                                OverlayRoute.ActionList,
                                -> Unit
                                OverlayRoute.FlowManager -> {
                                    FlowManagerScreen(
                                        breadcrumbs = breadcrumbs,
                                        state = state,
                                        scaffoldModifier = Modifier
                                            .fillMaxSize()
                                            .padding(top = 20.dp),
                                        onBack = { popRoute() },
                                        onNavigateByBreadcrumb = { depth ->
                                            popToRouteDepth(depth)
                                        },
                                        onClose = { hide() },
                                        onSelectFlow = { flowId ->
                                            store.selectFlow(flowId)
                                            touchEditor()
                                        },
                                        onSetEntryNode = { nodeId ->
                                            mutateStore("已设置入口节点=$nodeId") {
                                                it.setSelectedFlowEntryNode(nodeId)
                                            }
                                        },
                                        onRenameFlow = { flowName ->
                                            mutateStore("已重命名流程") {
                                                it.renameSelectedFlow(flowName)
                                            }
                                        },
                                        onAddFlow = { flowName ->
                                            mutateStore("已新增流程") {
                                                it.addFlow(flowName)
                                            }
                                        },
                                        onDeleteFlow = {
                                            when (val result = store.deleteSelectedFlow()) {
                                                TaskGraphEditorStore.DeleteSelectedFlowResult.Success -> {
                                                    statusText = "已删除流程"
                                                    touchEditor()
                                                    persistCurrentTask("已删除流程")
                                                    popRoute()
                                                }

                                                TaskGraphEditorStore.DeleteSelectedFlowResult.LastFlowBlocked -> {
                                                    statusText = "删除失败：至少保留一个流程"
                                                    touchEditor()
                                                }

                                                TaskGraphEditorStore.DeleteSelectedFlowResult.NotFound -> {
                                                    statusText = "删除失败：当前流程不存在"
                                                    touchEditor()
                                                }

                                                is TaskGraphEditorStore.DeleteSelectedFlowResult.Referenced -> {
                                                    val preview = result.references
                                                        .take(3)
                                                        .joinToString(",") { "${it.flowId}/${it.nodeId}" }
                                                    val suffix = if (result.references.size > 3) {
                                                        "..."
                                                    } else {
                                                        ""
                                                    }
                                                    statusText = "删除失败：被引用于 $preview$suffix"
                                                    touchEditor()
                                                }
                                            }
                                        },
                                    )
                                }

                                is OverlayRoute.NodeEditor -> {
                                    NodeEditScreen(
                                        breadcrumbs = breadcrumbs,
                                        state = state,
                                        scaffoldModifier = Modifier
                                            .fillMaxSize()
                                            .padding(top = 20.dp),
                                        onBack = {
                                            popRoute()
                                        },
                                        onNavigateByBreadcrumb = { depth ->
                                            popToRouteDepth(depth)
                                        },
                                        onClose = { hide() },
                                        onDelete = {
                                            mutateStore("已删除动作") {
                                                it.removeSelectedNode()
                                            }
                                            popRoute()
                                        },
                                        onSave = {
                                            persistCurrentTask("已保存")
                                        },
                                        onDone = {
                                            popRoute()
                                        },
                                        onUpdateNodeKind = { kind ->
                                            val selectedRouteNodeId = displayedRoute.nodeId
                                            mutateStore("已切换 kind=$kind") {
                                                it.updateSelectedNodeKind(kind)
                                                if (kind != NodeKind.ACTION) {
                                                    it.selectNode(selectedRouteNodeId)
                                                }
                                            }
                                        },
                                        onUpdateActionType = { actionType ->
                                            mutateStore("已切换 actionType=${actionType.raw}") {
                                                it.updateSelectedNodeActionType(actionType)
                                            }
                                        },
                                        onUpdateEnabled = { enabled ->
                                            mutateStore("已更新 enabled=$enabled") {
                                                it.updateSelectedNodeEnabled(enabled)
                                            }
                                        },
                                        onUpdateActive = { active ->
                                            mutateStore("已更新 active=$active") {
                                                it.updateSelectedNodeActive(active)
                                            }
                                        },
                                        onUpdateParam = { key, value ->
                                            mutateStore("已设置 $key=$value") {
                                                it.updateSelectedNodeParam(key, value)
                                            }
                                        },
                                        onRepairJumpTarget = {
                                            mutateStore("已修复跳转目标") {
                                                repairSelectedJumpTarget(it)
                                            }
                                        },
                                        onFillDefaults = {
                                            mutateStore("已填充默认参数") {
                                                it.fillDefaultsForSelectedNode()
                                            }
                                        },
                                        onUpdateBranchTarget = { condition, selectedTargetNodeId ->
                                            mutateStore("已设置 ${condition.name}=$selectedTargetNodeId") {
                                                it.updateSelectedBranchTarget(condition, selectedTargetNodeId)
                                            }
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun ActionListScreen(
        breadcrumbs: List<BreadcrumbItem>,
        taskName: String,
        state: TaskGraphEditorState,
        onAddAction: () -> Unit,
        onTogglePreview: () -> Unit,
        onSave: () -> Unit,
        onEditNode: (String) -> Unit,
        onRepairJumpTargetFromPreview: (String) -> Unit,
        onOpenFlowManager: () -> Unit,
        onNavigateByBreadcrumb: (Int) -> Unit,
        onClose: () -> Unit,
    ) {
        val flow = state.selectedFlow
        val actionNodes = flow?.nodes?.filter { it.kind != NodeKind.START && it.kind != NodeKind.END } ?: emptyList()
        OverlayDialogScaffold(
            title = "任务动作",
            showBack = false,
            breadcrumbs = breadcrumbs,
            onBreadcrumbNavigate = onNavigateByBreadcrumb,
            headerActions = listOf(
                OverlayAction(
                    text = "流程",
                    style = OverlayButtonStyle.OUTLINE,
                    onClick = onOpenFlowManager,
                ),
                OverlayAction(
                    text = if (previewVisible) "隐藏预览" else "查看预览",
                    style = if (previewVisible) OverlayButtonStyle.SOLID else OverlayButtonStyle.OUTLINE,
                    onClick = onTogglePreview,
                ),
            ),
            footerActions = listOf(
                OverlayAction(text = "添加动作", style = OverlayButtonStyle.SOLID, onClick = onAddAction),
                OverlayAction(text = "保存", style = OverlayButtonStyle.OUTLINE, onClick = onSave),
            ),
            onBack = {},
            onClose = onClose,
        ) {
            Text(
                text = "任务: $taskName",
                style = MaterialTheme.typography.bodyMedium,
            )
            if (previewVisible && flow != null) {
                Spacer(modifier = Modifier.height(8.dp))
                SectionTitle("Flow Preview")
                FlowPreviewGraph(
                    flow = flow,
                    selectedNodeId = state.selectedNodeId,
                    onSelectNode = { onEditNode(it) },
                    onOpenEdgeTarget = { onEditNode(it) },
                )
                val textPreview = buildString {
                    append("flow=${flow.flowId}\n")
                    flow.edges.forEach { edge ->
                        append("${edge.fromNodeId} --${edge.conditionType.name}--> ${edge.toNodeId}\n")
                    }
                    flow.nodes
                        .filter { it.kind == NodeKind.JUMP || it.kind == NodeKind.FOLDER_REF || it.kind == NodeKind.SUB_TASK_REF }
                        .forEach { node ->
                            val tf = node.params["targetFlowId"]?.toString().orEmpty()
                            val tn = node.params["targetNodeId"]?.toString().orEmpty()
                            append("${node.nodeId} --${node.kind.name}--> $tf/$tn\n")
                        }
                }
                Text(
                    text = textPreview.trim(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val invalidJumpNodes = flow.nodes
                    .filter {
                        it.kind == NodeKind.JUMP ||
                            it.kind == NodeKind.FOLDER_REF ||
                            it.kind == NodeKind.SUB_TASK_REF
                    }
                    .filter { node ->
                        val targetFlowId = node.params["targetFlowId"]?.toString()?.trim().orEmpty()
                        val targetNodeId = node.params["targetNodeId"]?.toString()?.trim().orEmpty()
                        if (targetFlowId.isBlank() || targetNodeId.isBlank()) {
                            true
                        } else {
                            val targetFlow = state.bundle.findFlow(targetFlowId)
                            targetFlow?.findNode(targetNodeId) == null
                        }
                    }
                if (invalidJumpNodes.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    SectionTitle("缺失目标修复")
                    invalidJumpNodes.forEach { node ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = nodeSummaryText(node),
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f),
                                color = MaterialTheme.colorScheme.error,
                            )
                            OutlinedButton(onClick = { onRepairJumpTargetFromPreview(node.nodeId) }) {
                                Text("修复")
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            if (actionNodes.isEmpty()) {
                Text(
                    text = "当前没有动作，点击“添加动作”开始",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else if (flow != null) {
                ActionListWithJumpConnections(
                    flow = flow,
                    actionNodes = actionNodes,
                    onEditNode = onEditNode,
                    onOpenJumpTarget = { targetNodeId ->
                        onEditNode(targetNodeId)
                        statusText = "已定位跳转目标: $targetNodeId"
                        touchEditor()
                    },
                )
            }

            if (statusText.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    @Composable
    private fun FlowManagerScreen(
        breadcrumbs: List<BreadcrumbItem>,
        state: TaskGraphEditorState,
        scaffoldModifier: Modifier = Modifier,
        onBack: () -> Unit,
        onNavigateByBreadcrumb: (Int) -> Unit,
        onClose: () -> Unit,
        onSelectFlow: (String) -> Unit,
        onSetEntryNode: (String) -> Unit,
        onRenameFlow: (String) -> Unit,
        onAddFlow: (String) -> Unit,
        onDeleteFlow: () -> Unit,
    ) {
        val selectedFlow = state.selectedFlow
        var newFlowName by remember(state.bundle.flows.size) { mutableStateOf("") }
        var renameFlowName by remember(selectedFlow?.flowId, selectedFlow?.name) {
            mutableStateOf(selectedFlow?.name.orEmpty())
        }
        LaunchedEffect(selectedFlow?.flowId, selectedFlow?.name) {
            renameFlowName = selectedFlow?.name.orEmpty()
        }

        OverlayDialogScaffold(
            title = "流程管理",
            showBack = true,
            breadcrumbs = breadcrumbs,
            onBreadcrumbNavigate = onNavigateByBreadcrumb,
            modifier = scaffoldModifier,
            footerActions = listOf(
                OverlayAction("删除当前流程", style = OverlayButtonStyle.TONAL, onClick = onDeleteFlow),
                OverlayAction("完成", style = OverlayButtonStyle.SOLID, onClick = onBack),
            ),
            onBack = onBack,
            onClose = onClose,
        ) {
            SectionTitle("当前流程")
            Text(
                text = "selected=${state.selectedFlowId}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OptionButtons(
                options = state.bundle.flows.map { it.flowId },
                selected = state.selectedFlowId,
                onSelect = onSelectFlow,
            )

            if (selectedFlow != null) {
                SectionTitle("流程名称")
                OutlinedTextField(
                    value = renameFlowName,
                    onValueChange = { renameFlowName = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("flow.name") },
                    singleLine = true,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    DialogButton(
                        text = "保存名称",
                        style = OverlayButtonStyle.OUTLINE,
                        modifier = Modifier.weight(1f),
                        onClick = { onRenameFlow(renameFlowName) },
                    )
                }

                SectionTitle("入口节点")
                OptionButtons(
                    options = selectedFlow.nodes.map { it.nodeId },
                    selected = selectedFlow.entryNodeId,
                    onSelect = onSetEntryNode,
                )
            }

            SectionTitle("新增流程")
            OutlinedTextField(
                value = newFlowName,
                onValueChange = { newFlowName = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("new flow name") },
                singleLine = true,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DialogButton(
                    text = "新增流程",
                    style = OverlayButtonStyle.SOLID,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        onAddFlow(newFlowName)
                        newFlowName = ""
                    },
                )
            }

            if (statusText.isNotBlank()) {
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    @Composable
    private fun NodeEditScreen(
        breadcrumbs: List<BreadcrumbItem>,
        state: TaskGraphEditorState,
        scaffoldModifier: Modifier = Modifier,
        onBack: () -> Unit,
        onNavigateByBreadcrumb: (Int) -> Unit,
        onClose: () -> Unit,
        onDelete: () -> Unit,
        onSave: () -> Unit,
        onDone: () -> Unit,
        onUpdateNodeKind: (NodeKind) -> Unit,
        onUpdateActionType: (ActionType) -> Unit,
        onUpdateEnabled: (Boolean) -> Unit,
        onUpdateActive: (Boolean) -> Unit,
        onUpdateParam: (String, String) -> Unit,
        onRepairJumpTarget: () -> Unit,
        onFillDefaults: () -> Unit,
        onUpdateBranchTarget: (EdgeConditionType, String) -> Unit,
    ) {
        val node = state.selectedNode
        val flow = state.selectedFlow
        if (node == null || flow == null) {
            OverlayDialogScaffold(
                title = "编辑动作",
                showBack = true,
                breadcrumbs = breadcrumbs,
                onBreadcrumbNavigate = onNavigateByBreadcrumb,
                modifier = scaffoldModifier,
                onBack = onBack,
                onClose = onClose,
            ) {
                Text("节点不存在")
            }
            return
        }

        val isJumpLike = node.kind == NodeKind.JUMP || node.kind == NodeKind.FOLDER_REF || node.kind == NodeKind.SUB_TASK_REF
        val targetFlowId = node.params["targetFlowId"]?.toString()?.takeIf { it.isNotBlank() } ?: state.selectedFlowId
        val targetNodeId = node.params["targetNodeId"]?.toString().orEmpty()
        val targetFlow = state.bundle.findFlow(targetFlowId)
        var flowFilter by remember(node.nodeId) { mutableStateOf("") }
        var nodeFilter by remember(node.nodeId, targetFlowId) { mutableStateOf("") }

        OverlayDialogScaffold(
            title = "编辑动作: ${node.nodeId}",
            showBack = true,
            breadcrumbs = breadcrumbs,
            onBreadcrumbNavigate = onNavigateByBreadcrumb,
            modifier = scaffoldModifier,
            headerActions = listOf(
                OverlayAction("填充默认值", style = OverlayButtonStyle.TONAL, onClick = onFillDefaults),
            ),
            footerActions = listOf(
                OverlayAction("删除动作", style = OverlayButtonStyle.TONAL, onClick = onDelete),
                OverlayAction("保存", style = OverlayButtonStyle.OUTLINE, onClick = onSave),
                OverlayAction("完成", style = OverlayButtonStyle.SOLID, onClick = onDone),
            ),
            onBack = onBack,
            onClose = onClose,
        ) {
            SectionTitle("Kind")
            OptionButtons(
                options = NodeKind.entries
                    .filter { it != NodeKind.START && it != NodeKind.END }
                    .map { it.name },
                selected = node.kind.name,
                onSelect = { raw ->
                    NodeKind.entries.firstOrNull { it.name == raw }?.let(onUpdateNodeKind)
                },
            )

            ToggleLine(
                label = "enabled",
                checked = node.flags.enabled,
                onCheckedChange = onUpdateEnabled,
            )
            ToggleLine(
                label = "active",
                checked = node.flags.active,
                onCheckedChange = onUpdateActive,
            )

            if (node.kind == NodeKind.ACTION) {
                SectionTitle("ActionType")
                OptionButtons(
                    options = EditorActionTypeCatalog.availableActionTypes.map { it.raw },
                    selected = node.actionType?.raw,
                    onSelect = { raw ->
                        EditorActionTypeCatalog.availableActionTypes.firstOrNull { it.raw == raw }?.let(onUpdateActionType)
                    },
                )
            }

            if (isJumpLike) {
                SectionTitle("Jump Target Flow")
                OutlinedTextField(
                    value = flowFilter,
                    onValueChange = { flowFilter = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("筛选流程") },
                    singleLine = true,
                )
                val flowOptions = state.bundle.flows
                    .map { it.flowId }
                    .filter {
                        flowFilter.isBlank() || it.contains(flowFilter, ignoreCase = true)
                    }
                OptionButtons(
                    options = flowOptions,
                    selected = targetFlowId,
                    onSelect = { onUpdateParam("targetFlowId", it) },
                )
                SectionTitle("Jump Target Node")
                OutlinedTextField(
                    value = nodeFilter,
                    onValueChange = { nodeFilter = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("筛选节点") },
                    singleLine = true,
                )
                val nodeOptions = targetFlow?.nodes
                    ?.asSequence()
                    ?.filter { it.kind != NodeKind.START }
                    ?.map { it.nodeId }
                    ?.filter {
                        nodeFilter.isBlank() || it.contains(nodeFilter, ignoreCase = true)
                    }
                    ?.toList()
                    ?: emptyList()
                OptionButtons(
                    options = nodeOptions,
                    selected = targetNodeId,
                    onSelect = { onUpdateParam("targetNodeId", it) },
                )
                val jumpTargetValid = targetNodeId.isNotBlank() && targetFlow?.findNode(targetNodeId) != null
                if (!jumpTargetValid) {
                    Text(
                        text = "当前跳转目标无效：$targetFlowId/$targetNodeId",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    DialogButton(
                        text = "修复为当前流程入口",
                        style = OverlayButtonStyle.TONAL,
                        onClick = onRepairJumpTarget,
                    )
                }
            }

            if (node.kind == NodeKind.BRANCH) {
                val trueTargetNodeId = flow.edges
                    .firstOrNull { it.fromNodeId == node.nodeId && it.conditionType == EdgeConditionType.TRUE }
                    ?.toNodeId
                    .orEmpty()
                val falseTargetNodeId = flow.edges
                    .firstOrNull { it.fromNodeId == node.nodeId && it.conditionType == EdgeConditionType.FALSE }
                    ?.toNodeId
                    .orEmpty()

                SectionTitle("Branch TRUE")
                OptionButtons(
                    options = flow.nodes.map { it.nodeId },
                    selected = trueTargetNodeId,
                    onSelect = { onUpdateBranchTarget(EdgeConditionType.TRUE, it) },
                )
                SectionTitle("Branch FALSE")
                OptionButtons(
                    options = flow.nodes.map { it.nodeId },
                    selected = falseTargetNodeId,
                    onSelect = { onUpdateBranchTarget(EdgeConditionType.FALSE, it) },
                )
            }

            val schemaFields = EditorParamSchemaRegistry.fieldsFor(node)
            val schemaByKey = schemaFields.associateBy { it.key }
            val hiddenParamKeys = buildSet {
                if (isJumpLike) {
                    add("targetFlowId")
                    add("targetNodeId")
                }
            }
            val paramKeys = (schemaFields.map { it.key } + node.params.keys)
                .distinct()
                .filterNot { it in hiddenParamKeys }

            if (paramKeys.isNotEmpty()) {
                SectionTitle("Params")
                paramKeys.forEach { key ->
                    val definition = schemaByKey[key]
                    val options = definition?.options.orEmpty()
                    val value = node.params[key]?.toString().orEmpty()
                    val helperText = definition?.helperText
                    val errorText = EditorParamValidator.validate(definition, value)
                    if (options.isNotEmpty()) {
                        Text(
                            text = key,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        OptionButtons(
                            options = options,
                            selected = value,
                            onSelect = { onUpdateParam(key, it) },
                        )
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
                    } else {
                        ParamTextFieldRow(
                            keyLabel = key,
                            value = value,
                            definition = definition,
                            onApply = { onUpdateParam(key, it) },
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun OverlayDialogScaffold(
        title: String,
        showBack: Boolean,
        breadcrumbs: List<BreadcrumbItem> = emptyList(),
        onBreadcrumbNavigate: ((Int) -> Unit)? = null,
        modifier: Modifier = Modifier,
        headerActions: List<OverlayAction> = emptyList(),
        footerActions: List<OverlayAction> = emptyList(),
        onBack: () -> Unit,
        onClose: () -> Unit,
        content: @Composable () -> Unit,
    ) {
        val cardShape = RoundedCornerShape(14.dp)
        Card(
            modifier = modifier
                .fillMaxSize(),
            shape = cardShape,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (showBack) {
                        DialogButton(
                            text = "返回",
                            style = OverlayButtonStyle.OUTLINE,
                            onClick = onBack,
                        )
                    }
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                    )
                    DialogButton(
                        text = "关闭",
                        style = OverlayButtonStyle.OUTLINE,
                        onClick = onClose,
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                if (headerActions.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        headerActions.forEach { action ->
                            DialogButton(
                                text = action.text,
                                style = action.style,
                                onClick = action.onClick,
                            )
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                }
                if (breadcrumbs.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        breadcrumbs.forEachIndexed { index, crumb ->
                            val isLast = index == breadcrumbs.lastIndex
                            if (isLast || onBreadcrumbNavigate == null) {
                                Text(
                                    text = crumb.label,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isLast) {
                                        MaterialTheme.colorScheme.onSurface
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    fontWeight = if (isLast) FontWeight.Medium else FontWeight.Normal,
                                )
                            } else {
                                Text(
                                    text = crumb.label,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.clickable { onBreadcrumbNavigate(crumb.routeDepth) },
                                )
                            }
                            if (!isLast) {
                                Text(
                                    text = "/",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    content()
                }

                if (footerActions.isNotEmpty()) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        footerActions.forEach { action ->
                            DialogButton(
                                text = action.text,
                                style = action.style,
                                modifier = Modifier.weight(1f),
                                onClick = action.onClick,
                            )
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun DialogButton(
        text: String,
        style: OverlayButtonStyle,
        modifier: Modifier = Modifier,
        onClick: () -> Unit,
    ) {
        when (style) {
            OverlayButtonStyle.SOLID -> Button(onClick = onClick, modifier = modifier) { Text(text) }
            OverlayButtonStyle.OUTLINE -> OutlinedButton(onClick = onClick, modifier = modifier) { Text(text) }
            OverlayButtonStyle.TONAL -> OutlinedButton(onClick = onClick, modifier = modifier) { Text(text) }
        }
    }

    @Composable
    private fun SectionTitle(text: String) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
        )
    }

    @Composable
    private fun ToggleLine(
        label: String,
        checked: Boolean,
        onCheckedChange: (Boolean) -> Unit,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
            )
        }
    }

    @Composable
    private fun OptionButtons(
        options: List<String>,
        selected: String?,
        onSelect: (String) -> Unit,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            options.forEach { option ->
                if (option == selected) {
                    Button(onClick = { onSelect(option) }) { Text(option) }
                } else {
                    OutlinedButton(onClick = { onSelect(option) }) { Text(option) }
                }
            }
        }
    }

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    private fun ParamTextFieldRow(
        keyLabel: String,
        value: String,
        definition: ParamFieldDefinition?,
        onApply: (String) -> Unit,
    ) {
        var draft by remember(keyLabel, value) { mutableStateOf(value) }
        val helperText = definition?.helperText
        val validationError = EditorParamValidator.validate(definition, draft)
        val bringIntoViewRequester = remember { BringIntoViewRequester() }
        val coroutineScope = rememberCoroutineScope()
        val keyboardType = if (definition?.inputType == ParamFieldInputType.NUMBER) {
            KeyboardType.Number
        } else {
            KeyboardType.Text
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier
                    .weight(1f)
                    .bringIntoViewRequester(bringIntoViewRequester)
                    .onFocusChanged { focusState ->
                        if (focusState.isFocused) {
                            coroutineScope.launch {
                                delay(120)
                                runCatching { bringIntoViewRequester.bringIntoView() }
                            }
                        }
                    },
                label = { Text(keyLabel) },
                placeholder = {
                    definition?.defaultValue?.let { default ->
                        Text("默认: $default")
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                isError = !validationError.isNullOrBlank(),
                supportingText = {
                    when {
                        !validationError.isNullOrBlank() -> Text(validationError)
                        !helperText.isNullOrBlank() -> Text(helperText)
                    }
                },
                singleLine = true,
            )
            OutlinedButton(
                enabled = validationError == null,
                onClick = { onApply(draft) },
            ) {
                Text("设定")
            }
        }
    }

    @Composable
    private fun ActionListWithJumpConnections(
        flow: TaskFlow,
        actionNodes: List<FlowNode>,
        onEditNode: (String) -> Unit,
        onOpenJumpTarget: (String) -> Unit,
    ) {
        val rowCenters = remember(flow.flowId, actionNodes.map { it.nodeId }) {
            mutableStateMapOf<String, Float>()
        }
        val jumpConnections = remember(flow, actionNodes) {
            buildJumpConnections(flow = flow, actionNodes = actionNodes)
        }

        Box(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                actionNodes.forEachIndexed { index, node ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .onGloballyPositioned { coordinates ->
                                rowCenters[node.nodeId] = coordinates.positionInParent().y + coordinates.size.height / 2f
                            }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "${index + 1}. ${nodeSummaryText(node)}",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedButton(onClick = { onEditNode(node.nodeId) }) {
                            Text("编辑")
                        }
                    }
                }
            }
            if (jumpConnections.isNotEmpty()) {
                JumpConnectionCanvas(
                    rowCenters = rowCenters,
                    connections = jumpConnections,
                    onConnectionTap = onOpenJumpTarget,
                    modifier = Modifier.matchParentSize(),
                )
            }
        }
    }

    @Composable
    private fun JumpConnectionCanvas(
        rowCenters: SnapshotStateMap<String, Float>,
        connections: List<Pair<String, String>>,
        onConnectionTap: (String) -> Unit,
        modifier: Modifier = Modifier,
    ) {
        val paintColor = MaterialTheme.colorScheme.primary
        Canvas(
            modifier = modifier.pointerInput(rowCenters.toMap(), connections) {
                detectTapGestures { tapOffset ->
                    val startX = size.width - 82.dp.toPx()
                    val midX = size.width - 30.dp.toPx()
                    val endX = size.width - 20.dp.toPx()
                    val thresholdPx = 14.dp.toPx()
                    val thresholdSq = thresholdPx * thresholdPx
                    var bestMatchTarget: String? = null
                    var bestMatchDistSq = Float.MAX_VALUE
                    connections.forEach { (fromId, toId) ->
                        val fromY = rowCenters[fromId] ?: return@forEach
                        val toY = rowCenters[toId] ?: return@forEach
                        val d1 = distanceSquaredToSegment(
                            point = tapOffset,
                            a = Offset(startX, fromY),
                            b = Offset(midX, fromY),
                        )
                        val d2 = distanceSquaredToSegment(
                            point = tapOffset,
                            a = Offset(midX, fromY),
                            b = Offset(midX, toY),
                        )
                        val d3 = distanceSquaredToSegment(
                            point = tapOffset,
                            a = Offset(midX, toY),
                            b = Offset(endX, toY),
                        )
                        val nearest = minOf(d1, d2, d3)
                        if (nearest < bestMatchDistSq) {
                            bestMatchDistSq = nearest
                            bestMatchTarget = toId
                        }
                    }
                    if (bestMatchDistSq <= thresholdSq) {
                        bestMatchTarget?.let(onConnectionTap)
                    }
                }
            },
        ) {
            val startX = size.width - 82.dp.toPx()
            val midX = size.width - 30.dp.toPx()
            val endX = size.width - 20.dp.toPx()
            connections.forEach { (fromId, toId) ->
                val fromY = rowCenters[fromId] ?: return@forEach
                val toY = rowCenters[toId] ?: return@forEach
                drawLine(
                    color = paintColor,
                    start = Offset(startX, fromY),
                    end = Offset(midX, fromY),
                    strokeWidth = 2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()), 0f),
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = paintColor,
                    start = Offset(midX, fromY),
                    end = Offset(midX, toY),
                    strokeWidth = 2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()), 0f),
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = paintColor,
                    start = Offset(midX, toY),
                    end = Offset(endX, toY),
                    strokeWidth = 2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()), 0f),
                    cap = StrokeCap.Round,
                )
                drawCircle(color = paintColor, radius = 2.dp.toPx(), center = Offset(startX, fromY))
                drawCircle(color = paintColor, radius = 2.dp.toPx(), center = Offset(endX, toY))
            }
        }
    }

    private fun distanceSquaredToSegment(
        point: Offset,
        a: Offset,
        b: Offset,
    ): Float {
        val abX = b.x - a.x
        val abY = b.y - a.y
        val apX = point.x - a.x
        val apY = point.y - a.y
        val abLenSq = abX * abX + abY * abY
        if (abLenSq <= 0f) {
            val dx = point.x - a.x
            val dy = point.y - a.y
            return dx * dx + dy * dy
        }
        val t = ((apX * abX + apY * abY) / abLenSq).coerceIn(0f, 1f)
        val projectionX = a.x + t * abX
        val projectionY = a.y + t * abY
        val dx = point.x - projectionX
        val dy = point.y - projectionY
        return dx * dx + dy * dy
    }

    @Composable
    private fun FlowPreviewGraph(
        flow: TaskFlow,
        selectedNodeId: String?,
        onSelectNode: (String) -> Unit,
        onOpenEdgeTarget: (String) -> Unit,
    ) {
        val defaultPoints = remember(flow) { calculateGraphPoints(flow) }
        val pointOverrides = remember(flow.flowId) { mutableStateMapOf<String, GraphPoint>() }
        var draggingNodeId by remember(flow.flowId) { mutableStateOf<String?>(null) }
        var didDragInCurrentGesture by remember(flow.flowId) { mutableStateOf(false) }
        var suppressNextTap by remember(flow.flowId) { mutableStateOf(false) }
        LaunchedEffect(flow.flowId, flow.nodes.map { it.nodeId }) {
            val validNodeIds = flow.nodes.map { it.nodeId }.toSet()
            pointOverrides.keys.toList().forEach { key ->
                if (key !in validNodeIds) {
                    pointOverrides.remove(key)
                }
            }
            flow.nodes.forEach { node ->
                if (!pointOverrides.containsKey(node.nodeId)) {
                    defaultPoints[node.nodeId]?.let { pointOverrides[node.nodeId] = it }
                }
            }
        }
        val flowNodeIds = flow.nodes.map { it.nodeId }
        val points = flow.nodes.associate { node ->
            val point = pointOverrides[node.nodeId]
                ?: defaultPoints[node.nodeId]
                ?: GraphPoint(0.5f, 0.5f)
            node.nodeId to point
        }
        val latestPoints = rememberUpdatedState(points)
        val edgeColor = MaterialTheme.colorScheme.onSurfaceVariant
        val jumpColor = MaterialTheme.colorScheme.primary
        val nodeFillColor = MaterialTheme.colorScheme.surface
        val nodeStrokeColor = MaterialTheme.colorScheme.outline
        val containerColor = MaterialTheme.colorScheme.surfaceVariant
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .background(containerColor, RoundedCornerShape(10.dp)),
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp)
                    .pointerInput(flow.flowId, flowNodeIds) {
                        detectTapGestures { tapOffset ->
                            if (suppressNextTap) {
                                suppressNextTap = false
                                return@detectTapGestures
                            }
                            val pointsSnapshot = latestPoints.value
                            val width = size.width.toFloat()
                            val height = size.height.toFloat()
                            val clickedNodeId = findClosestGraphNodeId(
                                points = pointsSnapshot,
                                tapOffset = tapOffset,
                                width = width,
                                height = height,
                                thresholdPx = 22.dp.toPx(),
                            )
                            if (clickedNodeId != null) {
                                onSelectNode(clickedNodeId)
                                return@detectTapGestures
                            }
                            val targetNodeId = findClosestPreviewEdgeTarget(
                                flow = flow,
                                points = pointsSnapshot,
                                tapOffset = tapOffset,
                                width = width,
                                height = height,
                                thresholdPx = 16.dp.toPx(),
                            )
                            if (targetNodeId != null) {
                                onOpenEdgeTarget(targetNodeId)
                            }
                        }
                    }
                    .pointerInput(flow.flowId, flowNodeIds) {
                        detectDragGestures(
                            onDragStart = { dragStart ->
                                didDragInCurrentGesture = false
                                val pointsSnapshot = latestPoints.value
                                val width = size.width.toFloat()
                                val height = size.height.toFloat()
                                draggingNodeId = findClosestGraphNodeId(
                                    points = pointsSnapshot,
                                    tapOffset = dragStart,
                                    width = width,
                                    height = height,
                                    thresholdPx = 26.dp.toPx(),
                                )
                            },
                            onDragCancel = {
                                draggingNodeId = null
                                didDragInCurrentGesture = false
                            },
                            onDragEnd = {
                                if (didDragInCurrentGesture) {
                                    suppressNextTap = true
                                }
                                draggingNodeId = null
                                didDragInCurrentGesture = false
                            },
                        ) { change, dragAmount ->
                            val nodeId = draggingNodeId ?: return@detectDragGestures
                            didDragInCurrentGesture = true
                            val width = size.width.toFloat().takeIf { it > 0f } ?: return@detectDragGestures
                            val height = size.height.toFloat().takeIf { it > 0f } ?: return@detectDragGestures
                            val current = pointOverrides[nodeId]
                                ?: defaultPoints[nodeId]
                                ?: return@detectDragGestures
                            pointOverrides[nodeId] = GraphPoint(
                                xRatio = (current.xRatio + (dragAmount.x / width)).coerceIn(0.08f, 0.92f),
                                yRatio = (current.yRatio + (dragAmount.y / height)).coerceIn(0.08f, 0.92f),
                            )
                            change.consume()
                        }
                    },
            ) {
                val toOffset: (GraphPoint) -> Offset = { point ->
                    Offset(point.xRatio * size.width, point.yRatio * size.height)
                }
                flow.edges.forEach { edge ->
                    val from = points[edge.fromNodeId] ?: return@forEach
                    val to = points[edge.toNodeId] ?: return@forEach
                    drawLine(
                        color = edgeColor,
                        start = toOffset(from),
                        end = toOffset(to),
                        strokeWidth = 2.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                }
                flow.nodes
                    .filter {
                        it.kind == NodeKind.JUMP ||
                            it.kind == NodeKind.FOLDER_REF ||
                            it.kind == NodeKind.SUB_TASK_REF
                    }
                    .forEach { node ->
                        val from = points[node.nodeId] ?: return@forEach
                        val targetFlowId = node.params["targetFlowId"]?.toString()?.takeIf { it.isNotBlank() } ?: flow.flowId
                        val targetNodeId = node.params["targetNodeId"]?.toString()?.takeIf { it.isNotBlank() } ?: return@forEach
                        if (targetFlowId != flow.flowId) {
                            return@forEach
                        }
                        val to = points[targetNodeId] ?: return@forEach
                        drawLine(
                            color = jumpColor,
                            start = toOffset(from),
                            end = toOffset(to),
                            strokeWidth = 2.dp.toPx(),
                            cap = StrokeCap.Round,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()), 0f),
                        )
                    }
                flow.nodes.forEach { node ->
                    val point = points[node.nodeId] ?: return@forEach
                    val center = toOffset(point)
                    val isSelected = node.nodeId == selectedNodeId
                    drawCircle(
                        color = if (isSelected) jumpColor else nodeFillColor,
                        radius = if (isSelected) 8.dp.toPx() else 7.dp.toPx(),
                        center = center,
                    )
                    drawCircle(
                        color = if (isSelected) jumpColor else nodeStrokeColor,
                        radius = if (isSelected) 8.dp.toPx() else 7.dp.toPx(),
                        center = center,
                        style = Stroke(width = if (isSelected) 2.dp.toPx() else 1.dp.toPx()),
                    )
                }
            }
        }
    }

    private fun findClosestGraphNodeId(
        points: Map<String, GraphPoint>,
        tapOffset: Offset,
        width: Float,
        height: Float,
        thresholdPx: Float,
    ): String? {
        if (points.isEmpty()) {
            return null
        }
        var closestNodeId: String? = null
        var closestDistSq = Float.MAX_VALUE
        points.forEach { (nodeId, point) ->
            val centerX = point.xRatio * width
            val centerY = point.yRatio * height
            val dx = tapOffset.x - centerX
            val dy = tapOffset.y - centerY
            val distSq = dx * dx + dy * dy
            if (distSq < closestDistSq) {
                closestDistSq = distSq
                closestNodeId = nodeId
            }
        }
        return if (closestDistSq <= thresholdPx * thresholdPx) closestNodeId else null
    }

    private fun findClosestPreviewEdgeTarget(
        flow: TaskFlow,
        points: Map<String, GraphPoint>,
        tapOffset: Offset,
        width: Float,
        height: Float,
        thresholdPx: Float,
    ): String? {
        var hitTarget: String? = null
        var bestDistSq = thresholdPx * thresholdPx
        val toOffset: (GraphPoint) -> Offset = { point ->
            Offset(point.xRatio * width, point.yRatio * height)
        }
        flow.edges.forEach { edge ->
            val from = points[edge.fromNodeId] ?: return@forEach
            val to = points[edge.toNodeId] ?: return@forEach
            val distSq = distanceSquaredToSegment(
                point = tapOffset,
                a = toOffset(from),
                b = toOffset(to),
            )
            if (distSq < bestDistSq) {
                bestDistSq = distSq
                hitTarget = edge.toNodeId
            }
        }
        flow.nodes
            .filter {
                it.kind == NodeKind.JUMP ||
                    it.kind == NodeKind.FOLDER_REF ||
                    it.kind == NodeKind.SUB_TASK_REF
            }
            .forEach { node ->
                val targetFlowId = node.params["targetFlowId"]?.toString()?.takeIf { it.isNotBlank() } ?: flow.flowId
                val targetNodeId = node.params["targetNodeId"]?.toString()?.takeIf { it.isNotBlank() } ?: return@forEach
                if (targetFlowId != flow.flowId) {
                    return@forEach
                }
                val from = points[node.nodeId] ?: return@forEach
                val to = points[targetNodeId] ?: return@forEach
                val distSq = distanceSquaredToSegment(
                    point = tapOffset,
                    a = toOffset(from),
                    b = toOffset(to),
                )
                if (distSq < bestDistSq) {
                    bestDistSq = distSq
                    hitTarget = targetNodeId
                }
            }
        return hitTarget
    }

    private fun buildJumpConnections(
        flow: TaskFlow,
        actionNodes: List<FlowNode>,
    ): List<Pair<String, String>> {
        val nodeIds = actionNodes.map { it.nodeId }.toSet()
        return flow.nodes
            .filter {
                it.kind == NodeKind.JUMP || it.kind == NodeKind.FOLDER_REF || it.kind == NodeKind.SUB_TASK_REF
            }
            .mapNotNull { node ->
                val targetFlowId = node.params["targetFlowId"]?.toString()?.takeIf { it.isNotBlank() } ?: flow.flowId
                val targetNodeId = node.params["targetNodeId"]?.toString()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                if (targetFlowId != flow.flowId) {
                    return@mapNotNull null
                }
                if (node.nodeId !in nodeIds || targetNodeId !in nodeIds) {
                    return@mapNotNull null
                }
                node.nodeId to targetNodeId
            }
    }

    private data class GraphPoint(
        val xRatio: Float,
        val yRatio: Float,
    )

    private fun calculateGraphPoints(flow: TaskFlow): Map<String, GraphPoint> {
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
        val denominator = (flow.nodes.size - 1).coerceAtLeast(1)
        return flow.nodes.mapIndexed { index, node ->
            val x = when {
                node.nodeId in trueTargets && node.nodeId !in falseTargets -> 0.28f
                node.nodeId in falseTargets && node.nodeId !in trueTargets -> 0.72f
                else -> 0.5f
            }
            val y = 0.1f + (0.8f * (index.toFloat() / denominator.toFloat()))
            node.nodeId to GraphPoint(
                xRatio = x.coerceIn(0.12f, 0.88f),
                yRatio = y.coerceIn(0.08f, 0.92f),
            )
        }.toMap()
    }

    private fun nodeSummaryText(node: FlowNode): String {
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

    private fun dp(value: Int): Int {
        return (value * context.resources.displayMetrics.density).roundToInt()
    }
}

private class OverlayComposeOwner : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
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
