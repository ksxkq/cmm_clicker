package com.ksxkq.cmm_clicker.accessibility

import com.ksxkq.cmm_clicker.core.model.ActionType
import com.ksxkq.cmm_clicker.core.model.FlowNode
import com.ksxkq.cmm_clicker.core.model.NodeKind
import com.ksxkq.cmm_clicker.feature.editor.EditorParamSchemaRegistry

internal enum class ParamEditorGroup(val title: String) {
    POSITION("位置/轨迹"),
    TIMING("时序"),
    TARGET("目标"),
    BEHAVIOR("行为"),
    ADVANCED("其它参数"),
}

internal fun buildNodeEditorParamsSnapshot(node: FlowNode): Map<String, Any?> {
    return EditorParamSchemaRegistry
        .mergedParamsWithDefaults(node)
        .filterNot { (key, _) ->
            node.kind == NodeKind.ACTION &&
                node.actionType == ActionType.CLICK &&
                (key == "x" || key == "y")
        }.filterNot { (key, _) ->
            node.kind == NodeKind.JUMP &&
                (key == "targetFlowId" || key == "targetNodeId")
        }
        .toSortedMap()
}

internal fun groupNodeEditorParams(
    paramsSnapshot: Map<String, Any?>,
): Map<ParamEditorGroup, List<Pair<String, Any?>>> {
    return paramsSnapshot
        .toList()
        .groupBy { (key, _) -> paramEditorGroupForKey(key) }
}

internal fun paramEditorGroupForKey(key: String): ParamEditorGroup {
    return when (key) {
        "x", "y", "startX", "startY", "endX", "endY", "points", "strokes", "timestampsMs" -> {
            ParamEditorGroup.POSITION
        }

        "durationMs", "postDelayMs", "intervalMs", "count", "startDelayMs" -> {
            ParamEditorGroup.TIMING
        }

        "targetFlowId", "targetNodeId" -> ParamEditorGroup.TARGET

        "variableKey", "operator", "expectedValue" -> ParamEditorGroup.BEHAVIOR

        else -> ParamEditorGroup.ADVANCED
    }
}
