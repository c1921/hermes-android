package com.nousresearch.hermes.protocol

import com.nousresearch.hermes.data.ModelSelection
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelProtocolModelsTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `decodes dynamic provider catalogue and per-model capabilities`() {
        val result = json.decodeFromString<ModelOptionsResult>(
            """{"model":"hermes-4","provider":"nous","providers":[{"slug":"nous","name":"Nous Portal","is_current":true,"models":["hermes-4","hermes-4-fast"],"total_models":2,"authenticated":true,"capabilities":{"hermes-4":{"fast":true,"reasoning":true},"hermes-4-fast":{"fast":false,"reasoning":true}}}]}""",
        )

        assertEquals("hermes-4", result.model)
        assertEquals(listOf("hermes-4", "hermes-4-fast"), result.providers.single().models)
        assertTrue(result.providers.single().capabilities.getValue("hermes-4").fast)
        assertFalse(result.providers.single().capabilities.getValue("hermes-4-fast").fast)
    }

    @Test
    fun `builds a session-scoped switch and rejects injected flag tokens`() {
        assertEquals(
            "anthropic/claude-opus-4-6 --provider openrouter --session",
            ModelSelection("openrouter", "anthropic/claude-opus-4-6").rpcValue(),
        )

        val failure = runCatching { ModelSelection("openrouter", "safe --global").rpcValue() }.exceptionOrNull()
        assertTrue(failure is IllegalArgumentException)
    }
}
