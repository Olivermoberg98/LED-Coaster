package com.olivermoberg.ledcoaster.ui.main

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

/**
 * Hue/saturation wheel plus a brightness slider. [onColorSelected] gets the
 * colour as ARGB each time a finger lifts off the wheel or the slider, so a
 * drag sends one packet, not one per move. OK only closes the dialog.
 */
@Composable
fun ColorPickerDialog(initialColor: Int, onColorSelected: (Int) -> Unit, onDismiss: () -> Unit) {
    val hsv = remember { FloatArray(3).also { android.graphics.Color.colorToHSV(initialColor, it) } }
    var hue by remember { mutableFloatStateOf(hsv[0]) }
    var saturation by remember { mutableFloatStateOf(hsv[1]) }
    var brightness by remember { mutableFloatStateOf(hsv[2]) }
    val color = Color.hsv(hue, saturation, brightness)
    // Reads the state when called, so a release right after the last move sends that move's colour
    val emit = { onColorSelected(Color.hsv(hue, saturation, brightness).toArgb()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
        title = { Text("Color") },
        text = {
            Column {
                HueSaturationWheel(
                    hue = hue,
                    saturation = saturation,
                    onChange = { h, s -> hue = h; saturation = s },
                    onRelease = emit,
                    modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                )
                Slider(
                    value = brightness,
                    onValueChange = { brightness = it },
                    onValueChangeFinished = emit,
                    modifier = Modifier.padding(top = 16.dp),
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(color),
                )
            }
        },
    )
}

/** Hue runs round the wheel clockwise from the right, saturation from the centre out. */
@Composable
private fun HueSaturationWheel(
    hue: Float,
    saturation: Float,
    onChange: (hue: Float, saturation: Float) -> Unit,
    onRelease: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentOnChange by rememberUpdatedState(onChange)
    val currentOnRelease by rememberUpdatedState(onRelease)
    Canvas(
        modifier = modifier.pointerInput(Unit) {
            fun pick(position: Offset) {
                val radius = min(size.width, size.height) / 2f
                val dx = position.x - size.width / 2f
                val dy = position.y - size.height / 2f
                val angle = Math.toDegrees(atan2(dy, dx).toDouble()).toFloat()
                currentOnChange((angle + 360f) % 360f, (hypot(dx, dy) / radius).coerceIn(0f, 1f))
            }
            awaitEachGesture {
                val down = awaitFirstDown()
                pick(down.position)
                down.consume()
                while (true) {
                    val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) break
                    pick(change.position)
                    change.consume()
                }
                currentOnRelease()
            }
        },
    ) {
        val radius = size.minDimension / 2f
        drawCircle(Brush.sweepGradient((0..6).map { Color.hsv(it * 60f % 360f, 1f, 1f) }, center), radius)
        drawCircle(Brush.radialGradient(listOf(Color.White, Color.Transparent), center, radius), radius)
        val rad = Math.toRadians(hue.toDouble())
        val marker = center + Offset(cos(rad).toFloat(), sin(rad).toFloat()) * (saturation * radius)
        drawCircle(Color.Black, 10.dp.toPx(), marker, style = Stroke(3.dp.toPx()))
        drawCircle(Color.White, 10.dp.toPx(), marker, style = Stroke(1.5.dp.toPx()))
    }
}
