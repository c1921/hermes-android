package com.nousresearch.hermes.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileIdentityPolicyTest {
    @Test
    fun `discard guard detects every editable profile identity field`() {
        assertFalse(profileIdentityDirty("soul", "soul", "nous", "nous", "hermes-4", "hermes-4"))
        assertTrue(profileIdentityDirty("soul", "changed", "nous", "nous", "hermes-4", "hermes-4"))
        assertTrue(profileIdentityDirty("soul", "soul", "nous", "other", "hermes-4", "hermes-4"))
        assertTrue(profileIdentityDirty("soul", "soul", "nous", "nous", "hermes-4", "other"))
    }
}
