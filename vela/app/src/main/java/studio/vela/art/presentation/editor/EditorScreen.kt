package studio.vela.art.presentation.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import studio.vela.art.app.AppContainer
import studio.vela.art.core.export.ExportFormat
import studio.vela.art.core.export.ExportRequest
import studio.vela.art.core.export.ExportResult
import studio.vela.art.data.settings.AppSettings
import studio.vela.art.data.settings.toInputPolicy
import studio.vela.art.drawing.document.ToolKind
import studio.vela.art.presentation.brushstudio.BrushStudioScreen
import studio.vela.art.presentation.colorstudio.ColorStudioScreen
import studio.vela.art.ui.components.ChoiceChip
import studio.vela.art.ui.components.ChipRow
import studio.vela.art.ui.components.LabelText
import studio.vela.art.ui.components.StatusPill
import studio.vela.art.ui.components.VelaIconButton
import studio.vela.art.ui.components.VelaIcons
import studio.vela.art.ui.components.VelaSlider
import studio.vela.art.ui.theme.VelaTheme
import kotlin.math.roundToInt

private enum class InspectorTab(val label: String) { BRUSH("Brush"), COLOR("Colour"), LAYERS("Layers") }

@Composable
fun EditorScreen(
    projectId: Long,
    container: AppContainer,
    settings: AppSettings,
    onBack: () -> Unit,
    onMessage: (String) -> Unit,
) {
    val viewModel: EditorViewModel = viewModel(
        key = "editor-$projectId",
        factory = EditorViewModel.Factory(container.projectRepository, container.settingsRepository, projectId),
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = VelaTheme.colors
    val dimens = VelaTheme.dimens
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var canvasView by remember { mutableStateOf<CanvasView?>(null) }
    var chromeVisible by remember { mutableStateOf(true) }
    var tab by remember { mutableStateOf(InspectorTab.BRUSH) }
    var panelVisible by remember { mutableStateOf(true) }
    var recentColors by remember { mutableStateOf(listOf(0xFF111111.toInt(), 0xFFFFFFFF.toInt(), 0xFFE5484D.toInt())) }
    var showExport by remember { mutableStateOf(false) }
    var showBrushStudio by remember { mutableStateOf(false) }
    var showColorStudio by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var exportBusy by remember { mutableStateOf(false) }

    // Persist when leaving the editor or backgrounding the app.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP || event == Lifecycle.Event.ON_PAUSE) viewModel.saveNow()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.saveNow()
        }
    }

    LaunchedEffect(state.message) {
        state.message?.let {
            onMessage(it)
            viewModel.removeMessage()
        }
    }
    LaunchedEffect(state.recoveryNote) {
        state.recoveryNote?.let {
            onMessage(it)
            viewModel.removeMessage()
        }
    }

    if (state.loading) {
        Box(Modifier.fillMaxSize().background(colors.ground), contentAlignment = Alignment.Center) {
            Text("Opening project…", color = colors.textSecondary)
        }
        return
    }
    val loadError = state.loadError
    if (loadError != null) {
        Box(Modifier.fillMaxSize().background(colors.ground), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Could not open project", style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
                Text(loadError, style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
                TextButton(onClick = onBack) { Text("Back to library") }
            }
        }
        return
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(colors.canvasGround)) {
        val compact = maxWidth < 760.dp

        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx -> CanvasView(ctx).also { canvasView = it } },
            update = { view ->
                if (view.boundControllerId() != viewModel.controller?.document?.projectId) {
                    view.bind(viewModel.controller)
                }
                view.applySettings(
                    inputPolicy = settings.toInputPolicy(),
                    checkerboard = settings.checkerboardBackground,
                    showCanvasShadow = settings.showCanvasShadow,
                    haptics = settings.haptics,
                )
                view.onViewportChanged = { zoom -> viewModel.setZoom(zoom) }
            },
        )

        if (chromeVisible) {
            EditorTopBar(
                state = state,
                modifier = Modifier.align(Alignment.TopCenter).padding(10.dp),
                onBack = onBack,
                onUndo = { viewModel.undo() },
                onRedo = { viewModel.redo() },
                onExport = { showExport = true },
                onMenu = { showMenu = true },
                onRename = { showRename = true },
                canUndo = state.canUndo,
                canRedo = state.canRedo,
            )
            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                DropdownMenuItem(text = { Text("Fit canvas") }, onClick = {
                    canvasView?.fitToScreen(); showMenu = false
                })
                DropdownMenuItem(text = { Text("Reset view") }, onClick = {
                    canvasView?.resetView(); showMenu = false
                })
                DropdownMenuItem(text = { Text("Save now") }, onClick = {
                    viewModel.saveNow(); showMenu = false
                })
                DropdownMenuItem(text = { Text(if (chromeVisible) "Hide panels" else "Show panels") }, onClick = {
                    chromeVisible = false; showMenu = false
                })
                DropdownMenuItem(text = { Text("Project info") }, onClick = {
                    onMessage("${state.canvasWidth}×${state.canvasHeight} px • ${state.canvasDpi} DPI • ${state.layers.size} layers")
                    showMenu = false
                })
            }

            if (!compact) {
                ToolRail(
                    modifier = Modifier.align(Alignment.CenterStart).padding(10.dp),
                    selected = state.tool,
                    onSelect = viewModel::selectTool,
                )
            }

            EditorBottomBar(
                state = state,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = if (compact) 74.dp else 14.dp, start = 14.dp, end = 14.dp),
                compact = compact,
                onSizeChange = { percent -> viewModel.updateBrush("Size") { it.copy(size = 1f + percent * 399f) } },
                onOpacityChange = { percent -> viewModel.updateBrush("Opacity") { it.copy(opacity = percent.coerceAtLeast(0.01f)) } },
                onColorClick = { tab = InspectorTab.COLOR; panelVisible = true },
                onZoomIn = { canvasView?.zoomBy(1.25f) },
                onZoomOut = { canvasView?.zoomBy(0.8f) },
                onFit = { canvasView?.fitToScreen() },
                onToggleFullscreen = { chromeVisible = false },
            )

            if (compact) {
                ToolRail(
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp),
                    selected = state.tool,
                    onSelect = viewModel::selectTool,
                    horizontal = true,
                )
            }

            if (panelVisible) {
                InspectorPanel(
                    modifier = if (compact) {
                        Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .fillMaxHeight(0.62f)
                    } else {
                        Modifier
                            .align(Alignment.CenterEnd)
                            .width(330.dp)
                            .fillMaxHeight(0.9f)
                            .padding(vertical = 8.dp)
                    },
                    state = state,
                    tab = tab,
                    onTabChange = { tab = it },
                    onClose = { panelVisible = false },
                    vm = viewModel,
                    recentColors = recentColors,
                    onPrimaryColor = { color ->
                        viewModel.setPrimaryColor(color)
                        recentColors = (listOf(color) + recentColors).distinct().take(12)
                    },
                    onOpenBrushStudio = { showBrushStudio = true },
                    onOpenColorStudio = { showColorStudio = true },
                )
            } else {
                VelaIconButton(
                    icon = VelaIcons.tune,
                    contentDescription = "Show panels",
                    onClick = { panelVisible = true },
                    modifier = Modifier.align(if (compact) Alignment.BottomEnd else Alignment.CenterEnd).padding(12.dp),
                )
            }
        } else {
            // Full-screen drawing: a single unobtrusive exit affordance.
            VelaIconButton(
                icon = VelaIcons.close,
                contentDescription = "Exit full screen",
                onClick = { chromeVisible = true },
                modifier = Modifier.align(Alignment.TopEnd).padding(10.dp),
            )
            Row(
                modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                VelaIconButton(VelaIcons.undo, "Undo", { viewModel.undo() }, enabled = state.canUndo)
                VelaIconButton(VelaIcons.redo, "Redo", { viewModel.redo() }, enabled = state.canRedo)
            }
        }
    }

    if (showBrushStudio) {
        BrushStudioScreen(
            initialBrush = state.brush,
            onApply = { preset ->
                viewModel.selectBrush(preset)
                showBrushStudio = false
            },
            onDismiss = { showBrushStudio = false },
        )
    }
    if (showColorStudio) {
        ColorStudioScreen(
            initialColor = state.primaryColor,
            onColorChange = { color ->
                viewModel.setPrimaryColor(color)
                recentColors = (listOf(color) + recentColors).distinct().take(12)
            },
            onDismiss = { showColorStudio = false },
        )
    }
    if (showExport) {
        ExportDialog(
            busy = exportBusy,
            canvasWidth = state.canvasWidth,
            canvasHeight = state.canvasHeight,
            onDismiss = { if (!exportBusy) showExport = false },
            onExport = { request, share ->
                val document = viewModel.document() ?: return@ExportDialog
                exportBusy = true
                scope.launch {
                    val result = withContext(Dispatchers.IO) { container.exporter.export(document, request) }
                    exportBusy = false
                    when (result) {
                        is ExportResult.Success -> {
                            showExport = false
                            if (share) {
                                context.startActivity(container.exporter.shareIntent(result.file, request.format))
                            } else {
                                onMessage("Exported ${result.file.name} (${result.bytes / 1024} KB)")
                            }
                        }

                        is ExportResult.Failure -> onMessage(result.reason)
                    }
                }
            },
        )
    }
    if (showRename) {
        RenameDialog(
            current = state.projectName,
            onDismiss = { showRename = false },
            onConfirm = { name ->
                viewModel.renameProject(name)
                showRename = false
            },
        )
    }
}

