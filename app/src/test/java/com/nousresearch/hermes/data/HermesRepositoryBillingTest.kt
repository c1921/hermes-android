package com.nousresearch.hermes.data

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.nousresearch.hermes.network.DashboardAuthClient
import com.nousresearch.hermes.network.DashboardSessionCookie
import com.nousresearch.hermes.network.HermesRestClient
import com.nousresearch.hermes.domain.TimelineItem
import com.nousresearch.hermes.protocol.GatewayConnectionState
import com.nousresearch.hermes.protocol.GatewayEvent
import com.nousresearch.hermes.protocol.HermesGatewayClient
import com.nousresearch.hermes.protocol.StoredSession
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HermesRepositoryBillingTest {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    @Test
    fun `ambiguous charge retries with the same key until settlement`() = runBlocking {
        MockWebServer().use { server ->
            server.dispatcher = readyDashboardDispatcher()
            server.start()
            val context = RuntimeEnvironment.getApplication()
            val backend = backend(server)
            val registry = BackendRegistry(context, json)
            val credentials = InMemoryCredentialStore()
            val pendingStore = BillingPendingChargeStore(context, json)
            val gateway = RecordingGateway(json)
            registry.save(backend)
            credentials.put(backend.id, SESSION_COOKIE)
            val repository = repository(context, registry, credentials, pendingStore, gateway)
            awaitReady(repository, backend.id)
            gateway.enqueue("billing.state", billingState())
            gateway.enqueue("subscription.state", json.parseToJsonElement("""{"ok":true,"logged_in":true}"""))
            repository.refreshBilling()
            gateway.enqueueFailure("billing.charge", IOException("connection dropped"))

            repository.chargeBillingCredits("20")

            assertTrue(repository.state.value.billingChargeUnconfirmed)
            val pending = checkNotNull(pendingStore.get(backend.id))
            gateway.enqueue("billing.charge", json.parseToJsonElement("""{"ok":true,"charge_id":"ch_1"}"""))
            gateway.enqueue("billing.charge_status", json.parseToJsonElement("""{"ok":true,"status":"settled","amount_usd":"20"}"""))
            gateway.enqueue("billing.state", billingState())
            gateway.enqueue("subscription.state", json.parseToJsonElement("""{"ok":true,"logged_in":true}"""))

            repository.chargeBillingCredits("20")

            val charges = gateway.requests.filter { it.method == "billing.charge" }
            assertEquals(2, charges.size)
            assertEquals(charges.first().params, charges.last().params)
            assertTrue(charges.first().params.toString().contains(pending.idempotencyKey))
            assertFalse(repository.state.value.billingChargeUnconfirmed)
            assertEquals(null, pendingStore.get(backend.id))
        }
    }

    @Test
    fun `pending charge restores before offline authentication and blocks forgetting backend`() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            val context = RuntimeEnvironment.getApplication()
            val backend = backend(server)
            val registry = BackendRegistry(context, json)
            val credentials = InMemoryCredentialStore()
            val pendingStore = BillingPendingChargeStore(context, json)
            registry.save(backend)
            pendingStore.put(
                PendingBillingCharge(
                    backendId = backend.id,
                    amountUsd = "20",
                    idempotencyKey = "same-key",
                    settlementDeadlineEpochMillis = System.currentTimeMillis() + 60_000L,
                ),
            )
            val repository = repository(context, registry, credentials, pendingStore, RecordingGateway(json))

            withTimeout(5_000L) {
                repository.state.first {
                    it.reconnectRequiredBackendId == backend.id && it.billingChargeUnconfirmed
                }
            }
            repository.forgetBackend(backend.id)

            assertTrue(registry.backends.first().any { it.id == backend.id })
            assertNotNull(pendingStore.get(backend.id))
            assertTrue(repository.state.value.error.orEmpty().contains("unconfirmed charge", ignoreCase = true))
        }
    }

    @Test
    fun `cancelling an in-flight charge keeps the persisted review lock`() = runBlocking {
        MockWebServer().use { server ->
            server.dispatcher = readyDashboardDispatcher()
            server.start()
            val context = RuntimeEnvironment.getApplication()
            val backend = backend(server)
            val registry = BackendRegistry(context, json)
            val credentials = InMemoryCredentialStore()
            val pendingStore = BillingPendingChargeStore(context, json)
            val gateway = RecordingGateway(json)
            registry.save(backend)
            credentials.put(backend.id, SESSION_COOKIE)
            val repository = repository(context, registry, credentials, pendingStore, gateway)
            awaitReady(repository, backend.id)
            gateway.enqueue("billing.state", billingState())
            gateway.enqueue("subscription.state", json.parseToJsonElement("""{"ok":true,"logged_in":true}"""))
            repository.refreshBilling()
            val requestStarted = CompletableDeferred<Unit>()
            gateway.enqueueBlock("billing.charge") {
                requestStarted.complete(Unit)
                CompletableDeferred<JsonElement>().await()
            }

            val charge = launch { repository.chargeBillingCredits("20") }
            withTimeout(5_000L) { requestStarted.await() }
            charge.cancelAndJoin()

            assertTrue(repository.state.value.billingChargeUnconfirmed)
            assertNotNull(pendingStore.get(backend.id))
            assertTrue(repository.state.value.billingError.orEmpty().contains("unconfirmed", ignoreCase = true))
        }
    }

    @Test
    fun `backend switch wins over a reconnect already holding the gateway lock`() = runBlocking {
        MockWebServer().use { server ->
            server.dispatcher = readyDashboardDispatcher()
            server.start()
            val context = RuntimeEnvironment.getApplication()
            val backendA = backend(server)
            val backendB = backendA.copy(id = "work", label = "Work")
            val registry = BackendRegistry(context, json)
            val credentials = InMemoryCredentialStore()
            val pendingStore = BillingPendingChargeStore(context, json)
            val gateway = RecordingGateway(json)
            registry.save(backendA)
            credentials.put(backendA.id, SESSION_COOKIE)
            credentials.put(backendB.id, SESSION_COOKIE)
            val repository = repository(context, registry, credentials, pendingStore, gateway)
            awaitReady(repository, backendA.id)
            val reconnectStarted = CompletableDeferred<Unit>()
            val releaseReconnect = CompletableDeferred<Unit>()
            gateway.blockNextReconnect(backendA.id, reconnectStarted, releaseReconnect)

            gateway.failConnection("network dropped")
            withTimeout(5_000L) { reconnectStarted.await() }
            registry.save(backendB)
            releaseReconnect.complete(Unit)
            awaitReady(repository, backendB.id)

            assertEquals(backendB.id, gateway.connectedBackendIds.last())
            val lastB = gateway.connectedBackendIds.indexOfLast { it == backendB.id }
            assertFalse(gateway.connectedBackendIds.drop(lastB + 1).contains(backendA.id))
        }
    }

    @Test
    fun `latest session open wins when an earlier resume finishes last`() = runBlocking {
        MockWebServer().use { server ->
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse = when (request.requestUrl?.encodedPath) {
                    "/api/status" -> MockResponse().setBody("""{"status":"ready","hermes_version":"0.18.2"}""")
                    "/api/profiles/sessions" -> MockResponse().setBody("""{"sessions":[]}""")
                    "/api/sessions/session-a/messages" -> MockResponse().setBody(
                        """{"session_id":"session-a","messages":[{"role":"user","text":"A"}]}""",
                    )
                    "/api/sessions/session-b/messages" -> MockResponse().setBody(
                        """{"session_id":"session-b","messages":[{"role":"user","text":"B"}]}""",
                    )
                    else -> MockResponse().setResponseCode(404)
                }
            }
            server.start()
            val context = RuntimeEnvironment.getApplication()
            val backend = backend(server)
            val registry = BackendRegistry(context, json)
            val credentials = InMemoryCredentialStore()
            val gateway = RecordingGateway(json)
            registry.save(backend)
            credentials.put(backend.id, SESSION_COOKIE)
            val repository = repository(context, registry, credentials, BillingPendingChargeStore(context, json), gateway)
            awaitReady(repository, backend.id)
            val firstStarted = CompletableDeferred<Unit>()
            val releaseFirst = CompletableDeferred<Unit>()
            gateway.enqueueBlock("session.resume") {
                firstStarted.complete(Unit)
                releaseFirst.await()
                json.parseToJsonElement(
                    """{"session_id":"live-a","session_key":"session-a","messages":[{"role":"user","text":"A"}]}""",
                )
            }
            gateway.enqueue(
                "session.resume",
                json.parseToJsonElement(
                    """{"session_id":"live-b","session_key":"session-b","messages":[{"role":"user","text":"B"}]}""",
                ),
            )
            repeat(2) { gateway.enqueue("model.options", json.parseToJsonElement("""{"providers":[]}""")) }

            val first = launch { repository.openSession(StoredSession(sessionId = "session-a")) }
            withTimeout(5_000L) { firstStarted.await() }
            val second = launch { repository.openSession(StoredSession(sessionId = "session-b")) }
            withTimeout(5_000L) { repository.state.first { it.runtimeSessionId == "live-b" } }
            releaseFirst.complete(Unit)
            first.join()
            second.join()

            assertEquals("session-b", repository.state.value.activeStoredSession?.durableId)
            assertEquals("live-b", repository.state.value.runtimeSessionId)
        }
    }

    @Test
    fun `completion received during history refresh survives the older resume snapshot`() = runBlocking {
        MockWebServer().use { server ->
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse = when (request.requestUrl?.encodedPath) {
                    "/api/status" -> MockResponse().setBody("""{"status":"ready","hermes_version":"0.18.2"}""")
                    "/api/profiles/sessions" -> MockResponse().setBody("""{"sessions":[]}""")
                    "/api/sessions/session-1/messages" -> MockResponse()
                        .setBodyDelay(1, TimeUnit.SECONDS)
                        .setBody(
                            """{"session_id":"session-1","messages":[{"role":"user","text":"Question"},{"role":"assistant","text":"Complete answer"}]}""",
                        )
                    else -> MockResponse().setResponseCode(404)
                }
            }
            server.start()
            val context = RuntimeEnvironment.getApplication()
            val backend = backend(server)
            val registry = BackendRegistry(context, json)
            val credentials = InMemoryCredentialStore()
            val gateway = RecordingGateway(json)
            registry.save(backend)
            credentials.put(backend.id, SESSION_COOKIE)
            val repository = repository(context, registry, credentials, BillingPendingChargeStore(context, json), gateway)
            awaitReady(repository, backend.id)
            gateway.enqueue(
                "session.resume",
                json.parseToJsonElement(
                    """{"session_id":"live-1","session_key":"session-1","messages":[],"running":true,"inflight":{"user":"Question","assistant":"Partial","streaming":true},"info":{"running":true}}""",
                ),
            )
            gateway.enqueue("model.options", json.parseToJsonElement("""{"providers":[]}"""))

            val opening = launch { repository.openSession(StoredSession(sessionId = "session-1")) }
            withTimeout(5_000L) {
                repository.state.first { state ->
                    state.runtimeSessionId == "live-1" && state.timeline.items.any {
                        it is TimelineItem.Message && it.text == "Partial" && it.streaming
                    }
                }
            }
            gateway.emit(
                GatewayEvent(
                    "message.complete",
                    "live-1",
                    buildJsonObject { put("text", "Complete answer"); put("status", "complete") },
                ),
            )
            withTimeout(5_000L) {
                repository.state.first { state ->
                    !state.runtimeInfo.running && state.timeline.items.any {
                        it is TimelineItem.Message && it.text == "Complete answer" && !it.streaming
                    }
                }
            }
            opening.join()

            val assistant = repository.state.value.timeline.items.filterIsInstance<TimelineItem.Message>().last()
            assertEquals("Complete answer", assistant.text)
            assertFalse(assistant.streaming)
            assertFalse(repository.state.value.runtimeInfo.running)
        }
    }

    private fun repository(
        context: Context,
        registry: BackendRegistry,
        credentials: SessionCredentialStore,
        pendingStore: BillingPendingChargeStore,
        gateway: HermesGatewayClient,
    ): HermesRepository {
        val client = OkHttpClient()
        val rest = HermesRestClient(client, json)
        return HermesRepository(
            backendRegistry = registry,
            tokenStore = credentials,
            restClient = rest,
            gateway = gateway,
            dashboardConnector = DashboardBackendConnector(
                DashboardAuthClient(client, json),
                rest,
                gateway,
                credentials,
                registry,
            ),
            json = json,
            attachmentReader = AttachmentReader(context),
            draftStore = DraftStore(context),
            composerQueueStore = ComposerQueueStore(context, json),
            privacyPreferences = PrivacyPreferences(
                PreferenceDataStoreFactory.create { context.filesDir.resolve("privacy-test.preferences_pb") },
            ),
            billingPendingChargeStore = pendingStore,
        )
    }

    private suspend fun awaitReady(repository: HermesRepository, backendId: String) {
        withTimeout(5_000L) {
            repository.state.first {
                it.backend?.id == backendId && !it.loading && !it.backendTransitionInProgress
            }
        }
    }

    private fun backend(server: MockWebServer) = BackendConfig(
        id = "personal",
        label = "Personal",
        baseUrl = server.url("/").toString().replace("localhost", "127.0.0.1").trimEnd('/'),
        authMode = AuthMode.DASHBOARD_SESSION,
        allowInsecurePrivateNetwork = true,
    )

    private fun billingState(): JsonElement = checkNotNull(
        javaClass.getResource("/fixtures/billing-state-5988fe6.json"),
    ).readText().let(json::parseToJsonElement)

    private fun readyDashboardDispatcher() = object : Dispatcher() {
        override fun dispatch(request: RecordedRequest): MockResponse = when (request.requestUrl?.encodedPath) {
            "/api/status" -> MockResponse().setBody("""{"status":"ready","hermes_version":"0.18.2"}""")
            "/api/profiles/sessions" -> MockResponse().setBody("""{"sessions":[]}""")
            else -> MockResponse().setResponseCode(404)
        }
    }

    private companion object {
        val SESSION_COOKIE = DashboardSessionCookie("hermes_session_at", "session-value")
    }
}

