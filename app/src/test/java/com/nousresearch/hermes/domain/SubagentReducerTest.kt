package com.nousresearch.hermes.domain

import com.nousresearch.hermes.protocol.ActiveSubagent
import com.nousresearch.hermes.protocol.BackgroundProcessListResponse
import com.nousresearch.hermes.protocol.DelegationStatusResponse
import com.nousresearch.hermes.protocol.GatewayEvent
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SubagentReducerTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `live events build a stable tree and preserve terminal detail`() {
        var items = SubagentReducer.reduce(emptyList(), event("subagent.start", "parent", null, "Coordinate QA"), 1_000)
        items = SubagentReducer.reduce(items, event("subagent.start", "child", "parent", "Run device tests"), 2_000)
        items = SubagentReducer.reduce(
            items,
            GatewayEvent(
                "subagent.tool",
                "runtime-1",
                buildJsonObject {
                    put("subagent_id", "child")
                    put("tool_name", "view_image")
                    put("tool_preview", "/tmp/test.png")
                },
            ),
            3_000,
        )
        items = SubagentReducer.reduce(
            items,
            GatewayEvent(
                "subagent.complete",
                "runtime-1",
                buildJsonObject {
                    put("subagent_id", "child")
                    put("status", "completed")
                    put("summary", "Samsung QA passed")
                    put("files_written", buildJsonArray { add("report.txt") })
                },
            ),
            4_000,
        )
        val settled = SubagentReducer.reduce(items, event("subagent.progress", "child", "parent", "ignored"), 5_000)

        val child = settled.single { it.id == "child" }
        assertEquals(SubagentStatus.COMPLETED, child.status)
        assertNull(child.currentTool)
        assertEquals(listOf("report.txt"), child.filesWritten)
        assertTrue(child.stream.any { it.kind == SubagentStreamKind.TOOL && "View Image" in it.text })
        assertEquals(listOf(0, 1), SubagentReducer.rows(settled).map(SubagentRow::depth))
    }

    @Test
    fun `active snapshot keeps richer streamed fields`() {
        val previous = SubagentProgress(
            id = "agent-7",
            goal = "Inspect upstream",
            sessionId = "stored-child",
            status = SubagentStatus.RUNNING,
            startedAtMillis = 1_000,
            updatedAtMillis = 2_000,
            stream = listOf(SubagentStreamEntry(2_000, SubagentStreamKind.PROGRESS, "Reading source")),
        )

        val current = SubagentReducer.fromActive(
            ActiveSubagent(id = "agent-7", goal = "Inspect upstream", toolCount = 4),
            previous,
            nowMillis = 3_000,
        )

        assertEquals("stored-child", current.sessionId)
        assertEquals(4, current.toolCount)
        assertEquals(previous.stream, current.stream)
    }

    @Test
    fun `delegation and process snapshots follow the pinned gateway contract`() {
        val delegation = json.decodeFromString<DelegationStatusResponse>(
            """{"active":[{"subagent_id":"agent-7","parent_id":null,"depth":1,"goal":"QA","model":"hermes","started_at":10.5,"tool_count":3,"status":"running"}],"paused":true,"max_spawn_depth":4,"max_concurrent_children":8}""",
        )
        val processes = json.decodeFromString<BackgroundProcessListResponse>(
            """{"processes":[{"session_id":"proc-1","command":"pytest","status":"running","uptime_seconds":12,"output_tail":"ok"}]}""",
        )

        assertTrue(delegation.paused)
        assertEquals(8, delegation.maxConcurrentChildren)
        assertEquals("agent-7", delegation.active.single().id)
        assertEquals("proc-1", processes.processes.single().id)
        assertEquals("ok", processes.processes.single().outputTail)
    }

    @Test
    fun `archived TUI snapshot normalizes camel case detail without random identity`() {
        val raw = json.parseToJsonElement(
            """{"id":"archived-child","parentId":"archived-parent","goal":"Inspect artifacts","model":"hermes-4","status":"timeout","index":2,"taskCount":3,"startedAt":1000,"durationSeconds":4.5,"costUsd":0.02,"inputTokens":120,"outputTokens":30,"toolCount":2,"filesRead":["input.txt"],"filesWritten":["report.md"],"thinking":["checking"],"notes":["bounded"],"outputTail":[{"tool":"terminal","preview":"gradle test","isError":false}],"summary":"Timed out safely","future":"ignored"}""",
        ).jsonObject

        val archived = SubagentReducer.fromSnapshot(raw, "archive:0", 9_000)

        assertEquals("archived-child", archived.id)
        assertEquals("archived-parent", archived.parentId)
        assertEquals(SubagentStatus.FAILED, archived.status)
        assertEquals(2, archived.taskIndex)
        assertEquals(3, archived.taskCount)
        assertEquals(listOf("input.txt"), archived.filesRead)
        assertEquals(listOf("report.md"), archived.filesWritten)
        assertTrue(archived.stream.any { it.kind == SubagentStreamKind.THINKING && it.text == "checking" })
        assertTrue(archived.stream.any { it.kind == SubagentStreamKind.TOOL && "Terminal" in it.text })
    }

    private fun event(type: String, id: String, parent: String?, text: String) = GatewayEvent(
        type,
        "runtime-1",
        buildJsonObject {
            put("subagent_id", id)
            parent?.let { put("parent_id", it) }
            put("goal", text)
            put("text", text)
        },
    )
}
