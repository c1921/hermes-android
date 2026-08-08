package com.nousresearch.hermes.ui.navigation

import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

enum class ProductHome { CHATS, ARTIFACTS, AUTOMATIONS, MANAGE, APP_SETTINGS }

enum class HostCapability {
    TERMINAL_BACKEND,
    COMPUTER_USE_PERMISSION,
    LOCAL_FILESYSTEM_REVEAL,
    TERMINAL_PANE,
    PLUGIN_ROUTE,
}

enum class HostCapabilityDisposition { REMOTE_STATUS, INTENTIONALLY_UNAVAILABLE }

data class DurableDestination(
    val legacyDestination: ManagementDestination,
    val productHome: ProductHome,
    val manageSection: ManageSection,
    val automationDestination: AutomationDestination? = null,
    val manageDestination: ManageDestination? = null,
)

object HermesDestinationCatalog {
    val durableDestinations: Map<ManagementDestination, DurableDestination> =
        ManagementDestination.entries.associateWith { destination ->
            DurableDestination(
                legacyDestination = destination,
                productHome = destination.productHome(),
                manageSection = destination.manageSection(),
                automationDestination = destination.automationDestination(),
                manageDestination = destination.manageDestination(),
            )
        }

    val hostCapabilities: Map<HostCapability, HostCapabilityDisposition> = mapOf(
        HostCapability.TERMINAL_BACKEND to HostCapabilityDisposition.REMOTE_STATUS,
        HostCapability.COMPUTER_USE_PERMISSION to HostCapabilityDisposition.REMOTE_STATUS,
        HostCapability.LOCAL_FILESYSTEM_REVEAL to HostCapabilityDisposition.INTENTIONALLY_UNAVAILABLE,
        HostCapability.TERMINAL_PANE to HostCapabilityDisposition.INTENTIONALLY_UNAVAILABLE,
        HostCapability.PLUGIN_ROUTE to HostCapabilityDisposition.INTENTIONALLY_UNAVAILABLE,
    )

    fun productHome(route: HermesRoute): ProductHome? = when (route) {
        is HermesRoute.SessionAtlas, is HermesRoute.Conversation -> ProductHome.CHATS
        is HermesRoute.Files -> ProductHome.ARTIFACTS
        is HermesRoute.Management -> durableDestinations.getValue(route.destination).productHome
        HermesRoute.Onboarding, is HermesRoute.BackendPicker -> null
        is HermesDestinationRoute.Chats -> ProductHome.CHATS
        is HermesDestinationRoute.Artifacts -> ProductHome.ARTIFACTS
        is HermesDestinationRoute.Automations -> ProductHome.AUTOMATIONS
        is HermesDestinationRoute.Manage -> ProductHome.MANAGE
        is HermesDestinationRoute.AppSettings -> ProductHome.APP_SETTINGS
    }

    fun destination(
        legacyDestination: ManagementDestination,
        backendId: String,
        profileId: String,
        resourceId: String? = null,
    ): HermesDestinationRoute {
        val entry = durableDestinations.getValue(legacyDestination)
        return if (entry.productHome == ProductHome.AUTOMATIONS) {
            HermesDestinationRoute.Automations(
                backendId = backendId,
                profileId = profileId,
                destination = checkNotNull(entry.automationDestination),
                resourceId = resourceId,
            )
        } else {
            HermesDestinationRoute.Manage(
                backendId = backendId,
                profileId = profileId,
                section = entry.manageSection,
                destination = checkNotNull(entry.manageDestination),
                resourceId = resourceId,
            )
        }
    }
}

private fun ManagementDestination.productHome(): ProductHome = when (this) {
    ManagementDestination.CRON,
    ManagementDestination.WEBHOOKS,
    ManagementDestination.AGENTS,
    ManagementDestination.COMMAND_CENTER,
    -> ProductHome.AUTOMATIONS

    else -> ProductHome.MANAGE
}

private fun ManagementDestination.manageSection(): ManageSection = when (this) {
    ManagementDestination.SKILLS,
    ManagementDestination.MCP,
    ManagementDestination.HOST_CAPABILITIES,
    -> ManageSection.CAPABILITIES

    ManagementDestination.PROFILES -> ManageSection.PROFILES_AND_MODELS

    ManagementDestination.CRON,
    ManagementDestination.WEBHOOKS,
    ManagementDestination.AGENTS,
    ManagementDestination.COMMAND_CENTER,
    ManagementDestination.BACKENDS,
    ManagementDestination.PROVIDERS,
    ManagementDestination.MESSAGING,
    -> ManageSection.CONNECTIONS_AND_DELIVERY

    ManagementDestination.STARMAP -> ManageSection.MEMORY_AND_LEARNING

    ManagementDestination.DIAGNOSTICS,
    ManagementDestination.USAGE,
    ManagementDestination.BILLING,
    ManagementDestination.CONFIG,
    -> ManageSection.SERVER_AND_ACCOUNT
}

private fun ManagementDestination.automationDestination(): AutomationDestination? = when (this) {
    ManagementDestination.CRON -> AutomationDestination.CRON
    ManagementDestination.WEBHOOKS -> AutomationDestination.WEBHOOKS
    ManagementDestination.AGENTS -> AutomationDestination.AGENTS
    ManagementDestination.COMMAND_CENTER -> AutomationDestination.COMMAND_CENTER

    else -> null
}

