package com.example.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Interactive, horizontally scrollable waveform viewer on Compose Canvas.
 * Allows users to zoom into specific transient spikes for precision editing.
 */
@Composable
fun WaveformViewer(
    peaks: FloatArray,
    transientSpikes: List<Int>,
    startFrame: Int,
    endFrame: Int,
    totalFrames: Int,
    sampleRate: Int,
    accentColor: Color,
    sampleName: String,
    attackSec: Float = 0.005f,
    decaySec: Float = 0.12f,
    sustainLevel: Float = 0.75f,
    releaseSec: Float = 0.18f,
    onTrimChanged: (startFrame: Int, endFrame: Int) -> Unit,
    onADSRChanged: (attack: Float, decay: Float, sustain: Float, release: Float) -> Unit = { _, _, _, _ -> },
    onAudition: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Zoom range: 1.0f (fit) to 30.0f (deep zoom into transient spikes)
    var zoomLevel by remember(peaks) { mutableFloatStateOf(1.0f) }
    var scrollOffsetPx by remember(peaks) { mutableFloatStateOf(0f) }

    // Active drag handle: 0 = none, 1 = start handle, 2 = end handle
    var draggingHandle by remember { mutableStateOf<Int?>(null) }

    val textMeasurer = rememberTextMeasurer()

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("waveform_viewer_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF0F172A)
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // Header: Sample Info, Trim Readouts & Audition Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(accentColor, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = sampleName.uppercase(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color(0xFF1E293B)
                    ) {
                        Text(
                            text = "${((totalFrames.toFloat() / sampleRate) * 1000).roundToInt()} ms",
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = Color(0xFF94A3B8)
                            ),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                // Audition button with testTag
                FilledTonalIconButton(
                    onClick = onAudition,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("waveform_audition_button"),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = accentColor.copy(alpha = 0.25f),
                        contentColor = accentColor
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Audition Sample",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Canvas Waveform Area
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF090D16))
                    .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(10.dp))
            ) {
                val canvasWidth = constraints.maxWidth.toFloat()
                val canvasHeight = constraints.maxHeight.toFloat()
                val numPeaks = peaks.size.coerceAtLeast(1)

                // The total virtual width when zoomed
                val virtualWidth = canvasWidth * zoomLevel
                val maxScroll = max(0f, virtualWidth - canvasWidth)

                // Clamp scroll offset
                scrollOffsetPx = scrollOffsetPx.coerceIn(0f, maxScroll)

                // Fraction conversions for trim handles
                val startRatio = if (totalFrames > 0) (startFrame.toFloat() / totalFrames).coerceIn(0f, 1f) else 0f
                val endRatio = if (totalFrames > 0) (endFrame.toFloat() / totalFrames).coerceIn(0f, 1f) else 1f

                val startX = (startRatio * virtualWidth) - scrollOffsetPx
                val endX = (endRatio * virtualWidth) - scrollOffsetPx

                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("waveform_interactive_canvas")
                        // Combined Gesture handling: Dragging trim handles, scroll, and pinch zoom
                        .pointerInput(peaks, zoomLevel, totalFrames) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                val newZoom = (zoomLevel * zoom).coerceIn(1.0f, 30.0f)
                                val newVirtualWidth = canvasWidth * newZoom
                                val newMaxScroll = max(0f, newVirtualWidth - canvasWidth)

                                // Adjust scroll relative to zoom center
                                val currentScroll = scrollOffsetPx
                                val newScroll = ((currentScroll - pan.x) * (newZoom / zoomLevel)).coerceIn(0f, newMaxScroll)

                                zoomLevel = newZoom
                                scrollOffsetPx = newScroll
                            }
                        }
                        .pointerInput(peaks, zoomLevel, startFrame, endFrame, totalFrames) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    val threshold = 32.dp.toPx()
                                    draggingHandle = when {
                                        abs(offset.x - startX) <= threshold -> 1
                                        abs(offset.x - endX) <= threshold -> 2
                                        else -> null
                                    }
                                },
                                onDragEnd = { draggingHandle = null },
                                onDragCancel = { draggingHandle = null },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    if (draggingHandle != null && totalFrames > 0) {
                                        val deltaFrames = ((dragAmount.x / virtualWidth) * totalFrames).toInt()
                                        if (draggingHandle == 1) {
                                            val newStart = (startFrame + deltaFrames).coerceIn(0, endFrame - 50)
                                            onTrimChanged(newStart, endFrame)
                                        } else if (draggingHandle == 2) {
                                            val newEnd = (endFrame + deltaFrames).coerceIn(startFrame + 50, totalFrames)
                                            onTrimChanged(startFrame, newEnd)
                                        }
                                    } else {
                                        // Pan scroll
                                        val newScroll = (scrollOffsetPx - dragAmount.x).coerceIn(0f, maxScroll)
                                        scrollOffsetPx = newScroll
                                    }
                                }
                            )
                        }
                ) {
                    drawWaveformCanvas(
                        peaks = peaks,
                        transientSpikes = transientSpikes,
                        zoomLevel = zoomLevel,
                        scrollOffsetPx = scrollOffsetPx,
                        virtualWidth = virtualWidth,
                        canvasWidth = canvasWidth,
                        canvasHeight = canvasHeight,
                        startX = startX,
                        endX = endX,
                        startRatio = startRatio,
                        endRatio = endRatio,
                        totalFrames = totalFrames,
                        sampleRate = sampleRate,
                        accentColor = accentColor,
                        textMeasurer = textMeasurer
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Precision Trim Info & Transient Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Millisecond trim readout
                val startMs = if (totalFrames > 0) (startFrame.toFloat() / sampleRate * 1000f) else 0f
                val endMs = if (totalFrames > 0) (endFrame.toFloat() / sampleRate * 1000f) else 0f
                val lengthMs = max(0f, endMs - startMs)

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "START: ",
                            style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF10B981))
                        )
                        Text(
                            text = "${"%.1f".format(startMs)}ms",
                            style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = Color.White)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "END: ",
                            style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFF43F5E))
                        )
                        Text(
                            text = "${"%.1f".format(endMs)}ms",
                            style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = Color.White)
                        )
                    }
                    Text(
                        text = "ACTIVE LENGTH: ${"%.1f".format(lengthMs)} ms  (${endFrame - startFrame} frames)",
                        style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = Color(0xFF94A3B8))
                    )
                }

                // Transient Spike Action: Snap to first major transient
                if (transientSpikes.isNotEmpty()) {
                    FilledTonalIconButton(
                        onClick = {
                            val primaryTransient = transientSpikes.firstOrNull() ?: 0
                            val frameAtTransient = ((primaryTransient.toFloat() / peaks.size) * totalFrames).toInt()
                            val clampedStart = frameAtTransient.coerceIn(0, max(0, endFrame - 100))
                            onTrimChanged(clampedStart, endFrame)
                        },
                        modifier = Modifier
                            .height(32.dp)
                            .testTag("waveform_snap_transient_button"),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = Color(0xFFFBBF24).copy(alpha = 0.2f),
                            contentColor = Color(0xFFFBBF24)
                        )
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FlashOn,
                                contentDescription = "Snap to Transient",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Snap Spike", style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Zoom Toolbar: Slider & Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(
                    onClick = {
                        zoomLevel = (zoomLevel / 1.5f).coerceAtLeast(1.0f)
                    },
                    modifier = Modifier.size(32.dp).testTag("waveform_zoom_out_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.ZoomOut,
                        contentDescription = "Zoom Out",
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(18.dp)
                    )
                }

                Slider(
                    value = zoomLevel,
                    onValueChange = { zoomLevel = it },
                    valueRange = 1.0f..30.0f,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("waveform_zoom_slider"),
                    colors = SliderDefaults.colors(
                        thumbColor = accentColor,
                        activeTrackColor = accentColor,
                        inactiveTrackColor = Color(0xFF334155)
                    )
                )

                IconButton(
                    onClick = {
                        zoomLevel = (zoomLevel * 1.5f).coerceAtMost(30.0f)
                    },
                    modifier = Modifier.size(32.dp).testTag("waveform_zoom_in_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.ZoomIn,
                        contentDescription = "Zoom In",
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(18.dp)
                    )
                }

                IconButton(
                    onClick = {
                        zoomLevel = 1.0f
                        scrollOffsetPx = 0f
                    },
                    modifier = Modifier.size(32.dp).testTag("waveform_zoom_fit_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.FitScreen,
                        contentDescription = "Fit to Screen",
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(18.dp)
                    )
                }

                Text(
                    text = "${"%.1f".format(zoomLevel)}x",
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = accentColor
                    ),
                    modifier = Modifier.width(36.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // ADSR Dynamic Envelope Generator Controls
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFF090D16),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E293B)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "VOICE ADSR VOLUME ENVELOPE",
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF38BDF8),
                                letterSpacing = 1.sp
                            )
                        )
                        Text(
                            text = "A: ${(attackSec * 1000).roundToInt()}ms | D: ${(decaySec * 1000).roundToInt()}ms | S: ${(sustainLevel * 100).roundToInt()}% | R: ${(releaseSec * 1000).roundToInt()}ms",
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.sp,
                                color = Color(0xFF94A3B8)
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Attack
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "ATTACK",
                                style = TextStyle(fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF10B981))
                            )
                            Slider(
                                value = attackSec,
                                onValueChange = { onADSRChanged(it, decaySec, sustainLevel, releaseSec) },
                                valueRange = 0.001f..0.500f,
                                modifier = Modifier.height(28.dp).testTag("adsr_attack_slider"),
                                colors = SliderDefaults.colors(
                                    thumbColor = Color(0xFF10B981),
                                    activeTrackColor = Color(0xFF10B981),
                                    inactiveTrackColor = Color(0xFF1E293B)
                                )
                            )
                        }

                        // Decay
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "DECAY",
                                style = TextStyle(fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFBBF24))
                            )
                            Slider(
                                value = decaySec,
                                onValueChange = { onADSRChanged(attackSec, it, sustainLevel, releaseSec) },
                                valueRange = 0.010f..1.500f,
                                modifier = Modifier.height(28.dp).testTag("adsr_decay_slider"),
                                colors = SliderDefaults.colors(
                                    thumbColor = Color(0xFFFBBF24),
                                    activeTrackColor = Color(0xFFFBBF24),
                                    inactiveTrackColor = Color(0xFF1E293B)
                                )
                            )
                        }

                        // Sustain
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "SUSTAIN",
                                style = TextStyle(fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF38BDF8))
                            )
                            Slider(
                                value = sustainLevel,
                                onValueChange = { onADSRChanged(attackSec, decaySec, it, releaseSec) },
                                valueRange = 0.0f..1.0f,
                                modifier = Modifier.height(28.dp).testTag("adsr_sustain_slider"),
                                colors = SliderDefaults.colors(
                                    thumbColor = Color(0xFF38BDF8),
                                    activeTrackColor = Color(0xFF38BDF8),
                                    inactiveTrackColor = Color(0xFF1E293B)
                                )
                            )
                        }

                        // Release
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "RELEASE",
                                style = TextStyle(fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFFF43F5E))
                            )
                            Slider(
                                value = releaseSec,
                                onValueChange = { onADSRChanged(attackSec, decaySec, sustainLevel, it) },
                                valueRange = 0.010f..2.000f,
                                modifier = Modifier.height(28.dp).testTag("adsr_release_slider"),
                                colors = SliderDefaults.colors(
                                    thumbColor = Color(0xFFF43F5E),
                                    activeTrackColor = Color(0xFFF43F5E),
                                    inactiveTrackColor = Color(0xFF1E293B)
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Draws the entire waveform, grid lines, time scale, transient spike pins, and trim handles on Canvas.
 */
private fun DrawScope.drawWaveformCanvas(
    peaks: FloatArray,
    transientSpikes: List<Int>,
    zoomLevel: Float,
    scrollOffsetPx: Float,
    virtualWidth: Float,
    canvasWidth: Float,
    canvasHeight: Float,
    startX: Float,
    endX: Float,
    startRatio: Float,
    endRatio: Float,
    totalFrames: Int,
    sampleRate: Int,
    accentColor: Color,
    textMeasurer: TextMeasurer
) {
    val centerY = canvasHeight / 2f
    val waveMaxHeight = (canvasHeight * 0.76f) / 2f
    val numPeaks = peaks.size.coerceAtLeast(1)

    // 1. Draw Background Grid Lines
    val numGridLines = 8
    for (i in 0..numGridLines) {
        val y = (canvasHeight / numGridLines) * i
        drawLine(
            color = Color(0xFF1E293B).copy(alpha = 0.5f),
            start = Offset(0f, y),
            end = Offset(canvasWidth, y),
            strokeWidth = 1f
        )
    }

    // Zero-crossing center axis line
    drawLine(
        color = Color(0xFF475569),
        start = Offset(0f, centerY),
        end = Offset(canvasWidth, centerY),
        strokeWidth = 1.2f
    )

    // 2. Draw Waveform Peaks
    val peakWidth = virtualWidth / numPeaks
    val step = max(1, (1f / (peakWidth * 0.5f)).toInt())

    val wavePath = Path()
    val mirrorWavePath = Path()

    // Determine visible peak indices
    val startPeakIdx = max(0, ((scrollOffsetPx / virtualWidth) * numPeaks).toInt() - 2)
    val endPeakIdx = min(numPeaks, (((scrollOffsetPx + canvasWidth) / virtualWidth) * numPeaks).toInt() + 2)

    val gradientBrush = Brush.verticalGradient(
        colors = listOf(
            accentColor,
            accentColor.copy(alpha = 0.6f),
            Color(0xFF38BDF8).copy(alpha = 0.8f)
        ),
        startY = centerY - waveMaxHeight,
        endY = centerY + waveMaxHeight
    )

    for (i in startPeakIdx until endPeakIdx step step) {
        val x = (i * peakWidth) - scrollOffsetPx
        if (x < -10f || x > canvasWidth + 10f) continue

        val peakVal = peaks[i].coerceIn(0f, 1f)
        val h = peakVal * waveMaxHeight

        // Draw vertical peak bar
        drawLine(
            brush = gradientBrush,
            start = Offset(x, centerY - h),
            end = Offset(x, centerY + h),
            strokeWidth = max(1.5f, peakWidth * 0.85f),
            cap = StrokeCap.Round
        )

        // If high zoom, draw detailed transient spike dots
        if (zoomLevel > 6f && peakVal > 0.4f) {
            drawCircle(
                color = Color.White,
                radius = 2.5f,
                center = Offset(x, centerY - h)
            )
        }
    }

    // 3. Highlight Detected Transient Spikes
    for (transientIdx in transientSpikes) {
        val tx = (transientIdx * peakWidth) - scrollOffsetPx
        if (tx in -10f..canvasWidth + 10f) {
            // Draw glowing vertical transient indicator line
            drawLine(
                color = Color(0xFFFBBF24).copy(alpha = 0.85f),
                start = Offset(tx, 4f),
                end = Offset(tx, canvasHeight - 4f),
                strokeWidth = 1.5f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)
            )

            // Draw transient spike badge icon at top
            drawCircle(
                color = Color(0xFFFBBF24),
                radius = 4f,
                center = Offset(tx, 10f)
            )

            if (zoomLevel > 3f) {
                val measured = textMeasurer.measure(
                    text = "SPIKE",
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFBBF24)
                    )
                )
                drawText(
                    textLayoutResult = measured,
                    topLeft = Offset(tx + 4f, 4f)
                )
            }
        }
    }

    // 4. Draw Trim Dimming Scrims (Outside selection)
    if (startX > 0f) {
        drawRect(
            color = Color(0x99000000),
            topLeft = Offset(0f, 0f),
            size = Size(startX.coerceAtMost(canvasWidth), canvasHeight)
        )
    }
    if (endX < canvasWidth) {
        val rightScrimStart = max(0f, endX)
        val rightScrimWidth = canvasWidth - rightScrimStart
        if (rightScrimWidth > 0f) {
            drawRect(
                color = Color(0x99000000),
                topLeft = Offset(rightScrimStart, 0f),
                size = Size(rightScrimWidth, canvasHeight)
            )
        }
    }

    // 5. Draw Start Trim Handle (Green)
    if (startX in -10f..canvasWidth + 10f) {
        drawLine(
            color = Color(0xFF10B981),
            start = Offset(startX, 0f),
            end = Offset(startX, canvasHeight),
            strokeWidth = 2.5f
        )
        // Top flag
        drawRect(
            color = Color(0xFF10B981),
            topLeft = Offset(startX, 0f),
            size = Size(14f, 18f)
        )
        // Bottom flag
        drawRect(
            color = Color(0xFF10B981),
            topLeft = Offset(startX, canvasHeight - 18f),
            size = Size(14f, 18f)
        )
    }

    // 6. Draw End Trim Handle (Red / Rose)
    if (endX in -10f..canvasWidth + 10f) {
        drawLine(
            color = Color(0xFFF43F5E),
            start = Offset(endX, 0f),
            end = Offset(endX, canvasHeight),
            strokeWidth = 2.5f
        )
        // Top flag
        drawRect(
            color = Color(0xFFF43F5E),
            topLeft = Offset(endX - 14f, 0f),
            size = Size(14f, 18f)
        )
        // Bottom flag
        drawRect(
            color = Color(0xFFF43F5E),
            topLeft = Offset(endX - 14f, canvasHeight - 18f),
            size = Size(14f, 18f)
        )
    }
}
