package studio.vela.art.presentation.brushstudio

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import studio.vela.art.drawing.brush.BrushLibrary
import studio.vela.art.drawing.brush.DabGenerator
import studio.vela.art.drawing.render.DabRenderer
import studio.vela.art.domain.model.BrushPreset
import studio.vela.art.ui.components.ChoiceChip
import studio.vela.art.ui.components.ChipRow
import studio.vela.art.ui.components.LabelText
import studio.vela.art.ui.components.SectionTitle
import studio.vela.art.ui.components.VelaSlider
import studio.vela.art.ui.theme.VelaTheme
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Brush Studio: a full-screen editor with a real, engine-rendered live preview.
 *
 * The preview bitmap is produced by the same [DabGenerator] + [DabRenderer] pipeline the canvas
 * uses, so what is shown is exactly what the brush paints.
 */
@Composable
fun BrushStudioScreen(
    initialBrush: BrushPreset,
    onApply: (BrushPreset) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = VelaTheme.colors
    val dimens = VelaTheme.dimens
    var brush by remember { mutableStateOf(initialBrush) }
    var category by remember { mutableStateOf(initialBrush.category) }

    Surface(modifier = Modifier.fillMaxSize(), color = colors.ground) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Brush Studio",
                    style = MaterialTheme.typography.headlineSmall,
                    color = colors.textPrimary,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onDismiss) { Text("Cancel") }
                TextButton(onClick = { onApply(brush.sanitized()) }) { Text("Use brush") }
            }
            Spacer(Modifier.height(10.dp))

            PreviewCard(brush, modifier = Modifier.fillMaxWidth().height(150.dp))

            Spacer(Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                // Preset browser
                Column(
                    modifier = Modifier
                        .weight(0.9f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(dimens.cornerLarge))
                        .background(colors.surface)
                        .border(1.dp, colors.border, RoundedCornerShape(dimens.cornerLarge))
                        .padding(10.dp),
                ) {
                    SectionTitle("Library")
                    ChipRow(modifier = Modifier.fillMaxWidth()) {
                        BrushLibrary.categoriesInUse().forEach { option ->
                            ChoiceChip(
                                label = option.label,
                                selected = option == category,
                                onClick = { category = option },
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(BrushLibrary.byCategory(category)) { preset ->
                            PresetRow(
                                preset = preset,
                                selected = preset.id == brush.id,
                                onClick = { brush = preset },
                            )
                        }
                    }
                }

                // Parameter editor
                Column(
                    modifier = Modifier
                        .weight(1.1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(dimens.cornerLarge))
                        .background(colors.surface)
                        .border(1.dp, colors.border, RoundedCornerShape(dimens.cornerLarge))
                        .verticalScroll(rememberScrollState())
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(brush.name, style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
                    LabelText(brush.category.label)
                    VelaSlider("Size", brush.size, { v -> brush = brush.copy(size = v) }, valueRange = 1f..400f, valueText = "${brush.size.roundToInt()} px")
                    VelaSlider("Opacity", brush.opacity, { v -> brush = brush.copy(opacity = v) }, valueText = "${(brush.opacity * 100).roundToInt()}%")
                    VelaSlider("Flow", brush.flow, { v -> brush = brush.copy(flow = v) }, valueText = "${(brush.flow * 100).roundToInt()}%")
                    VelaSlider("Spacing", brush.spacing, { v -> brush = brush.copy(spacing = v) }, valueRange = 0.01f..1f, valueText = "${(brush.spacing * 100).roundToInt()}%")
                    VelaSlider("Hardness", brush.hardness, { v -> brush = brush.copy(hardness = v) }, valueText = "${(brush.hardness * 100).roundToInt()}%")
                    VelaSlider("Roundness", brush.roundness, { v -> brush = brush.copy(roundness = v) }, valueText = "${(brush.roundness * 100).roundToInt()}%")
                    VelaSlider("Angle", brush.angleDeg, { v -> brush = brush.copy(angleDeg = v) }, valueRange = 0f..180f, valueText = "${brush.angleDeg.roundToInt()}°")
                    VelaSlider("Scatter", brush.scatter, { v -> brush = brush.copy(scatter = v) }, valueRange = 0f..2f)
                    VelaSlider("Stabilizer", brush.stabilizer, { v -> brush = brush.copy(stabilizer = v) }, valueRange = 0f..0.95f, valueText = "${(brush.stabilizer * 100).roundToInt()}%")
                    VelaSlider("Taper in", brush.taperStart, { v -> brush = brush.copy(taperStart = v) }, valueRange = 0f..3f)
                    VelaSlider("Size jitter", brush.sizeJitter, { v -> brush = brush.copy(sizeJitter = v) }, valueText = "${(brush.sizeJitter * 100).roundToInt()}%")
                    VelaSlider("Angle jitter", brush.angleJitterDeg, { v -> brush = brush.copy(angleJitterDeg = v) }, valueRange = 0f..180f, valueText = "${brush.angleJitterDeg.roundToInt()}°")
                    SectionTitle("Pressure")
                    VelaSlider("Size γ", brush.pressureSize.gamma, { v -> brush = brush.copy(pressureSize = brush.pressureSize.copy(gamma = v)) }, valueRange = 0.2f..3f)
                    VelaSlider("Min size", brush.pressureSize.min, { v -> brush = brush.copy(pressureSize = brush.pressureSize.copy(min = v)) }, valueText = "${(brush.pressureSize.min * 100).roundToInt()}%")
                    VelaSlider("Flow γ", brush.pressureFlow.gamma, { v -> brush = brush.copy(pressureFlow = brush.pressureFlow.copy(gamma = v)) }, valueRange = 0.2f..3f)
                    VelaSlider("Tilt", brush.tiltSize, { v -> brush = brush.copy(tiltSize = v) }, valueText = "${(brush.tiltSize * 100).roundToInt()}%")
                    VelaSlider("Velocity", brush.velocitySize, { v -> brush = brush.copy(velocitySize = v) }, valueText = "${(brush.velocitySize * 100).roundToInt()}%")
                    SectionTitle("Colour dynamics")
                    VelaSlider("Hue variation", brush.hueJitter, { v -> brush = brush.copy(hueJitter = v) }, valueText = "${(brush.hueJitter * 100).roundToInt()}%")
                    VelaSlider("Saturation variation", brush.saturationJitter, { v -> brush = brush.copy(saturationJitter = v) }, valueText = "${(brush.saturationJitter * 100).roundToInt()}%")
                    VelaSlider("Value variation", brush.valueJitter, { v -> brush = brush.copy(valueJitter = v) }, valueText = "${(brush.valueJitter * 100).roundToInt()}%")
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun PresetRow(preset: BrushPreset, selected: Boolean, onClick: () -> Unit) {
    val colors = VelaTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) colors.accentMuted else colors.surface2)
            .border(1.dp, if (selected) colors.accent else Color.Transparent, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                preset.name,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            LabelText("${preset.size.roundToInt()} px • ${preset.engine.name.lowercase().replace('_', ' ')}")
        }
    }
}

/** Renders an actual stroke with the brush so the preview cannot lie about behaviour. */
@Composable
private fun PreviewCard(brush: BrushPreset, modifier: Modifier = Modifier) {
    val colors = VelaTheme.colors
    val dimens = VelaTheme.dimens
    val bitmap = remember(brush) { renderStrokePreview(brush, widthPx = 900, heightPx = 300) }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(dimens.cornerLarge))
            .background(colors.surface)
            .border(1.dp, colors.border, RoundedCornerShape(dimens.cornerLarge)),
    ) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "Brush preview",
            modifier = Modifier.fillMaxSize(),
        )
        LabelText("Live engine preview", modifier = Modifier.align(Alignment.TopStart).padding(10.dp))
    }
}

