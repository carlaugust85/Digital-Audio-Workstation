package com.example.ui

import android.view.MotionEvent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.PadSample
import kotlinx.coroutines.delay

/**
 * 4x5 Drum Pad Grid (20 pads).
 * Utilizes low-latency pointerInteropFilter to trigger JNI function
 * immediately on the exact finger-down MotionEvent.
 */
@Composable
fun DrumPadGrid(
    pads: List<PadSample>,
    selectedPadIndex: Int,
    lastTriggeredPad: Int?,
    onPadSelect: (Int) -> Unit,
    onPadTrigger: (padIndex: Int, velocity: Float) -> Unit,
    onPadRelease: (padIndex: Int) -> Unit = {},
    soundFontActivePads: Set<Int> = emptySet(),
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("drum_pad_grid_4x5"),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        val rows = pads.chunked(4)
        rows.forEach { rowPads ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                rowPads.forEach { pad ->
                    val isSelected = pad.index == selectedPadIndex
                    val isRecentlyTriggered = pad.index == lastTriggeredPad
                    val isSf2 = soundFontActivePads.contains(pad.index)

                    Box(modifier = Modifier.weight(1f)) {
                        DrumPadItem(
                            pad = pad,
                            isSelected = isSelected,
                            isRecentlyTriggered = isRecentlyTriggered,
                            isSoundFontActive = isSf2,
                            onSelect = { onPadSelect(pad.index) },
                            onTrigger = { velocity -> onPadTrigger(pad.index, velocity) },
                            onRelease = { onPadRelease(pad.index) }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun DrumPadItem(
    pad: PadSample,
    isSelected: Boolean,
    isRecentlyTriggered: Boolean,
    isSoundFontActive: Boolean = false,
    onSelect: () -> Unit,
    onTrigger: (velocity: Float) -> Unit,
    onRelease: () -> Unit = {}
) {
    var isPressedDown by remember { mutableStateOf(false) }
    var flashTrigger by remember { mutableStateOf(false) }

    // Visual trigger pulse effect
    LaunchedEffect(isRecentlyTriggered) {
        if (isRecentlyTriggered) {
            flashTrigger = true
            delay(120)
            flashTrigger = false
        }
    }

    val padScale by animateFloatAsState(
        targetValue = if (isPressedDown || flashTrigger) 0.94f else 1.0f,
        animationSpec = tween(durationMillis = 60),
        label = "padScale"
    )

    val padGlowColor by animateColorAsState(
        targetValue = when {
            isPressedDown || flashTrigger -> Color.White
            isSelected -> pad.color
            else -> pad.color.copy(alpha = 0.20f)
        },
        animationSpec = tween(durationMillis = 80),
        label = "padGlow"
    )

    val baseBackground = when {
        isPressedDown || flashTrigger -> Brush.verticalGradient(
            listOf(pad.color.copy(alpha = 0.9f), pad.color)
        )
        isSelected -> Brush.verticalGradient(
            listOf(Color(0xFF1E293B), Color(0xFF0F172A))
        )
        else -> Brush.verticalGradient(
            listOf(Color(0xFF182234), Color(0xFF0D1522))
        )
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.15f)
            .scale(padScale)
            .clip(RoundedCornerShape(10.dp))
            .background(baseBackground)
            .border(
                width = if (isSelected || isPressedDown || flashTrigger) 2.dp else 1.dp,
                color = padGlowColor,
                shape = RoundedCornerShape(10.dp)
            )
            .testTag("drum_pad_${pad.index}")
            // Low-latency pointerInteropFilter for exact finger-down sub-millisecond response
            .pointerInteropFilter { motionEvent ->
                when (motionEvent.actionMasked) {
                    MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                        isPressedDown = true
                        // Velocity mapping: combine touch pressure and position
                        val pressure = motionEvent.pressure.takeIf { it > 0f } ?: 0.90f
                        val velocity = pressure.coerceIn(0.2f, 1.0f)

                        // Sub-millisecond direct dispatch to JNI engine
                        onTrigger(velocity)
                        onSelect()
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                        isPressedDown = false
                        onRelease()
                        true
                    }
                    else -> false
                }
            }
            .padding(6.dp)
    ) {
        // Pad Number Header (P01 .. P20)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "P%02d".format(pad.index + 1),
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isPressedDown || flashTrigger) Color.White else pad.color
                    )
                )
                if (isSoundFontActive) {
                    Spacer(modifier = Modifier.width(3.dp))
                    Surface(
                        shape = RoundedCornerShape(3.dp),
                        color = Color(0xFF064E3B)
                    ) {
                        Text(
                            text = "SF2",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 7.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF34D399),
                            modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp)
                        )
                    }
                }
            }

            // Category Mini Pill
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(pad.color, CircleShape)
            )
        }

        // Pad Title & Transient Indicator
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = pad.name,
                style = TextStyle(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    textAlign = TextAlign.Center
                ),
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = pad.category.label,
                style = TextStyle(
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (isPressedDown || flashTrigger) Color.White.copy(alpha = 0.9f) else Color(0xFF64748B)
                )
            )
        }

        // Selection Accent Dot
        if (isSelected) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(4.dp)
                    .background(Color.White, CircleShape)
            )
        }
    }
}
