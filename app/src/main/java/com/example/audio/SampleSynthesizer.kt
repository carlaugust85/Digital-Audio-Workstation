package com.example.audio

import androidx.compose.ui.graphics.Color
import com.example.model.PadCategory
import com.example.model.PadSample
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

object SampleSynthesizer {
    private const val SAMPLE_RATE = 44100

    fun generatePadSamples(): List<PadSample> {
        val samples = mutableListOf<PadSample>()

        // Colors per category
        val kickColor = Color(0xFF6366F1) // Indigo
        val snareColor = Color(0xFFEC4899) // Pink
        val hatColor = Color(0xFFF59E0B) // Amber
        val tomColor = Color(0xFF06B6D4) // Cyan
        val percColor = Color(0xFF10B981) // Emerald

        // Row 1: Kicks (Pads 0-3)
        samples.add(createSample(0, "808 Sub", PadCategory.KICK, kickColor, generate808Kick(0.55f, 55f, 32f)))
        samples.add(createSample(1, "Punch Kick", PadCategory.KICK, kickColor, generatePunchKick(0.35f, 130f, 48f)))
        samples.add(createSample(2, "Club Kick", PadCategory.KICK, kickColor, generatePunchKick(0.28f, 160f, 52f)))
        samples.add(createSample(3, "Sub Drop", PadCategory.KICK, kickColor, generateSubDrop(0.70f, 90f, 28f)))

        // Row 2: Snares & Claps (Pads 4-7)
        samples.add(createSample(4, "Trap Snare", PadCategory.SNARE, snareColor, generateSnare(0.25f, 210f, 0.7f)))
        samples.add(createSample(5, "Tight Snare", PadCategory.SNARE, snareColor, generateSnare(0.18f, 260f, 0.5f)))
        samples.add(createSample(6, "Rimshot", PadCategory.SNARE, snareColor, generateRimshot(0.12f, 440f)))
        samples.add(createSample(7, "Hand Clap", PadCategory.SNARE, snareColor, generateClap(0.30f)))

        // Row 3: Hi-Hats & Shakers (Pads 8-11)
        samples.add(createSample(8, "Closed Hat", PadCategory.HIHAT, hatColor, generateHat(0.08f, isClosed = true)))
        samples.add(createSample(9, "Open Hat", PadCategory.HIHAT, hatColor, generateHat(0.38f, isClosed = false)))
        samples.add(createSample(10, "Pedal Hat", PadCategory.HIHAT, hatColor, generateHat(0.14f, isClosed = true)))
        samples.add(createSample(11, "Shaker", PadCategory.HIHAT, hatColor, generateShaker(0.16f)))

        // Row 4: Toms & Tuned (Pads 12-15)
        samples.add(createSample(12, "Low Tom", PadCategory.TOM, tomColor, generateTom(0.40f, 95f)))
        samples.add(createSample(13, "Mid Tom", PadCategory.TOM, tomColor, generateTom(0.35f, 135f)))
        samples.add(createSample(14, "High Tom", PadCategory.TOM, tomColor, generateTom(0.30f, 185f)))
        samples.add(createSample(15, "Cowbell", PadCategory.TOM, tomColor, generateCowbell(0.30f)))

        // Row 5: Cymbals, Perc & FX (Pads 16-19)
        samples.add(createSample(16, "Crash Cymbal", PadCategory.PERC, percColor, generateCrash(0.65f)))
        samples.add(createSample(17, "Ride Cymbal", PadCategory.PERC, percColor, generateRide(0.45f)))
        samples.add(createSample(18, "Synth Perc", PadCategory.PERC, percColor, generatePerc(0.22f, 380f)))
        samples.add(createSample(19, "Laser Zap", PadCategory.PERC, percColor, generateZap(0.35f)))

        return samples
    }

    private fun createSample(
        index: Int,
        name: String,
        category: PadCategory,
        color: Color,
        pcm: FloatArray
    ): PadSample {
        val peaks = PadSample.extractPeaks(pcm, 1, 512)
        val transients = PadSample.detectTransients(peaks, threshold = 0.20f)
        return PadSample(
            index = index,
            name = name,
            category = category,
            color = color,
            pcmBuffer = pcm,
            channels = 1,
            sampleRate = SAMPLE_RATE,
            peaks = peaks,
            transientSpikeIndices = transients,
            startFrame = 0,
            endFrame = pcm.size
        )
    }

    private fun generate808Kick(durationSec: Float, startFreq: Float, endFreq: Float): FloatArray {
        val numFrames = (durationSec * SAMPLE_RATE).toInt()
        val buffer = FloatArray(numFrames)
        var phase = 0.0

        for (i in 0 until numFrames) {
            val t = i.toFloat() / SAMPLE_RATE
            val progress = t / durationSec
            val freq = startFreq * exp(-progress * 5.0) + endFreq
            val env = exp(-progress * 3.5).toFloat()

            phase += 2.0 * PI * freq / SAMPLE_RATE
            var sample = sin(phase).toFloat()

            // Subtle warm saturation
            sample = (sample * 1.3f).coerceIn(-1f, 1f)
            buffer[i] = sample * env * 0.95f
        }
        return buffer
    }

