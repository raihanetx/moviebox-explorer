package com.example.movieboxexplorer.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val MovieBoxDark = darkColorScheme(
    primary = Color(0xFFFF4D5A),
    onPrimary = Color.White,
    secondary = Color(0xFF22D3EE),
    background = Color(0xFF0B0B0F),
    onBackground = Color(0xFFF2F2F5),
    surface = Color(0xFF15151A),
    onSurface = Color(0xFFF2F2F5),
    surfaceVariant = Color(0xFF1E1E26),
    onSurfaceVariant = Color(0xFFA8A8B3),
    outline = Color(0xFF2A2A35),
)

private val MovieBoxLight = lightColorScheme(
    primary = Color(0xFFD92D3A),
    onPrimary = Color.White,
    secondary = Color(0xFF0288D1),
    background = Color(0xFFF7F7FA),
    surface = Color.White,
    surfaceVariant = Color(0xFFEEF0F4),
)

@Composable
fun MovieBoxTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) MovieBoxDark else MovieBoxLight,
        content = content,
    )
}