package com.nousresearch.hermes.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import com.nousresearch.hermes.data.SessionCredentialStore
import com.nousresearch.hermes.network.DashboardSessionCookie
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SecureTokenStore @Inject constructor(
    @ApplicationContext context: Context,
) : SessionCredentialStore {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }

    override fun put(backendId: String, cookie: DashboardSessionCookie) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val ciphertext = cipher.doFinal(cookie.headerValue.toByteArray(StandardCharsets.UTF_8))
        val encoded = listOf(cipher.iv, ciphertext).joinToString(SEPARATOR) {
            Base64.encodeToString(it, Base64.NO_WRAP)
        }
        check(preferences.edit().putString(secretKey(backendId), encoded).remove(legacySecretKey(backendId)).commit()) {
            "Could not persist the encrypted Hermes credential"
        }
    }

    override fun get(backendId: String): DashboardSessionCookie? {
        val encoded = preferences.getString(secretKey(backendId), null) ?: return null
        return runCatching {
            val (iv, ciphertext) = encoded.split(SEPARATOR, limit = 2).map {
                Base64.decode(it, Base64.NO_WRAP)
            }
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
            val header = String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8)
            DashboardSessionCookie.fromCookieHeader(header)
        }.getOrNull()
    }

    override fun remove(backendId: String) {
        preferences.edit().remove(secretKey(backendId)).remove(legacySecretKey(backendId)).apply()
    }

    private fun getOrCreateKey(): SecretKey {
        (keyStore.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .setUserAuthenticationRequired(false)
                .build(),
        )
        return generator.generateKey()
    }

    private fun secretKey(backendId: String) = "backend.$backendId.dashboard_session.v2"
    private fun legacySecretKey(backendId: String) = "backend.$backendId.token"

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "hermes.backend.sessions.v2"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val PREFERENCES = "hermes_secrets"
        const val SEPARATOR = "."
    }
}
