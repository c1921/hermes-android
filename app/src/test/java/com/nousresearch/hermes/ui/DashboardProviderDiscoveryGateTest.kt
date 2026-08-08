package com.nousresearch.hermes.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DashboardProviderDiscoveryGateTest {
    @Test
    fun `duplicate discovery is rejected until the active request finishes`() {
        val gate = DashboardProviderDiscoveryGate()
        val first = requireNotNull(gate.begin())

        assertNull(gate.begin())
        assertTrue(gate.isCurrent(first))

        gate.finish(first)
        assertNotNull(gate.begin())
    }

    @Test
    fun `input changes invalidate stale responses even after a round trip`() {
        val gate = DashboardProviderDiscoveryGate()
        val original = requireNotNull(gate.begin())

        gate.invalidate()
        gate.invalidate()

        assertFalse(gate.isCurrent(original))
        val replacement = requireNotNull(gate.begin())
        assertTrue(gate.isCurrent(replacement))
        gate.finish(original)
        assertTrue(gate.isCurrent(replacement))
    }
}
