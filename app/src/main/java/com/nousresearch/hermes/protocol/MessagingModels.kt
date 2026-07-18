package com.nousresearch.hermes.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MessagingPlatformsResponse(
    val platforms: List<MessagingPlatformInfo> = emptyList(),
    @SerialName("gateway_start_command") val gatewayStartCommand: String? = null,
)

@Serializable
data class MessagingPlatformInfo(
    val id: String,
    val name: String,
    val description: String = "",
    @SerialName("docs_url") val docsUrl: String = "",
    val enabled: Boolean = false,
    val configured: Boolean = false,
    @SerialName("gateway_running") val gatewayRunning: Boolean = false,
    val state: String? = null,
    @SerialName("error_code") val errorCode: String? = null,
    @SerialName("error_message") val errorMessage: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("home_channel") val homeChannel: MessagingHomeChannel? = null,
    @SerialName("env_vars") val envVars: List<MessagingEnvVarInfo> = emptyList(),
)

@Serializable
data class MessagingEnvVarInfo(
    val key: String,
    val required: Boolean = false,
    @SerialName("is_set") val isSet: Boolean = false,
    @SerialName("redacted_value") val redactedValue: String? = null,
    val description: String = "",
    val prompt: String = "",
    val help: String = "",
    val url: String? = null,
    @SerialName("is_password") val isPassword: Boolean = false,
    val advanced: Boolean = false,
)

@Serializable
data class MessagingHomeChannel(
    @SerialName("chat_id") val chatId: String,
    val name: String,
    val platform: String,
    @SerialName("thread_id") val threadId: String? = null,
)

@Serializable
data class MessagingPlatformUpdateResponse(
    val ok: Boolean,
    val platform: String,
)

@Serializable
data class MessagingPlatformTestResponse(
    val ok: Boolean,
    val message: String,
    val state: String? = null,
)
