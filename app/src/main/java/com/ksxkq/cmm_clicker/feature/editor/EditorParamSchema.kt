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
    val required: Boolean = false,
    val defaultValue: String? = null,
    val minValue: Double? = null,
    val maxValue: Double? = null,
    val helperText: String? = null,
)

object EditorParamSchemaRegistry {
    fun fieldsFor(node: FlowNode): List<ParamFieldDefinition> {
        return when (node.kind) {
            NodeKind.BRANCH -> branchFields(node)
            NodeKind.JUMP,
            NodeKind.FOLDER_REF,
            NodeKind.SUB_TASK_REF,
            -> listOf(
                ParamFieldDefinition(
                    key = "targetFlowId",
                    label = "targetFlowId",
                    required = true,
                    helperText = "目标 flow id",
                ),
                ParamFieldDefinition(
                    key = "targetNodeId",
                    label = "targetNodeId",
                    required = true,
                    helperText = "目标 node id",
                ),
            )

            NodeKind.ACTION -> actionFields(node.actionType)
            else -> emptyList()
        }
    }

    fun mergedParamsWithDefaults(node: FlowNode): Map<String, String> {
        val defaults = fieldsFor(node)
            .mapNotNull { field ->
                val value = field.defaultValue?.trim().orEmpty()
                if (value.isBlank()) null else field.key to value
            }
            .toMap()
            .toMutableMap()
        node.params.forEach { (key, raw) ->
            val text = raw?.toString()?.trim().orEmpty()
            if (text.isNotBlank()) {
                defaults[key] = text
            }
        }
        return defaults
    }

    private fun branchFields(node: FlowNode): List<ParamFieldDefinition> {
        val operator = node.params["operator"]?.toString()?.lowercase() ?: "truthy"
        val result = mutableListOf(
            ParamFieldDefinition(
                key = "variableKey",
                label = "branch.variableKey",
                required = true,
                defaultValue = "doSwipe",
                helperText = "条件变量名",
            ),
            ParamFieldDefinition(
                key = "operator",
                label = "branch.operator",
                options = listOf("truthy", "eq", "ne", "gt", "gte", "lt", "lte"),
                required = true,
                defaultValue = "truthy",
            ),
        )
        if (operator != "truthy") {
            result += ParamFieldDefinition(
                key = "expectedValue",
                label = "branch.expectedValue",
                inputType = ParamFieldInputType.TEXT,
                required = true,
                defaultValue = "true",
            )
        }
        return result
    }

