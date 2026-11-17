package com.example.movieboxexplorer.ui

import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import coil.compose.AsyncImage
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TextButton
import com.example.movieboxexplorer.data.RiskLevel
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.movieboxexplorer.data.RiskFlags
import com.example.movieboxexplorer.data.SubjectBrief
import com.example.movieboxexplorer.data.TitleInfo

/** The sheet below the hero: meta row, action buttons, badges. */
@Composable
internal fun DetailSheet(
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
internal fun MetaRow(info: TitleInfo) {
    val subject = info.subject
    val genres = subject.genre.split(",").map { it.trim() }.filter { it.isNotBlank() }
    val seasonNumber = if (subject.season > 0) subject.season else info.referenceSeasons
    val season = if (!info.isMovie && seasonNumber > 0) "Season $seasonNumber" else null

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Type first — the app must never make a movie look like a series.
        Badge(
            text = when (subject.subjectType) {
                1 -> "Movie"
                2 -> "Series"
                6 -> "Music"
                else -> "Title"
            },
            color = when (subject.subjectType) {
                1 -> Color(0xFF4FC3F7)
                2 -> Color(0xFF9CCC65)
                else -> ImdbGold
            },
        )
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
internal fun Dot() {
    Box(
        modifier = Modifier
            .size(3.dp)
            .clip(RoundedCornerShape(50))
            .background(TextTertiary),
    )
}

@Composable
internal fun InfoSection(title: String, content: @Composable () -> Unit) {
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

