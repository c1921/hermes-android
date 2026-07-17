package com.nousresearch.hermes.network

import com.nousresearch.hermes.data.AuthMode
import com.nousresearch.hermes.data.BackendConfig
import com.nousresearch.hermes.protocol.CronJobCreatePayload
import com.nousresearch.hermes.protocol.CronJobUpdates
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HermesRestClientManagementTest {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    @Test
    fun `skills and cron actions use audited REST routes and typed responses`() = runTest {
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
            server.enqueue(MockResponse().setBody("""[{"name":"browser","description":"Web research","category":"research","enabled":true,"usage":12,"provenance":"bundled"}]"""))
            server.enqueue(MockResponse().setBody("""{"ok":true,"name":"browser","enabled":false}"""))
            server.enqueue(MockResponse().setBody("""[{"id":"daily","enabled":true,"name":"Daily brief","schedule_display":"0 8 * * *","next_run_at":"2026-07-18T08:00:00Z"}]"""))
            server.enqueue(MockResponse().setBody("""{"id":"daily","enabled":false,"name":"Daily brief"}"""))
            server.enqueue(MockResponse().setBody("""{"id":"daily","enabled":false,"name":"Daily brief","state":"queued"}"""))
            server.enqueue(MockResponse().setBody("""{"id":"weekly","enabled":true,"name":"Weekly review"}"""))
            server.enqueue(MockResponse().setBody("""{"id":"weekly","enabled":true,"name":"Friday review"}"""))
            server.enqueue(MockResponse().setBody("""{"ok":true}"""))

            val skills = client.skills(config, "secret")
            val toggled = client.toggleSkill(config, "secret", "browser", false)
            val jobs = client.cronJobs(config, "secret")
            val paused = client.setCronEnabled(config, "secret", "daily", false)
            val triggered = client.triggerCron(config, "secret", "daily")
            val created = client.createCron(
                config,
                "secret",
                CronJobCreatePayload(name = "Weekly review", prompt = "Review the week", schedule = "0 17 * * 5"),
            )
            val updated = client.updateCron(
                config,
                "secret",
                "weekly",
                CronJobUpdates(name = "Friday review", schedule = "0 16 * * 5"),
            )
            client.deleteCron(config, "secret", "weekly")

            assertEquals(12, skills.single().usage)
            assertFalse(toggled.enabled)
            assertEquals("0 8 * * *", jobs.single().scheduleDisplay)
            assertFalse(paused.enabled)
            assertEquals("queued", triggered.state)
            assertEquals("weekly", created.id)
            assertEquals("Friday review", updated.name)

            val requests = List(8) { server.takeRequest() }
            assertEquals("/api/skills", requests[0].path)
            assertEquals("PUT", requests[1].method)
            assertEquals("/api/skills/toggle", requests[1].path)
            assertEquals("/api/cron/jobs", requests[2].path)
            assertEquals("/api/cron/jobs/daily/pause", requests[3].path)
            assertEquals("/api/cron/jobs/daily/trigger", requests[4].path)
            assertEquals("POST", requests[5].method)
            assertEquals("/api/cron/jobs", requests[5].path)
            assertTrue(requests[5].body.readUtf8().contains("Review the week"))
            assertEquals("PUT", requests[6].method)
            assertTrue(requests[6].body.readUtf8().contains("\"updates\""))
            assertEquals("DELETE", requests[7].method)
            assertEquals("/api/cron/jobs/weekly", requests[7].path)
            assertTrue(requests.all { it.getHeader("Authorization") == "Bearer secret" })
        }
    }
}
