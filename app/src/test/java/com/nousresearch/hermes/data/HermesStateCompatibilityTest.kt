package com.nousresearch.hermes.data

import com.nousresearch.hermes.protocol.SessionRuntimeInfo
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HermesStateCompatibilityTest {
    @Test
    fun `controls stay hidden before a runtime session exists`() {
        val state = HermesState(runtimeInfo = SessionRuntimeInfo(desktopContract = 3))

        assertNull(state.compatibilityWarning)
        assertFalse(state.supportsRemoteAttachments)
        assertFalse(state.supportsSessionYolo)
    }

    @Test
    fun `missing contract warns and hides gated controls`() {
        val state = HermesState(runtimeSessionId = "runtime-1")

        assertNotNull(state.compatibilityWarning)
        assertFalse(state.supportsRemoteAttachments)
        assertFalse(state.supportsSessionYolo)
    }

    @Test
    fun `contract two enables attachments but not session yolo`() {
        val state = HermesState(
            runtimeSessionId = "runtime-1",
            runtimeInfo = SessionRuntimeInfo(desktopContract = 2),
        )

        assertNotNull(state.compatibilityWarning)
        assertTrue(state.supportsRemoteAttachments)
        assertFalse(state.supportsSessionYolo)
    }

    @Test
    fun `current contract exposes all gated controls without warning`() {
        val state = HermesState(
            runtimeSessionId = "runtime-1",
            runtimeInfo = SessionRuntimeInfo(desktopContract = 3),
        )

        assertNull(state.compatibilityWarning)
        assertTrue(state.supportsRemoteAttachments)
        assertTrue(state.supportsSessionYolo)
    }
}
