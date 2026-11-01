#include "VoiceManager.h"
#include <limits>
#include <algorithm>

VoiceManager::VoiceManager(int maxVoices)
    : mMaxVoices(std::max(1, maxVoices)),
      mSampleRate(44100),
      mVoiceSlots(mMaxVoices),
      mNextTimestamp(0) {
    for (auto& slot : mVoiceSlots) {
        slot.voice.setSampleRate(mSampleRate);
    }
}

void VoiceManager::init(int sampleRate) {
    mSampleRate = (sampleRate > 8000) ? sampleRate : 44100;
    for (auto& slot : mVoiceSlots) {
        slot.voice.setSampleRate(mSampleRate);
    }
}

void VoiceManager::registerSample(int sampleId, const float* data, int numFrames, int numChannels) {
    if (data == nullptr || numFrames <= 0) return;

    SampleData& sd = mRegisteredSamples[sampleId];
    sd.numFrames = numFrames;
    sd.numChannels = (numChannels == 2) ? 2 : 1;
    sd.startFrame = 0;
    sd.endFrame = numFrames;

    size_t totalSamples = static_cast<size_t>(numFrames * sd.numChannels);
    sd.pcm.assign(data, data + totalSamples);
}

void VoiceManager::registerSample(int sampleId, std::vector<float> pcmData, int numChannels) {
    SampleData& sd = mRegisteredSamples[sampleId];
    sd.numChannels = (numChannels == 2) ? 2 : 1;
    sd.numFrames = static_cast<int>(pcmData.size()) / sd.numChannels;
    sd.startFrame = 0;
    sd.endFrame = sd.numFrames;
    sd.pcm = std::move(pcmData);
}

void VoiceManager::setSampleTrim(int sampleId, int startFrame, int endFrame) {
    auto it = mRegisteredSamples.find(sampleId);
    if (it != mRegisteredSamples.end()) {
        it->second.startFrame = std::max(0, startFrame);
        it->second.endFrame = std::min(it->second.numFrames, endFrame);
    }
}

void VoiceManager::setSampleADSR(int sampleId, float attack, float decay, float sustain, float release) {
    auto it = mRegisteredSamples.find(sampleId);
    if (it != mRegisteredSamples.end()) {
        it->second.attack = attack;
        it->second.decay = decay;
        it->second.sustain = sustain;
        it->second.release = release;
    }
}

void VoiceManager::setGlobalADSR(float attack, float decay, float sustain, float release) {
    for (auto& pair : mRegisteredSamples) {
        pair.second.attack = attack;
        pair.second.decay = decay;
        pair.second.sustain = sustain;
        pair.second.release = release;
    }
    for (auto& slot : mVoiceSlots) {
        slot.voice.setADSR(attack, decay, sustain, release);
    }
}

int VoiceManager::allocateVoiceSlot(int sampleId, int midiNote) {
    // 1. First priority: find an IDLE voice
    for (int i = 0; i < mMaxVoices; ++i) {
        if (!mVoiceSlots[i].voice.isPlaying() ||
            mVoiceSlots[i].voice.getEnvelopeState() == EnvelopeState::IDLE) {
            return i;
        }
    }

    // 2. Second priority: find voice in RELEASE phase with lowest envelope level
    int bestReleaseIdx = -1;
    float lowestReleaseLevel = 2.0f;

    for (int i = 0; i < mMaxVoices; ++i) {
        if (mVoiceSlots[i].voice.getEnvelopeState() == EnvelopeState::RELEASE) {
            float level = mVoiceSlots[i].voice.getEnvelopeLevel();
            if (level < lowestReleaseLevel) {
                lowestReleaseLevel = level;
                bestReleaseIdx = i;
            }
        }
    }
    if (bestReleaseIdx != -1) {
        return bestReleaseIdx;
    }

    // 3. Third priority: check if same sample or MIDI note is already playing
    for (int i = 0; i < mMaxVoices; ++i) {
        if ((sampleId >= 0 && mVoiceSlots[i].sampleId == sampleId) ||
            (midiNote >= 0 && mVoiceSlots[i].midiNote == midiNote)) {
            return i;
        }
    }

    // 4. Voice Stealing: find the quietest voice or oldest voice (LRU)
    int stealIdx = 0;
    float lowestGain = 2.0f;
    uint64_t oldestTime = std::numeric_limits<uint64_t>::max();

    for (int i = 0; i < mMaxVoices; ++i) {
        float envLevel = mVoiceSlots[i].voice.getEnvelopeLevel();
        if (envLevel < lowestGain) {
            lowestGain = envLevel;
            stealIdx = i;
            oldestTime = mVoiceSlots[i].triggerTimestamp;
        } else if (std::abs(envLevel - lowestGain) < 0.05f && mVoiceSlots[i].triggerTimestamp < oldestTime) {
            // Close gain level: prioritize oldest voice
            stealIdx = i;
            oldestTime = mVoiceSlots[i].triggerTimestamp;
        }
    }

    return stealIdx;
}

