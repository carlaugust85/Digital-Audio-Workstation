package com.example.audio

import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max

enum class EnvelopeState {
    IDLE,
    ATTACK,
    DECAY,
    SUSTAIN,
    RELEASE
}

/**
 * Kotlin implementation of SamplerVoice matching the C++ SamplerVoice class.
 * Holds a decoded float PCM buffer, playback head position, gain, and an integrated
 * ADSR envelope generator for dynamic volume shaping.
 * Adds samples into the master stereo buffer with linear interpolation for polyphonic mixing.
 */
class SamplerVoice {
    @Volatile
    private var pcmBuffer: FloatArray = FloatArray(0)
    @Volatile
    private var numFrames: Int = 0
    @Volatile
    private var numChannels: Int = 1 // 1 = mono, 2 = stereo
    private var sampleRate: Int = 44100

    @Volatile
    private var playbackHead: Float = 0f
    @Volatile
    private var gain: Float = 1.0f
    private val isPlaying = AtomicBoolean(false)

    @Volatile
    private var startFrame: Int = 0
    @Volatile
    private var endFrame: Int = 0
    private var pitchRatio: Float = 1.0f

    // ADSR Envelope
    @Volatile
    var attackSec: Float = 0.005f
        private set
    @Volatile
    var decaySec: Float = 0.12f
        private set
    @Volatile
    var sustainLevel: Float = 0.75f
        private set
    @Volatile
    var releaseSec: Float = 0.18f
        private set

    @Volatile
    private var envState = EnvelopeState.IDLE
    @Volatile
    private var currentEnvLevel = 0f
    private var attackRate = 0f
    private var decayRate = 0f
    private var releaseRate = 0f

    fun setSampleRate(sr: Int) {
        sampleRate = if (sr > 8000) sr else 44100
        updateEnvelopeRates()
    }

    fun setADSR(attack: Float, decay: Float, sustain: Float, release: Float) {
        attackSec = max(0.001f, attack)
        decaySec = max(0.001f, decay)
        sustainLevel = sustain.coerceIn(0f, 1f)
        releaseSec = max(0.001f, release)
        updateEnvelopeRates()
    }

    private fun updateEnvelopeRates() {
        val sr = sampleRate.toFloat()
        attackRate = if (attackSec > 0.0005f) (1f / (attackSec * sr)) else 1f
        decayRate = if (decaySec > 0.0005f) ((1f - sustainLevel) / (decaySec * sr)) else 1f
        releaseRate = if (releaseSec > 0.0005f) (1f / (releaseSec * sr)) else 1f
    }

    fun setSampleBuffer(data: FloatArray, channels: Int) {
        isPlaying.set(false)
        envState = EnvelopeState.IDLE
        currentEnvLevel = 0f
        numChannels = if (channels == 2) 2 else 1
        numFrames = data.size / numChannels
        startFrame = 0
        endFrame = numFrames
        pcmBuffer = data.copyOf()
        playbackHead = 0f
    }

    fun setTrimBounds(start: Int, end: Int) {
        val safeEnd = end.coerceIn(1, numFrames)
        val safeStart = start.coerceIn(0, safeEnd - 1)
        startFrame = safeStart
        endFrame = safeEnd
    }

    fun trigger(velocity: Float) {
        gain = velocity.coerceIn(0f, 1f)
        playbackHead = startFrame.toFloat()

        // Smooth transition from current envelope level to avoid clicks
        val sr = sampleRate.toFloat()
        val remainingAttack = 1f - currentEnvLevel
        attackRate = if (remainingAttack > 0f && attackSec > 0.0005f) {
            remainingAttack / (attackSec * sr)
        } else {
            1f
        }

        envState = EnvelopeState.ATTACK
        isPlaying.set(true)
    }

    fun release() {
        if (envState != EnvelopeState.IDLE && envState != EnvelopeState.RELEASE) {
            envState = EnvelopeState.RELEASE
            val sr = sampleRate.toFloat()
            releaseRate = if (currentEnvLevel > 0.0001f && releaseSec > 0.0005f) {
                currentEnvLevel / (releaseSec * sr)
            } else {
                1f
            }
        }
    }

