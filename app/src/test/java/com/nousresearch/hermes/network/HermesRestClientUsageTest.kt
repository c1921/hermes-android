package com.nousresearch.hermes.network

import com.nousresearch.hermes.data.AuthMode
import com.nousresearch.hermes.data.BackendConfig
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HermesRestClientUsageTest {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    @Test
    fun `usage analytics are profile scoped and reuse the dashboard cookie`() = runTest {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(
                MockResponse().setBody(
                    """{"daily":[{"day":"2026-07-18","input_tokens":1200,"output_tokens":300,"cache_read_tokens":400,"reasoning_tokens":50,"estimated_cost":0.42,"actual_cost":0.40,"sessions":2,"api_calls":8}],"by_model":[{"model":"hermes-4","input_tokens":1200,"output_tokens":300,"estimated_cost":0.42,"sessions":2,"api_calls":8,"future":"ignored"}],"totals":{"total_input":1200,"total_output":300,"total_cache_read":400,"total_reasoning":50,"total_estimated_cost":0.42,"total_actual_cost":0.40,"total_sessions":2,"total_api_calls":8},"period_days":30,"skills":{"summary":{"total_skill_loads":1,"total_skill_edits":0,"total_skill_actions":1,"distinct_skills_used":1},"top_skills":[]},"tools":[{"tool":"terminal","count":4,"percentage":50.0}]}""",
                ),
            )

            val result = client().usageAnalytics(config(server), COOKIE, "work profile", 30)

            assertEquals(1200L, result.totals.totalInput)
            assertEquals(400L, result.totals.totalCacheRead)
            assertEquals("hermes-4", result.byModel.single().model)
            assertEquals("terminal", result.tools.single().tool)
            val request = server.takeRequest()
            assertEquals("/api/analytics/usage?days=30&profile=work%20profile", request.path)
            assertEquals(COOKIE, request.getHeader("Cookie"))
        }
    }

    @Test
    fun `usage totals preserve null counters from an empty older database`() = runTest {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(
                MockResponse().setBody(
                    """{"daily":[],"by_model":[],"totals":{"total_input":null,"total_output":null,"total_cache_read":null,"total_reasoning":null,"total_estimated_cost":0,"total_actual_cost":0,"total_sessions":0,"total_api_calls":null},"period_days":7,"skills":{"summary":{"total_skill_loads":0,"total_skill_edits":0,"total_skill_actions":0,"distinct_skills_used":0},"top_skills":[]}}""",
                ),
            )

            val result = client().usageAnalytics(config(server), COOKIE, "default", 7)

            assertNull(result.totals.totalInput)
            assertNull(result.totals.totalApiCalls)
            assertEquals(0L, result.totals.totalSessions)
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
