package com.example.movieboxexplorer.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Persistent cache for the catalog grids (films / TV / animation).
 *
 * The SSG grid pages are large (hundreds of kilobytes) and take seconds on a phone
 * connection, so switching tabs re-downloaded the same page every time. This stores
 * the parsed grid alongside a TTL; the screen shows the stale copy instantly and
 * revalidates in the background — the SWR pattern every modern client uses.
 */
class GridCache(context: Context) {

    private val file = File(context.filesDir, "grid_cache.json")
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    fun load(key: String, ttlMillis: Long): CatalogGrid? = runCatching {
        if (!file.exists()) return null
        val payload = json.decodeFromString<GridCachePayload>(file.readText())
        val entry = payload.entries[key] ?: return null
        if (System.currentTimeMillis() - entry.savedAt > ttlMillis) return null
        entry.grid
    }.getOrNull()

    fun save(key: String, grid: CatalogGrid) {
        runCatching {
            val current = readEntries()
            current[key] = GridCacheEntry(
                grid = grid,
                savedAt = System.currentTimeMillis(),
            )
            // Keep the cache bounded — drop the oldest entries first.
            val trimmed = current.entries
                .sortedByDescending { it.value.savedAt }
                .take(MAX_ENTRIES)
                .associate { it.key to it.value }
            file.writeText(json.encodeToString(GridCachePayload.serializer(), GridCachePayload(trimmed)))
        }
    }

    fun size(): Long = if (file.exists()) file.length() else 0L

    fun clear() {
        runCatching { file.delete() }
    }

    private fun readEntries(): MutableMap<String, GridCacheEntry> =
        runCatching {
            json.decodeFromString<GridCachePayload>(file.readText()).entries.toMutableMap()
        }.getOrDefault(mutableMapOf())

    @Serializable
    private data class GridCacheEntry(
        val grid: CatalogGrid,
        val savedAt: Long,
    )

    @Serializable
    private data class GridCachePayload(
        val entries: Map<String, GridCacheEntry>,
    )

    private companion object {
        const val MAX_ENTRIES = 12
    }
}
