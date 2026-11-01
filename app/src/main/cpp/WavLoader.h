#ifndef WAV_LOADER_H
#define WAV_LOADER_H

#include <string>
#include <vector>
#include <cstdint>

/**
 * Audio data decoded from a standard WAV file.
 */
struct DecodedAudio {
    std::vector<float> pcmBuffer; // Interleaved float samples normalized to [-1.0f, 1.0f]
    int sampleRate = 0;
    int numChannels = 0;          // 1 = mono, 2 = stereo
    int numFrames = 0;            // Number of frames (pcmBuffer.size() / numChannels)
    int bitsPerSample = 0;
};

/**
 * Loads and decodes a WAV audio file from disk into a float PCM buffer.
 * Supports 8-bit unsigned, 16-bit signed, 24-bit signed, 32-bit signed integer,
 * and 32-bit IEEE float WAV files (mono and stereo).
 */
bool loadWavFile(const std::string& filePath, DecodedAudio& outAudio);

/**
 * Loads and decodes a WAV file from an in-memory byte buffer.
 */
bool loadWavFromMemory(const uint8_t* data, size_t dataSize, DecodedAudio& outAudio);

/**
 * Generates an array of peak-sampled float values from a decoded PCM buffer.
 * Computes maximum absolute value within small time windows / buckets.
 * Used for rendering in the interactive waveform viewer.
 */
std::vector<float> generatePeakSamples(
    const float* pcmBuffer,
    int numFrames,
    int numChannels,
    int targetPoints = 512
);

#endif // WAV_LOADER_H
