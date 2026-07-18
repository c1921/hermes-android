package com.nousresearch.hermes.network

import com.nousresearch.hermes.data.AuthMode
import com.nousresearch.hermes.data.BackendConfig
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HermesRestClientConfigTest {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    @Test
    fun `configuration read reuses dashboard cookie and preserves profile scope`() = runTest {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse().setBody("""{"timezone":"UTC","display":{"show_reasoning":true}}"""))
            server.enqueue(
                MockResponse().setBody(
                    """{"fields":{"timezone":{"type":"string","category":"general","description":"Timezone"}},"category_order":["general"],"future":"ignored"}""",
                ),
            )

            val config = client().serverConfig(config(server), COOKIE, "work profile")
            val schema = client().serverConfigSchema(config(server), COOKIE)

            assertEquals("UTC", config.getValue("timezone").jsonPrimitive.content)
            assertEquals("string", schema.fields.getValue("timezone").type)
            val configRequest = server.takeRequest()
            assertEquals("/api/config?profile=work%20profile", configRequest.path)
            assertEquals(COOKIE, configRequest.getHeader("Cookie"))
            val schemaRequest = server.takeRequest()
            assertEquals("/api/config/schema", schemaRequest.path)
            assertEquals(COOKIE, schemaRequest.getHeader("Cookie"))
        }
    }

    @Test
    fun `configuration mutation sends one nested field and validates acknowledgement`() = runTest {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse().setBody("""{"ok":true,"future":"ignored"}"""))

            val result = client().updateServerConfig(
                config(server),
                COOKIE,
                "mobile profile",
                "compression.protect_last_n",
                JsonPrimitive(6),
            )

            assertTrue(result.ok)
            val request = server.takeRequest()
            assertEquals("PUT", request.method)
            assertEquals("/api/config?profile=mobile%20profile", request.path)
            assertEquals(COOKIE, request.getHeader("Cookie"))
            val body = json.parseToJsonElement(request.body.readUtf8()).jsonObject
            assertEquals(setOf("config"), body.keys)
            val patch = body.getValue("config").jsonObject
            assertEquals(setOf("compression"), patch.keys)
            assertEquals(
                "6",
                patch.getValue("compression").jsonObject.getValue("protect_last_n").jsonPrimitive.content,
            )
        }
    }

    @Test
    fun `negative configuration acknowledgement remains a failure signal`() = runTest {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse().setBody("""{"ok":false}"""))

            val result = client().updateServerConfig(
                config(server),
                COOKIE,
                "default",
                "display.show_reasoning",
                JsonPrimitive(false),
            )

            assertFalse(result.ok)
        }
    }

    private fun client() = HermesRestClient(OkHttpClient(), json)

    private fun config(server: MockWebServer) = BackendConfig(
        id = "fake",
        label = "Fake Hermes",
        baseUrl = server.url("/").toString().trimEnd('/'),
        authMode = AuthMode.DASHBOARD_SESSION,
        allowInsecurePrivateNetwork = true,
    )

    private companion object {
        const val COOKIE = "hermes_session_at=abc"
    }
}
