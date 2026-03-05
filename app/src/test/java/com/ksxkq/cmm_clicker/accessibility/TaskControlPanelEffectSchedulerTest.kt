package com.ksxkq.cmm_clicker.accessibility

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskControlPanelEffectSchedulerTest {

    @Test
    fun `schedule replaces pending effect with same key`() = runBlocking {
        val launcher = FakeEffectLauncher()
        val scheduler = KeyedEffectScheduler<String>(launcher = launcher)
        val executed = mutableListOf<String>()

        scheduler.schedule(key = "settings", delayMs = 100L) {
            executed += "first"
        }
        scheduler.schedule(key = "settings", delayMs = 100L) {
            executed += "second"
        }

        assertTrue(launcher.tasks.first().cancelled)
        launcher.runAll()

        assertEquals(listOf("second"), executed)
    }

    @Test
    fun `schedule keeps effects for different keys`() = runBlocking {
        val launcher = FakeEffectLauncher()
        val scheduler = KeyedEffectScheduler<String>(launcher = launcher)
        val executed = mutableListOf<String>()

        scheduler.schedule(key = "settings", delayMs = 100L) {
            executed += "settings"
        }
        scheduler.schedule(key = "panel", delayMs = 220L) {
            executed += "panel"
        }

        launcher.runAll()

        assertEquals(listOf("settings", "panel"), executed)
    }

    @Test
    fun `cancel and cancelAll stop pending effects`() = runBlocking {
        val launcher = FakeEffectLauncher()
        val scheduler = KeyedEffectScheduler<String>(launcher = launcher)
        val executed = mutableListOf<String>()

        scheduler.schedule(key = "settings", delayMs = 100L) {
            executed += "settings"
        }
        scheduler.schedule(key = "panel", delayMs = 220L) {
            executed += "panel"
        }
        scheduler.cancel("settings")
        scheduler.cancelAll()

        launcher.runAll()

        assertTrue(executed.isEmpty())
    }
}

private class FakeEffectLauncher : EffectLauncher {
    data class Task(
        val delayMs: Long,
        val block: suspend () -> Unit,
        var cancelled: Boolean = false,
        var consumed: Boolean = false,
    )

    val tasks = mutableListOf<Task>()

    override fun launch(
        delayMs: Long,
        block: suspend () -> Unit,
    ): CancellableEffectHandle {
        val task = Task(delayMs = delayMs, block = block)
        tasks += task
        return CancellableEffectHandle {
            task.cancelled = true
        }
    }

    suspend fun runAll() {
        tasks.forEach { task ->
            if (task.cancelled || task.consumed) {
                return@forEach
            }
            task.consumed = true
            task.block()
        }
    }
}

