package com.nousresearch.hermes.network

import com.nousresearch.hermes.data.AuthMode
import com.nousresearch.hermes.data.BackendConfig
import org.junit.Assert.assertTrue
import org.junit.Test

class TransportPolicyTest {
    @Test
    fun `https is accepted without private-network override`() {
        assertTrue(TransportPolicy.validate(config("https://hermes.example.com", false)).isSuccess)
    }

    @Test
    fun `cleartext public host is rejected even with override`() {
        assertTrue(TransportPolicy.validate(config("http://hermes.example.com", true)).isFailure)
    }

    @Test
    fun `cleartext private literal requires explicit override`() {
        assertTrue(TransportPolicy.validate(config("http://192.168.1.10:8080", false)).isFailure)
        assertTrue(TransportPolicy.validate(config("http://192.168.1.10:8080", true)).isSuccess)
    }

    @Test
    fun `tailscale cgnat range is treated as private`() {
        assertTrue(TransportPolicy.validate(config("http://100.79.4.2:8080", true)).isSuccess)
    }

    private fun config(url: String, allowHttp: Boolean) = BackendConfig(
        id = "test",
        label = "Test",
        baseUrl = url,
        authMode = AuthMode.TOKEN,
        allowInsecurePrivateNetwork = allowHttp,
    )
}

