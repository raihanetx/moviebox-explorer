package com.example.movieboxexplorer.ui

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
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.movieboxexplorer.data.DownloadItem

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun DownloadsScreen(
    viewModel: DownloadsViewModel,
    onBack: () -> Unit,
) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    var selectMode by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<Long>()) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    LaunchedEffect(selected) {
        if (selectMode && selected.isEmpty()) selectMode = false
    }

    val downloading = items.filter { it.isActive }
    val paused = items.filter { it.isPausedUi }
    val failed = items.filter { it.isFailed }
    val done = items.filter { it.isComplete }

    Scaffold(
        containerColor = DlBg,
        topBar = {
            if (selectMode) {
                TopAppBar(
                    title = {
                        Text(
                            text = "${selected.size} selected",
                            color = DlText,
                            fontWeight = FontWeight.Bold,
                        )
                    },
                    navigationIcon = {
                        androidx.compose.material3.TextButton(onClick = {
                            selectMode = false
                            selected = emptySet()
                        }) {
                            Text("Cancel", color = DlText2, fontWeight = FontWeight.SemiBold)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = DlBg),
                )
            } else {
                TopAppBar(
                    title = {
                        Column {
                            Text("Downloads", color = DlText, fontWeight = FontWeight.Bold)
                            if (items.isNotEmpty()) {
                                val active = items.count { it.isActive }
                                Text(
                                    text = if (active > 0) "$active downloading" else "Manage your offline Library",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = DlText3,
                                )
                            } else {
                                Text(
                                    text = "Manage your offline Library",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = DlText3,
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = DlText2,
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = DlBg),
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        if (items.isEmpty()) {
            Box(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "No downloads yet",
                        color = DlText,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Download episodes to watch them offline — anytime, anywhere.",
                        color = DlText3,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp),
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (downloading.isNotEmpty() || paused.isNotEmpty()) {
                    item(key = "downloading-header") {
                        SectionHeader(
                            title = "Downloading",
                            count = downloading.size + paused.size,
                            iconColor = DlPrimary,
                        )
                    }
                    // downloading items
                    items(downloading, key = { it.id }) { item ->
                        DownloadRow(
                            item = item,
                            selectMode = false,
                            selected = false,
                            onSelectToggle = {},
                            onLongPress = {},
                            onPause = { viewModel.pause(item.id) },
                            onResume = { viewModel.resume(item.id) },
                            onDelete = { viewModel.delete(item.id) },
                            onPlay = {},
                        )
                    }
                    // paused items (dimmed) inside the same section
                    items(paused, key = { it.id }) { item ->
                        DownloadRow(
                            item = item,
                            selectMode = false,
                            selected = false,
                            onSelectToggle = {},
                            onLongPress = {},
                            onPause = { viewModel.pause(item.id) },
                            onResume = { viewModel.resume(item.id) },
                            onDelete = { viewModel.delete(item.id) },
                            onPlay = {},
                        )
                    }
                }
                if (failed.isNotEmpty()) {
                    item(key = "failed-header") {
                        SectionHeader(
                            title = "Failed",
                            count = failed.size,
                            iconColor = DlDanger,
                        )
                    }
                    items(failed, key = { it.id }) { item ->
                        DownloadRow(
                            item = item,
                            selectMode = false,
                            selected = false,
                            onSelectToggle = {},
                            onLongPress = {},
                            onPause = { viewModel.pause(item.id) },
                            onResume = { viewModel.resume(item.id) },
                            onDelete = { viewModel.delete(item.id) },
                            onPlay = {},
                        )
                    }
                }
                if (done.isNotEmpty()) {
                    item(key = "downloaded-header") {
                        SectionHeader(
                            title = "Downloaded",
                            count = done.size,
                            iconColor = DlPositive,
                            onSelectAll = if (selectMode) {
                                {
                                    val allSelected = done.all { selected.contains(it.id) }
                                    if (allSelected) {
                                        selected = selected - done.map { it.id }.toSet()
                                    } else {
                                        selected = selected + done.map { it.id }.toSet()
                                    }
                                }
                            } else null,
                            selectMode = selectMode,
                        )
                    }
                    items(done, key = { it.id }) { item ->
                        DownloadRow(
                            item = item,
                            selectMode = selectMode,
                            selected = selected.contains(item.id),
                            onSelectToggle = {
                                selected = if (selected.contains(item.id)) {
                                    selected - item.id
                                } else {
                                    selected + item.id
                                }
                            },
                            onLongPress = {
                                selectMode = true
                                selected = selected + item.id
                            },
                            onPause = { viewModel.pause(item.id) },
                            onResume = { viewModel.resume(item.id) },
                            onDelete = { viewModel.delete(item.id) },
                            onPlay = {
                                item.localUri?.let { uri ->
                                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                                        setDataAndType(android.net.Uri.parse(uri), "video/*")
                                        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(intent)
                                }
                            },
                        )
                    }
                }
            }
        }

        // Fixed action bar in select mode.
        if (selectMode && selected.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
                contentAlignment = Alignment.BottomCenter,
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 20.dp),
                    shape = RoundedCornerShape(25.dp),
                    color = DlDanger,
                    shadowElevation = 8.dp,
                ) {
                    Button(
                        onClick = {
                            selected.forEach { id -> viewModel.delete(id) }
                            selected = emptySet()
                            selectMode = false
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = DlDanger,
                            contentColor = Color(0xFF0F1116),
                        ),
                        shape = RoundedCornerShape(25.dp),
                    ) {
                        Text(
                            text = "Delete ${selected.size} item${if (selected.size == 1) "" else "s"}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    count: Int,
    iconColor: Color,
    onSelectAll: (() -> Unit)? = null,
    selectMode: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 5.dp, end = 4.dp, top = 4.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // section icon (download/downloaded)
        Box(
            modifier = Modifier.size(18.dp),
            contentAlignment = Alignment.Center,
        ) {
            // simple circle indicator
            androidx.compose.foundation.Canvas(modifier = Modifier.size(18.dp)) {
                drawCircle(color = iconColor, radius = 6.dp.toPx())
            }
        }
        Text(
            text = "$title ($count)",
            color = DlText,
            fontSize = 16.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (-0.2).sp,
        )
        if (selectMode && onSelectAll != null) {
            androidx.compose.material3.TextButton(
                onClick = onSelectAll,
                modifier = Modifier.padding(start = 4.dp),
            ) {
                Text(
                    text = "Select all",
                    color = DlPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}
