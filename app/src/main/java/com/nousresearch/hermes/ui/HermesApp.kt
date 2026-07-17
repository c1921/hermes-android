package com.nousresearch.hermes.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nousresearch.hermes.data.HermesState
import com.nousresearch.hermes.data.DiagnosticAction
import com.nousresearch.hermes.data.PendingAttachment
import com.nousresearch.hermes.domain.MessageRole
import com.nousresearch.hermes.domain.TimelineItem
import com.nousresearch.hermes.domain.ToolState
import com.nousresearch.hermes.protocol.GatewayConnectionState
import com.nousresearch.hermes.protocol.StoredSession
import com.nousresearch.hermes.ui.theme.Danger
import com.nousresearch.hermes.ui.theme.HermesTheme
import com.nousresearch.hermes.ui.theme.NousBlue
import com.nousresearch.hermes.ui.theme.Success
import com.nousresearch.hermes.ui.theme.Warning as WarningColor

private val WideLayout = 840.dp
private enum class WorkspaceDestination { SESSIONS, CHAT, SKILLS, CRON, PROFILES, BACKENDS, DIAGNOSTICS, PROVIDERS }

private data class ModelActions(
    val refresh: () -> Unit,
    val select: (String, String) -> Unit,
    val confirm: () -> Unit,
    val cancel: () -> Unit,
    val reasoning: (String) -> Unit,
    val fast: (Boolean) -> Unit,
    val yolo: (Boolean) -> Unit,
)

private data class SessionActionCallbacks(
    val rename: (String) -> Unit,
    val branch: (String) -> Unit,
    val undo: () -> Unit,
    val compress: (String) -> Unit,
    val archive: () -> Unit,
)

private data class ManagementActions(
    val refreshSkills: () -> Unit,
    val toggleSkill: (String, Boolean) -> Unit,
    val loadSkillHub: (String) -> Unit,
    val reviewSkill: (String) -> Unit,
    val closeSkillReview: () -> Unit,
    val installReviewedSkill: () -> Unit,
    val uninstallSkill: (String) -> Unit,
    val updateSkills: () -> Unit,
    val refreshCron: () -> Unit,
    val refreshCronRuns: (String) -> Unit,
    val setCronEnabled: (String, Boolean) -> Unit,
    val triggerCron: (String) -> Unit,
    val createCron: (String, String, String, String) -> Unit,
    val updateCron: (String, String, String, String, String) -> Unit,
    val deleteCron: (String) -> Unit,
    val refreshProfiles: () -> Unit,
    val createProfile: (String, String, Boolean, Boolean) -> Unit,
    val renameProfile: (String, String) -> Unit,
    val setActiveProfile: (String) -> Unit,
    val deleteProfile: (String) -> Unit,
    val runDiagnostic: (DiagnosticAction) -> Unit,
    val refreshProviders: () -> Unit,
    val saveProviderSetting: (String, String, String) -> Unit,
    val deleteProviderSetting: (String) -> Unit,
)

