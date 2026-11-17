package com.example.movieboxexplorer.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.movieboxexplorer.data.CatalogGrouper
import com.example.movieboxexplorer.data.MovieBoxRepository
import com.example.movieboxexplorer.data.RankingCategory
import com.example.movieboxexplorer.data.SubjectBrief
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface RankingUiState {
    data object Loading : RankingUiState
    data class Ready(
        val categories: List<RankingCategory>,
        val items: List<SubjectBrief>,
        val loadingCategory: Boolean = false,
    ) : RankingUiState
    data class Error(val message: String) : RankingUiState
}

class RankingViewModel(private val repository: MovieBoxRepository) : ViewModel() {

    private val _state = MutableStateFlow<RankingUiState>(RankingUiState.Loading)
    val state: StateFlow<RankingUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.value = RankingUiState.Loading
            _state.value = try {
                val page = repository.rankingPage()
                RankingUiState.Ready(page.rankingList, CatalogGrouper.dedupeForDisplay(page.subjectList))
            } catch (e: Exception) {
                RankingUiState.Error(e.message ?: "Failed to load ranking")
            }
        }
    }

    fun select(category: RankingCategory) {
        val current = _state.value as? RankingUiState.Ready ?: return
        if (current.loadingCategory) return
        _state.value = current.copy(loadingCategory = true)
        viewModelScope.launch {
            val items = runCatching { repository.rankingContent(category.id) }
                .getOrDefault(current.items)
            _state.value = current.copy(items = CatalogGrouper.dedupeForDisplay(items), loadingCategory = false)
        }
    }

    companion object {
        fun factory(repository: MovieBoxRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { RankingViewModel(repository) }
        }
    }
}