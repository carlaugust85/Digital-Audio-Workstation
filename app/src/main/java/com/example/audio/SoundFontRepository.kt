package com.example.audio

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.example.model.SoundFontPreset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Configuration mapping for an individual pad to a SoundFont preset and MIDI note.
 */
data class PadSoundFontConfig(
    val padIndex: Int,
    val bank: Int = 128,
    val presetIndex: Int = 0,
    val midiNote: Int = 35 + padIndex,
    val isSoundFontEnabled: Boolean = false
)

/**
 * Observable UI state for SoundFont banks, presets, and pad routing.
 */
data class SoundFontState(
    val isLoaded: Boolean = false,
    val soundFontName: String = "",
    val presets: List<SoundFontPreset> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val padMappings: Map<Int, PadSoundFontConfig> = emptyMap(),
    val soundFontGain: Float = 1.0f
)

/**
 * General MIDI (GM) Standard Percussion Key Map.
 * Provides standard human-readable labels for MIDI note numbers.
 */
object GeneralMidiPercussion {
    val DRUM_NOTE_MAP: Map<Int, String> = mapOf(
        35 to "Acoustic Bass Drum",
        36 to "Bass Drum 1 (Kick)",
        37 to "Side Stick",
        38 to "Acoustic Snare",
        39 to "Hand Clap",
        40 to "Electric Snare",
        41 to "Low Floor Tom",
        42 to "Closed Hi-Hat",
        43 to "High Floor Tom",
        44 to "Pedal Hi-Hat",
        45 to "Low Tom",
        46 to "Open Hi-Hat",
        47 to "Low-Mid Tom",
        48 to "Hi-Mid Tom",
        49 to "Crash Cymbal 1",
        50 to "High Tom",
        51 to "Ride Cymbal 1",
        52 to "Chinese Cymbal",
        53 to "Ride Bell",
        54 to "Tambourine",
        55 to "Splash Cymbal",
        56 to "Cowbell",
        57 to "Crash Cymbal 2",
        58 to "Vibraslap",
        59 to "Ride Cymbal 2",
        60 to "Hi Bongo",
        61 to "Low Bongo",
        62 to "Mute Hi Conga",
        63 to "Open Hi Conga",
        64 to "Low Conga",
        65 to "High Timbale",
        66 to "Low Timbale",
        67 to "High Agogo",
        68 to "Low Agogo",
        69 to "Cabasa",
        70 to "Maracas",
        71 to "Short Whistle",
        72 to "Long Whistle",
        73 to "Short Guiro",
        74 to "Long Guiro",
        75 to "Claves",
        76 to "Hi Wood Block",
        77 to "Low Wood Block",
        78 to "Mute Cuica",
        79 to "Open Cuica",
        80 to "Mute Triangle",
        81 to "Open Triangle"
    )

    fun getLabel(midiNote: Int): String {
        val name = DRUM_NOTE_MAP[midiNote]
        return if (name != null) "$midiNote: $name" else "Note $midiNote"
    }
}

/**
 * SoundFontRepository:
 * Orchestrates SoundFont 2 loading from Android SAF content URIs,
 * maintains observable preset inventories and pad assignment states,
 * and exposes automated GM drum kit binding.
 */
class SoundFontRepository {
    companion object {
        private const val TAG = "SoundFontRepository"
        const val NUM_PADS = 20
    }

    private val initialMappings = (0 until NUM_PADS).associateWith { padIdx ->
        PadSoundFontConfig(
            padIndex = padIdx,
            bank = 128,
            presetIndex = 0,
            midiNote = 35 + padIdx,
            isSoundFontEnabled = false
        )
    }

    private val _soundFontState = MutableStateFlow(
        SoundFontState(
            padMappings = initialMappings,
            soundFontGain = 1.0f
        )
    )
    val soundFontState: StateFlow<SoundFontState> = _soundFontState.asStateFlow()

