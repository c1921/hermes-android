package com.nousresearch.hermes.network

import com.nousresearch.hermes.data.AuthMode
import com.nousresearch.hermes.data.BackendConfig
import com.nousresearch.hermes.protocol.ProfileCreatePayload
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HermesRestClientProfilesTest {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    @Test
    fun `profile lifecycle uses audited REST routes and preserves scope semantics`() = runTest {
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
            server.enqueue(MockResponse().setBody("""{"profiles":[{"name":"default","is_default":true,"skill_count":4},{"name":"coder","provider":"nous","model":"hermes-4","skill_count":9}]}"""))
            server.enqueue(MockResponse().setBody("""{"active":"coder","current":"default"}"""))
            server.enqueue(MockResponse().setBody("""{"ok":true,"name":"research","path":"/profiles/research"}"""))
            server.enqueue(MockResponse().setBody("""{"ok":true,"name":"engineering","path":"/profiles/engineering"}"""))
            server.enqueue(MockResponse().setBody("""{"ok":true,"active":"engineering"}"""))
            server.enqueue(MockResponse().setBody("""{"ok":true,"path":"/profiles/engineering"}"""))

            val profiles = client.profiles(config, "secret")
            val active = client.activeProfile(config, "secret")
            client.createProfile(
                config,
                "secret",
                ProfileCreatePayload(name = "research", cloneFrom = "coder", cloneAll = true),
            )
            client.renameProfile(config, "secret", "research", "engineering")
            client.setActiveProfile(config, "secret", "engineering")
            client.deleteProfile(config, "secret", "engineering")

            assertTrue(profiles.profiles.first().isDefault)
            assertEquals("hermes-4", profiles.profiles.last().model)
            assertEquals("coder", active.active)
            assertEquals("default", active.current)

            val requests = List(6) { server.takeRequest() }
            assertEquals("/api/profiles", requests[0].path)
            assertEquals("/api/profiles/active", requests[1].path)
            assertEquals("POST", requests[2].method)
            assertTrue(requests[2].body.readUtf8().contains("\"clone_from\":\"coder\""))
            assertEquals("PATCH", requests[3].method)
            assertEquals("/api/profiles/research", requests[3].path)
            assertTrue(requests[3].body.readUtf8().contains("\"new_name\":\"engineering\""))
            assertEquals("POST", requests[4].method)
            assertEquals("/api/profiles/active", requests[4].path)
            assertEquals("DELETE", requests[5].method)
            assertEquals("/api/profiles/engineering", requests[5].path)
            assertTrue(requests.all { it.getHeader("Authorization") == "Bearer secret" })
        }
    }
}
