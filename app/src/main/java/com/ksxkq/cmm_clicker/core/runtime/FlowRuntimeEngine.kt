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
        val validationIssues = validator.validate(bundle)
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
        val entryFlow = bundle.findFlow(bundle.entryFlowId)
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
            runtimeContext.currentPointer = pointer
            val flow = bundle.findFlow(pointer.flowId)
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

            traceCollector.add(
                RuntimeTraceEvent(
                    traceId = traceId,
                    step = runtimeContext.step,
                    flowId = flow.flowId,
                    nodeId = node.nodeId,
                    nodeKind = node.kind,
                    phase = RuntimeTracePhase.NODE_START,
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
                    traceCollector.add(
                        RuntimeTraceEvent(
                            traceId = traceId,
                            step = runtimeContext.step,
                            flowId = flow.flowId,
                            nodeId = node.nodeId,
                            nodeKind = node.kind,
                            phase = RuntimeTracePhase.NODE_END,
                            message = outcome.message,
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

    private fun evalBranch(node: FlowNode, runtimeContext: RuntimeContext): Boolean {
        val key = node.params["variableKey"] as? String ?: return false
        val value = runtimeContext.variables[key]
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
    ): RuntimeExecutionResult {
        val current = runtimeContext.currentPointer ?: pointer
        if (current != null) {
            traceCollector.add(
                RuntimeTraceEvent(
                    traceId = runtimeContext.traceId,
                    step = runtimeContext.step,
                    flowId = current.flowId,
                    nodeId = current.nodeId,
                    nodeKind = NodeKind.ACTION,
                    phase = RuntimeTracePhase.NODE_ERROR,
                    message = message,
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
