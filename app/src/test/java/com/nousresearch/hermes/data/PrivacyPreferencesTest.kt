package com.nousresearch.hermes.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.nousresearch.hermes.ui.theme.HermesSkin
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PrivacyPreferencesTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `secure screen preference is durable and defaults off`() = runTest {
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val file = File(temporaryFolder.root, "privacy.preferences_pb")
        val store = PreferenceDataStoreFactory.create(scope = scope) { file }
        val preferences = PrivacyPreferences(store)

        assertFalse(preferences.secureScreen.first())
        assertEquals(HermesSkin.NOUS, preferences.skin.first())
        preferences.setSecureScreen(true)
        assertTrue(preferences.secureScreen.first())
        preferences.setSecureScreen(false)
        assertFalse(preferences.secureScreen.first())
        preferences.setSkin(HermesSkin.EMBER)
        assertEquals(HermesSkin.EMBER, preferences.skin.first())
        scope.cancel()
    }
}
