package com.ksxkq.cmm_clicker.core.migration

import com.ksxkq.cmm_clicker.core.model.BundleSchema
import com.ksxkq.cmm_clicker.core.model.TaskBundle

enum class BundleMigrationStatus {
    UP_TO_DATE,
    MIGRATED,
    REJECTED_VERSION_TOO_NEW,
    FAILED_MISSING_STEP,
}

data class BundleMigrationLog(
    val stepName: String,
    val fromVersion: Int,
    val toVersion: Int,
)

data class BundleMigrationResult(
    val status: BundleMigrationStatus,
    val bundle: TaskBundle?,
    val logs: List<BundleMigrationLog> = emptyList(),
    val message: String? = null,
)

interface BundleMigrationStep {
    val name: String
    val fromVersion: Int
    val toVersion: Int

    fun migrate(bundle: TaskBundle): TaskBundle
}

class BundleMigrationEngine(
    private val steps: List<BundleMigrationStep>,
    private val latestVersion: Int = BundleSchema.CURRENT_VERSION,
) {
    private val stepByFromVersion = steps.associateBy { it.fromVersion }

    fun migrateToLatest(bundle: TaskBundle): BundleMigrationResult {
        if (bundle.schemaVersion > latestVersion) {
            return BundleMigrationResult(
                status = BundleMigrationStatus.REJECTED_VERSION_TOO_NEW,
                bundle = null,
                message = "Bundle schemaVersion=${bundle.schemaVersion} is newer than supported=$latestVersion",
            )
        }
        if (bundle.schemaVersion == latestVersion) {
            return BundleMigrationResult(
                status = BundleMigrationStatus.UP_TO_DATE,
                bundle = bundle,
            )
        }

        var working = bundle
        val logs = mutableListOf<BundleMigrationLog>()
        while (working.schemaVersion < latestVersion) {
            val step = stepByFromVersion[working.schemaVersion]
            if (step == null) {
                return BundleMigrationResult(
                    status = BundleMigrationStatus.FAILED_MISSING_STEP,
                    bundle = null,
                    logs = logs.toList(),
                    message = "Missing migration step ${working.schemaVersion} -> ${working.schemaVersion + 1}",
                )
            }
            working = step.migrate(working).copy(schemaVersion = step.toVersion)
            logs += BundleMigrationLog(
                stepName = step.name,
                fromVersion = step.fromVersion,
                toVersion = step.toVersion,
            )
        }
        return BundleMigrationResult(
            status = BundleMigrationStatus.MIGRATED,
            bundle = working,
            logs = logs.toList(),
        )
    }
}

object BundleMigrations {
    fun default(): List<BundleMigrationStep> = listOf(
        SchemaV0ToV1Migration,
    )
}

object SchemaV0ToV1Migration : BundleMigrationStep {
    override val name: String = "schema_v0_to_v1"
    override val fromVersion: Int = 0
    override val toVersion: Int = 1

    override fun migrate(bundle: TaskBundle): TaskBundle {
        val metadata = bundle.metadata.toMutableMap()
        metadata.putIfAbsent("migration", "v0_to_v1")
        return bundle.copy(metadata = metadata)
    }
}
