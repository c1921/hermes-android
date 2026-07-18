package com.nousresearch.hermes.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nousresearch.hermes.data.HermesState
import com.nousresearch.hermes.R
import com.nousresearch.hermes.data.DiagnosticAction
import com.nousresearch.hermes.data.PendingAttachment
import com.nousresearch.hermes.data.SlashSuggestion
import com.nousresearch.hermes.domain.MessageRole
import com.nousresearch.hermes.domain.ComposerBrowseState
import com.nousresearch.hermes.domain.ComposerHistory
import com.nousresearch.hermes.domain.ComposerQueue
import com.nousresearch.hermes.domain.QueuedPrompt
import com.nousresearch.hermes.domain.SensitiveInputKind
import com.nousresearch.hermes.domain.TimelineItem
import com.nousresearch.hermes.domain.ToolState
import com.nousresearch.hermes.protocol.GatewayConnectionState
import com.nousresearch.hermes.protocol.SessionSearchHit
import com.nousresearch.hermes.protocol.StoredSession
import com.nousresearch.hermes.ui.theme.Danger
import com.nousresearch.hermes.ui.theme.HermesTheme
import com.nousresearch.hermes.ui.theme.Warning as WarningColor

private val WideLayout = 840.dp
private const val MAX_VISIBLE_COMPOSER_HISTORY = 20
private enum class WorkspaceDestination { SESSIONS, CHAT, SKILLS, CRON, PROFILES, BACKENDS, FILES, DIAGNOSTICS, PROVIDERS, MESSAGING, MCP, USAGE, AGENTS }

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
    val retry: () -> Unit,
    val undo: () -> Unit,
    val compress: (String) -> Unit,
    val reset: () -> Unit,
    val archive: () -> Unit,
    val refreshCheckpoints: () -> Unit,
    val previewCheckpoint: (String) -> Unit,
    val restoreCheckpoint: (String) -> Unit,
)

private data class QueueActions(
    val enqueueDraft: () -> Unit,
    val update: (String, String) -> Unit,
    val remove: (String) -> Unit,
    val sendNow: (String) -> Unit,
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
    val refreshMessaging: () -> Unit,
    val setMessagingEnabled: (String, Boolean) -> Unit,
    val saveMessagingSettings: (String, Map<String, String>) -> Unit,
    val clearMessagingSetting: (String, String) -> Unit,
    val testMessagingPlatform: (String) -> Unit,
    val restartMessagingGateway: () -> Unit,
    val refreshMcp: () -> Unit,
    val testMcpServer: (String) -> Unit,
    val setMcpServerEnabled: (String, Boolean) -> Unit,
    val removeMcpServer: (String) -> Unit,
    val installMcpCatalogEntry: (String, Map<String, String>) -> Unit,
    val refreshToolsets: () -> Unit,
    val setToolsetEnabled: (String, Boolean) -> Unit,
    val refreshUsage: (Int) -> Unit,
    val refreshAgents: () -> Unit,
    val setDelegationPaused: (Boolean) -> Unit,
    val interruptSubagent: (String) -> Unit,
    val stopBackgroundProcess: (String) -> Unit,
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
            retry = viewModel::retryLastMessage,
            undo = viewModel::undoLastTurn,
            compress = viewModel::compressActive,
            reset = viewModel::resetActive,
            archive = viewModel::archiveActive,
            refreshCheckpoints = viewModel::refreshCheckpoints,
            previewCheckpoint = viewModel::previewCheckpoint,
            restoreCheckpoint = viewModel::restoreCheckpoint,
        )
    }
    val queueActions = remember(viewModel) {
        QueueActions(
            enqueueDraft = viewModel::queueDraft,
            update = viewModel::updateQueuedPrompt,
            remove = viewModel::removeQueuedPrompt,
            sendNow = viewModel::sendQueuedPromptNow,
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
            refreshMessaging = viewModel::refreshMessaging,
            setMessagingEnabled = viewModel::setMessagingEnabled,
            saveMessagingSettings = viewModel::saveMessagingSettings,
            clearMessagingSetting = viewModel::clearMessagingSetting,
            testMessagingPlatform = viewModel::testMessagingPlatform,
            restartMessagingGateway = viewModel::restartMessagingGateway,
            refreshMcp = viewModel::refreshMcp,
            testMcpServer = viewModel::testMcpServer,
            setMcpServerEnabled = viewModel::setMcpServerEnabled,
            removeMcpServer = viewModel::removeMcpServer,
            installMcpCatalogEntry = viewModel::installMcpCatalogEntry,
            refreshToolsets = viewModel::refreshToolsets,
            setToolsetEnabled = viewModel::setToolsetEnabled,
            refreshUsage = viewModel::refreshUsage,
            refreshAgents = viewModel::refreshAgents,
            setDelegationPaused = viewModel::setDelegationPaused,
            interruptSubagent = viewModel::interruptSubagent,
            stopBackgroundProcess = viewModel::stopBackgroundProcess,
        )
    }
    HermesTheme {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Box(Modifier.fillMaxSize()) {
                NousBackdrop(Modifier.fillMaxSize())
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
                        onSearchSessions = viewModel::searchSessions,
                        onSession = viewModel::openSession,
                        onDeleteSession = viewModel::deleteSession,
                        onNewSession = viewModel::newSession,
                        onSend = viewModel::send,
                        onSteer = viewModel::steer,
                        onDraftChange = viewModel::updateDraft,
                        onCompleteSlash = viewModel::completeSlash,
                        onExecuteSlash = viewModel::executeSlash,
                        onAttach = viewModel::attach,
                        onRemoveAttachment = viewModel::removeAttachment,
                        onInterrupt = viewModel::interrupt,
                        onApprove = viewModel::approve,
                        onClarify = viewModel::clarify,
                        onSensitiveInput = viewModel::submitSensitiveInput,
                        modelActions = modelActions,
                        sessionActions = sessionActions,
                        queueActions = queueActions,
                        managementActions = managementActions,
                        onConnectBackend = viewModel::connect,
                        onSelectBackend = viewModel::selectBackend,
                        onForgetBackend = viewModel::forgetBackend,
                    )
                }
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
                val direction = if (targetState > initialState) {
                    AnimatedContentTransitionScope.SlideDirection.Left
                } else {
                    AnimatedContentTransitionScope.SlideDirection.Right
                }
                (slideIntoContainer(direction, tween(320)) + fadeIn(tween(220))) togetherWith
                    (slideOutOfContainer(direction, tween(320)) + fadeOut(tween(220)))
            },
            label = "onboarding",
            modifier = Modifier.align(Alignment.Center).padding(18.dp).widthIn(max = 560.dp),
        ) { activeStep ->
            if (activeStep == 0) {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        BrandGlyphSmall()
                        Column {
                            Text("HERMES", style = MaterialTheme.typography.titleLarge)
                            Text("AGENT / ANDROID", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    Text("OPEN SOURCE  ·  NATIVE ANDROID", style = MaterialTheme.typography.labelMedium)
                    Text(
                        "THE AGENT\nTHAT GROWS\nWITH YOU",
                        style = MaterialTheme.typography.headlineLarge,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(
                        "Your Hermes sessions, tools, skills, memory and approvals. Native on Android; agent state stays on the backend you control.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Button(onClick = { step = 1 }, modifier = Modifier.fillMaxWidth()) { Text("CONNECT TO HERMES") }
                    Image(
                        painter = painterResource(R.drawable.hermes_hero_art),
                        contentDescription = null,
                        modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp),
                        contentScale = ContentScale.Fit,
                    )
                    ArchitectureStrip()
                }
            } else {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()).imePadding(),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { step = 0 }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") }
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
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Next),
        visualTransformation = if (secret) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
        ),
    )
}