@Composable
private fun EditorTopBar(
    state: EditorUiState,
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onExport: () -> Unit,
    onMenu: () -> Unit,
    onRename: () -> Unit,
    canUndo: Boolean,
    canRedo: Boolean,
) {
    val colors = VelaTheme.colors
    val dimens = VelaTheme.dimens
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(dimens.cornerLarge))
            .background(colors.surface.copy(alpha = 0.96f))
            .border(1.dp, colors.border, RoundedCornerShape(dimens.cornerLarge))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        VelaIconButton(VelaIcons.back, "Back", onBack)
        Column(
            modifier = Modifier
                .width(150.dp)
                .clickable(onClick = onRename)
                .padding(horizontal = 8.dp),
        ) {
            Text(
                text = state.projectName,
                style = MaterialTheme.typography.titleMedium,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            LabelText("${state.canvasWidth}×${state.canvasHeight} • ${saveLabel(state)}")
        }
        Spacer(Modifier.width(4.dp))
        VelaIconButton(VelaIcons.undo, "Undo", onUndo, enabled = canUndo)
        VelaIconButton(VelaIcons.redo, "Redo", onRedo, enabled = canRedo)
        Spacer(Modifier.width(4.dp))
        StatusPill(
            text = "${state.zoomPercent.roundToInt()}%",
            color = colors.cyan,
        )
        VelaIconButton(VelaIcons.share, "Export", onExport)
        VelaIconButton(VelaIcons.more, "More", onMenu)
    }
}

