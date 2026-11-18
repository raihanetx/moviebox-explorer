package com.example.movieboxexplorer.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.movieboxexplorer.data.CatalogGrouper
import com.example.movieboxexplorer.data.CatalogContent
import com.example.movieboxexplorer.data.CatalogFilter
import com.example.movieboxexplorer.data.CatalogGrid
import com.example.movieboxexplorer.data.FilterGroup
import com.example.movieboxexplorer.data.GridCache
import com.example.movieboxexplorer.data.MovieBoxRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class CatalogKind { MOVIES, ANIMATED, TV }

sealed interface CatalogUiState {
    data object Loading : CatalogUiState

    data class Ready(
        val content: CatalogContent,
        val filters: List<FilterGroup> = emptyList(),
        val filter: CatalogFilter = CatalogFilter(),
        val loadingMore: Boolean = false,
        val filtering: Boolean = false,
    ) : CatalogUiState

    data class Error(val message: String) : CatalogUiState
}

class CatalogViewModel(
    private val repository: MovieBoxRepository,
    private val host: String,
    private val kind: CatalogKind,
    private val cache: GridCache? = null,
) : ViewModel() {

    private val _state = MutableStateFlow<CatalogUiState>(CatalogUiState.Loading)
    val state: StateFlow<CatalogUiState> = _state.asStateFlow()

    private val _filter = MutableStateFlow(CatalogFilter())

    /** The filter bar payload is parsed once per screen — the website never changes it. */
    private var filterConfigCache: List<FilterGroup>? = null

    init {
        // Stale-while-revalidate: show the cached grid instantly, refresh behind it.
        // The cache lives on disk and parsing it is CPU + I/O work — never do it
        // on the main thread.
        viewModelScope.launch(Dispatchers.IO) {
            val cached = cache?.load(gridCacheKey(), CACHE_TTL_MILLIS)
            if (cached != null) {
                _state.value = CatalogUiState.Ready(
                    content = CatalogContent.Grid(cached),
                    filters = emptyList(),
                    filter = _filter.value,
                )
            }
        }
        load()
    }

    fun load() {
        viewModelScope.launch {
            val hadContent = _state.value as? CatalogUiState.Ready
            val currentFilters = filterConfigCache.orEmpty()
            if (hadContent == null) {
                _state.value = CatalogUiState.Loading
            } else {
                _state.value = hadContent.copy(filtering = true)
            }
            _state.value = try {
                val content = buildContent(1, _filter.value)
                val filters = currentFilters.ifEmpty {
                    gridFilterConfig().also { filterConfigCache = it }
                }
                (content.gridOrNull())?.let { grid ->
                    withContext(Dispatchers.IO) { cache?.save(gridCacheKey(), grid) }
                }
                CatalogUiState.Ready(
                    content = content,
                    filters = filters,
                    filter = _filter.value,
                    filtering = false,
                )
            } catch (e: Exception) {
                if (hadContent != null) {
                    // Keep showing the stale page; refresh failed quietly.
                    _state.value = hadContent.copy(filtering = false)
                    return@launch
                }
                CatalogUiState.Error(e.message ?: "Failed to load catalogue")
            }
        }
    }

    private fun CatalogContent.gridOrNull(): CatalogGrid? = (this as? CatalogContent.Grid)?.grid

    private fun gridCacheKey(): String {
        val f = _filter.value
        return cacheKeyFor(kind, host, f)
    }

    /** Loads the next page of a grid catalog, the way the website's "load more" does. */
    fun loadMore() {
        val current = _state.value as? CatalogUiState.Ready ?: return
        val content = current.content as? CatalogContent.Grid ?: return
        if (!content.grid.hasMore || current.loadingMore) return

        _state.value = current.copy(loadingMore = true)
        viewModelScope.launch {
            val next = fetchGrid(content.grid.nextPage, _filter.value)
            _state.value = CatalogUiState.Ready(
                content = CatalogContent.Grid(
                    CatalogGrid(
                        items = CatalogGrouper.dedupeForDisplay(content.grid.items + next.items),
                        hasMore = next.hasMore,
                        nextPage = next.nextPage,
                    )
                ),
                filters = current.filters,
                filter = current.filter,
            )
        }
    }

    /** Applies one filter group and reloads the grid from page 1. */
    fun selectFilter(type: String, value: String?) {
        _filter.value = when (type) {
            "genre" -> _filter.value.copy(genre = value)
            "country" -> _filter.value.copy(country = value)
            "year" -> _filter.value.copy(year = value)
            "sort" -> _filter.value.copy(sort = value)
            "rate" -> _filter.value.copy(rate = value)
            "classify" -> _filter.value.copy(classify = value)
            else -> _filter.value
        }
        load()
    }

    private suspend fun buildContent(page: Int, filter: CatalogFilter): CatalogContent =
        when (kind) {
            // The movies tab is the website's "all films" browser — one flat poster
            // grid with the site's own filter bar, not the trending rows.
            CatalogKind.MOVIES ->
                CatalogContent.Grid(repository.filmGrid(host, page, filter).deduped())
            // The TV and animation catalogs are flat poster grids on the website.
            CatalogKind.ANIMATED ->
                CatalogContent.Grid(fetchGrid(page, filter).deduped())
            CatalogKind.TV ->
                CatalogContent.Grid(fetchGrid(page, filter).deduped())
        }

    private fun CatalogGrid.deduped(): CatalogGrid =
        copy(items = CatalogGrouper.dedupeForDisplay(items))

    private suspend fun fetchGrid(page: Int, filter: CatalogFilter): CatalogGrid =
        when (kind) {
            CatalogKind.ANIMATED -> repository.animatedGrid(host, page, filter)
            CatalogKind.MOVIES -> repository.filmGrid(host, page, filter)
            else -> repository.seriesGrid(host, page, filter)
        }

    private suspend fun gridFilterConfig(): List<FilterGroup> {
        val path = when (kind) {
            CatalogKind.TV -> "/web/tv-series"
            CatalogKind.ANIMATED -> "/web/animated-series"
            CatalogKind.MOVIES -> "/web/film"
        }
        val configs = runCatching { repository.filterConfig(path) }.getOrNull().orEmpty()
        return configs.firstOrNull()?.items.orEmpty()
    }

    companion object {
        private const val CACHE_TTL_MILLIS = 10 * 60 * 1000L

        fun cacheKeyFor(
            kind: CatalogKind,
            host: String,
            filter: CatalogFilter = CatalogFilter(),
        ): String = listOf(
            "grid", host, kind.name,
            filter.genre.orEmpty(), filter.country.orEmpty(), filter.year.orEmpty(),
            filter.sort.orEmpty(), filter.rate.orEmpty(), filter.classify.orEmpty(),
        ).joinToString("|")

        fun factory(
            repository: MovieBoxRepository,
            host: String,
            kind: CatalogKind,
            cache: GridCache? = null,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { CatalogViewModel(repository, host, kind, cache) }
        }
    }
}
