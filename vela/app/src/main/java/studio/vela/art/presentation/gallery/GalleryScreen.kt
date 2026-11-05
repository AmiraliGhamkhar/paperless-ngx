package studio.vela.art.presentation.gallery

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import studio.vela.art.app.AppContainer
import studio.vela.art.data.repository.ProjectSummary
import studio.vela.art.ui.components.BentoCard
import studio.vela.art.ui.components.ChoiceChip
import studio.vela.art.ui.components.ChipRow
import studio.vela.art.ui.components.EmptyState
import studio.vela.art.ui.components.LabelText
import studio.vela.art.ui.components.SectionTitle
import studio.vela.art.ui.components.VelaIconButton
import studio.vela.art.ui.components.VelaIcons
import studio.vela.art.ui.theme.VelaTheme
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Creative home: a Bento dashboard with recent work, library statistics and the full project
 * grid. This is deliberately a dashboard, unlike the editor.
 */
@Composable
fun GalleryScreen(
    container: AppContainer,
    onNewCanvas: () -> Unit,
    onOpenProject: (Long) -> Unit,
    onOpenSettings: () -> Unit,
    onMessage: (String) -> Unit,
) {
    val viewModel: GalleryViewModel = viewModel(
        factory = GalleryViewModel.Factory(container.projectRepository, container.projectStorage),
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val filters by viewModel.filters.collectAsStateWithLifecycle()
    val colors = VelaTheme.colors
    val dimens = VelaTheme.dimens

    var searchOpen by remember { mutableStateOf(false) }
    var menuProject by remember { mutableStateOf<ProjectSummary?>(null) }
    var renamingProject by remember { mutableStateOf<ProjectSummary?>(null) }
    var sortMenu by remember { mutableStateOf(false) }

    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(colors.ground)) {
        val columns = when {
            maxWidth < 600.dp -> 2
            maxWidth < 1000.dp -> 3
            else -> 4
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = dimens.pagePadding,
                end = dimens.pagePadding,
                top = dimens.pagePadding + 12.dp,
                bottom = 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(dimens.gap * 1.4f),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Vela Studio",
                            style = MaterialTheme.typography.displaySmall,
                            color = colors.textPrimary,
                        )
                        LabelText("Professional digital painting")
                    }
                    VelaIconButton(
                        icon = VelaIcons.search,
                        contentDescription = "Search projects",
                        onClick = { searchOpen = !searchOpen },
                        containerColor = colors.surface,
                    )
                    Spacer(Modifier.width(8.dp))
                    VelaIconButton(
                        icon = VelaIcons.settings,
                        contentDescription = "Settings",
                        onClick = onOpenSettings,
                        containerColor = colors.surface,
                    )
                }
            }

            if (searchOpen) {
                item {
                    OutlinedTextField(
                        value = filters.query,
                        onValueChange = viewModel::setQuery,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("Search projects") },
                        keyboardOptions = KeyboardOptions.Default,
                        leadingIcon = { Icon(VelaIcons.search, contentDescription = null) },
                    )
                }
            }

            // Bento hero row: create + statistics
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(dimens.gap)) {
                    BentoCard(
                        modifier = Modifier.weight(1.6f).height(150.dp),
                        onClick = onNewCanvas,
                        highlight = true,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(colors.accent),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(VelaIcons.add, contentDescription = null, tint = colors.onAccent)
                            }
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text("New canvas", style = MaterialTheme.typography.titleLarge, color = colors.textPrimary)
                                LabelText("Start a fresh artwork")
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("A4", "Square", "Screen", "Webtoon").forEach { preset ->
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(colors.surface3)
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                ) {
                                    Text(preset, style = MaterialTheme.typography.labelSmall, color = colors.textSecondary)
                                }
                            }
                        }
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(dimens.gap),
                    ) {
                        StatCard("Projects", state.allProjects.size.toString(), colors.cyan)
                        StatCard("Storage", formatBytes(state.storageBytes), colors.pink)
                    }
                }
            }

            if (state.allProjects.isEmpty() && !state.loading) {
                item {
                    BentoCard(modifier = Modifier.fillMaxWidth()) {
                        EmptyState(
                            icon = VelaIcons.image,
                            title = "Your studio is empty",
                            message = "Create your first canvas to start painting. Everything is stored on this device.",
                            action = {
                                ChoiceChip(label = "New canvas", selected = true, onClick = onNewCanvas)
                            },
                        )
                    }
                }
            }

            if (state.favorites.isNotEmpty() && !filters.favoritesOnly) {
                item {
                    SectionTitle("Favourites") {
                        Text(
                            "${state.favorites.size}",
                            style = MaterialTheme.typography.labelLarge,
                            color = colors.textSecondary,
                        )
                    }
                }
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(dimens.gap)) {
                        items(state.favorites, key = { it.id }) { project ->
                            ProjectCard(
                                project = project,
                                modifier = Modifier.width(150.dp),
                                onClick = { onOpenProject(project.id) },
                                onMenu = { menuProject = project },
                            )
                        }
                    }
                }
            }

            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionTitle("Library")
                    Spacer(Modifier.weight(1f))
                    Box {
                        ChoiceChip(
                            label = filters.sortMode.label,
                            selected = false,
                            onClick = { sortMenu = true },
                        )
                        DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                            SortMode.entries.forEach { mode ->
                                DropdownMenuItem(
                                    text = { Text(mode.label) },
                                    onClick = {
                                        viewModel.setSort(mode)
                                        sortMenu = false
                                    },
                                )
                            }
                        }
                    }
                }
            }

            item {
                ChipRow {
                    ChoiceChip(
                        label = "All",
                        selected = !filters.favoritesOnly && filters.collection == null,
                        onClick = {
                            viewModel.setFavoritesOnly(false)
                            viewModel.setCollection(null)
                        },
                    )
                    ChoiceChip(
                        label = "Favourites",
                        selected = filters.favoritesOnly,
                        onClick = { viewModel.setFavoritesOnly(!filters.favoritesOnly) },
                    )
                    state.collections.forEach { collection ->
                        ChoiceChip(
                            label = collection,
                            selected = filters.collection == collection,
                            onClick = {
                                viewModel.setCollection(if (filters.collection == collection) null else collection)
                            },
                        )
                    }
                }
            }

            if (state.visibleProjects.isEmpty() && state.allProjects.isNotEmpty()) {
                item {
                    BentoCard(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            "No projects match this filter",
                            style = MaterialTheme.typography.bodyLarge,
                            color = colors.textSecondary,
                        )
                    }
                }
            }

            val rows = state.visibleProjects.chunked(columns)
            items(rows.size) { rowIndex ->
                Row(horizontalArrangement = Arrangement.spacedBy(dimens.gap)) {
                    rows[rowIndex].forEach { project ->
                        Box(Modifier.weight(1f)) {
                            ProjectCard(
                                project = project,
                                onClick = { onOpenProject(project.id) },
                                onMenu = { menuProject = project },
                            )
                        }
                    }
                    repeat(columns - rows[rowIndex].size) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }

    val project = menuProject
    if (project != null) {
        DropdownMenu(expanded = true, onDismissRequest = { menuProject = null }) {
            DropdownMenuItem(text = { Text(if (project.favorite) "Remove favourite" else "Add favourite") }, onClick = {
                viewModel.toggleFavorite(project)
                menuProject = null
            })
            DropdownMenuItem(text = { Text("Rename") }, onClick = {
                renamingProject = project
                menuProject = null
            })
            DropdownMenuItem(text = { Text("Duplicate") }, onClick = {
                menuProject = null
                viewModel.duplicate(project) { newId ->
                    if (newId != null) onMessage("Project duplicated") else onMessage("Could not duplicate project")
                }
            })
            DropdownMenuItem(text = { Text("Delete") }, onClick = {
                menuProject = null
                viewModel.delete(project)
                onMessage("Project deleted")
            })
        }
    }

    val renameTarget = renamingProject
    if (renameTarget != null) {
        var text by remember(renameTarget.id) { mutableStateOf(renameTarget.name) }
        androidx.compose.ui.window.Dialog(onDismissRequest = { renamingProject = null }) {
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(colors.surface)
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Rename project", style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
                OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    ChoiceChip(label = "Cancel", selected = false, onClick = { renamingProject = null })
                    Spacer(Modifier.width(8.dp))
                    ChoiceChip(
                        label = "Save",
                        selected = true,
                        onClick = {
                            viewModel.rename(renameTarget, text)
                            renamingProject = null
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, accent: Color) {
    val colors = VelaTheme.colors
    BentoCard(modifier = Modifier.fillMaxWidth()) {
        LabelText(label)
        Spacer(Modifier.height(2.dp))
        Text(value, style = MaterialTheme.typography.headlineSmall, color = accent)
    }
}

@Composable
private fun ProjectCard(
    project: ProjectSummary,
    onClick: () -> Unit,
    onMenu: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = VelaTheme.colors
    val dimens = VelaTheme.dimens
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(dimens.cornerLarge))
            .background(colors.surface)
            .border(1.dp, colors.border, RoundedCornerShape(dimens.cornerLarge))
            .clickable(onClick = onClick)
            .padding(8.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.25f)
                .clip(RoundedCornerShape(dimens.cornerMedium))
                .background(colors.surface3),
        ) {
            val thumbnail = project.thumbnailPath
            if (thumbnail != null && File(thumbnail).exists()) {
                AsyncImage(
                    model = File(thumbnail),
                    contentDescription = project.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(VelaIcons.image, contentDescription = null, tint = colors.textSecondary)
                }
            }
            if (project.favorite) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(24.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(colors.accent.copy(alpha = 0.9f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(VelaIcons.star, contentDescription = "Favourite", tint = colors.onAccent, modifier = Modifier.size(14.dp))
                }
            }
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .size(26.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.surface.copy(alpha = 0.8f))
                    .clickable(onClick = onMenu),
                contentAlignment = Alignment.Center,
            ) {
                Icon(VelaIcons.more, contentDescription = "Project options", tint = colors.textPrimary, modifier = Modifier.size(15.dp))
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            project.name,
            style = MaterialTheme.typography.bodyLarge,
            color = colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        LabelText("${project.width}×${project.height} • ${formatDate(project.updatedAt)}")
    }
}

private fun formatDate(millis: Long): String {
    if (millis <= 0L) return "—"
    val now = System.currentTimeMillis()
    val diff = now - millis
    return when {
        diff < 60_000 -> "just now"
        diff < 3_600_000 -> "${diff / 60_000} min ago"
        diff < 86_400_000 -> "${diff / 3_600_000} h ago"
        else -> SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(millis))
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    bytes < 1024L * 1024 * 1024 -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
    else -> String.format(Locale.US, "%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
}
