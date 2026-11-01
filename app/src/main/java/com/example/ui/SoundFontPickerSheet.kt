package com.example.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.GeneralMidiPercussion
import com.example.audio.PadSoundFontConfig
import com.example.audio.SoundFontState
import com.example.model.SoundFontPreset

/**
 * SoundFontPickerSheet:
 * Modern modal bottom sheet for SoundFont 2 (.sf2) management.
 * Provides SAF file import, metadata display, preset enumeration with Bank 128 badges,
 * pad-to-MIDI note mapping inspector, and Auto-Map General MIDI quick actions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SoundFontPickerSheet(
    viewModel: SamplerViewModel,
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current
    val soundFontState by viewModel.soundFontState.collectAsState()
    val selectedPadIndex by viewModel.selectedPadIndex.collectAsState()
    val pads by viewModel.pads.collectAsState()

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Storage Access Framework document picker for .sf2 soundfonts
    val sf2PickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            viewModel.loadSoundFont(context, it)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = Color(0xFF0B111E),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .width(48.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF334155))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .verticalScroll(rememberScrollState())
                .testTag("soundfont_picker_sheet")
        ) {
            // Sheet Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF1E293B)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Album,
                            contentDescription = "SoundFont Studio",
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "SOUNDFONT 2 STUDIO",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Black,
                                letterSpacing = 0.5.sp
                            ),
                            color = Color.White
                        )
                        Text(
                            text = "TinySoundFont C++ Multi-Velocity Engine",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF64748B)
                        )
                    }
                }

                IconButton(
                    onClick = onDismissRequest,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("close_sf2_sheet_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color(0xFF94A3B8)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Error Banner
            if (soundFontState.errorMessage != null) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFF450A0A),
                    border = BorderStroke(1.dp, Color(0xFFEF4444)),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = "Error",
                            tint = Color(0xFFFCA5A5),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = soundFontState.errorMessage ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFFEE2E2),
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { viewModel.clearSoundFontError() },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Dismiss",
                                tint = Color(0xFFFCA5A5),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // Loading Indicator
            if (soundFontState.isLoading) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF131D2E),
                    border = BorderStroke(1.dp, Color(0xFF0284C7)),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                            color = Color(0xFF38BDF8),
                            trackColor = Color(0xFF1E293B)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Parsing SoundFont banks & sample layers in C++...",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF38BDF8),
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            // Section 1: Import Button & SoundFont Metadata Card
            SoundFontImportCard(
                soundFontState = soundFontState,
                onImportClick = {
                    // Open document picker for sf2 files
                    sf2PickerLauncher.launch(arrayOf("*/*", "application/octet-stream"))
                },
                onGainChange = { viewModel.setSoundFontGain(it) }
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Section 2: Auto-Map GM Drum Kit Quick Action (Pads 0-19 -> MIDI 35-54)
            if (soundFontState.isLoaded) {
                AutoMapGmCard(
                    soundFontState = soundFontState,
                    onAutoMapClick = { viewModel.autoMapGeneralMidiDrums() }
                )

                Spacer(modifier = Modifier.height(14.dp))
            }

            // Section 3: Pad Assignment Matrix (4x5 grid)
            Text(
                text = "PAD ROUTING MATRIX (SELECT PAD TO CONFIGURE)",
                style = MaterialTheme.typography.labelSmall.copy(
                    letterSpacing = 1.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = Color(0xFF64748B),
                modifier = Modifier.padding(start = 2.dp, bottom = 6.dp)
            )

            PadRoutingMatrix(
                soundFontState = soundFontState,
                selectedPadIndex = selectedPadIndex,
                onSelectPad = { viewModel.selectPad(it) }
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Section 4: Selected Pad Inspector & SoundFont Mapping Details
            PadInspectorCard(
                padIndex = selectedPadIndex,
                padName = pads.getOrNull(selectedPadIndex)?.name ?: "Pad ${selectedPadIndex + 1}",
                soundFontState = soundFontState,
                onToggleSoundFont = { enabled ->
                    val cfg = soundFontState.padMappings[selectedPadIndex]
                    val bank = cfg?.bank ?: 128
                    val preset = cfg?.presetIndex ?: 0
                    val note = cfg?.midiNote ?: (35 + selectedPadIndex)
                    viewModel.mapPadToSoundFont(selectedPadIndex, bank, preset, note, enabled)
                },
                onNoteChange = { newNote ->
                    val cfg = soundFontState.padMappings[selectedPadIndex]
                    val bank = cfg?.bank ?: 128
                    val preset = cfg?.presetIndex ?: 0
                    val enabled = cfg?.isSoundFontEnabled ?: true
                    viewModel.mapPadToSoundFont(selectedPadIndex, bank, preset, newNote, enabled)
                },
                onPresetChange = { newBank, newPreset ->
                    val cfg = soundFontState.padMappings[selectedPadIndex]
                    val note = cfg?.midiNote ?: (35 + selectedPadIndex)
                    val enabled = cfg?.isSoundFontEnabled ?: true
                    viewModel.mapPadToSoundFont(selectedPadIndex, newBank, newPreset, note, enabled)
                },
                onAudition = {
                    viewModel.triggerPad(selectedPadIndex, 0.95f)
                }
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

/**
 * Top Card: Import button, Loaded SoundFont Metadata, and Master SF2 Gain slider.
 */
@Composable
private fun SoundFontImportCard(
    soundFontState: SoundFontState,
    onImportClick: () -> Unit,
    onGainChange: (Float) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF131D2E),
        border = BorderStroke(1.dp, Color(0xFF1E293B)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (soundFontState.isLoaded) soundFontState.soundFontName else "No SoundFont Loaded",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (soundFontState.isLoaded) {
                            val drumCount = soundFontState.presets.count { it.isDrum }
                            "${soundFontState.presets.size} Presets ($drumCount Drum Kits)"
                        } else {
                            "Import any standard .sf2 soundbank"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (soundFontState.isLoaded) Color(0xFF10B981) else Color(0xFF64748B)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Button(
                    onClick = onImportClick,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF0284C7)
                    ),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("import_sf2_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudUpload,
                        contentDescription = "Import",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (soundFontState.isLoaded) "REPLACE .SF2" else "IMPORT .SF2",
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                }
            }

            // Volume Gain Slider
            if (soundFontState.isLoaded) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.VolumeUp,
                        contentDescription = "Gain",
                        tint = Color(0xFF38BDF8),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "SF2 BUS GAIN: ${(soundFontState.soundFontGain * 100).toInt()}%",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF94A3B8)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Slider(
                        value = soundFontState.soundFontGain,
                        onValueChange = onGainChange,
                        valueRange = 0.0f..2.0f,
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF38BDF8),
                            activeTrackColor = Color(0xFF38BDF8),
                            inactiveTrackColor = Color(0xFF1E293B)
                        ),
                        modifier = Modifier.weight(1f).testTag("sf2_gain_slider")
                    )
                }
            }
        }
    }
}

