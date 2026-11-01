#include "SoundFontManager.h"

#define TSF_IMPLEMENTATION
#include "tsf.h"

#include <sys/mman.h>
#include <unistd.h>
#include <fcntl.h>
#include <cerrno>
#include <cstring>
#include <algorithm>
#include <chrono>
#include <thread>
#include <android/log.h>

#define TAG "SoundFontManager"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

SoundFontManager::SoundFontManager()
    : mSampleRate(44100),
      mActiveTsf(nullptr),
      mGain(1.0f) {
    // Default GM drum mappings (Pads 0..19 mapped to MIDI notes 35..54, Bank 128)
    for (int i = 0; i < NUM_PADS; ++i) {
        mPadMappings[i] = {
            /* bank = */ 128,
            /* presetIndex = */ 0,
            /* midiNote = */ 35 + i,
            /* soundFontEnabled = */ false
        };
    }
}

SoundFontManager::~SoundFontManager() {
    closeSoundFont();
}

void SoundFontManager::init(int sampleRate) {
    mSampleRate = (sampleRate > 8000) ? sampleRate : 44100;
    tsf* f = mActiveTsf.load(std::memory_order_relaxed);
    if (f != nullptr) {
        tsf_set_output(f, TSF_STEREO_INTERLEAVED, mSampleRate, 0.0f);
    }
}

bool SoundFontManager::loadFromFd(int fileDescriptor, int64_t length) {
    if (fileDescriptor < 0 || length <= 0) {
        LOGE("Invalid file descriptor (%d) or length (%lld)", fileDescriptor, (long long)length);
        return false;
    }

    LOGI("Loading SoundFont from fd %d, size: %lld bytes", fileDescriptor, (long long)length);

    bool success = false;
    // Attempt zero-copy memory mapping
    void* mapped = mmap(nullptr, static_cast<size_t>(length), PROT_READ, MAP_SHARED, fileDescriptor, 0);
    if (mapped != MAP_FAILED) {
        LOGI("SoundFont successfully mmapped at %p", mapped);
        success = loadFromMemory(mapped, static_cast<size_t>(length));
        munmap(mapped, static_cast<size_t>(length));
    } else {
        LOGI("mmap failed (errno: %d, %s). Falling back to direct stream reading.", errno, strerror(errno));
        std::vector<uint8_t> buffer(length);
        lseek(fileDescriptor, 0, SEEK_SET);
        ssize_t bytesRead = 0;
        size_t totalRead = 0;
        while (totalRead < static_cast<size_t>(length)) {
            bytesRead = read(fileDescriptor, buffer.data() + totalRead, length - totalRead);
            if (bytesRead <= 0) break;
            totalRead += static_cast<size_t>(bytesRead);
        }

        if (totalRead > 0) {
            success = loadFromMemory(buffer.data(), totalRead);
        }
    }

    close(fileDescriptor);
    return success;
}

bool SoundFontManager::loadFromMemory(const void* data, size_t size) {
    if (data == nullptr || size < 128) {
        LOGE("Invalid memory buffer for SoundFont parsing");
        return false;
    }

    LOGI("Parsing SoundFont byte buffer (%zu bytes) with TinySoundFont...", size);

    tsf* newTsf = tsf_load_memory(data, static_cast<int>(size));
    if (newTsf == nullptr) {
        LOGE("TinySoundFont failed to parse SoundFont binary structure");
        return false;
    }

    // Configure stereo interleaved output and sample rate
    tsf_set_output(newTsf, TSF_STEREO_INTERLEAVED, mSampleRate, 0.0f);

    // Pre-allocate 64 voices to ensure real-time audio thread is strictly allocation-free
    tsf_set_max_voices(newTsf, 64);

    // Enumerate presets
    int count = tsf_get_presetcount(newTsf);
    std::vector<SoundFontPresetInfo> presets;
    presets.reserve(count);

    for (int i = 0; i < count; ++i) {
        const char* name = tsf_get_presetname(newTsf, i);
        int bank = newTsf->presets[i].bank;
        int presetNum = newTsf->presets[i].preset;
        bool isDrum = (bank == 128);

        SoundFontPresetInfo info;
        info.name = (name != nullptr && name[0] != '\0') ? name : ("Preset " + std::to_string(presetNum));
        info.bank = bank;
        info.presetIndex = presetNum;
        info.isDrum = isDrum;
        presets.push_back(std::move(info));
    }

    LOGI("SoundFont parsed: %d presets enumerated", count);

    {
        std::lock_guard<std::mutex> lock(mLoaderMutex);
        mCachedPresets = std::move(presets);

        // Pre-configure all pad channels to avoid allocations during playback
        applyPadChannels(newTsf);

        // Atomically publish the new SoundFont instance
        tsf* oldTsf = mActiveTsf.exchange(newTsf, std::memory_order_acq_rel);
        if (oldTsf != nullptr) {
            // Grace period to ensure any in-flight render callback concludes
            std::this_thread::sleep_for(std::chrono::milliseconds(25));
            tsf_close(oldTsf);
        }
    }

    return true;
}

