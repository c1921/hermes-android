package com.nousresearch.hermes.protocol

import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer

class FakeHermesBackend(
    private val json: Json,
) : AutoCloseable {
    private val server = MockWebServer()
    val requests = CopyOnWriteArrayList<JsonObject>()

    val baseUrl: String
        get() = server.url("/").toString().replace("localhost", "127.0.0.1").trimEnd('/')

    fun start(connectionCount: Int = 1) {
        repeat(connectionCount) {
            server.enqueue(
                MockResponse().withWebSocketUpgrade(
                    object : WebSocketListener() {
                        override fun onOpen(webSocket: WebSocket, response: Response) {
                            webSocket.send(
                                """{"jsonrpc":"2.0","method":"event","params":{"type":"gateway.ready","payload":{"skin":"nous"}}}""",
                            )
                        }

                        override fun onMessage(webSocket: WebSocket, text: String) {
                            val request = json.parseToJsonElement(text).jsonObject
                            requests += request
                            val id = request.getValue("id").jsonPrimitive.long
                            val method = request.getValue("method").jsonPrimitive.content
                        val result = when (method) {
                            "session.list" -> buildJsonObject { put("sessions", JsonArray(emptyList())) }
                            "session.interrupt" -> buildJsonObject { put("status", "interrupting") }
                            "model.options" -> json.parseToJsonElement(
                                """{"model":"hermes-4","provider":"nous","providers":[{"slug":"nous","name":"Nous Portal","authenticated":true,"models":["hermes-4"],"capabilities":{"hermes-4":{"fast":true,"reasoning":true}}}]}""",
                            )
                            else -> buildJsonObject { put("ok", true) }
                            }
                            webSocket.send(
                                json.encodeToString(
                                    JsonObject.serializer(),
                                    buildJsonObject {
                                        put("jsonrpc", "2.0")
                                        put("id", id)
                                        put("result", result)
                                    },
                                ),
                            )
                        }
                    },
                ),
            )
        }
        server.start()
    }

    override fun close() {
        server.shutdown()
    }
}
