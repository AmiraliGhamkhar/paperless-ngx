package studio.vela.art.presentation.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Image
import studio.vela.art.drawing.brush.BrushLibrary
import studio.vela.art.drawing.layers.Layer
import studio.vela.art.drawing.layers.LayerProps
import studio.vela.art.domain.model.BlendKind
import studio.vela.art.domain.model.BrushCategory
import studio.vela.art.domain.model.BrushPreset
import studio.vela.art.domain.model.GrainKind
import studio.vela.art.ui.components.ChoiceChip
import studio.vela.art.ui.components.ChipRow
import studio.vela.art.ui.components.ColorSwatch
import studio.vela.art.ui.components.LabelText
import studio.vela.art.ui.components.SectionTitle
import studio.vela.art.ui.components.VelaIconButton
import studio.vela.art.ui.components.VelaIcons
import studio.vela.art.ui.components.VelaSlider
import studio.vela.art.ui.theme.VelaTheme
import kotlin.math.roundToInt

/** Brush parameters panel. Every control here changes real rendering parameters. */
@Composable
fun BrushPanel(
    state: EditorUiState,
    onSelectBrush: (BrushPreset) -> Unit,
    onUpdateBrush: (String, (BrushPreset) -> BrushPreset) -> Unit,
    onOpenBrushStudio: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val brush = state.brush
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(brush.name, style = MaterialTheme.typography.titleMedium, color = VelaTheme.colors.textPrimary)
                LabelText("${brush.category.label} • ${brush.engine.name.lowercase().replace('_', ' ')}")
            }
            TextButton(onClick = onOpenBrushStudio) { Text("Studio") }
        }

        CategoryPicker(brush.category, onSelectBrush)

        VelaSlider(
            label = "Size",
            value = brush.size,
            valueRange = 1f..400f,
            valueText = "${brush.size.roundToInt()} px",
            onValueChange = { v -> onUpdateBrush("Size") { it.copy(size = v) } },
        )
        VelaSlider(
            label = "Opacity",
            value = brush.opacity,
            valueText = "${(brush.opacity * 100).roundToInt()}%",
            onValueChange = { v -> onUpdateBrush("Opacity") { it.copy(opacity = v) } },
        )
        VelaSlider(
            label = "Flow",
            value = brush.flow,
            valueText = "${(brush.flow * 100).roundToInt()}%",
            onValueChange = { v -> onUpdateBrush("Flow") { it.copy(flow = v) } },
        )
        VelaSlider(
            label = "Spacing",
            value = brush.spacing,
            valueRange = 0.01f..1f,
            valueText = "${(brush.spacing * 100).roundToInt()}%",
            onValueChange = { v -> onUpdateBrush("Spacing") { it.copy(spacing = v) } },
        )
        VelaSlider(
            label = "Hardness",
            value = brush.hardness,
            valueText = "${(brush.hardness * 100).roundToInt()}%",
            onValueChange = { v -> onUpdateBrush("Hardness") { it.copy(hardness = v) } },
        )
        VelaSlider(
            label = "Roundness",
            value = brush.roundness,
            valueText = "${(brush.roundness * 100).roundToInt()}%",
            onValueChange = { v -> onUpdateBrush("Roundness") { it.copy(roundness = v) } },
        )
        VelaSlider(
            label = "Angle",
            value = brush.angleDeg,
            valueRange = 0f..180f,
            valueText = "${brush.angleDeg.roundToInt()}°",
            onValueChange = { v -> onUpdateBrush("Angle") { it.copy(angleDeg = v) } },
        )
        VelaSlider(
            label = "Scatter",
            value = brush.scatter,
            valueRange = 0f..2f,
            valueText = String.format(java.util.Locale.US, "%.2f", brush.scatter),
            onValueChange = { v -> onUpdateBrush("Scatter") { it.copy(scatter = v) } },
        )
        VelaSlider(
            label = "Stabilizer",
            value = brush.stabilizer,
            valueRange = 0f..0.95f,
            valueText = "${(brush.stabilizer * 100).roundToInt()}%",
            onValueChange = { v -> onUpdateBrush("Stabilizer") { it.copy(stabilizer = v) } },
        )
        VelaSlider(
            label = "Taper in",
            value = brush.taperStart,
            valueRange = 0f..3f,
            valueText = String.format(java.util.Locale.US, "%.2f", brush.taperStart),
            onValueChange = { v -> onUpdateBrush("Taper") { it.copy(taperStart = v) } },
        )
        VelaSlider(
            label = "Size jitter",
            value = brush.sizeJitter,
            valueText = "${(brush.sizeJitter * 100).roundToInt()}%",
            onValueChange = { v -> onUpdateBrush("Size jitter") { it.copy(sizeJitter = v) } },
        )
        VelaSlider(
            label = "Angle jitter",
            value = brush.angleJitterDeg,
            valueRange = 0f..180f,
            valueText = "${brush.angleJitterDeg.roundToInt()}°",
            onValueChange = { v -> onUpdateBrush("Angle jitter") { it.copy(angleJitterDeg = v) } },
        )

        SectionTitle("Pressure")
        VelaSlider(
            label = "Size response",
            value = brush.pressureSize.gamma,
            valueRange = 0.2f..3f,
            valueText = String.format(java.util.Locale.US, "γ %.2f", brush.pressureSize.gamma),
            onValueChange = { v -> onUpdateBrush("Pressure size") { it.copy(pressureSize = it.pressureSize.copy(gamma = v)) } },
        )
        VelaSlider(
            label = "Minimum size",
            value = brush.pressureSize.min,
            valueText = "${(brush.pressureSize.min * 100).roundToInt()}%",
            onValueChange = { v -> onUpdateBrush("Pressure size min") { it.copy(pressureSize = it.pressureSize.copy(min = v)) } },
        )
        VelaSlider(
            label = "Flow response",
            value = brush.pressureFlow.gamma,
            valueRange = 0.2f..3f,
            valueText = String.format(java.util.Locale.US, "γ %.2f", brush.pressureFlow.gamma),
            onValueChange = { v -> onUpdateBrush("Pressure flow") { it.copy(pressureFlow = it.pressureFlow.copy(gamma = v)) } },
        )
        VelaSlider(
            label = "Tilt influence",
            value = brush.tiltSize,
            valueText = "${(brush.tiltSize * 100).roundToInt()}%",
            onValueChange = { v -> onUpdateBrush("Tilt") { it.copy(tiltSize = v) } },
        )
        VelaSlider(
            label = "Velocity influence",
            value = brush.velocitySize,
            valueText = "${(brush.velocitySize * 100).roundToInt()}%",
            onValueChange = { v -> onUpdateBrush("Velocity") { it.copy(velocitySize = v) } },
        )

        SectionTitle("Texture")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
            GrainKind.entries.forEach { grain ->
                ChoiceChip(
                    label = grain.label,
                    selected = brush.grain == grain,
                    onClick = { onUpdateBrush("Grain") { it.copy(grain = grain) } },
                )
            }
        }
        if (brush.grain != GrainKind.NONE) {
            VelaSlider(
                label = "Grain scale",
                value = brush.grainScale,
                valueRange = 0.25f..4f,
                valueText = String.format(java.util.Locale.US, "%.2f", brush.grainScale),
                onValueChange = { v -> onUpdateBrush("Grain scale") { it.copy(grainScale = v) } },
            )
            VelaSlider(
                label = "Grain strength",
                value = brush.grainStrength,
                valueText = "${(brush.grainStrength * 100).roundToInt()}%",
                onValueChange = { v -> onUpdateBrush("Grain strength") { it.copy(grainStrength = v) } },
            )
        }
        VelaSlider(
            label = "Hue variation",
            value = brush.hueJitter,
            valueText = "${(brush.hueJitter * 100).roundToInt()}%",
            onValueChange = { v -> onUpdateBrush("Hue variation") { it.copy(hueJitter = v) } },
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun CategoryPicker(current: BrushCategory, onSelect: (BrushPreset) -> Unit) {
    var category by remember(current) { mutableStateOf(current) }
    ChipRow {
        BrushLibrary.categoriesInUse().forEach { option ->
            ChoiceChip(
                label = option.label,
                selected = option == category,
                onClick = { category = option },
            )
        }
    }
    val presets = BrushLibrary.byCategory(category)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        presets.forEach { preset ->
            BrushRow(preset, onClick = { onSelect(preset) })
        }
    }
}

