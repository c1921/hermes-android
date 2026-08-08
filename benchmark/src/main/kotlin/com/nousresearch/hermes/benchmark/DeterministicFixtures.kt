package com.nousresearch.hermes.benchmark

enum class FixtureMessageKind {
    USER,
    ASSISTANT,
    TOOL,
    ERROR,
}

data class FixtureMessage(
    val id: String,
    val kind: FixtureMessageKind,
    val text: String,
)

data class ContinuousStreamFixture(
    val chunks: List<String>,
    val intervalMillis: Long,
)

object DeterministicFixtures {
    const val MIXED_TRANSCRIPT_MESSAGE_COUNT = 500
    const val CONTINUOUS_STREAM_CHUNK_COUNT = 120
    const val CONTINUOUS_STREAM_INTERVAL_MILLIS = 25L

    fun mixedTranscript(): List<FixtureMessage> = List(MIXED_TRANSCRIPT_MESSAGE_COUNT) { index ->
        val kind = when (index % 4) {
            0 -> FixtureMessageKind.USER
            1 -> FixtureMessageKind.ASSISTANT
            2 -> FixtureMessageKind.TOOL
            else -> FixtureMessageKind.ERROR
        }
        FixtureMessage(
            id = "fixture-message-${index.toString().padStart(3, '0')}",
            kind = kind,
            text = when (kind) {
                FixtureMessageKind.USER -> "User request ${index + 1}"
                FixtureMessageKind.ASSISTANT -> "Assistant response ${index + 1}"
                FixtureMessageKind.TOOL -> "Tool result ${index + 1}"
                FixtureMessageKind.ERROR -> "Recoverable fixture error ${index + 1}"
            },
        )
    }

    fun continuousStream(): ContinuousStreamFixture = ContinuousStreamFixture(
        chunks = List(CONTINUOUS_STREAM_CHUNK_COUNT) { index ->
            "stream-chunk-${index.toString().padStart(3, '0')}"
        },
        intervalMillis = CONTINUOUS_STREAM_INTERVAL_MILLIS,
    )
}
