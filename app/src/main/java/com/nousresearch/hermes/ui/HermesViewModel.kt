package com.nousresearch.hermes.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nousresearch.hermes.data.AuthMode
import com.nousresearch.hermes.data.BackendConfig
import com.nousresearch.hermes.data.DiagnosticAction
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

    fun connect(label: String, baseUrl: String, username: String, password: String, allowPrivateHttp: Boolean) {
        viewModelScope.launch {
            val normalized = baseUrl.trim().trimEnd('/')
            val config = BackendConfig(
                id = normalized.sha256().take(20),
                label = label.trim().ifBlank { "Hermes" },
                baseUrl = normalized,
                authMode = AuthMode.DASHBOARD_SESSION,
                allowInsecurePrivateNetwork = allowPrivateHttp,
            )
            runCatching { repository.testAndSave(config, username.trim(), password) }
        }
    }

    fun refresh() = viewModelScope.launch { repository.refreshSessions() }
    fun searchSessions(query: String) = repository.searchSessions(query)
    fun openSession(session: StoredSession) = viewModelScope.launch { repository.openSession(session) }
    fun newSession(profile: String? = null) = viewModelScope.launch { repository.newSession(profile) }
    fun send(text: String) = viewModelScope.launch { repository.send(text) }
    fun steer(text: String) = viewModelScope.launch { repository.steer(text) }
    fun updateDraft(value: String) = repository.updateDraft(value)
    fun completeSlash(text: String) = repository.completeSlash(text)
    fun executeSlash(command: String) = viewModelScope.launch { repository.executeSlash(command) }
    fun attach(uri: Uri) = viewModelScope.launch { repository.attach(uri) }
    fun removeAttachment(id: String) = viewModelScope.launch { repository.removePendingAttachment(id) }
    fun refreshModels() = viewModelScope.launch { repository.refreshModelOptions(refresh = true) }
    fun selectModel(provider: String, model: String) = viewModelScope.launch { repository.selectModel(provider, model) }
    fun confirmModel() = viewModelScope.launch { repository.confirmModelSelection() }
    fun cancelModel() = repository.cancelModelSelection()
    fun setReasoning(effort: String) = viewModelScope.launch { repository.setReasoningEffort(effort) }
    fun setFast(enabled: Boolean) = viewModelScope.launch { repository.setFastMode(enabled) }
    fun setYolo(enabled: Boolean) = viewModelScope.launch { repository.setYolo(enabled) }
    fun interrupt() = viewModelScope.launch { repository.interrupt() }
    fun approve(choice: String) = viewModelScope.launch { repository.respondToApproval(choice) }
    fun clarify(answer: String) = viewModelScope.launch { repository.respondToClarification(answer) }
    fun submitSensitiveInput(value: String) = viewModelScope.launch { repository.respondToSensitiveInput(value) }
    fun archiveActive() = viewModelScope.launch { repository.archiveActive() }
    fun deleteSession(session: StoredSession) = viewModelScope.launch { repository.deleteSession(session) }
    fun renameActive(title: String) = viewModelScope.launch { repository.renameActive(title) }
    fun branchActive(name: String) = viewModelScope.launch { repository.branchActive(name) }
    fun undoLastTurn() = viewModelScope.launch { repository.undoLastTurn() }
    fun retryLastMessage() = viewModelScope.launch { repository.retryLastMessage() }
    fun resetActive() = viewModelScope.launch { repository.newSession(repository.state.value.activeStoredSession?.profile) }
    fun compressActive(focusTopic: String) = viewModelScope.launch { repository.compressActive(focusTopic) }
    fun refreshSkills() = viewModelScope.launch { repository.refreshSkills() }
    fun toggleSkill(name: String, enabled: Boolean) = viewModelScope.launch { repository.toggleSkill(name, enabled) }
    fun loadSkillHub(query: String) = viewModelScope.launch { repository.loadSkillHub(query) }
    fun reviewSkill(identifier: String) = viewModelScope.launch { repository.reviewSkill(identifier) }
    fun closeSkillReview() = repository.closeSkillReview()
    fun installReviewedSkill() = viewModelScope.launch { repository.installReviewedSkill() }
    fun uninstallSkill(name: String) = viewModelScope.launch { repository.uninstallSkill(name) }
    fun updateSkills() = viewModelScope.launch { repository.updateSkills() }
    fun refreshCron() = viewModelScope.launch { repository.refreshCronJobs() }
    fun refreshCronRuns(jobId: String) = viewModelScope.launch { repository.refreshCronRuns(jobId) }
    fun setCronEnabled(jobId: String, enabled: Boolean) = viewModelScope.launch { repository.setCronEnabled(jobId, enabled) }
    fun triggerCron(jobId: String) = viewModelScope.launch { repository.triggerCron(jobId) }
    fun createCron(name: String, prompt: String, schedule: String, deliver: String) = viewModelScope.launch {
        repository.createCron(name, prompt, schedule, deliver)
    }
    fun updateCron(jobId: String, name: String, prompt: String, schedule: String, deliver: String) = viewModelScope.launch {
        repository.updateCron(jobId, name, prompt, schedule, deliver)
    }
    fun deleteCron(jobId: String) = viewModelScope.launch { repository.deleteCron(jobId) }
    fun refreshProfiles() = viewModelScope.launch { repository.refreshProfiles() }
    fun createProfile(name: String, cloneFrom: String, cloneAll: Boolean, noSkills: Boolean) = viewModelScope.launch {
        repository.createProfile(name, cloneFrom, cloneAll, noSkills)
    }
    fun renameProfile(name: String, newName: String) = viewModelScope.launch { repository.renameProfile(name, newName) }
    fun setActiveProfile(name: String) = viewModelScope.launch { repository.setActiveProfile(name) }
    fun deleteProfile(name: String) = viewModelScope.launch { repository.deleteProfile(name) }
    fun runDiagnostic(action: DiagnosticAction) = viewModelScope.launch { repository.runDiagnostic(action) }
    fun refreshProviders() = viewModelScope.launch { repository.refreshProviders(refresh = true) }
    fun saveProviderSetting(key: String, value: String, apiKey: String) = viewModelScope.launch {
        repository.saveProviderSetting(key, value, apiKey)
    }
    fun deleteProviderSetting(key: String) = viewModelScope.launch { repository.deleteProviderSetting(key) }
    fun refreshMessaging() = viewModelScope.launch { repository.refreshMessaging() }
    fun setMessagingEnabled(platformId: String, enabled: Boolean) = viewModelScope.launch {
        repository.setMessagingEnabled(platformId, enabled)
    }
    fun saveMessagingSettings(platformId: String, values: Map<String, String>) = viewModelScope.launch {
        repository.saveMessagingSettings(platformId, values)
    }
    fun clearMessagingSetting(platformId: String, key: String) = viewModelScope.launch {
        repository.clearMessagingSetting(platformId, key)
    }
    fun testMessagingPlatform(platformId: String) = viewModelScope.launch { repository.testMessagingPlatform(platformId) }
    fun restartMessagingGateway() = viewModelScope.launch { repository.restartMessagingGateway() }
    fun refreshMcp() = viewModelScope.launch { repository.refreshMcp() }
    fun testMcpServer(name: String) = viewModelScope.launch { repository.testMcpServer(name) }
    fun setMcpServerEnabled(name: String, enabled: Boolean) = viewModelScope.launch {
        repository.setMcpServerEnabled(name, enabled)
    }
    fun refreshAgents() = viewModelScope.launch { repository.refreshAgents() }
    fun setDelegationPaused(paused: Boolean) = viewModelScope.launch { repository.setDelegationPaused(paused) }
    fun interruptSubagent(id: String) = viewModelScope.launch { repository.interruptSubagent(id) }
    fun stopBackgroundProcess(id: String) = viewModelScope.launch { repository.stopBackgroundProcess(id) }
    fun selectBackend(id: String) = viewModelScope.launch { repository.selectBackend(id) }
    fun forgetBackend(id: String) = viewModelScope.launch { repository.forgetBackend(id) }
    fun disconnect() = viewModelScope.launch { repository.disconnectAndForget() }
}

private fun String.sha256(): String = MessageDigest.getInstance("SHA-256")
    .digest(toByteArray())
    .joinToString("") { "%02x".format(it) }
