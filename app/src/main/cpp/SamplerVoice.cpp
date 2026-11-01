#include "SamplerVoice.h"

SamplerVoice::SamplerVoice()
    : mNumFrames(0),
      mNumChannels(1),
      mSampleRate(44100),
      mPlaybackHead(0.0f),
      mGain(1.0f),
      mIsPlaying(false),
      mStartFrame(0),
      mEndFrame(0),
      mPitchRatio(1.0f),
      mAttackSec(0.005f),
      mDecaySec(0.12f),
      mSustain(0.75f),
      mReleaseSec(0.18f),
      mEnvState(EnvelopeState::IDLE),
      mCurrentEnvLevel(0.0f),
      mAttackRate(0.0f),
      mDecayRate(0.0f),
      mReleaseRate(0.0f) {
    updateEnvelopeRates();
}

void SamplerVoice::setSampleRate(int sampleRate) {
    mSampleRate = (sampleRate > 8000) ? sampleRate : 44100;
    updateEnvelopeRates();
}

void SamplerVoice::setADSR(float attackSec, float decaySec, float sustainLevel, float releaseSec) {
    mAttackSec = std::max(0.001f, attackSec);
    mDecaySec = std::max(0.001f, decaySec);
    mSustain = std::clamp(sustainLevel, 0.0f, 1.0f);
    mReleaseSec = std::max(0.001f, releaseSec);
    updateEnvelopeRates();
}

void SamplerVoice::updateEnvelopeRates() {
    float sr = static_cast<float>(mSampleRate);
    mAttackRate = (mAttackSec > 0.0005f) ? (1.0f / (mAttackSec * sr)) : 1.0f;
    mDecayRate = (mDecaySec > 0.0005f) ? ((1.0f - mSustain) / (mDecaySec * sr)) : 1.0f;
    mReleaseRate = (mReleaseSec > 0.0005f) ? (1.0f / (mReleaseSec * sr)) : 1.0f;
}

void SamplerVoice::setSampleBuffer(const float* data, int numFrames, int numChannels) {
    if (data == nullptr || numFrames <= 0 || (numChannels != 1 && numChannels != 2)) {
        return;
    }
    mIsPlaying.store(false, std::memory_order_release);
    mEnvState = EnvelopeState::IDLE;
    mCurrentEnvLevel = 0.0f;
    mNumFrames = numFrames;
    mNumChannels = numChannels;
    mStartFrame.store(0, std::memory_order_relaxed);
    mEndFrame.store(numFrames, std::memory_order_relaxed);

    size_t totalSamples = static_cast<size_t>(numFrames * numChannels);
    mPcmBuffer.assign(data, data + totalSamples);
    mPlaybackHead.store(0.0f, std::memory_order_relaxed);
}

void SamplerVoice::setSampleBuffer(std::vector<float> pcmData, int numChannels) {
    mIsPlaying.store(false, std::memory_order_release);
    mEnvState = EnvelopeState::IDLE;
    mCurrentEnvLevel = 0.0f;
    mNumChannels = (numChannels == 2) ? 2 : 1;
    mNumFrames = static_cast<int>(pcmData.size()) / mNumChannels;
    mStartFrame.store(0, std::memory_order_relaxed);
    mEndFrame.store(mNumFrames, std::memory_order_relaxed);
    mPcmBuffer = std::move(pcmData);
    mPlaybackHead.store(0.0f, std::memory_order_relaxed);
}

void SamplerVoice::setTrimBounds(int startFrame, int endFrame) {
    if (startFrame < 0) startFrame = 0;
    if (endFrame > mNumFrames) endFrame = mNumFrames;
    if (startFrame >= endFrame) startFrame = std::max(0, endFrame - 1);
    mStartFrame.store(startFrame, std::memory_order_relaxed);
    mEndFrame.store(endFrame, std::memory_order_relaxed);
}

void SamplerVoice::trigger(float velocity) {
    float clampedVelocity = std::clamp(velocity, 0.0f, 1.0f);
    mGain.store(clampedVelocity, std::memory_order_relaxed);

    int start = mStartFrame.load(std::memory_order_relaxed);
    mPlaybackHead.store(static_cast<float>(start), std::memory_order_relaxed);

    // Smooth envelope transition: start attacking from current level to prevent clicks
    float sr = static_cast<float>(mSampleRate);
    float remainingAttack = 1.0f - mCurrentEnvLevel;
    if (remainingAttack > 0.0f && mAttackSec > 0.0005f) {
        mAttackRate = remainingAttack / (mAttackSec * sr);
    } else {
        mAttackRate = 1.0f;
    }

    mEnvState = EnvelopeState::ATTACK;
    mIsPlaying.store(true, std::memory_order_release);
}

void SamplerVoice::release() {
    if (mEnvState != EnvelopeState::IDLE && mEnvState != EnvelopeState::RELEASE) {
        mEnvState = EnvelopeState::RELEASE;
        float sr = static_cast<float>(mSampleRate);
        if (mCurrentEnvLevel > 0.0001f && mReleaseSec > 0.0005f) {
            mReleaseRate = mCurrentEnvLevel / (mReleaseSec * sr);
        } else {
            mReleaseRate = 1.0f;
        }
    }
}

