package com.example.movieboxexplorer.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.movieboxexplorer.data.CatalogGrouper
import com.example.movieboxexplorer.data.HomeCache
import com.example.movieboxexplorer.data.HomePage
import com.example.movieboxexplorer.data.MovieBoxRepository
import com.example.movieboxexplorer.data.SubjectBrief
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** How a home rail should be presented. */
enum class HomeRailKind {
    /** A normal content rail (Popular Series, Action, K-Drama, …). */
    STANDARD,

    /** Titles that are announced but not available yet. */
    COMING_SOON,
}

/** One horizontal rail of the home page, exactly as the website orders it. */
data class HomeRail(
    val title: String,
    val items: List<SubjectBrief>,
    val kind: HomeRailKind = HomeRailKind.STANDARD,
)

/** Everything the home page renders. */
data class HomeUiState(
    val loading: Boolean = true,
    val error: String? = null,
    /** True while cached content is being refreshed. */
    val refreshing: Boolean = false,
    /** The hero slides from the home payload's `BANNER` rows. */
    val banners: List<SubjectBrief> = emptyList(),
    /** The platforms the catalogue advertises on the home page. */
    val platforms: List<String> = emptyList(),
    /** Every non-empty content rail, in the website's own order. */
    val rails: List<HomeRail> = emptyList(),
) {
    val isEmpty: Boolean
        get() = !loading && error == null && banners.isEmpty() && rails.isEmpty()
}

/**
 * The home page.
 *
 * This is the real MovieBox home: the hero carousel from the payload's banner rows,
 * the platform strip, and every content rail the website lists — in the same order,
 * with the same rows. Empty duplicate rows are dropped, the hidden home row the
 * website removes is removed here too (in [MovieBoxRepository.homeRows]), and the
 * trailing `Suggestions` row is appended as the website does.
 */
class HomeViewModel(
    private val repository: MovieBoxRepository,
    private val host: String,
    private val cache: HomeCache,
) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val cached = withContext(Dispatchers.IO) { cache.load(host) }
            if (cached != null) {
                _state.value = cached.toUiState(refreshing = true)
            }
            refresh()
        }
    }

    fun refresh() {
        viewModelScope.launch {
            val hasContent = _state.value.banners.isNotEmpty() || _state.value.rails.isNotEmpty()
            _state.value = _state.value.copy(
                loading = !hasContent,
                refreshing = true,
                error = null,
            )
            _state.value = try {
                val page = repository.homeRows(host)
                withContext(Dispatchers.IO) { cache.save(host, page) }
                page.toUiState(refreshing = false)
            } catch (e: Exception) {
                _state.value.copy(
                    loading = false,
                    refreshing = false,
                    error = e.message ?: "Failed to load the home page",
                )
            }
        }
    }

    private fun HomePage.toUiState(refreshing: Boolean): HomeUiState {
        val banners = operatingList
            .filter { it.type == "BANNER" }
            .flatMap { section -> section.banner?.items.orEmpty() }
            .mapNotNull { item -> item.subject?.takeIf { it.detailPath.isNotBlank() } }
            .let { CatalogGrouper.dedupeForDisplay(it) }
            .distinctBy { it.subjectId }

        val rails = operatingList
            .filter { section ->
                section.type !in NON_RAIL_TYPES && section.subjects.isNotEmpty()
            }
            .map { section ->
                HomeRail(
                    title = section.title.ifBlank { "Featured" },
                    items = CatalogGrouper.dedupeForDisplay(section.subjects),
                    kind = if (section.type == "APPOINTMENT_LIST") {
                        HomeRailKind.COMING_SOON
                    } else {
                        HomeRailKind.STANDARD
                    },
                )
            }

        return HomeUiState(
            loading = false,
            error = null,
            refreshing = refreshing,
            banners = banners,
            platforms = platformList
                .map { it.name }
                .filter { it.isNotBlank() }
                .distinct(),
            rails = rails,
        )
    }

    companion object {
        /** Row types that are either the hero, a filter bar, or have no content. */
        private val NON_RAIL_TYPES = setOf("BANNER", "FILTER", "SPORT_LIVE")

        fun factory(
            repository: MovieBoxRepository,
            host: String,
            cache: HomeCache,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { HomeViewModel(repository, host, cache) }
        }
    }
}