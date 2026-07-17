package com.nousresearch.hermes.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SkillInfo(
    val category: String = "general",
    val description: String = "",
    val enabled: Boolean,
    val name: String,
    val usage: Int? = null,
    val provenance: String? = null,
)

@Serializable
data class SkillToggleResult(
    val ok: Boolean,
    val name: String,
    val enabled: Boolean,
)

@Serializable
data class CronJobSchedule(
    val display: String? = null,
    val expr: String? = null,
    val kind: String? = null,
)

@Serializable
data class CronJob(
    val deliver: String? = null,
    val enabled: Boolean,
    val id: String,
    @SerialName("last_error") val lastError: String? = null,
    @SerialName("last_run_at") val lastRunAt: String? = null,
    val name: String? = null,
    @SerialName("next_run_at") val nextRunAt: String? = null,
    @SerialName("no_agent") val noAgent: Boolean = false,
    val prompt: String? = null,
    val schedule: CronJobSchedule? = null,
    @SerialName("schedule_display") val scheduleDisplay: String? = null,
    val script: String? = null,
    val state: String? = null,
)

@Serializable
data class CronJobCreatePayload(
    val deliver: String? = null,
    val name: String? = null,
    val prompt: String,
    val schedule: String,
)

@Serializable
data class CronJobUpdates(
    val deliver: String? = null,
    val enabled: Boolean? = null,
    val name: String? = null,
    val prompt: String? = null,
    val schedule: String? = null,
)

@Serializable
data class CronRunPage(
    val runs: List<StoredSession> = emptyList(),
    val limit: Int = runs.size,
)

@Serializable
data class ProfileInfo(
    @SerialName("has_env") val hasEnv: Boolean = false,
    @SerialName("is_default") val isDefault: Boolean = false,
    val model: String? = null,
    val name: String,
    val path: String = "",
    val provider: String? = null,
    @SerialName("skill_count") val skillCount: Int = 0,
)

@Serializable
data class ProfilesResponse(
    val profiles: List<ProfileInfo> = emptyList(),
)

@Serializable
data class ActiveProfileResponse(
    val active: String = "default",
    val current: String = "default",
)

@Serializable
data class ProfileCreatePayload(
    val name: String,
    @SerialName("clone_from") val cloneFrom: String? = null,
    @SerialName("clone_all") val cloneAll: Boolean = false,
    @SerialName("no_skills") val noSkills: Boolean = false,
)
