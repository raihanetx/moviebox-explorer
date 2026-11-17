package com.example.movieboxexplorer.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.movieboxexplorer.data.LanguageFilter
import com.example.movieboxexplorer.data.LanguageInfo
import com.example.movieboxexplorer.data.Downloads
import com.example.movieboxexplorer.data.PlaybackSource
import com.example.movieboxexplorer.data.RiskFlags
import com.example.movieboxexplorer.data.RiskLevel
import com.example.movieboxexplorer.data.Subject
import com.example.movieboxexplorer.data.SubjectBrief
import com.example.movieboxexplorer.data.TitleInfo
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    viewModel: DetailViewModel,
    allowAdult: Boolean,
    onEnableAdult: () -> Unit,
    onBack: () -> Unit,
    onPlayEpisode: (PlaybackSource, String) -> Unit,
    onPlayTrailer: () -> Unit,
    onOpen: (SubjectBrief) -> Unit,
    onOpenDownloads: () -> Unit,
    autoOpenDownload: Boolean = false,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val related by viewModel.related.collectAsStateWithLifecycle()
    val selection by viewModel.selection.collectAsStateWithLifecycle()
    val playback by viewModel.playback.collectAsStateWithLifecycle()
    val download by viewModel.download.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val downloads = remember { Downloads(context) }

    val listState = rememberLazyListState()
    val scrolled by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 12
        }
    }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var showInfo by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }
    var downloaded by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }
    var showPicker by remember { mutableStateOf(false) }
    var showDownload by remember { mutableStateOf(false) }
    var autoDownloadOpened by remember { mutableStateOf(false) }

    // A poster download button navigates here with autoOpenDownload — open the
    // guided download sheet as soon as the title is ready, once.
    LaunchedEffect(autoOpenDownload, state) {
        if (autoOpenDownload && !autoDownloadOpened && state is DetailUiState.Ready) {
            autoDownloadOpened = true
            showDownload = true
        }
    }

    val topBarColor by animateColorAsState(
        targetValue = if (scrolled) Color(0xCC0F1116) else Color.Transparent,
        label = "topBarColor",
    )

    // The episode resolved — hand it to the player and close the picker.
    LaunchedEffect(playback) {
        val ready = playback as? PlaybackUiState.Ready ?: return@LaunchedEffect
        showPicker = false
        onPlayEpisode(ready.source, ready.label)
    }

    // A download request resolved — hand it to Android's DownloadManager.
    LaunchedEffect(download) {
        val request = download ?: return@LaunchedEffect
        downloads.enqueue(
            title = request.title,
            url = request.url,
            headers = request.headers,
            fileName = request.fileName,
            meta = request.meta,
        )
        viewModel.clearDownload()
        val result = snackbarHostState.showSnackbar(
            message = "Download started — see Downloads",
            actionLabel = "View",
            withDismissAction = true,
        )
        if (result == SnackbarResult.ActionPerformed) {
            onOpenDownloads()
        }
    }

    LaunchedEffect(playback) {
        val failed = playback as? PlaybackUiState.Failed ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(failed.message)
    }

    val blocked = (state as? DetailUiState.Ready)?.info?.risk?.adult == true && !allowAdult

    if (blocked) {
        AdultTitleBlockedDialog(
            title = (state as? DetailUiState.Ready)?.info?.subject?.title.orEmpty(),
            onAllow = onEnableAdult,
            onDismiss = onBack,
        )
    }

    if (showInfo && state is DetailUiState.Ready) {
        ModalBottomSheet(
            onDismissRequest = { showInfo = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = Surface1,
        ) {
            InfoContent(
                info = (state as DetailUiState.Ready).info,
                onClose = { showInfo = false },
            )
        }
    }

    if (showPicker && state is DetailUiState.Ready) {
        ModalBottomSheet(
            onDismissRequest = { showPicker = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = Surface1,
        ) {
            EpisodePickerSheet(
                info = (state as DetailUiState.Ready).info,
                selection = selection,
                playback = playback,
                onSelectLanguage = viewModel::selectLanguage,
                onSelectSeason = viewModel::selectSeason,
                onSelectEpisode = viewModel::selectEpisode,
                onPlay = viewModel::play,
                seasonsOf = viewModel::seasonsOf,
                episodesIn = viewModel::episodesIn,
            )
        }
    }

    if (showDownload && state is DetailUiState.Ready) {
        ModalBottomSheet(
            onDismissRequest = { showDownload = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = Surface1,
        ) {
            DownloadSheet(
                info = (state as DetailUiState.Ready).info,
                selection = selection,
                resolving = playback is PlaybackUiState.Resolving,
                onSelectLanguage = viewModel::selectLanguage,
                onSelectSeason = viewModel::selectSeason,
                onSelectEpisode = viewModel::selectEpisode,
                onDownload = { quality ->
                    viewModel.download(quality)
                    showDownload = false
                    downloaded = true
                    scope.launch {
                        snackbarHostState.showSnackbar("Starting download…")
                    }
                },
                seasonsOf = viewModel::seasonsOf,
                episodesIn = viewModel::episodesIn,
                resolutionsFor = viewModel::resolutionsFor,
            )
        }
    }

    Scaffold(
        containerColor = Surface0,
        topBar = {
            TopAppBar(
                title = {
                    val alpha by animateFloatAsState(
                        targetValue = if (scrolled) 1f else 0f,
                        label = "titleAlpha",
                    )
                    Text(
                        text = (state as? DetailUiState.Ready)?.info?.subject?.title.orEmpty(),
                        color = Color.White.copy(alpha = alpha),
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = topBarColor,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        when (val current = state) {
            is DetailUiState.Loading -> LoadingBox("Loading title…")

            is DetailUiState.Error -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(current.message, color = TextSecondary)
                TextButton(onClick = viewModel::load) { Text("Retry") }
            }

            is DetailUiState.Ready -> LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 32.dp),
            ) {
                item { Hero(current.info.subject) }

                item {
                    DetailSheet(
                        info = current.info,
                        related = related,
                        saved = saved,
                        downloaded = downloaded,
                        expanded = expanded,
                        onToggleExpand = { expanded = !expanded },
                        onPlay = { showPicker = true },
                        onPlayTrailer = onPlayTrailer,
                        onToggleSaved = {
                            saved = !saved
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    if (saved) "Added to My List" else "Removed from My List"
                                )
                            }
                        },
                        onToggleDownload = {
                            showDownload = true
                        },
                        onShowInfo = { showInfo = true },
                        onOpen = onOpen,
                    )
                }
            }
        }
    }
}

