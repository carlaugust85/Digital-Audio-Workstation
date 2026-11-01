#include "WavLoader.h"
#include <fstream>
#include <cstring>
#include <cmath>
#include <algorithm>

namespace {

uint16_t readUint16LE(const uint8_t* p) {
    return static_cast<uint16_t>(p[0]) | (static_cast<uint16_t>(p[1]) << 8);
}

uint32_t readUint32LE(const uint8_t* p) {
    return static_cast<uint32_t>(p[0]) |
           (static_cast<uint32_t>(p[1]) << 8) |
           (static_cast<uint32_t>(p[2]) << 16) |
           (static_cast<uint32_t>(p[3]) << 24);
}

int32_t readInt24LE(const uint8_t* p) {
    uint32_t val = static_cast<uint32_t>(p[0]) |
                   (static_cast<uint32_t>(p[1]) << 8) |
                   (static_cast<uint32_t>(p[2]) << 16);
    // Sign-extend 24-bit to 32-bit
    if (val & 0x800000) {
        val |= 0xFF000000;
    }
    return static_cast<int32_t>(val);
}

} // namespace

bool loadWavFile(const std::string& filePath, DecodedAudio& outAudio) {
    std::ifstream file(filePath, std::ios::binary | std::ios::ate);
    if (!file.is_open()) {
        return false;
    }

    std::streamsize fileSize = file.tellg();
    if (fileSize < 44) {
        return false;
    }

    file.seekg(0, std::ios::beg);
    std::vector<uint8_t> buffer(static_cast<size_t>(fileSize));
    if (!file.read(reinterpret_cast<char*>(buffer.data()), fileSize)) {
        return false;
    }

    return loadWavFromMemory(buffer.data(), buffer.size(), outAudio);
}

bool loadWavFromMemory(const uint8_t* data, size_t dataSize, DecodedAudio& outAudio) {
    if (data == nullptr || dataSize < 44) {
        return false;
    }

    // Check RIFF header
    if (std::memcmp(data, "RIFF", 4) != 0 || std::memcmp(data + 8, "WAVE", 4) != 0) {
        return false;
    }

    uint16_t audioFormat = 0;
    uint16_t numChannels = 0;
    uint32_t sampleRate = 0;
    uint16_t bitsPerSample = 0;
    const uint8_t* pcmDataPtr = nullptr;
    uint32_t pcmDataSize = 0;

    size_t offset = 12; // Skip RIFF header and WAVE ID
    while (offset + 8 <= dataSize) {
        const char* chunkId = reinterpret_cast<const char*>(data + offset);
        uint32_t chunkSize = readUint32LE(data + offset + 4);
        offset += 8;

        if (offset + chunkSize > dataSize) {
            // Truncated chunk or file ending
            chunkSize = static_cast<uint32_t>(dataSize - offset);
        }

        if (std::memcmp(chunkId, "fmt ", 4) == 0 && chunkSize >= 16) {
            audioFormat = readUint16LE(data + offset);
            numChannels = readUint16LE(data + offset + 2);
            sampleRate = readUint32LE(data + offset + 4);
            bitsPerSample = readUint16LE(data + offset + 14);
        } else if (std::memcmp(chunkId, "data", 4) == 0) {
            pcmDataPtr = data + offset;
            pcmDataSize = chunkSize;
        }

        // Chunks are padded to even byte boundary
        offset += (chunkSize + 1) & ~1;
    }

    if (pcmDataPtr == nullptr || numChannels == 0 || sampleRate == 0) {
        return false;
    }

    outAudio.sampleRate = static_cast<int>(sampleRate);
    outAudio.numChannels = static_cast<int>(numChannels);
    outAudio.bitsPerSample = static_cast<int>(bitsPerSample);

    int bytesPerSample = bitsPerSample / 8;
    if (bytesPerSample <= 0) return false;

    int totalSamples = static_cast<int>(pcmDataSize / bytesPerSample);
    outAudio.numFrames = totalSamples / numChannels;
    outAudio.pcmBuffer.resize(totalSamples);

    // Decode to normalized float [-1.0f, 1.0f]
    if (audioFormat == 1) {
        // Integer PCM
        if (bitsPerSample == 16) {
            const int16_t* samples = reinterpret_cast<const int16_t*>(pcmDataPtr);
            for (int i = 0; i < totalSamples; ++i) {
                outAudio.pcmBuffer[i] = static_cast<float>(samples[i]) / 32768.0f;
            }
        } else if (bitsPerSample == 24) {
            const uint8_t* p = pcmDataPtr;
            for (int i = 0; i < totalSamples; ++i) {
                int32_t s24 = readInt24LE(p + i * 3);
                outAudio.pcmBuffer[i] = static_cast<float>(s24) / 8388608.0f;
            }
        } else if (bitsPerSample == 8) {
            // 8-bit unsigned PCM: 128 is midpoint
            for (int i = 0; i < totalSamples; ++i) {
                outAudio.pcmBuffer[i] = (static_cast<float>(pcmDataPtr[i]) - 128.0f) / 128.0f;
            }
        } else if (bitsPerSample == 32) {
            const int32_t* samples = reinterpret_cast<const int32_t*>(pcmDataPtr);
            for (int i = 0; i < totalSamples; ++i) {
                outAudio.pcmBuffer[i] = static_cast<float>(samples[i]) / 2147483648.0f;
            }
        } else {
            return false;
        }
    } else if (audioFormat == 3) {
        // IEEE Float
        if (bitsPerSample == 32) {
            const float* samples = reinterpret_cast<const float*>(pcmDataPtr);
            for (int i = 0; i < totalSamples; ++i) {
                outAudio.pcmBuffer[i] = std::clamp(samples[i], -1.0f, 1.0f);
            }
        } else {
            return false;
        }
    } else {
        return false; // Unsupported compression format
    }

    return true;
}

std::vector<float> generatePeakSamples(
    const float* pcmBuffer,
    int numFrames,
    int numChannels,
    int targetPoints
) {
    if (pcmBuffer == nullptr || numFrames <= 0 || numChannels <= 0 || targetPoints <= 0) {
        return std::vector<float>(targetPoints > 0 ? targetPoints : 1, 0.0f);
    }

    std::vector<float> peaks(targetPoints, 0.0f);
    int framesPerBucket = std::max(1, numFrames / targetPoints);
    float maxOverall = 0.001f;

    for (int i = 0; i < targetPoints; ++i) {
        int startFrame = i * framesPerBucket;
        int endFrame = std::min(numFrames, (i + 1) * framesPerBucket);
        float peak = 0.0f;

        for (int frame = startFrame; frame < endFrame; ++frame) {
            for (int ch = 0; ch < numChannels; ++ch) {
                float sampleVal = std::abs(pcmBuffer[frame * numChannels + ch]);
                if (sampleVal > peak) {
                    peak = sampleVal;
                }
            }
        }

        peaks[i] = peak;
        if (peak > maxOverall) {
            maxOverall = peak;
        }
    }

    // Normalize so highest transient peak reaches ~0.95f
    float scale = 0.95f / maxOverall;
    for (int i = 0; i < targetPoints; ++i) {
        peaks[i] = std::clamp(peaks[i] * scale, 0.0f, 1.0f);
    }

    return peaks;
}
