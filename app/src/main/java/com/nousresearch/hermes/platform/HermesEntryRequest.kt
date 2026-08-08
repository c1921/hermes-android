package com.nousresearch.hermes.platform

import com.nousresearch.hermes.ui.navigation.HermesDestinationRoute
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface HermesEntryRequest {
    val id: String

    data class OpenDestination(
        override val id: String,
        val route: HermesDestinationRoute,
    ) : HermesEntryRequest

    data class ImportDraft(
        override val id: String,
        val content: SharedContent,
    ) : HermesEntryRequest

    data class NewChat(
        override val id: String = NEW_CHAT_ID,
    ) : HermesEntryRequest
}

data class HermesEntryDelivery(
    val request: HermesEntryRequest,
    val attempt: Int = 0,
    val failureMessage: String? = null,
)

@Singleton
class HermesEntryRequestStore @Inject constructor() {
    private val lock = Any()
    private val mutableDeliveries = MutableStateFlow<List<HermesEntryDelivery>>(emptyList())
    val deliveries: StateFlow<List<HermesEntryDelivery>> = mutableDeliveries.asStateFlow()

    fun enqueue(request: HermesEntryRequest): Boolean = synchronized(lock) {
        val current = mutableDeliveries.value
        if (current.any { it.request.id == request.id } || current.size >= MAX_PENDING_ENTRY_REQUESTS) {
            false
        } else {
            mutableDeliveries.value = current + HermesEntryDelivery(request)
            true
        }
    }

    fun consume(id: String) = remove(id)

    fun discard(id: String) = remove(id)

    fun fail(id: String, message: String) = synchronized(lock) {
        val safeMessage = message.replace("\u0000", "").trim().take(MAX_FAILURE_MESSAGE_CHARACTERS)
        mutableDeliveries.value = mutableDeliveries.value.map { delivery ->
            if (delivery.request.id == id) delivery.copy(failureMessage = safeMessage) else delivery
        }
    }

    fun retry(id: String) = synchronized(lock) {
        mutableDeliveries.value = mutableDeliveries.value.map { delivery ->
            if (delivery.request.id == id && delivery.failureMessage != null) {
                delivery.copy(attempt = delivery.attempt + 1, failureMessage = null)
            } else {
                delivery
            }
        }
    }

    private fun remove(id: String) = synchronized(lock) {
        mutableDeliveries.value = mutableDeliveries.value.filterNot { it.request.id == id }
    }
}

internal fun stableEntryId(prefix: String, vararg values: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
    values.forEach { value ->
        val bytes = value.toByteArray(Charsets.UTF_8)
        digest.update(bytes.size.toString().toByteArray(Charsets.US_ASCII))
        digest.update(0)
        digest.update(bytes)
        digest.update(0)
    }
    return "$prefix:${digest.digest().joinToString("") { byte -> "%02x".format(byte) }.take(24)}"
}

private const val NEW_CHAT_ID = "new-chat"
private const val MAX_PENDING_ENTRY_REQUESTS = 3
private const val MAX_FAILURE_MESSAGE_CHARACTERS = 500
