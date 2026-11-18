package com.example.movieboxexplorer.data

import android.content.Context
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Persistent cache for a title's detail payload (H5 merged with its dubs).
 *
 * The detail screen fires a dozen H5 + mobile requests before it can paint; the
 * result rarely changes. Cache it so revisiting a title renders instantly, then
 * refresh in the background — the same SWR pattern used for the home page.
 */
class TitleCache(context: Context) {

    private val file = File(context.filesDir, "title_cache.json")
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    fun load(detailPath: String, subjectId: String, ttlMillis: Long): TitleInfo? = runCatching {
        if (!file.exists()) return null
        val payload = json.decodeFromString<TitleCachePayload>(file.readText())
        val key = cacheKey(detailPath, subjectId)
        val entry = payload.entries[key] ?: return null
        if (System.currentTimeMillis() - entry.savedAt > ttlMillis) return null
        entry.info
    }.getOrNull()

    fun save(detailPath: String, subjectId: String, info: TitleInfo) {
        runCatching {
            val current = readEntries()
            current[cacheKey(detailPath, subjectId)] = TitleCacheEntry(
                info = info,
                savedAt = System.currentTimeMillis(),
            )
            val trimmed = current.entries
                .sortedByDescending { it.value.savedAt }
                .take(MAX_ENTRIES)
                .associate { it.key to it.value }
            file.writeText(json.encodeToString(TitleCachePayload.serializer(), TitleCachePayload(trimmed)))
        }
    }

    fun size(): Long = if (file.exists()) file.length() else 0L

    fun clear() {
        runCatching { file.delete() }
    }

    private fun cacheKey(detailPath: String, subjectId: String) =
        (detailPath.ifBlank { subjectId }).let { it.ifBlank { subjectId } }

    private fun readEntries(): MutableMap<String, TitleCacheEntry> =
        runCatching {
            json.decodeFromString<TitleCachePayload>(file.readText()).entries.toMutableMap()
        }.getOrDefault(mutableMapOf())

    @kotlinx.serialization.Serializable
    private data class TitleCacheEntry(
        val info: TitleInfo,
        val savedAt: Long,
    )

    @kotlinx.serialization.Serializable
    private data class TitleCachePayload(
        val entries: Map<String, TitleCacheEntry>,
    )

    private companion object {
        const val MAX_ENTRIES = 40
    }
}
