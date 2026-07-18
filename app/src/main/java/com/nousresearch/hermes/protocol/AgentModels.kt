package com.nousresearch.hermes.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class ActiveSubagent(
    @SerialName("subagent_id") val id: String = "",
    @SerialName("parent_id") val parentId: String? = null,
    val depth: Int = 0,
    val goal: String = "",
    val model: String? = null,
    @SerialName("started_at") val startedAt: Double? = null,
    @SerialName("tool_count") val toolCount: Int = 0,
    val status: String = "running",
)

@Serializable
data class DelegationStatusResponse(
    val active: List<ActiveSubagent> = emptyList(),
    val paused: Boolean = false,
    @SerialName("max_spawn_depth") val maxSpawnDepth: Int = 0,
    @SerialName("max_concurrent_children") val maxConcurrentChildren: Int = 0,
)

@Serializable
data class DelegationPauseResponse(val paused: Boolean = false)

@Serializable
data class SubagentInterruptResponse(
    val found: Boolean = false,
    @SerialName("subagent_id") val subagentId: String = "",
)

@Serializable
data class SpawnTreeListEntry(
    val path: String,
    @SerialName("session_id") val sessionId: String? = null,
    @SerialName("started_at") val startedAt: Double? = null,
    @SerialName("finished_at") val finishedAt: Double? = null,
    val label: String = "",
    val count: Int = 0,
)

@Serializable
data class SpawnTreeListResponse(
    val entries: List<SpawnTreeListEntry> = emptyList(),
)

@Serializable
data class SpawnTreeSnapshot(
    @SerialName("session_id") val sessionId: String? = null,
    @SerialName("started_at") val startedAt: Double? = null,
    @SerialName("finished_at") val finishedAt: Double? = null,
    val label: String = "",
    val subagents: List<JsonObject> = emptyList(),
)

@Serializable
data class BackgroundProcessListResponse(val processes: List<BackgroundProcess> = emptyList())

@Serializable
data class BackgroundProcess(
    @SerialName("session_id") val id: String = "",
    val command: String = "",
    val cwd: String? = null,
    val pid: Long? = null,
    @SerialName("started_at") val startedAt: String? = null,
    @SerialName("uptime_seconds") val uptimeSeconds: Long = 0,
    val status: String = "running",
    @SerialName("exit_code") val exitCode: Int? = null,
    @SerialName("output_preview") val outputPreview: String = "",
    @SerialName("output_tail") val outputTail: String = "",
    val detached: Boolean = false,
)

@Serializable
data class BackgroundProcessKillResponse(
    val status: String = "",
    val error: String? = null,
)
