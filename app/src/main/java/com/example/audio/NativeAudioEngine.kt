package com.example.audio

import android.util.Log
import com.example.model.SoundFontPreset

/**
 * Thread-safe JNI bridge where Kotlin passes an integer padIndex and a float velocity,
 * and the C++ engine triggers the corresponding voice without locking.
 * Also supports WAV file decoding, peak generation, ADSR parameters, VoiceManager,
 * and TinySoundFont SF2 playback.
 */
object NativeAudioEngine {
    private const val TAG = "NativeAudioEngine"
    private var isNativeAvailable = false

    init {
        try {
            System.loadLibrary("audioengine")
            isNativeAvailable = true
            Log.i(TAG, "Native audioengine library loaded successfully")
        } catch (e: UnsatisfiedLinkError) {
            Log.w(TAG, "Native audioengine library not found in runtime. Using optimized Kotlin engine: ${e.message}")
            isNativeAvailable = false
        }
    }

    fun isNativeLoaded(): Boolean = isNativeAvailable

    @JvmStatic
    external fun nativeInit(sampleRate: Int): Boolean

    @JvmStatic
    external fun nativeTriggerPad(padIndex: Int, velocity: Float)

    @JvmStatic
    external fun nativeReleasePad(padIndex: Int)

    @JvmStatic
    external fun nativeStopPad(padIndex: Int)

    @JvmStatic
    external fun nativeLoadSample(
        padIndex: Int,
        pcmData: FloatArray,
        channels: Int,
        numFrames: Int
    )

    @JvmStatic
    external fun nativeSetPadTrim(
        padIndex: Int,
        startFrame: Int,
        endFrame: Int
    )

    @JvmStatic
    external fun nativeSetPadADSR(
        padIndex: Int,
        attack: Float,
        decay: Float,
        sustain: Float,
        release: Float
    )

    @JvmStatic
    external fun nativeLoadWavFile(
        filePath: String,
        padIndex: Int
    ): FloatArray?

    @JvmStatic
    external fun nativeLoadWavBytes(
        wavBytes: ByteArray,
        padIndex: Int
    ): FloatArray?

    @JvmStatic
    external fun nativeRender(
        outputBuffer: FloatArray,
        numFrames: Int
    )

    @JvmStatic
    external fun nativeIsPadPlaying(padIndex: Int): Boolean

    @JvmStatic
    external fun nativeGetActiveVoiceCount(): Int

    @JvmStatic
    external fun nativeLoadSoundFontFd(fileDescriptor: Int, length: Long): Boolean

    @JvmStatic
    external fun nativeGetSoundFontPresetList(): Array<SoundFontPreset>?

    @JvmStatic
    external fun nativeMapPadToSoundFont(
        padIndex: Int,
        bank: Int,
        presetIndex: Int,
        midiNote: Int,
        enabled: Boolean
    )

    @JvmStatic
    external fun nativeSetSoundFontGain(gain: Float)

    @JvmStatic
    external fun nativeIsSoundFontLoaded(): Boolean
}
