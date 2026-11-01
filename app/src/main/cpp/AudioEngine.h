#ifndef AUDIO_ENGINE_H
#define AUDIO_ENGINE_H

#include "VoiceManager.h"
#include "WavLoader.h"
#include "SoundFontManager.h"
#include <string>
#include <vector>

/**
 * AudioEngine:
 * Polyphonic real-time audio mixing engine.
 * Powered by VoiceManager for dynamic WAV voice allocation & ADSR envelopes,
 * and SoundFontManager for multi-velocity TinySoundFont 2 instrument & drum playback.
 */
class AudioEngine {
public:
    static constexpr int NUM_PADS = 20;

    AudioEngine();
    ~AudioEngine() = default;

    void init(int sampleRate);

    // Dynamic voice triggering and releasing (routed to SoundFontManager or VoiceManager)
    void triggerPad(int padIndex, float velocity);
    void releasePad(int padIndex);
    void stopPad(int padIndex);

    // Sample registration (WAV SamplerVoice)
    void loadSample(int padIndex, const float* data, int numFrames, int numChannels);
    void setPadTrim(int padIndex, int startFrame, int endFrame);
    void setPadADSR(int padIndex, float attack, float decay, float sustain, float release);

    // Load a WAV file from path into pad, generating peak-sampled values for waveform viewer
    bool loadWavToPad(int padIndex, const std::string& filePath, std::vector<float>& outPeaks);
    bool loadWavFromMemoryToPad(int padIndex, const uint8_t* data, size_t size, std::vector<float>& outPeaks);

    // SoundFont 2 (SF2) management
    bool loadSoundFontFd(int fileDescriptor, int64_t length);
    bool loadSoundFontMemory(const uint8_t* data, size_t size);
    std::vector<SoundFontPresetInfo> getSoundFontPresets() const;
    void mapPadToSoundFont(int padIndex, int bank, int presetIndex, int midiNote, bool enabled);
    void setSoundFontGain(float gain);
    float getSoundFontGain() const;
    bool isSoundFontLoaded() const;
    bool isPadSoundFontEnabled(int padIndex) const;

    // Polyphonic stereo rendering with additive mixing & soft limiter
    void renderMaster(float* output, int numFrames);

    bool isPadPlaying(int padIndex) const;
    int getActiveVoiceCount() const;
    int getSampleRate() const { return mSampleRate; }

private:
    int mSampleRate;
    VoiceManager mVoiceManager;
    SoundFontManager mSoundFontManager;
    std::vector<float> mScratchBuffer;
};

#endif // AUDIO_ENGINE_H
