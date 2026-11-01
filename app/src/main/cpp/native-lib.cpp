#include <jni.h>
#include <memory>
#include <vector>
#include <string>
#include <android/log.h>
#include "AudioEngine.h"

#define TAG "DrumSamplerNative"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

static std::unique_ptr<AudioEngine> gAudioEngine;

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_example_audio_NativeAudioEngine_nativeInit(
        JNIEnv* /* env */,
        jobject /* thiz */,
        jint sampleRate) {
    gAudioEngine = std::make_unique<AudioEngine>();
    gAudioEngine->init(sampleRate);
    LOGI("Native AudioEngine initialized with sample rate: %d", sampleRate);
    return JNI_TRUE;
}

/**
 * Thread-safe, lock-free pad trigger.
 * Kotlin passes padIndex and velocity.
 * Triggers the corresponding voice via VoiceManager with ADSR envelope.
 */
JNIEXPORT void JNICALL
Java_com_example_audio_NativeAudioEngine_nativeTriggerPad(
        JNIEnv* /* env */,
        jobject /* thiz */,
        jint padIndex,
        jfloat velocity) {
    if (gAudioEngine) {
        gAudioEngine->triggerPad(padIndex, velocity);
    }
}

JNIEXPORT void JNICALL
Java_com_example_audio_NativeAudioEngine_nativeReleasePad(
        JNIEnv* /* env */,
        jobject /* thiz */,
        jint padIndex) {
    if (gAudioEngine) {
        gAudioEngine->releasePad(padIndex);
    }
}

JNIEXPORT void JNICALL
Java_com_example_audio_NativeAudioEngine_nativeStopPad(
        JNIEnv* /* env */,
        jobject /* thiz */,
        jint padIndex) {
    if (gAudioEngine) {
        gAudioEngine->stopPad(padIndex);
    }
}

JNIEXPORT void JNICALL
Java_com_example_audio_NativeAudioEngine_nativeLoadSample(
        JNIEnv* env,
        jobject /* thiz */,
        jint padIndex,
        jfloatArray pcmData,
        jint channels,
        jint numFrames) {
    if (!gAudioEngine || pcmData == nullptr || numFrames <= 0) return;

    jsize len = env->GetArrayLength(pcmData);
    if (len < numFrames * channels) return;

    jfloat* pcm = env->GetFloatArrayElements(pcmData, nullptr);
    if (pcm != nullptr) {
        gAudioEngine->loadSample(padIndex, pcm, numFrames, channels);
        env->ReleaseFloatArrayElements(pcmData, pcm, JNI_ABORT);
    }
}

JNIEXPORT void JNICALL
Java_com_example_audio_NativeAudioEngine_nativeSetPadTrim(
        JNIEnv* /* env */,
        jobject /* thiz */,
        jint padIndex,
        jint startFrame,
        jint endFrame) {
    if (gAudioEngine) {
        gAudioEngine->setPadTrim(padIndex, startFrame, endFrame);
    }
}

JNIEXPORT void JNICALL
Java_com_example_audio_NativeAudioEngine_nativeSetPadADSR(
        JNIEnv* /* env */,
        jobject /* thiz */,
        jint padIndex,
        jfloat attack,
        jfloat decay,
        jfloat sustain,
        jfloat release) {
    if (gAudioEngine) {
        gAudioEngine->setPadADSR(padIndex, attack, decay, sustain, release);
    }
}

/**
 * Loads a WAV file from the filesystem directly in C++, decodes to float PCM,
 * registers it with VoiceManager, and returns the peak-sampled float values for the waveform viewer.
 */
JNIEXPORT jfloatArray JNICALL
Java_com_example_audio_NativeAudioEngine_nativeLoadWavFile(
        JNIEnv* env,
        jobject /* thiz */,
        jstring jFilePath,
        jint padIndex) {
    if (!gAudioEngine || jFilePath == nullptr) return nullptr;

    const char* cPath = env->GetStringUTFChars(jFilePath, nullptr);
    if (cPath == nullptr) return nullptr;

    std::string filePath(cPath);
    env->ReleaseStringUTFChars(jFilePath, cPath);

    std::vector<float> peaks;
    bool success = gAudioEngine->loadWavToPad(padIndex, filePath, peaks);
    if (!success || peaks.empty()) {
        return nullptr;
    }

    jfloatArray result = env->NewFloatArray(static_cast<jsize>(peaks.size()));
    if (result != nullptr) {
        env->SetFloatArrayRegion(result, 0, static_cast<jsize>(peaks.size()), peaks.data());
    }
    return result;
}

/**
 * Loads a WAV file from an in-memory byte array in C++, decodes to float PCM,
 * registers it with VoiceManager, and returns the peak-sampled float values for the waveform viewer.
 */
JNIEXPORT jfloatArray JNICALL
Java_com_example_audio_NativeAudioEngine_nativeLoadWavBytes(
        JNIEnv* env,
        jobject /* thiz */,
        jbyteArray jWavBytes,
        jint padIndex) {
    if (!gAudioEngine || jWavBytes == nullptr) return nullptr;

    jsize len = env->GetArrayLength(jWavBytes);
    if (len < 44) return nullptr;

    jbyte* bytes = env->GetByteArrayElements(jWavBytes, nullptr);
    if (bytes == nullptr) return nullptr;

    std::vector<float> peaks;
    bool success = gAudioEngine->loadWavFromMemoryToPad(
            padIndex,
            reinterpret_cast<const uint8_t*>(bytes),
            static_cast<size_t>(len),
            peaks
    );
    env->ReleaseByteArrayElements(jWavBytes, bytes, JNI_ABORT);

    if (!success || peaks.empty()) {
        return nullptr;
    }

    jfloatArray result = env->NewFloatArray(static_cast<jsize>(peaks.size()));
    if (result != nullptr) {
        env->SetFloatArrayRegion(result, 0, static_cast<jsize>(peaks.size()), peaks.data());
    }
    return result;
}

