package com.nousresearch.hermes.domain

import com.nousresearch.hermes.protocol.ActiveSubagent
import com.nousresearch.hermes.protocol.GatewayEvent
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull

enum class SubagentStatus { QUEUED, RUNNING, COMPLETED, FAILED, INTERRUPTED }

enum class SubagentStreamKind { PROGRESS, SUMMARY, THINKING, TOOL }

data class SubagentStreamEntry(
    val atMillis: Long,
    val kind: SubagentStreamKind,
    val text: String,
    val isError: Boolean = false,
)

data class SubagentProgress(
    val id: String,
    val parentId: String? = null,
    val goal: String = "Subagent",
    val sessionId: String? = null,
    val model: String? = null,
    val status: SubagentStatus = SubagentStatus.RUNNING,
    val taskCount: Int = 1,
    val taskIndex: Int = 0,
    val startedAtMillis: Long,
    val updatedAtMillis: Long,
    val durationSeconds: Double? = null,
    val costUsd: Double? = null,
    val inputTokens: Long? = null,
    val outputTokens: Long? = null,
    val toolCount: Int? = null,
    val filesRead: List<String> = emptyList(),
    val filesWritten: List<String> = emptyList(),
    val stream: List<SubagentStreamEntry> = emptyList(),
    val summary: String? = null,
    val currentTool: String? = null,
)

data class SubagentRow(val progress: SubagentProgress, val depth: Int)

object SubagentReducer {
    val eventTypes = setOf(
        "subagent.spawn_requested",
        "subagent.start",
        "subagent.thinking",
        "subagent.tool",
        "subagent.progress",
        "subagent.complete",
    )

    fun reduce(
        current: List<SubagentProgress>,
        event: GatewayEvent,
        nowMillis: Long = System.currentTimeMillis(),
    ): List<SubagentProgress> {
        if (event.type !in eventTypes) return current
        val payload = event.payload as? JsonObject ?: return current
        val id = payload.string("subagent_id").ifBlank {
            "${payload.string("parent_id").ifBlank { "root" }}:${payload.long("task_index") ?: 0}:${payload.string("goal")}"
        }
        if (id.isBlank()) return current
        val index = current.indexOfFirst { it.id == id }
        val previous = current.getOrNull(index)
        if (previous?.status?.isTerminal == true) return current
        val next = payload.toProgress(id, previous, event.type, nowMillis)
        val updated = if (index >= 0) current.toMutableList().apply { this[index] = next } else current + next
        if (updated.size <= MAX_AGENTS) return updated
        val active = updated.filterNot { it.status.isTerminal }
        return active + updated.filter { it.status.isTerminal }.takeLast((MAX_AGENTS - active.size).coerceAtLeast(0))
    }

    fun fromActive(
        active: ActiveSubagent,
        previous: SubagentProgress? = null,
        nowMillis: Long = System.currentTimeMillis(),
    ): SubagentProgress = SubagentProgress(
        id = active.id,
        parentId = active.parentId ?: previous?.parentId,
        goal = active.goal.ifBlank { previous?.goal ?: "Subagent" },
        sessionId = previous?.sessionId,
        model = active.model ?: previous?.model,
        status = active.status.toStatus(SubagentStatus.RUNNING),
        taskCount = previous?.taskCount ?: 1,
        taskIndex = previous?.taskIndex ?: 0,
        startedAtMillis = active.startedAt?.times(1_000)?.toLong() ?: previous?.startedAtMillis ?: nowMillis,
        updatedAtMillis = nowMillis,
        durationSeconds = previous?.durationSeconds,
        costUsd = previous?.costUsd,
        inputTokens = previous?.inputTokens,
        outputTokens = previous?.outputTokens,
        toolCount = active.toolCount.takeIf { it > 0 } ?: previous?.toolCount,
        filesRead = previous?.filesRead.orEmpty(),
        filesWritten = previous?.filesWritten.orEmpty(),
        stream = previous?.stream.orEmpty(),
        summary = previous?.summary,
        currentTool = previous?.currentTool,
    )

    fun rows(items: List<SubagentProgress>): List<SubagentRow> {
        val byParent = items.groupBy { it.parentId }
        val knownIds = items.mapTo(mutableSetOf()) { it.id }
        val roots = items.filter { it.parentId == null || it.parentId !in knownIds }
        val seen = mutableSetOf<String>()
        val result = mutableListOf<SubagentRow>()

        fun visit(item: SubagentProgress, depth: Int) {
            if (!seen.add(item.id)) return
            result += SubagentRow(item, depth)
            byParent[item.id].orEmpty().sortedWith(agentOrder).forEach { visit(it, depth + 1) }
        }

        roots.sortedWith(agentOrder).forEach { visit(it, 0) }
        items.filterNot { it.id in seen }.sortedWith(agentOrder).forEach { visit(it, 0) }
        return result
    }

