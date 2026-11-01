package com.example.model

import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.math.max

data class PadSample(
    val index: Int,
    val name: String,
    val category: PadCategory,
    val color: Color,
    val pcmBuffer: FloatArray,
    val channels: Int = 1,
    val sampleRate: Int = 44100,
    val peaks: FloatArray,
    val transientSpikeIndices: List<Int>,
    var startFrame: Int = 0,
    var endFrame: Int = pcmBuffer.size / channels,
    var attackSec: Float = 0.005f,
    var decaySec: Float = 0.12f,
    var sustainLevel: Float = 0.75f,
    var releaseSec: Float = 0.18f
) {
    val durationMs: Float
        get() = (totalFrames.toFloat() / sampleRate) * 1000f

    val totalFrames: Int
        get() = pcmBuffer.size / channels

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PadSample) return false
        return index == other.index &&
                name == other.name &&
                startFrame == other.startFrame &&
                endFrame == other.endFrame &&
                attackSec == other.attackSec &&
                decaySec == other.decaySec &&
                sustainLevel == other.sustainLevel &&
                releaseSec == other.releaseSec
    }

    override fun hashCode(): Int {
        var result = index
        result = 31 * result + name.hashCode()
        result = 31 * result + startFrame
        result = 31 * result + endFrame
        result = 31 * result + attackSec.hashCode()
        result = 31 * result + decaySec.hashCode()
        result = 31 * result + sustainLevel.hashCode()
        result = 31 * result + releaseSec.hashCode()
        return result
    }

    companion object {
        /**
         * Extracts peak-sampled floats from decoded PCM buffer for rendering waveform.
         * Creates normalized values in range [0f, 1f].
         */
        fun extractPeaks(pcm: FloatArray, channels: Int, targetPoints: Int = 512): FloatArray {
            val totalFrames = pcm.size / channels
            if (totalFrames <= 0) return FloatArray(targetPoints)

            val peaks = FloatArray(targetPoints)
            val framesPerBucket = max(1, totalFrames / targetPoints)

            var maxOverall = 0.001f
            for (i in 0 until targetPoints) {
                val start = i * framesPerBucket
                val end = minOf(totalFrames, (i + 1) * framesPerBucket)
                var peak = 0f

                for (frame in start until end) {
                    val sample = abs(pcm[frame * channels])
                    if (sample > peak) peak = sample
                }
                peaks[i] = peak
                if (peak > maxOverall) maxOverall = peak
            }

            // Normalize so highest peak reaches near 0.95
            val scale = 0.95f / maxOverall
            for (i in peaks.indices) {
                peaks[i] = (peaks[i] * scale).coerceIn(0f, 1f)
            }

            return peaks
        }

        /**
         * Identifies transient spike indices in the peak array based on sharp amplitude delta.
         */
        fun detectTransients(peaks: FloatArray, threshold: Float = 0.25f): List<Int> {
            val transients = mutableListOf<Int>()
            for (i in 1 until peaks.size - 1) {
                val delta = peaks[i] - peaks[i - 1]
                if (delta > threshold && peaks[i] > peaks[i + 1]) {
                    transients.add(i)
                }
            }
            if (transients.isEmpty() && peaks.isNotEmpty()) {
                var maxIdx = 0
                var maxVal = 0f
                for (i in peaks.indices) {
                    if (peaks[i] > maxVal) {
                        maxVal = peaks[i]
                        maxIdx = i
                    }
                }
                transients.add(maxIdx)
            }
            return transients
        }
    }
}

enum class PadCategory(val label: String) {
    KICK("Kicks"),
    SNARE("Snares"),
    HIHAT("Hi-Hats"),
    TOM("Toms"),
    PERC("Perc & FX")
}
