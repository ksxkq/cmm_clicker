package com.ksxkq.cmm_clicker.accessibility

import com.ksxkq.cmm_clicker.core.runtime.RuntimeTraceEvent

internal data class CurrentRunSessionState(
    val traceId: String = "",
    val taskId: String = "",
    val taskName: String = "",
    val status: String = "",
    val stepCount: Int = 0,
    val startedAtEpochMs: Long = 0L,
    val finishedAtEpochMs: Long = 0L,
    val message: String = "",
    val errorCode: String = "",
    val events: List<RuntimeTraceEvent> = emptyList(),
) {
    fun hasSession(): Boolean = startedAtEpochMs > 0L || events.isNotEmpty()

    fun toHistorySnapshot(
        runningPanelState: RunningPanelState,
        running: Boolean,
    ): TaskControlRunHistorySnapshot? {
        if (!hasSession()) {
            return null
        }
        val taskName = taskName.ifBlank { runningPanelState.taskName }
        val status = status.ifBlank {
            if (running) "RUNNING" else "-"
        }
        val message = message.ifBlank {
            runningPanelState.lastMessage
        }
        val errorCode = errorCode.ifBlank {
            runningPanelState.lastErrorCode
        }
        val stepCount = maxOf(stepCount, runningPanelState.stepCount)
        return TaskControlRunHistorySnapshot(
            traceId = traceId,
            taskId = taskId,
            taskName = taskName,
            status = status,
            stepCount = stepCount,
            startedAtEpochMs = startedAtEpochMs,
            finishedAtEpochMs = finishedAtEpochMs.takeIf { it > 0L },
            message = message,
            errorCode = errorCode,
            events = events,
        )
    }

    fun begin(
        taskId: String,
        taskName: String,
        startedAtEpochMs: Long = System.currentTimeMillis(),
    ): CurrentRunSessionState {
        return copy(
            traceId = "",
            taskId = taskId,
            taskName = taskName,
            status = "RUNNING",
            stepCount = 0,
            startedAtEpochMs = startedAtEpochMs,
            finishedAtEpochMs = 0L,
            message = "running",
            errorCode = "",
            events = emptyList(),
        )
    }

    fun reset(): CurrentRunSessionState = CurrentRunSessionState()

    fun updateFromTrace(event: RuntimeTraceEvent): CurrentRunSessionState {
        return copy(
            traceId = if (traceId.isBlank()) event.traceId else traceId,
            stepCount = maxOf(stepCount, event.step + 1),
            events = (events + event).takeLast(600),
        )
    }

    fun appendControlEvent(event: RuntimeTraceEvent): CurrentRunSessionState {
        return copy(events = (events + event).takeLast(600))
    }

    fun withTraceIdIfBlank(candidate: String): CurrentRunSessionState {
        if (traceId.isNotBlank() || candidate.isBlank()) {
            return this
        }
        return copy(traceId = candidate)
    }

    fun withMessage(message: String): CurrentRunSessionState = copy(message = message)

    fun finalize(
        status: String,
        message: String,
        errorCode: String,
        stepCount: Int,
        finishedAtEpochMs: Long = System.currentTimeMillis(),
    ): CurrentRunSessionState {
        val validFinishedAt = finishedAtEpochMs.coerceAtLeast(startedAtEpochMs)
        return copy(
            status = status,
            message = message,
            errorCode = errorCode,
            stepCount = maxOf(this.stepCount, stepCount),
            finishedAtEpochMs = validFinishedAt,
        )
    }
}
