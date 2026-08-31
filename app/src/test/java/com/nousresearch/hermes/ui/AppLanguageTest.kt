package com.nousresearch.hermes.ui

import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import com.nousresearch.hermes.R
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.VANILLA_ICE_CREAM])
class AppLanguageTest {
    @Test
    fun languageTagsMapToSupportedChoices() {
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromLanguageTag(null))
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromLanguageTag(""))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromLanguageTag("en-US"))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromLanguageTag("en-GB"))
        assertEquals(AppLanguage.SIMPLIFIED_CHINESE, AppLanguage.fromLanguageTag("zh-CN"))
        assertEquals(AppLanguage.SIMPLIFIED_CHINESE, AppLanguage.fromLanguageTag("zh-Hans"))
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromLanguageTag("fr-FR"))
    }

    @Test
    fun supportedLanguageTagsStayStable() {
        assertEquals("", AppLanguage.SYSTEM.languageTag)
        assertEquals("en-US", AppLanguage.ENGLISH.languageTag)
        assertEquals("zh-CN", AppLanguage.SIMPLIFIED_CHINESE.languageTag)
    }

    @Test
    fun simplifiedChineseResourcesResolve() {
        val base = RuntimeEnvironment.getApplication()
        val configuration = Configuration(base.resources.configuration).apply {
            setLocales(LocaleList(Locale.forLanguageTag("zh-CN")))
        }
        val chinese = base.createConfigurationContext(configuration)
        assertEquals("应用设置", chinese.getString(R.string.app_settings))
        assertEquals("新建对话", chinese.getString(R.string.widget_new_chat_label))
        assertEquals("Hermes 任务已完成", chinese.getString(R.string.notification_title_completion))
    }
}
