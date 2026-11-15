package com.example.movieboxexplorer.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.movieboxexplorer.data.DownloadItem
import com.example.movieboxexplorer.data.Downloads
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DownloadsViewModel(
    private val downloads: Downloads,
) : ViewModel() {

    private val _items = MutableStateFlow<List<DownloadItem>>(emptyList())
    val items: StateFlow<List<DownloadItem>> = _items.asStateFlow()

    init {
        viewModelScope.launch {
            while (isActive) {
                _items.value = withContext(Dispatchers.IO) { downloads.items() }
                delay(1_000)
            }
        }
    }

    fun delete(id: Long) {
        downloads.delete(id)
        viewModelScope.launch {
            _items.value = withContext(Dispatchers.IO) { downloads.items() }
        }
    }

    companion object {
        fun factory(downloads: Downloads): ViewModelProvider.Factory = viewModelFactory {
            initializer { DownloadsViewModel(downloads) }
        }
    }
}