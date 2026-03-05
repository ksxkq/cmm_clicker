package com.ksxkq.cmm_clicker.accessibility

import com.ksxkq.cmm_clicker.core.runtime.RuntimeEngineOptions

internal const val RUN_ENGINE_MAX_STEPS = 200
internal const val RUN_ENGINE_PAUSE_POLL_INTERVAL_MS = 120L

internal fun buildRunRuntimeEngineOptions(
    isPaused: () -> Boolean,
): RuntimeEngineOptions {
    return RuntimeEngineOptions(
        dryRun = false,
        maxSteps = RUN_ENGINE_MAX_STEPS,
        stopOnValidationError = true,
        isPaused = isPaused,
        pausePollIntervalMs = RUN_ENGINE_PAUSE_POLL_INTERVAL_MS,
    )
}