    fun stop() {
        envState = EnvelopeState.IDLE
        currentEnvLevel = 0f
        isPlaying.set(false)
    }

    fun isVoicePlaying(): Boolean = isPlaying.get() && envState != EnvelopeState.IDLE

    fun getPlaybackHeadPosition(): Float = playbackHead

    fun getTotalFrames(): Int = numFrames

    fun getBuffer(): FloatArray = pcmBuffer

    fun getEnvelopeState(): EnvelopeState = envState

    fun getEnvelopeLevel(): Float = currentEnvLevel

    /**
     * Polyphonic additive mixing:
     * Adds interpolated samples shaped by the ADSR envelope into the stereo master output buffer.
     * output format: interleaved stereo [L0, R0, L1, R1, ...]
     */
    fun render(output: FloatArray, framesToRender: Int) {
        if (!isPlaying.get() || envState == EnvelopeState.IDLE) return

        val buf = pcmBuffer
        val total = numFrames
        val channels = numChannels
        if (buf.isEmpty() || total <= 0) {
            isPlaying.set(false)
            envState = EnvelopeState.IDLE
            return
        }

        var head = playbackHead
        val baseGain = gain
        val maxEnd = if (endFrame in 1..total) endFrame else total
        val sr = sampleRate.toFloat()

        for (i in 0 until framesToRender) {
            // Step ADSR envelope state
            when (envState) {
                EnvelopeState.ATTACK -> {
                    currentEnvLevel += attackRate
                    if (currentEnvLevel >= 1f) {
                        currentEnvLevel = 1f
                        envState = EnvelopeState.DECAY
                        decayRate = if (decaySec > 0.0005f) ((1f - sustainLevel) / (decaySec * sr)) else 1f
                    }
                }
                EnvelopeState.DECAY -> {
                    currentEnvLevel -= decayRate
                    if (currentEnvLevel <= sustainLevel) {
                        currentEnvLevel = sustainLevel
                        envState = EnvelopeState.SUSTAIN
                    }
                }
                EnvelopeState.SUSTAIN -> {
                    currentEnvLevel = sustainLevel
                }
                EnvelopeState.RELEASE -> {
                    currentEnvLevel -= releaseRate
                    if (currentEnvLevel <= 0.0001f) {
                        currentEnvLevel = 0f
                        envState = EnvelopeState.IDLE
                        isPlaying.set(false)
                        playbackHead = head
                        return
                    }
                }
                EnvelopeState.IDLE -> {
                    currentEnvLevel = 0f
                    isPlaying.set(false)
                    playbackHead = head
                    return
                }
            }

            if (head >= (maxEnd - 1).toFloat()) {
                if (envState != EnvelopeState.RELEASE) {
                    envState = EnvelopeState.RELEASE
                    releaseRate = if (currentEnvLevel > 0f) (currentEnvLevel / (0.01f * sr)) else 1f
                } else {
                    envState = EnvelopeState.IDLE
                    currentEnvLevel = 0f
                    isPlaying.set(false)
                    break
                }
            }

            val effectiveGain = baseGain * currentEnvLevel
            val idx0 = head.toInt()
            val idx1 = idx0 + 1
            val frac = head - idx0

            if (channels == 1) {
                val s0 = buf[idx0]
                val s1 = if (idx1 < maxEnd) buf[idx1] else 0f
                val interp = (s0 + frac * (s1 - s0)) * effectiveGain

                output[i * 2 + 0] += interp
                output[i * 2 + 1] += interp
            } else {
                val offset0 = idx0 * 2
                val offset1 = idx1 * 2

                val s0L = buf[offset0]
                val s0R = buf[offset0 + 1]
                val s1L = if (idx1 < maxEnd) buf[offset1] else 0f
                val s1R = if (idx1 < maxEnd) buf[offset1 + 1] else 0f

                val interpL = (s0L + frac * (s1L - s0L)) * effectiveGain
                val interpR = (s0R + frac * (s1R - s0R)) * effectiveGain

                output[i * 2 + 0] += interpL
                output[i * 2 + 1] += interpR
            }

            head += pitchRatio
        }

        playbackHead = head
    }
}
