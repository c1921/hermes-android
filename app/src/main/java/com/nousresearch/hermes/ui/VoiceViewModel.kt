package com.nousresearch.hermes.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nousresearch.hermes.audio.AndroidVoiceRecorder
import com.nousresearch.hermes.audio.AndroidVoiceHaptics
import com.nousresearch.hermes.audio.AndroidVoicePlayer
import com.nousresearch.hermes.audio.VoiceHaptic
import com.nousresearch.hermes.audio.VoicePlaybackPhase
import com.nousresearch.hermes.audio.sanitizeTextForSpeech
import com.nousresearch.hermes.data.BackendConfig
import com.nousresearch.hermes.data.VoiceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class VoicePhase { IDLE, RECORDING, TRANSCRIBING }
enum class VoiceRecordingMode { PRESS_TO_TALK, LOCKED }
enum class SpeechPhase { IDLE, LOADING, PLAYING, PAUSED }

data class VoiceTranscript(val id: String, val text: String)

data class VoiceUiState(
    val phase: VoicePhase = VoicePhase.IDLE,
    val recordingMode: VoiceRecordingMode = VoiceRecordingMode.LOCKED,
    val level: Float = 0f,
    val elapsedMillis: Long = 0L,
    val transcript: VoiceTranscript? = null,
    val error: String? = null,
)

data class SpeechUiState(
    val phase: SpeechPhase = SpeechPhase.IDLE,
    val messageId: String? = null,
    val outputName: String = "Android media output",
    val error: String? = null,
)