@Composable
private fun BrandGlyph() {
    Image(
        painter = painterResource(R.drawable.hermes_badge),
        contentDescription = "Hermes Agent",
        modifier = Modifier.size(72.dp).clip(RoundedCornerShape(16.dp)),
        contentScale = ContentScale.Crop,
    )
}

@Composable
private fun ArchitectureStrip() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = Color.Transparent,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Row(
            Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("ANDROID", style = MaterialTheme.typography.labelMedium)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Icon(Icons.Outlined.SwapHoriz, null, modifier = Modifier.size(16.dp))
                Text("HTTPS / WSS", style = MaterialTheme.typography.labelMedium)
            }
            Text("HERMES SERVE", style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun HermesWorkspace(
    state: HermesState,
    connection: GatewayConnectionState,
    onRefresh: () -> Unit,
    onSearchSessions: (String) -> Unit,
    onSession: (StoredSession) -> Unit,
    onDeleteSession: (StoredSession) -> Unit,
    onNewSession: (String?) -> Unit,
    onSend: (String) -> Unit,
    onSteer: (String) -> Unit,
    onDraftChange: (String) -> Unit,
    onCompleteSlash: (String) -> Unit,
    onExecuteSlash: (String) -> Unit,
    onAttach: (android.net.Uri) -> Unit,
    onRemoveAttachment: (String) -> Unit,
    onInterrupt: () -> Unit,
    onApprove: (String) -> Unit,
    onClarify: (String) -> Unit,
    onSensitiveInput: (String) -> Unit,
    modelActions: ModelActions,
    sessionActions: SessionActionCallbacks,
    queueActions: QueueActions,
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
                    state, connection, onRefresh, onSearchSessions,
                    onSession = { onSession(it); destination = WorkspaceDestination.CHAT },
                    onDeleteSession = onDeleteSession,
                    onNewSession = { onNewSession(null); destination = WorkspaceDestination.CHAT },
                    onSkills = { destination = WorkspaceDestination.SKILLS },
                    onCron = { destination = WorkspaceDestination.CRON },
                    onProfiles = { destination = WorkspaceDestination.PROFILES },
                    onBackends = { destination = WorkspaceDestination.BACKENDS },
                    onFiles = { destination = WorkspaceDestination.FILES },
                    onDiagnostics = { destination = WorkspaceDestination.DIAGNOSTICS },
                    onProviders = { destination = WorkspaceDestination.PROVIDERS },
                    onMessaging = { destination = WorkspaceDestination.MESSAGING },
                    onMcp = { destination = WorkspaceDestination.MCP },
                    onUsage = { destination = WorkspaceDestination.USAGE },
                    onAgents = { destination = WorkspaceDestination.AGENTS },
                    modifier = Modifier.width(330.dp).fillMaxHeight(),
                )
                HorizontalDivider(Modifier.fillMaxHeight().width(1.dp))
                when (destination) {
                    WorkspaceDestination.SKILLS -> SkillsScreen(
                        state, managementActions.refreshSkills, managementActions.toggleSkill,
                        managementActions.refreshToolsets, managementActions.setToolsetEnabled,
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
                    WorkspaceDestination.FILES -> WorkspaceFilesScreen(
                        backend = requireNotNull(state.backend),
                        initialPath = state.runtimeInfo.cwd.takeIf(String::isNotBlank),
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
                    WorkspaceDestination.MESSAGING -> MessagingScreen(
                        state = state,
                        onRefresh = managementActions.refreshMessaging,
                        onSetEnabled = managementActions.setMessagingEnabled,
                        onSave = managementActions.saveMessagingSettings,
                        onClear = managementActions.clearMessagingSetting,
                        onTest = managementActions.testMessagingPlatform,
                        onRestartGateway = managementActions.restartMessagingGateway,
                        onBack = null,
                        modifier = Modifier.weight(1f),
                    )
                    WorkspaceDestination.MCP -> McpScreen(
                        state = state,
                        onRefresh = managementActions.refreshMcp,
                        onTest = managementActions.testMcpServer,
                        onSetEnabled = managementActions.setMcpServerEnabled,
                        onRemove = managementActions.removeMcpServer,
                        onInstall = managementActions.installMcpCatalogEntry,
                        onBack = null,
                        modifier = Modifier.weight(1f),
                    )
                    WorkspaceDestination.USAGE -> UsageScreen(
                        state = state,
                        onRefresh = managementActions.refreshUsage,
                        onBack = null,
                        modifier = Modifier.weight(1f),
                    )
                    WorkspaceDestination.AGENTS -> AgentsScreen(
                        state = state,
                        onRefresh = managementActions.refreshAgents,
                        onSetPaused = managementActions.setDelegationPaused,
                        onInterrupt = managementActions.interruptSubagent,
                        onStopProcess = managementActions.stopBackgroundProcess,
                        onOpenSession = { onSession(it); destination = WorkspaceDestination.CHAT },
                        onBack = null,
                        modifier = Modifier.weight(1f),
                    )
                    else -> ChatSurface(
                        state, connection, onSend, onSteer, onDraftChange, onCompleteSlash, onExecuteSlash,
                        onAttach, onRemoveAttachment, onInterrupt,
                        onApprove, onClarify, onSensitiveInput, modelActions, sessionActions, queueActions, Modifier.weight(1f),
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
                        state, connection, onSend, onSteer, onDraftChange, onCompleteSlash, onExecuteSlash,
                        onAttach, onRemoveAttachment, onInterrupt,
                        onApprove, onClarify, onSensitiveInput, modelActions, sessionActions, queueActions,
                        Modifier.fillMaxSize(),
                        onBack = { destination = WorkspaceDestination.SESSIONS },
                    )
                    WorkspaceDestination.SKILLS -> SkillsScreen(
                        state, managementActions.refreshSkills, managementActions.toggleSkill,
                        managementActions.refreshToolsets, managementActions.setToolsetEnabled,
                        managementActions.loadSkillHub, managementActions.reviewSkill, managementActions.closeSkillReview,
                        managementActions.installReviewedSkill, managementActions.uninstallSkill, managementActions.updateSkills,
                        onBack = { destination = WorkspaceDestination.SESSIONS },
                        modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(),
                    )
                    WorkspaceDestination.CRON -> CronScreen(
                        state, managementActions.refreshCron, managementActions.setCronEnabled,
                        managementActions.triggerCron, managementActions.refreshCronRuns,
                        { onSession(it); destination = WorkspaceDestination.CHAT },
                        managementActions.createCron,
                        managementActions.updateCron, managementActions.deleteCron,
                        onBack = { destination = WorkspaceDestination.SESSIONS },
                        modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(),
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
                        modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(),
                    )
                    WorkspaceDestination.BACKENDS -> BackendsScreen(
                        state = state,
                        onConnect = onConnectBackend,
                        onSelect = onSelectBackend,
                        onForget = onForgetBackend,
                        onBack = { destination = WorkspaceDestination.SESSIONS },
                        modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(),
                    )
                    WorkspaceDestination.FILES -> WorkspaceFilesScreen(
                        backend = requireNotNull(state.backend),
                        initialPath = state.runtimeInfo.cwd.takeIf(String::isNotBlank),
                        onBack = { destination = WorkspaceDestination.SESSIONS },
                        modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(),
                    )
                    WorkspaceDestination.DIAGNOSTICS -> DiagnosticsScreen(
                        state = state,
                        connection = connection,
                        onRun = managementActions.runDiagnostic,
                        onBack = { destination = WorkspaceDestination.SESSIONS },
                        modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(),
                    )
                    WorkspaceDestination.PROVIDERS -> ProvidersScreen(
                        state = state,
                        onRefresh = managementActions.refreshProviders,
                        onSave = managementActions.saveProviderSetting,
                        onDelete = managementActions.deleteProviderSetting,
                        onBack = { destination = WorkspaceDestination.SESSIONS },
                        modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(),
                    )
                    WorkspaceDestination.MESSAGING -> MessagingScreen(
                        state = state,
                        onRefresh = managementActions.refreshMessaging,
                        onSetEnabled = managementActions.setMessagingEnabled,
                        onSave = managementActions.saveMessagingSettings,
                        onClear = managementActions.clearMessagingSetting,
                        onTest = managementActions.testMessagingPlatform,
                        onRestartGateway = managementActions.restartMessagingGateway,
                        onBack = { destination = WorkspaceDestination.SESSIONS },
                        modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(),
                    )
                    WorkspaceDestination.MCP -> McpScreen(
                        state = state,
                        onRefresh = managementActions.refreshMcp,
                        onTest = managementActions.testMcpServer,
                        onSetEnabled = managementActions.setMcpServerEnabled,
                        onRemove = managementActions.removeMcpServer,
                        onInstall = managementActions.installMcpCatalogEntry,
                        onBack = { destination = WorkspaceDestination.SESSIONS },
                        modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(),
                    )
                    WorkspaceDestination.USAGE -> UsageScreen(
                        state = state,
                        onRefresh = managementActions.refreshUsage,
                        onBack = { destination = WorkspaceDestination.SESSIONS },
                        modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(),
                    )
                    WorkspaceDestination.AGENTS -> AgentsScreen(
                        state = state,
                        onRefresh = managementActions.refreshAgents,
                        onSetPaused = managementActions.setDelegationPaused,
                        onInterrupt = managementActions.interruptSubagent,
                        onStopProcess = managementActions.stopBackgroundProcess,
                        onOpenSession = { onSession(it); destination = WorkspaceDestination.CHAT },
                        onBack = { destination = WorkspaceDestination.SESSIONS },
                        modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(),
                    )
                    WorkspaceDestination.SESSIONS -> SessionRail(
                        state, connection, onRefresh, onSearchSessions,
                        onSession = { onSession(it); destination = WorkspaceDestination.CHAT },
                        onDeleteSession = onDeleteSession,
                        onNewSession = { onNewSession(null); destination = WorkspaceDestination.CHAT },
                        onSkills = { destination = WorkspaceDestination.SKILLS },
                        onCron = { destination = WorkspaceDestination.CRON },
                        onProfiles = { destination = WorkspaceDestination.PROFILES },
                        onBackends = { destination = WorkspaceDestination.BACKENDS },
                        onFiles = { destination = WorkspaceDestination.FILES },
                        onDiagnostics = { destination = WorkspaceDestination.DIAGNOSTICS },
                        onProviders = { destination = WorkspaceDestination.PROVIDERS },
                        onMessaging = { destination = WorkspaceDestination.MESSAGING },
                        onMcp = { destination = WorkspaceDestination.MCP },
                        onUsage = { destination = WorkspaceDestination.USAGE },
                        onAgents = { destination = WorkspaceDestination.AGENTS },
                        modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(),
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
    onSearchSessions: (String) -> Unit,
    onSession: (StoredSession) -> Unit,
    onDeleteSession: (StoredSession) -> Unit,
    onNewSession: () -> Unit,
    onSkills: () -> Unit,
    onCron: () -> Unit,
    onProfiles: () -> Unit,
    onBackends: () -> Unit,
    onFiles: () -> Unit,
    onDiagnostics: () -> Unit,
    onProviders: () -> Unit,
    onMessaging: () -> Unit,
    onMcp: () -> Unit,
    onUsage: () -> Unit,
    onAgents: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var pendingDelete by remember { mutableStateOf<StoredSession?>(null) }
    var confirmNewSession by rememberSaveable { mutableStateOf(false) }
    val visibleSessions = state.sessions.filter { session ->
        query.isBlank() || listOf(
            session.displayTitle,
            session.profile,
            session.model,
            session.provider,
            session.source,
        ).filterNotNull().any { it.contains(query.trim(), ignoreCase = true) }
    }
    val remoteResults = if (query.isBlank()) emptyList() else state.sessionSearchResults.filterNot { result ->
        visibleSessions.any { it.durableId == result.sessionId && it.profile == result.profile }
    }
    LaunchedEffect(query) { onSearchSessions(query) }
    Column(modifier.background(Color.Transparent)) {
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
            IconButton(
                onClick = {
                    if (state.runtimeSessionId == null) onNewSession() else confirmNewSession = true
                },
            ) { Icon(Icons.Outlined.Add, "New session") }
        }
        ConnectionLine(connection)
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = onSkills,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 12.dp),
            ) {
                Icon(Icons.Outlined.AutoAwesome, null)
                Spacer(Modifier.width(6.dp))
                Text("Skills")
            }
            OutlinedButton(
                onClick = onCron,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 12.dp),
            ) {
                Icon(Icons.Outlined.Schedule, null)
                Spacer(Modifier.width(6.dp))
                Text("Cron")
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = onProfiles,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 12.dp),
            ) {
                Icon(Icons.Outlined.Person, null)
                Spacer(Modifier.width(6.dp))
                Text("Profiles")
            }
            OutlinedButton(
                onClick = onProviders,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 12.dp),
            ) {
                Icon(Icons.Outlined.Key, null)
                Spacer(Modifier.width(6.dp))
                Text("Providers")
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(onClick = onFiles, modifier = Modifier.weight(1f)) {
                Icon(Icons.Outlined.Folder, null)
                Spacer(Modifier.width(6.dp))
                Text("Files")
            }
            OutlinedButton(onClick = onDiagnostics, modifier = Modifier.weight(1f)) {
                Icon(Icons.Outlined.Info, null)
                Spacer(Modifier.width(6.dp))
                Text("Diagnostics")
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(onClick = onMessaging, modifier = Modifier.weight(1f)) {
                Icon(Icons.Outlined.Forum, null)
                Spacer(Modifier.width(6.dp))
                Text("Messaging")
            }
            OutlinedButton(onClick = onMcp, modifier = Modifier.weight(1f)) {
                Icon(Icons.Outlined.Terminal, null)
                Spacer(Modifier.width(6.dp))
                Text("MCP")
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(onClick = onAgents, modifier = Modifier.weight(1f)) {
                Icon(Icons.Outlined.Terminal, null)
                Spacer(Modifier.width(6.dp))
                Text("Agents")
            }
            OutlinedButton(onClick = onUsage, modifier = Modifier.weight(1f)) {
                Icon(Icons.Outlined.BarChart, null)
                Spacer(Modifier.width(6.dp))
                Text("Usage")
            }
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it.take(200) },
            placeholder = { Text("Search sessions") },
            leadingIcon = { Icon(Icons.Outlined.Search, null) },
            trailingIcon = {
                if (state.sessionSearchLoading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        )
        state.error?.let { ErrorBanner(it, Modifier.padding(12.dp)) }
        LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
            items(visibleSessions, key = { "${it.profile}:${it.durableId}" }) { session ->
                val selected = state.activeStoredSession?.durableId == session.durableId
                SessionRow(
                    session = session,
                    selected = selected,
                    onClick = { onSession(session) },
                    onDelete = if (!selected) ({ pendingDelete = session }) else null,
                )
            }
            items(remoteResults, key = { "search:${it.profile}:${it.sessionId}" }) { result ->
                SearchResultRow(result) {
                    onSession(
                        StoredSession(
                            sessionId = result.sessionId,
                            profile = result.profile,
                            source = result.source,
                            model = result.model,
                            startedAt = result.sessionStarted,
                        ),
                    )
                }
            }
            if (visibleSessions.isEmpty() && remoteResults.isEmpty() && !state.loading && !state.sessionSearchLoading) {
                item {
                    Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(if (state.sessions.isEmpty()) "NO SESSIONS" else "NO MATCHES", style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (state.sessions.isEmpty()) "Start a conversation or connect another Hermes surface."
                            else "Try a title, profile, model, provider or source.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }

    pendingDelete?.let { session ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("DELETE SESSION?") },
            text = { Text("${session.displayTitle} and its stored transcript will be permanently removed from Hermes. This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDelete = null
                        onDeleteSession(session)
                    },
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } },
        )
    }
    if (confirmNewSession) {
        AlertDialog(
            onDismissRequest = { confirmNewSession = false },
            title = { Text("START FRESH?") },
            text = { Text("Hermes will end the current live conversation and open a clean session. Its stored transcript remains available in the session list.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmNewSession = false
                        onNewSession()
                    },
                ) { Text("Start new session") }
            },
            dismissButton = { TextButton(onClick = { confirmNewSession = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SearchResultRow(result: SessionSearchHit, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                highlightedSearchSnippet(
                    result.snippet.ifBlank { "Session ${result.sessionId}" },
                    MaterialTheme.colorScheme.primary,
                ),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOf(result.profile, result.source, result.model).filterNotNull().filter(String::isNotBlank).joinToString(" / "),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun highlightedSearchSnippet(raw: String, highlight: Color): AnnotatedString = buildAnnotatedString {
    val text = raw.trim()
    var cursor = 0
    while (cursor < text.length) {
        val start = text.indexOf(">>>", cursor)
        if (start < 0) {
            append(text.substring(cursor))
            break
        }
        append(text.substring(cursor, start))
        val end = text.indexOf("<<<", start + 3)
        if (end < 0) {
            append(text.substring(start))
            break
        }
        withStyle(SpanStyle(color = highlight, fontWeight = FontWeight.Bold)) {
            append(text.substring(start + 3, end))
        }
        cursor = end + 3
    }
}

@Composable
private fun BrandGlyphSmall() {
    Image(
        painter = painterResource(R.drawable.hermes_badge),
        contentDescription = "Hermes Agent",
        modifier = Modifier.size(34.dp).clip(RoundedCornerShape(8.dp)),
        contentScale = ContentScale.Crop,
    )
}

@Composable
private fun ConnectionLine(connection: GatewayConnectionState) {
    val (colour, text) = when (connection) {
        GatewayConnectionState.Open -> MaterialTheme.colorScheme.tertiary to "LIVE / JSON-RPC"
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
private fun SessionRow(
    session: StoredSession,
    selected: Boolean,
    onClick: () -> Unit,
    onDelete: (() -> Unit)?,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(12.dp))
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
        if (session.isActive) Box(Modifier.size(7.dp).clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.tertiary))
        onDelete?.let {
            IconButton(onClick = it) { Icon(Icons.Outlined.Delete, "Delete ${session.displayTitle}") }
        }
    }
}

@Composable
private fun ChatSurface(
    state: HermesState,
    connection: GatewayConnectionState,
    onSend: (String) -> Unit,
    onSteer: (String) -> Unit,
    onDraftChange: (String) -> Unit,
    onCompleteSlash: (String) -> Unit,
    onExecuteSlash: (String) -> Unit,
    onAttach: (android.net.Uri) -> Unit,
    onRemoveAttachment: (String) -> Unit,
    onInterrupt: () -> Unit,
    onApprove: (String) -> Unit,
    onClarify: (String) -> Unit,
    onSensitiveInput: (String) -> Unit,
    modelActions: ModelActions,
    sessionActions: SessionActionCallbacks,
    queueActions: QueueActions,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
) {
    val voiceViewModel: VoiceViewModel = hiltViewModel()
    val speechState by voiceViewModel.speechState.collectAsStateWithLifecycle()
    LaunchedEffect(state.backend?.id) { state.backend?.let(voiceViewModel::bind) }
    DisposableEffect(voiceViewModel) {
        onDispose {
            voiceViewModel.cancelRecording()
            voiceViewModel.stopSpeaking()
        }
    }
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
                Timeline(
                    items = state.timeline.items,
                    speechState = speechState,
                    onSpeak = voiceViewModel::speak,
                    onStopSpeaking = voiceViewModel::stopSpeaking,
                )
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
                backend = requireNotNull(state.backend),
                historySessionId = requireNotNull(state.runtimeSessionId),
                userHistory = remember(state.timeline.items) { ComposerHistory.derive(state.timeline) },
                draft = state.draft,
                slashSuggestions = state.slashSuggestions,
                slashLoading = state.slashLoading,
                sending = state.sending || state.runtimeInfo.running || state.timeline.items.any {
                    it is TimelineItem.Message && it.streaming
                },
                attaching = state.attaching,
                connected = connection is GatewayConnectionState.Open,
                attachmentEnabled = state.supportsRemoteAttachments,
                attachments = state.pendingAttachments,
                queuedPrompts = state.queuedPrompts,
                queueDraining = state.queueDraining,
                queueNotice = state.queueNotice,
                onSend = onSend,
                onSteer = onSteer,
                onQueue = queueActions.enqueueDraft,
                onUpdateQueued = queueActions.update,
                onRemoveQueued = queueActions.remove,
                onSendQueuedNow = queueActions.sendNow,
                onDraftChange = onDraftChange,
                onCompleteSlash = onCompleteSlash,
                onExecuteSlash = onExecuteSlash,
                onAttach = onAttach,
                onRemoveAttachment = onRemoveAttachment,
                onInterrupt = onInterrupt,
                voiceViewModel = voiceViewModel,
            )
        }
    }

    state.timeline.approval?.let { request ->
        ApprovalDialog(request.command, request.description, request.choices, onApprove)
    }
    state.timeline.clarification?.let { request ->
        ClarificationDialog(request.question, request.choices, onClarify)
    }
    state.timeline.sensitiveInput?.let { request ->
        SensitiveInputDialog(
            requestId = request.requestId,
            kind = request.kind,
            prompt = request.prompt,
            environmentVariable = request.environmentVariable,
            onSubmit = onSensitiveInput,
        )
    }
}

@Composable
private fun ChatHeader(
    state: HermesState,
    onBack: (() -> Unit)?,
    sessionActions: SessionActionCallbacks,
) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        onBack?.let { IconButton(onClick = it) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back to sessions") } }
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
            onRetry = sessionActions.retry,
            onUndo = sessionActions.undo,
            onCompress = sessionActions.compress,
            onReset = sessionActions.reset,
            onArchive = sessionActions.archive,
            onRefreshCheckpoints = sessionActions.refreshCheckpoints,
            onPreviewCheckpoint = sessionActions.previewCheckpoint,
            onRestoreCheckpoint = sessionActions.restoreCheckpoint,
        )
    }
    HorizontalDivider()
}

@Composable
private fun Timeline(
    items: List<TimelineItem>,
    speechState: SpeechUiState,
    onSpeak: (String, String) -> Unit,
    onStopSpeaking: () -> Unit,
) {
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
                is TimelineItem.Message -> MessageBlock(
                    message = item,
                    speechState = speechState,
                    onSpeak = { onSpeak(item.id, item.text) },
                    onStopSpeaking = onStopSpeaking,
                )
                is TimelineItem.Tool -> ToolBlock(item)
                is TimelineItem.Reasoning -> ReasoningBlock(item)
                is TimelineItem.Status -> StatusBlock(item)
            }
        }
    }
}