    private fun generatePunchKick(durationSec: Float, startFreq: Float, endFreq: Float): FloatArray {
        val numFrames = (durationSec * SAMPLE_RATE).toInt()
        val buffer = FloatArray(numFrames)
        var phase = 0.0

        for (i in 0 until numFrames) {
            val t = i.toFloat() / SAMPLE_RATE
            val progress = t / durationSec
            val freq = startFreq * exp(-progress * 18.0) + endFreq
            val env = exp(-progress * 6.0).toFloat()

            phase += 2.0 * PI * freq / SAMPLE_RATE
            var sample = sin(phase).toFloat()

            // Initial click transient
            if (i < 120) {
                val click = (1f - i / 120f) * (Random.nextFloat() * 2f - 1f) * 0.4f
                sample += click
            }

            buffer[i] = (sample * env * 0.95f).coerceIn(-1f, 1f)
        }
        return buffer
    }

    private fun generateSubDrop(durationSec: Float, startFreq: Float, endFreq: Float): FloatArray {
        val numFrames = (durationSec * SAMPLE_RATE).toInt()
        val buffer = FloatArray(numFrames)
        var phase = 0.0

        for (i in 0 until numFrames) {
            val t = i.toFloat() / SAMPLE_RATE
            val progress = t / durationSec
            val freq = startFreq - (startFreq - endFreq) * progress
            val env = (1.0f - progress * progress).coerceAtLeast(0f)

            phase += 2.0 * PI * freq / SAMPLE_RATE
            buffer[i] = (sin(phase) * env * 0.95).toFloat()
        }
        return buffer
    }

    private fun generateSnare(durationSec: Float, baseFreq: Float, noiseMix: Float): FloatArray {
        val numFrames = (durationSec * SAMPLE_RATE).toInt()
        val buffer = FloatArray(numFrames)
        var phase = 0.0

        for (i in 0 until numFrames) {
            val progress = i.toFloat() / numFrames
            val freq = baseFreq * exp(-progress * 8.0)
            val toneEnv = exp(-progress * 10.0).toFloat()
            val noiseEnv = exp(-progress * 4.5).toFloat()

            phase += 2.0 * PI * freq / SAMPLE_RATE
            val tone = sin(phase).toFloat() * toneEnv
            val noise = (Random.nextFloat() * 2f - 1f) * noiseEnv

            val combined = tone * (1f - noiseMix) + noise * noiseMix
            buffer[i] = (combined * 0.90f).coerceIn(-1f, 1f)
        }
        return buffer
    }

    private fun generateRimshot(durationSec: Float, freq: Float): FloatArray {
        val numFrames = (durationSec * SAMPLE_RATE).toInt()
        val buffer = FloatArray(numFrames)
        var phase = 0.0

        for (i in 0 until numFrames) {
            val progress = i.toFloat() / numFrames
            val env = exp(-progress * 25.0).toFloat()
            phase += 2.0 * PI * freq / SAMPLE_RATE
            val tone = sin(phase).toFloat()
            val click = if (i < 60) (Random.nextFloat() * 2f - 1f) else 0f
            buffer[i] = ((tone * 0.6f + click * 0.4f) * env).coerceIn(-1f, 1f)
        }
        return buffer
    }

    private fun generateClap(durationSec: Float): FloatArray {
        val numFrames = (durationSec * SAMPLE_RATE).toInt()
        val buffer = FloatArray(numFrames)

        // 3 pre-bursts + main tail
        val burstInterval = (0.015f * SAMPLE_RATE).toInt()
        for (b in 0 until 3) {
            val start = b * burstInterval
            for (i in 0 until burstInterval) {
                if (start + i < numFrames) {
                    val env = (1f - i.toFloat() / burstInterval)
                    val noise = (Random.nextFloat() * 2f - 1f)
                    buffer[start + i] += noise * env * 0.45f
                }
            }
        }

        val mainStart = 3 * burstInterval
        for (i in mainStart until numFrames) {
            val progress = (i - mainStart).toFloat() / (numFrames - mainStart)
            val env = exp(-progress * 6.0).toFloat()
            val noise = (Random.nextFloat() * 2f - 1f)
            buffer[i] += noise * env * 0.65f
        }

        for (i in buffer.indices) {
            buffer[i] = buffer[i].coerceIn(-1f, 1f)
        }
        return buffer
    }

    private fun generateHat(durationSec: Float, isClosed: Boolean): FloatArray {
        val numFrames = (durationSec * SAMPLE_RATE).toInt()
        val buffer = FloatArray(numFrames)
        val decayRate = if (isClosed) 25.0 else 6.0

        // Metal frequency blend
        val freqs = doubleArrayOf(312.0, 420.0, 545.0, 680.0, 810.0, 1120.0)
        val phases = DoubleArray(freqs.size)

        for (i in 0 until numFrames) {
            val progress = i.toFloat() / numFrames
            val env = exp(-progress * decayRate).toFloat()

            var metal = 0.0
            for (f in freqs.indices) {
                phases[f] += 2.0 * PI * freqs[f] / SAMPLE_RATE
                // square wave harmonics for harsh metallic sound
                metal += if (sin(phases[f]) > 0.0) 1.0 else -1.0
            }
            metal /= freqs.size

            val noise = (Random.nextFloat() * 2f - 1f)
            val combined = (metal * 0.35 + noise * 0.65).toFloat()
            buffer[i] = (combined * env * 0.85f).coerceIn(-1f, 1f)
        }
        return buffer
    }

