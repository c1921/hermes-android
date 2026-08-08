package com.nousresearch.hermes.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AttachmentWorkflowTest {
    private val scope = AttachmentScope("backend", "default", "runtime")

    @Test
    fun `only exact scoped ready items can be sent`() {
        val ready = attachment("ready", AttachmentPhase.READY)
        val uploading = attachment("uploading", AttachmentPhase.UPLOADING)

        assertTrue(listOf(ready).readyToSend(scope))
        assertFalse(listOf(ready, uploading).readyToSend(scope))
        assertFalse(listOf(ready.copy(scope = scope.copy(runtimeSessionId = "other"))).readyToSend(scope))
        assertTrue(ready.matches(scope))
        assertFalse(ready.matches(scope.copy(runtimeSessionId = "other")))
    }

    @Test
    fun `executable attachment types are rejected before reading`() {
        assertFalse(isSupportedAttachmentMime("application/vnd.android.package-archive"))
        assertFalse(isSupportedAttachmentMime("application/x-msdownload"))
        assertTrue(isSupportedAttachmentMime("application/pdf"))
        assertTrue(isSupportedAttachmentMime("image/jpeg"))
    }

    private fun attachment(id: String, phase: AttachmentPhase) = PendingAttachment(
        id = id,
        label = "$id.txt",
        phase = phase,
        scope = scope,
    )
}
