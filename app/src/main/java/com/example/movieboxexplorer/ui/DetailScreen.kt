package com.example.movieboxexplorer.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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

private val Surface0 = Color(0xFF0F1116)
private val Surface1 = Color(0xFF181A20)
private val Surface2 = Color(0xFF1F2229)
private val Surface3 = Color(0xFF272A32)
private val TextPrimary = Color(0xFFE8E6EC)
private val TextSecondary = Color(0xFFB5B3BE)
private val TextTertiary = Color(0xFF7A7883)
private val PrimarySoft = Color(0xFFFFB4A8)
private val PrimaryBg = Color(0xFF4A2A26)
private val PrimaryFg = Color(0xFFFFDAD3)
private val OriginalGold = Color(0xFFE6C27A)
private val ImdbGold = Color(0xFFF5C518)
private val Hair = Color(0x0FFFFFFF)

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
        )
        viewModel.clearDownload()
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
                            viewModel.download()
                            downloaded = true
                            scope.launch {
                                snackbarHostState.showSnackbar("Download started")
                            }
                        },
                        onShowInfo = { showInfo = true },
                        onOpen = onOpen,
                    )
                }
            }
        }
    }
}

@Composable
private fun Hero(subject: Subject) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(250.dp)
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF150C25), Color(0xFF0A0512)),
                ),
            ),
    ) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()

        // The cover artwork. Without this the hero is only the gradient placeholder.
        AsyncImage(
            model = subject.cover?.url,
            contentDescription = subject.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color(0x8C9655C8), Color.Transparent),
                        center = androidx.compose.ui.geometry.Offset(w * 0.26f, h * 0.26f),
                        radius = w * 0.58f,
                    ),
                ),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color(0x80DC6991), Color.Transparent),
                        center = androidx.compose.ui.geometry.Offset(w * 0.78f, h * 0.20f),
                        radius = w * 0.46f,
                    ),
                ),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color(0x59375AC8), Color.Transparent),
                        center = androidx.compose.ui.geometry.Offset(w * 0.55f, h * 0.62f),
                        radius = w * 0.40f,
                    ),
                ),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color(0x520F1116),
                        0.18f to Color.Transparent,
                        0.48f to Color.Transparent,
                        0.68f to Color(0x57181A20),
                        0.86f to Color(0xC2181A20),
                        0.96f to Color(0xF7181A20),
                        1f to Surface1,
                    ),
                ),
        )
    }
}

