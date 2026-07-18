package com.nousresearch.hermes.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nousresearch.hermes.data.AuthMode
import com.nousresearch.hermes.data.BackendConfig
import com.nousresearch.hermes.data.DiagnosticAction
import com.nousresearch.hermes.data.HermesRepository
import com.nousresearch.hermes.protocol.StoredSession
import com.nousresearch.hermes.platform.SharedContent
import dagger.hilt.android.lifecycle.HiltViewModel
import java.security.MessageDigest
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement

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
    fun queueDraft() = viewModelScope.launch { repository.queueDraft() }
    fun updateQueuedPrompt(id: String, text: String) = viewModelScope.launch { repository.updateQueuedPrompt(id, text) }
    fun removeQueuedPrompt(id: String) = viewModelScope.launch { repository.removeQueuedPrompt(id) }
    fun sendQueuedPromptNow(id: String) = viewModelScope.launch { repository.sendQueuedPromptNow(id) }
    fun updateDraft(value: String) = repository.updateDraft(value)
    suspend fun ingestSharedContent(content: SharedContent): Boolean =
        repository.ingestSharedContent(content.text, content.uriStrings.map(Uri::parse))
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
    fun startProviderOAuth(providerId: String) = viewModelScope.launch { repository.startProviderOAuth(providerId) }
    fun submitProviderOAuth(code: String) = viewModelScope.launch { repository.submitProviderOAuth(code) }
    fun cancelProviderOAuth() = viewModelScope.launch { repository.cancelProviderOAuth() }
    fun disconnectProviderOAuth(providerId: String) = viewModelScope.launch { repository.disconnectProviderOAuth(providerId) }
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
    fun removeMcpServer(name: String) = viewModelScope.launch { repository.removeMcpServer(name) }
    fun installMcpCatalogEntry(name: String, env: Map<String, String>) = viewModelScope.launch {
        repository.installMcpCatalogEntry(name, env)
    }
    fun refreshToolsets() = viewModelScope.launch { repository.refreshToolsets() }
    fun setToolsetEnabled(name: String, enabled: Boolean) = viewModelScope.launch {
        repository.setToolsetEnabled(name, enabled)
    }
    fun refreshServerConfig() = viewModelScope.launch { repository.refreshServerConfig() }
    fun updateServerConfig(key: String, value: JsonElement) = viewModelScope.launch {
        repository.updateServerConfig(key, value)
    }
    fun refreshUsage(days: Int) = viewModelScope.launch { repository.refreshUsage(days) }
    fun refreshBilling() = viewModelScope.launch { repository.refreshBilling() }
    fun chargeBillingCredits(amount: String) = viewModelScope.launch { repository.chargeBillingCredits(amount) }
    fun updateBillingAutoReload(enabled: Boolean, threshold: String, reloadTo: String) = viewModelScope.launch {
        repository.updateBillingAutoReload(enabled, threshold, reloadTo)
    }
    fun startBillingStepUp() = viewModelScope.launch { repository.startBillingStepUp() }
    fun acknowledgeUnconfirmedBillingCharge() = viewModelScope.launch {
        repository.acknowledgeUnconfirmedBillingCharge()
    }
    fun refreshCheckpoints() = viewModelScope.launch { repository.refreshCheckpoints() }
    fun previewCheckpoint(hash: String) = viewModelScope.launch { repository.previewCheckpoint(hash) }
    fun restoreCheckpoint(hash: String) = viewModelScope.launch { repository.restoreCheckpoint(hash) }
    fun refreshAgents() = viewModelScope.launch { repository.refreshAgents() }
    fun refreshSpawnTrees() = viewModelScope.launch { repository.refreshSpawnTrees() }
    fun loadSpawnTree(path: String) = viewModelScope.launch { repository.loadSpawnTree(path) }
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