@Composable
private fun BrushRow(preset: BrushPreset, onClick: () -> Unit) {
    val colors = VelaTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surface2)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(colors.accentMuted),
            contentAlignment = Alignment.Center,
        ) {
            Icon(VelaIcons.brush, contentDescription = null, tint = colors.accent, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(preset.name, style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            LabelText("size ${preset.size.roundToInt()} • flow ${(preset.flow * 100).roundToInt()}%")
        }
    }
}

/** Colour panel: HSV controls, hex entry, swatches and recent colours. */
@Composable
fun ColorPanel(
    state: EditorUiState,
    onPrimaryColor: (Int) -> Unit,
    onSecondaryColor: (Int) -> Unit,
    onSwap: () -> Unit,
    onOpenColorStudio: () -> Unit,
    recentColors: List<Int>,
    modifier: Modifier = Modifier,
) {
    val color = Color(state.primaryColor)
    val hsv = remember(state.primaryColor) {
        val array = FloatArray(3)
        android.graphics.Color.colorToHSV(state.primaryColor, array)
        array
    }
    var hexText by remember(state.primaryColor) {
        mutableStateOf(String.format("%08X", state.primaryColor))
    }
    val colors = VelaTheme.colors

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(color)
                    .border(2.dp, colors.border, RoundedCornerShape(16.dp)),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Primary colour", style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
                LabelText(String.format("#%08X", state.primaryColor))
            }
            VelaIconButton(
                icon = VelaIcons.palette,
                contentDescription = "Swap colours",
                onClick = onSwap,
            )
        }

        VelaSlider(
            label = "Hue",
            value = hsv[0],
            valueRange = 0f..360f,
            valueText = "${hsv[0].roundToInt()}°",
            onValueChange = { v -> onPrimaryColor(hsvColor(v, hsv[1], hsv[2], state.primaryColor)) },
        )
        VelaSlider(
            label = "Saturation",
            value = hsv[1],
            valueText = "${(hsv[1] * 100).roundToInt()}%",
            onValueChange = { v -> onPrimaryColor(hsvColor(hsv[0], v, hsv[2], state.primaryColor)) },
        )
        VelaSlider(
            label = "Value",
            value = hsv[2],
            valueText = "${(hsv[2] * 100).roundToInt()}%",
            onValueChange = { v -> onPrimaryColor(hsvColor(hsv[0], hsv[1], v, state.primaryColor)) },
        )
        VelaSlider(
            label = "Alpha",
            value = android.graphics.Color.alpha(state.primaryColor) / 255f,
            valueText = "${(android.graphics.Color.alpha(state.primaryColor) * 100 / 255)}%",
            onValueChange = { v ->
                onPrimaryColor(
                    android.graphics.Color.argb(
                        (v * 255).roundToInt(),
                        android.graphics.Color.red(state.primaryColor),
                        android.graphics.Color.green(state.primaryColor),
                        android.graphics.Color.blue(state.primaryColor),
                    ),
                )
            },
        )

        OutlinedTextField(
            value = hexText,
            onValueChange = { text ->
                hexText = text.filter { it.isLetterOrDigit() }.take(8)
                val parsed = hexText.toLongOrNull(16)
                if (parsed != null && hexText.length == 8) {
                    onPrimaryColor(parsed.toInt())
                }
            },
            label = { Text("Hex (AARRGGBB)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
        )

        SectionTitle("Swatches")
        val palette = listOf(
            0xFF111111.toInt(), 0xFF4A4A4A.toInt(), 0xFF8A8A8A.toInt(), 0xFFDDDDDD.toInt(), 0xFFFFFFFF.toInt(),
            0xFFE5484D.toInt(), 0xFFFF8A3D.toInt(), 0xFFF2C14E.toInt(), 0xFF7BE495.toInt(), 0xFF37E0D8.toInt(),
            0xFF4C6FFF.toInt(), 0xFF8B6BFF.toInt(), 0xFFFF5FA2.toInt(), 0xFF8B5A3C.toInt(), 0xFF2E5A44.toInt(),
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            palette.chunked(5).forEach { rowColors ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    rowColors.forEach { swatch ->
                        Box(Modifier.clickable { onPrimaryColor(swatch) }) {
                            ColorSwatch(Color(swatch), selected = swatch == state.primaryColor)
                        }
                    }
                }
            }
        }

        if (recentColors.isNotEmpty()) {
            SectionTitle("Recent")
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                recentColors.chunked(6).forEach { rowColors ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        rowColors.forEach { recent ->
                            Box(Modifier.clickable { onPrimaryColor(recent) }) {
                                ColorSwatch(Color(recent), size = 26.dp)
                            }
                        }
                    }
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f)) {
                Text("Secondary", style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
                Box(Modifier.padding(top = 4.dp)) {
                    Box(
                        Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(state.secondaryColor))
                            .border(1.dp, colors.border, RoundedCornerShape(10.dp))
                            .clickable { onSecondaryColor(state.secondaryColor) },
                    )
                }
            }
            TextButton(onClick = onOpenColorStudio) { Text("Color studio") }
        }
        Spacer(Modifier.height(24.dp))
    }
}

