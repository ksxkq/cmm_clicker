package com.ksxkq.cmm_clicker.feature.task

import android.content.Context
import com.ksxkq.cmm_clicker.core.model.ActionType
import com.ksxkq.cmm_clicker.core.model.BundleSchema
import com.ksxkq.cmm_clicker.core.model.EdgeConditionType
import com.ksxkq.cmm_clicker.core.model.FlowEdge
import com.ksxkq.cmm_clicker.core.model.FlowNode
import com.ksxkq.cmm_clicker.core.model.NodeFlags
import com.ksxkq.cmm_clicker.core.model.NodeKind
import com.ksxkq.cmm_clicker.core.model.TaskBundle
import com.ksxkq.cmm_clicker.core.model.TaskFlow
import java.util.UUID
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject

data class TaskRecord(
    val taskId: String,
    val name: String,
    val bundle: TaskBundle,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val lastRunAtEpochMs: Long? = null,
    val lastRunStatus: String? = null,
    val lastRunSummary: String? = null,
)

interface TaskRepository {
    suspend fun listTasks(): List<TaskRecord>

    suspend fun getTask(taskId: String): TaskRecord?

    suspend fun createTask(name: String, withTemplate: Boolean = true): TaskRecord

    suspend fun renameTask(taskId: String, name: String): TaskRecord?

    suspend fun duplicateTask(taskId: String): TaskRecord?

    suspend fun deleteTask(taskId: String): Boolean

    suspend fun updateTaskBundle(taskId: String, bundle: TaskBundle): TaskRecord?

    suspend fun updateTaskRunInfo(taskId: String, status: String, summary: String): TaskRecord?
}

