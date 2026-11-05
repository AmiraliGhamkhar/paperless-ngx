package studio.vela.art.presentation.colorstudio

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import studio.vela.art.ui.components.ColorSwatch
import studio.vela.art.ui.components.LabelText
import studio.vela.art.ui.components.SectionTitle
import studio.vela.art.ui.components.VelaSlider
import studio.vela.art.ui.theme.VelaTheme
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Colour Studio: interactive HSV wheel, value/alpha control, harmonies and hex entry.
 *
 * The wheel is drawn with a real sweep gradient plus a radial saturation falloff, and pointer
 * drags map back to hue/saturation with actual geometry (no fake slider overlays).
 */
@Composable
fun ColorStudioScreen(
    initialColor: Int,
    onColorChange: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = VelaTheme.colors
    val dimens = VelaTheme.dimens
    val hsv = remember(initialColor) {
        FloatArray(3).also { AndroidColor.colorToHSV(initialColor, it) }
    }
    var hue by remember { mutableStateOf(hsv[0]) }
    var saturation by remember { mutableStateOf(hsv[1]) }
    var value by remember { mutableStateOf(hsv[2]) }
    var alpha by remember { mutableStateOf(AndroidColor.alpha(initialColor) / 255f) }

    fun currentArgb(): Int = AndroidColor.HSVToColor(
        (alpha * 255).roundToInt().coerceIn(0, 255),
        floatArrayOf(hue, saturation, value.coerceAtLeast(0.001f)),
    )

    Surface(modifier = Modifier.fillMaxSize(), color = colors.ground) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Color Studio",
                    style = MaterialTheme.typography.headlineSmall,
                    color = colors.textPrimary,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onDismiss) { Text("Close") }
            }
            Spacer(Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    HsvWheel(
                        hue = hue,
                        saturation = saturation,
                        modifier = Modifier.size(240.dp),
                        onPick = { h, s ->
                            hue = h
                            saturation = s
                            onColorChange(currentArgb())
                        },
                    )
                    Spacer(Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(currentArgb()))
                            .border(2.dp, colors.border, RoundedCornerShape(16.dp)),
                    )
                    LabelText(String.format("#%08X", currentArgb()))
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    VelaSlider(
                        label = "Hue",
                        value = hue,
                        valueRange = 0f..360f,
                        valueText = "${hue.roundToInt()}°",
                        onValueChange = { hue = it; onColorChange(currentArgb()) },
                    )
                    VelaSlider(
                        label = "Saturation",
                        value = saturation,
                        valueText = "${(saturation * 100).roundToInt()}%",
                        onValueChange = { saturation = it; onColorChange(currentArgb()) },
                    )
                    VelaSlider(
                        label = "Value",
                        value = value,
                        valueText = "${(value * 100).roundToInt()}%",
                        onValueChange = { value = it; onColorChange(currentArgb()) },
                    )
                    VelaSlider(
                        label = "Alpha",
                        value = alpha,
                        valueText = "${(alpha * 100).roundToInt()}%",
                        onValueChange = { alpha = it; onColorChange(currentArgb()) },
                    )
                    SectionTitle("Harmonies")
                    val harmonies = remember(hue, saturation, value, alpha) {
                        listOf(
                            "Complementary" to ((hue + 180f) % 360f),
                            "Triadic A" to ((hue + 120f) % 360f),
                            "Triadic B" to ((hue + 240f) % 360f),
                            "Analogous −" to ((hue + 330f) % 360f),
                            "Analogous +" to ((hue + 30f) % 360f),
                            "Split" to ((hue + 150f) % 360f),
                        ).map { (label, harmonyHue) ->
                            label to AndroidColor.HSVToColor(
                                (alpha * 255).roundToInt().coerceIn(0, 255),
                                floatArrayOf(harmonyHue, saturation, value),
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        harmonies.take(6).forEach { (_, harmonyColor) ->
                            Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(Color(harmonyColor))
                                .border(1.dp, colors.border, RoundedCornerShape(10.dp)))
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ColorSwatch(Color(currentArgb()), size = 30.dp)
                        LabelText("Values are applied live to the active brush colour.")
                    }
                }
            }
        }
    }
}

@Composable
private fun HsvWheel(
    hue: Float,
    saturation: Float,
    modifier: Modifier = Modifier,
    onPick: (hue: Float, saturation: Float) -> Unit,
) {
    val colors = VelaTheme.colors
    var center by remember { mutableStateOf(Offset.Zero) }
    var radius by remember { mutableStateOf(1f) }

    fun pick(position: Offset) {
        val dx = position.x - center.x
        val dy = position.y - center.y
        val distance = hypot(dx, dy)
        if (distance <= 0.001f) return
        val angle = (Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat() + 360f) % 360f
        val sat = (distance / radius).coerceIn(0f, 1f)
        onPick(angle, sat)
    }

    Canvas(
        modifier = modifier.pointerInput(Unit) {
            detectDragGestures(
                onDragStart = { pick(it) },
                onDrag = { change, _ -> pick(change.position) },
            )
        },
    ) {
        center = Offset(size.width / 2f, size.height / 2f)
        radius = size.minDimension / 2f - 6f
        val wheelColors = List(37) { index ->
            Color(AndroidColor.HSVToColor(floatArrayOf(index * 10f, 1f, 1f)))
        }
        drawCircle(brush = Brush.sweepGradient(wheelColors, center))
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color.White, Color.White.copy(alpha = 0f)),
                center = center,
                radius = radius,
            ),
            radius = radius,
        )
        val angleRad = Math.toRadians(hue.toDouble())
        val markerRadius = radius * saturation.coerceIn(0f, 1f)
        val marker = Offset(
            center.x + (cos(angleRad) * markerRadius).toFloat(),
            center.y + (sin(angleRad) * markerRadius).toFloat(),
        )
        drawCircle(color = Color.Black.copy(alpha = 0.6f), radius = 11f, center = marker, style = Stroke(width = 4f))
        drawCircle(color = colors.textPrimary, radius = 9f, center = marker, style = Stroke(width = 2.5f))
        rotate(degrees = 0f) {
            drawArc(
                color = colors.border,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = Size(radius * 2, radius * 2),
                style = Stroke(width = 2f),
            )
        }
    }
}