/**
 * Draws a tapering S-curve plus a pressure ramp into an offscreen bitmap using the real
 * dab pipeline.
 */
fun renderStrokePreview(brush: BrushPreset, widthPx: Int, heightPx: Int): Bitmap {
    val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawColor(0xFFF6F4FA.toInt())
    val renderer = DabRenderer()
    val preset = brush.sanitized().copy(size = brush.size.coerceAtMost(heightPx * 0.55f))
    val generator = DabGenerator(preset, Random(7))
    val curve = mutableListOf<Pair<Float, Float>>()
    val steps = 90
    for (i in 0..steps) {
        val t = i / steps.toFloat()
        val x = 60f + t * (widthPx - 120f)
        val y = heightPx * 0.6f - kotlin.math.sin(t * Math.PI * 1.4f) * heightPx * 0.22f
        curve.add(x to y)
    }
    var previous = curve.first()
    generator.begin(previous.first, previous.second, 0.35f, 0.2f, 0L)
        .forEach { dab -> renderer.drawDab(canvas, dab, preset, 0xFF1A1722.toInt()) }
    var time = 0L
    for (index in 1 until curve.size) {
        val point = curve[index]
        val t = index / curve.size.toFloat()
        // Pressure ramps up then down across the stroke, exercising the response curves.
        val pressure = 0.25f + 0.75f * kotlin.math.sin(t * Math.PI).toFloat()
        time += 16L
        val dabs = generator.add(point.first, point.second, pressure, 0.2f, time)
        dabs.forEach { dab -> renderer.drawDab(canvas, dab, preset, 0xFF1A1722.toInt()) }
        previous = point
    }
    generator.end()
    return bitmap
}