    /**
     * Loads a .sf2 SoundFont from an Android Storage Access Framework (SAF) URI.
     * Safely accesses the ParcelFileDescriptor without copying large files into memory.
     */
    suspend fun loadSoundFontFromUri(context: Context, uri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        _soundFontState.value = _soundFontState.value.copy(
            isLoading = true,
            errorMessage = null
        )

        // 1. Resolve human-readable file name from ContentResolver
        val fileName = try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIdx >= 0) cursor.getString(nameIdx) else null
                } else null
            } ?: uri.lastPathSegment ?: "Custom_SoundFont.sf2"
        } catch (_: Throwable) {
            uri.lastPathSegment ?: "Custom_SoundFont.sf2"
        }

        try {
            // 2. Open ParcelFileDescriptor
            val pfd = context.contentResolver.openFileDescriptor(uri, "r")
                ?: return@withContext Result.failure(Exception("Unable to open file descriptor for URI: $uri"))

            val length = pfd.statSize
            val fd = pfd.detachFd() // Transfer ownership to C++ engine which will close it

            Log.i(TAG, "Opened SAF file descriptor ($fd), length: $length bytes for $fileName")

            // 3. Dispatch to C++ TinySoundFont loader
            val success = NativeAudioEngine.nativeLoadSoundFontFd(fd, length)
            if (!success) {
                _soundFontState.value = _soundFontState.value.copy(
                    isLoading = false,
                    errorMessage = "TinySoundFont failed to parse $fileName. Verify it is a valid SoundFont 2 (.sf2) file."
                )
                return@withContext Result.failure(Exception("Failed parsing SoundFont"))
            }

            // 4. Retrieve parsed presets list
            val presetsArray = NativeAudioEngine.nativeGetSoundFontPresetList() ?: emptyArray()
            val presetList = presetsArray.toList()

            Log.i(TAG, "Successfully loaded $fileName with ${presetList.size} presets")

            // 5. Re-synchronize existing pad mappings with native engine
            _soundFontState.value.padMappings.values.forEach { config ->
                NativeAudioEngine.nativeMapPadToSoundFont(
                    config.padIndex,
                    config.bank,
                    config.presetIndex,
                    config.midiNote,
                    config.isSoundFontEnabled
                )
            }

            // Set current gain
            NativeAudioEngine.nativeSetSoundFontGain(_soundFontState.value.soundFontGain)

            _soundFontState.value = _soundFontState.value.copy(
                isLoaded = true,
                soundFontName = fileName,
                presets = presetList,
                isLoading = false,
                errorMessage = null
            )

            Result.success(Unit)
        } catch (e: Throwable) {
            Log.e(TAG, "Error importing SoundFont: ${e.message}", e)
            _soundFontState.value = _soundFontState.value.copy(
                isLoading = false,
                errorMessage = "Import error: ${e.localizedMessage ?: e.message}"
            )
            Result.failure(e)
        }
    }

    /**
     * Automatically binds pads 0..19 to Bank 128 percussion keys 35 through 54.
     */
    fun autoMapGeneralMidiDrums() {
        val state = _soundFontState.value
        // Find best drum preset (Bank 128), or first available preset
        val drumPreset = state.presets.find { it.isDrum }
            ?: state.presets.find { it.bank == 128 }
            ?: state.presets.firstOrNull()

        val bank = drumPreset?.bank ?: 128
        val presetIndex = drumPreset?.presetIndex ?: 0

        val updated = state.padMappings.toMutableMap()
        for (padIdx in 0 until NUM_PADS) {
            val midiNote = 35 + padIdx // 35..54
            val newConfig = PadSoundFontConfig(
                padIndex = padIdx,
                bank = bank,
                presetIndex = presetIndex,
                midiNote = midiNote,
                isSoundFontEnabled = true
            )
            updated[padIdx] = newConfig

            NativeAudioEngine.nativeMapPadToSoundFont(
                padIdx,
                bank,
                presetIndex,
                midiNote,
                enabled = true
            )
        }

        _soundFontState.value = state.copy(padMappings = updated)
        Log.i(TAG, "Auto-mapped 20 pads to GM drum keys 35..54 on preset $presetIndex (Bank $bank)")
    }

    /**
     * Updates an individual pad's SoundFont mapping.
     */
    fun mapPad(
        padIndex: Int,
        bank: Int,
        presetIndex: Int,
        midiNote: Int,
        enabled: Boolean
    ) {
        if (padIndex !in 0 until NUM_PADS) return

        val state = _soundFontState.value
        val updated = state.padMappings.toMutableMap()
        val newConfig = PadSoundFontConfig(
            padIndex = padIndex,
            bank = bank,
            presetIndex = presetIndex,
            midiNote = midiNote,
            isSoundFontEnabled = enabled
        )
        updated[padIndex] = newConfig

        NativeAudioEngine.nativeMapPadToSoundFont(
            padIndex,
            bank,
            presetIndex,
            midiNote,
            enabled
        )

        _soundFontState.value = state.copy(padMappings = updated)
    }

    /**
     * Sets master SoundFont output gain.
     */
    fun setSoundFontGain(gain: Float) {
        val clamped = gain.coerceIn(0.0f, 2.0f)
        NativeAudioEngine.nativeSetSoundFontGain(clamped)
        _soundFontState.value = _soundFontState.value.copy(soundFontGain = clamped)
    }

    /**
     * Clears error message from state.
     */
    fun clearError() {
        _soundFontState.value = _soundFontState.value.copy(errorMessage = null)
    }
}
