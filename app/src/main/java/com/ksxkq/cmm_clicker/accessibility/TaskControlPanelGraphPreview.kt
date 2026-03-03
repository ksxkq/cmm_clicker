package com.ksxkq.cmm_clicker.accessibility

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path as ComposePath
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ksxkq.cmm_clicker.core.model.FlowNode
import com.ksxkq.cmm_clicker.core.model.NodeKind
import com.ksxkq.cmm_clicker.core.model.TaskFlow
import com.ksxkq.cmm_clicker.feature.editor.TaskGraphEditorState
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

internal data class RowAnchor(
    val centerY: Float,
    val rightX: Float,
)

internal data class JumpLaneConnection(
    val sourceNodeId: String,
    val targetNodeId: String,
    val lane: Int,
    val isForward: Boolean,
    val isCollapsed: Boolean,
)

internal data class JumpConnectionLayout(
    val connections: List<JumpLaneConnection>,
    val laneCount: Int,
    val overflowCount: Int,
)

private data class JumpPolyline(
    val targetNodeId: String,
    val start: Offset,
    val laneStart: Offset,
    val laneEnd: Offset,
    val end: Offset,
    val isForward: Boolean,
    val isCollapsed: Boolean,
)

private data class IndexedJumpConnection(
    val sourceNodeId: String,
    val targetNodeId: String,
    val sourceIndex: Int,
    val targetIndex: Int,
    val startIndex: Int,
    val endIndex: Int,
)

private data class GraphPoint(
    val xRatio: Float,
    val yRatio: Float,
)

private data class PreviewJumpLink(
    val fromNodeId: String,
    val toNodeId: String,
    val lane: Int,
    val fromLabel: String,
    val toLabel: String,
)

internal fun connectionGutterWidthDp(laneCount: Int): Float {
    if (laneCount <= 0) {
        return 0f
    }
    return 36f + ((laneCount - 1) * 12f)
}

internal fun buildJumpConnectionLayout(
    flow: TaskFlow,
    actionNodes: List<FlowNode>,
    maxVisibleLanes: Int,
): JumpConnectionLayout {
    val idToIndex = actionNodes
        .mapIndexed { index, node -> node.nodeId to index }
        .toMap()
    if (idToIndex.isEmpty()) {
        return JumpConnectionLayout(
            connections = emptyList(),
            laneCount = 0,
            overflowCount = 0,
        )
    }
    val indexedConnections = flow.nodes
        .filter {
            it.kind == NodeKind.JUMP || it.kind == NodeKind.FOLDER_REF || it.kind == NodeKind.SUB_TASK_REF
        }
        .mapNotNull { node ->
            val fromIndex = idToIndex[node.nodeId] ?: return@mapNotNull null
            val targetFlowId = node.params["targetFlowId"]?.toString()?.takeIf { it.isNotBlank() } ?: flow.flowId
            val targetNodeId = node.params["targetNodeId"]?.toString()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val toIndex = idToIndex[targetNodeId] ?: return@mapNotNull null
            if (targetFlowId != flow.flowId || node.nodeId == targetNodeId) {
                return@mapNotNull null
            }
            IndexedJumpConnection(
                sourceNodeId = node.nodeId,
                targetNodeId = targetNodeId,
                sourceIndex = fromIndex,
                targetIndex = toIndex,
                startIndex = min(fromIndex, toIndex),
                endIndex = max(fromIndex, toIndex),
            )
        }
        .sortedWith(
            compareBy<IndexedJumpConnection> { it.startIndex }
                .thenByDescending { it.endIndex - it.startIndex },
        )
    if (indexedConnections.isEmpty()) {
        return JumpConnectionLayout(
            connections = emptyList(),
            laneCount = 0,
            overflowCount = 0,
        )
    }
    val safeMaxVisibleLanes = maxVisibleLanes.coerceAtLeast(1)
    val laneLastEnd = mutableListOf<Int>()
    var overflowCount = 0
    val laneConnections = indexedConnections.map { connection ->
        val fullLane = laneLastEnd.indexOfFirst { lastEnd -> lastEnd < connection.startIndex }
            .takeIf { it >= 0 }
            ?: run {
                laneLastEnd += Int.MIN_VALUE
                laneLastEnd.lastIndex
            }
        laneLastEnd[fullLane] = connection.endIndex
        val isCollapsed = fullLane >= safeMaxVisibleLanes
        if (isCollapsed) {
            overflowCount++
        }
        JumpLaneConnection(
            sourceNodeId = connection.sourceNodeId,
            targetNodeId = connection.targetNodeId,
            lane = fullLane.coerceAtMost(safeMaxVisibleLanes - 1),
            isForward = connection.targetIndex > connection.sourceIndex,
            isCollapsed = isCollapsed,
        )
    }
    return JumpConnectionLayout(
        connections = laneConnections,
        laneCount = min(laneLastEnd.size, safeMaxVisibleLanes),
        overflowCount = overflowCount,
    )
}

