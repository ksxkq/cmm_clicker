package com.ksxkq.cmm_clicker.core.runtime

import com.ksxkq.cmm_clicker.core.actions.ActionResult
import com.ksxkq.cmm_clicker.core.model.GraphValidationIssue
import com.ksxkq.cmm_clicker.core.model.NodeKind
import com.ksxkq.cmm_clicker.core.model.NodePointer

data class RuntimeEngineOptions(
    val maxSteps: Int = 1000,
    val dryRun: Boolean = false,
    val stopOnValidationError: Boolean = true,
)

data class RuntimeContext(
    val traceId: String,
    val dryRun: Boolean,
    val variables: MutableMap<String, Any?>,
    var step: Int = 0,
    var currentPointer: NodePointer? = null,
    var lastActionResult: ActionResult? = null,
)

data class RuntimeExecutionResult(
    val status: RuntimeExecutionStatus,
    val traceId: String,
    val stepCount: Int,
    val finalPointer: NodePointer?,
    val message: String? = null,
    val validationIssues: List<GraphValidationIssue> = emptyList(),
    val traceEvents: List<RuntimeTraceEvent> = emptyList(),
)

enum class RuntimeExecutionStatus {
    COMPLETED,
    FAILED,
    STOPPED,
}

data class RuntimeTraceEvent(
    val traceId: String,
    val step: Int,
    val flowId: String,
    val nodeId: String,
    val nodeKind: NodeKind,
    val phase: RuntimeTracePhase,
    val message: String? = null,
    val timeMillis: Long = System.currentTimeMillis(),
)

enum class RuntimeTracePhase {
    NODE_START,
    NODE_END,
    NODE_ERROR,
}

interface RuntimeTraceCollector {
    fun add(event: RuntimeTraceEvent)
    fun snapshot(): List<RuntimeTraceEvent>
}

class InMemoryRuntimeTraceCollector : RuntimeTraceCollector {
    private val events = mutableListOf<RuntimeTraceEvent>()

    override fun add(event: RuntimeTraceEvent) {
        events += event
    }

    override fun snapshot(): List<RuntimeTraceEvent> = events.toList()
}