JNIEXPORT void JNICALL
Java_com_example_audio_NativeAudioEngine_nativeRender(
        JNIEnv* env,
        jobject /* thiz */,
        jfloatArray outputBuffer,
        jint numFrames) {
    if (!gAudioEngine || outputBuffer == nullptr || numFrames <= 0) return;

    jsize len = env->GetArrayLength(outputBuffer);
    if (len < numFrames * 2) return;

    jfloat* out = env->GetFloatArrayElements(outputBuffer, nullptr);
    if (out != nullptr) {
        gAudioEngine->renderMaster(out, numFrames);
        env->ReleaseFloatArrayElements(outputBuffer, out, 0); // Copy back to Java array
    }
}

JNIEXPORT jboolean JNICALL
Java_com_example_audio_NativeAudioEngine_nativeIsPadPlaying(
        JNIEnv* /* env */,
        jobject /* thiz */,
        jint padIndex) {
    if (gAudioEngine) {
        return gAudioEngine->isPadPlaying(padIndex) ? JNI_TRUE : JNI_FALSE;
    }
    return JNI_FALSE;
}

JNIEXPORT jint JNICALL
Java_com_example_audio_NativeAudioEngine_nativeGetActiveVoiceCount(
        JNIEnv* /* env */,
        jobject /* thiz */) {
    if (gAudioEngine) {
        return gAudioEngine->getActiveVoiceCount();
    }
    return 0;
}

/**
 * Loads a SoundFont 2 (.sf2) file via an open ParcelFileDescriptor from Android SAF.
 * Uses zero-copy mmap or streaming buffer directly into TinySoundFont.
 */
JNIEXPORT jboolean JNICALL
Java_com_example_audio_NativeAudioEngine_nativeLoadSoundFontFd(
        JNIEnv* /* env */,
        jobject /* thiz */,
        jint fileDescriptor,
        jlong length) {
    if (!gAudioEngine) return JNI_FALSE;
    bool success = gAudioEngine->loadSoundFontFd(fileDescriptor, static_cast<int64_t>(length));
    LOGI("nativeLoadSoundFontFd (fd=%d, len=%lld): %s",
         fileDescriptor, (long long)length, success ? "SUCCESS" : "FAILED");
    return success ? JNI_TRUE : JNI_FALSE;
}

/**
 * Returns an array of SoundFontPreset objects parsed from the loaded SoundFont.
 */
JNIEXPORT jobjectArray JNICALL
Java_com_example_audio_NativeAudioEngine_nativeGetSoundFontPresetList(
        JNIEnv* env,
        jobject /* thiz */) {
    if (!gAudioEngine) return nullptr;

    const auto presets = gAudioEngine->getSoundFontPresets();
    jclass presetClass = env->FindClass("com/example/model/SoundFontPreset");
    if (presetClass == nullptr) {
        LOGE("Failed to find SoundFontPreset class com/example/model/SoundFontPreset");
        return nullptr;
    }

    jmethodID constructor = env->GetMethodID(presetClass, "<init>", "(Ljava/lang/String;IIZ)V");
    if (constructor == nullptr) {
        LOGE("Failed to find SoundFontPreset constructor (String, int, int, boolean)");
        return nullptr;
    }

    jobjectArray result = env->NewObjectArray(
            static_cast<jsize>(presets.size()),
            presetClass,
            nullptr
    );
    if (result == nullptr) return nullptr;

    for (size_t i = 0; i < presets.size(); ++i) {
        const auto& p = presets[i];
        jstring jName = env->NewStringUTF(p.name.c_str());
        jobject obj = env->NewObject(
                presetClass,
                constructor,
                jName,
                static_cast<jint>(p.bank),
                static_cast<jint>(p.presetIndex),
                p.isDrum ? JNI_TRUE : JNI_FALSE
        );
        env->SetObjectArrayElement(result, static_cast<jsize>(i), obj);
        env->DeleteLocalRef(jName);
        env->DeleteLocalRef(obj);
    }
    return result;
}

/**
 * Maps a specific pad (0..19) to a SoundFont preset (bank & presetIndex) and MIDI note (0..127).
 * Toggles whether this pad triggers the SoundFont or the WAV SamplerVoice.
 */
JNIEXPORT void JNICALL
Java_com_example_audio_NativeAudioEngine_nativeMapPadToSoundFont(
        JNIEnv* /* env */,
        jobject /* thiz */,
        jint padIndex,
        jint bank,
        jint presetIndex,
        jint midiNote,
        jboolean enabled) {
    if (gAudioEngine) {
        gAudioEngine->mapPadToSoundFont(
                padIndex,
                bank,
                presetIndex,
                midiNote,
                enabled == JNI_TRUE
        );
    }
}

/**
 * Controls the master volume/gain of the SoundFont bus.
 */
JNIEXPORT void JNICALL
Java_com_example_audio_NativeAudioEngine_nativeSetSoundFontGain(
        JNIEnv* /* env */,
        jobject /* thiz */,
        jfloat gain) {
    if (gAudioEngine) {
        gAudioEngine->setSoundFontGain(gain);
    }
}

/**
 * Returns whether a SoundFont is currently loaded and active.
 */
JNIEXPORT jboolean JNICALL
Java_com_example_audio_NativeAudioEngine_nativeIsSoundFontLoaded(
        JNIEnv* /* env */,
        jobject /* thiz */) {
    if (gAudioEngine) {
        return gAudioEngine->isSoundFontLoaded() ? JNI_TRUE : JNI_FALSE;
    }
    return JNI_FALSE;
}

} // extern "C"
