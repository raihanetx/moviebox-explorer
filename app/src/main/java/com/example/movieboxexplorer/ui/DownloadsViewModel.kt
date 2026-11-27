package com.example.movieboxexplorer.ui

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
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

    /** Guards against concurrent retry attempts on the same entry. */
    private val retrying = mutableSetOf<Long>()

    private val connectivity =
        downloads.context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    /** Network or VPN came back — retry anything that failed with a fresh URL. */
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) { retryFailed() }
        override fun onCapabilitiesChanged(network: Network, caps: android.net.NetworkCapabilities) { retryFailed() }
    }

    init {
        runCatching { connectivity.registerDefaultNetworkCallback(networkCallback) }
        viewModelScope.launch {
            while (isActive) {
                _items.value = withContext(Dispatchers.IO) { poll() }
                delay(1_000)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        runCatching { connectivity.unregisterNetworkCallback(networkCallback) }
    }

    /** Retry every failed (not paused) download once, with a freshly resolved URL. */
    fun retryFailed() {
        viewModelScope.launch {
            // Give DownloadManager a moment to settle STATUS_FAILED after the
            // network returns; re-read live items rather than a stale snapshot.
            delay(500)
            _items.value.filter { it.isFailed }.map { it.id }.forEach { retry(it) }
        }
    }

    fun retry(id: Long) {
        if (!retrying.add(id)) return
        viewModelScope.launch {
            resume(id)
            retrying.remove(id)
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
            // Always re-resolve a FRESH signed URL for a retry/resume — never
            // replay a cached one; the previous attempt may have invalidated it.
            // Retry transient failures with backoff (VPN/network reconnect).
            val result = com.example.movieboxexplorer.data.PlaybackRetry.resolveFresh(
                attempts = 3,
                backoffMillis = { 600L * (it + 1) },
            ) {
                repository.playEpisode(
                    detailPath = meta.detailPath,
                    subjectId = meta.subjectId,
                    season = meta.season.coerceAtLeast(1),
                    episode = meta.episode.coerceAtLeast(1),
                    preferredResolution = meta.preferredResolution,
                    useCache = false,
                )
            }
            when (result) {
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