@Composable
internal fun JumpConnectionCanvas(
    rowAnchors: SnapshotStateMap<String, RowAnchor>,
    connections: List<JumpLaneConnection>,
    laneCount: Int,
    onConnectionTap: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val forwardColor = MaterialTheme.colorScheme.primary
    val backwardColor = MaterialTheme.colorScheme.secondary
    val rowAnchorsSnapshot = rowAnchors.toMap()
    val densityScale = androidx.compose.ui.platform.LocalDensity.current.density
    val interactiveModifier = if (onConnectionTap == null) {
        modifier
    } else {
        modifier.pointerInput(rowAnchorsSnapshot, connections, laneCount, densityScale) {
            detectTapGestures { tapOffset ->
                val thresholdPx = 14f * densityScale
                val thresholdSq = thresholdPx * thresholdPx
                var bestTarget: String? = null
                var bestDistSq = Float.MAX_VALUE
                val polylines = buildJumpPolylines(
                    rowAnchors = rowAnchorsSnapshot,
                    connections = connections,
                    laneCount = laneCount,
                    canvasWidth = size.width.toFloat(),
                    densityScale = densityScale,
                )
                polylines.forEach { line ->
                    val first = distanceSquaredToSegment(point = tapOffset, a = line.start, b = line.laneStart)
                    val second = distanceSquaredToSegment(point = tapOffset, a = line.laneStart, b = line.laneEnd)
                    val third = distanceSquaredToSegment(point = tapOffset, a = line.laneEnd, b = line.end)
                    val nearest = min(first, min(second, third))
                    if (nearest < bestDistSq) {
                        bestDistSq = nearest
                        bestTarget = line.targetNodeId
                    }
                }
                if (bestDistSq <= thresholdSq) {
                    bestTarget?.let { targetNodeId ->
                        onConnectionTap.invoke(targetNodeId)
                    }
                }
            }
        }
    }
    Canvas(modifier = interactiveModifier) {
        val dash = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()), 0f)
        val polylines = buildJumpPolylines(
            rowAnchors = rowAnchors,
            connections = connections,
            laneCount = laneCount,
            canvasWidth = size.width,
            densityScale = densityScale,
        )
        val startDotRadius = 2.dp.toPx()
        val arrowLength = 10.dp.toPx()
        val arrowWidth = 8.dp.toPx()
        val dashedStroke = 2.dp.toPx()
        val endStroke = 2.8.dp.toPx()
        polylines.forEach { line ->
            val paintColor = if (line.isForward) forwardColor else backwardColor
            val alpha = if (line.isCollapsed) 0.58f else 0.92f
            drawLine(
                color = paintColor.copy(alpha = alpha),
                start = line.start,
                end = line.laneStart,
                strokeWidth = dashedStroke,
                pathEffect = dash,
                cap = StrokeCap.Round,
            )
            drawLine(
                color = paintColor.copy(alpha = alpha),
                start = line.laneStart,
                end = line.laneEnd,
                strokeWidth = dashedStroke,
                pathEffect = dash,
                cap = StrokeCap.Round,
            )
            drawLine(
                color = paintColor.copy(alpha = if (line.isCollapsed) 0.76f else 1f),
                start = line.laneEnd,
                end = line.end,
                strokeWidth = endStroke,
                cap = StrokeCap.Round,
            )
            drawCircle(color = paintColor.copy(alpha = if (line.isCollapsed) 0.16f else 0.25f), radius = 4.dp.toPx(), center = line.end)
            drawCircle(color = paintColor.copy(alpha = if (line.isCollapsed) 0.5f else 0.75f), radius = startDotRadius, center = line.start)
            drawArrowHead(
                color = paintColor.copy(alpha = if (line.isCollapsed) 0.8f else 1f),
                from = line.laneEnd,
                to = line.end,
                lengthPx = arrowLength,
                widthPx = arrowWidth,
            )
        }
    }
}

