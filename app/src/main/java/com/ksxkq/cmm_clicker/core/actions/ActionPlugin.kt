package com.ksxkq.cmm_clicker.core.actions

import com.ksxkq.cmm_clicker.core.model.ActionType
import com.ksxkq.cmm_clicker.core.model.NodePointer

interface ActionPlugin {
    val pluginId: String
    val supportedTypes: Set<ActionType>

    fun validate(params: Map<String, Any?>): List<ActionValidationIssue> = emptyList()

    suspend fun execute(
        context: ActionContext,
        actionType: ActionType,
        params: Map<String, Any?>,
    ): ActionResult
}

data class ActionContext(
    val traceId: String,
    val flowId: String,
    val nodeId: String,
    val dryRun: Boolean,
    val variables: MutableMap<String, Any?>,
)

data class ActionResult(
    val status: ActionExecutionStatus = ActionExecutionStatus.SUCCESS,
    val message: String? = null,
    val payload: Map<String, Any?> = emptyMap(),
    val next: NodePointer? = null,
    val errorCode: String? = null,
)

enum class ActionExecutionStatus {
    SUCCESS,
    FAILED,
    ERROR,
    STOPPED,
}

data class ActionValidationIssue(
    val level: ActionValidationLevel,
    val code: String,
    val message: String,
)

enum class ActionValidationLevel {
    WARNING,
    ERROR,
}
