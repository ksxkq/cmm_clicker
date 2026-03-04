package com.ksxkq.cmm_clicker.core.runtime

import com.ksxkq.cmm_clicker.core.actions.ActionContext
import com.ksxkq.cmm_clicker.core.actions.ActionExecutionStatus
import com.ksxkq.cmm_clicker.core.actions.ActionPluginRegistry
import com.ksxkq.cmm_clicker.core.actions.ActionResult
import com.ksxkq.cmm_clicker.core.actions.ActionValidationLevel
import com.ksxkq.cmm_clicker.core.actions.builtin.BuiltinPluginFactory
import com.ksxkq.cmm_clicker.core.model.EdgeConditionType
import com.ksxkq.cmm_clicker.core.model.FlowEdge
import com.ksxkq.cmm_clicker.core.model.FlowGraphValidator
import com.ksxkq.cmm_clicker.core.model.FlowNode
import com.ksxkq.cmm_clicker.core.model.NodeKind
import com.ksxkq.cmm_clicker.core.model.NodePointer
import com.ksxkq.cmm_clicker.core.model.TaskBundle
import com.ksxkq.cmm_clicker.core.model.TaskFlow
import com.ksxkq.cmm_clicker.core.model.ValidationSeverity
import java.util.UUID
import kotlinx.coroutines.delay