@Composable
internal fun FlowPreviewPanel(
    state: TaskGraphEditorState,
) {
    val flow = state.selectedFlow
    if (flow == null) {
        Text(
            text = "当前流程不存在，无法预览",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    val crossFlowJumps = flow.nodes
        .filter { node ->
            node.kind == NodeKind.JUMP || node.kind == NodeKind.FOLDER_REF || node.kind == NodeKind.SUB_TASK_REF
        }
        .mapNotNull { node ->
            val targetFlowId = node.params["targetFlowId"]?.toString()
                ?.takeIf { it.isNotBlank() }
                ?: flow.flowId
            val targetNodeId = node.params["targetNodeId"]?.toString().orEmpty()
            if (targetFlowId == flow.flowId || targetNodeId.isBlank()) {
                return@mapNotNull null
            }
            Triple(node.nodeId, targetFlowId, targetNodeId)
        }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "流程预览（图形）",
                style = MaterialTheme.typography.bodyMedium,
            )
            FlowPreviewGraph(
                flow = flow,
            )
            if (crossFlowJumps.isEmpty()) {
                Text(
                    text = "当前流程没有跨流程跳转",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    text = "跨流程跳转",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                )
                crossFlowJumps.forEach { (nodeId, targetFlowId, targetNodeId) ->
                    val flowName = state.bundle.findFlow(targetFlowId)?.name ?: targetFlowId
                    Text(
                        text = "$nodeId => $flowName/$targetNodeId",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun FlowPreviewGraph(
    flow: TaskFlow,
) {
    val chainNodes = remember(flow) { buildPreviewChainNodes(flow) }
    val points = remember(flow, chainNodes.map { it.nodeId }) {
        calculatePreviewReadOnlyPoints(chainNodes)
    }
    val jumpLinks = remember(flow, chainNodes.map { it.nodeId }) {
        buildPreviewJumpLinks(flow = flow, chainNodes = chainNodes)
    }
    val edgeColor = MaterialTheme.colorScheme.onSurfaceVariant
    val jumpColor = MaterialTheme.colorScheme.primary
    val nodeFillColor = MaterialTheme.colorScheme.surface
    val nodeStrokeColor = MaterialTheme.colorScheme.outline
    val containerColor = MaterialTheme.colorScheme.surfaceVariant
    val startNodeColor = androidx.compose.ui.graphics.Color(0xFF16A34A)
    val endNodeColor = androidx.compose.ui.graphics.Color(0xFFDC2626)
    val labelBgColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
    val labelTextColor = MaterialTheme.colorScheme.onSurface
    val jumpLabelTextColor = MaterialTheme.colorScheme.onSurface
    val labelTextPaint = remember(flow.flowId) { Paint(Paint.ANTI_ALIAS_FLAG) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
            .background(containerColor, RoundedCornerShape(10.dp)),
    ) {
        if (chainNodes.isEmpty()) {
            Text(
                text = "当前流程无可预览节点",
                modifier = Modifier.align(Alignment.Center),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Box
        }
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
        ) {
            val toOffset: (GraphPoint) -> Offset = { point ->
                Offset(point.xRatio * size.width, point.yRatio * size.height)
            }
            for (index in 0 until chainNodes.lastIndex) {
                val from = points[chainNodes[index].nodeId] ?: continue
                val to = points[chainNodes[index + 1].nodeId] ?: continue
                drawLine(
                    color = edgeColor,
                    start = toOffset(from),
                    end = toOffset(to),
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
            val jumpDash = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()), 0f)
            val jumpLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = jumpLabelTextColor.toArgb()
                textSize = 9.dp.toPx()
            }
            val rightMargin = 10.dp.toPx()
            val laneSpacing = 13.dp.toPx()
            val laneCount = jumpLinks.maxOfOrNull { it.lane + 1 } ?: 0
            val maxLane = (laneCount - 1).coerceAtLeast(0)
            jumpLinks.forEach { link ->
                val fromPoint = points[link.fromNodeId] ?: return@forEach
                val toPoint = points[link.toNodeId] ?: return@forEach
                val from = toOffset(fromPoint)
                val to = toOffset(toPoint)
                val laneX = size.width - rightMargin - ((maxLane - link.lane) * laneSpacing)
                val startBreak = Offset((from.x + 12.dp.toPx()).coerceAtMost(laneX - 6.dp.toPx()), from.y)
                val endBreak = Offset((to.x + 12.dp.toPx()).coerceAtMost(laneX - 6.dp.toPx()), to.y)
                drawLine(
                    color = jumpColor.copy(alpha = 0.94f),
                    start = from,
                    end = startBreak,
                    strokeWidth = 1.8.dp.toPx(),
                    pathEffect = jumpDash,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = jumpColor.copy(alpha = 0.94f),
                    start = Offset(laneX, startBreak.y),
                    end = Offset(laneX, endBreak.y),
                    strokeWidth = 1.8.dp.toPx(),
                    pathEffect = jumpDash,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = jumpColor.copy(alpha = 0.94f),
                    start = endBreak,
                    end = to,
                    strokeWidth = 1.8.dp.toPx(),
                    pathEffect = jumpDash,
                    cap = StrokeCap.Round,
                )
                drawArrowHead(
                    color = jumpColor,
                    from = endBreak,
                    to = to,
                    lengthPx = 8.dp.toPx(),
                    widthPx = 6.dp.toPx(),
                )
                val label = "${link.fromLabel} → ${link.toLabel}"
                val labelWidth = jumpLabelPaint.measureText(label)
                val metrics = jumpLabelPaint.fontMetrics
                val labelHeight = metrics.descent - metrics.ascent
                val labelPaddingX = 4.dp.toPx()
                val labelPaddingY = 2.dp.toPx()
                val labelX = (laneX - labelWidth - labelPaddingX * 2f - 2.dp.toPx())
                    .coerceAtLeast(2.dp.toPx())
                val labelY = ((startBreak.y + endBreak.y) / 2f - labelHeight / 2f - labelPaddingY)
                    .coerceIn(2.dp.toPx(), size.height - labelHeight - labelPaddingY * 2f - 2.dp.toPx())
                drawRoundRect(
                    color = labelBgColor,
                    topLeft = Offset(labelX, labelY),
                    size = Size(labelWidth + labelPaddingX * 2f, labelHeight + labelPaddingY * 2f),
                    cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx()),
                )
                drawContext.canvas.nativeCanvas.drawText(
                    label,
                    labelX + labelPaddingX,
                    labelY + labelPaddingY - metrics.ascent,
                    jumpLabelPaint,
                )
            }

            chainNodes.forEach { node ->
                val point = points[node.nodeId] ?: return@forEach
                val center = toOffset(point)
                val strokeWidth = 1.dp.toPx()
                when (node.kind) {
                    NodeKind.START -> {
                        val nodeWidth = 18.dp.toPx()
                        val nodeHeight = 12.dp.toPx()
                        val topLeft = Offset(center.x - nodeWidth / 2f, center.y - nodeHeight / 2f)
                        drawRoundRect(
                            color = startNodeColor.copy(alpha = 0.9f),
                            topLeft = topLeft,
                            size = Size(nodeWidth, nodeHeight),
                            cornerRadius = CornerRadius(nodeHeight / 2f, nodeHeight / 2f),
                        )
                        drawRoundRect(
                            color = startNodeColor.copy(alpha = 0.95f),
                            topLeft = topLeft,
                            size = Size(nodeWidth, nodeHeight),
                            cornerRadius = CornerRadius(nodeHeight / 2f, nodeHeight / 2f),
                            style = Stroke(width = strokeWidth),
                        )
                    }

                    NodeKind.END -> {
                        val radius = 8.dp.toPx()
                        val diamondPath = ComposePath().apply {
                            moveTo(center.x, center.y - radius)
                            lineTo(center.x + radius, center.y)
                            lineTo(center.x, center.y + radius)
                            lineTo(center.x - radius, center.y)
                            close()
                        }
                        drawPath(
                            path = diamondPath,
                            color = endNodeColor.copy(alpha = 0.9f),
                        )
                        drawPath(
                            path = diamondPath,
                            color = endNodeColor.copy(alpha = 0.95f),
                            style = Stroke(width = strokeWidth),
                        )
                    }

                    else -> {
                        drawCircle(
                            color = nodeFillColor,
                            radius = 7.dp.toPx(),
                            center = center,
                        )
                        drawCircle(
                            color = nodeStrokeColor,
                            radius = 7.dp.toPx(),
                            center = center,
                            style = Stroke(width = strokeWidth),
                        )
                    }
                }

                val label = previewNodeDisplayName(node)
                labelTextPaint.color = labelTextColor.toArgb()
                labelTextPaint.textSize = 10.dp.toPx()
                labelTextPaint.isFakeBoldText = false
                val metrics = labelTextPaint.fontMetrics
                val textWidth = labelTextPaint.measureText(label)
                val textHeight = metrics.descent - metrics.ascent
                val horizontalPadding = 5.dp.toPx()
                val verticalPadding = 3.dp.toPx()
                val labelWidth = textWidth + horizontalPadding * 2f
                val labelHeight = textHeight + verticalPadding * 2f
                val labelLeft = (center.x + 10.dp.toPx())
                    .coerceAtMost(size.width - labelWidth - 2.dp.toPx())
                    .coerceAtLeast(2.dp.toPx())
                val labelTop = (center.y - labelHeight / 2f)
                    .coerceIn(2.dp.toPx(), size.height - labelHeight - 2.dp.toPx())
                drawRoundRect(
                    color = labelBgColor,
                    topLeft = Offset(labelLeft, labelTop),
                    size = Size(labelWidth, labelHeight),
                    cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
                )
                drawRoundRect(
                    color = nodeStrokeColor.copy(alpha = 0.8f),
                    topLeft = Offset(labelLeft, labelTop),
                    size = Size(labelWidth, labelHeight),
                    cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
                    style = Stroke(width = 0.8.dp.toPx()),
                )
                val baseline = labelTop + verticalPadding - metrics.ascent
                drawContext.canvas.nativeCanvas.drawText(
                    label,
                    labelLeft + horizontalPadding,
                    baseline,
                    labelTextPaint,
                )
            }
        }
    }
}

private fun buildJumpPolylines(
    rowAnchors: Map<String, RowAnchor>,
    connections: List<JumpLaneConnection>,
    laneCount: Int,
    canvasWidth: Float,
    densityScale: Float,
): List<JumpPolyline> {
    if (connections.isEmpty() || laneCount <= 0) {
        return emptyList()
    }
    val rightMarginPx = 14f * densityScale
    val laneSpacingPx = 12f * densityScale
    val attachGapPx = 8f * densityScale
    val minOffsetPx = 6f * densityScale
    val maxLane = (laneCount - 1).coerceAtLeast(0)
    fun laneX(lane: Int): Float {
        val normalizedLane = lane.coerceIn(0, maxLane)
        return canvasWidth - rightMarginPx - ((maxLane - normalizedLane) * laneSpacingPx)
    }
    return connections.mapNotNull { connection ->
        val source = rowAnchors[connection.sourceNodeId] ?: return@mapNotNull null
        val target = rowAnchors[connection.targetNodeId] ?: return@mapNotNull null
        val start = Offset(source.rightX + attachGapPx, source.centerY)
        val end = Offset(target.rightX + attachGapPx, target.centerY)
        val laneVerticalX = laneX(connection.lane)
        val safeVerticalX = max(
            laneVerticalX,
            max(start.x + minOffsetPx, end.x + minOffsetPx),
        )
        JumpPolyline(
            targetNodeId = connection.targetNodeId,
            start = start,
            laneStart = Offset(safeVerticalX, start.y),
            laneEnd = Offset(safeVerticalX, end.y),
            end = end,
            isForward = connection.isForward,
            isCollapsed = connection.isCollapsed,
        )
    }
}

private fun buildPreviewChainNodes(flow: TaskFlow): List<FlowNode> {
    if (flow.nodes.isEmpty()) {
        return emptyList()
    }
    val start = flow.nodes.firstOrNull { it.kind == NodeKind.START }
        ?: flow.findNode(flow.entryNodeId)
    val end = flow.nodes.firstOrNull { it.kind == NodeKind.END }
    val middle = flow.nodes.filter { node ->
        node.nodeId != start?.nodeId && node.nodeId != end?.nodeId
    }
    return buildList {
        start?.let(::add)
        addAll(middle)
        end?.let(::add)
    }
}

private fun calculatePreviewReadOnlyPoints(nodes: List<FlowNode>): Map<String, GraphPoint> {
    if (nodes.isEmpty()) {
        return emptyMap()
    }
    val denominator = (nodes.size - 1).coerceAtLeast(1)
    return nodes.mapIndexed { index, node ->
        val y = 0.08f + (0.84f * (index.toFloat() / denominator.toFloat()))
        node.nodeId to GraphPoint(
            xRatio = 0.26f,
            yRatio = y.coerceIn(0.08f, 0.92f),
        )
    }.toMap()
}

private fun buildPreviewJumpLinks(
    flow: TaskFlow,
    chainNodes: List<FlowNode>,
): List<PreviewJumpLink> {
    if (chainNodes.isEmpty()) {
        return emptyList()
    }
    val indexByNode = chainNodes.mapIndexed { index, node -> node.nodeId to index }.toMap()
    data class RawLink(
        val fromNodeId: String,
        val toNodeId: String,
        val start: Int,
        val end: Int,
        val fromLabel: String,
        val toLabel: String,
    )
    val rawLinks = flow.nodes
        .filter { node ->
            node.kind == NodeKind.JUMP || node.kind == NodeKind.FOLDER_REF || node.kind == NodeKind.SUB_TASK_REF
        }
        .mapNotNull { node ->
            val fromIndex = indexByNode[node.nodeId] ?: return@mapNotNull null
            val targetFlowId = node.params["targetFlowId"]?.toString()?.takeIf { it.isNotBlank() } ?: flow.flowId
            val targetNodeId = node.params["targetNodeId"]?.toString()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val toIndex = indexByNode[targetNodeId] ?: return@mapNotNull null
            if (targetFlowId != flow.flowId || node.nodeId == targetNodeId) {
                return@mapNotNull null
            }
            RawLink(
                fromNodeId = node.nodeId,
                toNodeId = targetNodeId,
                start = min(fromIndex, toIndex),
                end = max(fromIndex, toIndex),
                fromLabel = previewNodeDisplayName(node),
                toLabel = previewNodeDisplayName(chainNodes[toIndex]),
            )
        }
        .sortedWith(compareBy<RawLink> { it.start }.thenByDescending { it.end - it.start })
    if (rawLinks.isEmpty()) {
        return emptyList()
    }
    val laneLastEnd = mutableListOf<Int>()
    return rawLinks.map { link ->
        val lane = laneLastEnd.indexOfFirst { lastEnd -> lastEnd < link.start }
            .takeIf { it >= 0 }
            ?: run {
                laneLastEnd += Int.MIN_VALUE
                laneLastEnd.lastIndex
            }
        laneLastEnd[lane] = link.end
        PreviewJumpLink(
            fromNodeId = link.fromNodeId,
            toNodeId = link.toNodeId,
            lane = lane,
            fromLabel = if (link.fromLabel.length <= 8) link.fromLabel else link.fromLabel.take(7) + "…",
            toLabel = if (link.toLabel.length <= 8) link.toLabel else link.toLabel.take(7) + "…",
        )
    }
}

private fun previewNodeDisplayName(node: FlowNode): String {
    val raw = when (node.kind) {
        NodeKind.START -> "开始"
        NodeKind.END -> "结束"
        NodeKind.ACTION -> {
            val custom = node.params["name"]?.toString()?.trim().orEmpty()
            if (custom.isNotEmpty()) {
                custom
            } else {
                "${node.actionType?.name ?: "ACTION"} · ${node.nodeId}"
            }
        }

        NodeKind.BRANCH -> "分支 · ${node.nodeId}"
        NodeKind.JUMP -> "跳转 · ${node.nodeId}"
        NodeKind.FOLDER_REF -> "文件夹 · ${node.nodeId}"
        NodeKind.SUB_TASK_REF -> "子任务 · ${node.nodeId}"
    }
    return if (raw.length <= 16) raw else raw.take(15) + "…"
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

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawArrowHead(
    color: androidx.compose.ui.graphics.Color,
    from: Offset,
    to: Offset,
    lengthPx: Float,
    widthPx: Float,
) {
    val dx = to.x - from.x
    val dy = to.y - from.y
    val lenSq = dx * dx + dy * dy
    if (lenSq < 0.25f) {
        return
    }
    val len = sqrt(lenSq)
    val ux = dx / len
    val uy = dy / len
    val px = -uy
    val py = ux
    val baseX = to.x - ux * lengthPx
    val baseY = to.y - uy * lengthPx
    val left = Offset(baseX + px * widthPx * 0.5f, baseY + py * widthPx * 0.5f)
    val right = Offset(baseX - px * widthPx * 0.5f, baseY - py * widthPx * 0.5f)
    val arrowPath = ComposePath().apply {
        moveTo(to.x, to.y)
        lineTo(left.x, left.y)
        lineTo(right.x, right.y)
        close()
    }
    drawPath(
        path = arrowPath,
        color = color,
    )
}