int VoiceManager::triggerVoice(int sampleId, float velocity, float pitchRatio, int midiNote) {
    auto it = mRegisteredSamples.find(sampleId);
    if (it == mRegisteredSamples.end() || it->second.pcm.empty()) {
        return -1;
    }

    const SampleData& sample = it->second;
    int slotIndex = allocateVoiceSlot(sampleId, midiNote);
    if (slotIndex < 0 || slotIndex >= mMaxVoices) {
        return -1;
    }

    VoiceSlot& slot = mVoiceSlots[slotIndex];

    // Update voice buffer if needed
    if (slot.sampleId != sampleId) {
        slot.voice.setSampleBuffer(sample.pcm.data(), sample.numFrames, sample.numChannels);
    }

    slot.voice.setTrimBounds(sample.startFrame, sample.endFrame);
    slot.voice.setADSR(sample.attack, sample.decay, sample.sustain, sample.release);
    slot.voice.setPitchRatio(pitchRatio);

    slot.sampleId = sampleId;
    slot.midiNote = midiNote;
    slot.triggerTimestamp = ++mNextTimestamp;

    // Smoothly triggers with envelope transition
    slot.voice.trigger(velocity);
    return slotIndex;
}

void VoiceManager::releaseVoice(int voiceIndex) {
    if (voiceIndex >= 0 && voiceIndex < mMaxVoices) {
        mVoiceSlots[voiceIndex].voice.release();
    }
}

void VoiceManager::releaseSample(int sampleId) {
    for (int i = 0; i < mMaxVoices; ++i) {
        if (mVoiceSlots[i].sampleId == sampleId && mVoiceSlots[i].voice.isPlaying()) {
            mVoiceSlots[i].voice.release();
        }
    }
}

void VoiceManager::noteOff(int midiNote) {
    for (int i = 0; i < mMaxVoices; ++i) {
        if (mVoiceSlots[i].midiNote == midiNote && mVoiceSlots[i].voice.isPlaying()) {
            mVoiceSlots[i].voice.release();
        }
    }
}

void VoiceManager::stopVoice(int voiceIndex) {
    if (voiceIndex >= 0 && voiceIndex < mMaxVoices) {
        mVoiceSlots[voiceIndex].voice.stop();
        mVoiceSlots[voiceIndex].sampleId = -1;
        mVoiceSlots[voiceIndex].midiNote = -1;
    }
}

void VoiceManager::stopAll() {
    for (auto& slot : mVoiceSlots) {
        slot.voice.stop();
        slot.sampleId = -1;
        slot.midiNote = -1;
    }
}

void VoiceManager::render(float* output, int numFrames) {
    for (auto& slot : mVoiceSlots) {
        if (slot.voice.isPlaying()) {
            slot.voice.render(output, numFrames);
        }
    }
}

bool VoiceManager::isSamplePlaying(int sampleId) const {
    for (const auto& slot : mVoiceSlots) {
        if (slot.sampleId == sampleId && slot.voice.isPlaying() &&
            slot.voice.getEnvelopeState() != EnvelopeState::IDLE) {
            return true;
        }
    }
    return false;
}

int VoiceManager::getActiveVoiceCount() const {
    int count = 0;
    for (const auto& slot : mVoiceSlots) {
        if (slot.voice.isPlaying() && slot.voice.getEnvelopeState() != EnvelopeState::IDLE) {
            count++;
        }
    }
    return count;
}
