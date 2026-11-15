package com.example.movieboxexplorer.ui

import com.example.movieboxexplorer.data.PlaybackSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Hands the resolved episode over to the player screen.
 *
 * The playback headers carry a long edge cache credential and the manifest URL is
 * signed, so neither fits into a navigation route. The source travels through memory
 * instead, and is cleared as soon as the player is left.
 */
object PlaybackHolder {
    private val _source = MutableStateFlow<PlaybackSource?>(null)
    val source: StateFlow<PlaybackSource?> = _source.asStateFlow()

    fun set(source: PlaybackSource) {
        _source.value = source
    }

    fun clear() {
        _source.value = null
    }
}
