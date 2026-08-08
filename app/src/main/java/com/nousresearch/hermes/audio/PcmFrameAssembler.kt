package com.nousresearch.hermes.audio

data class VoicePcmFormat(
    val sampleRate: Int,
    val channels: Int,
    val sampleWidthBytes: Int = 2,
) {
    init {
        require(sampleRate in MIN_SAMPLE_RATE..MAX_SAMPLE_RATE) {
            "Hermes voice stream sample rate is outside the Android safety range"
        }
        require(channels == 1) { "Hermes voice streams must be mono" }
        require(sampleWidthBytes == 2) { "Hermes voice streams must use int16 PCM" }
    }

    private companion object {
        const val MIN_SAMPLE_RATE = 8_000
        const val MAX_SAMPLE_RATE = 96_000
    }
}

internal class PcmFrameAssembler(
    val format: VoicePcmFormat,
    private val maxFrameBytes: Int = DEFAULT_MAX_FRAME_BYTES,
    private val maxStreamBytes: Long = DEFAULT_MAX_STREAM_BYTES,
) {
    private var carriedByte: Byte? = null
    private var receivedBytes = 0L
    private var finished = false

    init {
        require(maxFrameBytes > 0) { "maxFrameBytes must be positive" }
        require(maxStreamBytes >= maxFrameBytes) { "maxStreamBytes must cover one frame" }
    }

    fun append(frame: ByteArray): ByteArray {
        check(!finished) { "PCM stream is already finished" }
        require(frame.size <= maxFrameBytes) { "Hermes voice PCM frame exceeds the Android safety limit" }
        require(receivedBytes + frame.size <= maxStreamBytes) {
            "Hermes voice stream exceeds the Android audio safety limit"
        }
        receivedBytes += frame.size

        if (frame.isEmpty() && carriedByte == null) return ByteArray(0)
        val merged = if (carriedByte == null) {
            frame
        } else {
            ByteArray(frame.size + 1).also {
                it[0] = carriedByte!!
                frame.copyInto(it, destinationOffset = 1)
            }
        }
        carriedByte = null
        val usableBytes = merged.size - (merged.size % format.sampleWidthBytes)
        if (usableBytes < merged.size) carriedByte = merged[usableBytes]
        return merged.copyOf(usableBytes)
    }

    fun finish() {
        check(!finished) { "PCM stream is already finished" }
        require(carriedByte == null) { "Hermes voice stream ended mid-sample" }
        finished = true
    }

    private companion object {
        const val DEFAULT_MAX_FRAME_BYTES = MAX_PCM_CHUNK_BYTES
        const val DEFAULT_MAX_STREAM_BYTES = 25L * 1024L * 1024L
    }
}
