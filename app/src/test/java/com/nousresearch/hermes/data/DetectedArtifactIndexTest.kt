package com.nousresearch.hermes.data

import com.nousresearch.hermes.domain.DetectedArtifactKind
import com.nousresearch.hermes.network.HermesRestClient
import com.nousresearch.hermes.protocol.ProtocolMessage
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DetectedArtifactIndexTest {
    private val backend = BackendConfig(
        id = "backend-a",
        label = "Hermes A",
        baseUrl = "https://hermes.example",
        authMode = AuthMode.DASHBOARD_SESSION,
    )

    @Test
    fun `projection is newest first deduplicated and preserves session origin`() {
        val scope = ArtifactIndexScope(backend.id, "default")
        val sessions = listOf(
            DetectedArtifactSession(
                sessionId = "old-session",
                title = "Old notes",
                timestamp = 10.0,
                messages = listOf(
                    ProtocolMessage("old-message", "assistant", JsonPrimitive("https://example.com/old.txt")),
                ),
            ),
            DetectedArtifactSession(
                sessionId = "new-session",
                title = "Latest report",
                timestamp = 20.0,
                messages = listOf(
                    ProtocolMessage(
                        "new-message",
                        "assistant",
                        JsonPrimitive("https://example.com/chart.png https://example.com/chart.png"),
                    ),
                ),
            ),
        )

        val snapshot = DetectedArtifactIndex.project(scope, sessions)

        assertEquals(listOf("new-session", "old-session"), snapshot.entries.map { it.artifact.origin.sessionId })
        assertEquals("Latest report", snapshot.entries.first().sessionTitle)
        assertEquals(20.0, snapshot.entries.first().sessionTimestamp, 0.0)
        assertEquals("new-message", snapshot.entries.first().artifact.origin.messageId)
        assertEquals(1, snapshot.entries.count { it.artifact.value == "https://example.com/chart.png" })
        assertEquals(backend.id, snapshot.backendId)
        assertEquals("default", snapshot.profileId)
    }

    @Test
    fun `projection keeps the newest thirty sessions regardless of input order`() {
        val sessions = (0..30).map { index ->
            DetectedArtifactSession(
                sessionId = "s-$index",
                title = "Session $index",
                timestamp = index.toDouble(),
                messages = listOf(
                    ProtocolMessage("m-$index", "assistant", JsonPrimitive("https://example.com/$index")),
                ),
            )
        }.reversed()

        val snapshot = DetectedArtifactIndex.project(
            ArtifactIndexScope(backend.id, "default"),
            sessions,
        )

        assertEquals(30, snapshot.sessionsLoaded)
        assertEquals("s-30", snapshot.entries.firstOrNull()?.artifact?.origin?.sessionId)
        assertTrue(snapshot.entries.none { it.artifact.origin.sessionId == "s-0" })
    }

    @Test
    fun `search supports exact desktop filters and session title label value matching`() {
        val scope = ArtifactIndexScope(backend.id, "default")
        val sessions = listOf(
            DetectedArtifactSession(
                "s1",
                "Design review",
                30.0,
                listOf(ProtocolMessage("m1", "assistant", JsonPrimitive("MEDIA:/tmp/mockup.png"))),
            ),
            DetectedArtifactSession(
                "s2",
                "Operations",
                40.0,
                listOf(ProtocolMessage("m2", "assistant", JsonPrimitive("[runbook](https://example.com/runbook)"))),
            ),
            DetectedArtifactSession(
                "s3",
                "Release docs",
                20.0,
                listOf(ProtocolMessage("m3", "assistant", JsonPrimitive("MEDIA:/tmp/manual.pdf"))),
            ),
        )
        val snapshot = DetectedArtifactIndex.project(scope, sessions)

        assertEquals(3, snapshot.search(ArtifactIndexFilter.ALL).size)
        assertEquals(1, snapshot.search(ArtifactIndexFilter.IMAGES).size)
        assertEquals(1, snapshot.search(ArtifactIndexFilter.FILES).size)
        assertEquals(1, snapshot.search(ArtifactIndexFilter.LINKS).size)
        assertEquals("Design review", snapshot.search(ArtifactIndexFilter.ALL, "design").single().sessionTitle)
        assertEquals("/tmp/mockup.png", snapshot.search(ArtifactIndexFilter.IMAGES, "mockup").single().artifact.value)
        assertEquals("https://example.com/runbook", snapshot.search(ArtifactIndexFilter.LINKS, "RUNBOOK").single().artifact.value)
        assertTrue(snapshot.search(ArtifactIndexFilter.LINKS, "missing").isEmpty())
    }

    @Test
    fun `loader seam receives explicit backend profile credential and repository caps at thirty`() = runTest {
        var received: List<Any?> = emptyList()
        val loader = DetectedArtifactSessionLoader { config, profile, credential, limit ->
            received = listOf(config, profile, credential, limit)
            (0 until 31).map { index ->
                DetectedArtifactSession(
                    sessionId = "s-$index",
                    title = "Session $index",
                    timestamp = index.toDouble(),
                    messages = emptyList(),
                )
            }
        }
        val index = DetectedArtifactIndex(loader)

        val snapshot = index.load(backend, "default", "session-cookie")

        assertEquals(listOf(backend, "default", "session-cookie", 30), received)
        assertEquals(30, snapshot.sessionsLoaded)
    }

    @Test
    fun `loader rejects blank profile and credential before touching the seam`() = runTest {
        var calls = 0
        val loader = DetectedArtifactSessionLoader { _, _, _, _ ->
            calls += 1
            emptyList()
        }
        val index = DetectedArtifactIndex(loader)

        assertIllegalArgument { index.load(backend, " ", "secret") }
        assertIllegalArgument { index.load(backend, "default", " ") }
        assertEquals(0, calls)
    }

    @Test
    fun `rest loader keeps selected and server-null profiles while excluding mixed rows`() = runTest {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(
                MockResponse().setBody(
                    """{"sessions":[
                        {"session_id":"selected","profile":"research","title":"Selected","started_at":1,"last_active":2},
                        {"session_id":"defaulted","profile":null,"title":"Defaulted","started_at":3,"last_active":4},
                        {"session_id":"other","profile":"default","title":"Other","started_at":5,"last_active":6}
                    ],"total":3}""".trimIndent(),
                ),
            )
            server.enqueue(MockResponse().setBody("""{"session_id":"selected","messages":[]}"""))
            server.enqueue(MockResponse().setBody("""{"session_id":"defaulted","messages":[]}"""))

            val config = backend.copy(
                baseUrl = server.url("/").toString().trimEnd('/'),
                allowInsecurePrivateNetwork = true,
            )
            val loader = HermesArtifactSessionLoader(
                HermesRestClient(OkHttpClient(), Json { ignoreUnknownKeys = true }),
            )

            val sessions = loader.load(config, " research ", "secret", limit = 30)

            assertEquals(listOf("selected", "defaulted"), sessions.map { it.sessionId })
            assertEquals(
                "/api/profiles/sessions?limit=30&offset=0&order=recent&profile=research&exclude_sources=cron",
                server.takeRequest().path,
            )
            assertEquals("/api/sessions/selected/messages?profile=research", server.takeRequest().path)
            assertEquals("/api/sessions/defaulted/messages?profile=research", server.takeRequest().path)
        }
    }

    private suspend fun assertIllegalArgument(action: suspend () -> Unit) {
        val error = try {
            action()
            null
        } catch (failure: Throwable) {
            failure
        }
        assertTrue(error is IllegalArgumentException)
    }
}