private fun hsvColor(h: Float, s: Float, v: Float, source: Int): Int =
    android.graphics.Color.HSVToColor(
        android.graphics.Color.alpha(source),
        floatArrayOf(h, s, v),
    )

/** Layer inspector with real compositing controls. */
@Composable
fun LayersPanel(
    state: EditorUiState,
    onAdd: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onMerge: () -> Unit,
    onClear: () -> Unit,
    onSelect: (String) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRename: (String, String) -> Unit,
    onCaptureProps: (String) -> LayerProps?,
    onUpdateProps: (String, (Layer) -> Unit) -> Unit,
    onCommitProps: (String, LayerProps?, String) -> Unit,
    onSetBlend: (String, BlendKind) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = VelaTheme.colors
    var renaming by remember { mutableStateOf<LayerRow?>(null) }
    var renameText by remember { mutableStateOf("") }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            VelaIconButton(VelaIcons.add, "Add layer", onAdd)
            VelaIconButton(VelaIcons.duplicate, "Duplicate layer", onDuplicate, enabled = state.activeLayerId != null)
            VelaIconButton(VelaIcons.up, "Move layer up", onMoveUp, enabled = state.activeLayerId != null)
            VelaIconButton(VelaIcons.down, "Move layer down", onMoveDown, enabled = state.activeLayerId != null)
            Spacer(Modifier.weight(1f))
            VelaIconButton(VelaIcons.merge, "Merge down", onMerge, enabled = state.layers.size > 1)
            VelaIconButton(VelaIcons.delete, "Delete layer", onDelete, enabled = state.layers.size > 1)
        }
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LabelText("${state.layers.size} layers • ${state.layers.count { it.visible }} visible")
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onClear) { Text("Clear") }
        }

        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            items(state.layers, key = { it.id }) { row ->
                LayerItem(
                    row = row,
                    onSelect = { onSelect(row.id) },
                    onVisibility = {
                        val before = onCaptureProps(row.id)
                        onUpdateProps(row.id) { it.visible = !it.visible }
                        onCommitProps(row.id, before, "Layer visibility")
                    },
                    onLock = {
                        val before = onCaptureProps(row.id)
                        onUpdateProps(row.id) { it.locked = !it.locked }
                        onCommitProps(row.id, before, "Layer lock")
                    },
                    onAlphaLock = {
                        val before = onCaptureProps(row.id)
                        onUpdateProps(row.id) { it.alphaLocked = !it.alphaLocked }
                        onCommitProps(row.id, before, "Alpha lock")
                    },
                    onClipping = {
                        val before = onCaptureProps(row.id)
                        onUpdateProps(row.id) { it.clipping = !it.clipping }
                        onCommitProps(row.id, before, "Clipping mask")
                    },
                    onCapture = { onCaptureProps(row.id) },
                    onOpacity = { value ->
                        onUpdateProps(row.id) { it.opacity = value }
                    },
                    onOpacityFinished = { before ->
                        onCommitProps(row.id, before, "Layer opacity")
                    },
                    onBlend = { blend -> onSetBlend(row.id, blend) },
                    onRename = {
                        renaming = row
                        renameText = row.name
                    },
                )
            }
        }
    }

    val target = renaming
    if (target != null) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { renaming = null }) {
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(colors.surface)
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Rename layer", style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = { renaming = null }) { Text("Cancel") }
                    TextButton(onClick = {
                        onRename(target.id, renameText)
                        renaming = null
                    }) { Text("Rename") }
                }
            }
        }
    }
}

