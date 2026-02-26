package com.ksxkq.cmm_clicker.core.migration

import com.ksxkq.cmm_clicker.core.model.NodeKind
import com.ksxkq.cmm_clicker.core.model.TaskBundle
import com.ksxkq.cmm_clicker.core.model.TaskFlow
import com.ksxkq.cmm_clicker.core.model.FlowNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BundleMigrationEngineTest {
    @Test
    fun `should return up to date when schema is latest`() {
        val engine = BundleMigrationEngine(steps = BundleMigrations.default(), latestVersion = 1)
        val result = engine.migrateToLatest(sampleBundle(schemaVersion = 1))

        assertEquals(BundleMigrationStatus.UP_TO_DATE, result.status)
        assertNotNull(result.bundle)
        assertTrue(result.logs.isEmpty())
    }

    @Test
    fun `should migrate from v0 to v1`() {
        val engine = BundleMigrationEngine(steps = BundleMigrations.default(), latestVersion = 1)
        val result = engine.migrateToLatest(sampleBundle(schemaVersion = 0))

        assertEquals(BundleMigrationStatus.MIGRATED, result.status)
        val bundle = requireNotNull(result.bundle)
        assertEquals(1, bundle.schemaVersion)
        assertEquals("v0_to_v1", bundle.metadata["migration"])
        assertEquals(1, result.logs.size)
        assertEquals("schema_v0_to_v1", result.logs.first().stepName)
    }

    @Test
    fun `should reject bundle when schema is newer than app supports`() {
        val engine = BundleMigrationEngine(steps = BundleMigrations.default(), latestVersion = 1)
        val result = engine.migrateToLatest(sampleBundle(schemaVersion = 2))

        assertEquals(BundleMigrationStatus.REJECTED_VERSION_TOO_NEW, result.status)
        assertNull(result.bundle)
        assertTrue(result.message?.contains("newer") == true)
    }

    @Test
    fun `should fail when migration chain is missing step`() {
        val engine = BundleMigrationEngine(steps = BundleMigrations.default(), latestVersion = 2)
        val result = engine.migrateToLatest(sampleBundle(schemaVersion = 0))

        assertEquals(BundleMigrationStatus.FAILED_MISSING_STEP, result.status)
        assertNull(result.bundle)
        assertEquals(1, result.logs.size)
        assertTrue(result.message?.contains("Missing migration step 1 -> 2") == true)
    }

    private fun sampleBundle(schemaVersion: Int): TaskBundle {
        return TaskBundle(
            bundleId = "bundle_test",
            name = "bundle test",
            schemaVersion = schemaVersion,
            entryFlowId = "main",
            flows = listOf(
                TaskFlow(
                    flowId = "main",
                    name = "main",
                    entryNodeId = "start",
                    nodes = listOf(
                        FlowNode(nodeId = "start", kind = NodeKind.START),
                    ),
                    edges = emptyList(),
                ),
            ),
        )
    }
}
