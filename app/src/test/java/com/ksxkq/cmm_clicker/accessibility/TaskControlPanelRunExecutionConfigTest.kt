package com.ksxkq.cmm_clicker.accessibility

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskControlPanelRunExecutionConfigTest {

    @Test
    fun `buildRunRuntimeEngineOptions returns expected defaults`() {
        val options = buildRunRuntimeEngineOptions(
            isPaused = { false },
        )

        assertFalse(options.dryRun)
        assertEquals(RUN_ENGINE_MAX_STEPS, options.maxSteps)
        assertTrue(options.stopOnValidationError)
        assertEquals(RUN_ENGINE_PAUSE_POLL_INTERVAL_MS, options.pausePollIntervalMs)
    }

    @Test
    fun `buildRunRuntimeEngineOptions keeps pause callback`() {
        var paused = false
        val options = buildRunRuntimeEngineOptions(
            isPaused = { paused },
        )

        assertFalse(options.isPaused?.invoke() ?: true)
        paused = true
        assertTrue(options.isPaused?.invoke() ?: false)
    }
}

