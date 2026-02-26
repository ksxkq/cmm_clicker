package com.ksxkq.cmm_clicker.feature.editor

import com.ksxkq.cmm_clicker.core.model.ActionType
import com.ksxkq.cmm_clicker.core.model.FlowNode
import com.ksxkq.cmm_clicker.core.model.NodeKind

enum class ParamFieldInputType {
    TEXT,
    NUMBER,
}

data class ParamFieldDefinition(
    val key: String,
    val label: String,
    val inputType: ParamFieldInputType = ParamFieldInputType.TEXT,
    val options: List<String> = emptyList(),
)

object EditorParamSchemaRegistry {
    fun fieldsFor(node: FlowNode): List<ParamFieldDefinition> {
        return when (node.kind) {
            NodeKind.BRANCH -> branchFields(node)
            NodeKind.JUMP,
            NodeKind.FOLDER_REF,
            NodeKind.SUB_TASK_REF,
            -> listOf(
                ParamFieldDefinition(key = "targetFlowId", label = "targetFlowId"),
                ParamFieldDefinition(key = "targetNodeId", label = "targetNodeId"),
            )

            NodeKind.ACTION -> actionFields(node.actionType)
            else -> emptyList()
        }
    }

    private fun branchFields(node: FlowNode): List<ParamFieldDefinition> {
        val operator = node.params["operator"]?.toString()?.lowercase() ?: "truthy"
        val result = mutableListOf(
            ParamFieldDefinition(key = "variableKey", label = "branch.variableKey"),
            ParamFieldDefinition(
                key = "operator",
                label = "branch.operator",
                options = listOf("truthy", "eq", "ne", "gt", "gte", "lt", "lte"),
            ),
        )
        if (operator != "truthy") {
            result += ParamFieldDefinition(
                key = "expectedValue",
                label = "branch.expectedValue",
                inputType = ParamFieldInputType.TEXT,
            )
        }
        return result
    }

    private fun actionFields(actionType: ActionType?): List<ParamFieldDefinition> {
        return when (actionType) {
            ActionType.CLICK -> listOf(
                ParamFieldDefinition(key = "x", label = "x", inputType = ParamFieldInputType.NUMBER),
                ParamFieldDefinition(key = "y", label = "y", inputType = ParamFieldInputType.NUMBER),
                ParamFieldDefinition(key = "durationMs", label = "durationMs", inputType = ParamFieldInputType.NUMBER),
            )

            ActionType.DUP_CLICK -> listOf(
                ParamFieldDefinition(key = "x", label = "x", inputType = ParamFieldInputType.NUMBER),
                ParamFieldDefinition(key = "y", label = "y", inputType = ParamFieldInputType.NUMBER),
                ParamFieldDefinition(key = "durationMs", label = "durationMs", inputType = ParamFieldInputType.NUMBER),
                ParamFieldDefinition(key = "count", label = "count", inputType = ParamFieldInputType.NUMBER),
                ParamFieldDefinition(key = "intervalMs", label = "intervalMs", inputType = ParamFieldInputType.NUMBER),
            )

            ActionType.SWIPE -> listOf(
                ParamFieldDefinition(key = "startX", label = "startX", inputType = ParamFieldInputType.NUMBER),
                ParamFieldDefinition(key = "startY", label = "startY", inputType = ParamFieldInputType.NUMBER),
                ParamFieldDefinition(key = "endX", label = "endX", inputType = ParamFieldInputType.NUMBER),
                ParamFieldDefinition(key = "endY", label = "endY", inputType = ParamFieldInputType.NUMBER),
                ParamFieldDefinition(key = "durationMs", label = "durationMs", inputType = ParamFieldInputType.NUMBER),
            )

            ActionType.RECORD -> listOf(
                ParamFieldDefinition(key = "durationMs", label = "durationMs", inputType = ParamFieldInputType.NUMBER),
            )

            else -> emptyList()
        }
    }
}
