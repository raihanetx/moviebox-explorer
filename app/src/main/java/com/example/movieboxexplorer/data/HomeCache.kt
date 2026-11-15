package com.example.movieboxexplorer.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Persistent cache for the home page.
 *
 * The full `HomePage` payload is stored as JSON, so the next launch can render every
 * hero slide, platform and rail immediately. The app then refreshes in the background
 * and replaces the cache — no rows are dropped to make the first paint faster.
 */
class HomeCache(context: Context) {

    private val file = File(context.filesDir, "home_cache.json")
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    fun load(host: String): HomePage? = runCatching {
        if (!file.exists()) return null
        val payload = json.decodeFromString<HomeCachePayload>(file.readText())
        payload.page.takeIf { payload.host == host }
    }.getOrNull()

    fun save(host: String, page: HomePage) {
        runCatching {
            val payload = HomeCachePayload(host = host, page = page)
            file.writeText(json.encodeToString(HomeCachePayload.serializer(), payload))
        }
    }

    fun clear() {
        runCatching { file.delete() }
    }

    fun size(): Long = if (file.exists()) file.length() else 0L

    @Serializable
    private data class HomeCachePayload(
        val host: String,
        val page: HomePage,
    )
}