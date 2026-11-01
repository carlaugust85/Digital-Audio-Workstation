cmake_minimum_required(VERSION 3.22.1)

project("drumsampler")

add_library(
    audioengine
    SHARED
    SamplerVoice.cpp
    WavLoader.cpp
    VoiceManager.cpp
    SoundFontManager.cpp
    AudioEngine.cpp
    native-lib.cpp
)

find_library(
    log-lib
    log
)

target_link_libraries(
    audioengine
    ${log-lib}
)
