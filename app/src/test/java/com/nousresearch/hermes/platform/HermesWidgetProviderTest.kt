package com.nousresearch.hermes.platform

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import com.nousresearch.hermes.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HermesWidgetProviderTest {
    @Test
    fun `manifest exposes the privacy safe new chat widget`() {
        val context = RuntimeEnvironment.getApplication() as Context
        val receiver = context.packageManager.getReceiverInfo(
            ComponentName(context, HermesWidgetProvider::class.java),
            PackageManager.GET_META_DATA,
        )

        assertTrue(receiver.exported)
        assertEquals(
            R.xml.hermes_widget_info,
            receiver.metaData.getInt("android.appwidget.provider"),
        )
    }
}
