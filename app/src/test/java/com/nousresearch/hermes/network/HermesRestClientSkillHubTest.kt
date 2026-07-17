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

class HermesRestClientSkillHubTest {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    @Test
    fun `hub review scan and background mutations use audited profile routes`() = runTest {
        MockWebServer().use { server ->
            server.start()
            val config = BackendConfig("fake", "Fake", server.url("/").toString().trimEnd('/'), AuthMode.TOKEN, true)
            val client = HermesRestClient(OkHttpClient(), json)
            server.enqueue(MockResponse().setBody("""{"sources":[{"id":"official","label":"Official"}],"index_available":true,"featured":[{"name":"Research","identifier":"official/research","trust_level":"trusted"}]}"""))
            server.enqueue(MockResponse().setBody("""{"results":[{"name":"Research","identifier":"official/research","source":"official","trust_level":"trusted"}],"source_counts":{"official":1},"timed_out":[]}"""))
            server.enqueue(MockResponse().setBody("""{"name":"Research","identifier":"official/research","trust_level":"trusted","skill_md":"# Research","files":["SKILL.md"]}"""))
            server.enqueue(MockResponse().setBody("""{"name":"Research","identifier":"official/research","verdict":"clean","policy":"allow","findings":[],"severity_counts":{}}"""))
            server.enqueue(MockResponse().setBody("""{"ok":true,"pid":44,"name":"skills-install-official-research-a1b2c3d4"}"""))
            server.enqueue(MockResponse().setBody("""{"name":"skills-install-official-research-a1b2c3d4","running":false,"exit_code":0,"pid":44,"lines":["installed"]}"""))
            server.enqueue(MockResponse().setBody("""{"ok":true,"pid":45,"name":"skills-uninstall-research-e5f6a7b8"}"""))
            server.enqueue(MockResponse().setBody("""{"ok":true,"pid":46,"name":"skills-update"}"""))

            assertEquals(1, client.skillHubSources(config, "token", "lab").featured.size)
            assertEquals(1, client.searchSkillHub(config, "token", "lab", "web research").results.size)
            assertTrue(client.previewSkillHub(config, "token", "lab", "official/research").skillMarkdown.startsWith("#"))
            assertEquals("allow", client.scanSkillHub(config, "token", "lab", "official/research").policy)
            val install = client.installSkillHub(config, "token", "lab", "official/research")
            assertEquals(0, client.actionStatus(config, "token", install.name).exitCode)
            client.uninstallSkillHub(config, "token", "lab", "Research")
            client.updateSkillsHub(config, "token", "lab")

            val requests = List(8) { server.takeRequest() }
            assertEquals("/api/skills/hub/sources?profile=lab", requests[0].path)
            assertEquals("/api/skills/hub/search?q=web%20research&source=all&limit=30&profile=lab", requests[1].path)
            assertEquals("/api/skills/hub/preview?identifier=official%2Fresearch&profile=lab", requests[2].path)
            assertEquals("/api/skills/hub/scan?identifier=official%2Fresearch&profile=lab", requests[3].path)
            assertTrue(requests[4].body.readUtf8().contains("official/research"))
            assertEquals("/api/actions/${install.name}/status?lines=400", requests[5].path)
            assertEquals("/api/skills/hub/uninstall", requests[6].path)
            assertEquals("/api/skills/hub/update", requests[7].path)
        }
    }
}