@Composable
private fun DetailSheet(
    info: TitleInfo,
    related: List<SubjectBrief>,
    saved: Boolean,
    downloaded: Boolean,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    onPlay: () -> Unit,
    onPlayTrailer: () -> Unit,
    onToggleSaved: () -> Unit,
    onToggleDownload: () -> Unit,
    onShowInfo: () -> Unit,
    onOpen: (SubjectBrief) -> Unit,
) {
    val subject = info.subject

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Surface1)
            .padding(horizontal = 20.dp)
            .padding(top = 112.dp, bottom = 40.dp),
    ) {
        Text(
            text = subject.title,
            style = MaterialTheme.typography.headlineSmall.copy(
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.6).sp,
            ),
            color = TextPrimary,
        )

        Spacer(Modifier.height(8.dp))

        MetaRow(info)

        if (info.risk.level != RiskLevel.Normal) {
            Spacer(Modifier.height(10.dp))
            RiskBadges(info.risk)
        }

        Spacer(Modifier.height(18.dp))

        ActionsRow(
            saved = saved,
            downloaded = downloaded,
            onPlay = onPlay,
            onToggleSaved = onToggleSaved,
            onToggleDownload = onToggleDownload,
            onShowInfo = onShowInfo,
        )

        if (!info.isMovie) {
            TextButton(
                onClick = onPlayTrailer,
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                modifier = Modifier.padding(top = 4.dp),
            ) {
                Text(
                    text = "Play trailer instead",
                    color = PrimarySoft,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        if (subject.description.isNotBlank()) {
            Text(
                text = subject.description,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 13.5.sp,
                    lineHeight = 22.sp,
                ),
                color = TextSecondary,
                maxLines = if (expanded) Int.MAX_VALUE else 4,
                overflow = TextOverflow.Ellipsis,
            )
            if (subject.description.length > 150) {
                TextButton(
                    onClick = onToggleExpand,
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.padding(top = 2.dp),
                ) {
                    Text(
                        text = if (expanded) "Show less" else "Read more",
                        color = PrimarySoft,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }

        Spacer(Modifier.height(28.dp))

        if (related.isNotEmpty()) {
            Text(
                text = "More like this",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(12.dp))
            related.chunked(3).forEach { rowItems ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    rowItems.forEach { item ->
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onOpen(item) },
                        ) {
                            AsyncImage(
                                model = item.cover?.url,
                                contentDescription = null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(2f / 3f)
                                    .clip(RoundedCornerShape(11.dp))
                                    .background(Surface2),
                                contentScale = ContentScale.Crop,
                            )
                            Text(
                                text = item.title,
                                modifier = Modifier.padding(top = 8.dp, start = 2.dp, end = 2.dp),
                                style = MaterialTheme.typography.labelMedium,
                                color = TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    repeat(3 - rowItems.size) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun MetaRow(info: TitleInfo) {
    val subject = info.subject
    val genres = subject.genre.split(",").map { it.trim() }.filter { it.isNotBlank() }
    val seasonNumber = if (subject.season > 0) subject.season else info.referenceSeasons
    val season = if (!info.isMovie && seasonNumber > 0) "Season $seasonNumber" else null

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (subject.imdbRatingValue.isNotBlank()) {
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = null,
                tint = ImdbGold,
                modifier = Modifier.size(13.dp),
            )
            Text(
                text = subject.imdbRatingValue,
                color = TextPrimary,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
            )
        }
        subject.releaseDate.take(4).takeIf { it.isNotBlank() }?.let {
            Dot()
            Text(it, color = TextSecondary, style = MaterialTheme.typography.labelMedium)
        }
        genres.take(2).forEach {
            Dot()
            Text(it, color = TextSecondary, style = MaterialTheme.typography.labelMedium)
        }
        season?.let {
            Dot()
            Text(it, color = TextSecondary, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun Dot() {
    Box(
        modifier = Modifier
            .size(3.dp)
            .clip(RoundedCornerShape(50))
            .background(TextTertiary),
    )
}

@Composable
private fun ActionsRow(
    saved: Boolean,
    downloaded: Boolean,
    onPlay: () -> Unit,
    onToggleSaved: () -> Unit,
    onToggleDownload: () -> Unit,
    onShowInfo: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Button(
            onClick = onPlay,
            modifier = Modifier
                .weight(1f)
                .height(44.dp),
            shape = RoundedCornerShape(22.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.White,
                contentColor = Surface0,
            ),
            contentPadding = PaddingValues(horizontal = 16.dp),
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(17.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Play Now",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        ActionIcon(
            icon = if (downloaded) Icons.Default.Check else Icons.Default.ShoppingCart,
            active = downloaded,
            onClick = onToggleDownload,
            label = if (downloaded) "Remove download" else "Download",
        )
        ActionIcon(
            icon = if (saved) Icons.Default.Check else Icons.Default.Add,
            active = saved,
            onClick = onToggleSaved,
            label = if (saved) "Remove from My List" else "Add to My List",
        )
        ActionIcon(
            icon = Icons.Default.Info,
            active = false,
            onClick = onShowInfo,
            label = "More information",
        )
    }
}

@Composable
private fun ActionIcon(
    icon: ImageVector,
    active: Boolean,
    onClick: () -> Unit,
    label: String,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.size(44.dp),
        shape = RoundedCornerShape(22.dp),
        color = if (active) PrimaryBg else Surface2,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (active) PrimaryFg else TextSecondary,
                modifier = Modifier.size(19.dp),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun InfoContent(info: TitleInfo, onClose: () -> Unit) {
    val audio = info.languages.filter { !it.isSubtitle }
    val qualities = info.languages
        .flatMap { it.resolutions }
        .map { it.resolution }
        .distinct()
        .sorted()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 28.dp),
    ) {
        Box(
            modifier = Modifier
                .padding(bottom = 18.dp)
                .size(width = 40.dp, height = 4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Surface3),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Available on",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
            )
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = TextTertiary)
            }
        }

        InfoSection("Audio") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                audio.forEach { language ->
                    InfoChip(
                        text = language.label,
                        sub = if (language.isOriginal) "Original" else null,
                        original = language.isOriginal,
                    )
                }
            }
        }

        InfoSection("Language coverage") {
            if (info.languages.isEmpty()) {
                Text(
                    text = "No language data",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
            } else {
                info.languages.forEach { language ->
                    LanguageCoverageRow(
                        language = language,
                        referenceEpisodes = info.referenceEpisodes,
                        referenceSeasons = info.referenceSeasons,
                    )
                }
            }
        }

        InfoSection("Quality") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                qualities.forEach { resolution ->
                    val (label, value) = qualityLabel(resolution)
                    InfoChip(
                        text = label,
                        sub = value,
                        best = resolution == qualities.maxOrNull(),
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("HDR10", "Dolby Vision", "Dolby Atmos").forEach { badge ->
                    Surface(
                        shape = RoundedCornerShape(5.dp),
                        color = Surface2,
                        border = BorderStroke(1.dp, Hair),
                    ) {
                        Text(
                            text = badge,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary,
                        )
                    }
                }
            }
        }

        InfoSection("Raw data") {
            val subject = info.subject
            RawLine("id", subject.subjectId)
            RawLine("type", subject.subjectType.toString())
            RawLine("releaseDate", subject.releaseDate)
            RawLine("duration", subject.duration.toString())
            RawLine("country", subject.countryName)
            RawLine("imdb", "${subject.imdbRatingValue} (${subject.imdbRatingCount})")
            RawLine("genre", subject.genre)
            RawLine("detailPath", subject.detailPath)
            RawLine("webHighRisk", subject.webHighRisk.toString())
            RawLine("isPornographic", subject.isPornographic.toString())
            RawLine("subtitles", subject.subtitles)

            Spacer(Modifier.height(8.dp))
            Text(
                text = "resource.seasons",
                style = MaterialTheme.typography.labelSmall,
                color = TextTertiary,
                fontWeight = FontWeight.Bold,
            )
            val seasons = info.resource?.seasons.orEmpty()
            if (seasons.isEmpty()) {
                RawLine("resource", "not provided")
            } else {
                seasons.forEach { season ->
                    RawLine(
                        label = "S${season.se}",
                        value = "maxEp=${season.maxEp}, allEp=${season.allEp.ifBlank { "—" }}, " +
                            "resolutions=${season.resolutions.joinToString { "${it.resolution}:${it.epNum}" }}",
                    )
                }
            }
        }
    }
}

@Composable
private fun LanguageCoverageRow(
    language: LanguageInfo,
    referenceEpisodes: Int,
    referenceSeasons: Int,
) {
    val missingEpisodes = (referenceEpisodes - language.episodes).coerceAtLeast(0)
    val missingSeasons = (referenceSeasons - language.seasonCount).coerceAtLeast(0)

    val status = when {
        language.episodes == 0 -> "NOT AVAILABLE"
        missingEpisodes == 0 && missingSeasons == 0 -> "FULL"
        else -> "PARTIAL"
    }
    val statusColor = when (status) {
        "FULL" -> Color(0xFF22C55E)
        "PARTIAL" -> Color(0xFFF59E0B)
        else -> Color(0xFFEF4444)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = language.label,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
            )
            Text(
                text = status,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = statusColor,
            )
        }
        val episodesText = if (referenceEpisodes > 0) {
            "${language.episodes}/$referenceEpisodes episodes"
        } else {
            "${language.episodes} episodes"
        }
        Text(
            text = buildString {
                append(episodesText)
                if (missingEpisodes > 0) append(" · $missingEpisodes missing")
                if (missingSeasons > 0) append(" · $missingSeasons season(s) missing")
            },
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary,
        )
        val seasons = language.seasons
            .filter { it.episodes > 0 }
            .joinToString(" ") { "S${it.season}:${it.episodes}" }
        if (seasons.isNotBlank()) {
            Text(
                text = seasons,
                style = MaterialTheme.typography.labelSmall,
                color = TextTertiary,
            )
        }
    }
}

@Composable
private fun RawLine(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
    ) {
        Text(
            text = label,
            modifier = Modifier.width(110.dp),
            style = MaterialTheme.typography.labelSmall,
            color = TextTertiary,
        )
        Text(
            text = value.ifBlank { "—" },
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary,
        )
    }
}

@Composable
private fun InfoSection(title: String, content: @Composable () -> Unit) {
    Column(modifier = Modifier.padding(bottom = 22.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(bottom = 10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(RoundedCornerShape(50))
                    .background(PrimarySoft),
            )
            Text(
                text = title.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = TextTertiary,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp,
            )
        }
        content()
    }
}

@Composable
private fun InfoChip(
    text: String,
    sub: String? = null,
    original: Boolean = false,
    best: Boolean = false,
) {
    val container = when {
        original -> OriginalGold.copy(alpha = 0.10f)
        best -> PrimaryBg
        else -> Surface2
    }
    val content = when {
        original -> OriginalGold
        best -> PrimaryFg
        else -> TextSecondary
    }
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = container,
        border = if (original) BorderStroke(1.dp, OriginalGold.copy(alpha = 0.28f)) else null,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = content,
                fontWeight = FontWeight.SemiBold,
            )
            if (sub != null) {
                Spacer(Modifier.width(5.dp))
                Text(
                    text = sub,
                    style = MaterialTheme.typography.labelSmall,
                    color = content.copy(alpha = 0.7f),
                )
            }
        }
    }
}