/**
 * Quick Action: Auto-Map General MIDI Drums to Pads 0..19 (Keys 35..54, Bank 128).
 */
@Composable
private fun AutoMapGmCard(
    soundFontState: SoundFontState,
    onAutoMapClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF0F2F24),
        border = BorderStroke(1.dp, Color(0xFF059669)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Bolt,
                        contentDescription = "Auto Map",
                        tint = Color(0xFF34D399),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "AUTO-MAP GENERAL MIDI DRUMS",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.5.sp
                        ),
                        color = Color(0xFF34D399)
                    )
                }
                Text(
                    text = "Binds Pads 01-20 to Bank 128 Percussion Keys (35: Kick -> 54: Tambourine)",
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 11.sp,
                    color = Color(0xFFD1FAE5)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Button(
                onClick = onAutoMapClick,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF10B981)
                ),
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("auto_map_gm_kit_button")
            ) {
                Text(
                    text = "MAP KIT",
                    fontWeight = FontWeight.Black,
                    fontSize = 11.sp,
                    color = Color.Black
                )
            }
        }
    }
}

/**
 * Pad Routing Matrix: 4x5 interactive grid showing mode status (WAV vs SF2) and note names.
 */
@Composable
private fun PadRoutingMatrix(
    soundFontState: SoundFontState,
    selectedPadIndex: Int,
    onSelectPad: (Int) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        val rows = (0 until 20).chunked(4)
        rows.forEach { rowIndices ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                rowIndices.forEach { padIdx ->
                    val isSelected = padIdx == selectedPadIndex
                    val mapping = soundFontState.padMappings[padIdx]
                    val isSf2 = mapping?.isSoundFontEnabled == true && soundFontState.isLoaded

                    val noteLabel = if (isSf2) {
                        GeneralMidiPercussion.DRUM_NOTE_MAP[mapping?.midiNote ?: 36]?.take(7) ?: "Note ${mapping?.midiNote}"
                    } else {
                        "WAV"
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                when {
                                    isSelected -> Color(0xFF1E3A5F)
                                    isSf2 -> Color(0xFF0F2E24)
                                    else -> Color(0xFF131D2E)
                                }
                            )
                            .border(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = when {
                                    isSelected -> Color(0xFF38BDF8)
                                    isSf2 -> Color(0xFF10B981)
                                    else -> Color(0xFF1E293B)
                                },
                                shape = RoundedCornerShape(6.dp)
                            )
                            .clickable { onSelectPad(padIdx) }
                            .padding(4.dp)
                            .testTag("matrix_pad_$padIdx")
                    ) {
                        Column(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "P%02d".format(padIdx + 1),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSf2) Color(0xFF34D399) else Color(0xFF94A3B8)
                            )
                            Text(
                                text = noteLabel,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (isSf2) Color.White else Color(0xFF64748B),
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Inspector Card for the selected pad:
 * Allows toggling between WAV and SoundFont modes, selecting GM MIDI note,
 * picking SoundFont presets (with Bank 128 highlight), and live auditioning.
 */
@Composable
private fun PadInspectorCard(
    padIndex: Int,
    padName: String,
    soundFontState: SoundFontState,
    onToggleSoundFont: (Boolean) -> Unit,
    onNoteChange: (Int) -> Unit,
    onPresetChange: (bank: Int, presetIndex: Int) -> Unit,
    onAudition: () -> Unit
) {
    val mapping = soundFontState.padMappings[padIndex]
    val isSf2Enabled = mapping?.isSoundFontEnabled == true && soundFontState.isLoaded
    val currentNote = mapping?.midiNote ?: (35 + padIndex)

    var showNoteDropdown by remember { mutableStateOf(false) }
    var showPresetDropdown by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF131D2E),
        border = BorderStroke(1.dp, Color(0xFF1E293B)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Inspector Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(if (isSf2Enabled) Color(0xFF10B981) else Color(0xFF38BDF8)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "%02d".format(padIndex + 1),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "PAD ${padIndex + 1}: $padName",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )
                }

                // Audition Pad Button
                Button(
                    onClick = onAudition,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF1E293B)
                    ),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("audition_pad_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Audition",
                        tint = Color(0xFF38BDF8),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "TEST",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF38BDF8)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Mode Selector: WAV Sample vs SoundFont SF2
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF0F172A))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // WAV Mode Option
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (!isSf2Enabled) Color(0xFF1E293B) else Color.Transparent)
                        .clickable { onToggleSoundFont(false) }
                        .padding(horizontal = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.GraphicEq,
                            contentDescription = null,
                            tint = if (!isSf2Enabled) Color(0xFF38BDF8) else Color(0xFF64748B),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "WAV SAMPLE",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (!isSf2Enabled) Color.White else Color(0xFF64748B)
                        )
                    }
                }

                // SoundFont Mode Option
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isSf2Enabled) Color(0xFF064E3B) else Color.Transparent)
                        .clickable(enabled = soundFontState.isLoaded) { onToggleSoundFont(true) }
                        .padding(horizontal = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Album,
                            contentDescription = null,
                            tint = if (isSf2Enabled) Color(0xFF34D399) else Color(0xFF64748B),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "SF2 SOUNDFONT",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSf2Enabled) Color.White else Color(0xFF64748B)
                        )
                    }
                }
            }

            if (!soundFontState.isLoaded) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "💡 Import a .sf2 SoundFont above to enable multi-velocity SoundFont drum triggers for this pad.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF94A3B8)
                )
            } else if (isSf2Enabled) {
                Spacer(modifier = Modifier.height(14.dp))

                // General MIDI Drum Note Selector
                Text(
                    text = "MIDI PERCUSSION KEY (0..127)",
                    style = MaterialTheme.typography.labelSmall.copy(
                        letterSpacing = 0.5.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = Color(0xFF94A3B8)
                )
                Spacer(modifier = Modifier.height(4.dp))

                Box(modifier = Modifier.fillMaxWidth()) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF0F172A),
                        border = BorderStroke(1.dp, Color(0xFF334155)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showNoteDropdown = true }
                            .minimumInteractiveComponentSize()
                            .testTag("midi_note_dropdown_trigger")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = GeneralMidiPercussion.getLabel(currentNote),
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color.White
                            )
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = "Select note",
                                tint = Color(0xFF94A3B8)
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = showNoteDropdown,
                        onDismissRequest = { showNoteDropdown = false },
                        modifier = Modifier
                            .background(Color(0xFF0F172A))
                            .height(280.dp)
                    ) {
                        GeneralMidiPercussion.DRUM_NOTE_MAP.forEach { (note, name) ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = "$note: $name",
                                        fontWeight = if (note == currentNote) FontWeight.Bold else FontWeight.Normal,
                                        color = if (note == currentNote) Color(0xFF38BDF8) else Color.White
                                    )
                                },
                                onClick = {
                                    onNoteChange(note)
                                    showNoteDropdown = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // SoundFont Preset Selector (with Bank 128 Drum Kit highlight)
                Text(
                    text = "SOUNDFONT PRESET",
                    style = MaterialTheme.typography.labelSmall.copy(
                        letterSpacing = 0.5.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = Color(0xFF94A3B8)
                )
                Spacer(modifier = Modifier.height(4.dp))

                val currentBank = mapping?.bank ?: 128
                val currentPresetIdx = mapping?.presetIndex ?: 0
                val activePreset = soundFontState.presets.find { it.bank == currentBank && it.presetIndex == currentPresetIdx }
                    ?: soundFontState.presets.firstOrNull()

                Box(modifier = Modifier.fillMaxWidth()) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF0F172A),
                        border = BorderStroke(1.dp, Color(0xFF334155)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showPresetDropdown = true }
                            .minimumInteractiveComponentSize()
                            .testTag("sf2_preset_dropdown_trigger")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (activePreset?.isDrum == true) {
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = Color(0xFF064E3B)
                                    ) {
                                        Text(
                                            text = "DRUM KIT",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Black,
                                            color = Color(0xFF34D399),
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                }
                                Text(
                                    text = activePreset?.name ?: "Preset $currentPresetIdx",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = Color.White
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = "Select preset",
                                tint = Color(0xFF94A3B8)
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = showPresetDropdown,
                        onDismissRequest = { showPresetDropdown = false },
                        modifier = Modifier
                            .background(Color(0xFF0F172A))
                            .height(280.dp)
                    ) {
                        soundFontState.presets.forEach { preset ->
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (preset.isDrum) {
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = Color(0xFF064E3B)
                                            ) {
                                                Text(
                                                    text = "DRUM",
                                                    fontSize = 8.sp,
                                                    fontWeight = FontWeight.Black,
                                                    color = Color(0xFF34D399),
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(6.dp))
                                        }
                                        Text(
                                            text = "${preset.name} (Bank ${preset.bank}: ${preset.presetIndex})",
                                            fontWeight = if (preset.bank == currentBank && preset.presetIndex == currentPresetIdx)
                                                FontWeight.Bold else FontWeight.Normal,
                                            color = if (preset.bank == currentBank && preset.presetIndex == currentPresetIdx)
                                                Color(0xFF38BDF8) else Color.White
                                        )
                                    }
                                },
                                onClick = {
                                    onPresetChange(preset.bank, preset.presetIndex)
                                    showPresetDropdown = false
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