@Composable
private fun MessageBlock(
    message: TimelineItem.Message,
    speechState: SpeechUiState,
    onSpeak: () -> Unit,
    onStopSpeaking: () -> Unit,
) {
    val user = message.role == MessageRole.USER
    val label = when (message.role) {
        MessageRole.USER -> "YOU"
        MessageRole.ASSISTANT -> "HERMES"
        MessageRole.SYSTEM -> "SYSTEM"
        MessageRole.TOOL -> "TOOL"
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (user) Arrangement.End else Arrangement.Start) {
        Surface(
            modifier = Modifier.fillMaxWidth(if (user) 0.88f else 1f),
            color = if (user) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
            shape = RoundedCornerShape(12.dp),
            border = if (message.failed) BorderStroke(1.dp, MaterialTheme.colorScheme.error) else null,
        ) {
            Column(Modifier.padding(if (user) 14.dp else 6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                    if (!user && message.role == MessageRole.ASSISTANT && !message.streaming && message.text.isNotBlank()) {
                        val active = speechState.messageId == message.id && speechState.phase != SpeechPhase.IDLE
                        if (active && speechState.phase == SpeechPhase.LOADING) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(4.dp))
                        }
                        IconButton(onClick = if (active) onStopSpeaking else onSpeak, modifier = Modifier.size(36.dp)) {
                            Icon(
                                if (active) Icons.Outlined.StopCircle else Icons.AutoMirrored.Outlined.VolumeUp,
                                if (active) "Stop reading this reply" else "Read this reply aloud",
                                modifier = Modifier.size(19.dp),
                            )
                        }
                    }
                }
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
        ToolState.COMPLETE -> MaterialTheme.colorScheme.tertiary
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
    backend: com.nousresearch.hermes.data.BackendConfig,
    historySessionId: String,
    userHistory: List<String>,
    draft: String,
    slashSuggestions: List<SlashSuggestion>,
    slashLoading: Boolean,
    sending: Boolean,
    attaching: Boolean,
    connected: Boolean,
    attachmentEnabled: Boolean,
    attachments: List<PendingAttachment>,
    queuedPrompts: List<QueuedPrompt>,
    queueDraining: Boolean,
    queueNotice: String?,
    onSend: (String) -> Unit,
    onSteer: (String) -> Unit,
    onQueue: () -> Unit,
    onUpdateQueued: (String, String) -> Unit,
    onRemoveQueued: (String) -> Unit,
    onSendQueuedNow: (String) -> Unit,
    onDraftChange: (String) -> Unit,
    onCompleteSlash: (String) -> Unit,
    onExecuteSlash: (String) -> Unit,
    onAttach: (android.net.Uri) -> Unit,
    onRemoveAttachment: (String) -> Unit,
    onInterrupt: () -> Unit,
    voiceViewModel: VoiceViewModel,
) {
    var pendingDestructiveSlash by rememberSaveable { mutableStateOf<String?>(null) }
    var microphoneDenied by rememberSaveable { mutableStateOf(false) }
    var historyMenuOpen by rememberSaveable(historySessionId) { mutableStateOf(false) }
    var historyCursor by rememberSaveable(historySessionId) { mutableIntStateOf(-1) }
    var historyDraftSnapshot by rememberSaveable(historySessionId) { mutableStateOf("") }
    var queuedEditId by rememberSaveable(historySessionId) { mutableStateOf<String?>(null) }
    var queuedEditText by rememberSaveable(historySessionId) { mutableStateOf("") }
    val focus = LocalFocusManager.current
    val softwareKeyboard = LocalSoftwareKeyboardController.current
    val context = LocalContext.current
    val voiceState by voiceViewModel.state.collectAsStateWithLifecycle()
    val speechState by voiceViewModel.speechState.collectAsStateWithLifecycle()
    val latestDraft by rememberUpdatedState(draft)
    val latestDraftChange by rememberUpdatedState(onDraftChange)
    val microphonePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        microphoneDenied = !granted
        if (granted) voiceViewModel.startRecording(VoiceRecordingMode.LOCKED) else voiceViewModel.permissionDenied()
    }
    LaunchedEffect(voiceState.transcript?.id) {
        voiceState.transcript?.let { transcript ->
            val combined = listOf(latestDraft.trimEnd(), transcript.text).filter(String::isNotBlank).joinToString(" ")
            latestDraftChange(combined)
            voiceViewModel.consumeTranscript(transcript.id)
        }
    }
    LaunchedEffect(voiceState.phase) {
        if (voiceState.phase != VoicePhase.IDLE) {
            focus.clearFocus(force = true)
            softwareKeyboard?.hide()
        }
    }
    fun toggleVoice() {
        when (voiceState.phase) {
            VoicePhase.IDLE -> {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                    microphoneDenied = false
                    voiceViewModel.startRecording(VoiceRecordingMode.LOCKED)
                } else {
                    microphoneDenied = false
                    microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
                }
            }
            VoicePhase.RECORDING -> voiceViewModel.stopAndTranscribe()
            VoicePhase.TRANSCRIBING -> voiceViewModel.cancelRecording()
        }
    }
    fun resetHistoryBrowse() {
        historyCursor = -1
        historyDraftSnapshot = ""
    }
    fun browseHistory(backward: Boolean): Boolean {
        val current = ComposerBrowseState(historyCursor, historyDraftSnapshot)
        val result = if (backward) {
            ComposerHistory.backward(current, draft, userHistory)
        } else {
            ComposerHistory.forward(current, userHistory)
        } ?: return false
        historyCursor = result.state.cursor
        historyDraftSnapshot = result.state.draftSnapshot
        onDraftChange(result.text)
        return true
    }
    fun submit() {
        if (draft.isBlank()) return
        if (draft.trimStart().startsWith('/')) {
            val slash = draft.trim()
            val normalized = slash.lowercase()
            if (
                normalized.substringBefore(' ') in setOf("/new", "/reset") ||
                normalized.startsWith("/rollback restore")
            ) {
                pendingDestructiveSlash = slash
            } else {
                onExecuteSlash(slash)
            }
        } else if (sending) onSteer(draft) else onSend(draft)
        resetHistoryBrowse()
        focus.clearFocus()
    }
    LaunchedEffect(draft) { onCompleteSlash(draft) }
    val documentPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(onAttach)
    }
    Column(Modifier.fillMaxWidth().imePadding().navigationBarsPadding().padding(12.dp)) {
        if (queuedPrompts.isNotEmpty() || queueNotice != null) {
            PendingMessageQueue(
                entries = queuedPrompts,
                busy = sending,
                draining = queueDraining,
                notice = queueNotice,
                onEdit = { entry ->
                    queuedEditId = entry.id
                    queuedEditText = entry.text
                },
                onRemove = onRemoveQueued,
                onSendNow = onSendQueuedNow,
            )
            Spacer(Modifier.height(8.dp))
        }
        if (slashSuggestions.isNotEmpty() || slashLoading) {
            SlashSuggestions(
                suggestions = slashSuggestions,
                loading = slashLoading,
                onSuggestion = onDraftChange,
            )
            Spacer(Modifier.height(8.dp))
        }
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
        VoiceStatus(
            state = voiceState,
            onStop = voiceViewModel::stopAndTranscribe,
            onCancel = { voiceViewModel.cancelRecording() },
            onDismissError = {
                microphoneDenied = false
                voiceViewModel.clearError()
            },
            showPermissionSettings = microphoneDenied,
            onOpenPermissionSettings = {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            },
        )
        SpeechStatus(
            state = speechState,
            onPause = voiceViewModel::pauseSpeaking,
            onResume = voiceViewModel::resumeSpeaking,
            onStop = voiceViewModel::stopSpeaking,
            onOutput = voiceViewModel::showOutputSwitcher,
            onDismissError = voiceViewModel::clearSpeechError,
        )
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
            Box {
                IconButton(
                    onClick = { historyMenuOpen = true },
                    enabled = connected && userHistory.isNotEmpty(),
                    modifier = Modifier.semantics { contentDescription = "Open message history" },
                ) { Icon(Icons.Outlined.History, null) }
                DropdownMenu(
                    expanded = historyMenuOpen,
                    onDismissRequest = { historyMenuOpen = false },
                ) {
                    userHistory.take(MAX_VISIBLE_COMPOSER_HISTORY).forEach { message ->
                        DropdownMenuItem(
                            text = { Text(message, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                            onClick = {
                                resetHistoryBrowse()
                                onDraftChange(message)
                                historyMenuOpen = false
                            },
                        )
                    }
                }
            }
            OutlinedTextField(
                value = draft,
                onValueChange = {
                    resetHistoryBrowse()
                    onDraftChange(it)
                },
                placeholder = {
                    Text(
                        when {
                            !connected -> "Reconnect to send"
                            sending -> "Steer the current run"
                            else -> "Message Hermes"
                        },
                    )
                },
                modifier = Modifier
                    .weight(1f)
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown || !event.isCtrlPressed) {
                            false
                        } else {
                            when (event.key) {
                                Key.DirectionUp -> browseHistory(backward = true)
                                Key.DirectionDown -> browseHistory(backward = false)
                                else -> false
                            }
                        }
                    },
                enabled = connected,
                maxLines = 6,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { submit() }),
                trailingIcon = {
                    VoiceRecordButton(
                        state = voiceState,
                        connected = connected,
                        hasPermission = {
                            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                        },
                        onRequestPermission = { microphonePermission.launch(Manifest.permission.RECORD_AUDIO) },
                        onTap = ::toggleVoice,
                        onPressStart = { voiceViewModel.startRecording(VoiceRecordingMode.PRESS_TO_TALK) },
                        onLock = voiceViewModel::lockRecording,
                        onRelease = voiceViewModel::stopAndTranscribe,
                        onCancel = { voiceViewModel.cancelRecording() },
                    )
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            )
            if (sending) {
                IconButton(onClick = onInterrupt, modifier = Modifier.semantics { contentDescription = "Stop the current Hermes run" }) {
                    Icon(Icons.Outlined.StopCircle, null, tint = MaterialTheme.colorScheme.error)
                }
                IconButton(
                    onClick = {
                        onQueue()
                        resetHistoryBrowse()
                        focus.clearFocus()
                    },
                    enabled = connected && draft.isNotBlank() && attachments.isEmpty() &&
                        queuedPrompts.size < ComposerQueue.MAX_ENTRIES && !queueDraining,
                ) {
                    Icon(Icons.Outlined.Schedule, "Queue for the next turn")
                }
                IconButton(onClick = ::submit, enabled = connected && draft.isNotBlank()) {
                    Icon(Icons.AutoMirrored.Outlined.Send, "Steer the current run")
                }
            } else {
                IconButton(onClick = ::submit, enabled = connected && draft.isNotBlank()) { Icon(Icons.AutoMirrored.Outlined.Send, "Send message") }
            }
        }
        if (sending && attachments.isNotEmpty()) {
            Text(
                "Pending-message queue is text-only; send or remove attachments first.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
    queuedEditId?.let { entryId ->
        AlertDialog(
            onDismissRequest = { queuedEditId = null },
            title = { Text("EDIT PENDING MESSAGE") },
            text = {
                OutlinedTextField(
                    value = queuedEditText,
                    onValueChange = { queuedEditText = it.take(ComposerQueue.MAX_TEXT_CHARACTERS) },
                    label = { Text("Message") },
                    minLines = 3,
                    maxLines = 8,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onUpdateQueued(entryId, queuedEditText)
                        queuedEditId = null
                    },
                    enabled = queuedEditText.isNotBlank() && !queueDraining,
                ) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { queuedEditId = null }) { Text("Cancel") } },
        )
    }
    pendingDestructiveSlash?.let { command ->
        val restoresSnapshot = command.lowercase().startsWith("/rollback restore")
        AlertDialog(
            onDismissRequest = { pendingDestructiveSlash = null },
            title = { Text(if (restoresSnapshot) "RESTORE SNAPSHOT?" else "START FRESH?") },
            text = {
                Text(
                    if (restoresSnapshot) {
                        "Hermes will replace the current workspace with the selected snapshot. Unsaved workspace changes may be lost."
                    } else {
                        "Hermes will end the current live conversation and open a clean session. Its stored transcript remains available in the session list."
                    },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDestructiveSlash = null
                        onExecuteSlash(command)
                    },
                ) { Text(if (restoresSnapshot) "Restore snapshot" else "Start new session") }
            },
            dismissButton = { TextButton(onClick = { pendingDestructiveSlash = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun PendingMessageQueue(
    entries: List<QueuedPrompt>,
    busy: Boolean,
    draining: Boolean,
    notice: String?,
    onEdit: (QueuedPrompt) -> Unit,
    onRemove: (String) -> Unit,
    onSendNow: (String) -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(Modifier.fillMaxWidth().padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("PENDING MESSAGES", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                if (draining) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(6.dp))
                }
                Text(entries.size.toString(), style = MaterialTheme.typography.labelMedium)
            }
            notice?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            if (entries.isNotEmpty()) {
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 180.dp)) {
                    items(entries, key = { it.id }) { entry ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                        ) {
                            Text(
                                entry.text,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            if (entry.autoDrainFailures >= ComposerQueue.MAX_AUTO_DRAIN_ATTEMPTS && !busy) {
                                IconButton(
                                    onClick = { onSendNow(entry.id) },
                                    enabled = !draining,
                                    modifier = Modifier.size(36.dp),
                                ) { Icon(Icons.Outlined.Refresh, "Retry pending message", modifier = Modifier.size(17.dp)) }
                            }
                            IconButton(
                                onClick = { onEdit(entry) },
                                enabled = !draining,
                                modifier = Modifier.size(36.dp),
                            ) { Icon(Icons.Outlined.Edit, "Edit pending message", modifier = Modifier.size(17.dp)) }
                            IconButton(
                                onClick = { onRemove(entry.id) },
                                enabled = !draining,
                                modifier = Modifier.size(36.dp),
                            ) { Icon(Icons.Outlined.Delete, "Remove pending message", modifier = Modifier.size(17.dp)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VoiceRecordButton(
    state: VoiceUiState,
    connected: Boolean,
    hasPermission: () -> Boolean,
    onRequestPermission: () -> Unit,
    onTap: () -> Unit,
    onPressStart: () -> Unit,
    onLock: () -> Unit,
    onRelease: () -> Unit,
    onCancel: () -> Unit,
) {
    val gestureThreshold = with(LocalDensity.current) { 72.dp.toPx() }
    val latestState by rememberUpdatedState(state)
    val latestTap by rememberUpdatedState(onTap)
    val latestPermission by rememberUpdatedState(hasPermission)
    val latestRequestPermission by rememberUpdatedState(onRequestPermission)
    val latestPressStart by rememberUpdatedState(onPressStart)
    val latestLock by rememberUpdatedState(onLock)
    val latestRelease by rememberUpdatedState(onRelease)
    val latestCancel by rememberUpdatedState(onCancel)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(50))
            .semantics {
                role = Role.Button
                contentDescription = when (state.phase) {
                    VoicePhase.IDLE -> "Hold to talk or tap for locked recording"
                    VoicePhase.RECORDING -> "Stop and transcribe voice recording"
                    VoicePhase.TRANSCRIBING -> "Cancel voice transcription"
                }
                onClick {
                    if (connected) latestTap()
                    connected
                }
            }
            .focusable(enabled = connected)
            .pointerInput(connected, gestureThreshold) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                down.consume()
                if (!connected) return@awaitEachGesture
                if (latestState.phase != VoicePhase.IDLE) {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) {
                            change.consume()
                            latestTap()
                            break
                        }
                        change.consume()
                    }
                    return@awaitEachGesture
                }
                if (!latestPermission()) {
                    latestRequestPermission()
                    return@awaitEachGesture
                }

                latestPressStart()
                var locked = false
                var cancelled = false
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    val horizontal = change.position.x - down.position.x
                    val vertical = change.position.y - down.position.y
                    if (!locked && !cancelled && horizontal <= -gestureThreshold) {
                        cancelled = true
                        latestCancel()
                    } else if (!locked && !cancelled && vertical <= -gestureThreshold) {
                        locked = true
                        latestLock()
                    }
                    if (!change.pressed) {
                        if (!locked && !cancelled) {
                            if (change.uptimeMillis - down.uptimeMillis < PRESS_TO_TALK_THRESHOLD_MILLIS) {
                                latestLock()
                            } else {
                                latestRelease()
                            }
                        }
                        change.consume()
                        break
                    }
                    change.consume()
                }
            }
        },
    ) {
        Icon(
            when {
                state.phase == VoicePhase.IDLE -> Icons.Outlined.Mic
                state.phase == VoicePhase.RECORDING && state.recordingMode == VoiceRecordingMode.LOCKED -> Icons.Outlined.Lock
                else -> Icons.Outlined.StopCircle
            },
            contentDescription = null,
            tint = if (state.phase == VoicePhase.RECORDING) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun VoiceStatus(
    state: VoiceUiState,
    onStop: () -> Unit,
    onCancel: () -> Unit,
    onDismissError: () -> Unit,
    showPermissionSettings: Boolean,
    onOpenPermissionSettings: () -> Unit,
) {
    when (state.phase) {
        VoicePhase.RECORDING -> Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        ) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${if (state.recordingMode == VoiceRecordingMode.PRESS_TO_TALK) "HOLDING" else "RECORDING"} / ${state.elapsedMillis.formatVoiceTime()}",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onCancel) { Text("Cancel") }
                    Button(onClick = onStop) { Text("Transcribe") }
                }
                LinearProgressIndicator(progress = { state.level }, modifier = Modifier.fillMaxWidth())
                Text(
                    if (state.recordingMode == VoiceRecordingMode.PRESS_TO_TALK) {
                        "Release to transcribe, slide up to lock, or slide left to cancel."
                    } else {
                        "Locked recording. Tap Transcribe when finished; it stops automatically after two minutes."
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        VoicePhase.TRANSCRIBING -> Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        ) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Text("HERMES IS TRANSCRIBING", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = onCancel) { Text("Cancel") }
            }
        }
        VoicePhase.IDLE -> state.error?.let { message ->
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            ) {
                Row(Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(message, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    if (showPermissionSettings) TextButton(onClick = onOpenPermissionSettings) { Text("Settings") }
                    IconButton(onClick = onDismissError) { Icon(Icons.Outlined.Close, "Dismiss voice error") }
                }
            }
        }
    }
}

@Composable
private fun SpeechStatus(
    state: SpeechUiState,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    onOutput: () -> Unit,
    onDismissError: () -> Unit,
) {
    if (state.phase != SpeechPhase.IDLE) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        ) {
            Row(
                Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (state.phase == SpeechPhase.LOADING) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                else Icon(Icons.AutoMirrored.Outlined.VolumeUp, null, modifier = Modifier.size(20.dp))
                Text(
                    if (state.phase == SpeechPhase.LOADING) "HERMES IS PREPARING AUDIO" else "SPEAKING / ${state.outputName}",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (state.phase == SpeechPhase.PLAYING) {
                    IconButton(onClick = onPause) { Icon(Icons.Outlined.Pause, "Pause spoken reply") }
                } else if (state.phase == SpeechPhase.PAUSED) {
                    IconButton(onClick = onResume) { Icon(Icons.Outlined.PlayArrow, "Resume spoken reply") }
                }
                if (state.phase != SpeechPhase.LOADING) {
                    TextButton(onClick = onOutput) { Text("Output") }
                }
                IconButton(onClick = onStop) { Icon(Icons.Outlined.StopCircle, "Stop spoken reply") }
            }
        }
    } else if (state.error != null) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.errorContainer,
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        ) {
            Row(Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(state.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                IconButton(onClick = onDismissError) { Icon(Icons.Outlined.Close, "Dismiss spoken reply error") }
            }
        }
    }
}