private fun qualityLabel(resolution: Int): Pair<String, String> = when {
    resolution >= 2160 -> "UHD" to "${resolution}p"
    resolution >= 1080 -> "FHD" to "${resolution}p"
    resolution >= 720 -> "HD" to "${resolution}p"
    else -> "SD" to "${resolution}p"
}

// ---------------------------------------------------------------- risk badges

/**
 * The platform's own flags for this title.
 *
 * `Unlisted` means the web UI hides the title — the app can still open it.
 * `18+` means the title is adult content. `Unavailable` means the record exists but
 * must not be played.
 */
@Composable
private fun RiskBadges(risk: RiskFlags) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (risk.unlisted) Badge("Unlisted", Color(0xFF8E24AA))
        if (risk.adult) Badge("18+", Color(0xFFD32F2F))
        if (risk.forbidden) Badge("Unavailable", Color(0xFF757575))
    }
}

// ---------------------------------------------------------------- episode picker

/**
 * Picks the language, season and episode to play.
 *
 * Every language track is a separate upload with its own episode list, so the
 * episode numbers a track offers depend on the track that is selected — a Hindi dub
 * that only covers 11 of 12 episodes simply has no twelfth entry here.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EpisodePickerSheet(
    info: TitleInfo,
    selection: EpisodeSelection?,
    playback: PlaybackUiState,
    onSelectLanguage: (LanguageInfo) -> Unit,
    onSelectSeason: (Int) -> Unit,
    onSelectEpisode: (Int) -> Unit,
    onPlay: () -> Unit,
    seasonsOf: (LanguageInfo) -> List<Int>,
    episodesIn: (LanguageInfo, Int) -> Int,
) {
    val language = selection?.language
    val season = selection?.season ?: 1
    val episode = selection?.episode ?: 1
    val seasons = language?.let(seasonsOf).orEmpty()
    val episodes = language?.let { episodesIn(it, season) } ?: 0
    val resolving = playback is PlaybackUiState.Resolving
    val failure = playback as? PlaybackUiState.Failed

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 28.dp),
    ) {
        Box(
            modifier = Modifier
                .padding(bottom = 18.dp)
                .size(width = 40.dp, height = 4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Surface3),
        )

        Text(
            text = if (info.isMovie) "Play" else "Play episode",
            style = MaterialTheme.typography.titleLarge,
            color = TextPrimary,
            fontWeight = FontWeight.Bold,
        )

        InfoSection("Language") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                info.languages.forEach { item ->
                    PickerChip(
                        text = item.label,
                        selected = item.detailPath == language?.detailPath &&
                            item.code == language?.code,
                        onClick = { onSelectLanguage(item) },
                    )
                }
            }
        }

        if (seasons.size > 1) {
            InfoSection("Season") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    seasons.forEach { number ->
                        PickerChip(
                            text = "S$number",
                            selected = number == season,
                            onClick = { onSelectSeason(number) },
                        )
                    }
                }
            }
        }

        if (episodes > 1) {
            InfoSection("Episode") {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    (1..episodes).forEach { number ->
                        PickerChip(
                            text = number.toString(),
                            selected = number == episode,
                            onClick = { onSelectEpisode(number) },
                        )
                    }
                }
            }
        }

        if (failure != null) {
            Text(
                text = failure.message,
                modifier = Modifier.padding(top = 4.dp),
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFFF8A80),
            )
        }

        Spacer(Modifier.height(16.dp))

        Button(
            onClick = onPlay,
            enabled = !resolving && language != null,
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp),
            shape = RoundedCornerShape(23.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.White,
                contentColor = Surface0,
            ),
        ) {
            if (resolving) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = Surface0,
                )
            } else {
                Text(
                    text = if (info.isMovie) "Play" else "Play S$season E$episode",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun PickerChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (selected) PrimaryBg else Surface2,
        border = if (selected) BorderStroke(1.dp, PrimarySoft.copy(alpha = 0.4f)) else null,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) PrimaryFg else TextSecondary,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        )
    }
}