    private fun actionFields(actionType: ActionType?): List<ParamFieldDefinition> {
        return when (actionType) {
            ActionType.CLICK -> listOf(
                ParamFieldDefinition(
                    key = "x",
                    label = "x",
                    inputType = ParamFieldInputType.NUMBER,
                    required = true,
                    defaultValue = "0.5",
                    minValue = 0.0,
                    maxValue = 1.0,
                    helperText = "0~1 屏幕比例",
                ),
                ParamFieldDefinition(
                    key = "y",
                    label = "y",
                    inputType = ParamFieldInputType.NUMBER,
                    required = true,
                    defaultValue = "0.5",
                    minValue = 0.0,
                    maxValue = 1.0,
                    helperText = "0~1 屏幕比例",
                ),
                ParamFieldDefinition(
                    key = "durationMs",
                    label = "durationMs",
                    inputType = ParamFieldInputType.NUMBER,
                    required = true,
                    defaultValue = "60",
                    minValue = 1.0,
                    maxValue = 10000.0,
                    helperText = "点击时长(毫秒)",
                ),
            )

            ActionType.DUP_CLICK -> listOf(
                ParamFieldDefinition(
                    key = "x",
                    label = "x",
                    inputType = ParamFieldInputType.NUMBER,
                    required = true,
                    defaultValue = "0.5",
                    minValue = 0.0,
                    maxValue = 1.0,
                    helperText = "0~1 屏幕比例",
                ),
                ParamFieldDefinition(
                    key = "y",
                    label = "y",
                    inputType = ParamFieldInputType.NUMBER,
                    required = true,
                    defaultValue = "0.5",
                    minValue = 0.0,
                    maxValue = 1.0,
                    helperText = "0~1 屏幕比例",
                ),
                ParamFieldDefinition(
                    key = "durationMs",
                    label = "durationMs",
                    inputType = ParamFieldInputType.NUMBER,
                    required = true,
                    defaultValue = "50",
                    minValue = 1.0,
                    maxValue = 10000.0,
                ),
                ParamFieldDefinition(
                    key = "count",
                    label = "count",
                    inputType = ParamFieldInputType.NUMBER,
                    required = true,
                    defaultValue = "2",
                    minValue = 1.0,
                    maxValue = 20.0,
                ),
                ParamFieldDefinition(
                    key = "intervalMs",
                    label = "intervalMs",
                    inputType = ParamFieldInputType.NUMBER,
                    required = true,
                    defaultValue = "80",
                    minValue = 0.0,
                    maxValue = 5000.0,
                ),
            )

            ActionType.SWIPE -> listOf(
                ParamFieldDefinition(
                    key = "startX",
                    label = "startX",
                    inputType = ParamFieldInputType.NUMBER,
                    required = true,
                    defaultValue = "0.5",
                    minValue = 0.0,
                    maxValue = 1.0,
                    helperText = "0~1 屏幕比例",
                ),
                ParamFieldDefinition(
                    key = "startY",
                    label = "startY",
                    inputType = ParamFieldInputType.NUMBER,
                    required = true,
                    defaultValue = "0.8",
                    minValue = 0.0,
                    maxValue = 1.0,
                    helperText = "0~1 屏幕比例",
                ),
                ParamFieldDefinition(
                    key = "endX",
                    label = "endX",
                    inputType = ParamFieldInputType.NUMBER,
                    required = true,
                    defaultValue = "0.5",
                    minValue = 0.0,
                    maxValue = 1.0,
                    helperText = "0~1 屏幕比例",
                ),
                ParamFieldDefinition(
                    key = "endY",
                    label = "endY",
                    inputType = ParamFieldInputType.NUMBER,
                    required = true,
                    defaultValue = "0.2",
                    minValue = 0.0,
                    maxValue = 1.0,
                    helperText = "0~1 屏幕比例",
                ),
                ParamFieldDefinition(
                    key = "durationMs",
                    label = "durationMs",
                    inputType = ParamFieldInputType.NUMBER,
                    required = true,
                    defaultValue = "300",
                    minValue = 1.0,
                    maxValue = 10000.0,
                ),
            )

            ActionType.RECORD -> listOf(
                ParamFieldDefinition(
                    key = "durationMs",
                    label = "durationMs",
                    inputType = ParamFieldInputType.NUMBER,
                    required = true,
                    defaultValue = "400",
                    minValue = 1.0,
                    maxValue = 600000.0,
                ),
            )

            else -> emptyList()
        }
    }
}

object EditorParamValidator {
    fun validate(
        definition: ParamFieldDefinition?,
        value: String,
    ): String? {
        if (definition == null) {
            return null
        }
        val trimmed = value.trim()
        if (definition.required && trimmed.isEmpty()) {
            return "${definition.label} 不能为空"
        }
        if (trimmed.isEmpty()) {
            return null
        }
        if (definition.options.isNotEmpty() && trimmed !in definition.options) {
            return "仅支持: ${definition.options.joinToString("/")}"
        }
        if (definition.inputType == ParamFieldInputType.NUMBER) {
            val number = trimmed.toDoubleOrNull() ?: return "请输入数字"
            definition.minValue?.let { min ->
                if (number < min) {
                    return "不能小于 $min"
                }
            }
            definition.maxValue?.let { max ->
                if (number > max) {
                    return "不能大于 $max"
                }
            }
        }
        return null
    }
}
