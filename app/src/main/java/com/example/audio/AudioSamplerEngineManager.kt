package com.example.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Process
import android.util.Log
import com.example.model.PadSample
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Arrays
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Coordinates the polyphonic real-time audio pipeline:
 * - Thread-safe lock-free JNI calls (or optimized Kotlin fallback)
 * - Low-latency AudioTrack running in a high-priority audio thread
 * - VoiceManager with ADSR volume envelopes and voice allocation
 * - C++ WAV decoding and peak sample generation
 */
class AudioSamplerEngineManager {
    companion object {
        private const val TAG = "AudioSamplerManager"
        const val NUM_PADS = 20
        const val SAMPLE_RATE = 44100
        private const val BUFFER_FRAMES = 256 // Low latency buffer frame size
    }

    private val isRunning = AtomicBoolean(false)
    private var audioThread: Thread? = null
    private var audioTrack: AudioTrack? = null

    // 20 polyphonic voices for fallback
    private val kotlinVoices = Array(NUM_PADS) { SamplerVoice() }
    private val padSamplesList = mutableListOf<PadSample>()

    // Flash trigger events for UI
    private val _padTriggerEvents = MutableStateFlow<Pair<Int, Long>?>(null)
    val padTriggerEvents: StateFlow<Pair<Int, Long>?> = _padTriggerEvents.asStateFlow()

    private val _isNativeEngineActive = MutableStateFlow(false)
    val isNativeEngineActive: StateFlow<Boolean> = _isNativeEngineActive.asStateFlow()

    init {
        val nativeLoaded = NativeAudioEngine.isNativeLoaded()
        if (nativeLoaded) {
            try {
                NativeAudioEngine.nativeInit(SAMPLE_RATE)
                _isNativeEngineActive.value = true
                Log.i(TAG, "Native C++ AudioEngine initialized successfully")
            } catch (e: Throwable) {
                Log.e(TAG, "Error initializing native engine: ${e.message}")
                _isNativeEngineActive.value = false
            }
        } else {
            _isNativeEngineActive.value = false
            Log.i(TAG, "Running high-performance Kotlin audio engine fallback")
        }

        loadDefaultSamples()
        startAudioTrack()
    }

    private fun loadDefaultSamples() {
        val synthesized = SampleSynthesizer.generatePadSamples()
        padSamplesList.clear()
        padSamplesList.addAll(synthesized)

        for (sample in synthesized) {
            kotlinVoices[sample.index].setSampleRate(SAMPLE_RATE)
            kotlinVoices[sample.index].setSampleBuffer(sample.pcmBuffer, sample.channels)
            kotlinVoices[sample.index].setADSR(sample.attackSec, sample.decaySec, sample.sustainLevel, sample.releaseSec)

            if (_isNativeEngineActive.value) {
                try {
                    NativeAudioEngine.nativeLoadSample(
                        sample.index,
                        sample.pcmBuffer,
                        sample.channels,
                        sample.totalFrames
                    )
                    NativeAudioEngine.nativeSetPadADSR(
                        sample.index,
                        sample.attackSec,
                        sample.decaySec,
                        sample.sustainLevel,
                        sample.releaseSec
                    )
                } catch (e: Throwable) {
                    Log.e(TAG, "Failed loading sample to native: ${e.message}")
                }
            }
        }
    }

    fun getSamples(): List<PadSample> = padSamplesList

    fun getSample(padIndex: Int): PadSample? {
        return padSamplesList.getOrNull(padIndex)
    }

    /**
     * Exact finger-down low-latency trigger:
     * Dispatches immediately without locks to C++ JNI bridge or atomic Kotlin voice.
     */
    fun triggerPad(padIndex: Int, velocity: Float = 0.95f) {
        if (padIndex !in 0 until NUM_PADS) return

        // 1. Trigger C++ native engine without locking (dynamic VoiceManager allocation)
        if (_isNativeEngineActive.value) {
            try {
                NativeAudioEngine.nativeTriggerPad(padIndex, velocity)
            } catch (e: Throwable) {
                Log.e(TAG, "Native trigger error: ${e.message}")
                kotlinVoices[padIndex].trigger(velocity)
            }
        } else {
            // 2. Lock-free atomic trigger in Kotlin engine
            kotlinVoices[padIndex].trigger(velocity)
        }

        // Notify UI for tactile feedback
        _padTriggerEvents.value = Pair(padIndex, System.currentTimeMillis())
    }

    fun releasePad(padIndex: Int) {
        if (padIndex !in 0 until NUM_PADS) return
        if (_isNativeEngineActive.value) {
            try {
                NativeAudioEngine.nativeReleasePad(padIndex)
            } catch (_: Throwable) {
                kotlinVoices[padIndex].release()
            }
        } else {
            kotlinVoices[padIndex].release()
        }
    }

    fun stopPad(padIndex: Int) {
        if (padIndex !in 0 until NUM_PADS) return
        if (_isNativeEngineActive.value) {
            try {
                NativeAudioEngine.nativeStopPad(padIndex)
            } catch (_: Throwable) {
                kotlinVoices[padIndex].stop()
            }
        } else {
            kotlinVoices[padIndex].stop()
        }
    }