class LocalFileTaskRepository(
    private val context: Context,
) : TaskRepository {
    private val mutex = Mutex()
    private val fileName = "tasks_v1.json"

    override suspend fun listTasks(): List<TaskRecord> = mutex.withLock {
        val tasks = loadTasksLocked()
        if (tasks.isEmpty()) {
            val seeded = createTaskLocked("示例任务", withTemplate = true)
            return listOf(seeded)
        }
        tasks.sortedByDescending { it.updatedAtEpochMs }
    }

    override suspend fun getTask(taskId: String): TaskRecord? = mutex.withLock {
        loadTasksLocked().firstOrNull { it.taskId == taskId }
    }

    override suspend fun createTask(name: String, withTemplate: Boolean): TaskRecord = mutex.withLock {
        createTaskLocked(name = name, withTemplate = withTemplate)
    }

    override suspend fun renameTask(taskId: String, name: String): TaskRecord? = mutex.withLock {
        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) {
            return null
        }
        val tasks = loadTasksLocked().toMutableList()
        val index = tasks.indexOfFirst { it.taskId == taskId }
        if (index < 0) {
            return null
        }
        val current = tasks[index]
        val updated = current.copy(
            name = trimmedName,
            bundle = current.bundle.copy(name = trimmedName),
            updatedAtEpochMs = now(),
        )
        tasks[index] = updated
        saveTasksLocked(tasks)
        updated
    }

    override suspend fun duplicateTask(taskId: String): TaskRecord? = mutex.withLock {
        val tasks = loadTasksLocked().toMutableList()
        val source = tasks.firstOrNull { it.taskId == taskId } ?: return null
        val newTaskId = nextTaskId()
        val timestamp = now()
        val duplicated = source.copy(
            taskId = newTaskId,
            name = "${source.name} 副本",
            bundle = source.bundle.copy(
                bundleId = newTaskId,
                name = "${source.name} 副本",
            ),
            createdAtEpochMs = timestamp,
            updatedAtEpochMs = timestamp,
            lastRunAtEpochMs = null,
            lastRunStatus = null,
            lastRunSummary = null,
        )
        tasks += duplicated
        saveTasksLocked(tasks)
        duplicated
    }

    override suspend fun deleteTask(taskId: String): Boolean = mutex.withLock {
        val tasks = loadTasksLocked().toMutableList()
        val removed = tasks.removeAll { it.taskId == taskId }
        if (removed) {
            saveTasksLocked(tasks)
        }
        removed
    }

    override suspend fun updateTaskBundle(taskId: String, bundle: TaskBundle): TaskRecord? = mutex.withLock {
        val tasks = loadTasksLocked().toMutableList()
        val index = tasks.indexOfFirst { it.taskId == taskId }
        if (index < 0) {
            return null
        }
        val current = tasks[index]
        val updated = current.copy(
            bundle = bundle.copy(
                bundleId = taskId,
                name = current.name,
            ),
            updatedAtEpochMs = now(),
        )
        tasks[index] = updated
        saveTasksLocked(tasks)
        updated
    }

    override suspend fun updateTaskRunInfo(taskId: String, status: String, summary: String): TaskRecord? = mutex.withLock {
        val tasks = loadTasksLocked().toMutableList()
        val index = tasks.indexOfFirst { it.taskId == taskId }
        if (index < 0) {
            return null
        }
        val updated = tasks[index].copy(
            lastRunAtEpochMs = now(),
            lastRunStatus = status,
            lastRunSummary = summary,
            updatedAtEpochMs = now(),
        )
        tasks[index] = updated
        saveTasksLocked(tasks)
        updated
    }

    private fun createTaskLocked(name: String, withTemplate: Boolean): TaskRecord {
        val taskName = name.trim().ifEmpty { "未命名任务" }
        val tasks = loadTasksLocked().toMutableList()
        val taskId = nextTaskId()
        val timestamp = now()
        val bundle = if (withTemplate) {
            createTaskTemplateBundle(taskId = taskId, name = taskName)
        } else {
            createEmptyTaskBundle(taskId = taskId, name = taskName)
        }
        val task = TaskRecord(
            taskId = taskId,
            name = taskName,
            bundle = bundle,
            createdAtEpochMs = timestamp,
            updatedAtEpochMs = timestamp,
        )
        tasks += task
        saveTasksLocked(tasks)
        return task
    }

    private fun loadTasksLocked(): List<TaskRecord> {
        val file = context.getFileStreamPath(fileName)
        if (!file.exists()) {
            return emptyList()
        }
        val raw = file.readText()
        if (raw.isBlank()) {
            return emptyList()
        }
        val root = JSONObject(raw)
        val tasksArray = root.optJSONArray("tasks") ?: JSONArray()
        val result = mutableListOf<TaskRecord>()
        for (i in 0 until tasksArray.length()) {
            val obj = tasksArray.optJSONObject(i) ?: continue
            decodeTaskRecord(obj)?.let { result += it }
        }
        return result
    }

    private fun saveTasksLocked(tasks: List<TaskRecord>) {
        val root = JSONObject()
            .put("version", 1)
            .put(
                "tasks",
                JSONArray().apply {
                    tasks.forEach { put(encodeTaskRecord(it)) }
                },
            )
        val file = context.getFileStreamPath(fileName)
        file.parentFile?.mkdirs()
        file.writeText(root.toString())
    }

    private fun decodeTaskRecord(obj: JSONObject): TaskRecord? {
        val taskId = obj.optString("taskId")
        val name = obj.optString("name")
        if (taskId.isBlank() || name.isBlank()) {
            return null
        }
        val bundleObj = obj.optJSONObject("bundle") ?: return null
        val bundle = decodeTaskBundle(bundleObj) ?: return null
        return TaskRecord(
            taskId = taskId,
            name = name,
            bundle = bundle.copy(
                bundleId = taskId,
                name = name,
            ),
            createdAtEpochMs = obj.optLong("createdAtEpochMs", now()),
            updatedAtEpochMs = obj.optLong("updatedAtEpochMs", now()),
            lastRunAtEpochMs = obj.optLongOrNull("lastRunAtEpochMs"),
            lastRunStatus = obj.optStringOrNull("lastRunStatus"),
            lastRunSummary = obj.optStringOrNull("lastRunSummary"),
        )
    }

    private fun encodeTaskRecord(record: TaskRecord): JSONObject {
        return JSONObject()
            .put("taskId", record.taskId)
            .put("name", record.name)
            .put("bundle", encodeTaskBundle(record.bundle))
            .put("createdAtEpochMs", record.createdAtEpochMs)
            .put("updatedAtEpochMs", record.updatedAtEpochMs)
            .put("lastRunAtEpochMs", record.lastRunAtEpochMs)
            .put("lastRunStatus", record.lastRunStatus)
            .put("lastRunSummary", record.lastRunSummary)
    }

    private fun decodeTaskBundle(obj: JSONObject): TaskBundle? {
        val bundleId = obj.optString("bundleId")
        val name = obj.optString("name")
        val entryFlowId = obj.optString("entryFlowId")
        val flowsArray = obj.optJSONArray("flows") ?: return null
        if (bundleId.isBlank() || name.isBlank() || entryFlowId.isBlank()) {
            return null
        }
        val flows = mutableListOf<TaskFlow>()
        for (i in 0 until flowsArray.length()) {
            val flowObj = flowsArray.optJSONObject(i) ?: continue
            decodeTaskFlow(flowObj)?.let { flows += it }
        }
        val metadata = decodeStringMap(obj.optJSONObject("metadata"))
        return TaskBundle(
            bundleId = bundleId,
            name = name,
            schemaVersion = obj.optInt("schemaVersion", BundleSchema.CURRENT_VERSION),
            entryFlowId = entryFlowId,
            flows = flows,
            metadata = metadata,
        )
    }

    private fun encodeTaskBundle(bundle: TaskBundle): JSONObject {
        return JSONObject()
            .put("bundleId", bundle.bundleId)
            .put("name", bundle.name)
            .put("schemaVersion", bundle.schemaVersion)
            .put("entryFlowId", bundle.entryFlowId)
            .put(
                "flows",
                JSONArray().apply {
                    bundle.flows.forEach { put(encodeTaskFlow(it)) }
                },
            )
            .put("metadata", encodeStringMap(bundle.metadata))
    }

    private fun decodeTaskFlow(obj: JSONObject): TaskFlow? {
        val flowId = obj.optString("flowId")
        val name = obj.optString("name")
        val entryNodeId = obj.optString("entryNodeId")
        if (flowId.isBlank() || name.isBlank() || entryNodeId.isBlank()) {
            return null
        }
        val nodes = mutableListOf<FlowNode>()
        val nodesArray = obj.optJSONArray("nodes") ?: JSONArray()
        for (i in 0 until nodesArray.length()) {
            val nodeObj = nodesArray.optJSONObject(i) ?: continue
            decodeFlowNode(nodeObj)?.let { nodes += it }
        }
        val edges = mutableListOf<FlowEdge>()
        val edgesArray = obj.optJSONArray("edges") ?: JSONArray()
        for (i in 0 until edgesArray.length()) {
            val edgeObj = edgesArray.optJSONObject(i) ?: continue
            decodeFlowEdge(edgeObj)?.let { edges += it }
        }
        return TaskFlow(
            flowId = flowId,
            name = name,
            entryNodeId = entryNodeId,
            nodes = nodes,
            edges = edges,
        )
    }

    private fun encodeTaskFlow(flow: TaskFlow): JSONObject {
        return JSONObject()
            .put("flowId", flow.flowId)
            .put("name", flow.name)
            .put("entryNodeId", flow.entryNodeId)
            .put(
                "nodes",
                JSONArray().apply {
                    flow.nodes.forEach { put(encodeFlowNode(it)) }
                },
            )
            .put(
                "edges",
                JSONArray().apply {
                    flow.edges.forEach { put(encodeFlowEdge(it)) }
                },
            )
    }

    private fun decodeFlowNode(obj: JSONObject): FlowNode? {
        val nodeId = obj.optString("nodeId")
        val kindRaw = obj.optString("kind")
        if (nodeId.isBlank() || kindRaw.isBlank()) {
            return null
        }
        val kind = NodeKind.entries.firstOrNull { it.name == kindRaw } ?: return null
        val actionType = obj.optString("actionType")
            .takeIf { it.isNotBlank() }
            ?.let { raw -> ActionType.entries.firstOrNull { it.raw == raw } }
        val params = decodeAnyMap(obj.optJSONObject("params"))
        val flagsObj = obj.optJSONObject("flags")
        return FlowNode(
            nodeId = nodeId,
            kind = kind,
            actionType = actionType,
            pluginId = obj.optStringOrNull("pluginId"),
            params = params,
            flags = NodeFlags(
                enabled = flagsObj?.optBoolean("enabled", true) ?: true,
                active = flagsObj?.optBoolean("active", true) ?: true,
            ),
        )
    }

    private fun encodeFlowNode(node: FlowNode): JSONObject {
        return JSONObject()
            .put("nodeId", node.nodeId)
            .put("kind", node.kind.name)
            .put("actionType", node.actionType?.raw)
            .put("pluginId", node.pluginId)
            .put("params", encodeAnyMap(node.params))
            .put(
                "flags",
                JSONObject()
                    .put("enabled", node.flags.enabled)
                    .put("active", node.flags.active),
            )
    }

    private fun decodeFlowEdge(obj: JSONObject): FlowEdge? {
        val edgeId = obj.optString("edgeId")
        val fromNodeId = obj.optString("fromNodeId")
        val toNodeId = obj.optString("toNodeId")
        if (edgeId.isBlank() || fromNodeId.isBlank() || toNodeId.isBlank()) {
            return null
        }
        val conditionTypeRaw = obj.optString("conditionType")
        val conditionType = EdgeConditionType.entries.firstOrNull { it.name == conditionTypeRaw }
            ?: EdgeConditionType.ALWAYS
        return FlowEdge(
            edgeId = edgeId,
            fromNodeId = fromNodeId,
            toNodeId = toNodeId,
            conditionType = conditionType,
            conditionKey = obj.optStringOrNull("conditionKey"),
            priority = obj.optInt("priority", 0),
        )
    }

    private fun encodeFlowEdge(edge: FlowEdge): JSONObject {
        return JSONObject()
            .put("edgeId", edge.edgeId)
            .put("fromNodeId", edge.fromNodeId)
            .put("toNodeId", edge.toNodeId)
            .put("conditionType", edge.conditionType.name)
            .put("conditionKey", edge.conditionKey)
            .put("priority", edge.priority)
    }

    private fun decodeAnyMap(obj: JSONObject?): Map<String, Any?> {
        if (obj == null) {
            return emptyMap()
        }
        val keys = obj.keys()
        val result = linkedMapOf<String, Any?>()
        while (keys.hasNext()) {
            val key = keys.next()
            result[key] = obj.opt(key)?.takeUnless { it == JSONObject.NULL }?.toString()
        }
        return result
    }

    private fun encodeAnyMap(map: Map<String, Any?>): JSONObject {
        return JSONObject().apply {
            map.forEach { (key, value) ->
                if (value == null) {
                    put(key, JSONObject.NULL)
                } else {
                    put(key, value.toString())
                }
            }
        }
    }

    private fun decodeStringMap(obj: JSONObject?): Map<String, String> {
        if (obj == null) {
            return emptyMap()
        }
        val keys = obj.keys()
        val result = linkedMapOf<String, String>()
        while (keys.hasNext()) {
            val key = keys.next()
            val value = obj.optString(key)
            if (value.isNotBlank()) {
                result[key] = value
            }
        }
        return result
    }

    private fun encodeStringMap(map: Map<String, String>): JSONObject {
        return JSONObject().apply {
            map.forEach { (key, value) ->
                put(key, value)
            }
        }
    }

    private fun createTaskTemplateBundle(taskId: String, name: String): TaskBundle {
        val flow = TaskFlow(
            flowId = "main",
            name = "Main Flow",
            entryNodeId = "start",
            nodes = listOf(
                FlowNode(nodeId = "start", kind = NodeKind.START),
                FlowNode(
                    nodeId = "click_1",
                    kind = NodeKind.ACTION,
                    actionType = ActionType.CLICK,
                    pluginId = "builtin.basic_gesture",
                    params = mapOf(
                        "x" to "0.5",
                        "y" to "0.5",
                        "durationMs" to "60",
                    ),
                ),
                FlowNode(nodeId = "end", kind = NodeKind.END),
            ),
            edges = listOf(
                FlowEdge(edgeId = "edge_1", fromNodeId = "start", toNodeId = "click_1"),
                FlowEdge(edgeId = "edge_2", fromNodeId = "click_1", toNodeId = "end"),
            ),
        )
        return TaskBundle(
            bundleId = taskId,
            name = name,
            schemaVersion = BundleSchema.CURRENT_VERSION,
            entryFlowId = "main",
            flows = listOf(flow),
        )
    }

    private fun createEmptyTaskBundle(taskId: String, name: String): TaskBundle {
        val flow = TaskFlow(
            flowId = "main",
            name = "Main Flow",
            entryNodeId = "start",
            nodes = listOf(
                FlowNode(nodeId = "start", kind = NodeKind.START),
                FlowNode(nodeId = "end", kind = NodeKind.END),
            ),
            edges = listOf(
                FlowEdge(edgeId = "edge_1", fromNodeId = "start", toNodeId = "end"),
            ),
        )
        return TaskBundle(
            bundleId = taskId,
            name = name,
            schemaVersion = BundleSchema.CURRENT_VERSION,
            entryFlowId = "main",
            flows = listOf(flow),
        )
    }

    private fun JSONObject.optStringOrNull(key: String): String? {
        val value = optString(key)
        return value.takeIf { it.isNotBlank() }
    }

    private fun JSONObject.optLongOrNull(key: String): Long? {
        if (!has(key) || isNull(key)) {
            return null
        }
        return optLong(key)
    }

    private fun nextTaskId(): String {
        return "task_${UUID.randomUUID().toString().replace("-", "").take(12)}"
    }

    private fun now(): Long = System.currentTimeMillis()
}
