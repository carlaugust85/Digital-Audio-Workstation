#ifndef SAMPLER_VOICE_H
#define SAMPLER_VOICE_H

#include <vector>
#include <atomic>
#include <algorithm>
#include <cmath>
#include <cstdint>

enum class EnvelopeState {
    IDLE,
    ATTACK,
    DECAY,
    SUSTAIN,
    RELEASE
};

/**
 * SamplerVoice:
 * Lightweight C++ class that holds a decoded float PCM buffer, playback head position,
 * gain, and an integrated ADSR envelope generator for dynamic volume shaping.
 *
 * Implements render(float* output, int numFrames) for polyphonic audio mixing with stereo
 * output capabilities and linear interpolation for seamless sound blending across multiple
 * voices in real-time.
 */
class SamplerVoice {
public:
    SamplerVoice();
    ~SamplerVoice() = default;

    // Load decoded float PCM buffer (interleaved if stereo)
    void setSampleBuffer(const float* data, int numFrames, int numChannels);
    void setSampleBuffer(std::vector<float> pcmData, int numChannels);

    // ADSR configuration
    void setADSR(float attackSec, float decaySec, float sustainLevel, float releaseSec);
    void setSampleRate(int sampleRate);

    // Lock-free trigger, release, and stop methods
    void trigger(float velocity);
    void release();
    void stop();

    // Render audio into stereo master output buffer (accumulating / additive mixing)
    void render(float* output, int numFrames);

    // Edit start and end frame boundaries for precision trimming
    void setTrimBounds(int startFrame, int endFrame);

    // Pitch / playback rate ratio (1.0 = standard speed)
    void setPitchRatio(float ratio) { mPitchRatio = (ratio > 0.01f) ? ratio : 1.0f; }
    float getPitchRatio() const { return mPitchRatio; }

    // Inspect status
    bool isPlaying() const { return mIsPlaying.load(std::memory_order_relaxed); }
    float getPlaybackHead() const { return mPlaybackHead.load(std::memory_order_relaxed); }
    int getNumSampleFrames() const { return mNumFrames; }
    int getNumChannels() const { return mNumChannels; }
    const std::vector<float>& getBuffer() const { return mPcmBuffer; }

    EnvelopeState getEnvelopeState() const { return mEnvState; }
    float getEnvelopeLevel() const { return mCurrentEnvLevel; }
    float getAttackTime() const { return mAttackSec; }
    float getDecayTime() const { return mDecaySec; }
    float getSustainLevel() const { return mSustain; }
    float getReleaseTime() const { return mReleaseSec; }

private:
    std::vector<float> mPcmBuffer;     // Decoded PCM buffer (mono or interleaved stereo)
    int mNumFrames;                    // Total frames in buffer
    int mNumChannels;                  // 1 = mono, 2 = stereo
    int mSampleRate;

    std::atomic<float> mPlaybackHead;  // Current fractional playback head position
    std::atomic<float> mGain;          // Base velocity gain
    std::atomic<bool> mIsPlaying;      // Trigger/playback active flag

    std::atomic<int> mStartFrame;      // Trimmed start frame
    std::atomic<int> mEndFrame;        // Trimmed end frame
    float mPitchRatio;                 // Resampling/pitch ratio for linear interpolation

    // ADSR Envelope Parameters
    float mAttackSec;
    float mDecaySec;
    float mSustain;
    float mReleaseSec;

    // ADSR Runtime State
    EnvelopeState mEnvState;
    float mCurrentEnvLevel;
    float mAttackRate;
    float mDecayRate;
    float mReleaseRate;

    void updateEnvelopeRates();
};

#endif // SAMPLER_VOICE_H