void SoundFontManager::closeSoundFont() {
    std::lock_guard<std::mutex> lock(mLoaderMutex);
    tsf* oldTsf = mActiveTsf.exchange(nullptr, std::memory_order_acq_rel);
    if (oldTsf != nullptr) {
        std::this_thread::sleep_for(std::chrono::milliseconds(25));
        tsf_close(oldTsf);
    }
    mCachedPresets.clear();
}

bool SoundFontManager::isLoaded() const {
    return mActiveTsf.load(std::memory_order_relaxed) != nullptr;
}

std::vector<SoundFontPresetInfo> SoundFontManager::getPresetList() const {
    std::lock_guard<std::mutex> lock(mLoaderMutex);
    return mCachedPresets;
}

void SoundFontManager::applyPadChannels(tsf* f) {
    if (f == nullptr) return;

    // Pre-initialize channel table up to NUM_PADS-1
    for (int i = 0; i < NUM_PADS; ++i) {
        const auto& mapping = mPadMappings[i];
        tsf_channel_set_bank_preset(f, i, mapping.bank, mapping.presetIndex);
        tsf_channel_set_pan(f, i, 0.5f);
        tsf_channel_set_volume(f, i, 1.0f);
    }
}

void SoundFontManager::mapPad(int padIndex, int bank, int presetIndex, int midiNote, bool enabled) {
    if (padIndex < 0 || padIndex >= NUM_PADS) return;

    std::lock_guard<std::mutex> lock(mLoaderMutex);
    mPadMappings[padIndex] = {
        bank,
        presetIndex,
        std::clamp(midiNote, 0, 127),
        enabled
    };

    tsf* f = mActiveTsf.load(std::memory_order_acquire);
    if (f != nullptr) {
        tsf_channel_set_bank_preset(f, padIndex, bank, presetIndex);
    }
}

PadMappingConfig SoundFontManager::getPadMapping(int padIndex) const {
    if (padIndex < 0 || padIndex >= NUM_PADS) return {};
    return mPadMappings[padIndex];
}

bool SoundFontManager::isPadSoundFontEnabled(int padIndex) const {
    if (padIndex < 0 || padIndex >= NUM_PADS) return false;
    return mPadMappings[padIndex].soundFontEnabled && (mActiveTsf.load(std::memory_order_relaxed) != nullptr);
}

void SoundFontManager::noteOn(int padIndex, float velocity) {
    if (padIndex < 0 || padIndex >= NUM_PADS) return;
    const auto& mapping = mPadMappings[padIndex];
    if (!mapping.soundFontEnabled) return;

    tsf* f = mActiveTsf.load(std::memory_order_relaxed);
    if (f == nullptr) return;

    // Scale velocity curve with slight exponential touch dynamic
    float clampedVel = std::clamp(velocity, 0.01f, 1.0f);
    tsf_channel_note_on(f, padIndex, mapping.midiNote, clampedVel);
}

void SoundFontManager::noteOff(int padIndex) {
    if (padIndex < 0 || padIndex >= NUM_PADS) return;
    const auto& mapping = mPadMappings[padIndex];
    if (!mapping.soundFontEnabled) return;

    tsf* f = mActiveTsf.load(std::memory_order_relaxed);
    if (f == nullptr) return;

    tsf_channel_note_off(f, padIndex, mapping.midiNote);
}

void SoundFontManager::stopAllNotes() {
    tsf* f = mActiveTsf.load(std::memory_order_relaxed);
    if (f != nullptr) {
        tsf_note_off_all(f);
    }
}

void SoundFontManager::render(float* scratchBuffer, float* masterBuffer, int numFrames) {
    tsf* f = mActiveTsf.load(std::memory_order_acquire);
    if (f == nullptr || scratchBuffer == nullptr || masterBuffer == nullptr || numFrames <= 0) {
        return;
    }

    float gain = mGain.load(std::memory_order_relaxed);
    if (gain <= 0.0001f) return;

    // Render SoundFont output into intermediate scratch buffer (flag_mixing = 0: clear first)
    tsf_render_float(f, scratchBuffer, numFrames, 0);

    // Sum into master stereo buffer with gain
    int totalSamples = numFrames * 2;
    for (int i = 0; i < totalSamples; ++i) {
        masterBuffer[i] += scratchBuffer[i] * gain;
    }
}

void SoundFontManager::setGain(float gain) {
    mGain.store(std::clamp(gain, 0.0f, 2.0f), std::memory_order_relaxed);
}

float SoundFontManager::getGain() const {
    return mGain.load(std::memory_order_relaxed);
}

int SoundFontManager::getActiveVoiceCount() const {
    tsf* f = mActiveTsf.load(std::memory_order_relaxed);
    if (f != nullptr) {
        return tsf_active_voice_count(f);
    }
    return 0;
}
