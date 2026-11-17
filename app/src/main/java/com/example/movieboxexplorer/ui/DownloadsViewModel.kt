package com.example.movieboxexplorer.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.movieboxexplorer.data.DownloadItem
import com.example.movieboxexplorer.data.Downloads
import com.example.movieboxexplorer.data.MovieBoxRepository
import com.example.movieboxexplorer.data.PlaybackResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DownloadsViewModel(
    private val downloads: Downloads,
    private val repository: MovieBoxRepository,
) : ViewModel() {

    private val _items = MutableStateFlow<List<DownloadItem>>(emptyList())
    val items: StateFlow<List<DownloadItem>> = _items.asStateFlow()

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val events: SharedFlow<String> = _events.asSharedFlow()

    /** Smoothed bytes/second per running download, and the last sample. */
    private val speeds = mutableMapOf<Long, Float>()
    private val lastBytes = mutableMapOf<Long, Pair<Long, Long>>()

    init {
        viewModelScope.launch {
            while (isActive) {
                _items.value = withContext(Dispatchers.IO) { poll() }
                delay(1_000)
            }
        }
    }

    private fun poll(): List<DownloadItem> {
        val now = System.currentTimeMillis()
        return downloads.items().map { item ->
            if (!item.isActive || !item.hasSizes) {
                speeds.remove(item.id)
                lastBytes.remove(item.id)
                return@map item.copy(etaSeconds = null)
            }
            val prev = lastBytes[item.id]
            lastBytes[item.id] = now to item.downloadedBytes
            if (prev != null) {
                val dtSeconds = (now - prev.first) / 1000f
                val delta = item.downloadedBytes - prev.second
                if (dtSeconds >= 0.2f && delta >= 0) {
                    val instant = delta / dtSeconds
                    speeds[item.id] = speeds[item.id]
                        ?.let { previous -> 0.6f * previous + 0.4f * instant }
                        ?: instant
                }
            }
            val speed = speeds[item.id]
            val remaining = item.totalBytes - item.downloadedBytes
            val eta = if (speed != null && speed > 1024f && remaining > 0) {
                (remaining / speed).toInt()
            } else {
                null
            }
            item.copy(etaSeconds = eta)
        }
    }

    fun pause(id: Long) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { downloads.pause(id) }
            refresh()
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { downloads.delete(id) }
            lastBytes.remove(id)
            speeds.remove(id)
            refresh()
        }
    }

    /** Resume a paused download, or retry a failed one (same request, fresh link). */
    fun resume(id: Long) {
        viewModelScope.launch {
            val meta = withContext(Dispatchers.IO) { downloads.metaOf(id) }
            if (meta == null) {
                _events.tryEmit("Nothing to resume")
                return@launch
            }
            when (val result = repository.playEpisode(
                detailPath = meta.detailPath,
                subjectId = meta.subjectId,
                season = meta.season.coerceAtLeast(1),
                episode = meta.episode.coerceAtLeast(1),
                preferredResolution = meta.preferredResolution,
            )) {
                is PlaybackResult.Ready -> {
                    val freshId = withContext(Dispatchers.IO) {
                        downloads.resume(id, result.source.url, result.source.headers)
                    }
                    _events.tryEmit(
                        if (freshId != null) "Download resumed" else "Could not restart the download",
                    )
                }
                is PlaybackResult.Locked -> _events.tryEmit(result.message)
                is PlaybackResult.NoResource ->
                    _events.tryEmit("Episode ${meta.episode} was never uploaded for this track")
                is PlaybackResult.Failed -> _events.tryEmit(result.message)
            }
            refresh()
        }
    }

    private suspend fun refresh() {
        _items.value = withContext(Dispatchers.IO) { poll() }
    }

    companion object {
        fun factory(
            downloads: Downloads,
            repository: MovieBoxRepository,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { DownloadsViewModel(downloads, repository) }
        }
    }
}
