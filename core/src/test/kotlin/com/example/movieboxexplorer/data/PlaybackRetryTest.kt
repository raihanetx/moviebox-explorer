package com.example.movieboxexplorer.data

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PlaybackRetryTest {

    private val ready = PlaybackResult.Ready(
        PlaybackSource(url = "https://cdn/x.mp4", format = "MP4", resolution = 1080, codec = "h264", durationSeconds = 60)
    )

    @Test fun `ready on first attempt returns immediately, no extra calls`() = runBlocking {
        var calls = 0
        val r = PlaybackRetry.resolveFresh(attempts = 3, backoffMillis = { 0 }) { calls++; ready }
        assertEquals(ready, r); assertEquals(1, calls)
    }

    @Test fun `transient failures are retried until ready`() = runBlocking {
        var calls = 0
        val r = PlaybackRetry.resolveFresh(attempts = 4, backoffMillis = { 0 }) {
            calls++
            if (calls < 3) PlaybackResult.Failed("network") else ready
        }
        assertEquals(ready, r); assertEquals(3, calls)
    }

    @Test fun `gives up after N attempts returning last transient failure`() = runBlocking {
        var calls = 0
        val r = PlaybackRetry.resolveFresh(attempts = 3, backoffMillis = { 0 }) { calls++; PlaybackResult.Failed("nope") }
        assertIs<PlaybackResult.Failed>(r); assertEquals(3, calls)
    }

    @Test fun `NoResource is definitive and not retried`() = runBlocking {
        var calls = 0
        val r = PlaybackRetry.resolveFresh(attempts = 5, backoffMillis = { 0 }) { calls++; PlaybackResult.NoResource }
        assertEquals(PlaybackResult.NoResource, r); assertEquals(1, calls)
    }

    @Test fun `Locked is definitive and not retried`() = runBlocking {
        var calls = 0
        val r = PlaybackRetry.resolveFresh(attempts = 5, backoffMillis = { 0 }) { calls++; PlaybackResult.Locked("premium") }
        assertIs<PlaybackResult.Locked>(r); assertEquals(1, calls)
    }

    @Test fun `a thrown exception is treated as transient`() = runBlocking {
        var calls = 0
        val r = PlaybackRetry.resolveFresh(attempts = 2, backoffMillis = { 0 }) { calls++; if (calls == 1) throw java.io.IOException("boom") else ready }
        assertEquals(ready, r); assertEquals(2, calls)
    }

    @Test fun `fresh resolution on each attempt`() = runBlocking {
        val urls = mutableListOf<String>()
        PlaybackRetry.resolveFresh(attempts = 2, backoffMillis = { 0 }) {
            urls.add("call${urls.size}")
            if (urls.size == 1) PlaybackResult.Failed("x") else ready
        }
        assertEquals(2, urls.size)
    }
}