private fun saveLabel(state: EditorUiState): String = when (state.saveState) {
    SaveState.SAVED -> "Saved"
    SaveState.SAVING -> "Saving…"
    SaveState.UNSAVED -> "Unsaved"
    SaveState.ERROR -> "Save failed"
}

@Composable
private fun ToolRail(
    modifier: Modifier = Modifier,
    selected: ToolKind,
    onSelect: (ToolKind) -> Unit,
    horizontal: Boolean = false,
) {
    val colors = VelaTheme.colors
    val dimens = VelaTheme.dimens
    val tools = listOf(
        ToolKind.BRUSH to VelaIcons.brush,
        ToolKind.PENCIL to VelaIcons.pencil,
        ToolKind.PEN to VelaIcons.pen,
        ToolKind.ERASER to VelaIcons.eraser,
        ToolKind.SMUDGE to VelaIcons.smudge,
        ToolKind.FILL to VelaIcons.fill,
        ToolKind.SHAPE to VelaIcons.shape,
        ToolKind.EYEDROPPER to VelaIcons.eyedropper,
    )
    val content: @Composable () -> Unit = {
        tools.forEach { (tool, icon) ->
            VelaIconButton(
                icon = icon,
                contentDescription = tool.label,
                onClick = { onSelect(tool) },
                selected = selected == tool,
            )
        }
    }
    if (horizontal) {
        Row(
            modifier = modifier
                .clip(RoundedCornerShape(dimens.cornerLarge))
                .background(colors.surface.copy(alpha = 0.96f))
                .border(1.dp, colors.border, RoundedCornerShape(dimens.cornerLarge))
                .padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) { content() }
    } else {
        Column(
            modifier = modifier
                .clip(RoundedCornerShape(dimens.cornerLarge))
                .background(colors.surface.copy(alpha = 0.96f))
                .border(1.dp, colors.border, RoundedCornerShape(dimens.cornerLarge))
                .padding(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) { content() }
    }
}

@Composable
private fun EditorBottomBar(
    state: EditorUiState,
    modifier: Modifier = Modifier,
    compact: Boolean,
    onSizeChange: (Float) -> Unit,
    onOpacityChange: (Float) -> Unit,
    onColorClick: () -> Unit,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onFit: () -> Unit,
    onToggleFullscreen: () -> Unit,
) {
    val colors = VelaTheme.colors
    val dimens = VelaTheme.dimens
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(dimens.cornerLarge))
            .background(colors.surface.copy(alpha = 0.96f))
            .border(1.dp, colors.border, RoundedCornerShape(dimens.cornerLarge))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(state.primaryColor))
                .border(2.dp, colors.border, RoundedCornerShape(12.dp))
                .clickable(onClick = onColorClick),
        )
        Column(modifier = Modifier.width(if (compact) 110.dp else 170.dp)) {
            VelaSlider(
                label = "Size",
                value = (state.brush.size - 1f) / 399f,
                valueText = "${state.brush.size.roundToInt()}",
                onValueChange = onSizeChange,
            )
        }
        Column(modifier = Modifier.width(if (compact) 110.dp else 150.dp)) {
            VelaSlider(
                label = "Opacity",
                value = state.brush.opacity,
                valueText = "${(state.brush.opacity * 100).roundToInt()}%",
                onValueChange = onOpacityChange,
            )
        }
        if (!compact) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                VelaIconButton(VelaIcons.zoomOut, "Zoom out", onZoomOut)
                VelaIconButton(VelaIcons.zoomIn, "Zoom in", onZoomIn)
                VelaIconButton(VelaIcons.grid, "Fit", onFit)
            }
        }
        VelaIconButton(VelaIcons.tune, "Full screen", onToggleFullscreen)
    }
}

