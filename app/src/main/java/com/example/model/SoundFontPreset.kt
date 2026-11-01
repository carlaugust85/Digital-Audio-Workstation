package com.example.model

/**
 * Data representation of a SoundFont 2 preset enumerated via TinySoundFont.
 */
data class SoundFontPreset(
    val name: String,
    val bank: Int,
    val presetIndex: Int,
    val isDrum: Boolean
) {
    val displayName: String
        get() = if (isDrum) "🥁 $name [Percussion]" else "$name (Bank $bank, #$presetIndex)"
}
