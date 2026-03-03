package com.ksxkq.cmm_clicker.accessibility

import com.ksxkq.cmm_clicker.core.model.FlowNode
import com.ksxkq.cmm_clicker.core.model.NodeKind
import kotlin.math.roundToInt

internal fun resolveJumpTargetFlowId(
    node: FlowNode,
    currentFlowId: String,
): String {
    return node.params["targetFlowId"]
        ?.toString()
        ?.takeIf { it.isNotBlank() }
        ?: currentFlowId
}

internal fun defaultJumpTargetNodeId(nodes: List<FlowNode>): String {
    return nodes.firstOrNull { it.kind != NodeKind.START }?.nodeId.orEmpty()
}

internal fun resolveJumpTargetNodeId(
    node: FlowNode,
    selectableNodes: List<FlowNode>,
): String {
    return node.params["targetNodeId"]
        ?.toString()
        ?.takeIf { it.isNotBlank() }
        ?: defaultJumpTargetNodeId(selectableNodes)
}

internal fun ratioToPixel(
    ratio: Double,
    screenSizePx: Int,
): Int {
    val maxIndex = (screenSizePx - 1).coerceAtLeast(0)
    return (ratio.coerceIn(0.0, 1.0) * maxIndex).roundToInt()
}

internal fun pixelInputToRatioOrNull(
    input: String,
    screenSizePx: Int,
): Double? {
    val parsed = input.toIntOrNull() ?: return null
    val maxIndex = (screenSizePx - 1).coerceAtLeast(0)
    val clamped = parsed.coerceIn(0, maxIndex)
    return clamped.toDouble() / maxIndex.coerceAtLeast(1).toDouble()
}