@Composable
private fun InspectorPanel(
    state: EditorUiState,
    tab: InspectorTab,
    onTabChange: (InspectorTab) -> Unit,
    onClose: () -> Unit,
    vm: EditorViewModel,
    recentColors: List<Int>,
    onPrimaryColor: (Int) -> Unit,
    onOpenBrushStudio: () -> Unit,
    onOpenColorStudio: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = VelaTheme.colors
    val dimens = VelaTheme.dimens
    Column(
        modifier = modifier
            .padding(8.dp)
            .clip(RoundedCornerShape(dimens.cornerLarge))
            .background(colors.surface.copy(alpha = 0.98f))
            .border(1.dp, colors.border, RoundedCornerShape(dimens.cornerLarge)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ChipRow(Modifier.weight(1f)) {
                InspectorTab.entries.forEach { entry ->
                    ChoiceChip(
                        label = entry.label,
                        selected = entry == tab,
                        onClick = { onTabChange(entry) },
                    )
                }
            }
            VelaIconButton(VelaIcons.close, "Hide panels", onClose)
        }
        Box(Modifier.fillMaxWidth().weight(1f)) {
            when (tab) {
                InspectorTab.BRUSH -> BrushPanel(
                    state = state,
                    onSelectBrush = vm::selectBrush,
                    onUpdateBrush = vm::updateBrush,
                    onOpenBrushStudio = onOpenBrushStudio,
                )

                InspectorTab.COLOR -> ColorPanel(
                    state = state,
                    onPrimaryColor = onPrimaryColor,
                    onSecondaryColor = vm::setSecondaryColor,
                    onSwap = vm::swapColors,
                    onOpenColorStudio = onOpenColorStudio,
                    recentColors = recentColors,
                )

                InspectorTab.LAYERS -> LayersPanel(
                    state = state,
                    onAdd = { vm.addLayer() },
                    onDuplicate = { vm.duplicateLayer() },
                    onDelete = { vm.deleteLayer() },
                    onMerge = { vm.mergeLayerDown() },
                    onClear = { vm.clearLayer() },
                    onSelect = vm::selectLayer,
                    onMoveUp = { vm.moveLayer(true) },
                    onMoveDown = { vm.moveLayer(false) },
                    onRename = vm::renameLayer,
                    onCaptureProps = vm::captureLayerProps,
                    onUpdateProps = vm::updateLayerProps,
                    onCommitProps = vm::commitLayerProps,
                    onSetBlend = vm::setLayerBlend,
                )
            }
        }
    }
}

