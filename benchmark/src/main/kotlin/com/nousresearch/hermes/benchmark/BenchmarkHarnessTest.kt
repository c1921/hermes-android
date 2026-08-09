package com.nousresearch.hermes.benchmark

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BenchmarkHarnessTest {
    @Test
    fun deterministicFixturesHaveTheAcceptedCorpusShape() {
        val transcript = DeterministicFixtures.mixedTranscript()
        val stream = DeterministicFixtures.continuousStream()

        assertEquals(500, transcript.size)
        assertEquals(transcript, DeterministicFixtures.mixedTranscript())
        assertEquals(4, transcript.map(FixtureMessage::kind).distinct().size)
        assertEquals(120, stream.chunks.size)
        assertEquals(25L, stream.intervalMillis)
        assertEquals(stream, DeterministicFixtures.continuousStream())
    }

    @Test
    fun evidenceJsonContainsStableMetadataAndResults() {
        val evidence = BenchmarkEvidence(
            benchmark = "cold-start",
            commit = "abc123",
            device = "Pixel 6",
            androidApi = 36,
            toolchain = "AGP 8.9.2 / Gradle 8.11.1",
            profileState = "baseline-profile",
            repetitions = 5,
            environment = mapOf("runner" to "gmd", "profile" to "release"),
            metrics = mapOf("timeToInitialDisplayMs" to 123.4),
        )

        assertEquals(
            "{\"benchmark\":\"cold-start\",\"commit\":\"abc123\",\"device\":\"Pixel 6\"," +
                "\"androidApi\":36,\"toolchain\":\"AGP 8.9.2 / Gradle 8.11.1\"," +
                "\"profileState\":\"baseline-profile\",\"repetitions\":5," +
                "\"environment\":{\"profile\":\"release\",\"runner\":\"gmd\"}," +
                "\"metrics\":{\"timeToInitialDisplayMs\":123.4}}",
            evidence.toJsonLine(),
        )
    }

    @Test
    fun comparatorAcceptsExactlyTenPercentAndRejectsMore() {
        val baseline = mapOf("timeToInitialDisplayMs" to 100.0)

        assertTrue(
            BenchmarkRegression.isAccepted(
                baseline = baseline,
                candidate = mapOf("timeToInitialDisplayMs" to 110.0),
            ),
        )
        assertFalse(
            BenchmarkRegression.isAccepted(
                baseline = baseline,
                candidate = mapOf("timeToInitialDisplayMs" to 110.01),
            ),
        )
        assertEquals(
            listOf("timeToInitialDisplayMs"),
            BenchmarkRegression.findFailures(
                baseline = baseline,
                candidate = mapOf("timeToInitialDisplayMs" to 110.01),
            ).map(RegressionFailure::metric),
        )
    }
}
