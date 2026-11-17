package com.example.movieboxexplorer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.movieboxexplorer.data.Subject

/** Full-bleed cover hero with the title/rating overlay. */
@Composable
internal fun Hero(subject: Subject) {
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

