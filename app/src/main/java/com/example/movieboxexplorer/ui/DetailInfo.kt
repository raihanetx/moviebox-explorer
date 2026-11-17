package com.example.movieboxexplorer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.movieboxexplorer.data.LanguageInfo
import com.example.movieboxexplorer.data.RiskFlags
import com.example.movieboxexplorer.data.RiskLevel
import com.example.movieboxexplorer.data.TitleInfo

/** The Info sheet: raw API fields and the language coverage report. */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun InfoContent(info: TitleInfo, onClose: () -> Unit) {
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
internal fun LanguageCoverageRow(
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
internal fun RawLine(label: String, value: String) {
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
internal fun InfoChip(
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

internal fun qualityLabel(resolution: Int): Pair<String, String> = when {
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
