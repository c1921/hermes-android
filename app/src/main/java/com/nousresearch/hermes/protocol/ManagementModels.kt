package com.nousresearch.hermes.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SkillInfo(
    val category: String? = null,
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

@Serializable
data class ActionResponse(
    val name: String,
    val ok: Boolean,
    val pid: Long,
)

@Serializable
data class ActionStatusResponse(
    @SerialName("exit_code") val exitCode: Int? = null,
    val lines: List<String> = emptyList(),
    val name: String,
    val pid: Long? = null,
    val running: Boolean,
)

@Serializable
data class EnvVarInfo(
    val advanced: Boolean = false,
    val category: String = "",
    @SerialName("channel_managed") val channelManaged: Boolean = false,
    val custom: Boolean = false,
    val description: String = "",
    @SerialName("is_password") val isPassword: Boolean = true,
    @SerialName("is_set") val isSet: Boolean = false,
    val provider: String = "",
    @SerialName("provider_label") val providerLabel: String = "",
    @SerialName("redacted_value") val redactedValue: String? = null,
    val tools: List<String> = emptyList(),
    val url: String? = null,
)

@Serializable
data class ProviderValidationResult(
    val ok: Boolean,
    val reachable: Boolean,
    val message: String = "",
    val models: List<String> = emptyList(),
)

@Serializable
data class ManagedFileEntry(
    val name: String,
    val path: String,
    @SerialName("is_directory") val isDirectory: Boolean,
    val size: Long? = null,
    val mtime: Double = 0.0,
    @SerialName("mime_type") val mimeType: String? = null,
)

@Serializable
data class ManagedFilesResponse(
    val root: String? = null,
    val path: String,
    val parent: String? = null,
    @SerialName("locked_root") val lockedRoot: String? = null,
    @SerialName("can_change_path") val canChangePath: Boolean = false,
    val entries: List<ManagedFileEntry> = emptyList(),
)

@Serializable
data class ManagedFileReadResponse(
    val name: String,
    val path: String,
    val size: Long,
    @SerialName("mime_type") val mimeType: String,
    @SerialName("data_url") val dataUrl: String,
    val root: String? = null,
    @SerialName("locked_root") val lockedRoot: String? = null,
    @SerialName("can_change_path") val canChangePath: Boolean = false,
)

@Serializable
data class SkillHubResult(
    val name: String,
    val description: String = "",
    val source: String = "",
    val identifier: String,
    @SerialName("trust_level") val trustLevel: String = "community",
    val repo: String? = null,
    val tags: List<String> = emptyList(),
)

@Serializable
data class SkillHubSourcesResponse(
    val sources: List<SkillHubSource> = emptyList(),
    @SerialName("index_available") val indexAvailable: Boolean = false,
    val featured: List<SkillHubResult> = emptyList(),
)

@Serializable
data class SkillHubSource(
    val id: String,
    val label: String,
    val available: Boolean? = null,
    @SerialName("rate_limited") val rateLimited: Boolean? = null,
    val searchable: Boolean? = null,
)

@Serializable
data class SkillHubSearchResponse(
    val results: List<SkillHubResult> = emptyList(),
    @SerialName("source_counts") val sourceCounts: Map<String, Int> = emptyMap(),
    @SerialName("timed_out") val timedOut: List<String> = emptyList(),
)

@Serializable
data class SkillHubPreview(
    val name: String,
    val description: String = "",
    val source: String = "",
    val identifier: String,
    @SerialName("trust_level") val trustLevel: String = "community",
    val repo: String? = null,
    val tags: List<String> = emptyList(),
    @SerialName("skill_md") val skillMarkdown: String = "",
    val files: List<String> = emptyList(),
)

@Serializable
data class SkillHubScanFinding(
    val severity: String,
    val category: String,
    val file: String,
    val line: Int? = null,
    val description: String,
)

@Serializable
data class SkillHubScanResult(
    val name: String,
    val identifier: String,
    val source: String = "",
    @SerialName("trust_level") val trustLevel: String = "community",
    val verdict: String,
    val summary: String = "",
    val policy: String,
    @SerialName("policy_reason") val policyReason: String? = null,
    val findings: List<SkillHubScanFinding> = emptyList(),
    @SerialName("severity_counts") val severityCounts: Map<String, Int> = emptyMap(),
)

@Serializable
data class McpServerSummary(
    val name: String,
    val transport: String,
    val command: String? = null,
    val args: List<String> = emptyList(),
    val url: String? = null,
    val auth: String? = null,
    val enabled: Boolean,
)

@Serializable
data class McpServersResponse(
    val servers: List<McpServerSummary> = emptyList(),
)

@Serializable
data class McpToolSummary(
    val name: String,
    val description: String = "",
)

@Serializable
data class McpServerTestResponse(
    val ok: Boolean,
    val error: String? = null,
    val tools: List<McpToolSummary> = emptyList(),
    val prompts: Int = 0,
    val resources: Int = 0,
)

@Serializable
data class McpServerToggleResponse(
    val ok: Boolean,
    val name: String,
    val enabled: Boolean,
)

@Serializable
data class McpCatalogEnvRequirement(
    val name: String,
    val prompt: String,
    val required: Boolean,
)

@Serializable
data class McpCatalogEntry(
    val name: String,
    val description: String = "",
    val source: String = "",
    val transport: String,
    @SerialName("auth_type") val authType: String = "none",
    @SerialName("required_env") val requiredEnv: List<McpCatalogEnvRequirement> = emptyList(),
    val command: String? = null,
    val args: List<String> = emptyList(),
    val url: String? = null,
    @SerialName("install_url") val installUrl: String? = null,
    @SerialName("install_ref") val installRef: String? = null,
    val bootstrap: List<String> = emptyList(),
    @SerialName("default_enabled") val defaultEnabled: List<String>? = null,
    @SerialName("post_install") val postInstall: String = "",
    @SerialName("needs_install") val needsInstall: Boolean = false,
    val installed: Boolean,
    val enabled: Boolean,
)

@Serializable
data class McpCatalogDiagnostic(
    val name: String,
    val kind: String,
    val message: String,
)

@Serializable
data class McpCatalogResponse(
    val entries: List<McpCatalogEntry> = emptyList(),
    val diagnostics: List<McpCatalogDiagnostic> = emptyList(),
)

@Serializable
data class McpReloadResponse(
    val status: String,
    val message: String? = null,
    @SerialName("turn_isolation") val turnIsolation: Boolean = false,
)

@Serializable
data class ContextUsageCategory(
    val id: String,
    val label: String,
    val color: String = "",
    val tokens: Long,
)

@Serializable
data class ContextBreakdown(
    val categories: List<ContextUsageCategory> = emptyList(),
    @SerialName("context_max") val contextMax: Long,
    @SerialName("context_percent") val contextPercent: Double,
    @SerialName("context_used") val contextUsed: Long,
    @SerialName("estimated_total") val estimatedTotal: Long,
    val model: String? = null,
)

@Serializable
data class AnalyticsDailyEntry(
    val day: String,
    @SerialName("input_tokens") val inputTokens: Long,
    @SerialName("output_tokens") val outputTokens: Long,
    @SerialName("cache_read_tokens") val cacheReadTokens: Long,
    @SerialName("reasoning_tokens") val reasoningTokens: Long,
    @SerialName("estimated_cost") val estimatedCost: Double,
    @SerialName("actual_cost") val actualCost: Double,
    val sessions: Long,
    @SerialName("api_calls") val apiCalls: Long,
)

@Serializable
data class AnalyticsModelEntry(
    val model: String,
    @SerialName("input_tokens") val inputTokens: Long,
    @SerialName("output_tokens") val outputTokens: Long,
    @SerialName("estimated_cost") val estimatedCost: Double,
    val sessions: Long,
    @SerialName("api_calls") val apiCalls: Long,
)

@Serializable
data class AnalyticsToolEntry(
    val tool: String,
    val count: Long,
    val percentage: Double,
)

@Serializable
data class AnalyticsSkillEntry(
    val skill: String,
    @SerialName("last_used_at") val lastUsedAt: Double? = null,
    @SerialName("manage_count") val manageCount: Long,
    val percentage: Double,
    @SerialName("total_count") val totalCount: Long,
    @SerialName("view_count") val viewCount: Long,
)

@Serializable
data class AnalyticsSkillsSummary(
    @SerialName("distinct_skills_used") val distinctSkillsUsed: Long,
    @SerialName("total_skill_actions") val totalSkillActions: Long,
    @SerialName("total_skill_edits") val totalSkillEdits: Long,
    @SerialName("total_skill_loads") val totalSkillLoads: Long,
)

@Serializable
data class AnalyticsSkills(
    val summary: AnalyticsSkillsSummary,
    @SerialName("top_skills") val topSkills: List<AnalyticsSkillEntry> = emptyList(),
)

@Serializable
data class AnalyticsTotals(
    @SerialName("total_actual_cost") val totalActualCost: Double,
    @SerialName("total_api_calls") val totalApiCalls: Long? = null,
    @SerialName("total_cache_read") val totalCacheRead: Long? = null,
    @SerialName("total_estimated_cost") val totalEstimatedCost: Double,
    @SerialName("total_input") val totalInput: Long? = null,
    @SerialName("total_output") val totalOutput: Long? = null,
    @SerialName("total_reasoning") val totalReasoning: Long? = null,
    @SerialName("total_sessions") val totalSessions: Long,
)

@Serializable
data class AnalyticsResponse(
    val daily: List<AnalyticsDailyEntry> = emptyList(),
    @SerialName("by_model") val byModel: List<AnalyticsModelEntry> = emptyList(),
    val totals: AnalyticsTotals,
    @SerialName("period_days") val periodDays: Int,
    val skills: AnalyticsSkills,
    val tools: List<AnalyticsToolEntry> = emptyList(),
)
