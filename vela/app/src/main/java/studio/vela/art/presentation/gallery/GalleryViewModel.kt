package studio.vela.art.presentation.gallery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import studio.vela.art.data.repository.ProjectRepository
import studio.vela.art.data.repository.ProjectSummary
import studio.vela.art.data.storage.ProjectStorage

enum class SortMode(val label: String) {
    UPDATED("Last edited"),
    CREATED("Newest"),
    NAME("Name"),
    SIZE("Canvas size"),
}

data class GalleryUiState(
    val loading: Boolean = true,
    val allProjects: List<ProjectSummary> = emptyList(),
    val visibleProjects: List<ProjectSummary> = emptyList(),
    val favorites: List<ProjectSummary> = emptyList(),
    val collections: List<String> = emptyList(),
    val totalPixels: Long = 0,
    val storageBytes: Long = 0,
)

class GalleryViewModel(
    private val repository: ProjectRepository,
    private val storage: ProjectStorage,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val sortMode = MutableStateFlow(SortMode.UPDATED)
    private val favoritesOnly = MutableStateFlow(false)
    private val collectionFilter = MutableStateFlow<String?>(null)
    private val storageBytes = MutableStateFlow(0L)

    val filters: StateFlow<GalleryFilters> = combine(query, sortMode, favoritesOnly, collectionFilter) { q, s, f, c ->
        GalleryFilters(q, s, f, c)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, GalleryFilters())

    val state: StateFlow<GalleryUiState> = combine(
        repository.observeProjects(),
        query,
        sortMode,
        favoritesOnly,
        collectionFilter,
    ) { projects, q, sort, favOnly, collection ->
        val filtered = projects.asSequence()
            .filter { !favOnly || it.favorite }
            .filter { collection == null || it.collection == collection }
            .filter { q.isBlank() || it.name.contains(q, ignoreCase = true) }
            .toList()
        val sorted = when (sort) {
            SortMode.UPDATED -> filtered.sortedByDescending { it.updatedAt }
            SortMode.CREATED -> filtered.sortedByDescending { it.createdAt }
            SortMode.NAME -> filtered.sortedBy { it.name.lowercase() }
            SortMode.SIZE -> filtered.sortedByDescending { it.width.toLong() * it.height }
        }
        Quintuplet(
            projects = sorted,
            all = projects,
            query = q,
            sort = sort,
            favOnly = favOnly,
        )
    }.combine(storageBytes) { computed, bytes ->
        GalleryUiState(
            loading = false,
            allProjects = computed.all,
            visibleProjects = computed.projects,
            favorites = computed.all.filter { it.favorite },
            collections = computed.all.mapNotNull { it.collection }.distinct().sorted(),
            totalPixels = computed.all.sumOf { it.width.toLong() * it.height },
            storageBytes = bytes,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GalleryUiState())

    private data class Quintuplet(
        val projects: List<ProjectSummary>,
        val all: List<ProjectSummary>,
        val query: String,
        val sort: SortMode,
        val favOnly: Boolean,
    )

    init {
        refreshStorage()
    }

    fun setQuery(value: String) {
        query.value = value
    }

    fun setSort(mode: SortMode) {
        sortMode.value = mode
    }

    fun setFavoritesOnly(enabled: Boolean) {
        favoritesOnly.value = enabled
    }

    fun setCollection(name: String?) {
        collectionFilter.value = name
    }

    fun toggleFavorite(project: ProjectSummary) {
        viewModelScope.launch { repository.setFavorite(project.id, !project.favorite) }
    }

    fun delete(project: ProjectSummary) {
        viewModelScope.launch {
            repository.deleteProject(project.id)
            refreshStorage()
        }
    }

    fun duplicate(project: ProjectSummary, onDone: (Long?) -> Unit) {
        viewModelScope.launch {
            val id = repository.duplicateProject(project.id)
            refreshStorage()
            onDone(id)
        }
    }

    fun rename(project: ProjectSummary, name: String) {
        viewModelScope.launch { repository.renameProject(project.id, name) }
    }

    private fun refreshStorage() {
        viewModelScope.launch { storageBytes.value = storage.storageUsageBytes() }
    }

    class Factory(
        private val repository: ProjectRepository,
        private val storage: ProjectStorage,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = GalleryViewModel(repository, storage) as T
    }
}

data class GalleryFilters(
    val query: String = "",
    val sortMode: SortMode = SortMode.UPDATED,
    val favoritesOnly: Boolean = false,
    val collection: String? = null,
)
