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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CheckpointGatewayContractTest {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    @Test
    fun `uses pinned session scoped list preview restore and history contracts`() = runBlocking {
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
            val sessionId = "live-session"
            val hash = "0123456789abcdef0123456789abcdef01234567"

            client.connect(config, "test-token")
            val listed = client.request("rollback.list", buildJsonObject { put("session_id", sessionId) })
            val diff = client.request(
                "rollback.diff",
                buildJsonObject {
                    put("session_id", sessionId)
                    put("hash", hash)
                },
            )
            val restored = client.request(
                "rollback.restore",
                buildJsonObject {
                    put("session_id", sessionId)
                    put("hash", hash)
                },
            )
            val history = client.request("session.history", buildJsonObject { put("session_id", sessionId) })

            assertTrue(json.decodeFromJsonElement(RollbackListResult.serializer(), listed).enabled)
            assertTrue(json.decodeFromJsonElement(RollbackDiffResult.serializer(), diff).diff.contains("-old"))
            assertEquals(3, json.decodeFromJsonElement(RollbackRestoreResult.serializer(), restored).historyRemoved)
            assertEquals(1, json.decodeFromJsonElement(SessionHistoryResult.serializer(), history).messages.size)

            val requests = backend.requests.associateBy { it.getValue("method").jsonPrimitive.content }
            assertEquals(setOf("session_id"), requests.getValue("rollback.list").getValue("params").jsonObject.keys)
            assertEquals(setOf("session_id", "hash"), requests.getValue("rollback.diff").getValue("params").jsonObject.keys)
            assertEquals(setOf("session_id", "hash"), requests.getValue("rollback.restore").getValue("params").jsonObject.keys)
            assertNull(requests.getValue("rollback.restore").getValue("params").jsonObject["file_path"])
            client.disconnect()
        }
    }
}