@Composable
fun HermesApp(viewModel: HermesViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val connection by viewModel.connectionState.collectAsStateWithLifecycle()
    val modelActions = remember(viewModel) {
        ModelActions(
            refresh = viewModel::refreshModels,
            select = viewModel::selectModel,
            confirm = viewModel::confirmModel,
            cancel = viewModel::cancelModel,
            reasoning = viewModel::setReasoning,
            fast = viewModel::setFast,
            yolo = viewModel::setYolo,
        )
    }
    val sessionActions = remember(viewModel) {
        SessionActionCallbacks(
            rename = viewModel::renameActive,
            branch = viewModel::branchActive,
            undo = viewModel::undoLastTurn,
            compress = viewModel::compressActive,
            archive = viewModel::archiveActive,
        )
    }
    val managementActions = remember(viewModel) {
        ManagementActions(
            refreshSkills = viewModel::refreshSkills,
            toggleSkill = viewModel::toggleSkill,
            loadSkillHub = viewModel::loadSkillHub,
            reviewSkill = viewModel::reviewSkill,
            closeSkillReview = viewModel::closeSkillReview,
            installReviewedSkill = viewModel::installReviewedSkill,
            uninstallSkill = viewModel::uninstallSkill,
            updateSkills = viewModel::updateSkills,
            refreshCron = viewModel::refreshCron,
            refreshCronRuns = viewModel::refreshCronRuns,
            setCronEnabled = viewModel::setCronEnabled,
            triggerCron = viewModel::triggerCron,
            createCron = viewModel::createCron,
            updateCron = viewModel::updateCron,
            deleteCron = viewModel::deleteCron,
            refreshProfiles = viewModel::refreshProfiles,
            createProfile = viewModel::createProfile,
            renameProfile = viewModel::renameProfile,
            setActiveProfile = viewModel::setActiveProfile,
            deleteProfile = viewModel::deleteProfile,
            runDiagnostic = viewModel::runDiagnostic,
            refreshProviders = viewModel::refreshProviders,
            saveProviderSetting = viewModel::saveProviderSetting,
            deleteProviderSetting = viewModel::deleteProviderSetting,
        )
    }
    HermesTheme {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            if (state.backend == null && state.savedBackends.isEmpty()) {
                OnboardingScreen(
                    busy = state.loading,
                    error = state.error,
                    onConnect = viewModel::connect,
                )
            } else if (state.backend == null) {
                BackendsScreen(
                    state = state,
                    onConnect = viewModel::connect,
                    onSelect = viewModel::selectBackend,
                    onForget = viewModel::forgetBackend,
                    onBack = null,
                    modifier = Modifier.fillMaxSize().statusBarsPadding(),
                )
            } else {
                HermesWorkspace(
                    state = state,
                    connection = connection,
                    onRefresh = viewModel::refresh,
                    onSession = viewModel::openSession,
                    onNewSession = viewModel::newSession,
                    onSend = viewModel::send,
                    onSteer = viewModel::steer,
                    onAttach = viewModel::attach,
                    onRemoveAttachment = viewModel::removeAttachment,
                    onInterrupt = viewModel::interrupt,
                    onApprove = viewModel::approve,
                    onClarify = viewModel::clarify,
                    modelActions = modelActions,
                    sessionActions = sessionActions,
                    managementActions = managementActions,
                    onConnectBackend = viewModel::connect,
                    onSelectBackend = viewModel::selectBackend,
                    onForgetBackend = viewModel::forgetBackend,
                )
            }
        }
    }
}

