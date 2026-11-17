package com.example.movieboxexplorer.ui
import com.example.movieboxexplorer.data.diagnostics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.movieboxexplorer.data.AppSettings
import com.example.movieboxexplorer.data.DiagnosticsReport
import com.example.movieboxexplorer.data.HomeCache
import com.example.movieboxexplorer.data.MovieBoxRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface DiagnosticsUiState {
    data object Idle : DiagnosticsUiState
    data object Running : DiagnosticsUiState
    data class Done(val report: DiagnosticsReport) : DiagnosticsUiState
    data class Error(val message: String) : DiagnosticsUiState
}

class DiagnosticsViewModel(
    private val repository: MovieBoxRepository,
    private val settings: AppSettings,
    private val cache: HomeCache,
) : ViewModel() {

    private val _state = MutableStateFlow<DiagnosticsUiState>(DiagnosticsUiState.Idle)
    val state: StateFlow<DiagnosticsUiState> = _state.asStateFlow()

    fun run() {
        viewModelScope.launch {
            _state.value = DiagnosticsUiState.Running
            _state.value = try {
                val report = repository.diagnostics(
                    host = settings.siteHost,
                    appVersion = "1.0",
                    apiBase = settings.apiBase,
                    siteHost = settings.siteHost,
                    cacheBytes = withContext(Dispatchers.IO) { cache.size() },
                )
                DiagnosticsUiState.Done(report)
            } catch (e: Exception) {
                DiagnosticsUiState.Error(e.message ?: "Diagnostics failed")
            }
        }
    }

    fun clearCache() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { cache.clear() }
            run()
        }
    }

    companion object {
        fun factory(
            repository: MovieBoxRepository,
            settings: AppSettings,
            cache: HomeCache,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { DiagnosticsViewModel(repository, settings, cache) }
        }
    }
}