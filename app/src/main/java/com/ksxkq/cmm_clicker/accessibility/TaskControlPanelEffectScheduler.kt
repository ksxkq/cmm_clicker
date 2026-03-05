package com.ksxkq.cmm_clicker.accessibility

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal enum class TaskControlPanelEffectKey {
    SETTINGS_SHEET_ENTER,
    SETTINGS_DISMISS,
    PANEL_DISMISS,
    RECORDING_SAVE_TO_START_CONFIRM,
    RUNNING_TRACE_UI_FLUSH,
}

internal fun interface CancellableEffectHandle {
    fun cancel()
}

internal fun interface EffectLauncher {
    fun launch(
        delayMs: Long,
        block: suspend () -> Unit,
    ): CancellableEffectHandle
}

internal class CoroutineEffectLauncher(
    private val scope: CoroutineScope,
) : EffectLauncher {
    override fun launch(
        delayMs: Long,
        block: suspend () -> Unit,
    ): CancellableEffectHandle {
        val job = scope.launch {
            delay(delayMs.coerceAtLeast(0L))
            block()
        }
        return CancellableEffectHandle { job.cancel() }
    }
}

internal class KeyedEffectScheduler<K>(
    private val launcher: EffectLauncher,
) {
    private val handles = linkedMapOf<K, CancellableEffectHandle>()

    fun schedule(
        key: K,
        delayMs: Long,
        block: suspend () -> Unit,
    ) {
        handles.remove(key)?.cancel()
        var currentHandle: CancellableEffectHandle? = null
        currentHandle = launcher.launch(delayMs) {
            try {
                block()
            } finally {
                val active = handles[key]
                if (active === currentHandle) {
                    handles.remove(key)
                }
            }
        }
        handles[key] = currentHandle
    }

    fun cancel(key: K) {
        handles.remove(key)?.cancel()
    }

    fun cancelAll() {
        val activeHandles = handles.values.toList()
        handles.clear()
        activeHandles.forEach { it.cancel() }
    }
}
