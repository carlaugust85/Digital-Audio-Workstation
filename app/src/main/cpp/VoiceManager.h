#ifndef VOICE_MANAGER_H
#define VOICE_MANAGER_H

#include "SamplerVoice.h"
#include <array>
#include <vector>
#include <cstdint>
#include <atomic>
#include <memory>
#include <unordered_map>

/**
 * VoiceSlot:
 * Tracks metadata for a managed SamplerVoice in the pool.
 */
struct VoiceSlot {
    SamplerVoice voice;
    int sampleId = -1;
    int midiNote = -1;
    uint64_t triggerTimestamp = 0;
};

/**
 * VoiceManager:
 * Manages a pre-allocated pool of polyphonic SamplerVoice instances for real-time audio.
 * Handles voice allocation, voice stealing (e.g. lowest gain / LRU), MIDI noteOn/noteOff,
 * and polyphonic stereo mixing without audio-thread dynamic memory allocation.
 */
class VoiceManager {
public:
    static constexpr int DEFAULT_MAX_VOICES = 32;

    explicit VoiceManager(int maxVoices = DEFAULT_MAX_VOICES);
    ~VoiceManager() = default;

    void init(int sampleRate);

    // Register a sample buffer so voices can be allocated dynamically
    void registerSample(int sampleId, const float* data, int numFrames, int numChannels);
    void registerSample(int sampleId, std::vector<float> pcmData, int numChannels);
    void setSampleTrim(int sampleId, int startFrame, int endFrame);
    void setSampleADSR(int sampleId, float attack, float decay, float sustain, float release);

    // Dynamic Voice Allocation and Triggering
    // Returns the voice index allocated, or -1 on failure
    int triggerVoice(int sampleId, float velocity, float pitchRatio = 1.0f, int midiNote = -1);

    // Stop or release voice
    void releaseVoice(int voiceIndex);
    void releaseSample(int sampleId);
    void noteOff(int midiNote);
    void stopVoice(int voiceIndex);
    void stopAll();

    // Render all active voices into interleaved stereo output buffer
    void render(float* output, int numFrames);

    // Voice & ADSR configuration
    void setGlobalADSR(float attack, float decay, float sustain, float release);
    bool isSamplePlaying(int sampleId) const;
    int getActiveVoiceCount() const;
    int getMaxVoices() const { return mMaxVoices; }
    int getSampleRate() const { return mSampleRate; }

private:
    int mMaxVoices;
    int mSampleRate;
    std::vector<VoiceSlot> mVoiceSlots;
    uint64_t mNextTimestamp;

    struct SampleData {
        std::vector<float> pcm;
        int numFrames = 0;
        int numChannels = 1;
        int startFrame = 0;
        int endFrame = 0;
        float attack = 0.005f;
        float decay = 0.12f;
        float sustain = 0.75f;
        float release = 0.18f;
    };

    std::unordered_map<int, SampleData> mRegisteredSamples;

    // Voice allocation & stealing algorithm
    int allocateVoiceSlot(int sampleId, int midiNote);
};

#endif // VOICE_MANAGER_H
