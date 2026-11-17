package com.example.movieboxexplorer.data

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * What this app knows about one queued download.
 *
 * The system DownloadManager only stores bytes and a name — not which language,
 * season, episode or quality the file is. We persist it here so the Downloads tab
 * can show a proper title and offer Pause / Resume / Retry (signed URLs cannot be
 * resumed mid-file, so "Resume" re-resolves a fresh link for the same request).
 */
@Serializable
data class DownloadMeta(
    val mediaTitle: String = "",
    val languageLabel: String = "",
    val season: Int = 0,
    val episode: Int = 0,
    val qualityLabel: String = "",
    val resolution: Int = 0,
    val fileName: String = "",
    val detailPath: String = "",
    val subjectId: String = "",
    val preferredResolution: Int? = null,
    val paused: Boolean = false,
    val pausedBytes: Long = 0,
    val pausedTotal: Long = 0,
    val isMovie: Boolean = false,
)

/** One item shown in the Downloads tab. */
data class DownloadItem(
    val id: Long,
    val status: Int,
    val downloadedBytes: Long,
    val totalBytes: Long,
    val localUri: String?,
    val reason: Int = 0,
    val meta: DownloadMeta = DownloadMeta(),
    /** Seconds remaining, from the last speed sample. Null when unknown. */
    val etaSeconds: Int? = null,
) {
    val isPausedUi: Boolean get() = status == STATUS_PAUSED_UI

    val progress: Float
        get() = if (totalBytes > 0) {
            (downloadedBytes.toFloat() / totalBytes).coerceIn(0f, 1f)
        } else {
            0f
        }

    val hasSizes: Boolean get() = totalBytes > 0

    val percent: Int get() = (progress * 100).toInt()

    val isComplete: Boolean
        get() = status == DownloadManager.STATUS_SUCCESSFUL

    val isFailed: Boolean
        get() = status == DownloadManager.STATUS_FAILED

    val isActive: Boolean
        get() = status == DownloadManager.STATUS_RUNNING ||
            status == DownloadManager.STATUS_PENDING ||
            status == DownloadManager.STATUS_PAUSED

    /** e.g. "Baki Hanma — Hindi dub · S1 E5 · 1080p" (season only for series). */
    val displayTitle: String
        get() {
            return buildString {
                append(meta.mediaTitle.ifBlank { "Video" })
                if (meta.languageLabel.isNotBlank()) append(" — ${meta.languageLabel}")
                if (!meta.isMovie) {
                    if (meta.season > 0) append(" · S${meta.season}")
                    if (meta.episode > 0) append(" E${meta.episode}")
                }
                if (meta.qualityLabel.isNotBlank()) append(" · ${meta.qualityLabel}")
            }
        }

    /** "S1 E5 · 1080p" — the secondary line under the title. For films: "1080p". */
    val displaySubtitle: String
        get() = if (meta.isMovie) {
            meta.qualityLabel
        } else {
            val bits = buildList {
                if (meta.season > 0) add("S${meta.season}")
                if (meta.episode > 0) add("E${meta.episode}")
                if (meta.qualityLabel.isNotBlank()) add(meta.qualityLabel)
            }
            bits.joinToString(" ")
        }

    companion object {
        /** UI-only status for a download the user paused (no live system entry). */
        const val STATUS_PAUSED_UI = -2
    }
}

/**
 * Thin wrapper around Android's system `DownloadManager`.
 *
 * The movie streams are signed URLs with a short expiry, so the download starts
 * immediately after the URL is resolved. The system handles progress, the
 * notification and the file; we persist the request metadata so the Downloads tab
 * can offer Pause / Resume / Retry.
 */
class Downloads(context: Context) {

    private val appContext = context.applicationContext
    private val manager =
        appContext.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    private val json = Json { ignoreUnknownKeys = true }

    /** Which downloads belong to this app — SharedPreferences keeps their ids. */
    private val prefs = appContext.getSharedPreferences(
        "moviebox_downloads",
        Context.MODE_PRIVATE,
    )

    // ---------------------------------------------------------------- metadata

    private fun trackedIds(): MutableSet<String> =
        (prefs.getStringSet("ids", emptySet()) ?: emptySet()).toMutableSet()

    private fun setTrackedIds(ids: Set<String>) {
        prefs.edit().putStringSet("ids", HashSet(ids)).apply()
    }

    private fun metaKey(id: Long) = "meta_$id"

    fun metaOf(id: Long): DownloadMeta? =
        prefs.getString(metaKey(id), null)?.let { raw ->
            runCatching { json.decodeFromString(DownloadMeta.serializer(), raw) }.getOrNull()
        }

    private fun saveMeta(id: Long, meta: DownloadMeta) {
        prefs.edit().putString(metaKey(id), json.encodeToString(DownloadMeta.serializer(), meta))
            .apply()
    }

    private fun removeMeta(id: Long) {
        prefs.edit().remove(metaKey(id)).apply()
    }

    /** Live and paused entries together, for the Downloads tab. */
    fun allIds(): Set<String> = trackedIds()

