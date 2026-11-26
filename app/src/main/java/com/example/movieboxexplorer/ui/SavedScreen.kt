package com.example.movieboxexplorer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.movieboxexplorer.data.SavedEntry
import com.example.movieboxexplorer.data.SavedList
import com.example.movieboxexplorer.data.SubjectBrief
import com.example.movieboxexplorer.data.relativeSavedAt
import com.example.movieboxexplorer.data.savedKey

/**
 * The user's saved titles ("My List"), persisted on device.
 *
 * Two sections — **Movies** and **TV**. Each row: poster, title, metadata line
 * + how long ago it was saved, and a right-side unsave button.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedScreen(
    savedList: SavedList,
    onBack: () -> Unit,
    onOpen: (SubjectBrief) -> Unit,
) {
    var entries by remember { mutableStateOf(savedList.saved()) }

    fun reload() { entries = savedList.saved() }

    val sorted = entries.sortedByDescending { it.savedAt }
    val movies = sorted.filter { it.isMovie }
    val tv = sorted.filter { !it.isMovie }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Saved", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        if (entries.isEmpty()) {
            Box(modifier = Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Nothing saved yet", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Tap “Add to My List” on any movie or series to save it here.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (movies.isNotEmpty()) {
                    item(key = "movies-h") { SectionHeader("Movies", movies.size) }
                    items(movies, key = { it.subject.savedKey() + "|m" }) { entry ->
                        SavedRow(entry = entry, onOpen = onOpen, onUnsave = { savedList.unsave(it); reload() })
                    }
                }
                if (tv.isNotEmpty()) {
                    item(key = "tv-h") { SectionHeader("TV", tv.size) }
                    items(tv, key = { it.subject.savedKey() + "|t" }) { entry ->
                        SavedRow(entry = entry, onOpen = onOpen, onUnsave = { savedList.unsave(it); reload() })
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, count: Int) {
    Text(
        text = "$title ($count)",
        fontSize = 16.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = (-0.2).sp,
        modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 12.dp, bottom = 6.dp),
    )
}

@Composable
private fun SavedRow(
    entry: SavedEntry,
    onOpen: (SubjectBrief) -> Unit,
    onUnsave: (String) -> Unit,
) {
    val subject = entry.subject
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpen(subject) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .width(96.dp)
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            if (subject.cover?.url?.isNotBlank() == true) {
                AsyncImage(
                    model = subject.cover?.url,
                    contentDescription = subject.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize(),
                )
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = subject.title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = metaLine(subject, entry.savedAt),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        // Right-side unsave.
        IconButton(
            onClick = { onUnsave(subject.savedKey()) },
            modifier = Modifier.size(36.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(28.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Unsave",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

private fun metaLine(subject: SubjectBrief, savedAt: Long): String {
    val bits = mutableListOf<String>()
    subject.genre.takeIf { it.isNotBlank() }?.let { bits.add(it) }
    subject.releaseDate.take(4).takeIf { it.isNotBlank() }?.let { bits.add(it) }
    subject.imdbRatingValue.takeIf { it.isNotBlank() && it != "0" }?.let { bits.add("★ $it") }
    bits.add(relativeSavedAt(savedAt))
    return bits.joinToString(" · ")
}

