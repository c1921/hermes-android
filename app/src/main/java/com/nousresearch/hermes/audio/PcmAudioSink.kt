package com.nousresearch.hermes.audio

/** A serialized, terminal sink for aligned PCM chunks. */
interface PcmAudioSink {
    fun write(pcm: ByteArray)

    fun end()
}

private const val PCM_16_BIT_BYTES = 2
internal const val MAX_PCM_CHUNK_BYTES = 64 * 1024

internal fun validatePcmChunk(pcm: ByteArray) {
    require(pcm.isNotEmpty()) { "PCM chunks must not be empty" }
    require(pcm.size % PCM_16_BIT_BYTES == 0) {
        "PCM chunks must contain complete 16-bit samples"
    }
    require(pcm.size <= MAX_PCM_CHUNK_BYTES) {
        "PCM chunks exceed the $MAX_PCM_CHUNK_BYTES-byte playback bound"
    }
}

internal class PcmStreamLifecycle {
    private var writable = true

    @Synchronized
    fun requireWritable() {
        check(writable) { "PCM stream is no longer writable" }
    }

    @Synchronized
    fun end() {
        requireWritable()
        writable = false
    }

    @Synchronized
    fun stop() {
        writable = false
    }
}
