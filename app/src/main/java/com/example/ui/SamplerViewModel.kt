package com.example.ui

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.AudioSamplerEngineManager
import com.example.audio.SoundFontRepository
import com.example.audio.SoundFontState
import com.example.model.PadSample
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class SamplerViewModel : ViewModel() {
    private val engineManager = AudioSamplerEngineManager()
    private val soundFontRepository = SoundFontRepository()

    private val _pads = MutableStateFlow<List<PadSample>>(emptyList())
    val pads: StateFlow<List<PadSample>> = _pads.asStateFlow()

    private val _selectedPadIndex = MutableStateFlow(0)
    val selectedPadIndex: StateFlow<Int> = _selectedPadIndex.asStateFlow()

    private val _lastTriggeredPad = MutableStateFlow<Int?>(null)
    val lastTriggeredPad: StateFlow<Int?> = _lastTriggeredPad.asStateFlow()

    private val _activeVoices = MutableStateFlow(0)
    val activeVoices: StateFlow<Int> = _activeVoices.asStateFlow()

    val isNativeEngineActive: StateFlow<Boolean> = engineManager.isNativeEngineActive
    val soundFontState: StateFlow<SoundFontState> = soundFontRepository.soundFontState

    init {
        _pads.value = engineManager.getSamples()

        // Observe pad trigger events for UI ripples
        viewModelScope.launch {
            engineManager.padTriggerEvents.collect { event ->
                event?.let { (padIndex, _) ->
                    _lastTriggeredPad.value = padIndex
                }
            }
        }

        // Periodic active voice polling for studio UI meters
        viewModelScope.launch {
            while (isActive) {
                _activeVoices.value = engineManager.getActiveVoiceCount()
                delay(80)
            }
        }
    }

    fun triggerPad(padIndex: Int, velocity: Float) {
        engineManager.triggerPad(padIndex, velocity)
    }

    fun releasePad(padIndex: Int) {
        engineManager.releasePad(padIndex)
    }

    fun selectPad(padIndex: Int) {
        if (padIndex in _pads.value.indices) {
            _selectedPadIndex.value = padIndex
        }
    }

    fun updateTrim(startFrame: Int, endFrame: Int) {
        val currentIdx = _selectedPadIndex.value
        engineManager.updateTrim(currentIdx, startFrame, endFrame)

        // Trigger UI recomposition
        val updated = _pads.value.toMutableList()
        val sample = updated.getOrNull(currentIdx) ?: return
        updated[currentIdx] = sample.copy(startFrame = startFrame, endFrame = endFrame)
        _pads.value = updated
    }

    fun updateADSR(attackSec: Float, decaySec: Float, sustainLevel: Float, releaseSec: Float) {
        val currentIdx = _selectedPadIndex.value
        engineManager.updateADSR(currentIdx, attackSec, decaySec, sustainLevel, releaseSec)

        val updated = _pads.value.toMutableList()
        val sample = updated.getOrNull(currentIdx) ?: return
        updated[currentIdx] = sample.copy(
            attackSec = attackSec,
            decaySec = decaySec,
            sustainLevel = sustainLevel,
            releaseSec = releaseSec
        )
        _pads.value = updated
    }

    fun auditionSelected() {
        val idx = _selectedPadIndex.value
        engineManager.triggerPad(idx, 1.0f)
    }

    fun releaseSelected() {
        val idx = _selectedPadIndex.value
        engineManager.releasePad(idx)
    }

    fun resetTrim() {
        val currentIdx = _selectedPadIndex.value
        val sample = _pads.value.getOrNull(currentIdx) ?: return
        updateTrim(0, sample.totalFrames)
    }

    // --- SoundFont 2 (SF2) Actions ---

    fun loadSoundFont(context: Context, uri: Uri) {
        viewModelScope.launch {
            soundFontRepository.loadSoundFontFromUri(context, uri)
        }
    }

    fun autoMapGeneralMidiDrums() {
        soundFontRepository.autoMapGeneralMidiDrums()
    }

    fun mapPadToSoundFont(
        padIndex: Int,
        bank: Int,
        presetIndex: Int,
        midiNote: Int,
        enabled: Boolean
    ) {
        soundFontRepository.mapPad(padIndex, bank, presetIndex, midiNote, enabled)
    }

    fun setSoundFontGain(gain: Float) {
        soundFontRepository.setSoundFontGain(gain)
    }

    fun clearSoundFontError() {
        soundFontRepository.clearError()
    }

    override fun onCleared() {
        super.onCleared()
        engineManager.release()
    }
}