@Composable
private fun LayerItem(
    row: LayerRow,
    onSelect: () -> Unit,
    onVisibility: () -> Unit,
    onLock: () -> Unit,
    onAlphaLock: () -> Unit,
    onClipping: () -> Unit,
    onCapture: () -> LayerProps?,
    onOpacity: (Float) -> Unit,
    onOpacityFinished: (LayerProps?) -> Unit,
    onBlend: (BlendKind) -> Unit,
    onRename: () -> Unit,
) {
    val colors = VelaTheme.colors
    var blendMenu by remember { mutableStateOf(false) }
    var opacityBefore by remember(row.id) { mutableStateOf<LayerProps?>(null) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (row.isActive) colors.accentMuted else colors.surface2)
            .border(
                1.dp,
                if (row.isActive) colors.accent.copy(alpha = 0.6f) else colors.border,
                RoundedCornerShape(14.dp),
            )
            .clickable(onClick = onSelect)
            .padding(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val thumb = row.thumbnail
            if (thumb != null) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(colors.surface3),
                ) {
                    Image(
                        bitmap = thumb.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = row.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable(onClick = onRename),
                )
                LabelText("${row.blend.label} • ${(row.opacity * 100).roundToInt()}%")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                LayerToggle(
                    icon = if (row.visible) VelaIcons.visible else VelaIcons.hidden,
                    description = "Visibility",
                    active = row.visible,
                    onClick = onVisibility,
                )
                LayerToggle(
                    icon = if (row.locked) VelaIcons.lock else VelaIcons.unlock,
                    description = "Lock",
                    active = row.locked,
                    onClick = onLock,
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = row.blend.label,
                style = MaterialTheme.typography.labelLarge,
                color = colors.textSecondary,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { blendMenu = true }
                    .padding(horizontal = 6.dp, vertical = 4.dp),
            )
            DropdownMenu(expanded = blendMenu, onDismissRequest = { blendMenu = false }) {
                BlendKind.entries.forEach { blend ->
                    DropdownMenuItem(
                        text = { Text(blend.label) },
                        onClick = {
                            onBlend(blend)
                            blendMenu = false
                        },
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            LayerToggle(icon = VelaIcons.opacity, description = "Alpha lock", active = row.alphaLocked, onClick = onAlphaLock)
            LayerToggle(icon = VelaIcons.grid, description = "Clipping mask", active = row.clipping, onClick = onClipping)
        }
        Slider(
            value = row.opacity,
            onValueChange = { value ->
                if (opacityBefore == null) opacityBefore = onCapture()
                onOpacity(value)
            },
            onValueChangeFinished = {
                onOpacityFinished(opacityBefore)
                opacityBefore = null
            },
            colors = androidx.compose.material3.SliderDefaults.colors(
                thumbColor = colors.accent,
                activeTrackColor = colors.accent,
                inactiveTrackColor = colors.surface3,
            ),
        )
    }
}

@Composable
private fun LayerToggle(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    val colors = VelaTheme.colors
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(if (active) colors.accent.copy(alpha = 0.22f) else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = if (active) colors.accent else colors.textSecondary,
            modifier = Modifier.size(17.dp),
        )
    }
}
