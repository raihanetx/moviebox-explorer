package com.example.movieboxexplorer.data

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment

/** One item in the system download queue. */
data class DownloadItem(
    val id: Long,
    val title: String,
    val status: Int,
    val downloadedBytes: Long,
    val totalBytes: Long,
    val localUri: String?,
    val reason: Int = 0,
) {
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
}

/**
 * Thin wrapper around Android's system `DownloadManager`.
 *
 * The movie streams are signed URLs with a short expiry, so the download starts
 * immediately after the URL is resolved. The system handles progress, the
 * notification and the file.
 */
class Downloads(context: Context) {

    private val appContext = context.applicationContext
    private val manager =
        appContext.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    /** Which downloads belong to this app — SharedPreferences keeps their ids. */
    private val prefs = appContext.getSharedPreferences(
        "moviebox_downloads",
        Context.MODE_PRIVATE,
    )

    private fun trackedIds(): MutableSet<String> =
        (prefs.getStringSet("ids", emptySet()) ?: emptySet()).toMutableSet()

    private fun setTrackedIds(ids: Set<String>) {
        prefs.edit().putStringSet("ids", HashSet(ids)).apply()
    }

    fun enqueue(
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
        val id = runCatching { manager.enqueue(request) }.getOrNull() ?: return null
        setTrackedIds(trackedIds() + id.toString())
        return id
    }

    /** This app's downloads only — the system queue holds other apps' files too. */
    fun items(): List<DownloadItem> {
        val ids = trackedIds().mapNotNull { it.toLongOrNull() }
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

            val found = buildList {
                while (c.moveToNext()) {
                    add(
                        DownloadItem(
                            id = c.getLong(idColumn),
                            title = c.getString(titleColumn).orEmpty(),
                            status = c.getInt(statusColumn),
                            downloadedBytes = c.getLong(bytesColumn),
                            totalBytes = c.getLong(totalColumn),
                            localUri = c.getString(uriColumn),
                            reason = c.getInt(reasonColumn),
                        ),
                    )
                }
            }
            // Forget the ids the system no longer knows about.
            setTrackedIds(found.map { it.id.toString() }.toSet())
            return found
        }
    }

    fun delete(id: Long) {
        runCatching { manager.remove(id) }
        setTrackedIds(trackedIds() - id.toString())
    }
}