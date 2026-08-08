package com.nousresearch.hermes.ui

import com.nousresearch.hermes.domain.BlockingRequestKind
import com.nousresearch.hermes.domain.MessageRole
import com.nousresearch.hermes.domain.TimelineItem
import com.nousresearch.hermes.domain.ToolState
import org.junit.Assert.assertEquals
import org.junit.Test

class TimelineRendererRegistryTest {
    @Test
    fun `every typed timeline part resolves to its specialised renderer`() {
        val cases = listOf(
            TimelineItem.Message("m", MessageRole.ASSISTANT, "text") to TimelineRendererKind.MESSAGE,
            TimelineItem.Tool("t", "terminal", state = ToolState.COMPLETE) to TimelineRendererKind.TOOL,
            TimelineItem.Reasoning("r", "why", streaming = false) to TimelineRendererKind.REASONING,
            TimelineItem.Status("s", "ready", "Ready") to TimelineRendererKind.STATUS,
            TimelineItem.Reference("ref", "Model", "Alternative") to TimelineRendererKind.REFERENCE,
            TimelineItem.Artifact("a", "report.md") to TimelineRendererKind.ARTIFACT,
            TimelineItem.Error("e", "Failure") to TimelineRendererKind.ERROR,
            TimelineItem.BlockingRequest("b", BlockingRequestKind.CLARIFICATION, "Which?") to
                TimelineRendererKind.BLOCKING_REQUEST,
            TimelineItem.Unknown("u", "future", "Unsupported future part") to TimelineRendererKind.GENERIC,
        )

        cases.forEach { (item, expected) ->
            assertEquals(expected, TimelineRendererRegistry.resolve(item))
        }
    }
}