void SamplerVoice::stop() {
    mEnvState = EnvelopeState::IDLE;
    mCurrentEnvLevel = 0.0f;
    mIsPlaying.store(false, std::memory_order_release);
}

void SamplerVoice::render(float* output, int numFrames) {
    if (!mIsPlaying.load(std::memory_order_acquire) || mEnvState == EnvelopeState::IDLE) {
        return;
    }
    if (mPcmBuffer.empty() || mNumFrames <= 0) {
        mIsPlaying.store(false, std::memory_order_release);
        mEnvState = EnvelopeState::IDLE;
        return;
    }

    float head = mPlaybackHead.load(std::memory_order_relaxed);
    float baseGain = mGain.load(std::memory_order_relaxed);
    int endFrame = mEndFrame.load(std::memory_order_relaxed);
    if (endFrame <= 0 || endFrame > mNumFrames) {
        endFrame = mNumFrames;
    }

    const float* buffer = mPcmBuffer.data();
    int channels = mNumChannels;
    float sr = static_cast<float>(mSampleRate);

    for (int i = 0; i < numFrames; ++i) {
        // Step the ADSR envelope state machine smoothly
        switch (mEnvState) {
            case EnvelopeState::ATTACK:
                mCurrentEnvLevel += mAttackRate;
                if (mCurrentEnvLevel >= 1.0f) {
                    mCurrentEnvLevel = 1.0f;
                    mEnvState = EnvelopeState::DECAY;
                    mDecayRate = (mDecaySec > 0.0005f)
                        ? ((1.0f - mSustain) / (mDecaySec * sr))
                        : 1.0f;
                }
                break;

            case EnvelopeState::DECAY:
                mCurrentEnvLevel -= mDecayRate;
                if (mCurrentEnvLevel <= mSustain) {
                    mCurrentEnvLevel = mSustain;
                    mEnvState = EnvelopeState::SUSTAIN;
                }
                break;

            case EnvelopeState::SUSTAIN:
                mCurrentEnvLevel = mSustain;
                break;

            case EnvelopeState::RELEASE:
                mCurrentEnvLevel -= mReleaseRate;
                if (mCurrentEnvLevel <= 0.0001f) {
                    mCurrentEnvLevel = 0.0f;
                    mEnvState = EnvelopeState::IDLE;
                    mIsPlaying.store(false, std::memory_order_release);
                    mPlaybackHead.store(head, std::memory_order_relaxed);
                    return;
                }
                break;

            case EnvelopeState::IDLE:
                mCurrentEnvLevel = 0.0f;
                mIsPlaying.store(false, std::memory_order_release);
                mPlaybackHead.store(head, std::memory_order_relaxed);
                return;
        }

        // Check if sample playback head reached trimmed end
        if (head >= static_cast<float>(endFrame - 1)) {
            if (mEnvState != EnvelopeState::RELEASE) {
                // Smooth release when reaching end of sample
                mEnvState = EnvelopeState::RELEASE;
                mReleaseRate = (mCurrentEnvLevel > 0.0f) ? (mCurrentEnvLevel / (0.01f * sr)) : 1.0f;
            } else {
                mEnvState = EnvelopeState::IDLE;
                mCurrentEnvLevel = 0.0f;
                mIsPlaying.store(false, std::memory_order_release);
                break;
            }
        }

        float effectiveGain = baseGain * mCurrentEnvLevel;
        int idx0 = static_cast<int>(head);
        int idx1 = idx0 + 1;
        float frac = head - static_cast<float>(idx0);

        if (channels == 1) {
            // Mono source decoded PCM -> stereo output accumulation
            float s0 = buffer[idx0];
            float s1 = (idx1 < endFrame) ? buffer[idx1] : 0.0f;
            float interpolated = (s0 + frac * (s1 - s0)) * effectiveGain;

            output[i * 2 + 0] += interpolated; // Left
            output[i * 2 + 1] += interpolated; // Right
        } else {
            // Interleaved stereo source decoded PCM -> stereo output accumulation
            int offset0 = idx0 * 2;
            int offset1 = idx1 * 2;

            float s0L = buffer[offset0];
            float s0R = buffer[offset0 + 1];
            float s1L = (idx1 < endFrame) ? buffer[offset1] : 0.0f;
            float s1R = (idx1 < endFrame) ? buffer[offset1 + 1] : 0.0f;

            float interpL = (s0L + frac * (s1L - s0L)) * effectiveGain;
            float interpR = (s0R + frac * (s1R - s0R)) * effectiveGain;

            output[i * 2 + 0] += interpL; // Left
            output[i * 2 + 1] += interpR; // Right
        }

        head += mPitchRatio;
    }

    mPlaybackHead.store(head, std::memory_order_relaxed);
}
