package com.ksxkq.cmm_clicker.accessibility

internal class WindowOpsExecutor<V, P>(
    private val addOp: (view: V, params: P) -> Unit,
    private val removeOp: (view: V) -> Unit,
    private val updateOp: (view: V, params: P) -> Unit,
    private val onWarn: (message: String, throwable: Throwable) -> Unit,
    private val onError: (message: String, throwable: Throwable) -> Unit,
) {
    fun tryAddView(
        view: V,
        params: P,
        reason: String,
    ): Boolean {
        return runOperation(
            message = "addView failed: $reason",
            level = LogLevel.ERROR,
        ) {
            addOp(view, params)
        }
    }

    fun tryRemoveView(
        view: V,
        reason: String,
    ): Boolean {
        return runOperation(
            message = "removeView failed: $reason",
            level = LogLevel.WARN,
        ) {
            removeOp(view)
        }
    }

    fun tryUpdateViewLayout(
        view: V,
        params: P,
        reason: String,
    ): Boolean {
        return runOperation(
            message = "updateViewLayout failed: $reason",
            level = LogLevel.ERROR,
        ) {
            updateOp(view, params)
        }
    }

    fun tryRestackView(
        view: V,
        params: P,
        reason: String,
    ): Boolean {
        val removed = runOperation(
            message = "restack remove failed: $reason",
            level = LogLevel.WARN,
        ) {
            removeOp(view)
        }
        if (!removed) {
            return false
        }
        return runOperation(
            message = "restack add failed: $reason",
            level = LogLevel.WARN,
        ) {
            addOp(view, params)
        }
    }

    private enum class LogLevel {
        WARN,
        ERROR,
    }

    private fun runOperation(
        message: String,
        level: LogLevel,
        operation: () -> Unit,
    ): Boolean {
        return runCatching(operation).fold(
            onSuccess = { true },
            onFailure = { throwable ->
                when (level) {
                    LogLevel.WARN -> onWarn(message, throwable)
                    LogLevel.ERROR -> onError(message, throwable)
                }
                false
            },
        )
    }
}

