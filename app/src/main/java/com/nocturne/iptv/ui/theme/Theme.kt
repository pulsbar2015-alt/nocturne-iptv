package com.nocturne.iptv.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val NocturneScheme = darkColorScheme(
    primary = NocturnePalette.Blood,
    onPrimary = NocturnePalette.Bone,
    primaryContainer = NocturnePalette.Coffin,
    onPrimaryContainer = NocturnePalette.Bone,
    secondary = NocturnePalette.Ember,
    onSecondary = NocturnePalette.Abyss,
    tertiary = NocturnePalette.Gleam,
    onTertiary = NocturnePalette.Abyss,
    background = NocturnePalette.Abyss,
    onBackground = NocturnePalette.Bone,
    surface = NocturnePalette.Crypt,
    onSurface = NocturnePalette.Bone,
    surfaceVariant = NocturnePalette.Coffin,
    onSurfaceVariant = NocturnePalette.Ash,
    outline = Color(0xFF2A2333),
    error = NocturnePalette.Ember,
    onError = NocturnePalette.Bone
)

/** Nocturne is always dark — the house has no lights. */
@Composable
fun NocturneTheme(
    @Suppress("UNUSED_PARAMETER") darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = NocturneScheme,
        typography = NocturneTypography,
        content = content
    )
}