    private fun generateShaker(durationSec: Float): FloatArray {
        val numFrames = (durationSec * SAMPLE_RATE).toInt()
        val buffer = FloatArray(numFrames)

        for (i in 0 until numFrames) {
            val progress = i.toFloat() / numFrames
            // Attack and decay envelope
            val env = sin(progress * PI).toFloat()
            val noise = (Random.nextFloat() * 2f - 1f)
            buffer[i] = (noise * env * 0.70f).coerceIn(-1f, 1f)
        }
        return buffer
    }

    private fun generateTom(durationSec: Float, baseFreq: Float): FloatArray {
        val numFrames = (durationSec * SAMPLE_RATE).toInt()
        val buffer = FloatArray(numFrames)
        var phase = 0.0

        for (i in 0 until numFrames) {
            val progress = i.toFloat() / numFrames
            val freq = baseFreq * (1.0 + 0.6 * exp(-progress * 12.0))
            val env = exp(-progress * 5.5).toFloat()

            phase += 2.0 * PI * freq / SAMPLE_RATE
            buffer[i] = (sin(phase).toFloat() * env * 0.90f).coerceIn(-1f, 1f)
        }
        return buffer
    }

    private fun generateCowbell(durationSec: Float): FloatArray {
        val numFrames = (durationSec * SAMPLE_RATE).toInt()
        val buffer = FloatArray(numFrames)
        val f1 = 587.0
        val f2 = 845.0
        var p1 = 0.0
        var p2 = 0.0

        for (i in 0 until numFrames) {
            val progress = i.toFloat() / numFrames
            val env = exp(-progress * 8.0).toFloat()

            p1 += 2.0 * PI * f1 / SAMPLE_RATE
            p2 += 2.0 * PI * f2 / SAMPLE_RATE

            val tone1 = if (sin(p1) > 0.0) 0.5f else -0.5f
            val tone2 = if (sin(p2) > 0.0) 0.5f else -0.5f
            val combined = (tone1 + tone2) * 0.7f
            buffer[i] = (combined * env).coerceIn(-1f, 1f)
        }
        return buffer
    }

    private fun generateCrash(durationSec: Float): FloatArray {
        val numFrames = (durationSec * SAMPLE_RATE).toInt()
        val buffer = FloatArray(numFrames)

        for (i in 0 until numFrames) {
            val progress = i.toFloat() / numFrames
            val env = exp(-progress * 3.0).toFloat()
            val noise = (Random.nextFloat() * 2f - 1f)
            buffer[i] = (noise * env * 0.75f).coerceIn(-1f, 1f)
        }
        return buffer
    }

    private fun generateRide(durationSec: Float): FloatArray {
        val numFrames = (durationSec * SAMPLE_RATE).toInt()
        val buffer = FloatArray(numFrames)
        var p1 = 0.0

        for (i in 0 until numFrames) {
            val progress = i.toFloat() / numFrames
            val env = exp(-progress * 4.2).toFloat()
            p1 += 2.0 * PI * 820.0 / SAMPLE_RATE
            val bell = sin(p1).toFloat() * 0.3f
            val noise = (Random.nextFloat() * 2f - 1f) * 0.7f
            buffer[i] = ((bell + noise) * env * 0.75f).coerceIn(-1f, 1f)
        }
        return buffer
    }

    private fun generatePerc(durationSec: Float, freq: Float): FloatArray {
        val numFrames = (durationSec * SAMPLE_RATE).toInt()
        val buffer = FloatArray(numFrames)
        var phase = 0.0

        for (i in 0 until numFrames) {
            val progress = i.toFloat() / numFrames
            val env = exp(-progress * 14.0).toFloat()
            phase += 2.0 * PI * freq * (1.0 + exp(-progress * 20.0)) / SAMPLE_RATE
            buffer[i] = (sin(phase).toFloat() * env * 0.85f).coerceIn(-1f, 1f)
        }
        return buffer
    }

    private fun generateZap(durationSec: Float): FloatArray {
        val numFrames = (durationSec * SAMPLE_RATE).toInt()
        val buffer = FloatArray(numFrames)
        var phase = 0.0

        for (i in 0 until numFrames) {
            val progress = i.toFloat() / numFrames
            val freq = 2400.0 * exp(-progress * 10.0) + 80.0
            val env = (1.0f - progress).coerceAtLeast(0f)
            phase += 2.0 * PI * freq / SAMPLE_RATE
            buffer[i] = (sin(phase).toFloat() * env * 0.85f).coerceIn(-1f, 1f)
        }
        return buffer
    }
}
