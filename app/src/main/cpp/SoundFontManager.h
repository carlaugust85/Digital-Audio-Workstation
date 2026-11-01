#ifndef SOUND_FONT_MANAGER_H
#define SOUND_FONT_MANAGER_H

#include <string>
#include <vector>
#include <array>
#include <atomic>
#include <mutex>
#include <cstdint>
#include <cstddef>

// Forward declaration of tsf struct
struct tsf;

/**
 * Metadata for an individual SoundFont preset.
 */
struct SoundFontPresetInfo {
    std::string name;
    int bank = 0;
    int presetIndex = 0;
    bool isDrum = false;
};

/**
 * Mapping configuration for an individual drum pad.
 */
struct PadMappingConfig {
    int bank = 128;
    int presetIndex = 0;
    int midiNote = 36;
    bool soundFontEnabled = false;
};

/**
 * SoundFontManager:
 * High-performance, lock-free SoundFont 2 (SF2) playback engine powered by TinySoundFont (tsf.h).
 * Supports zero-copy mmap loading, preset enumeration, GM drum mapping, and real-time mixing.
 */
class SoundFontManager {
public:
    static constexpr int NUM_PADS = 20;

    SoundFontManager();
    ~SoundFontManager();

    void init(int sampleRate);

    // Thread-safe loading from Android content URIs via file descriptor or memory buffer
    bool loadFromFd(int fileDescriptor, int64_t length);
    bool loadFromMemory(const void* data, size_t size);
    void closeSoundFont();

    bool isLoaded() const;
    std::vector<SoundFontPresetInfo> getPresetList() const;

    // Pad mapping configuration
    void mapPad(int padIndex, int bank, int presetIndex, int midiNote, bool enabled);
    PadMappingConfig getPadMapping(int padIndex) const;
    bool isPadSoundFontEnabled(int padIndex) const;

    // Real-time lock-free note triggering
    void noteOn(int padIndex, float velocity);
    void noteOff(int padIndex);
    void stopAllNotes();

    // Real-time stereo rendering into scratch buffer and additive mixing into master
    void render(float* scratchBuffer, float* masterBuffer, int numFrames);

    // Bus gain control
    void setGain(float gain);
    float getGain() const;

    int getActiveVoiceCount() const;

private:
    int mSampleRate = 44100;
    std::atomic<tsf*> mActiveTsf{nullptr};
    mutable std::mutex mLoaderMutex;

    std::vector<SoundFontPresetInfo> mCachedPresets;
    std::array<PadMappingConfig, NUM_PADS> mPadMappings;
    std::atomic<float> mGain{1.0f};

    void applyPadChannels(tsf* f);
};

#endif // SOUND_FONT_MANAGER_H
