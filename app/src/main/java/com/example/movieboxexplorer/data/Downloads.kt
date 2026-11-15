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
) {
    val progress: Float
        get() = if (totalBytes > 0) {
            (downloadedBytes.toFloat() / totalBytes).coerceIn(0f, 1f)
        } else {
            0f
        }

    val isComplete: Boolean
        get() = status == DownloadManager.STATUS_SUCCESSFUL
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
        return runCatching { manager.enqueue(request) }.getOrNull()
    }

    fun items(): List<DownloadItem> {
        val cursor = manager.query(DownloadManager.Query())
        cursor.use { c ->
            val idColumn = c.getColumnIndexOrThrow(DownloadManager.COLUMN_ID)
            val titleColumn = c.getColumnIndexOrThrow(DownloadManager.COLUMN_TITLE)
            val statusColumn = c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)
            val bytesColumn =
                c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
            val totalColumn =
                c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
            val uriColumn = c.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI)

            return buildList {
                while (c.moveToNext()) {
                    add(
                        DownloadItem(
                            id = c.getLong(idColumn),
                            title = c.getString(titleColumn).orEmpty(),
                            status = c.getInt(statusColumn),
                            downloadedBytes = c.getLong(bytesColumn),
                            totalBytes = c.getLong(totalColumn),
                            localUri = c.getString(uriColumn),
                        ),
                    )
                }
            }
        }
    }

    fun delete(id: Long) {
        runCatching { manager.remove(id) }
    }
}