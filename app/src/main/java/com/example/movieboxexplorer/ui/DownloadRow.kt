package com.example.movieboxexplorer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.movieboxexplorer.data.DownloadItem

// Design tokens taken from the reference mockup.
internal val DlBg = Color(0xFF0F1116)
internal val DlSurface2 = Color(0xFF1F2229)
internal val DlSurface3 = Color(0xFF272A32)
internal val DlText = Color(0xFFE8E6EC)
internal val DlText2 = Color(0xFFB5B3BE)
internal val DlText3 = Color(0xFF7A7883)
internal val DlPrimary = Color(0xFFFFB4A8)
internal val DlPositive = Color(0xFF7CD9A0)
internal val DlDanger = Color(0xFFFA8B)

@Composable
internal fun DownloadRow(
    item: DownloadItem,
    selectMode: Boolean,
    selected: Boolean,
    onSelectToggle: () -> Unit,
    onLongPress: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onDelete: () -> Unit,
    onPlay: () -> Unit,
) {
    val downloading = item.isActive && !item.isPausedUi
    val paused = item.isPausedUi
    val failed = item.isFailed
    val done = item.isComplete
    val selectable = !downloading

    val context = androidx.compose.ui.platform.LocalContext.current
    var menuOpen by remember { mutableStateOf(false) }

    val rowAlpha = if (paused) 0.55f else 1f

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(selectable) {
                if (!selectable) return@pointerInput
                detectTapGestures(
                    onLongPress = { onLongPress() },
                    onTap = { if (selectMode) onSelectToggle() },
                )
            }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Select toggle (only visible in select mode, only for selectable rows).
        if (selectMode && selectable) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clickable { onSelectToggle() },
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    shape = CircleShape,
                    color = if (selected) DlPrimary else Color.Transparent,
                    border = androidx.compose.foundation.BorderStroke(
                        1.5.dp,
                        if (selected) DlPrimary else DlText3,
                    ),
                    modifier = Modifier.size(20.dp),
                ) {
                    if (selected) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = Color(0xFF0F1116),
                                modifier = Modifier.size(12.dp),
                            )
                        }
                    }
                }
            }
        }

        // Cover 16:9.
        Box(
            modifier = Modifier
                .width(132.dp)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(8.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(Color(0xFF3A2A6B), Color(0xFF0E0E14)),
                    ),
                ),
        ) {
            if (item.meta.coverUrl.isNotBlank()) {
                AsyncImage(
                    model = item.meta.coverUrl,
                    contentDescription = item.displayTitle,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize(),
                )
            }
            // Duration badge.
            if (item.meta.qualityLabel.isNotBlank()) {
                Text(
                    text = item.meta.qualityLabel,
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                        .background(Color(0x99000000), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 1.dp),
                )
            }
            // Watch ring (progress at bottom of cover).
            if (item.isActive || item.isComplete) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .height(2.dp)
                        .background(Color(0x66000000)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(if (item.isComplete) 1f else item.progress)
                            .height(2.dp)
                            .background(if (item.isComplete) DlPositive else DlText2),
                    )
                }
            }
            if (paused) {
                Box(modifier = Modifier.matchParentSize().background(Color(0x88000000)))
            }
        }

        // Mid: title + meta.
        Column(
            modifier = Modifier.weight(1f).padding(end = 4.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.displayTitle.substringBefore(" — ").ifBlank { item.displayTitle },
                    color = if (paused) DlText3 else DlText,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                // episode chip after title
                val ep = item.displaySubtitle.split(" ").firstOrNull { it.startsWith("S") }
                if (ep != null && ep != item.displayTitle) {
                    // already in displayTitle; skip duplicate
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = bottomLine(context, item),
                color = when {
                    failed -> DlDanger
                    done -> DlPositive
                    else -> DlText2
                },
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        // 3-dot menu.
        Box {
            IconButton(
                onClick = { menuOpen = true },
                modifier = Modifier.size(32.dp),
            ) {
                // three dots drawn
                Column(
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    repeat(3) {
                        Box(
                            modifier = Modifier
                                .size(4.dp)
                                .clip(CircleShape)
                                .background(DlText3),
                        )
                    }
                }
            }
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false },
                containerColor = DlSurface2,
            ) {
                when {
                    done -> DropdownMenuItem(
                        text = { Text("Play", color = DlText) },
                        onClick = { menuOpen = false; onPlay() },
                    )
                    paused -> DropdownMenuItem(
                        text = { Text("Resume", color = DlText) },
                        onClick = { menuOpen = false; onResume() },
                    )
                    failed -> DropdownMenuItem(
                        text = { Text("Retry", color = DlText) },
                        onClick = { menuOpen = false; onResume() },
                    )
                    else -> DropdownMenuItem(
                        text = { Text("Pause", color = DlText) },
                        onClick = { menuOpen = false; onPause() },
                    )
                }
                if (!done) {
                    DropdownMenuItem(
                        text = { Text("Cancel download", color = DlDanger) },
                        onClick = { menuOpen = false; onDelete() },
                    )
                } else {
                    DropdownMenuItem(
                        text = { Text("Delete", color = DlDanger) },
                        onClick = { menuOpen = false; onDelete() },
                    )
                }
            }
        }
    }
}

private fun bottomLine(context: android.content.Context, item: DownloadItem): String {
    val size = item.totalBytes.takeIf { it > 0 }
    val done = item.downloadedBytes.takeIf { it > 0 }
    val fmt: (Long) -> String = { android.text.format.Formatter.formatShortFileSize(context, it) }
    val lang = item.meta.languageLabel.takeIf { it.isNotBlank() }
    val prefix = if (lang != null) "$lang · " else ""
    return when {
        item.isFailed -> "Failed · ${reasonLabel(item.reason)}"
        item.isComplete -> prefix + "✓ Downloaded" + (size?.let { " · " + fmt(it) } ?: "")
        item.isPausedUi -> {
            val kept = done?.let { fmt(it) }
            if (kept != null && size != null) "Paused · $kept of ${fmt(size)} kept"
            else "Paused"
        }
        item.isActive && size != null && done != null ->
            prefix + "${fmt(done)} of ${fmt(size)} · ${item.percent}%"
        item.isActive -> (lang?.let { "$it · " } ?: "") + "Downloading…"
        else -> "Unknown"
    }
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
