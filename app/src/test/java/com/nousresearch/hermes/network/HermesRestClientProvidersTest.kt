package com.nousresearch.hermes.network

import com.nousresearch.hermes.data.AuthMode
import com.nousresearch.hermes.data.BackendConfig
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HermesRestClientProvidersTest {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    @Test
    fun `provider catalogue validation and persistence use profile scoped audited routes`() = runTest {
        MockWebServer().use { server ->
            server.start()
            val config = BackendConfig(
                id = "fake",
                label = "Fake Hermes",
                baseUrl = server.url("/").toString().trimEnd('/'),
                authMode = AuthMode.TOKEN,
                allowInsecurePrivateNetwork = true,
            )
            val client = HermesRestClient(OkHttpClient(), json)
            server.enqueue(MockResponse().setBody("""{"providers":[{"slug":"openrouter","name":"OpenRouter","authenticated":false,"auth_type":"api_key","key_env":"OPENROUTER_API_KEY","models":["model-a"]}]}"""))
            server.enqueue(MockResponse().setBody("""{"OPENROUTER_API_KEY":{"advanced":false,"category":"provider","description":"OpenRouter key","is_password":true,"is_set":false,"provider":"openrouter","provider_label":"OpenRouter","redacted_value":null,"tools":[]}}"""))
            server.enqueue(MockResponse().setBody("""{"ok":true,"reachable":true,"message":""}"""))
            server.enqueue(MockResponse().setBody("""{"ok":true,"key":"OPENROUTER_API_KEY"}"""))
            server.enqueue(MockResponse().setBody("""{"ok":true}"""))

            val options = client.globalModelOptions(config, "session-token", "research lab", refresh = true)
            val env = client.envVars(config, "session-token", "research lab")
            val validation = client.validateProviderCredential(config, "session-token", "OPENROUTER_API_KEY", "provider-secret")
            client.setEnvVar(config, "session-token", "research lab", "OPENROUTER_API_KEY", "provider-secret")
            client.deleteEnvVar(config, "session-token", "research lab", "OPENROUTER_API_KEY")

            assertFalse(options.providers.single().authenticated)
            assertEquals("OPENROUTER_API_KEY", options.providers.single().keyEnvironment)
            assertTrue(env.getValue("OPENROUTER_API_KEY").isPassword)
            assertTrue(validation.ok)

            val requests = List(5) { server.takeRequest() }
            assertEquals("/api/model/options?explicit_only=1&include_unconfigured=1&refresh=1&profile=research%20lab", requests[0].path)
            assertEquals("/api/env?profile=research%20lab", requests[1].path)
            assertEquals("/api/providers/validate", requests[2].path)
            assertTrue(requests[2].body.readUtf8().contains("provider-secret"))
            assertEquals("PUT", requests[3].method)
            assertTrue(requests[3].body.readUtf8().contains("research lab"))
            assertEquals("DELETE", requests[4].method)
            assertTrue(requests.all { it.getHeader("Authorization") == "Bearer session-token" })
        }
    }
}
