package com.nousresearch.hermes.data

import com.nousresearch.hermes.protocol.ServerConfigSchemaField
import com.nousresearch.hermes.protocol.ServerConfigSchemaResponse
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class ServerConfigSafetyTest {
    @Test
    fun `schema parser exposes supported non-secret fields in server category order`() {
        val schema = ServerConfigSchemaResponse(
            fields = mapOf(
                "timezone" to field("string", "general"),
                "display.show_reasoning" to field("boolean", "display"),
                "compression.protect_last_n" to field("number", "compression"),
                "terminal.env_passthrough" to field("list", "terminal"),
                "dashboard.password" to field("string", "security"),
                "provider.api_key" to field("string", "general"),
                "approvals.mode" to field("select", "security", "manual", "smart", "off"),
                "security.allow_private_urls" to field("boolean", "security"),
                "security.redact_secrets" to field("boolean", "security"),
                "terminal.backend" to field("select", "terminal", "direct", "docker"),
                "updates.non_interactive_local_changes" to field("select", "general", "stash", "discard"),
                "model" to field("string", "general"),
                "toolsets" to field("list", "general"),
                "unsupported.object" to field("object", "general"),
            ),
            categoryOrder = listOf("general", "terminal", "display", "compression", "security"),
        )
        val config = buildJsonObject {
            put("timezone", "Europe/London")
            put("display", buildJsonObject { put("show_reasoning", true) })
            put("compression", buildJsonObject { put("protect_last_n", 4) })
            put("terminal", buildJsonObject { put("env_passthrough", "TERM,LANG") })
            put("dashboard", buildJsonObject { put("password", "must-not-surface") })
            put("provider", buildJsonObject { put("api_key", "must-not-surface") })
        }

        val snapshot = parseServerConfig(schema, config)

        assertEquals(listOf("general", "display", "compression"), snapshot.categories)
        assertEquals(
            setOf("timezone", "display.show_reasoning", "compression.protect_last_n"),
            snapshot.fields.map { it.key }.toSet(),
        )
        assertFalse(snapshot.fields.any { it.value.toString().contains("must-not-surface") })
    }

    @Test
    fun `nested patch contains only the selected advertised field`() {
        val patch = buildServerConfigPatch("compression.protect_last_n", JsonPrimitive(6))

        assertEquals(setOf("compression"), patch.keys)
        val compression = patch.getValue("compression").jsonObject
        assertEquals(setOf("protect_last_n"), compression.keys)
        assertEquals(6, compression.getValue("protect_last_n").jsonPrimitive.content.toInt())
    }

    @Test
    fun `field identity and values are bounded before mutation`() {
        val select = ServerConfigField(
            key = "display.resume_display",
            category = "display",
            description = "History display mode",
            type = ServerConfigType.SELECT,
            options = listOf("minimal", "full", "off"),
            value = JsonPrimitive("minimal"),
        )
        assertEquals(JsonPrimitive("full"), validateServerConfigValue(select, JsonPrimitive("full")))
        assertThrows(IllegalArgumentException::class.java) {
            validateServerConfigValue(select, JsonPrimitive("invented"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            buildServerConfigPatch("display..skin", JsonPrimitive("default"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            buildServerConfigPatch("dashboard.password", JsonPrimitive("secret"))
        }
    }

    @Test
    fun `boolean and number validation preserves JSON types`() {
        val booleanField = configField("display.show_reasoning", ServerConfigType.BOOLEAN, JsonPrimitive(true))
        val numberField = configField("agent.max_turns", ServerConfigType.NUMBER, JsonPrimitive(10))

        assertFalse(validateServerConfigValue(booleanField, JsonPrimitive(false)).jsonPrimitive.content.toBoolean())
        assertEquals("12", validateServerConfigValue(numberField, JsonPrimitive(12)).jsonPrimitive.content)
        assertThrows(IllegalArgumentException::class.java) {
            validateServerConfigValue(numberField, JsonPrimitive("twelve"))
        }
    }

    private fun field(type: String, category: String, vararg options: String) = ServerConfigSchemaField(
        type = type,
        category = category,
        description = "Description",
        options = options.map(::JsonPrimitive),
    )

    private fun configField(key: String, type: ServerConfigType, value: kotlinx.serialization.json.JsonElement) =
        ServerConfigField(key, key.substringBefore('.'), "Description", type, emptyList(), value)
}