@HiltViewModel
class VoiceViewModel @Inject constructor(
    private val recorder: AndroidVoiceRecorder,
    private val player: AndroidVoicePlayer,
    private val haptics: AndroidVoiceHaptics,
    private val voice: VoiceRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow(VoiceUiState())
    val state = mutableState.asStateFlow()
    private val mutableSpeechState = MutableStateFlow(SpeechUiState())
    val speechState = mutableSpeechState.asStateFlow()

    private var backend: BackendConfig? = null
    private var clockJob: Job? = null
    private var levelJob: Job? = null
    private var timeoutJob: Job? = null
    private var transcriptionJob: Job? = null
    private var speechJob: Job? = null
    private var speechGeneration = 0L

    fun bind(config: BackendConfig) {
        if (backend?.id != config.id) {
            cancelRecording(feedback = false)
            stopSpeaking()
            backend = config
            mutableState.value = VoiceUiState()
        }
    }

    fun startRecording(mode: VoiceRecordingMode = VoiceRecordingMode.LOCKED) {
        if (mutableState.value.phase != VoicePhase.IDLE) return
        stopSpeaking()
        runCatching {
            recorder.start(viewModelScope) {
                viewModelScope.launch { cancelRecording("Recording stopped because Android audio focus changed") }
            }
        }.onSuccess {
            haptics.perform(VoiceHaptic.RECORD_START)
            val startedAt = System.currentTimeMillis()
            mutableState.value = VoiceUiState(phase = VoicePhase.RECORDING, recordingMode = mode)
            levelJob = viewModelScope.launch {
                recorder.level.collect { level -> mutableState.update { it.copy(level = level) } }
            }
            clockJob = viewModelScope.launch {
                while (true) {
                    mutableState.update { it.copy(elapsedMillis = System.currentTimeMillis() - startedAt) }
                    delay(100L)
                }
            }
            timeoutJob = viewModelScope.launch {
                delay(MAX_RECORDING_MILLIS)
                stopAndTranscribe()
            }
        }.onFailure { error ->
            mutableState.value = VoiceUiState(error = error.userVoiceMessage())
        }
    }

    fun lockRecording() {
        val shouldLock = mutableState.value.let {
            it.phase == VoicePhase.RECORDING && it.recordingMode != VoiceRecordingMode.LOCKED
        }
        if (!shouldLock) return
        mutableState.update { current ->
            current.copy(recordingMode = VoiceRecordingMode.LOCKED)
        }
        haptics.perform(VoiceHaptic.RECORD_LOCK)
    }

    fun stopAndTranscribe() {
        if (mutableState.value.phase != VoicePhase.RECORDING) return
        val config = backend ?: return cancelRecording("Reconnect Hermes before using voice input")
        haptics.perform(VoiceHaptic.RECORD_STOP)
        stopMetering()
        mutableState.update { it.copy(phase = VoicePhase.TRANSCRIBING, level = 0f, error = null) }
        transcriptionJob = viewModelScope.launch {
            try {
                val recording = withContext(Dispatchers.IO) { recorder.stop() }
                val transcript = voice.transcribe(config, recording)
                if (transcript.isBlank()) {
                    mutableState.value = VoiceUiState(error = "Hermes did not detect speech in that recording")
                } else {
                    mutableState.value = VoiceUiState(transcript = VoiceTranscript(UUID.randomUUID().toString(), transcript))
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                recorder.cancel()
                mutableState.value = VoiceUiState(error = error.userVoiceMessage())
            }
        }
    }

    fun cancelRecording(message: String? = null, feedback: Boolean = true) {
        val wasRecording = mutableState.value.phase == VoicePhase.RECORDING
        stopMetering()
        transcriptionJob?.cancel()
        transcriptionJob = null
        recorder.cancel()
        mutableState.value = VoiceUiState(error = message)
        if (feedback && wasRecording) haptics.perform(VoiceHaptic.RECORD_CANCEL)
    }

    fun permissionDenied() {
        mutableState.value = VoiceUiState(error = "Microphone access is required for Hermes voice input")
    }

    fun consumeTranscript(id: String) {
        mutableState.update { current ->
            if (current.transcript?.id == id) current.copy(transcript = null) else current
        }
    }

    fun clearError() = mutableState.update { it.copy(error = null) }

    fun speak(messageId: String, text: String) {
        val speakableText = sanitizeTextForSpeech(text)
        if (speakableText.isBlank()) return
        if (mutableSpeechState.value.messageId == messageId && mutableSpeechState.value.phase != SpeechPhase.IDLE) {
            stopSpeaking()
            return
        }
        cancelRecording(feedback = false)
        stopSpeaking()
        val generation = ++speechGeneration
        mutableSpeechState.value = SpeechUiState(phase = SpeechPhase.LOADING, messageId = messageId)
        speechJob = viewModelScope.launch {
            try {
                val config = backend ?: throw IllegalStateException("Reconnect Hermes before playing spoken replies")
                val audio = voice.speak(config, speakableText)
                if (generation != speechGeneration) return@launch
                player.play(
                    audio = audio,
                    onStatus = { status ->
                        if (generation == speechGeneration && mutableSpeechState.value.messageId == messageId) {
                            mutableSpeechState.value = SpeechUiState(
                                phase = if (status.phase == VoicePlaybackPhase.PLAYING) SpeechPhase.PLAYING else SpeechPhase.PAUSED,
                                messageId = messageId,
                                outputName = status.outputName,
                            )
                        }
                    },
                    onError = { message ->
                        if (generation == speechGeneration && mutableSpeechState.value.messageId == messageId) {
                            mutableSpeechState.value = SpeechUiState(error = message)
                        }
                    },
                    onComplete = {
                        if (generation == speechGeneration && mutableSpeechState.value.messageId == messageId) {
                            mutableSpeechState.value = SpeechUiState()
                        }
                    },
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                if (generation == speechGeneration) {
                    mutableSpeechState.value = SpeechUiState(error = error.userVoiceMessage())
                }
            }
        }
    }

    fun pauseSpeaking() = player.pause()
    fun resumeSpeaking() = player.resume()
    fun showOutputSwitcher() = player.showOutputSwitcher()

    fun stopSpeaking() {
        speechGeneration++
        speechJob?.cancel()
        speechJob = null
        player.stop()
        mutableSpeechState.value = SpeechUiState()
    }

    fun clearSpeechError() = mutableSpeechState.update { it.copy(error = null) }

    override fun onCleared() {
        cancelRecording(feedback = false)
        stopSpeaking()
        super.onCleared()
    }

    private fun stopMetering() {
        clockJob?.cancel()
        levelJob?.cancel()
        timeoutJob?.cancel()
        clockJob = null
        levelJob = null
        timeoutJob = null
    }

    private fun Throwable.userVoiceMessage(): String = message?.trim().takeUnless { it.isNullOrBlank() }
        ?: "Hermes voice input failed"

    private companion object {
        const val MAX_RECORDING_MILLIS = 120_000L
    }
}
