package com.ksxkq.cmm_clicker.accessibility

import com.ksxkq.cmm_clicker.core.runtime.RuntimeExecutionResult
import com.ksxkq.cmm_clicker.core.runtime.RuntimeExecutionStatus

internal const val RUN_STATUS_NO_TASK = "没有可执行任务"
internal const val RUN_STATUS_TASK_NOT_FOUND = "任务不存在，无法执行"
internal const val RUN_STATUS_RUNNING = "运行中..."

internal data class RunTerminationUiModel(
    val panelMessage: String,
    val sessionStatus: RuntimeExecutionStatus,
    val sessionMessage: String,
    val sessionErrorCode: String,
    val statusText: String,
)

internal fun buildRunSummaryText(
    result: RuntimeExecutionResult,
): String {
    return buildString {
        append("模式=REAL ")
        append("状态=${result.status} ")
        append("step=${result.stepCount} ")
        append("msg=${result.message ?: "-"}")
    }
}

internal fun buildRunStoppedUiModel(): RunTerminationUiModel {
    return RunTerminationUiModel(
        panelMessage = "用户已停止",
        sessionStatus = RuntimeExecutionStatus.STOPPED,
        sessionMessage = "user_requested_stop",
        sessionErrorCode = "",
        statusText = "任务已停止",
    )
}

internal fun buildRunFailedUiModel(errorMessage: String?): RunTerminationUiModel {
    val resolved = errorMessage ?: "unknown"
    return RunTerminationUiModel(
        panelMessage = "运行异常",
        sessionStatus = RuntimeExecutionStatus.FAILED,
        sessionMessage = resolved,
        sessionErrorCode = "",
        statusText = "运行失败: $resolved",
    )
}