    // ---------------------------------------------------------------- queue

    fun enqueue(
        title: String,
        url: String,
        headers: Map<String, String>,
        fileName: String,
        meta: DownloadMeta,
    ): Long? {
        val id = enqueueRaw(title, url, headers, fileName) ?: return null
        setTrackedIds(trackedIds() + id.toString())
        saveMeta(id, meta)
        return id
    }

    private fun enqueueRaw(
        title: String,
        url: String,
        headers: Map<String, String>,
        fileName: String,
    ): Long? {
        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle(title)
            .setDescription("Downloading…")
            .setDestinationInExternalFilesDir(
                appContext,
                Environment.DIRECTORY_DOWNLOADS,
                fileName,
            )
            .setNotificationVisibility(
                DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED,
            )

        headers.forEach { (key, value) -> request.addRequestHeader(key, value) }
        return runCatching { manager.enqueue(request) }.getOrNull()
    }

    /**
     * Pause a running download.
     *
     * DownloadManager has no pause API, so we cancel the transfer and keep the
     * request. Resuming starts it over from 0 — signed URLs cannot be resumed
     * mid-file by the system.
     */
    fun pause(id: Long) {
        val meta = metaOf(id) ?: return
        val system = systemItem(id)
        saveMeta(
            id,
            meta.copy(
                paused = true,
                pausedBytes = system?.downloadedBytes ?: 0,
                pausedTotal = system?.totalBytes ?: 0,
            ),
        )
        runCatching { manager.remove(id) }
    }

    /**
     * Resume a paused download with a fresh signed URL. The caller resolves
     * [url]/[headers] via the playback API; we re-enqueue under a new id and
     * keep the metadata (minus the paused flag).
     */
    fun resume(
        id: Long,
        url: String,
        headers: Map<String, String>,
    ): Long? {
        val meta = metaOf(id) ?: return null
        val fresh = enqueueRaw(meta.mediaTitle.ifBlank { meta.fileName }, url, headers, meta.fileName)
            ?: return null
        setTrackedIds((trackedIds() - id.toString()) + fresh.toString())
        saveMeta(fresh, meta.copy(paused = false, pausedBytes = 0, pausedTotal = 0))
        removeMeta(id)
        return fresh
    }

    fun items(): List<DownloadItem> {
        val all = trackedIds().mapNotNull { it.toLongOrNull() }
        if (all.isEmpty()) return emptyList()

        // Paused entries have no live system row — synthesize them from metadata.
        val pausedItems = all.mapNotNull { id ->
            val meta = metaOf(id) ?: return@mapNotNull null
            if (!meta.paused) return@mapNotNull null
            DownloadItem(
                id = id,
                status = DownloadItem.STATUS_PAUSED_UI,
                downloadedBytes = meta.pausedBytes,
                totalBytes = meta.pausedTotal,
                localUri = null,
                meta = meta,
            )
        }

        val liveIds = all.filter { id -> metaOf(id)?.paused != true }
        val rows = systemItems(liveIds)
        val matched = rows.map { row ->
            row.copy(meta = metaOf(row.id) ?: DownloadMeta())
        }

        // Forget entries the system no longer knows and that were not paused.
        val pruned = trackedIds()
            .filter { key ->
                val id = key.toLongOrNull() ?: return@filter false
                matched.any { it.id == id } || pausedItems.any { it.id == id }
            }
            .toSet()
        if (pruned.size != all.size) setTrackedIds(pruned)

        return pausedItems + matched
    }

    private fun systemItem(id: Long): DownloadItem? = systemItems(listOf(id)).firstOrNull()

    private fun systemItems(ids: List<Long>): List<DownloadItem> {
        if (ids.isEmpty()) return emptyList()
        val cursor = manager.query(
            DownloadManager.Query().setFilterById(*ids.toLongArray())
        )
        cursor.use { c ->
            val idColumn = c.getColumnIndexOrThrow(DownloadManager.COLUMN_ID)
            val titleColumn = c.getColumnIndexOrThrow(DownloadManager.COLUMN_TITLE)
            val statusColumn = c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)
            val bytesColumn =
                c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
            val totalColumn =
                c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
            val uriColumn = c.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI)
            val reasonColumn = c.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON)

            return buildList {
                while (c.moveToNext()) {
                    add(
                        DownloadItem(
                            id = c.getLong(idColumn),
                            status = c.getInt(statusColumn),
                            downloadedBytes = c.getLong(bytesColumn),
                            totalBytes = c.getLong(totalColumn),
                            localUri = c.getString(uriColumn),
                            reason = c.getInt(reasonColumn),
                            // The system column title carries our display string;
                            // the metadata is authoritative, so keep it blank here.
                            meta = DownloadMeta(fileName = c.getString(titleColumn).orEmpty()),
                        ),
                    )
                }
            }
        }
    }

    fun delete(id: Long) {
        runCatching { manager.remove(id) }
        setTrackedIds(trackedIds() - id.toString())
        removeMeta(id)
    }
}
