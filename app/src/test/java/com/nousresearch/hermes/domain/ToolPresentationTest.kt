package com.nousresearch.hermes.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolPresentationTest {
    @Test
    fun `structured command result becomes a readable multiline transcript`() {
        val presentation = TimelineItem.Tool(
            id = "tool-1",
            name = "terminal",
            state = ToolState.COMPLETE,
            detail = """{"output":"first line\nsecond line","exit_code":0,"error":null}""",
        ).presentation()

        assertEquals(null, presentation.summary)
        assertEquals(ToolState.COMPLETE, presentation.state)
        assertEquals(
            """Output
first line
second line

Exit code
0""",
            presentation.transcript,
        )
        assertFalse(presentation.transcript.contains("\\n"))
    }

    @Test
    fun `generic structured detail is indented without losing nested values`() {
        val presentation = TimelineItem.Tool(
            id = "tool-2",
            name = "read_file",
            state = ToolState.COMPLETE,
            detail = """{"path":"notes.txt","range":{"start":1,"end":4}}""",
        ).presentation()

        assertTrue(presentation.transcript.contains("\n"))
        assertTrue(presentation.transcript.contains("  \"path\": \"notes.txt\""))
        assertTrue(presentation.transcript.contains("    \"start\": 1"))
    }

    @Test
    fun `structured output values remain in the full transcript`() {
        val presentation = TimelineItem.Tool(
            id = "tool-structured",
            name = "search",
            state = ToolState.COMPLETE,
            detail = """{"output":{"items":["one","two"]},"exit_code":0}""",
        ).presentation()

        assertTrue(presentation.transcript.contains("Output\n{"))
        assertTrue(presentation.transcript.contains("\"items\""))
        assertTrue(presentation.transcript.contains("\"two\""))
    }

    @Test
    fun `summary never removes explicit execution state`() {
        val presentation = TimelineItem.Tool(
            id = "tool-failed",
            name = "terminal",
            state = ToolState.FAILED,
            summary = "Command returned output",
        ).presentation()

        assertEquals("Command returned output", presentation.summary)
        assertEquals(ToolState.FAILED, presentation.state)
    }

    @Test
    fun `expanded transcript retains complete tool output`() {
        val fullOutput = "x".repeat(25_000)
        val presentation = TimelineItem.Tool(
            id = "tool-3",
            name = "terminal",
            state = ToolState.COMPLETE,
            detail = fullOutput,
        ).presentation()

        assertEquals(fullOutput, presentation.transcript)
    }
}