private class InMemoryCredentialStore : SessionCredentialStore {
    private val cookies = mutableMapOf<String, DashboardSessionCookie>()

    override fun put(backendId: String, cookie: DashboardSessionCookie) {
        cookies[backendId] = cookie
    }

    override fun get(backendId: String): DashboardSessionCookie? = cookies[backendId]

    override fun remove(backendId: String) {
        cookies.remove(backendId)
    }
}

private class RecordingGateway(
    private val json: Json,
) : HermesGatewayClient {
    private val mutableConnectionState = MutableStateFlow<GatewayConnectionState>(GatewayConnectionState.Idle)
    private val mutableEvents = MutableSharedFlow<GatewayEvent>(extraBufferCapacity = 8)
    private val responses = mutableMapOf<String, ArrayDeque<suspend () -> JsonElement>>()
    private var blockedReconnect: BlockedReconnect? = null
    val requests = mutableListOf<RecordedGatewayRequest>()
    val connectedBackendIds = mutableListOf<String>()

    override val connectionState: StateFlow<GatewayConnectionState> = mutableConnectionState
    override val events: SharedFlow<GatewayEvent> = mutableEvents

    fun enqueue(method: String, response: JsonElement) {
        responses.getOrPut(method, ::ArrayDeque).addLast { response }
    }

    fun enqueueFailure(method: String, error: Throwable) {
        responses.getOrPut(method, ::ArrayDeque).addLast { throw error }
    }

    fun enqueueBlock(method: String, response: suspend () -> JsonElement) {
        responses.getOrPut(method, ::ArrayDeque).addLast(response)
    }

    fun blockNextReconnect(
        backendId: String,
        started: CompletableDeferred<Unit>,
        release: CompletableDeferred<Unit>,
    ) {
        blockedReconnect = BlockedReconnect(backendId, started, release)
    }

    fun failConnection(reason: String) {
        mutableConnectionState.value = GatewayConnectionState.Failed(reason)
    }

    fun emit(event: GatewayEvent) {
        check(mutableEvents.tryEmit(event))
    }

    override suspend fun connect(config: BackendConfig, token: String) {
        connect(config)
    }

    override suspend fun connect(config: BackendConfig, cookie: DashboardSessionCookie) {
        connect(config)
    }

    private suspend fun connect(config: BackendConfig) {
        val blocked = blockedReconnect?.takeIf {
            it.backendId == config.id && connectedBackendIds.contains(config.id)
        }
        if (blocked != null) {
            blockedReconnect = null
            blocked.started.complete(Unit)
            blocked.release.await()
        }
        connectedBackendIds += config.id
        mutableConnectionState.value = GatewayConnectionState.Open
    }

    override suspend fun disconnect() {
        mutableConnectionState.value = GatewayConnectionState.Closed("test disconnect")
    }

    override suspend fun request(method: String, params: JsonElement): JsonElement {
        requests += RecordedGatewayRequest(method, params)
        return responses[method]?.removeFirstOrNull()?.invoke()
            ?: error("No fake response for $method: ${json.encodeToString(JsonElement.serializer(), params)}")
    }
}

private data class BlockedReconnect(
    val backendId: String,
    val started: CompletableDeferred<Unit>,
    val release: CompletableDeferred<Unit>,
)

private data class RecordedGatewayRequest(
    val method: String,
    val params: JsonElement,
)