@Composable
private fun OnboardingScreen(
    busy: Boolean,
    error: String?,
    onConnect: (String, String, String, String, Boolean) -> Unit,
) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    var label by rememberSaveable { mutableStateOf("My Hermes") }
    var url by rememberSaveable { mutableStateOf("") }
    var username by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var privateHttp by rememberSaveable { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        AnimatedContent(
            targetState = step,
            transitionSpec = {
                (slideInHorizontally(tween(320)) { it / 5 } + fadeIn(tween(220))) togetherWith
                    (slideOutHorizontally(tween(260)) { -it / 6 } + fadeOut(tween(160)))
            },
            label = "onboarding",
            modifier = Modifier.align(Alignment.Center).padding(24.dp).widthIn(max = 560.dp),
        ) { activeStep ->
            if (activeStep == 0) {
                Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                    BrandGlyph()
                    Text("HERMES / ANDROID", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.semantics { heading() })
                    Text(
                        "A native control surface for your existing Hermes Agent. The agent, sessions, tools, skills and memory remain on the backend you control.",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    ArchitectureStrip()
                    Button(onClick = { step = 1 }, modifier = Modifier.fillMaxWidth()) { Text("Connect to Hermes") }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { step = 0 }) { Icon(Icons.Outlined.ArrowBack, "Back") }
                        Text("BACKEND LINK", style = MaterialTheme.typography.headlineMedium)
                    }
                    HermesField(label, { label = it }, "Connection name")
                    HermesField(url, { url = it }, "https://hermes.example.com", KeyboardType.Uri)
                    HermesField(username, { username = it }, "Dashboard username")
                    HermesField(password, { password = it }, "Dashboard password", KeyboardType.Password, secret = true)
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable(role = Role.Switch) { privateHttp = !privateHttp }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Allow private-network HTTP", style = MaterialTheme.typography.titleMedium)
                            Text("Only literal LAN, loopback or Tailscale IPs. HTTPS is required otherwise.", style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(checked = privateHttp, onCheckedChange = { privateHttp = it })
                    }
                    error?.let { ErrorBanner(it) }
                    Button(
                        enabled = !busy && url.isNotBlank() && username.isNotBlank() && password.isNotEmpty(),
                        onClick = {
                            val submittedPassword = password
                            password = ""
                            onConnect(label, url, username, submittedPassword, privateHttp)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Test HTTP + WebSocket and save")
                    }
                    Text(
                        "Only the returned Dashboard session cookie is encrypted with Android Keystore. Your password is never saved or restored as UI state.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun HermesField(
    value: String,
    onValue: (String) -> Unit,
    label: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    secret: Boolean = false,
) {
    TextField(
        value = value,
        onValueChange = onValue,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Next),
        visualTransformation = if (secret) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
        ),
    )
}

@Composable
private fun BrandGlyph() {
    Surface(shape = RoundedCornerShape(10.dp), color = NousBlue, modifier = Modifier.size(72.dp)) {
        Box(contentAlignment = Alignment.Center) {
            Text("H", color = Color(0xFFFFE6CB), style = MaterialTheme.typography.headlineLarge)
        }
    }
}

@Composable
private fun ArchitectureStrip() {
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)).padding(14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("ANDROID", style = MaterialTheme.typography.labelMedium)
        Text("⇄  HTTPS / WSS  ⇄", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Text("HERMES SERVE", style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun HermesWorkspace(
    state: HermesState,
    connection: GatewayConnectionState,
    onRefresh: () -> Unit,
    onSession: (StoredSession) -> Unit,
    onNewSession: (String?) -> Unit,
    onSend: (String) -> Unit,
    onSteer: (String) -> Unit,
    onAttach: (android.net.Uri) -> Unit,
    onRemoveAttachment: (String) -> Unit,
    onInterrupt: () -> Unit,
    onApprove: (String) -> Unit,
    onClarify: (String) -> Unit,
    modelActions: ModelActions,
    sessionActions: SessionActionCallbacks,
    managementActions: ManagementActions,
    onConnectBackend: (String, String, String, String, Boolean) -> Unit,
    onSelectBackend: (String) -> Unit,
    onForgetBackend: (String) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= WideLayout
        var destination by rememberSaveable { mutableStateOf(WorkspaceDestination.SESSIONS) }
        LaunchedEffect(state.runtimeSessionId) {
            if (state.runtimeSessionId != null && destination == WorkspaceDestination.SESSIONS) {
                destination = WorkspaceDestination.CHAT
            }
        }
        BackHandler(enabled = !wide && destination != WorkspaceDestination.SESSIONS) {
            destination = WorkspaceDestination.SESSIONS
        }

        if (wide) {
            Row(Modifier.fillMaxSize().statusBarsPadding()) {
                SessionRail(
                    state, connection, onRefresh,
                    onSession = { onSession(it); destination = WorkspaceDestination.CHAT },
                    onNewSession = { onNewSession(null); destination = WorkspaceDestination.CHAT },
                    onSkills = { destination = WorkspaceDestination.SKILLS },
                    onCron = { destination = WorkspaceDestination.CRON },
                    onProfiles = { destination = WorkspaceDestination.PROFILES },
                    onBackends = { destination = WorkspaceDestination.BACKENDS },
                    onDiagnostics = { destination = WorkspaceDestination.DIAGNOSTICS },
                    onProviders = { destination = WorkspaceDestination.PROVIDERS },
                    modifier = Modifier.width(330.dp).fillMaxHeight(),
                )
                HorizontalDivider(Modifier.fillMaxHeight().width(1.dp))
                when (destination) {
                    WorkspaceDestination.SKILLS -> SkillsScreen(
                        state, managementActions.refreshSkills, managementActions.toggleSkill,
                        managementActions.loadSkillHub, managementActions.reviewSkill, managementActions.closeSkillReview,
                        managementActions.installReviewedSkill, managementActions.uninstallSkill, managementActions.updateSkills,
                        null, Modifier.weight(1f),
                    )
                    WorkspaceDestination.CRON -> CronScreen(
                        state, managementActions.refreshCron, managementActions.setCronEnabled,
                        managementActions.triggerCron, managementActions.refreshCronRuns,
                        { onSession(it); destination = WorkspaceDestination.CHAT },
                        managementActions.createCron,
                        managementActions.updateCron, managementActions.deleteCron,
                        null, Modifier.weight(1f),
                    )
                    WorkspaceDestination.PROFILES -> ProfilesScreen(
                        state = state,
                        onRefresh = managementActions.refreshProfiles,
                        onStartSession = { onNewSession(it); destination = WorkspaceDestination.CHAT },
                        onCreate = managementActions.createProfile,
                        onRename = managementActions.renameProfile,
                        onSetActive = managementActions.setActiveProfile,
                        onDelete = managementActions.deleteProfile,
                        onBack = null,
                        modifier = Modifier.weight(1f),
                    )
                    WorkspaceDestination.BACKENDS -> BackendsScreen(
                        state = state,
                        onConnect = onConnectBackend,
                        onSelect = onSelectBackend,
                        onForget = onForgetBackend,
                        onBack = null,
                        modifier = Modifier.weight(1f),
                    )
                    WorkspaceDestination.DIAGNOSTICS -> DiagnosticsScreen(
                        state = state,
                        connection = connection,
                        onRun = managementActions.runDiagnostic,
                        onBack = null,
                        modifier = Modifier.weight(1f),
                    )
                    WorkspaceDestination.PROVIDERS -> ProvidersScreen(
                        state = state,
                        onRefresh = managementActions.refreshProviders,
                        onSave = managementActions.saveProviderSetting,
                        onDelete = managementActions.deleteProviderSetting,
                        onBack = null,
                        modifier = Modifier.weight(1f),
                    )
                    else -> ChatSurface(
                        state, connection, onSend, onSteer, onAttach, onRemoveAttachment, onInterrupt,
                        onApprove, onClarify, modelActions, sessionActions, Modifier.weight(1f),
                    )
                }
            }
        } else {
            AnimatedContent(
                targetState = destination,
                transitionSpec = {
                    if (targetState != WorkspaceDestination.SESSIONS) {
                        slideInHorizontally(tween(260)) { it / 3 } togetherWith slideOutHorizontally(tween(220)) { -it / 4 }
                    } else {
                        slideInHorizontally(tween(260)) { -it / 3 } togetherWith slideOutHorizontally(tween(220)) { it / 4 }
                    }
                },
                label = "mobile-master-detail",
            ) { activeDestination ->
                when (activeDestination) {
                    WorkspaceDestination.CHAT -> ChatSurface(
                        state, connection, onSend, onSteer, onAttach, onRemoveAttachment, onInterrupt,
                        onApprove, onClarify, modelActions, sessionActions,
                        Modifier.fillMaxSize(),
                        onBack = { destination = WorkspaceDestination.SESSIONS },
                    )
                    WorkspaceDestination.SKILLS -> SkillsScreen(
                        state, managementActions.refreshSkills, managementActions.toggleSkill,
                        managementActions.loadSkillHub, managementActions.reviewSkill, managementActions.closeSkillReview,
                        managementActions.installReviewedSkill, managementActions.uninstallSkill, managementActions.updateSkills,
                        onBack = { destination = WorkspaceDestination.SESSIONS },
                        modifier = Modifier.fillMaxSize().statusBarsPadding(),
                    )
                    WorkspaceDestination.CRON -> CronScreen(
                        state, managementActions.refreshCron, managementActions.setCronEnabled,
                        managementActions.triggerCron, managementActions.refreshCronRuns,
                        { onSession(it); destination = WorkspaceDestination.CHAT },
                        managementActions.createCron,
                        managementActions.updateCron, managementActions.deleteCron,
                        onBack = { destination = WorkspaceDestination.SESSIONS },
                        modifier = Modifier.fillMaxSize().statusBarsPadding(),
                    )
                    WorkspaceDestination.PROFILES -> ProfilesScreen(
                        state = state,
                        onRefresh = managementActions.refreshProfiles,
                        onStartSession = { onNewSession(it); destination = WorkspaceDestination.CHAT },
                        onCreate = managementActions.createProfile,
                        onRename = managementActions.renameProfile,
                        onSetActive = managementActions.setActiveProfile,
                        onDelete = managementActions.deleteProfile,
                        onBack = { destination = WorkspaceDestination.SESSIONS },
                        modifier = Modifier.fillMaxSize().statusBarsPadding(),
                    )
                    WorkspaceDestination.BACKENDS -> BackendsScreen(
                        state = state,
                        onConnect = onConnectBackend,
                        onSelect = onSelectBackend,
                        onForget = onForgetBackend,
                        onBack = { destination = WorkspaceDestination.SESSIONS },
                        modifier = Modifier.fillMaxSize().statusBarsPadding(),
                    )
                    WorkspaceDestination.DIAGNOSTICS -> DiagnosticsScreen(
                        state = state,
                        connection = connection,
                        onRun = managementActions.runDiagnostic,
                        onBack = { destination = WorkspaceDestination.SESSIONS },
                        modifier = Modifier.fillMaxSize().statusBarsPadding(),
                    )
                    WorkspaceDestination.PROVIDERS -> ProvidersScreen(
                        state = state,
                        onRefresh = managementActions.refreshProviders,
                        onSave = managementActions.saveProviderSetting,
                        onDelete = managementActions.deleteProviderSetting,
                        onBack = { destination = WorkspaceDestination.SESSIONS },
                        modifier = Modifier.fillMaxSize().statusBarsPadding(),
                    )
                    WorkspaceDestination.SESSIONS -> SessionRail(
                        state, connection, onRefresh,
                        onSession = { onSession(it); destination = WorkspaceDestination.CHAT },
                        onNewSession = { onNewSession(null); destination = WorkspaceDestination.CHAT },
                        onSkills = { destination = WorkspaceDestination.SKILLS },
                        onCron = { destination = WorkspaceDestination.CRON },
                        onProfiles = { destination = WorkspaceDestination.PROFILES },
                        onBackends = { destination = WorkspaceDestination.BACKENDS },
                        onDiagnostics = { destination = WorkspaceDestination.DIAGNOSTICS },
                        onProviders = { destination = WorkspaceDestination.PROVIDERS },
                        modifier = Modifier.fillMaxSize().statusBarsPadding(),
                    )
                }
            }
        }
    }
}

@Composable
private fun SessionRail(
    state: HermesState,
    connection: GatewayConnectionState,
    onRefresh: () -> Unit,
    onSession: (StoredSession) -> Unit,
    onNewSession: () -> Unit,
    onSkills: () -> Unit,
    onCron: () -> Unit,
    onProfiles: () -> Unit,
    onBackends: () -> Unit,
    onDiagnostics: () -> Unit,
    onProviders: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.background(MaterialTheme.colorScheme.background)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BrandGlyphSmall()
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f).clickable(onClick = onBackends)) {
                Text("HERMES", style = MaterialTheme.typography.titleLarge)
                Text(state.backend?.label.orEmpty(), style = MaterialTheme.typography.bodySmall, maxLines = 1)
            }
            IconButton(onClick = onRefresh) { Icon(Icons.Outlined.Refresh, "Refresh sessions") }
            IconButton(onClick = onNewSession) { Icon(Icons.Outlined.Add, "New session") }
        }
        ConnectionLine(connection)
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(onClick = onSkills, modifier = Modifier.weight(1f)) {
                Icon(Icons.Outlined.AutoAwesome, null)
                Spacer(Modifier.width(6.dp))
                Text("Skills")
            }
            OutlinedButton(onClick = onCron, modifier = Modifier.weight(1f)) {
                Icon(Icons.Outlined.Schedule, null)
                Spacer(Modifier.width(6.dp))
                Text("Cron")
            }
            OutlinedButton(onClick = onProfiles, modifier = Modifier.weight(1f)) {
                Icon(Icons.Outlined.Person, null)
                Spacer(Modifier.width(6.dp))
                Text("Profiles")
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(onClick = onProviders, modifier = Modifier.weight(1f)) {
                Icon(Icons.Outlined.Key, null)
                Spacer(Modifier.width(6.dp))
                Text("Providers")
            }
            OutlinedButton(onClick = onDiagnostics, modifier = Modifier.weight(1f)) {
                Icon(Icons.Outlined.Info, null)
                Spacer(Modifier.width(6.dp))
                Text("Diagnostics")
            }
        }
        state.error?.let { ErrorBanner(it, Modifier.padding(12.dp)) }
        LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
            items(state.sessions, key = { "${it.profile}:${it.durableId}" }) { session ->
                SessionRow(session, selected = state.activeStoredSession?.durableId == session.durableId) { onSession(session) }
            }
            if (state.sessions.isEmpty() && !state.loading) {
                item {
                    Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("NO SESSIONS", style = MaterialTheme.typography.titleMedium)
                        Text("Start a conversation or connect another Hermes surface.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun BrandGlyphSmall() {
    Surface(shape = RoundedCornerShape(6.dp), color = NousBlue, modifier = Modifier.size(34.dp)) {
        Box(contentAlignment = Alignment.Center) { Text("H", color = Color(0xFFFFE6CB), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold) }
    }
}

@Composable
private fun ConnectionLine(connection: GatewayConnectionState) {
    val (colour, text) = when (connection) {
        GatewayConnectionState.Open -> Success to "LIVE / JSON-RPC"
        is GatewayConnectionState.Connecting -> WarningColor to "CONNECTING"
        is GatewayConnectionState.Reconnecting -> WarningColor to "RECONNECTING"
        is GatewayConnectionState.Failed -> Danger to "CONNECTION FAILED"
        is GatewayConnectionState.Closed -> Danger to "OFFLINE"
        GatewayConnectionState.Idle -> MaterialTheme.colorScheme.outline to "IDLE"
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(7.dp).clip(RoundedCornerShape(50)).background(colour))
        Spacer(Modifier.width(7.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = colour)
    }
}

@Composable
private fun SessionRow(session: StoredSession, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(session.displayTitle, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                session.profile?.let { Text(it.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary) }
                session.model?.let { Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
        }
        if (session.isActive) Box(Modifier.size(7.dp).clip(RoundedCornerShape(50)).background(Success))
    }
}

@Composable
private fun ChatSurface(
    state: HermesState,
    connection: GatewayConnectionState,
    onSend: (String) -> Unit,
    onSteer: (String) -> Unit,
    onAttach: (android.net.Uri) -> Unit,
    onRemoveAttachment: (String) -> Unit,
    onInterrupt: () -> Unit,
    onApprove: (String) -> Unit,
    onClarify: (String) -> Unit,
    modelActions: ModelActions,
    sessionActions: SessionActionCallbacks,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
) {
    Column(modifier.statusBarsPadding()) {
        ChatHeader(state, onBack, sessionActions)
        Box(Modifier.weight(1f)) {
            if (state.runtimeSessionId == null) {
                Column(Modifier.align(Alignment.Center).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    BrandGlyph()
                    Spacer(Modifier.height(18.dp))
                    Text("OPEN A SESSION", style = MaterialTheme.typography.headlineMedium)
                    Text("Select an existing conversation or create a new one.", style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                Timeline(state.timeline.items)
            }
            if (state.loading) CircularProgressIndicator(Modifier.align(Alignment.Center))
        }
        state.error?.let { ErrorBanner(it, Modifier.padding(horizontal = 12.dp)) }
        state.compatibilityWarning?.let { CompatibilityBanner(it, Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) }
        if (state.runtimeSessionId != null) {
            ModelControls(
                state = state,
                onRefresh = modelActions.refresh,
                onSelect = modelActions.select,
                onConfirmModel = modelActions.confirm,
                onCancelModel = modelActions.cancel,
                onReasoning = modelActions.reasoning,
                onFast = modelActions.fast,
                onYolo = modelActions.yolo,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            )
            Composer(
                sending = state.sending || state.runtimeInfo.running,
                attaching = state.attaching,
                connected = connection is GatewayConnectionState.Open,
                attachmentEnabled = state.supportsRemoteAttachments,
                attachments = state.pendingAttachments,
                onSend = onSend,
                onSteer = onSteer,
                onAttach = onAttach,
                onRemoveAttachment = onRemoveAttachment,
                onInterrupt = onInterrupt,
            )
        }
    }

    state.timeline.approval?.let { request ->
        ApprovalDialog(request.command, request.description, request.choices, onApprove)
    }
    state.timeline.clarification?.let { request ->
        ClarificationDialog(request.question, request.choices, onClarify)
    }
}

@Composable
private fun ChatHeader(
    state: HermesState,
    onBack: (() -> Unit)?,
    sessionActions: SessionActionCallbacks,
) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        onBack?.let { IconButton(onClick = it) { Icon(Icons.Outlined.ArrowBack, "Back to sessions") } }
        Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
            Text(
                state.activeStoredSession?.displayTitle ?: state.runtimeInfo.title.ifBlank { "New session" },
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
            )
            Text(
                listOf(state.activeStoredSession?.profile, state.runtimeInfo.provider, state.runtimeInfo.model)
                    .filterNotNull().filter(String::isNotBlank).joinToString(" / ").ifBlank { "Hermes Agent" },
                style = MaterialTheme.typography.bodySmall,
            )
        }
        SessionActions(
            state = state,
            onRename = sessionActions.rename,
            onBranch = sessionActions.branch,
            onUndo = sessionActions.undo,
            onCompress = sessionActions.compress,
            onArchive = sessionActions.archive,
        )
    }
    HorizontalDivider()
}

@Composable
private fun Timeline(items: List<TimelineItem>) {
    val listState = rememberLazyListState()
    LaunchedEffect(items.size, (items.lastOrNull() as? TimelineItem.Message)?.text?.length) {
        if (items.isNotEmpty()) listState.animateScrollToItem(items.lastIndex)
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(items, key = { it.id }) { item ->
            when (item) {
                is TimelineItem.Message -> MessageBlock(item)
                is TimelineItem.Tool -> ToolBlock(item)
                is TimelineItem.Reasoning -> ReasoningBlock(item)
                is TimelineItem.Status -> StatusBlock(item)
            }
        }
    }
}

@Composable
private fun MessageBlock(message: TimelineItem.Message) {
    val user = message.role == MessageRole.USER
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (user) Arrangement.End else Arrangement.Start) {
        Surface(
            modifier = Modifier.fillMaxWidth(if (user) 0.88f else 1f),
            color = if (user) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
            shape = RoundedCornerShape(if (user) 12.dp else 0.dp),
            border = if (message.failed) BorderStroke(1.dp, MaterialTheme.colorScheme.error) else null,
        ) {
            Column(Modifier.padding(if (user) 14.dp else 6.dp)) {
                Text(if (user) "YOU" else "HERMES", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(5.dp))
                RichText(message.text.ifBlank { if (message.streaming) "▍" else "" })
            }
        }
    }
}

