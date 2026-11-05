package studio.vela.art.presentation.newcanvas

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import studio.vela.art.app.AppContainer
import studio.vela.art.domain.model.CanvasPresets
import studio.vela.art.domain.model.CanvasSpec
import studio.vela.art.ui.components.BentoCard
import studio.vela.art.ui.components.ChoiceChip
import studio.vela.art.ui.components.ChipRow
import studio.vela.art.ui.components.LabelText
import studio.vela.art.ui.components.SectionTitle
import studio.vela.art.ui.components.StatusPill
import studio.vela.art.ui.components.VelaIconButton
import studio.vela.art.ui.components.VelaIcons
import studio.vela.art.ui.theme.VelaTheme

private enum class BackgroundChoice(val label: String) { WHITE("White"), TRANSPARENT("Transparent"), TONED("Toned") }

@Composable
fun NewCanvasScreen(
    container: AppContainer,
    onBack: () -> Unit,
    onCreated: (Long) -> Unit,
) {
    val colors = VelaTheme.colors
    val dimens = VelaTheme.dimens
    val scope = rememberCoroutineScope()

    var name by remember { mutableStateOf("") }
    var width by remember { mutableStateOf(2048) }
    var height by remember { mutableStateOf(2048) }
    var dpi by remember { mutableStateOf(300) }
    var background by remember { mutableStateOf(BackgroundChoice.WHITE) }
    var creating by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val spec = remember(width, height, dpi) {
        CanvasSpec(width, height, dpi).clampedToLimits()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.ground)
            .verticalScroll(rememberScrollState())
            .padding(dimens.pagePadding),
        verticalArrangement = Arrangement.spacedBy(dimens.gap * 1.2f),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            VelaIconButton(VelaIcons.back, "Back", onBack, containerColor = colors.surface)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("New canvas", style = MaterialTheme.typography.headlineMedium, color = colors.textPrimary)
                LabelText("Choose a size, resolution and paper")
            }
            ChoiceChip(
                label = if (creating) "Creating…" else "Create",
                selected = true,
                onClick = {
                    if (creating) return@ChoiceChip
                    creating = true
                    error = null
                    scope.launch {
                        try {
                            val projectName = name.ifBlank { "Untitled" }
                            val backgroundArgb = when (background) {
                                BackgroundChoice.WHITE -> AndroidColor.WHITE
                                BackgroundChoice.TRANSPARENT -> null
                                BackgroundChoice.TONED -> 0xFFF1EAD8.toInt()
                            }
                            val document = withContext(Dispatchers.IO) {
                                container.projectRepository.createProject(projectName, spec, backgroundArgb)
                            }
                            onCreated(document.projectId)
                        } catch (e: java.io.IOException) {
                            error = "Could not create the project: ${e.message}"
                        } finally {
                            creating = false
                        }
                    }
                },
            )
        }

        BentoCard(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Project name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                NumberField("Width", width, Modifier.weight(1f)) { width = it }
                NumberField("Height", height, Modifier.weight(1f)) { height = it }
                NumberField("DPI", dpi, Modifier.weight(1f)) { dpi = it }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusPill(spec.formattedMegapixels(), colors.cyan)
                Spacer(Modifier.width(6.dp))
                StatusPill("≈ ${spec.formattedMemory()}", colors.pink)
                Spacer(Modifier.width(6.dp))
                StatusPill(spec.formattedPhysicalSize(), colors.textSecondary)
                Spacer(Modifier.weight(1f))
                ChoiceChip(
                    label = "Swap",
                    selected = false,
                    onClick = {
                        val tmp = width
                        width = height
                        height = tmp
                    },
                )
            }
            if (spec.isRiskyForDevice()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Large canvas: ${spec.formattedMegapixels()} may exceed memory on some devices. Consider a smaller size or fewer layers.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.warning,
                )
            }
        }

        SectionTitle("Presets")
        CanvasPresets.categories.forEach { category ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                LabelText(category)
                ChipRow {
                    CanvasPresets.all.filter { it.category == category }.forEach { preset ->
                        ChoiceChip(
                            label = preset.label,
                            selected = width == preset.spec.width && height == preset.spec.height,
                            onClick = {
                                width = preset.spec.width
                                height = preset.spec.height
                                dpi = preset.spec.dpi
                                if (name.isBlank()) name = preset.label
                            },
                        )
                    }
                }
            }
        }

        SectionTitle("Paper")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BackgroundChoice.entries.forEach { choice ->
                ChoiceChip(
                    label = choice.label,
                    selected = background == choice,
                    onClick = { background = choice },
                )
            }
        }

        SectionTitle("Resolution guidance")
        BentoCard(modifier = Modifier.fillMaxWidth()) {
            GuidanceRow("Sketch / study", "1500–2000 px", "fast, low memory")
            GuidanceRow("Finished illustration", "2500–3500 px", "print quality at 300 DPI")
            GuidanceRow("Print poster", "3508 px wide (A4)", "use 300 DPI")
            GuidanceRow("Webtoon", "1600 × 8000 px", "tall strips, single layer recommended")
        }

        error?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = colors.error)
        }

        BentoCard(modifier = Modifier.fillMaxWidth()) {
            Spacer(Modifier.height(4.dp))
            ChoiceChip(
                label = if (creating) "Creating…" else "Create canvas",
                selected = true,
                onClick = {
                    if (creating) return@ChoiceChip
                    creating = true
                    scope.launch {
                        try {
                            val backgroundArgb = when (background) {
                                BackgroundChoice.WHITE -> AndroidColor.WHITE
                                BackgroundChoice.TRANSPARENT -> null
                                BackgroundChoice.TONED -> 0xFFF1EAD8.toInt()
                            }
                            val document = withContext(Dispatchers.IO) {
                                container.projectRepository.createProject(name.ifBlank { "Untitled" }, spec, backgroundArgb)
                            }
                            onCreated(document.projectId)
                        } catch (e: java.io.IOException) {
                            error = "Could not create the project: ${e.message}"
                        } finally {
                            creating = false
                        }
                    }
                },
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun GuidanceRow(title: String, size: String, note: String) {
    val colors = VelaTheme.colors
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary)
            LabelText(note)
        }
        Text(size, style = MaterialTheme.typography.labelLarge, color = colors.cyan)
    }
}

@Composable
private fun NumberField(label: String, value: Int, modifier: Modifier = Modifier, onChange: (Int) -> Unit) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    OutlinedTextField(
        value = text,
        onValueChange = { raw ->
            text = raw.filter { it.isDigit() }.take(5)
            text.toIntOrNull()?.let(onChange)
        },
        label = { Text(label) },
        singleLine = true,
        modifier = modifier,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    )
}
