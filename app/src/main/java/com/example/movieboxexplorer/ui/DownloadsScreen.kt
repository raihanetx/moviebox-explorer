package com.example.movieboxexplorer.ui

import android.content.Intent
import android.net.Uri
import android.text.format.Formatter
import androidx.compose.foundation.background
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import com.example.movieboxexplorer.data.DownloadItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    viewModel: DownloadsViewModel,
    onBack: () -> Unit,
) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    val active = items.count { it.isActive }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.events.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

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
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
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
                val downloading = items.filter { it.isActive }
                val paused = items.filter { it.isPausedUi }
                val failed = items.filter { it.isFailed }
                val done = items.filter { it.isComplete }

                if (downloading.isNotEmpty()) {
                    item(key = "active-title") { SectionLabel("Downloading") }
                    items(downloading, key = { it.id }) { item ->
                        DownloadRow(
                            item = item,
                            onPause = { viewModel.pause(item.id) },
                            onResume = { viewModel.resume(item.id) },
                            onDelete = { viewModel.delete(item.id) },
                        )
                    }
                }
                if (paused.isNotEmpty()) {
                    item(key = "paused-title") { SectionLabel("Paused") }
                    items(paused, key = { it.id }) { item ->
                        DownloadRow(
                            item = item,
                            onPause = { viewModel.pause(item.id) },
                            onResume = { viewModel.resume(item.id) },
                            onDelete = { viewModel.delete(item.id) },
                        )
                    }
                }
                if (failed.isNotEmpty()) {
                    item(key = "failed-title") { SectionLabel("Failed") }
                    items(failed, key = { it.id }) { item ->
                        DownloadRow(
                            item = item,
                            onPause = { viewModel.pause(item.id) },
                            onResume = { viewModel.resume(item.id) },
                            onDelete = { viewModel.delete(item.id) },
                        )
                    }
                }
                if (done.isNotEmpty()) {
                    item(key = "done-title") { SectionLabel("Downloaded") }
                    items(done, key = { it.id }) { item ->
                        DownloadRow(
                            item = item,
                            onPause = { viewModel.pause(item.id) },
                            onResume = { viewModel.resume(item.id) },
                            onDelete = { viewModel.delete(item.id) },
                        )
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

/** The three-dot affordance — drawn, so it works on every icon pack. */
@Composable
private fun MoreVertIcon() {
    Column(
        modifier = Modifier.padding(2.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        repeat(3) {
            Box(
                modifier = Modifier
                    .size(4.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(MaterialTheme.colorScheme.onSurface),
            )
        }
    }
}

@Composable
private fun DownloadRow(
    item: DownloadItem,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    var menuOpen by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Poster — so the row is identifiable at a glance.
                AsyncImage(
                    model = item.meta.coverUrl.takeIf { it.isNotBlank() },
                    contentDescription = item.displayTitle,
                    modifier = Modifier
                        .size(width = 54.dp, height = 76.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentScale = ContentScale.Crop,
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.displayTitle,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (item.displaySubtitle.isNotBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = item.displaySubtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))

            // Progress bar with the three-dot menu on its right edge.
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (item.hasSizes) {
                    LinearProgressIndicator(
                        progress = { item.progress },
                        modifier = Modifier
                            .weight(1f)
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                    )
                } else {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .weight(1f)
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                    )
                }
                Spacer(Modifier.width(6.dp))
                Box {
                    IconButton(
                        onClick = { menuOpen = true },
                        modifier = Modifier.size(36.dp),
                    ) {
                        MoreVertIcon()
                    }
                    DropdownMenu(
                        expanded = menuOpen,
                        onDismissRequest = { menuOpen = false },
                    ) {
                        when {
                            item.isComplete -> DropdownMenuItem(
                                text = { Text("Play") },
                                onClick = {
                                    menuOpen = false
                                    item.localUri?.let { uri ->
                                        val intent = Intent(Intent.ACTION_VIEW).apply {
                                            setDataAndType(Uri.parse(uri), "video/*")
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(intent)
                                    }
                                },
                            )

                            item.isPausedUi -> DropdownMenuItem(
                                text = { Text("Resume") },
                                onClick = {
                                    menuOpen = false
                                    onResume()
                                },
                            )

                            item.isFailed -> DropdownMenuItem(
                                text = { Text("Retry") },
                                onClick = {
                                    menuOpen = false
                                    onResume()
                                },
                            )

                            else -> DropdownMenuItem(
                                text = { Text("Pause") },
                                onClick = {
                                    menuOpen = false
                                    onPause()
                                },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Delete") },
                            onClick = {
                                menuOpen = false
                                onDelete()
                            },
                        )
                    }
                }
            }
            Spacer(Modifier.height(6.dp))

            // Bottom line: how much is done and how long it could take.
            Text(
                text = bottomLine(context, item),
                style = MaterialTheme.typography.labelMedium,
                color = if (item.isFailed) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

private fun bottomLine(
    context: android.content.Context,
    item: DownloadItem,
): String {
    val size = item.totalBytes.takeIf { it > 0 }
    val done = item.downloadedBytes.takeIf { it > 0 }
    return when {
        item.isFailed -> "Failed · ${reasonLabel(item.reason)}"
        item.isComplete -> "Downloaded · " +
            Formatter.formatShortFileSize(context, item.totalBytes)
        item.isPausedUi -> {
            val kept = done?.let { Formatter.formatShortFileSize(context, it) }
            if (kept != null && size != null) {
                "Paused · $kept of ${Formatter.formatShortFileSize(context, size)} kept — resume starts over"
            } else {
                "Paused — resume starts over"
            }
        }
        item.isActive && size != null && done != null -> {
            val progress = "${Formatter.formatShortFileSize(context, done)} of " +
                Formatter.formatShortFileSize(context, size)
            val eta = item.etaSeconds?.let { " · ${etaLabel(it)}" } ?: ""
            "$progress · ${item.percent}%$eta"
        }
        item.isActive -> "Downloading…"
        else -> "Unknown"
    }
}

private fun etaLabel(seconds: Int): String = when {
    seconds < 60 -> "≈ $seconds s left"
    seconds < 3600 -> "≈ ${seconds / 60} min left"
    else -> "≈ ${seconds / 3600} h ${(seconds % 3600) / 60} min left"
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
