package com.nousresearch.hermes.protocol

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageProtocolModelsTest {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    @Test
    fun `session context breakdown decodes audited categories and future fields`() {
        val result = json.decodeFromString(
            ContextBreakdown.serializer(),
            """{"categories":[{"id":"system","label":"System prompt","color":"#0000ff","tokens":800},{"id":"tools","label":"Tools","color":"#00ff00","tokens":200}],"context_max":32768,"context_percent":12.5,"context_used":4000,"estimated_total":4300,"model":"hermes-4","future_field":{"ignored":true}}""",
        )

        assertEquals(32768L, result.contextMax)
        assertEquals(4000L, result.contextUsed)
        assertEquals("tools", result.categories.last().id)
    }

    @Test
    fun `empty context fallback remains a valid result`() {
        val result = json.decodeFromString(
            ContextBreakdown.serializer(),
            """{"categories":[],"context_max":0,"context_percent":0,"context_used":0,"estimated_total":0,"model":""}""",
        )

        assertTrue(result.categories.isEmpty())
        assertEquals(0L, result.estimatedTotal)
    }
}