    private fun JsonObject.toProgress(
        id: String,
        previous: SubagentProgress?,
        eventType: String,
        nowMillis: Long,
    ): SubagentProgress {
        val status = string("status").toStatus(
            when (eventType) {
                "subagent.spawn_requested" -> SubagentStatus.QUEUED
                "subagent.complete" -> SubagentStatus.COMPLETED
                else -> SubagentStatus.RUNNING
            },
        )
        val entries = streamEntries(status, eventType, nowMillis)
        val stream = entries.fold(previous?.stream.orEmpty(), ::appendStream)
        val tool = string("tool_name")
        return SubagentProgress(
            id = id,
            parentId = string("parent_id").takeIf(String::isNotBlank) ?: previous?.parentId,
            goal = string("goal").ifBlank { previous?.goal ?: "Subagent" },
            sessionId = string("child_session_id").takeIf(String::isNotBlank) ?: previous?.sessionId,
            model = string("model").takeIf(String::isNotBlank) ?: previous?.model,
            status = status,
            taskCount = long("task_count")?.toInt() ?: previous?.taskCount ?: 1,
            taskIndex = long("task_index")?.toInt() ?: previous?.taskIndex ?: 0,
            startedAtMillis = double("started_at")?.times(1_000)?.toLong() ?: previous?.startedAtMillis ?: nowMillis,
            updatedAtMillis = nowMillis,
            durationSeconds = double("duration_seconds") ?: previous?.durationSeconds,
            costUsd = double("cost_usd") ?: previous?.costUsd,
            inputTokens = long("input_tokens") ?: previous?.inputTokens,
            outputTokens = long("output_tokens") ?: previous?.outputTokens,
            toolCount = long("tool_count")?.toInt() ?: previous?.toolCount,
            filesRead = stringList("files_read").ifEmpty { previous?.filesRead.orEmpty() },
            filesWritten = stringList("files_written").ifEmpty { previous?.filesWritten.orEmpty() },
            stream = stream,
            summary = string("summary").takeIf(String::isNotBlank) ?: previous?.summary,
            currentTool = if (status.isTerminal) null else tool.takeIf(String::isNotBlank) ?: previous?.currentTool,
        )
    }

    private fun JsonObject.streamEntries(
        status: SubagentStatus,
        eventType: String,
        nowMillis: Long,
    ): List<SubagentStreamEntry> = buildList {
        val isError = boolean("error") || status == SubagentStatus.FAILED
        (this@streamEntries["output_tail"] as? JsonArray).orEmpty().forEach { raw ->
            val tail = raw as? JsonObject ?: return@forEach
            val tool = tail.string("tool")
            val preview = compact(tail.string("preview"), TOOL_PREVIEW_MAX)
            val text = if (tool.isBlank()) preview else formatTool(tool, preview)
            if (text.isNotBlank()) add(
                SubagentStreamEntry(nowMillis, if (tool.isBlank()) SubagentStreamKind.PROGRESS else SubagentStreamKind.TOOL, text, tail.boolean("is_error")),
            )
        }
        val tool = string("tool_name")
        val preview = string("tool_preview").ifBlank { string("text") }
        if (tool.isNotBlank()) add(SubagentStreamEntry(nowMillis, SubagentStreamKind.TOOL, formatTool(tool, preview), isError))
        val text = compact(string("text").ifBlank { preview })
        if (eventType == "subagent.progress" && text.isNotBlank()) add(SubagentStreamEntry(nowMillis, SubagentStreamKind.PROGRESS, text, isError))
        if (eventType == "subagent.thinking" && text.isNotBlank()) add(SubagentStreamEntry(nowMillis, SubagentStreamKind.THINKING, text))
        val summary = compact(string("summary").ifBlank { string("text") })
        if (status.isTerminal && summary.isNotBlank()) add(SubagentStreamEntry(nowMillis, SubagentStreamKind.SUMMARY, summary, isError))
    }

    private fun appendStream(
        stream: List<SubagentStreamEntry>,
        entry: SubagentStreamEntry,
    ): List<SubagentStreamEntry> {
        val last = stream.lastOrNull()
        if (last?.kind == entry.kind && last.text == entry.text && last.isError == entry.isError) return stream
        return (stream + entry).takeLast(MAX_STREAM)
    }

    private fun JsonObject.string(key: String): String = (this[key] as? JsonPrimitive)?.contentOrNull.orEmpty()
    private fun JsonObject.long(key: String): Long? = (this[key] as? JsonPrimitive)?.contentOrNull?.toLongOrNull()
    private fun JsonObject.double(key: String): Double? = (this[key] as? JsonPrimitive)?.doubleOrNull
    private fun JsonObject.boolean(key: String): Boolean = (this[key] as? JsonPrimitive)?.booleanOrNull == true
    private fun JsonObject.stringList(key: String): List<String> = (this[key] as? JsonArray).orEmpty().mapNotNull {
        (it as? JsonPrimitive)?.contentOrNull
    }

    private fun String.toStatus(default: SubagentStatus): SubagentStatus = when (lowercase()) {
        "queued" -> SubagentStatus.QUEUED
        "completed" -> SubagentStatus.COMPLETED
        "failed", "error" -> SubagentStatus.FAILED
        "interrupted" -> SubagentStatus.INTERRUPTED
        "running" -> SubagentStatus.RUNNING
        else -> default
    }

    private fun formatTool(name: String, preview: String): String {
        val label = name.split('_').filter(String::isNotBlank).joinToString(" ") { part ->
            part.replaceFirstChar { it.uppercase() }
        }.ifBlank { name }
        val snippet = compact(preview, TOOL_PREVIEW_MAX)
        return if (snippet.isBlank()) label else "$label(\"$snippet\")"
    }

    private fun compact(text: String, max: Int = PREVIEW_MAX): String {
        val line = text.replace(Regex("\\s+"), " ").trim()
        return if (line.length <= max) line else "${line.take(max - 1)}…"
    }

    private val agentOrder = compareBy<SubagentProgress>({ it.startedAtMillis }, { it.taskIndex }, { it.goal })
    private val SubagentStatus.isTerminal: Boolean
        get() = this == SubagentStatus.COMPLETED || this == SubagentStatus.FAILED || this == SubagentStatus.INTERRUPTED

    private const val MAX_AGENTS = 100
    private const val MAX_STREAM = 24
    private const val PREVIEW_MAX = 220
    private const val TOOL_PREVIEW_MAX = 96
}
