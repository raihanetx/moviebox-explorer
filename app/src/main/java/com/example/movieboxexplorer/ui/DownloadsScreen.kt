package com.example.movieboxexplorer.ui

import android.content.Intent
import android.net.Uri
import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.movieboxexplorer.data.DownloadItem
import com.example.movieboxexplorer.data.Downloads

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    viewModel: DownloadsViewModel,
    onBack: () -> Unit,
) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    val active = items.count { it.isActive }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Downloads", fontWeight = FontWeight.Bold)
                        if (items.isNotEmpty()) {
                            Text(
                                text = if (active > 0) "$active downloading" else "Nothing downloading",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        if (items.isEmpty()) {
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("No downloads yet", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Tap the download icon on any poster — or the Download button on a title — to save an episode or movie.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                val downloads = items.filter { it.isActive }
                val failed = items.filter { it.isFailed }
                val done = items.filter { it.isComplete }

                if (downloads.isNotEmpty()) {
                    item(key = "active-title") { SectionLabel("Downloading") }
                    items(downloads, key = { it.id }) { item ->
                        DownloadRow(item = item, onDelete = { viewModel.delete(item.id) })
                    }
                }
                if (failed.isNotEmpty()) {
                    item(key = "failed-title") { SectionLabel("Failed") }
                    items(failed, key = { it.id }) { item ->
                        DownloadRow(item = item, onDelete = { viewModel.delete(item.id) })
                    }
                }
                if (done.isNotEmpty()) {
                    item(key = "done-title") { SectionLabel("Downloaded") }
                    items(done, key = { it.id }) { item ->
                        DownloadRow(item = item, onDelete = { viewModel.delete(item.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.Bold,
    )
}

@Composable
private fun DownloadRow(item: DownloadItem, onDelete: () -> Unit) {
    val context = LocalContext.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = statusLine(context, item),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (item.isFailed) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                    if (item.isActive && item.hasSizes) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "${Formatter.formatShortFileSize(context, item.downloadedBytes)} of ${Formatter.formatShortFileSize(context, item.totalBytes)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (item.isComplete && item.localUri != null) {
                    IconButton(onClick = {
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(Uri.parse(item.localUri), "video/*")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(intent)
                    }) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Play")
                    }
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete")
                }
            }
            if (item.isActive) {
                Spacer(Modifier.height(10.dp))
                if (item.hasSizes) {
                    LinearProgressIndicator(
                        progress = { item.progress },
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

private fun statusLine(
    context: android.content.Context,
    item: DownloadItem,
): String = when (item.status) {
    android.app.DownloadManager.STATUS_PENDING -> "Pending — waiting to start"
    android.app.DownloadManager.STATUS_RUNNING ->
        if (item.hasSizes) "Downloading · ${item.percent}%" else "Downloading…"
    android.app.DownloadManager.STATUS_PAUSED -> "Paused (no connection)"
    android.app.DownloadManager.STATUS_SUCCESSFUL ->
        "Downloaded · " + Formatter.formatShortFileSize(context, item.downloadedBytes)
    android.app.DownloadManager.STATUS_FAILED -> "Failed (${reasonLabel(item.reason)})"
    else -> "Unknown"
}

private fun reasonLabel(reason: Int): String = when (reason) {
    android.app.DownloadManager.ERROR_CANNOT_RESUME -> "cannot resume"
    android.app.DownloadManager.ERROR_DEVICE_NOT_FOUND -> "storage missing"
    android.app.DownloadManager.ERROR_FILE_ALREADY_EXISTS -> "file exists"
    android.app.DownloadManager.ERROR_FILE_ERROR -> "file error"
    android.app.DownloadManager.ERROR_HTTP_DATA_ERROR -> "server data error"
    android.app.DownloadManager.ERROR_INSUFFICIENT_SPACE -> "not enough space"
    android.app.DownloadManager.ERROR_TOO_MANY_REDIRECTS -> "too many redirects"
    android.app.DownloadManager.ERROR_UNHANDLED_HTTP_CODE -> "server refused"
    android.app.DownloadManager.ERROR_UNKNOWN -> "unknown"
    else -> "error $reason"
}
