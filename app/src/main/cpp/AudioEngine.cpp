#include "AudioEngine.h"
#include <cmath>
#include <algorithm>

AudioEngine::AudioEngine()
    : mSampleRate(44100),
      mVoiceManager(32) {
    mScratchBuffer.resize(4096 * 2, 0.0f);
}

void AudioEngine::init(int sampleRate) {
    mSampleRate = (sampleRate > 8000) ? sampleRate : 44100;
    mVoiceManager.init(mSampleRate);
    mSoundFontManager.init(mSampleRate);
    mScratchBuffer.resize(4096 * 2, 0.0f);
}

void AudioEngine::triggerPad(int padIndex, float velocity) {
    if (padIndex >= 0 && padIndex < NUM_PADS) {
        if (mSoundFontManager.isPadSoundFontEnabled(padIndex)) {
            mSoundFontManager.noteOn(padIndex, velocity);
        } else {
            mVoiceManager.triggerVoice(padIndex, velocity);
        }
    }
}

void AudioEngine::releasePad(int padIndex) {
    if (padIndex >= 0 && padIndex < NUM_PADS) {
        if (mSoundFontManager.isPadSoundFontEnabled(padIndex)) {
            mSoundFontManager.noteOff(padIndex);
        } else {
            mVoiceManager.releaseSample(padIndex);
        }
    }
}

void AudioEngine::stopPad(int padIndex) {
    if (padIndex >= 0 && padIndex < NUM_PADS) {
        if (mSoundFontManager.isPadSoundFontEnabled(padIndex)) {
            mSoundFontManager.noteOff(padIndex);
        } else {
            mVoiceManager.releaseSample(padIndex);
        }
    }
}

void AudioEngine::loadSample(int padIndex, const float* data, int numFrames, int numChannels) {
    if (padIndex >= 0 && padIndex < NUM_PADS && data != nullptr && numFrames > 0) {
        mVoiceManager.registerSample(padIndex, data, numFrames, numChannels);
    }
}

void AudioEngine::setPadTrim(int padIndex, int startFrame, int endFrame) {
    if (padIndex >= 0 && padIndex < NUM_PADS) {
        mVoiceManager.setSampleTrim(padIndex, startFrame, endFrame);
    }
}

void AudioEngine::setPadADSR(int padIndex, float attack, float decay, float sustain, float release) {
    if (padIndex >= 0 && padIndex < NUM_PADS) {
        mVoiceManager.setSampleADSR(padIndex, attack, decay, sustain, release);
    }
}

bool AudioEngine::loadWavToPad(int padIndex, const std::string& filePath, std::vector<float>& outPeaks) {
    if (padIndex < 0 || padIndex >= NUM_PADS) return false;

    DecodedAudio audio;
    if (!loadWavFile(filePath, audio)) {
        return false;
    }

    mVoiceManager.registerSample(padIndex, audio.pcmBuffer.data(), audio.numFrames, audio.numChannels);
    outPeaks = generatePeakSamples(audio.pcmBuffer.data(), audio.numFrames, audio.numChannels, 512);
    return true;
}

bool AudioEngine::loadWavFromMemoryToPad(int padIndex, const uint8_t* data, size_t size, std::vector<float>& outPeaks) {
    if (padIndex < 0 || padIndex >= NUM_PADS || data == nullptr || size < 44) return false;

    DecodedAudio audio;
    if (!loadWavFromMemory(data, size, audio)) {
        return false;
    }

    mVoiceManager.registerSample(padIndex, audio.pcmBuffer.data(), audio.numFrames, audio.numChannels);
    outPeaks = generatePeakSamples(audio.pcmBuffer.data(), audio.numFrames, audio.numChannels, 512);
    return true;
}

bool AudioEngine::isPadPlaying(int padIndex) const {
    if (padIndex >= 0 && padIndex < NUM_PADS) {
        if (mSoundFontManager.isPadSoundFontEnabled(padIndex)) {
            return mSoundFontManager.getActiveVoiceCount() > 0;
        }
        return mVoiceManager.isSamplePlaying(padIndex);
    }
    return false;
}

bool AudioEngine::loadSoundFontFd(int fileDescriptor, int64_t length) {
    return mSoundFontManager.loadFromFd(fileDescriptor, length);
}

bool AudioEngine::loadSoundFontMemory(const uint8_t* data, size_t size) {
    return mSoundFontManager.loadFromMemory(data, size);
}

std::vector<SoundFontPresetInfo> AudioEngine::getSoundFontPresets() const {
    return mSoundFontManager.getPresetList();
}

void AudioEngine::mapPadToSoundFont(int padIndex, int bank, int presetIndex, int midiNote, bool enabled) {
    mSoundFontManager.mapPad(padIndex, bank, presetIndex, midiNote, enabled);
}

void AudioEngine::setSoundFontGain(float gain) {
    mSoundFontManager.setGain(gain);
}

float AudioEngine::getSoundFontGain() const {
    return mSoundFontManager.getGain();
}

bool AudioEngine::isSoundFontLoaded() const {
    return mSoundFontManager.isLoaded();
}

bool AudioEngine::isPadSoundFontEnabled(int padIndex) const {
    return mSoundFontManager.isPadSoundFontEnabled(padIndex);
}

int AudioEngine::getActiveVoiceCount() const {
    return mVoiceManager.getActiveVoiceCount() + mSoundFontManager.getActiveVoiceCount();
}

void AudioEngine::renderMaster(float* output, int numFrames) {
    if (output == nullptr || numFrames <= 0) return;

    // Clear master stereo output buffer
    std::fill(output, output + (numFrames * 2), 0.0f);

    // 1. Render and sum all active PCM WAV voices managed by VoiceManager
    mVoiceManager.render(output, numFrames);

    // 2. Render SoundFont voices into scratch buffer and sum into master with additive mixing
    if (mScratchBuffer.size() < static_cast<size_t>(numFrames * 2)) {
        mScratchBuffer.resize(numFrames * 2, 0.0f);
    }
    mSoundFontManager.render(mScratchBuffer.data(), output, numFrames);

    // 3. Soft limiter (tanh saturation) on master stereo bus to prevent harsh digital clipping
    int totalSamples = numFrames * 2;
    for (int i = 0; i < totalSamples; ++i) {
        float sample = output[i];
        if (sample > 0.95f || sample < -0.95f) {
            output[i] = std::tanh(sample * 0.92f);
        }
    }
}