@Composable
private fun ExportDialog(
    busy: Boolean,
    canvasWidth: Int,
    canvasHeight: Int,
    onDismiss: () -> Unit,
    onExport: (ExportRequest, Boolean) -> Unit,
) {
    val colors = VelaTheme.colors
    var format by remember { mutableStateOf(ExportFormat.PNG) }
    var quality by remember { mutableStateOf(95f) }
    var scale by remember { mutableStateOf(1f) }
    var includeBackground by remember { mutableStateOf(true) }

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(24.dp))
                .background(colors.surface)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Export artwork", style = MaterialTheme.typography.titleLarge, color = colors.textPrimary)
            LabelText("Output ${(canvasWidth * scale).roundToInt()} × ${(canvasHeight * scale).roundToInt()} px")
            ChipRow {
                ExportFormat.entries.forEach { option ->
                    ChoiceChip(
                        label = option.label,
                        selected = option == format,
                        onClick = { format = option },
                    )
                }
            }
            ChipRow {
                listOf(1f, 0.5f, 0.25f).forEach { option ->
                    ChoiceChip(
                        label = "${(option * 100).roundToInt()}%",
                        selected = scale == option,
                        onClick = { scale = option },
                    )
                }
            }
            if (format.supportsQuality) {
                VelaSlider(
                    label = "Quality",
                    value = quality,
                    valueRange = 40f..100f,
                    valueText = quality.roundToInt().toString(),
                    onValueChange = { quality = it },
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ChoiceChip(
                    label = if (includeBackground) "With background" else "Transparent",
                    selected = includeBackground,
                    onClick = { includeBackground = !includeBackground },
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancel") }
                TextButton(
                    onClick = { onExport(ExportRequest(format, quality.roundToInt(), includeBackground, scale), false) },
                    enabled = !busy,
                ) { Text("Save") }
                TextButton(
                    onClick = { onExport(ExportRequest(format, quality.roundToInt(), includeBackground, scale), true) },
                    enabled = !busy,
                ) { Text(if (busy) "Exporting…" else "Share") }
            }
        }
    }
}

@Composable
private fun RenameDialog(current: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    val colors = VelaTheme.colors
    var text by remember { mutableStateOf(current) }
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(24.dp))
                .background(colors.surface)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Project name", style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
            OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                TextButton(onClick = { onConfirm(text) }) { Text("Rename") }
            }
        }
    }
}
