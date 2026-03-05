package com.ksxkq.cmm_clicker.accessibility

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskControlPanelWindowOpsExecutorTest {

    @Test
    fun `tryAddView success delegates add operation`() {
        val calls = mutableListOf<String>()
        val executor = buildExecutor(
            add = { view, params -> calls += "add:$view:$params" },
        )

        val success = executor.tryAddView(
            view = "panel",
            params = "layout",
            reason = "open",
        )

        assertTrue(success)
        assertEquals(listOf("add:panel:layout"), calls)
    }

    @Test
    fun `tryUpdateViewLayout failure reports error`() {
        val errors = mutableListOf<String>()
        val executor = buildExecutor(
            update = { _, _ -> error("boom") },
            onError = { message, _ -> errors += message },
        )

        val success = executor.tryUpdateViewLayout(
            view = "panel",
            params = "layout",
            reason = "drag",
        )

        assertFalse(success)
        assertEquals(listOf("updateViewLayout failed: drag"), errors)
    }

    @Test
    fun `tryRestackView remove failure short circuits add and reports warn`() {
        val calls = mutableListOf<String>()
        val warns = mutableListOf<String>()
        val executor = buildExecutor(
            add = { view, params -> calls += "add:$view:$params" },
            remove = { _ -> error("remove_failed") },
            onWarn = { message, _ -> warns += message },
        )

        val success = executor.tryRestackView(
            view = "panel",
            params = "layout",
            reason = "restack",
        )

        assertFalse(success)
        assertTrue(calls.isEmpty())
        assertEquals(listOf("restack remove failed: restack"), warns)
    }
}

private fun buildExecutor(
    add: (String, String) -> Unit = { _, _ -> },
    remove: (String) -> Unit = { _ -> },
    update: (String, String) -> Unit = { _, _ -> },
    onWarn: (String, Throwable) -> Unit = { _, _ -> },
    onError: (String, Throwable) -> Unit = { _, _ -> },
): WindowOpsExecutor<String, String> {
    return WindowOpsExecutor(
        addOp = add,
        removeOp = remove,
        updateOp = update,
        onWarn = onWarn,
        onError = onError,
    )
}

