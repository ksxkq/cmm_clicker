package com.ksxkq.cmm_clicker.core.actions.builtin

import com.ksxkq.cmm_clicker.accessibility.AccessibilityGestureExecutor
import com.ksxkq.cmm_clicker.core.actions.ActionContext
import com.ksxkq.cmm_clicker.core.actions.ActionExecutionStatus
import com.ksxkq.cmm_clicker.core.actions.ActionPlugin
import com.ksxkq.cmm_clicker.core.actions.ActionPluginRegistry
import com.ksxkq.cmm_clicker.core.actions.ActionResult
import com.ksxkq.cmm_clicker.core.model.ActionType
import kotlinx.coroutines.delay

object BuiltinPluginFactory {
    fun createDefaultRegistry(): ActionPluginRegistry {
        val fallback = UnsupportedActionPlugin
        return ActionPluginRegistry(fallbackPlugin = fallback)
            .register(BasicGesturePlugin)
            .register(CloseCurrentUiNoopPlugin)
    }
}

object BasicGesturePlugin : ActionPlugin {
    override val pluginId: String = "builtin.basic_gesture"
    override val supportedTypes: Set<ActionType> = setOf(
        ActionType.CLICK,
        ActionType.SWIPE,
        ActionType.RECORD,
        ActionType.DUP_CLICK,
    )

    override suspend fun execute(
        context: ActionContext,
        actionType: ActionType,
        params: Map<String, Any?>,
    ): ActionResult {
        if (context.dryRun) {
            return ActionResult(
                status = ActionExecutionStatus.SUCCESS,
                message = "dryrun:${actionType.raw}",
            )
        }
        if (!AccessibilityGestureExecutor.isAvailable()) {
            return ActionResult(
                status = ActionExecutionStatus.FAILED,
                errorCode = "accessibility_service_unavailable",
                message = "Accessibility service is not connected",
            )
        }

        val success = runCatching {
            when (actionType) {
                ActionType.CLICK -> {
                    val x = params.readDouble("x", 0.5)
                    val y = params.readDouble("y", 0.5)
                    val duration = params.readLong("durationMs", 60L)
                    AccessibilityGestureExecutor.performClick(x, y, duration)
                }

                ActionType.SWIPE -> {
                    val startX = params.readDouble("startX", 0.5)
                    val startY = params.readDouble("startY", 0.8)
                    val endX = params.readDouble("endX", 0.5)
                    val endY = params.readDouble("endY", 0.2)
                    val duration = params.readLong("durationMs", 320L)
                    AccessibilityGestureExecutor.performSwipe(
                        startXRatio = startX,
                        startYRatio = startY,
                        endXRatio = endX,
                        endYRatio = endY,
                        durationMs = duration,
                    )
                }

                ActionType.RECORD -> {
                    val points = params.readPoints("points")
                        ?: listOf(0.5 to 0.8, 0.5 to 0.2)
                    val duration = params.readLong("durationMs", 400L)
                    AccessibilityGestureExecutor.performRecordPath(
                        points = points,
                        durationMs = duration,
                    )
                }

                ActionType.DUP_CLICK -> {
                    val x = params.readDouble("x", 0.5)
                    val y = params.readDouble("y", 0.5)
                    val duration = params.readLong("durationMs", 50L)
                    val count = params.readInt("count", 2).coerceAtLeast(1)
                    val interval = params.readLong("intervalMs", 80L).coerceAtLeast(0L)
                    var allSuccess = true
                    repeat(count) { index ->
                        val clickSuccess = AccessibilityGestureExecutor.performClick(x, y, duration)
                        if (!clickSuccess) {
                            allSuccess = false
                            return@repeat
                        }
                        if (index != count - 1 && interval > 0) {
                            delay(interval)
                        }
                    }
                    allSuccess
                }

                else -> false
            }
        }.getOrElse { error ->
            return ActionResult(
                status = ActionExecutionStatus.ERROR,
                errorCode = "gesture_exception",
                message = error.message ?: "gesture_exception",
            )
        }

        return ActionResult(
            status = if (success) ActionExecutionStatus.SUCCESS else ActionExecutionStatus.FAILED,
            message = if (success) "executed:${actionType.raw}" else "failed:${actionType.raw}",
        )
    }
}

object CloseCurrentUiNoopPlugin : ActionPlugin {
    override val pluginId: String = "builtin.close_current_ui_noop"
    override val supportedTypes: Set<ActionType> = setOf(ActionType.CLOSE_CURRENT_UI)

    override suspend fun execute(
        context: ActionContext,
        actionType: ActionType,
        params: Map<String, Any?>,
    ): ActionResult {
        return ActionResult(
            status = ActionExecutionStatus.SUCCESS,
            message = "noop:${actionType.raw}",
            payload = mapOf("noop" to true),
        )
    }
}

object UnsupportedActionPlugin : ActionPlugin {
    override val pluginId: String = "builtin.unsupported"
    override val supportedTypes: Set<ActionType> = emptySet()

    override suspend fun execute(
        context: ActionContext,
        actionType: ActionType,
        params: Map<String, Any?>,
    ): ActionResult {
        return ActionResult(
            status = ActionExecutionStatus.ERROR,
            errorCode = "unsupported_action",
            message = "Action plugin is not implemented: ${actionType.raw}",
        )
    }
}

private fun Map<String, Any?>.readDouble(key: String, default: Double): Double {
    val value = this[key] ?: return default
    return when (value) {
        is Number -> value.toDouble()
        is String -> value.toDoubleOrNull() ?: default
        else -> default
    }
}

private fun Map<String, Any?>.readLong(key: String, default: Long): Long {
    val value = this[key] ?: return default
    return when (value) {
        is Number -> value.toLong()
        is String -> value.toLongOrNull() ?: default
        else -> default
    }
}

private fun Map<String, Any?>.readInt(key: String, default: Int): Int {
    val value = this[key] ?: return default
    return when (value) {
        is Number -> value.toInt()
        is String -> value.toIntOrNull() ?: default
        else -> default
    }
}

private fun Map<String, Any?>.readPoints(key: String): List<Pair<Double, Double>>? {
    val value = this[key] as? List<*> ?: return null
    val result = mutableListOf<Pair<Double, Double>>()
    value.forEach { item ->
        val map = item as? Map<*, *> ?: return@forEach
        val x = when (val xValue = map["x"]) {
            is Number -> xValue.toDouble()
            is String -> xValue.toDoubleOrNull()
            else -> null
        }
        val y = when (val yValue = map["y"]) {
            is Number -> yValue.toDouble()
            is String -> yValue.toDoubleOrNull()
            else -> null
        }
        if (x != null && y != null) {
            result += x to y
        }
    }
    return result.takeIf { it.isNotEmpty() }
}
