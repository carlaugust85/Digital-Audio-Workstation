package com.example

import com.example.audio.EnvelopeState
import com.example.audio.SamplerVoice
import com.example.model.PadSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun testSamplerVoiceADSREnvelope() {
        val voice = SamplerVoice()
        voice.setSampleRate(44100)

        // 1000 frames of test sine data
        val testPcm = FloatArray(44100) { 0.8f }
        voice.setSampleBuffer(testPcm, channels = 1)
        voice.setADSR(attack = 0.01f, decay = 0.02f, sustain = 0.5f, release = 0.02f)

        assertFalse(voice.isVoicePlaying())
        assertEquals(EnvelopeState.IDLE, voice.getEnvelopeState())

        // Trigger voice
        voice.trigger(velocity = 1.0f)
        assertTrue(voice.isVoicePlaying())
        assertEquals(EnvelopeState.ATTACK, voice.getEnvelopeState())

        // Render attack frames
        val output = FloatArray(256 * 2)
        voice.render(output, 256)
        assertTrue(voice.isVoicePlaying())

        // Render until sustain
        val framesForAttackAndDecay = (0.035f * 44100).toInt()
        val discard = FloatArray(framesForAttackAndDecay * 2)
        voice.render(discard, framesForAttackAndDecay)
        assertEquals(EnvelopeState.SUSTAIN, voice.getEnvelopeState())
        assertEquals(0.5f, voice.getEnvelopeLevel(), 0.05f)

        // Trigger release
        voice.release()
        assertEquals(EnvelopeState.RELEASE, voice.getEnvelopeState())

        // Render release frames to completion
        val releaseFrames = (0.025f * 44100).toInt()
        val releaseDiscard = FloatArray(releaseFrames * 2)
        voice.render(releaseDiscard, releaseFrames)
        assertEquals(EnvelopeState.IDLE, voice.getEnvelopeState())
        assertFalse(voice.isVoicePlaying())
    }

    @Test
    fun testPeakExtractionAndTransients() {
        val pcm = FloatArray(1000) { 0.05f }
        // Inject sharp transients at frame 200 and frame 600
        pcm[200] = 0.95f
        pcm[600] = 0.85f

        val peaks = PadSample.extractPeaks(pcm, channels = 1, targetPoints = 100)
        assertEquals(100, peaks.size)
        assertTrue(peaks.any { it > 0.8f })

        val transients = PadSample.detectTransients(peaks, threshold = 0.2f)
        assertTrue("Transient spikes detected", transients.isNotEmpty())
    }
}
