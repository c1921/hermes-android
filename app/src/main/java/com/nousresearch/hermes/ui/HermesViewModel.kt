package com.nousresearch.hermes.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nousresearch.hermes.data.AuthMode
import com.nousresearch.hermes.data.BackendConfig
import com.nousresearch.hermes.data.HermesRepository
import com.nousresearch.hermes.protocol.StoredSession
import dagger.hilt.android.lifecycle.HiltViewModel
import java.security.MessageDigest
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class HermesViewModel @Inject constructor(
    private val repository: HermesRepository,
) : ViewModel() {
    val state = repository.state.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), repository.state.value)
    val connectionState = repository.connectionState

    fun connect(label: String, baseUrl: String, token: String, allowPrivateHttp: Boolean) {
        viewModelScope.launch {
            val normalized = baseUrl.trim().trimEnd('/')
            val config = BackendConfig(
                id = normalized.sha256().take(20),
                label = label.trim().ifBlank { "Hermes" },
                baseUrl = normalized,
                authMode = AuthMode.TOKEN,
                allowInsecurePrivateNetwork = allowPrivateHttp,
            )
            runCatching { repository.testAndSave(config, token.trim()) }
        }
    }

    fun refresh() = viewModelScope.launch { repository.refreshSessions() }
    fun openSession(session: StoredSession) = viewModelScope.launch { repository.openSession(session) }
    fun newSession() = viewModelScope.launch { repository.newSession() }
    fun send(text: String) = viewModelScope.launch { repository.send(text) }
    fun interrupt() = viewModelScope.launch { repository.interrupt() }
    fun approve(choice: String) = viewModelScope.launch { repository.respondToApproval(choice) }
    fun clarify(answer: String) = viewModelScope.launch { repository.respondToClarification(answer) }
    fun archiveActive() = viewModelScope.launch { repository.archiveActive() }
    fun disconnect() = viewModelScope.launch { repository.disconnectAndForget() }
}

private fun String.sha256(): String = MessageDigest.getInstance("SHA-256")
    .digest(toByteArray())
    .joinToString("") { "%02x".format(it) }

