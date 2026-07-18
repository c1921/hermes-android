package com.nousresearch.hermes.protocol

import com.nousresearch.hermes.data.AuthMode
import com.nousresearch.hermes.data.BackendConfig
import com.nousresearch.hermes.network.DashboardAuthClient
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpawnTreeGatewayContractTest {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    @Test
    fun `lists bounded cross session archives and loads only the returned path`() = runBlocking {
        FakeHermesBackend(json).use { backend ->
            backend.start()
            val http = OkHttpClient()
            val client = OkHttpHermesGatewayClient(http, json, DashboardAuthClient(http, json))
            val config = BackendConfig(
                id = "fake",
                label = "Fake Hermes",
                baseUrl = backend.baseUrl,
                authMode = AuthMode.TOKEN,
                allowInsecurePrivateNetwork = true,
            )

            client.connect(config, "test-token")
            val listed = client.request(
                "spawn_tree.list",
                buildJsonObject {
                    put("limit", 30)
                    put("cross_session", true)
                },
            ).let { json.decodeFromJsonElement(SpawnTreeListResponse.serializer(), it) }
            val entry = listed.entries.single()
            val loaded = client.request(
                "spawn_tree.load",
                buildJsonObject { put("path", entry.path) },
            ).let { json.decodeFromJsonElement(SpawnTreeSnapshot.serializer(), it) }

            assertEquals("Archive QA", entry.label)
            assertEquals(2, entry.count)
            assertEquals("stored-session", loaded.sessionId)
            assertEquals(2, loaded.subagents.size)
            val requests = backend.requests.associateBy { it.getValue("method").jsonPrimitive.content }
            assertEquals(
                setOf("limit", "cross_session"),
                requests.getValue("spawn_tree.list").getValue("params").jsonObject.keys,
            )
            assertTrue(
                requests.getValue("spawn_tree.load").getValue("params").jsonObject
                    .getValue("path").jsonPrimitive.content.endsWith("20260718T090000.json"),
            )
            client.disconnect()
        }
    }
}
