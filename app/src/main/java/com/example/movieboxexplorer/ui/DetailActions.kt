package com.example.movieboxexplorer.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.background
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.movieboxexplorer.data.RiskFlags
import com.example.movieboxexplorer.data.RiskLevel

/** Action buttons: download, save, info — the bottom row of the hero card. */
@Composable
internal fun ActionsRow(
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

        // A labeled button, not an icon-only cart — users could not tell what it does.
        OutlinedButton(
            onClick = onToggleDownload,
            modifier = Modifier.height(44.dp),
            shape = RoundedCornerShape(22.dp),
            border = BorderStroke(1.dp, Surface3),
            contentPadding = PaddingValues(horizontal = 14.dp),
        ) {
            Icon(
                imageVector = if (downloaded) Icons.Default.Check else Icons.Default.ShoppingCart,
                contentDescription = null,
                modifier = Modifier.size(17.dp),
                tint = if (downloaded) PrimaryFg else TextPrimary,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = if (downloaded) "Queued" else "Download",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = if (downloaded) PrimaryFg else TextPrimary,
                maxLines = 1,
            )
        }
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
internal fun ActionIcon(
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
internal fun RiskBadges(risk: RiskFlags) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (risk.unlisted) Badge("Unlisted", Color(0xFF8E24AA))
        if (risk.adult) Badge("18+", Color(0xFFD32F2F))
        if (risk.forbidden) Badge("Unavailable", Color(0xFF757575))
    }
}

