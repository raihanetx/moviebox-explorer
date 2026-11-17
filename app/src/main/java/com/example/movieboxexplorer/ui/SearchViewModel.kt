package com.example.movieboxexplorer.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.movieboxexplorer.data.CatalogGrouper
import com.example.movieboxexplorer.data.EveryoneSearchPage
import com.example.movieboxexplorer.data.MovieBoxRepository
import com.example.movieboxexplorer.data.SearchCount
import com.example.movieboxexplorer.data.SubjectBrief
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface SearchUiState {
    data object Idle : SearchUiState
    data object Loading : SearchUiState
    data class Results(
        val items: List<SubjectBrief>,
        val keyword: String,
        val counts: List<SearchCount>,
        val hasMore: Boolean,
        val loadingMore: Boolean = false,
    ) : SearchUiState
    data class Error(val message: String) : SearchUiState
}

class SearchViewModel(private val repository: MovieBoxRepository) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _subjectType = MutableStateFlow(0)
    val subjectType: StateFlow<Int> = _subjectType.asStateFlow()

    private val _state = MutableStateFlow<SearchUiState>(SearchUiState.Idle)
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    private val _suggestions = MutableStateFlow<List<String>>(emptyList())
    val suggestions: StateFlow<List<String>> = _suggestions.asStateFlow()

    private val _everyone = MutableStateFlow(EveryoneSearchPage())
    val everyone: StateFlow<EveryoneSearchPage> = _everyone.asStateFlow()

    private val _recent = MutableStateFlow<List<String>>(emptyList())
    val recent: StateFlow<List<String>> = _recent.asStateFlow()

    private var searchJob: Job? = null
    private var suggestJob: Job? = null
    private var page = 1

    init {
        viewModelScope.launch {
            runCatching { repository.everyoneSearch() }
                .onSuccess { _everyone.value = it }
        }
    }

    fun onQueryChange(value: String) {
        _query.value = value
        searchJob?.cancel()
        suggestJob?.cancel()

        if (value.trim().length < 2) {
            _state.value = SearchUiState.Idle
            _suggestions.value = emptyList()
            return
        }

        suggestJob = viewModelScope.launch {
            delay(250)
            runCatching { repository.suggest(value.trim()) }
                .onSuccess { _suggestions.value = it }
        }

        searchJob = viewModelScope.launch {
            delay(350)
            runSearch(reset = true)
        }
    }

    /** Commit the current query (keyboard Search / suggestion / chip). */
    fun commitSearch() {
        val keyword = _query.value.trim()
        if (keyword.length < 2) return
        rememberRecent(keyword)
        suggestJob?.cancel()
        _suggestions.value = emptyList()
        searchJob?.cancel()
        searchJob = viewModelScope.launch { runSearch(reset = true) }
    }

    /** Search for a specific term (suggestion, trending chip, recent chip). */
    fun searchFor(value: String) {
        val keyword = value.trim()
        if (keyword.length < 2) return
        _query.value = keyword
        rememberRecent(keyword)
        suggestJob?.cancel()
        _suggestions.value = emptyList()
        searchJob?.cancel()
        searchJob = viewModelScope.launch { runSearch(reset = true) }
    }

    fun clearRecent() {
        _recent.value = emptyList()
    }

    private fun rememberRecent(keyword: String) {
        _recent.value = (listOf(keyword) + _recent.value.filter { it != keyword }).take(8)
    }

    fun onSubjectTypeChange(type: Int) {
        if (_subjectType.value == type) return
        _subjectType.value = type
        val keyword = _query.value.trim()
        if (keyword.length >= 2) {
            searchJob?.cancel()
            searchJob = viewModelScope.launch { runSearch(reset = true) }
        }
    }

    fun refresh() {
        val keyword = _query.value.trim()
        if (keyword.length < 2) return
        searchJob?.cancel()
        searchJob = viewModelScope.launch { runSearch(reset = true) }
    }

    fun loadMore() {
        val current = _state.value as? SearchUiState.Results ?: return
        if (!current.hasMore || current.loadingMore) return
        _state.value = current.copy(loadingMore = true)
        searchJob = viewModelScope.launch {
            page += 1
            runCatching {
                repository.searchPage(
                    keyword = current.keyword,
                    subjectType = _subjectType.value,
                    page = page,
                    perPage = 28,
                )
            }.onSuccess { result ->
                _state.value = current.copy(
                    items = current.items + result.items,
                    hasMore = result.pager?.hasMore == true,
                    loadingMore = false,
                )
            }.onFailure {
                _state.value = current.copy(loadingMore = false)
            }
        }
    }

    private suspend fun runSearch(reset: Boolean) {
        val keyword = _query.value.trim()
        if (reset) {
            page = 1
            _state.value = SearchUiState.Loading
        }
        val result = try {
            repository.searchPage(
                keyword = keyword,
                subjectType = _subjectType.value,
                page = page,
                perPage = if (reset) 24 else 28,
            )
        } catch (e: Exception) {
            _state.value = SearchUiState.Error(e.message ?: "Request failed")
            return
        }

        // The search API already returns the unlisted titles, so the server-rendered
        // fallback is only consulted when it did not.
        val highRisk = if (result.items.any { it.webHighRisk }) {
            emptyList()
        } else {
            runCatching { repository.highRiskSearch(keyword) }.getOrDefault(emptyList())
        }
        val merged = (result.items + highRisk)
            // H5 and mobile share the same `subjectId`; dedupe on it so the same
            // record is never shown twice (with and without a `detailPath`).
            .distinctBy { it.subjectId.ifBlank { it.detailPath } }

        _state.value = SearchUiState.Results(
            items = CatalogGrouper.dedupeForDisplay(merged),
            keyword = keyword,
            counts = result.counts,
            hasMore = result.pager?.hasMore == true,
        )
    }

    companion object {
        fun factory(repository: MovieBoxRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { SearchViewModel(repository) }
        }
    }
}