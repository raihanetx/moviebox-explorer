package com.example.movieboxexplorer.ui

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.runtime.getValue
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.movieboxexplorer.data.LanguageInfo
import com.example.movieboxexplorer.data.TitleInfo

/** Bottom sheets that ask where to watch/download: language, season, episode, quality. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun EpisodePickerSheet(
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
            .verticalScroll(rememberScrollState())
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

        if (!info.isMovie && seasons.size > 1) {
            InfoSection("Season") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    seasons.forEach { number ->
                        val count = language?.let { episodesIn(it, number) } ?: 0
                        PickerChip(
                            text = if (count > 0) "S$number ($count)" else "S$number",
                            selected = number == season,
                            onClick = { onSelectSeason(number) },
                        )
                    }
                }
            }
        }

        if (episodes > 1) {
            InfoSection("Episode · $episodes") {
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

/**
 * The guided download flow: language → (season/episode for series) → quality.
 * Movies skip the episode step automatically because they report a single season
 * with a single episode.
 */
@Composable
internal fun DownloadSheet(
    info: TitleInfo,
    selection: EpisodeSelection?,
    resolving: Boolean,
    onSelectLanguage: (LanguageInfo) -> Unit,
    onSelectSeason: (Int) -> Unit,
    onSelectEpisode: (Int) -> Unit,
    onDownload: (Int?) -> Unit,
    seasonsOf: (LanguageInfo) -> List<Int>,
    episodesIn: (LanguageInfo, Int) -> Int,
    resolutionsFor: (LanguageInfo, Int) -> List<Int>,
) {
    val language = selection?.language
    val season = selection?.season ?: 1
    val episode = selection?.episode ?: 1
    val seasons = language?.let(seasonsOf).orEmpty()
    val episodes = language?.let { episodesIn(it, season) } ?: 0
    val qualities = language?.let { resolutionsFor(it, season) }.orEmpty()

    // 0 = "best available"; a real resolution overrides it.
    var quality by remember(language?.detailPath, language?.code, season) {
        mutableStateOf(0)
    }
    val effectiveQuality = quality.takeIf { it > 0 }
    val selectedLabel = effectiveQuality?.let { "${it}p" } ?: "Best"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
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
            text = if (info.isMovie) "Download movie" else "Download episode",
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

        if (!info.isMovie && seasons.size > 1) {
            InfoSection("Season") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    seasons.forEach { number ->
                        val count = language?.let { episodesIn(it, number) } ?: 0
                        PickerChip(
                            text = if (count > 0) "S$number ($count)" else "S$number",
                            selected = number == season,
                            onClick = { onSelectSeason(number) },
                        )
                    }
                }
            }
        }

        if (episodes > 1) {
            InfoSection("Episode · $episodes") {
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

        InfoSection("Quality") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PickerChip(
                    text = "BEST",
                    selected = quality == 0,
                    onClick = { quality = 0 },
                )
                qualities.forEach { resolution ->
                    PickerChip(
                        text = "${resolution}p",
                        selected = quality == resolution,
                        onClick = { quality = resolution },
                    )
                }
            }
            if (qualities.isEmpty()) {
                Text(
                    text = "Uploader did not report per-quality counts; the best available file is used.",
                    modifier = Modifier.padding(top = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextTertiary,
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        Button(
            onClick = { onDownload(effectiveQuality) },
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
                    text = if (info.isMovie) {
                        "Download · $selectedLabel"
                    } else {
                        "Download S$season E$episode · $selectedLabel"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
internal fun PickerChip(
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