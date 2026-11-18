package com.example.movieboxexplorer.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The playback resolution cache: keying, TTL, LRU eviction, failure exclusion. */
class PlaybackCacheTest {

    private fun ready(url: String = "https://cdn/example.mp4") =
        PlaybackResult.Ready(
            PlaybackSource(url = url, format = "MP4", resolution = 1080, codec = "h264", durationSeconds = 1425)
        )

    @Test
    fun `a stored resolution is returned within the ttl`() {
        val cache = PlaybackCache(ttlMillis = 1_000)
        val key = cache.key("path", "id", 1, 1, null)
        cache.put(key, ready(), now = 10_000)
        assertEquals(ready(), cache.get(key, now = 10_500))
    }

    @Test
    fun `an entry expires after the ttl`() {
        val cache = PlaybackCache(ttlMillis = 1_000)
        val key = cache.key("path", "id", 1, 1, null)
        cache.put(key, ready(), now = 10_000)
        assertNull(cache.get(key, now = 11_001))
    }

    @Test
    fun `a missing key is a miss`() {
        assertNull(PlaybackCache().get("nope"))
    }

    @Test
    fun `ready no-resource and locked results are cached`() {
        val cache = PlaybackCache(ttlMillis = 60_000)
        cache.put("a", ready())
        cache.put("b", PlaybackResult.NoResource)
        cache.put("c", PlaybackResult.Locked("premium"))
        assertEquals(3, cache.size())
    }

    @Test
    fun `a failed resolution is never cached`() {
        val cache = PlaybackCache(ttlMillis = 60_000)
        cache.put("a", PlaybackResult.Failed("could not reach the catalogue"))
        assertEquals(0, cache.size())
        assertNull(cache.get("a"))
    }

    @Test
    fun `eviction drops the least recently used entry`() {
        val cache = PlaybackCache(ttlMillis = 60_000, maxEntries = 2)
        cache.put("a", ready("a.mp4"), now = 1)
        cache.put("b", ready("b.mp4"), now = 2)
        // touch `a` so `b` becomes the least recently used
        cache.get("a", now = 3)
        cache.put("c", ready("c.mp4"), now = 4)
        assertNull(cache.get("b", now = 5))
        assertEquals(ready("a.mp4"), cache.get("a", now = 6))
        assertEquals(ready("c.mp4"), cache.get("c", now = 6))
    }

    @Test
    fun `quality preference is part of the key`() {
        val cache = PlaybackCache()
        assertFalse(cache.key("p", "i", 1, 1, 720) == cache.key("p", "i", 1, 1, 1080))
        assertTrue(cache.key("p", "i", 1, 1, null) == cache.key("p", "i", 1, 1, null))
    }

    @Test
    fun `clear drops everything`() {
        val cache = PlaybackCache()
        cache.put("a", ready())
        cache.clear()
        assertEquals(0, cache.size())
    }
}
