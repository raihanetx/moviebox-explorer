package com.example.movieboxexplorer.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The user's "My List" — saved titles, persisted on device.
 *
 * `savedAt` is epoch millis so the Saved screen can show "how long ago it was
 * saved". Movies and TV are separated by [isMovie].
 */
@Serializable
data class SavedEntry(
    val subject: SubjectBrief,
    val savedAt: Long,
    val isMovie: Boolean,
)

/** Stable key for a subject record. */
fun SubjectBrief.savedKey(): String = subjectId.ifBlank { detailPath }

class SavedList(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("moviebox_saved", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    fun saved(): List<SavedEntry> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        return runCatching { json.decodeFromString(ListSerializer, raw) }.getOrDefault(emptyList())
    }

    fun isSaved(subject: SubjectBrief): Boolean {
        val key = subject.savedKey()
        return saved().any { it.subject.savedKey() == key }
    }

    fun save(subject: SubjectBrief, isMovie: Boolean) {
        val key = subject.savedKey()
        val rest = saved().filter { it.subject.savedKey() != key }
        persist(rest + SavedEntry(subject = subject, savedAt = System.currentTimeMillis(), isMovie = isMovie))
    }

    fun unsave(key: String) {
        persist(saved().filter { it.subject.savedKey() != key })
    }

    fun toggle(subject: SubjectBrief, isMovie: Boolean) {
        if (isSaved(subject)) unsave(subject.savedKey()) else save(subject, isMovie)
    }

    private fun persist(entries: List<SavedEntry>) {
        prefs.edit().putString(KEY, json.encodeToString(ListSerializer, entries)).apply()
    }

    private companion object {
        const val KEY = "entries"
        val ListSerializer = kotlinx.serialization.builtins.ListSerializer(SavedEntry.serializer())
    }
}

/** Human relative time: "today", "1 day ago", "N days ago". */
fun relativeSavedAt(savedAtMillis: Long, nowMillis: Long = System.currentTimeMillis()): String {
    val diffMs = (nowMillis - savedAtMillis).coerceAtLeast(0)
    val minutes = diffMs / 60_000
    val hours = minutes / 60
    val days = hours / 24
    return when {
        days <= 0 && hours <= 0 && minutes < 1 -> "just now"
        days <= 0 && hours <= 0 -> "$minutes minute${if (minutes == 1L) "" else "s"} ago"
        days <= 0 -> "$hours hour${if (hours == 1L) "" else "s"} ago"
        days == 1L -> "1 day ago"
        days < 7 -> "$days days ago"
        days < 14 -> "1 week ago"
        days < 30 -> "${days / 7} weeks ago"
        days < 365 -> "${days / 30} month${if (days / 30 == 1L) "" else "s"} ago"
        else -> "${days / 365} year${if (days / 365 == 1L) "" else "s"} ago"
    }
}