private fun Long.formatVoiceTime(): String {
    val totalSeconds = this / 1_000L
    return "%d:%02d".format(totalSeconds / 60L, totalSeconds % 60L)
}

private const val PRESS_TO_TALK_THRESHOLD_MILLIS = 300L

@Composable
private fun SlashSuggestions(
    suggestions: List<SlashSuggestion>,
    loading: Boolean,
    onSuggestion: (String) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth().heightIn(max = 168.dp),
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(vertical = 8.dp),
        ) {
            if (loading && suggestions.isEmpty()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text("Loading Hermes commands", style = MaterialTheme.typography.bodySmall)
                }
            }
            suggestions.forEachIndexed { index, suggestion ->
                if (index == 0 || suggestions[index - 1].group != suggestion.group) {
                    Text(
                        suggestion.group.uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
                    )
                }
                Surface(
                    onClick = { onSuggestion(suggestion.text) },
                    shape = RoundedCornerShape(10.dp),
                    color = Color.Transparent,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Column(Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
                        Text(suggestion.display, style = MaterialTheme.typography.titleMedium)
                        if (suggestion.meta.isNotBlank()) {
                            Text(
                                suggestion.meta,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
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
                OutlinedTextField(answer, { answer = it }, Modifier.fillMaxWidth(), label = { Text("Answer") })
            }
        },
        confirmButton = { Button(enabled = answer.isNotBlank(), onClick = { onAnswer(answer.trim()) }) { Text("CONTINUE") } },
    )
}

@Composable
internal fun SensitiveInputDialog(
    requestId: String,
    kind: SensitiveInputKind,
    prompt: String,
    environmentVariable: String?,
    onSubmit: (String) -> Unit,
) {
    var value by remember(requestId) { mutableStateOf("") }
    val submit: (String) -> Unit = { response ->
        value = ""
        onSubmit(response)
    }
    val sudo = kind == SensitiveInputKind.SUDO_PASSWORD
    AlertDialog(
        onDismissRequest = { },
        icon = { Icon(Icons.Outlined.Key, null, tint = WarningColor) },
        title = { Text(if (sudo) "SUDO PASSWORD REQUIRED" else "SECRET REQUIRED") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(prompt)
                environmentVariable?.let {
                    Text("ENVIRONMENT VARIABLE / $it", style = MaterialTheme.typography.labelMedium)
                }
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it.take(8_192) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(if (sudo) "Password" else "Secret value") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { if (value.isNotEmpty()) submit(value) }),
                )
                Text(
                    "This value is sent once to the active Hermes request. Android does not save it to drafts, restored state, logs, or local preferences.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            Button(enabled = value.isNotEmpty(), onClick = { submit(value) }) { Text("CONTINUE") }
        },
        dismissButton = {
            OutlinedButton(onClick = { submit("") }) { Text("CANCEL REQUEST") }
        },
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
