package com.ksxkq.cmm_clicker.accessibility

import com.ksxkq.cmm_clicker.core.runtime.RuntimeExecutionResult
import com.ksxkq.cmm_clicker.core.runtime.RuntimeExecutionStatus
import com.ksxkq.cmm_clicker.core.runtime.RuntimeTraceEvent
import com.ksxkq.cmm_clicker.core.runtime.RuntimeTracePhase

internal data class RunningPanelState(
    val taskName: String = "",
    val stepCount: Int = 0,
    val currentFlowId: String = "",
    val currentNodeId: String = "",
    val lastMessage: String = "",
    val lastErrorCode: String = "",
    val paused: Boolean = false,
) {
    fun startWithTask(taskName: String): RunningPanelState {
        return copy(
            taskName = taskName.ifBlank { "未命名任务" },
            stepCount = 0,
            currentFlowId = "-",
            currentNodeId = "-",
            lastMessage = "等待执行...",
            lastErrorCode = "",
            paused = false,
        )
    }

    fun reset(): RunningPanelState = RunningPanelState()

    fun applyTrace(event: RuntimeTraceEvent): RunningPanelState {
        val nextMessage = when (event.phase) {
            RuntimeTracePhase.NODE_START -> "执行中"
            RuntimeTracePhase.NODE_END -> event.message?.takeIf { it.isNotBlank() } ?: "动作已完成"
            RuntimeTracePhase.NODE_ERROR -> event.message?.takeIf { it.isNotBlank() } ?: "执行失败"
        }
        val nextErrorCode = if (event.phase == RuntimeTracePhase.NODE_ERROR) {
            event.details["errorCode"]
                ?.takeIf { it.isNotBlank() && it != "-" }
                ?: lastErrorCode
        } else {
            lastErrorCode
        }
        return copy(
            stepCount = maxOf(stepCount, event.step + 1),
            currentFlowId = event.flowId,
            currentNodeId = event.nodeId,
            lastMessage = nextMessage,
            lastErrorCode = nextErrorCode,
        )
    }

    fun applyResult(result: RuntimeExecutionResult): RunningPanelState {
        val traceErrorCode = result.traceEvents
            .lastOrNull { it.phase == RuntimeTracePhase.NODE_ERROR }
            ?.details
            ?.get("errorCode")
            ?.takeIf { it.isNotBlank() && it != "-" }
        val nextErrorCode = traceErrorCode ?: lastErrorCode
        val nextMessage = when (result.status) {
            RuntimeExecutionStatus.COMPLETED -> "执行完成"
            RuntimeExecutionStatus.STOPPED -> "已停止"
            RuntimeExecutionStatus.FAILED -> result.message?.takeIf { it.isNotBlank() } ?: "执行失败"
        }
        return copy(
            stepCount = result.stepCount.coerceAtLeast(stepCount),
            lastMessage = nextMessage,
            lastErrorCode = nextErrorCode,
            paused = false,
        )
    }

    fun togglePause(): RunningPanelState {
        val pausedNow = !paused
        return copy(
            paused = pausedNow,
            lastMessage = if (pausedNow) "任务已暂停" else "任务继续执行",
        )
    }

    fun withStoppingMessage(): RunningPanelState = copy(lastMessage = "正在停止...")

    fun withUserStopped(): RunningPanelState = copy(
        paused = false,
        lastMessage = "用户已停止",
        lastErrorCode = "",
    )

    fun withRuntimeError(message: String = "运行异常"): RunningPanelState = copy(
        paused = false,
        lastMessage = message,
        lastErrorCode = "",
    )
}
