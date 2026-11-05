package studio.vela.art.presentation.settings

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import studio.vela.art.app.AppContainer
import studio.vela.art.data.settings.AccentChoice
import studio.vela.art.data.settings.AppSettings
import studio.vela.art.data.settings.CornerStyle
import studio.vela.art.data.settings.DensityLevel
import studio.vela.art.data.settings.ThemeMode
import studio.vela.art.ui.components.BentoCard
import studio.vela.art.ui.components.ChoiceChip
import studio.vela.art.ui.components.ChipRow
import studio.vela.art.ui.components.LabelText
import studio.vela.art.ui.components.SectionTitle
import studio.vela.art.ui.components.VelaIconButton
import studio.vela.art.ui.components.VelaIcons
import studio.vela.art.ui.components.VelaSlider
import studio.vela.art.ui.components.VelaSwitchRow
import studio.vela.art.ui.theme.VelaTheme
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    container: AppContainer,
    settings: AppSettings,
    onUpdate: ((AppSettings) -> AppSettings) -> Unit,
    onBack: () -> Unit,
) {
    val colors = VelaTheme.colors
    val dimens = VelaTheme.dimens
    val scope = rememberCoroutineScope()
    var storageBytes by remember { mutableStateOf<Long?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.ground)
            .verticalScroll(rememberScrollState())
            .padding(dimens.pagePadding),
        verticalArrangement = Arrangement.spacedBy(dimens.gap),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            VelaIconButton(VelaIcons.back, "Back", onBack, containerColor = colors.surface)
            Spacer(Modifier.width(10.dp))
            Column {
                Text("Settings", style = MaterialTheme.typography.headlineMedium, color = colors.textPrimary)
                LabelText("Vela Studio 1.0.0")
            }
        }

        SectionTitle("Appearance")
        BentoCard(modifier = Modifier.fillMaxWidth()) {
            LabelText("Theme")
            ChipRow {
                ThemeMode.entries.forEach { mode ->
                    ChoiceChip(
                        label = mode.label,
                        selected = settings.themeMode == mode,
                        onClick = { onUpdate { it.copy(themeMode = mode) } },
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            LabelText("Accent")
            ChipRow {
                AccentChoice.entries.forEach { accent ->
                    ChoiceChip(
                        label = accent.label,
                        selected = settings.accent == accent,
                        onClick = { onUpdate { it.copy(accent = accent) } },
                        leading = {
                            Box(
                                Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when (accent) {
                                            AccentChoice.LIME -> Color(0xFFC7F24E)
                                            AccentChoice.VIOLET -> Color(0xFF8B6BFF)
                                            AccentChoice.CYAN -> Color(0xFF37E0D8)
                                            AccentChoice.PINK -> Color(0xFFFF5FA2)
                                            AccentChoice.CUSTOM -> Color(settings.customAccentArgb)
                                        },
                                    ),
                            )
                        },
                    )
                }
            }
            if (settings.accent == AccentChoice.CUSTOM) {
                Spacer(Modifier.height(6.dp))
                ChipRow {
                    listOf(0xFFE2725B, 0xFF4C6FFF, 0xFF7BE495, 0xFFF2C14E, 0xFFB48CFF).forEach { value ->
                        ChoiceChip(
                            label = String.format("#%06X", value),
                            selected = settings.customAccentArgb.toLong() and 0xFFFFFFFFL == value,
                            onClick = { onUpdate { it.copy(customAccentArgb = value.toInt()) } },
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            LabelText("Corners")
            ChipRow {
                CornerStyle.entries.forEach { style ->
                    ChoiceChip(
                        label = style.label,
                        selected = settings.cornerStyle == style,
                        onClick = { onUpdate { it.copy(cornerStyle = style) } },
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            LabelText("Interface density")
            ChipRow {
                DensityLevel.entries.forEach { level ->
                    ChoiceChip(
                        label = level.label,
                        selected = settings.density == level,
                        onClick = { onUpdate { it.copy(density = level) } },
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            VelaSlider(
                label = "Font scale",
                value = settings.fontScale,
                valueRange = 0.85f..1.3f,
                valueText = "${(settings.fontScale * 100).roundToInt()}%",
                onValueChange = { value -> onUpdate { it.copy(fontScale = value) } },
            )
            VelaSwitchRow(
                title = "Reduced motion",
                subtitle = "Minimise panel and button animations",
                checked = settings.reducedMotion,
                onCheckedChange = { value -> onUpdate { it.copy(reducedMotion = value) } },
            )
            VelaSwitchRow(
                title = "Haptic feedback",
                subtitle = "Vibrate on gesture undo/redo and tool changes",
                checked = settings.haptics,
                onCheckedChange = { value -> onUpdate { it.copy(haptics = value) } },
            )
        }

        SectionTitle("Stylus & gestures")
        BentoCard(modifier = Modifier.fillMaxWidth()) {
            VelaSwitchRow(
                title = "Palm rejection",
                subtitle = "Ignore finger contact while the stylus is painting",
                checked = settings.palmRejection,
                onCheckedChange = { value -> onUpdate { it.copy(palmRejection = value) } },
            )
            VelaSwitchRow(
                title = "Draw with finger",
                subtitle = "Finger strokes when no stylus is nearby",
                checked = settings.drawWithFinger,
                onCheckedChange = { value -> onUpdate { it.copy(drawWithFinger = value) } },
            )
            VelaSwitchRow(
                title = "Stylus eraser switches to eraser",
                subtitle = "Use the eraser tip without changing tools",
                checked = settings.eraserSwitchesTool,
                onCheckedChange = { value -> onUpdate { it.copy(eraserSwitchesTool = value) } },
            )
            VelaSwitchRow(
                title = "Two-finger tap to undo",
                checked = settings.twoFingerUndo,
                onCheckedChange = { value -> onUpdate { it.copy(twoFingerUndo = value) } },
            )
            VelaSwitchRow(
                title = "Three-finger tap to redo",
                checked = settings.threeFingerRedo,
                onCheckedChange = { value -> onUpdate { it.copy(threeFingerRedo = value) } },
            )
            VelaSwitchRow(
                title = "Rotate canvas with two fingers",
                checked = settings.rotateGesture,
                onCheckedChange = { value -> onUpdate { it.copy(rotateGesture = value) } },
            )
        }

        SectionTitle("Canvas & saving")
        BentoCard(modifier = Modifier.fillMaxWidth()) {
            VelaSlider(
                label = "Autosave interval",
                value = settings.autosaveSeconds.toFloat(),
                valueRange = 2f..60f,
                valueText = "${settings.autosaveSeconds} s",
                onValueChange = { value -> onUpdate { it.copy(autosaveSeconds = value.roundToInt()) } },
            )
            VelaSlider(
                label = "Undo history budget",
                value = settings.historyBudgetMb.toFloat(),
                valueRange = 32f..512f,
                valueText = "${settings.historyBudgetMb} MB",
                onValueChange = { value -> onUpdate { it.copy(historyBudgetMb = value.roundToInt()) } },
            )
            VelaSwitchRow(
                title = "Transparency checkerboard",
                checked = settings.checkerboardBackground,
                onCheckedChange = { value -> onUpdate { it.copy(checkerboardBackground = value) } },
            )
            VelaSwitchRow(
                title = "Canvas shadow",
                checked = settings.showCanvasShadow,
                onCheckedChange = { value -> onUpdate { it.copy(showCanvasShadow = value) } },
            )
        }

        SectionTitle("Storage")
        BentoCard(modifier = Modifier.fillMaxWidth()) {
            LabelText("Projects and exports are stored in app-private storage. Nothing leaves the device.")
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    storageBytes?.let { formatBytes(it) } ?: "Tap to calculate",
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.cyan,
                )
                Spacer(Modifier.weight(1f))
                ChoiceChip(
                    label = "Measure",
                    selected = false,
                    onClick = {
                        scope.launch {
                            storageBytes = withContext(Dispatchers.IO) { container.projectRepository.storageUsageBytes() }
                        }
                    },
                )
                Spacer(Modifier.width(8.dp))
                ChoiceChip(
                    label = "Clean temp",
                    selected = false,
                    onClick = {
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                container.projectRepository.cleanupTemporaryFiles()
                                container.projectStorage.cleanupTemporaryFiles(olderThanMillis = 0)
                            }
                            storageBytes = withContext(Dispatchers.IO) { container.projectRepository.storageUsageBytes() }
                        }
                    },
                )
            }
        }

        SectionTitle("About")
        BentoCard(modifier = Modifier.fillMaxWidth()) {
            Text("Vela Studio", style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
            LabelText("Native Android painting studio — Jetpack Compose UI, custom raster engine.")
            Spacer(Modifier.height(6.dp))
            LabelText("Fully offline: the app requests no network permission.")
            Spacer(Modifier.height(6.dp))
            LabelText("Third-party components are listed in DEPENDENCIES.md.")
        }
        Spacer(Modifier.height(32.dp))
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    bytes < 1024L * 1024 * 1024 -> String.format(java.util.Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
    else -> String.format(java.util.Locale.US, "%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
}
