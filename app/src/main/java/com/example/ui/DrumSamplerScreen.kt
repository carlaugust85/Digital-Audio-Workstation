package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DrumSamplerScreen(
    viewModel: SamplerViewModel = viewModel()
) {
    val pads by viewModel.pads.collectAsState()
    val selectedPadIndex by viewModel.selectedPadIndex.collectAsState()
    val lastTriggeredPad by viewModel.lastTriggeredPad.collectAsState()
    val isNativeEngineActive by viewModel.isNativeEngineActive.collectAsState()
    val activeVoices by viewModel.activeVoices.collectAsState()
    val soundFontState by viewModel.soundFontState.collectAsState()

    var showSoundFontSheet by remember { mutableStateOf(false) }
    val soundFontActivePads = soundFontState.padMappings
        .filterValues { it.isSoundFontEnabled && soundFontState.isLoaded }
        .keys

    val selectedSample = pads.getOrNull(selectedPadIndex)

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = Color(0xFF070B12),
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth().padding(end = 12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.GraphicEq,
                                contentDescription = "Drum Sampler",
                                tint = Color(0xFF38BDF8),
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "POLY SAMPLER",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 1.sp
                                ),
                                color = Color.White
                            )
                        }

                        // Engine Status Badges & SoundFont Studio Trigger
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            // SoundFont SF2 Button
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (soundFontState.isLoaded) Color(0xFF064E3B) else Color(0xFF1E293B),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (soundFontState.isLoaded) Color(0xFF10B981) else Color(0xFF38BDF8)
                                ),
                                modifier = Modifier
                                    .clickable { showSoundFontSheet = true }
                                    .testTag("open_sf2_sheet_button")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Album,
                                        contentDescription = "SoundFont Studio",
                                        tint = if (soundFontState.isLoaded) Color(0xFF34D399) else Color(0xFF38BDF8),
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (soundFontState.isLoaded) "SF2 ACTIVE" else "SF2 STUDIO",
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }

                            // Polyphony Voice Meter
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFF131D2E),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E293B))
                            ) {
                                Text(
                                    text = "VOICES: $activeVoices/32",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (activeVoices > 0) Color(0xFF38BDF8) else Color(0xFF64748B),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }

                            // JNI / Audio Mode Chip
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isNativeEngineActive) Color(0xFF064E3B) else Color(0xFF1E1B4B),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (isNativeEngineActive) Color(0xFF10B981) else Color(0xFF818CF8)
                                )
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .background(
                                                if (isNativeEngineActive) Color(0xFF10B981) else Color(0xFF818CF8),
                                                CircleShape
                                            )
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        text = if (isNativeEngineActive) "C++ JNI" else "STEREO MIX",
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0B111E)
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .navigationBarsPadding()
        ) {
            // Section 1: 4x5 Drum Pad Grid
            Text(
                text = "DRUM PADS (4x5 GRID - 20 POLYPHONIC VOICES)",
                style = MaterialTheme.typography.labelSmall.copy(
                    letterSpacing = 1.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = Color(0xFF64748B),
                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
            )

            DrumPadGrid(
                pads = pads,
                selectedPadIndex = selectedPadIndex,
                lastTriggeredPad = lastTriggeredPad,
                soundFontActivePads = soundFontActivePads,
                onPadSelect = { viewModel.selectPad(it) },
                onPadTrigger = { padIdx, vel -> viewModel.triggerPad(padIdx, vel) },
                onPadRelease = { padIdx -> viewModel.releasePad(padIdx) }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Section 2: Interactive Waveform Viewer & ADSR Envelope Editor
            Text(
                text = "TRANSIENT WAVEFORM & ADSR ENVELOPE SHAPER",
                style = MaterialTheme.typography.labelSmall.copy(
                    letterSpacing = 1.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = Color(0xFF64748B),
                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
            )

            if (selectedSample != null) {
                WaveformViewer(
                    peaks = selectedSample.peaks,
                    transientSpikes = selectedSample.transientSpikeIndices,
                    startFrame = selectedSample.startFrame,
                    endFrame = selectedSample.endFrame,
                    totalFrames = selectedSample.totalFrames,
                    sampleRate = selectedSample.sampleRate,
                    accentColor = selectedSample.color,
                    sampleName = selectedSample.name,
                    attackSec = selectedSample.attackSec,
                    decaySec = selectedSample.decaySec,
                    sustainLevel = selectedSample.sustainLevel,
                    releaseSec = selectedSample.releaseSec,
                    onTrimChanged = { start, end -> viewModel.updateTrim(start, end) },
                    onADSRChanged = { attack, decay, sustain, release ->
                        viewModel.updateADSR(attack, decay, sustain, release)
                    },
                    onAudition = { viewModel.auditionSelected() }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        if (showSoundFontSheet) {
            SoundFontPickerSheet(
                viewModel = viewModel,
                onDismissRequest = { showSoundFontSheet = false }
            )
        }
    }
}
