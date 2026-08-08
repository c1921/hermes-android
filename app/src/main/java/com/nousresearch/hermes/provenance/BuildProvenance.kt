package com.nousresearch.hermes.provenance

import com.nousresearch.hermes.BuildConfig

/**
 * The generated build identity shared by diagnostics, support exports, and release metadata.
 *
 * Values that cannot be known for a local build deliberately remain explicit fallbacks such as
 * `unknown`, `local`, or `unsigned`; they are never presented as a verified CI or signed build.
 */
data class BuildProvenance(
    val androidVersion: String,
    val versionCode: Int,
    val channel: String,
    val androidCommit: String,
    val auditedHermesCommit: String,
    val hermesAgentVersion: String,
    val hermesAgentVersionRange: String,
    val hermesDesktopVersion: String,
    val hermesDesktopVersionRange: String,
    val toolchainDigest: String,
    val buildIdentity: String,
    val packageName: String,
    val signingFingerprint: String,
    val author: String,
) {
    fun reportEntries(): List<Pair<String, String>> = listOf(
        "Android version" to "$androidVersion ($versionCode)",
        "Channel" to channel,
        "Android commit" to androidCommit,
        "Audited Hermes commit" to auditedHermesCommit,
        "Hermes Agent" to hermesAgentVersion,
        "Hermes Agent range" to hermesAgentVersionRange,
        "Hermes Desktop" to hermesDesktopVersion,
        "Hermes Desktop range" to hermesDesktopVersionRange,
        "Toolchain digest" to toolchainDigest,
        "Build identity" to buildIdentity,
        "Package" to packageName,
        "Signing fingerprint" to signingFingerprint,
        "Build author" to author,
    )

    companion object {
        fun from(inputs: BuildProvenanceInputs): BuildProvenance = BuildProvenance(
            androidVersion = inputs.androidVersion.ifBlank { "unknown" },
            versionCode = inputs.versionCode,
            channel = inputs.channel.ifBlank { "unknown" },
            androidCommit = inputs.androidCommit.ifBlank { "unknown" },
            auditedHermesCommit = inputs.auditedHermesCommit.ifBlank { "unknown" },
            hermesAgentVersion = inputs.hermesAgentVersion.ifBlank { "unknown" },
            hermesAgentVersionRange = inputs.hermesAgentVersionRange.ifBlank { "unknown" },
            hermesDesktopVersion = inputs.hermesDesktopVersion.ifBlank { "unknown" },
            hermesDesktopVersionRange = inputs.hermesDesktopVersionRange.ifBlank { "unknown" },
            toolchainDigest = inputs.toolchainDigest.ifBlank { "unknown" },
            buildIdentity = inputs.buildIdentity.ifBlank { "local" },
            packageName = inputs.packageName.ifBlank { "unknown" },
            signingFingerprint = inputs.signingFingerprint.ifBlank { "unsigned" },
            author = inputs.author.ifBlank { "luinbytes" },
        )
    }
}

data class BuildProvenanceInputs(
    val androidVersion: String,
    val versionCode: Int,
    val channel: String,
    val androidCommit: String,
    val auditedHermesCommit: String,
    val hermesAgentVersion: String,
    val hermesAgentVersionRange: String,
    val hermesDesktopVersion: String,
    val hermesDesktopVersionRange: String,
    val toolchainDigest: String,
    val buildIdentity: String,
    val packageName: String,
    val signingFingerprint: String,
    val author: String,
)

object BuildProvenanceSource {
    val current: BuildProvenance by lazy {
        BuildProvenance.from(
            BuildProvenanceInputs(
                androidVersion = BuildConfig.VERSION_NAME,
                versionCode = BuildConfig.VERSION_CODE,
                channel = BuildConfig.HERMES_BUILD_CHANNEL,
                androidCommit = BuildConfig.HERMES_ANDROID_COMMIT,
                auditedHermesCommit = BuildConfig.HERMES_AUDIT_COMMIT,
                hermesAgentVersion = BuildConfig.HERMES_AGENT_VERSION,
                hermesAgentVersionRange = BuildConfig.HERMES_AGENT_VERSION_RANGE,
                hermesDesktopVersion = BuildConfig.HERMES_DESKTOP_VERSION,
                hermesDesktopVersionRange = BuildConfig.HERMES_DESKTOP_VERSION_RANGE,
                toolchainDigest = BuildConfig.HERMES_TOOLCHAIN_DIGEST,
                buildIdentity = BuildConfig.HERMES_BUILD_IDENTITY,
                packageName = BuildConfig.APPLICATION_ID,
                signingFingerprint = BuildConfig.HERMES_SIGNING_FINGERPRINT,
                author = BuildConfig.HERMES_BUILD_AUTHOR,
            ),
        )
    }
}
