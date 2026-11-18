package com.example.movieboxexplorer.data

/**
 * Short-lived in-memory cache for resolved playback sources.
 *
 * Resolving an episode costs one `/subject/play` round-trip (~1 s) and every
 * Play tap, retry, quality switch and "download after play" pays it again —
 * even for the same track seconds later. The signed CDN URL lives far longer
 * than this cache, so an eight minute entry is always safe to replay.
 *
 * Only terminal, non-transient outcomes are cached: a [PlaybackResult.Failed]
 * ("could not reach the catalogue") is almost always transient and must be
 * retried fresh, not replayed from cache.
 */
class PlaybackCache(
    private val ttlMillis: Long = DEFAULT_TTL_MILLIS,
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES,
) {
    private val entries =
        LinkedHashMap<String, Pair<Long, PlaybackResult>>(maxEntries + 1, 0.75f, true)

    /** Cache key: one track, one episode, one quality preference. */
    fun key(detailPath: String, subjectId: String, season: Int, episode: Int, preferredResolution: Int?): String =
        listOf(detailPath, subjectId, season, episode, preferredResolution ?: -1).joinToString("|")

    @Synchronized
    fun get(key: String, now: Long = System.currentTimeMillis()): PlaybackResult? {
        val entry = entries[key] ?: return null
        if (now - entry.first > ttlMillis) {
            entries.remove(key)
            return null
        }
        return entry.second
    }

    /** Stores terminal outcomes; transient failures are never cached. */
    @Synchronized
    fun put(key: String, value: PlaybackResult, now: Long = System.currentTimeMillis()) {
        if (!cacheable(value)) return
        entries[key] = now to value
        while (entries.size > maxEntries) {
            entries.remove(entries.keys.iterator().next())
        }
    }

    @Synchronized
    fun clear() = entries.clear()

    @Synchronized
    fun size(): Int = entries.size

    private fun cacheable(value: PlaybackResult): Boolean = when (value) {
        is PlaybackResult.Ready -> true
        PlaybackResult.NoResource -> true
        is PlaybackResult.Locked -> true
        is PlaybackResult.Failed -> false
    }

    internal companion object {
        const val DEFAULT_TTL_MILLIS: Long = 8 * 60 * 1000L
        const val DEFAULT_MAX_ENTRIES: Int = 64
    }
}
