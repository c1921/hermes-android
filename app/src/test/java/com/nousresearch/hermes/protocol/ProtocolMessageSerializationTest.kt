package com.nousresearch.hermes.protocol

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class ProtocolMessageSerializationTest {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    @Test
    fun `session history accepts sqlite numeric message identifiers`() {
        val message = json.decodeFromString(ProtocolMessage.serializer(), """{"id":42,"role":"assistant","content":"Ready"}""")

        assertEquals("42", message.id)
    }

    @Test
    fun `session history keeps string and absent identifiers`() {
        assertEquals(
            "gateway-message",
            json.decodeFromString(ProtocolMessage.serializer(), """{"id":"gateway-message","role":"assistant"}""").id,
        )
        assertNull(json.decodeFromString(ProtocolMessage.serializer(), """{"role":"assistant"}""").id)
    }

    @Test
    fun `session history rejects boolean identifiers`() {
        assertThrows(IllegalArgumentException::class.java) {
            json.decodeFromString(ProtocolMessage.serializer(), """{"id":true,"role":"assistant"}""")
        }
    }

    @Test
    fun `session history keeps shipped durable tool pairing fields`() {
        val message = json.decodeFromString(
            ProtocolMessage.serializer(),
            """{"role":"tool","tool_call_id":"img-1","tool_name":"image_generate","content":"{}"}""",
        )

        assertEquals("img-1", message.toolCallId)
        assertEquals("image_generate", message.toolName)
    }
}
