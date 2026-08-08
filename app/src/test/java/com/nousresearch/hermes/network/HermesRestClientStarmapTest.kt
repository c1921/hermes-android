package com.nousresearch.hermes.network

import com.nousresearch.hermes.data.AuthMode
import com.nousresearch.hermes.data.BackendConfig
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HermesRestClientStarmapTest {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    @Test
    fun `learning graph and node mutations retain exact profile scope`() = runTest {
        MockWebServer().use { server ->
            server.start()
            val client = HermesRestClient(OkHttpClient(), json)
            val config = BackendConfig(
                id = "fake",
                label = "Fake Hermes",
                baseUrl = server.url("/").toString().trimEnd('/'),
                authMode = AuthMode.TOKEN,
                allowInsecurePrivateNetwork = true,
            )
            server.enqueue(MockResponse().setBody("""{"nodes":[{"id":"skill:review","label":"Review","kind":"skill","category":"workflow","useCount":4,"state":"active","createdBy":"agent","pinned":true}],"edges":[],"clusters":[{"category":"workflow","count":1}],"memory":[],"stats":{"total":1}}"""))
            server.enqueue(MockResponse().setBody("""{"ok":true,"kind":"skill","label":"Review","content":"# Review"}"""))
            server.enqueue(MockResponse().setBody("""{"ok":true,"message":"updated"}"""))
            server.enqueue(MockResponse().setBody("""{"ok":true,"message":"archived"}"""))

            val graph = client.learningGraph(config, "secret", "coder")
            val detail = client.learningNode(config, "secret", "coder", "skill:review")
            client.updateLearningNode(config, "secret", "coder", "skill:review", "# Better review")
            client.deleteLearningNode(config, "secret", "coder", "skill:review")

            assertEquals("Review", graph.nodes.single().label)
            assertEquals("# Review", detail.content)
            val requests = List(4) { server.takeRequest() }
            assertEquals("/api/learning/graph?profile=coder", requests[0].path)
            assertEquals("/api/learning/node?id=skill:review&profile=coder", requests[1].path)
            assertEquals("PUT", requests[2].method)
            val updateBody = requests[2].body.readUtf8()
            assertTrue(updateBody.contains("\"profile\":\"coder\""))
            assertTrue(updateBody.contains("# Better review"))
            assertEquals("DELETE", requests[3].method)
            assertTrue(requests[3].body.readUtf8().contains("\"id\":\"skill:review\""))
            assertTrue(requests.all { it.getHeader("Authorization") == "Bearer secret" })
        }
    }

    @Test
    fun `learning mutation rejects missing acknowledgement before a second request`() = runTest {
        MockWebServer().use { server ->
            server.start()
            val client = HermesRestClient(OkHttpClient(), json)
            val config = BackendConfig(
                id = "fake",
                label = "Fake Hermes",
                baseUrl = server.url("/").toString().trimEnd('/'),
                authMode = AuthMode.TOKEN,
                allowInsecurePrivateNetwork = true,
            )
            server.enqueue(MockResponse().setBody("""{"ok":false,"message":"rejected"}"""))

            assertTrue(runCatching {
                client.updateLearningNode(config, "secret", "coder", "skill:review", "content")
            }.isFailure)
            assertTrue(runCatching {
                client.updateLearningNode(config, "secret", "coder", "skill:review", "x".repeat(262_145))
            }.isFailure)
            assertEquals(1, server.requestCount)
        }
    }
}
