package com.ksxkq.cmm_clicker.accessibility

import com.ksxkq.cmm_clicker.feature.debug.RuntimeRunReportSummary

internal const val MODAL_ACTION_CANCEL = "cancel"
internal const val MODAL_ACTION_CONFIRM = "confirm"

internal sealed interface SettingsModal {
    data class ConfirmStartTask(
        val taskId: String,
        val taskName: String,
    ) : SettingsModal

    data class ConfirmDeleteTask(
        val taskId: String,
        val taskName: String,
    ) : SettingsModal

    data class ConfirmDeleteActionNode(
        val flowId: String,
        val nodeId: String,
    ) : SettingsModal

    data class ConfirmDeleteRuntimeReport(
        val reportId: String,
    ) : SettingsModal

    data class Success(
        val title: String,
        val message: String,
    ) : SettingsModal

    data class Failure(
        val title: String,
        val message: String,
    ) : SettingsModal
}

internal sealed interface SettingsModalAction {
    data object Dismiss : SettingsModalAction

    data class StartTask(
        val taskId: String,
    ) : SettingsModalAction

    data class DeleteTask(
        val taskId: String,
    ) : SettingsModalAction

    data class DeleteActionNode(
        val flowId: String,
        val nodeId: String,
    ) : SettingsModalAction

    data class DeleteRuntimeReport(
        val reportId: String,
    ) : SettingsModalAction
}

internal fun resolveSettingsModalAction(
    modal: SettingsModal,
    actionKey: String,
): SettingsModalAction? {
    return when (modal) {
        is SettingsModal.ConfirmStartTask -> when (actionKey) {
            MODAL_ACTION_CANCEL -> SettingsModalAction.Dismiss
            MODAL_ACTION_CONFIRM -> SettingsModalAction.StartTask(taskId = modal.taskId)
            else -> null
        }

        is SettingsModal.ConfirmDeleteTask -> when (actionKey) {
            MODAL_ACTION_CANCEL -> SettingsModalAction.Dismiss
            MODAL_ACTION_CONFIRM -> SettingsModalAction.DeleteTask(taskId = modal.taskId)
            else -> null
        }

        is SettingsModal.ConfirmDeleteActionNode -> when (actionKey) {
            MODAL_ACTION_CANCEL -> SettingsModalAction.Dismiss
            MODAL_ACTION_CONFIRM -> SettingsModalAction.DeleteActionNode(
                flowId = modal.flowId,
                nodeId = modal.nodeId,
            )
            else -> null
        }

        is SettingsModal.ConfirmDeleteRuntimeReport -> when (actionKey) {
            MODAL_ACTION_CANCEL -> SettingsModalAction.Dismiss
            MODAL_ACTION_CONFIRM -> SettingsModalAction.DeleteRuntimeReport(reportId = modal.reportId)
            else -> null
        }

        is SettingsModal.Success,
        is SettingsModal.Failure,
        -> SettingsModalAction.Dismiss
    }
}

internal fun buildSettingsModalModel(
    modal: SettingsModal?,
    runtimeReportHistory: List<RuntimeRunReportSummary>,
): TaskControlModalModel? {
    return when (modal) {
        null -> null
        is SettingsModal.ConfirmStartTask -> {
            TaskControlModalModel(
                title = "确认开始任务",
                message = "是否开始执行任务：${modal.taskName.ifBlank { "未命名任务" }}",
                tone = TaskControlModalTone.DEFAULT,
                dismissOnBackdropTap = false,
                actions = listOf(
                    TaskControlModalAction(
                        key = MODAL_ACTION_CANCEL,
                        text = "取消",
                    ),
                    TaskControlModalAction(
                        key = MODAL_ACTION_CONFIRM,
                        text = "开始",
                    ),
                ),
            )
        }

        is SettingsModal.ConfirmDeleteRuntimeReport -> {
            val pendingItem = runtimeReportHistory.firstOrNull { it.reportId == modal.reportId }
                ?: return null
            TaskControlModalModel(
                title = "删除历史记录",
                message = "确认删除这条历史记录吗？\n${pendingItem.reportId}",
                tone = TaskControlModalTone.WARNING,
                dismissOnBackdropTap = true,
                actions = listOf(
                    TaskControlModalAction(
                        key = MODAL_ACTION_CANCEL,
                        text = "取消",
                    ),
                    TaskControlModalAction(
                        key = MODAL_ACTION_CONFIRM,
                        text = "确认删除",
                    ),
                ),
            )
        }

        is SettingsModal.ConfirmDeleteTask -> {
            TaskControlModalModel(
                title = "删除任务",
                message = "确认删除任务：${modal.taskName.ifBlank { "未命名任务" }}",
                tone = TaskControlModalTone.WARNING,
                dismissOnBackdropTap = true,
                actions = listOf(
                    TaskControlModalAction(
                        key = MODAL_ACTION_CANCEL,
                        text = "取消",
                    ),
                    TaskControlModalAction(
                        key = MODAL_ACTION_CONFIRM,
                        text = "确认删除",
                    ),
                ),
            )
        }

        is SettingsModal.ConfirmDeleteActionNode -> {
            TaskControlModalModel(
                title = "删除动作",
                message = "确认删除动作 ${modal.nodeId} 吗？",
                tone = TaskControlModalTone.WARNING,
                dismissOnBackdropTap = true,
                actions = listOf(
                    TaskControlModalAction(
                        key = MODAL_ACTION_CANCEL,
                        text = "取消",
                    ),
                    TaskControlModalAction(
                        key = MODAL_ACTION_CONFIRM,
                        text = "确认删除",
                    ),
                ),
            )
        }

        is SettingsModal.Success -> {
            TaskControlModalModel(
                title = modal.title,
                message = modal.message,
                tone = TaskControlModalTone.SUCCESS,
                dismissOnBackdropTap = true,
                actions = listOf(
                    TaskControlModalAction(
                        key = MODAL_ACTION_CONFIRM,
                        text = "知道了",
                    ),
                ),
            )
        }

        is SettingsModal.Failure -> {
            TaskControlModalModel(
                title = modal.title,
                message = modal.message,
                tone = TaskControlModalTone.FAILURE,
                dismissOnBackdropTap = true,
                actions = listOf(
                    TaskControlModalAction(
                        key = MODAL_ACTION_CONFIRM,
                        text = "知道了",
                    ),
                ),
            )
        }
    }
}