@Composable
private fun RichText(text: String) {
    val parts = remember(text) { text.split("```") }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        parts.forEachIndexed { index, part ->
            if (index % 2 == 1) {
                Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(6.dp)) {
                    Text(part.trimStart().substringAfter('\n', part.trimStart()), Modifier.fillMaxWidth().padding(12.dp), style = MaterialTheme.typography.bodySmall)
                }
            } else if (part.isNotBlank()) {
                Text(part.trim(), style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
private fun ToolBlock(tool: TimelineItem.Tool) {
    var expanded by rememberSaveable(tool.id) { mutableStateOf(false) }
    val colour = when (tool.state) {
        ToolState.RUNNING -> WarningColor
        ToolState.COMPLETE -> Success
        ToolState.FAILED -> Danger
    }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable { expanded = !expanded }.padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Terminal, null, tint = colour, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(9.dp))
            Text(tool.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(tool.summary ?: tool.state.name, style = MaterialTheme.typography.labelMedium, color = colour)
        }
        tool.context?.let { Text(it, style = MaterialTheme.typography.bodySmall, maxLines = if (expanded) 20 else 2) }
        AnimatedVisibility(expanded && !tool.detail.isNullOrBlank(), enter = fadeIn(), exit = fadeOut()) {
            Text(tool.detail.orEmpty(), Modifier.padding(top = 10.dp), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ReasoningBlock(reasoning: TimelineItem.Reasoning) {
    var expanded by rememberSaveable(reasoning.id) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(vertical = 4.dp)) {
        Text("REASONING ${if (expanded) "−" else "+"}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (expanded) Text(reasoning.text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun StatusBlock(status: TimelineItem.Status) {
    Text("${status.kind.uppercase()} / ${status.text}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Composer(
    sending: Boolean,
    attaching: Boolean,
    connected: Boolean,
    attachmentEnabled: Boolean,
    attachments: List<PendingAttachment>,
    onSend: (String) -> Unit,
    onSteer: (String) -> Unit,
    onAttach: (android.net.Uri) -> Unit,
    onRemoveAttachment: (String) -> Unit,
    onInterrupt: () -> Unit,
) {
    var draft by rememberSaveable { mutableStateOf("") }
    val focus = LocalFocusManager.current
    fun submit() {
        if (draft.isBlank()) return
        if (sending) onSteer(draft) else onSend(draft)
        draft = ""
        focus.clearFocus()
    }
    val documentPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(onAttach)
    }
    Column(Modifier.fillMaxWidth().imePadding().navigationBarsPadding().padding(12.dp)) {
        if (attachments.isNotEmpty() || attaching) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(bottom = 8.dp),
            ) {
                attachments.forEach { attachment ->
                    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 10.dp)) {
                            Text(attachment.label, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                            IconButton(onClick = { onRemoveAttachment(attachment.id) }, modifier = Modifier.size(36.dp)) {
                                Icon(Icons.Outlined.Close, "Remove ${attachment.label}", modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
                if (attaching) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
            }
        }
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (attachmentEnabled) {
                IconButton(onClick = { documentPicker.launch(arrayOf("*/*")) }, enabled = connected && !attaching) {
                    Icon(Icons.Outlined.AttachFile, "Attach a file")
                }
            }
            TextField(
                value = draft,
                onValueChange = { draft = it },
                placeholder = {
                    Text(
                        when {
                            !connected -> "Reconnect to send"
                            sending -> "Steer the current run"
                            else -> "Message Hermes"
                        },
                    )
                },
                modifier = Modifier.weight(1f),
                enabled = connected,
                maxLines = 6,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { submit() }),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
            )
            if (sending) {
                IconButton(onClick = onInterrupt, modifier = Modifier.semantics { contentDescription = "Stop the current Hermes run" }) {
                    Icon(Icons.Outlined.StopCircle, null, tint = MaterialTheme.colorScheme.error)
                }
                IconButton(onClick = ::submit, enabled = connected && draft.isNotBlank()) {
                    Icon(Icons.Outlined.Send, "Steer the current run")
                }
            } else {
                IconButton(onClick = ::submit, enabled = connected && draft.isNotBlank()) { Icon(Icons.Outlined.Send, "Send message") }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ApprovalDialog(command: String, description: String?, choices: List<String>, onChoice: (String) -> Unit) {
    AlertDialog(
        onDismissRequest = { },
        icon = { Icon(Icons.Outlined.Terminal, null, tint = MaterialTheme.colorScheme.error) },
        title = { Text("COMMAND APPROVAL") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(description ?: "Hermes is waiting for permission to execute a potentially dangerous command.")
                Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(6.dp)) {
                    Text(command.ifBlank { "Command details were redacted by Hermes." }, Modifier.fillMaxWidth().padding(12.dp), style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                choices.filterNot { it == "deny" }.forEach { choice ->
                    Button(onClick = { onChoice(choice) }) { Text(choice.uppercase()) }
                }
            }
        },
        dismissButton = { OutlinedButton(onClick = { onChoice("deny") }) { Text("DENY") } },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ClarificationDialog(question: String, choices: List<String>, onAnswer: (String) -> Unit) {
    var answer by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { },
        title = { Text("HERMES NEEDS INPUT") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(question)
                if (choices.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        choices.forEach { choice -> OutlinedButton(onClick = { answer = choice }) { Text(choice) } }
                    }
                }
                TextField(answer, { answer = it }, Modifier.fillMaxWidth(), label = { Text("Answer") })
            }
        },
        confirmButton = { Button(enabled = answer.isNotBlank(), onClick = { onAnswer(answer.trim()) }) { Text("CONTINUE") } },
    )
}

@Composable
private fun ErrorBanner(message: String, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(6.dp)).padding(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(Icons.Outlined.ErrorOutline, null, tint = MaterialTheme.colorScheme.error)
        Spacer(Modifier.width(8.dp))
        Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
    }
}

@Composable
private fun CompatibilityBanner(message: String, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp)).padding(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(Icons.Outlined.Warning, null, tint = WarningColor)
        Spacer(Modifier.width(8.dp))
        Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