private fun ManagementDestination.manageDestination(): ManageDestination? = when (this) {
    ManagementDestination.SKILLS -> ManageDestination.SKILLS
    ManagementDestination.PROFILES -> ManageDestination.PROFILES
    ManagementDestination.BACKENDS -> ManageDestination.BACKENDS
    ManagementDestination.DIAGNOSTICS -> ManageDestination.DIAGNOSTICS
    ManagementDestination.PROVIDERS -> ManageDestination.PROVIDERS
    ManagementDestination.MESSAGING -> ManageDestination.MESSAGING
    ManagementDestination.MCP -> ManageDestination.MCP
    ManagementDestination.USAGE -> ManageDestination.USAGE
    ManagementDestination.BILLING -> ManageDestination.BILLING
    ManagementDestination.STARMAP -> ManageDestination.STARMAP
    ManagementDestination.HOST_CAPABILITIES -> ManageDestination.HOST_CAPABILITIES
    ManagementDestination.CONFIG -> ManageDestination.CONFIG
    ManagementDestination.CRON,
    ManagementDestination.WEBHOOKS,
    ManagementDestination.AGENTS,
    ManagementDestination.COMMAND_CENTER,
    -> null
}

object HermesDestinationUri {
    private const val SCHEME = "hermes"
    private const val MAX_URI_LENGTH = 8_192
    private val charset = StandardCharsets.UTF_8

    fun encode(route: HermesDestinationRoute): String {
        val (host, values) = when (route) {
            is HermesDestinationRoute.Chats -> "chats" to listOf(
                "backend" to route.backendId,
                "profile" to route.profileId,
                "session" to route.sessionId,
                "message" to route.messageId,
            )
            is HermesDestinationRoute.Artifacts -> "artifacts" to listOf(
                "backend" to route.backendId,
                "profile" to route.profileId,
                "artifact" to route.artifactId,
                "path" to route.filePath,
            )
            is HermesDestinationRoute.Automations -> "automations" to listOf(
                "backend" to route.backendId,
                "profile" to route.profileId,
                "destination" to route.destination?.name,
                "resource" to route.resourceId,
            )
            is HermesDestinationRoute.Manage -> "manage" to listOf(
                "backend" to route.backendId,
                "profile" to route.profileId,
                "section" to route.section?.name,
                "destination" to route.destination?.name,
                "resource" to route.resourceId,
            )
            is HermesDestinationRoute.AppSettings -> "app-settings" to listOf(
                "section" to route.section?.name,
            )
        }
        val query = values.mapNotNull { (key, value) ->
            value?.let { "$key=${URLEncoder.encode(it, charset.name())}" }
        }.joinToString("&")
        return "$SCHEME://$host${query.takeIf(String::isNotEmpty)?.let { "?$it" }.orEmpty()}"
    }

    fun parse(value: String): HermesDestinationRoute? {
        if (value.length > MAX_URI_LENGTH) return null
        val uri = runCatching { URI(value) }.getOrNull() ?: return null
        if (
            uri.scheme != SCHEME ||
            uri.fragment != null ||
            uri.userInfo != null ||
            uri.port != -1 ||
            uri.rawPath.isNotEmpty()
        ) return null
        val query = parseQuery(uri.rawQuery) ?: return null
        return runCatching {
            when (uri.host) {
                "chats" -> {
                    if (!query.hasOnly("backend", "profile", "session", "message")) return null
                    HermesDestinationRoute.Chats(
                        query.required("backend"),
                        query.required("profile"),
                        query["session"],
                        query["message"],
                    )
                }
                "artifacts" -> {
                    if (!query.hasOnly("backend", "profile", "artifact", "path")) return null
                    HermesDestinationRoute.Artifacts(
                        query.required("backend"),
                        query.required("profile"),
                        query["artifact"],
                        query["path"],
                    )
                }
                "automations" -> {
                    if (!query.hasOnly("backend", "profile", "destination", "resource")) return null
                    HermesDestinationRoute.Automations(
                        query.required("backend"),
                        query.required("profile"),
                        query["destination"]?.let(AutomationDestination::valueOf),
                        query["resource"],
                    )
                }
                "manage" -> {
                    if (!query.hasOnly("backend", "profile", "section", "destination", "resource")) return null
                    HermesDestinationRoute.Manage(
                        query.required("backend"),
                        query.required("profile"),
                        query["section"]?.let(ManageSection::valueOf),
                        query["destination"]?.let(ManageDestination::valueOf),
                        query["resource"],
                    )
                }
                "app-settings" -> {
                    if (!query.hasOnly("section")) return null
                    HermesDestinationRoute.AppSettings(query["section"]?.let(AppSettingsSection::valueOf))
                }
                else -> null
            }
        }.getOrNull()
    }

    private fun parseQuery(raw: String?): Map<String, String>? {
        if (raw.isNullOrEmpty()) return emptyMap()
        val result = linkedMapOf<String, String>()
        for (part in raw.split('&')) {
            val separator = part.indexOf('=')
            if (separator <= 0) return null
            val key = decode(part.substring(0, separator)) ?: return null
            val value = decode(part.substring(separator + 1)) ?: return null
            if (key in result || value.isBlank()) return null
            result[key] = value
        }
        return result
    }

    private fun decode(value: String): String? = runCatching { URLDecoder.decode(value, charset.name()) }.getOrNull()
    private fun Map<String, String>.required(key: String): String = get(key) ?: error("Missing $key")
    private fun Map<String, String>.hasOnly(vararg keys: String): Boolean = this.keys.all(keys.toSet()::contains)
}