class FlowRuntimeEngine(
    private val pluginRegistry: ActionPluginRegistry = BuiltinPluginFactory.createDefaultRegistry(),
    private val validator: FlowGraphValidator = FlowGraphValidator(),
    private val options: RuntimeEngineOptions = RuntimeEngineOptions(),
) {
    suspend fun execute(
        bundle: TaskBundle,
        initialVariables: Map<String, Any?> = emptyMap(),
        traceCollector: RuntimeTraceCollector = InMemoryRuntimeTraceCollector(),
    ): RuntimeExecutionResult {
        val normalizedBundle = normalizeBundleSequentialAlwaysEdges(bundle)
        val validationIssues = validator.validate(normalizedBundle)
        val hasValidationErrors = validationIssues.any { it.severity == ValidationSeverity.ERROR }
        if (hasValidationErrors && options.stopOnValidationError) {
            return RuntimeExecutionResult(
                status = RuntimeExecutionStatus.FAILED,
                traceId = "invalid_graph",
                stepCount = 0,
                finalPointer = null,
                message = "graph_validation_failed",
                validationIssues = validationIssues,
            )
        }

        val traceId = UUID.randomUUID().toString()
        val entryFlow = normalizedBundle.findFlow(normalizedBundle.entryFlowId)
            ?: return RuntimeExecutionResult(
                status = RuntimeExecutionStatus.FAILED,
                traceId = traceId,
                stepCount = 0,
                finalPointer = null,
                message = "entry_flow_not_found",
                validationIssues = validationIssues,
            )

        val runtimeContext = RuntimeContext(
            traceId = traceId,
            dryRun = options.dryRun,
            variables = initialVariables.toMutableMap(),
        )
        val flowCallStack = ArrayDeque<FlowCallFrame>()

        var pointer: NodePointer? = NodePointer(entryFlow.flowId, entryFlow.entryNodeId)
        while (pointer != null && runtimeContext.step < options.maxSteps) {
            waitIfPaused()
            runtimeContext.currentPointer = pointer
            val flow = normalizedBundle.findFlow(pointer.flowId)
                ?: return fail(
                    runtimeContext = runtimeContext,
                    traceCollector = traceCollector,
                    pointer = pointer,
                    message = "flow_not_found:${pointer.flowId}",
                    validationIssues = validationIssues,
                )
            val node = flow.findNode(pointer.nodeId)
                ?: return fail(
                    runtimeContext = runtimeContext,
                    traceCollector = traceCollector,
                    pointer = pointer,
                    message = "node_not_found:${pointer.nodeId}",
                    validationIssues = validationIssues,
                )

            runtimeContext.lastActionResult = null
            traceCollector.add(
                RuntimeTraceEvent(
                    traceId = traceId,
                    step = runtimeContext.step,
                    flowId = flow.flowId,
                    nodeId = node.nodeId,
                    nodeKind = node.kind,
                    phase = RuntimeTracePhase.NODE_START,
                    details = buildNodeStartDetails(node),
                ),
            )

            if (!node.flags.enabled || !node.flags.active) {
                val skipped = resolveNextByDefault(flow, node)
                traceCollector.add(
                    RuntimeTraceEvent(
                        traceId = traceId,
                        step = runtimeContext.step,
                        flowId = flow.flowId,
                        nodeId = node.nodeId,
                        nodeKind = node.kind,
                        phase = RuntimeTracePhase.NODE_END,
                        message = "node_skipped",
                        details = mapOf(
                            "skipReason" to "node_disabled_or_inactive",
                            "nextFlowId" to (skipped?.flowId ?: "-"),
                            "nextNodeId" to (skipped?.nodeId ?: "-"),
                        ),
                    ),
                )
                pointer = skipped
                runtimeContext.step++
                continue
            }

            val outcome = when (node.kind) {
                NodeKind.START -> NodeOutcome(
                    status = NodeOutcomeStatus.CONTINUE,
                    next = resolveNextByDefault(flow, node),
                )

                NodeKind.END -> NodeOutcome(
                    status = if (flowCallStack.isEmpty()) NodeOutcomeStatus.COMPLETE else NodeOutcomeStatus.CONTINUE,
                    next = if (flowCallStack.isEmpty()) null else flowCallStack.removeLast().returnPointer,
                    message = if (flowCallStack.isEmpty()) "flow_end" else "return_from_subflow",
                )

                NodeKind.ACTION -> executeActionNode(
                    flow = flow,
                    node = node,
                    runtimeContext = runtimeContext,
                )

                NodeKind.JUMP,
                -> {
                    val target = parseTargetPointer(node, flow.flowId)
                    if (target == null) {
                        NodeOutcome(
                            status = NodeOutcomeStatus.FAIL,
                            message = "jump_target_missing",
                        )
                    } else {
                        NodeOutcome(
                            status = NodeOutcomeStatus.CONTINUE,
                            next = target,
                        )
                    }
                }

                NodeKind.FOLDER_REF,
                NodeKind.SUB_TASK_REF,
                -> {
                    val target = parseTargetPointer(node, flow.flowId)
                    if (target == null) {
                        NodeOutcome(
                            status = NodeOutcomeStatus.FAIL,
                            message = "jump_target_missing",
                        )
                    } else {
                        val returnPointer = resolveNextByDefault(flow, node)
                        flowCallStack.addLast(
                            FlowCallFrame(
                                caller = NodePointer(flow.flowId, node.nodeId),
                                returnPointer = returnPointer,
                            ),
                        )
                        NodeOutcome(
                            status = NodeOutcomeStatus.CONTINUE,
                            next = target,
                            message = "enter_subflow",
                        )
                    }
                }

                NodeKind.BRANCH -> {
                    val branchValue = evalBranch(node, runtimeContext)
                    val next = resolveNextForBranch(flow, node, branchValue)
                    NodeOutcome(
                        status = NodeOutcomeStatus.CONTINUE,
                        next = next,
                        message = "branch=$branchValue",
                    )
                }
            }

            when (outcome.status) {
                NodeOutcomeStatus.COMPLETE -> {
                    traceCollector.add(
                        RuntimeTraceEvent(
                            traceId = traceId,
                            step = runtimeContext.step,
                            flowId = flow.flowId,
                            nodeId = node.nodeId,
                            nodeKind = node.kind,
                            phase = RuntimeTracePhase.NODE_END,
                            message = outcome.message ?: "completed",
                            details = buildNodeEndDetails(
                                node = node,
                                outcome = outcome,
                                postDelayMs = 0L,
                                actionResult = runtimeContext.lastActionResult,
                            ),
                        ),
                    )
                    return RuntimeExecutionResult(
                        status = RuntimeExecutionStatus.COMPLETED,
                        traceId = traceId,
                        stepCount = runtimeContext.step + 1,
                        finalPointer = pointer,
                        message = "completed",
                        validationIssues = validationIssues,
                        traceEvents = traceCollector.snapshot(),
                    )
                }

                NodeOutcomeStatus.CONTINUE -> {
                    val postDelayMs = readAnyLong(
                        value = node.params["postDelayMs"],
                        fallback = 0L,
                    ).coerceAtLeast(0L)
                    if (postDelayMs > 0L && !runtimeContext.dryRun) {
                        delayWithPause(postDelayMs)
                    }
                    traceCollector.add(
                        RuntimeTraceEvent(
                            traceId = traceId,
                            step = runtimeContext.step,
                            flowId = flow.flowId,
                            nodeId = node.nodeId,
                            nodeKind = node.kind,
                            phase = RuntimeTracePhase.NODE_END,
                            message = if (postDelayMs > 0L) {
                                val baseMessage = outcome.message?.takeIf { it.isNotBlank() } ?: "ok"
                                "$baseMessage | postDelayMs=$postDelayMs"
                            } else {
                                outcome.message
                            },
                            details = buildNodeEndDetails(
                                node = node,
                                outcome = outcome,
                                postDelayMs = postDelayMs,
                                actionResult = runtimeContext.lastActionResult,
                            ),
                        ),
                    )
                    pointer = outcome.next
                    runtimeContext.step++
                }

                NodeOutcomeStatus.FAIL -> {
                    return fail(
                        runtimeContext = runtimeContext,
                        traceCollector = traceCollector,
                        pointer = pointer,
                        message = outcome.message ?: "node_failed",
                        validationIssues = validationIssues,
                        nodeKind = node.kind,
                    )
                }

                NodeOutcomeStatus.STOP -> {
                    traceCollector.add(
                        RuntimeTraceEvent(
                            traceId = traceId,
                            step = runtimeContext.step,
                            flowId = flow.flowId,
                            nodeId = node.nodeId,
                            nodeKind = node.kind,
                            phase = RuntimeTracePhase.NODE_END,
                            message = outcome.message ?: "stopped",
                            details = buildNodeEndDetails(
                                node = node,
                                outcome = outcome,
                                postDelayMs = 0L,
                                actionResult = runtimeContext.lastActionResult,
                            ),
                        ),
                    )
                    return RuntimeExecutionResult(
                        status = RuntimeExecutionStatus.STOPPED,
                        traceId = traceId,
                        stepCount = runtimeContext.step + 1,
                        finalPointer = pointer,
                        message = outcome.message ?: "stopped",
                        validationIssues = validationIssues,
                        traceEvents = traceCollector.snapshot(),
                    )
                }
            }
        }

        if (pointer == null) {
            return RuntimeExecutionResult(
                status = RuntimeExecutionStatus.COMPLETED,
                traceId = runtimeContext.traceId,
                stepCount = runtimeContext.step,
                finalPointer = null,
                message = "completed_no_next",
                validationIssues = validationIssues,
                traceEvents = traceCollector.snapshot(),
            )
        }

        return RuntimeExecutionResult(
            status = RuntimeExecutionStatus.FAILED,
            traceId = runtimeContext.traceId,
            stepCount = runtimeContext.step,
            finalPointer = pointer,
            message = "max_steps_reached",
            validationIssues = validationIssues,
            traceEvents = traceCollector.snapshot(),
        )
    }

    private suspend fun waitIfPaused() {
        val checker = options.isPaused ?: return
        val pollIntervalMs = options.pausePollIntervalMs.coerceIn(16L, 1000L)
        while (checker.invoke()) {
            delay(pollIntervalMs)
        }
    }

    private suspend fun delayWithPause(durationMs: Long) {
        var remaining = durationMs.coerceAtLeast(0L)
        if (remaining == 0L) {
            return
        }
        val slice = options.pausePollIntervalMs.coerceIn(16L, 1000L)
        while (remaining > 0L) {
            waitIfPaused()
            val chunk = minOf(remaining, slice)
            delay(chunk)
            remaining -= chunk
        }
    }

    private fun normalizeBundleSequentialAlwaysEdges(bundle: TaskBundle): TaskBundle {
        val normalizedFlows = bundle.flows.map { flow ->
            if (shouldNormalizeSequentialAlwaysEdges(flow)) {
                normalizeFlowSequentialAlwaysEdges(flow)
            } else {
                flow
            }
        }
        return bundle.copy(flows = normalizedFlows)
    }

    private fun shouldNormalizeSequentialAlwaysEdges(flow: TaskFlow): Boolean {
        val endIndex = flow.nodes.indexOfFirst { it.kind == NodeKind.END }
        if (endIndex < 0) {
            return false
        }
        return flow.nodes
            .drop(endIndex + 1)
            .any { it.kind != NodeKind.END }
    }

    private fun normalizeFlowSequentialAlwaysEdges(flow: TaskFlow): TaskFlow {
        if (flow.nodes.isEmpty()) {
            return flow.copy(edges = emptyList())
        }
        val validNodeIds = flow.nodes.map { it.nodeId }.toSet()
        val preservedConditionalEdges = flow.edges.filter { edge ->
            edge.conditionType != EdgeConditionType.ALWAYS &&
                edge.fromNodeId in validNodeIds &&
                edge.toNodeId in validNodeIds
        }
        val existingAlwaysByFromNodeId = flow.edges
            .asSequence()
            .filter { edge ->
                edge.conditionType == EdgeConditionType.ALWAYS &&
                    edge.fromNodeId in validNodeIds &&
                    edge.toNodeId in validNodeIds
            }
            .groupBy { edge -> edge.fromNodeId }
            .mapValues { (_, edges) -> edges.first() }
        val sequentialNodes = buildSequentialExecutionNodes(flow.nodes)
        val rebuiltAlwaysEdges = sequentialNodes
            .zipWithNext()
            .map { (from, to) ->
                val existing = existingAlwaysByFromNodeId[from.nodeId]
                FlowEdge(
                    edgeId = existing?.edgeId ?: "runtime_auto_${flow.flowId}_${from.nodeId}_${to.nodeId}",
                    fromNodeId = from.nodeId,
                    toNodeId = to.nodeId,
                    conditionType = EdgeConditionType.ALWAYS,
                    priority = existing?.priority ?: 0,
                )
            }
        return flow.copy(edges = preservedConditionalEdges + rebuiltAlwaysEdges)
    }

    private fun buildSequentialExecutionNodes(nodes: List<FlowNode>): List<FlowNode> {
        if (nodes.isEmpty()) {
            return emptyList()
        }
        val start = nodes.firstOrNull { it.kind == NodeKind.START }
        val end = nodes.firstOrNull { it.kind == NodeKind.END }
        val middle = nodes.filter { it.kind != NodeKind.START && it.kind != NodeKind.END }
        return buildList {
            if (start != null) {
                add(start)
            }
            addAll(middle)
            if (end != null) {
                add(end)
            }
        }
    }

    private suspend fun executeActionNode(
        flow: TaskFlow,
        node: FlowNode,
        runtimeContext: RuntimeContext,
    ): NodeOutcome {
        val actionType = node.actionType
            ?: return NodeOutcome(
                status = NodeOutcomeStatus.FAIL,
                message = "action_type_missing",
            )

        val plugin = pluginRegistry.resolve(actionType)
        val validationIssues = plugin.validate(node.params)
        if (validationIssues.any { it.level == ActionValidationLevel.ERROR }) {
            return NodeOutcome(
                status = NodeOutcomeStatus.FAIL,
                message = "action_validation_failed:${actionType.raw}",
            )
        }

        val actionResult = plugin.execute(
            context = ActionContext(
                traceId = runtimeContext.traceId,
                flowId = flow.flowId,
                nodeId = node.nodeId,
                dryRun = runtimeContext.dryRun,
                variables = runtimeContext.variables,
            ),
            actionType = actionType,
            params = node.params,
        )
        runtimeContext.lastActionResult = actionResult

        return when (actionResult.status) {
            ActionExecutionStatus.SUCCESS -> {
                val matchKey = actionResult.payload["matchKey"] as? String
                val next = actionResult.next ?: resolveNext(flow, node, matchKey)
                NodeOutcome(
                    status = NodeOutcomeStatus.CONTINUE,
                    next = next,
                    message = actionResult.message,
                )
            }

            ActionExecutionStatus.FAILED,
            ActionExecutionStatus.ERROR,
            -> NodeOutcome(
                status = NodeOutcomeStatus.FAIL,
                message = actionResult.errorCode ?: actionResult.message ?: "action_failed",
            )

            ActionExecutionStatus.STOPPED -> NodeOutcome(
                status = NodeOutcomeStatus.STOP,
                message = actionResult.message ?: "action_stopped",
            )
        }
    }

    private fun readAnyLong(value: Any?, fallback: Long): Long {
        val parsed = when (value) {
            is Number -> value.toLong()
            is String -> value.toLongOrNull()
            else -> null
        }
        return parsed ?: fallback
    }

    private fun evalBranch(node: FlowNode, runtimeContext: RuntimeContext): Boolean {
        val key = node.params["variableKey"] as? String ?: return false
        val value = runtimeContext.variables[key]
        val operator = node.params["operator"]?.toString()?.lowercase() ?: "truthy"
        val expectedValue = node.params["expectedValue"]?.toString()

        return when (operator) {
            "eq" -> value?.toString() == (expectedValue ?: "")
            "ne" -> value?.toString() != (expectedValue ?: "")
            "gt" -> compareAsDouble(value, expectedValue) { left, right -> left > right }
            "gte" -> compareAsDouble(value, expectedValue) { left, right -> left >= right }
            "lt" -> compareAsDouble(value, expectedValue) { left, right -> left < right }
            "lte" -> compareAsDouble(value, expectedValue) { left, right -> left <= right }
            else -> asTruthy(value)
        }
    }

    private fun compareAsDouble(
        leftValue: Any?,
        rightRaw: String?,
        predicate: (Double, Double) -> Boolean,
    ): Boolean {
        val left = when (leftValue) {
            is Number -> leftValue.toDouble()
            is String -> leftValue.toDoubleOrNull()
            else -> null
        } ?: return false
        val right = rightRaw?.toDoubleOrNull() ?: return false
        return predicate(left, right)
    }

    private fun asTruthy(value: Any?): Boolean {
        return when (value) {
            is Boolean -> value
            is Number -> value.toInt() != 0
            is String -> value.equals("true", ignoreCase = true) || value == "1"
            else -> false
        }
    }

    private fun resolveNext(flow: TaskFlow, node: FlowNode, matchKey: String?): NodePointer? {
        val matchEdge = if (matchKey != null) {
            flow.edgesFrom(node.nodeId)
                .filter { it.conditionType == EdgeConditionType.MATCH_KEY && it.conditionKey == matchKey }
                .maxByOrNull { it.priority }
        } else {
            null
        }
        return toPointer(matchEdge, flow)
            ?: resolveNextByDefault(flow, node)
    }

    private fun resolveNextByDefault(flow: TaskFlow, node: FlowNode): NodePointer? {
        val edge = flow.edgesFrom(node.nodeId)
            .filter { it.conditionType == EdgeConditionType.ALWAYS }
            .maxByOrNull { it.priority }
        return toPointer(edge, flow)
    }

    private fun resolveNextForBranch(flow: TaskFlow, node: FlowNode, value: Boolean): NodePointer? {
        val preferred = if (value) EdgeConditionType.TRUE else EdgeConditionType.FALSE
        val preferredEdge = flow.edgesFrom(node.nodeId)
            .filter { it.conditionType == preferred }
            .maxByOrNull { it.priority }
        return toPointer(preferredEdge, flow)
            ?: resolveNextByDefault(flow, node)
    }

    private fun parseTargetPointer(node: FlowNode, defaultFlowId: String): NodePointer? {
        val targetNodeId = node.params["targetNodeId"] as? String ?: return null
        val targetFlowId = node.params["targetFlowId"] as? String ?: defaultFlowId
        if (targetFlowId.isBlank() || targetNodeId.isBlank()) {
            return null
        }
        return NodePointer(flowId = targetFlowId, nodeId = targetNodeId)
    }

    private fun toPointer(edge: FlowEdge?, flow: TaskFlow): NodePointer? {
        if (edge == null) {
            return null
        }
        return NodePointer(flowId = flow.flowId, nodeId = edge.toNodeId)
    }

    private fun fail(
        runtimeContext: RuntimeContext,
        traceCollector: RuntimeTraceCollector,
        pointer: NodePointer?,
        message: String,
        validationIssues: List<com.ksxkq.cmm_clicker.core.model.GraphValidationIssue>,
        nodeKind: NodeKind = NodeKind.ACTION,
    ): RuntimeExecutionResult {
        val current = runtimeContext.currentPointer ?: pointer
        if (current != null) {
            val actionResult = runtimeContext.lastActionResult
            traceCollector.add(
                RuntimeTraceEvent(
                    traceId = runtimeContext.traceId,
                    step = runtimeContext.step,
                    flowId = current.flowId,
                    nodeId = current.nodeId,
                    nodeKind = nodeKind,
                    phase = RuntimeTracePhase.NODE_ERROR,
                    message = message,
                    details = linkedMapOf<String, String>().apply {
                        put("errorCode", inferErrorCode(message))
                        put("rawMessage", message)
                        actionResult?.let { result ->
                            put("actionStatus", result.status.name)
                            put("actionErrorCode", result.errorCode ?: "-")
                            put("actionMessage", result.message ?: "-")
                            put(
                                "actionPayloadKeys",
                                if (result.payload.isEmpty()) "-" else result.payload.keys.sorted().joinToString(","),
                            )
                        }
                    },
                ),
            )
        }
        return RuntimeExecutionResult(
            status = RuntimeExecutionStatus.FAILED,
            traceId = runtimeContext.traceId,
            stepCount = runtimeContext.step + 1,
            finalPointer = pointer,
            message = message,
            validationIssues = validationIssues,
            traceEvents = traceCollector.snapshot(),
        )
    }

    private fun buildNodeStartDetails(node: FlowNode): Map<String, String> {
        return linkedMapOf<String, String>().apply {
            put("enabled", node.flags.enabled.toString())
            put("active", node.flags.active.toString())
            put("actionType", node.actionType?.raw ?: "-")
            put("params", compactParams(node.params))
        }
    }

    private fun buildNodeEndDetails(
        node: FlowNode,
        outcome: NodeOutcome,
        postDelayMs: Long,
        actionResult: ActionResult?,
    ): Map<String, String> {
        return linkedMapOf<String, String>().apply {
            put("nodeKind", node.kind.name)
            put("outcome", outcome.status.name)
            put("nextFlowId", outcome.next?.flowId ?: "-")
            put("nextNodeId", outcome.next?.nodeId ?: "-")
            put("postDelayMs", postDelayMs.toString())
            if (actionResult != null) {
                put("actionStatus", actionResult.status.name)
                put("actionErrorCode", actionResult.errorCode ?: "-")
                put("actionMessage", actionResult.message ?: "-")
                put(
                    "actionPayloadKeys",
                    if (actionResult.payload.isEmpty()) "-" else actionResult.payload.keys.sorted().joinToString(","),
                )
            }
        }
    }

    private fun compactParams(params: Map<String, Any?>): String {
        if (params.isEmpty()) {
            return "{}"
        }
        val raw = params
            .toSortedMap()
            .entries
            .joinToString(separator = ", ") { (key, value) ->
                "$key=${value?.toString()?.replace("\n", "\\n") ?: "null"}"
            }
        return if (raw.length > 360) {
            raw.take(357) + "..."
        } else {
            raw
        }
    }

    private fun inferErrorCode(message: String): String {
        val candidate = message.substringBefore(':').substringBefore('|').trim()
        return candidate.ifBlank { "runtime_failed" }
    }
}

private data class NodeOutcome(
    val status: NodeOutcomeStatus,
    val next: NodePointer? = null,
    val message: String? = null,
)

private enum class NodeOutcomeStatus {
    CONTINUE,
    COMPLETE,
    FAIL,
    STOP,
}

private data class FlowCallFrame(
    val caller: NodePointer,
    val returnPointer: NodePointer?,
)