    fun updateTrim(padIndex: Int, startFrame: Int, endFrame: Int) {
        if (padIndex !in 0 until NUM_PADS) return
        val sample = padSamplesList.getOrNull(padIndex) ?: return
        sample.startFrame = startFrame
        sample.endFrame = endFrame

        kotlinVoices[padIndex].setTrimBounds(startFrame, endFrame)
        if (_isNativeEngineActive.value) {
            try {
                NativeAudioEngine.nativeSetPadTrim(padIndex, startFrame, endFrame)
            } catch (e: Throwable) {
                Log.e(TAG, "Native trim error: ${e.message}")
            }
        }
    }

    fun updateADSR(padIndex: Int, attackSec: Float, decaySec: Float, sustainLevel: Float, releaseSec: Float) {
        if (padIndex !in 0 until NUM_PADS) return
        val sample = padSamplesList.getOrNull(padIndex) ?: return
        sample.attackSec = attackSec
        sample.decaySec = decaySec
        sample.sustainLevel = sustainLevel
        sample.releaseSec = releaseSec

        kotlinVoices[padIndex].setADSR(attackSec, decaySec, sustainLevel, releaseSec)
        if (_isNativeEngineActive.value) {
            try {
                NativeAudioEngine.nativeSetPadADSR(padIndex, attackSec, decaySec, sustainLevel, releaseSec)
            } catch (e: Throwable) {
                Log.e(TAG, "Native ADSR update error: ${e.message}")
            }
        }
    }

    /**
     * Loads a WAV file from disk directly through C++ WavLoader.
     * Returns generated peak samples for the waveform viewer.
     */
    fun loadWavFile(padIndex: Int, filePath: String): FloatArray? {
        if (padIndex !in 0 until NUM_PADS) return null
        if (_isNativeEngineActive.value) {
            try {
                val peaks = NativeAudioEngine.nativeLoadWavFile(filePath, padIndex)
                if (peaks != null && peaks.isNotEmpty()) {
                    val sample = padSamplesList.getOrNull(padIndex)
                    if (sample != null) {
                        padSamplesList[padIndex] = sample.copy(
                            peaks = peaks,
                            transientSpikeIndices = PadSample.detectTransients(peaks)
                        )
                    }
                    return peaks
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Native loadWavFile error: ${e.message}")
            }
        }
        return null
    }

    fun getActiveVoiceCount(): Int {
        if (_isNativeEngineActive.value) {
            try {
                return NativeAudioEngine.nativeGetActiveVoiceCount()
            } catch (_: Throwable) {}
        }
        return kotlinVoices.count { it.isVoicePlaying() }
    }

    private fun startAudioTrack() {
        if (isRunning.get()) return

        val minBufSize = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_STEREO,
            AudioFormat.ENCODING_PCM_FLOAT
        )
        val bufferSize = maxOf(minBufSize, BUFFER_FRAMES * 2 * 4 * 2)

        try {
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            audioTrack = track
            if (track.state == AudioTrack.STATE_INITIALIZED) {
                track.play()
                isRunning.set(true)

                audioThread = Thread {
                    try {
                        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
                    } catch (_: Throwable) {}
                    val renderBuffer = FloatArray(BUFFER_FRAMES * 2) // Interleaved stereo

                    while (isRunning.get()) {
                        // Check if native engine is active
                        if (_isNativeEngineActive.value) {
                            try {
                                NativeAudioEngine.nativeRender(renderBuffer, BUFFER_FRAMES)
                            } catch (e: Throwable) {
                                // Fallback to Kotlin render
                                renderKotlinMix(renderBuffer, BUFFER_FRAMES)
                            }
                        } else {
                            renderKotlinMix(renderBuffer, BUFFER_FRAMES)
                        }

                        audioTrack?.write(
                            renderBuffer,
                            0,
                            renderBuffer.size,
                            AudioTrack.WRITE_BLOCKING
                        )
                    }
                }.apply {
                    name = "AudioSamplerRenderThread"
                    priority = Thread.MAX_PRIORITY
                    start()
                }
                Log.i(TAG, "AudioTrack render loop running at $SAMPLE_RATE Hz (stereo float)")
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to start AudioTrack: ${e.message}")
        }
    }

    private fun renderKotlinMix(output: FloatArray, numFrames: Int) {
        Arrays.fill(output, 0f)
        val totalSamples = numFrames * 2

        // Polyphonic mixing: sum active voices
        for (i in 0 until NUM_PADS) {
            kotlinVoices[i].render(output, numFrames)
        }

        // Soft saturation limiter to prevent digital clipping
        for (i in 0 until totalSamples) {
            val s = output[i]
            if (s > 1.0f) {
                output[i] = 1.0f - 1.0f / (s + 0.5f)
            } else if (s < -1.0f) {
                output[i] = -1.0f + 1.0f / (-s + 0.5f)
            }
        }
    }

    fun release() {
        isRunning.set(false)
        try {
            audioThread?.join(500)
        } catch (_: InterruptedException) {}
        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (_: Exception) {}
        audioTrack = null
    }
}
