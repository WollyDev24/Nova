package com.livewall.app.ui.theme

import android.graphics.Color as AndroidColor
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb

@Immutable
data class SeedPalette(
    val name: String,
    val seed: Color,
) {
    fun scheme(dark: Boolean): ColorScheme =
        if (dark) buildDarkScheme(seed) else buildLightScheme(seed)
}

val SeedPalettes: List<SeedPalette> = listOf(
    SeedPalette(name = "Cosmic Violet", seed = Color(0xFF6750A4)),
    SeedPalette(name = "Electric Cyan", seed = Color(0xFF00677F)),
    SeedPalette(name = "Hyper Jade", seed = Color(0xFF006C4C)),
    SeedPalette(name = "Sunset Amber", seed = Color(0xFF8B5000)),
    SeedPalette(name = "Obsidian Slate", seed = Color(0xFF455A64))
)

private fun Color.shiftHue(degrees: Float): Color {
    val hsv = FloatArray(3)
    AndroidColor.colorToHSV(toArgb(), hsv)
    hsv[0] = (hsv[0] + degrees + 360f) % 360f
    return Color(AndroidColor.HSVToColor(hsv))
}

private fun Color.adjustTone(saturationScale: Float, lightness: Float): Color {
    val hsv = FloatArray(3)
    AndroidColor.colorToHSV(toArgb(), hsv)
    hsv[1] = (hsv[1] * saturationScale).coerceIn(0f, 1f)
    val rgb = AndroidColor.HSVToColor(hsv)
    return lerp(Color(rgb), Color.White, lightness)
}

private fun Color.darken(fraction: Float): Color = lerp(this, Color.Black, fraction)
private fun Color.lighten(fraction: Float): Color = lerp(this, Color.White, fraction)
private fun Color.onColor(): Color = if (luminance() > 0.45f) Color.Black else Color.White

fun buildLightScheme(seed: Color): ColorScheme {
    val primary = seed
    val primaryContainer = primary.lighten(0.65f)
    val onPrimaryContainer = primary.darken(0.65f)

    val secondarySeed = seed.shiftHue(28f).adjustTone(0.55f, 0.10f)
    val secondary = secondarySeed
    val secondaryContainer = secondary.lighten(0.55f)
    val onSecondaryContainer = secondary.darken(0.6f)

    val tertiarySeed = seed.shiftHue(-32f).adjustTone(0.6f, 0.12f)
    val tertiary = tertiarySeed
    val tertiaryContainer = tertiary.lighten(0.5f)
    val onTertiaryContainer = tertiary.darken(0.6f)

    val surface = Color(0xFFFAF8FF)
    val onSurface = Color(0xFF191B23)
    val surfaceContainerLowest = Color(0xFFFFFFFF)
    val surfaceContainerLow = surface.lighten(0.25f)
    val surfaceContainer = surface.darken(0.04f)
    val surfaceContainerHigh = surface.darken(0.08f)
    val surfaceContainerHighest = surface.darken(0.12f)
    val surfaceVariant = surface.darken(0.12f)
    val onSurfaceVariant = surface.darken(0.55f)
    val outline = surface.darken(0.45f)
    val outlineVariant = surface.darken(0.18f)

    return lightColorScheme(
        primary = primary,
        onPrimary = primary.onColor(),
        primaryContainer = primaryContainer,
        onPrimaryContainer = onPrimaryContainer,
        secondary = secondary,
        onSecondary = secondary.onColor(),
        secondaryContainer = secondaryContainer,
        onSecondaryContainer = onSecondaryContainer,
        tertiary = tertiary,
        onTertiary = tertiary.onColor(),
        tertiaryContainer = tertiaryContainer,
        onTertiaryContainer = onTertiaryContainer,
        background = surface,
        onBackground = onSurface,
        surface = surface,
        onSurface = onSurface,
        surfaceVariant = surfaceVariant,
        onSurfaceVariant = onSurfaceVariant,
        surfaceTint = primary,
        inverseSurface = Color(0xFF2E3038),
        inverseOnSurface = Color(0xFFF0F0F8),
        inversePrimary = primary.lighten(0.35f),
        error = Color(0xFFBA1A1A),
        onError = Color.White,
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF410002),
        outline = outline,
        outlineVariant = outlineVariant,
        scrim = Color.Black,
        surfaceBright = surface.lighten(0.05f),
        surfaceDim = surface.darken(0.1f),
        surfaceContainer = surfaceContainer,
        surfaceContainerHigh = surfaceContainerHigh,
        surfaceContainerHighest = surfaceContainerHighest,
        surfaceContainerLow = surfaceContainerLow,
        surfaceContainerLowest = surfaceContainerLowest,
        primaryFixed = primaryContainer,
        primaryFixedDim = primary.lighten(0.35f),
        onPrimaryFixed = onPrimaryContainer,
        onPrimaryFixedVariant = primary.darken(0.42f),
        secondaryFixed = secondaryContainer,
        secondaryFixedDim = secondary.lighten(0.3f),
        onSecondaryFixed = onSecondaryContainer,
        onSecondaryFixedVariant = secondary.darken(0.4f),
        tertiaryFixed = tertiaryContainer,
        tertiaryFixedDim = tertiary.lighten(0.3f),
        onTertiaryFixed = onTertiaryContainer,
        onTertiaryFixedVariant = tertiary.darken(0.4f)
    )
}

fun buildDarkScheme(seed: Color): ColorScheme {
    val primary = seed.lighten(0.45f)
    val onPrimary = seed.darken(0.65f)
    val primaryContainer = seed.darken(0.2f)
    val onPrimaryContainer = seed.lighten(0.65f)

    val secondarySeed = seed.shiftHue(28f).adjustTone(0.5f, 0.12f)
    val secondary = secondarySeed.lighten(0.42f)
    val onSecondary = secondary.darken(0.6f)
    val secondaryContainer = secondarySeed.darken(0.2f)
    val onSecondaryContainer = secondary.lighten(0.45f)

    val tertiarySeed = seed.shiftHue(-32f).adjustTone(0.55f, 0.14f)
    val tertiary = tertiarySeed.lighten(0.4f)
    val onTertiary = tertiary.darken(0.6f)
    val tertiaryContainer = tertiarySeed.darken(0.18f)
    val onTertiaryContainer = tertiary.lighten(0.45f)

    val surface = Color(0xFF111318)
    val onSurface = Color(0xFFE2E2E9)
    val surfaceContainerLowest = Color(0xFF0C0E13)
    val surfaceContainerLow = surface.lighten(0.07f)
    val surfaceContainer = surface.lighten(0.12f)
    val surfaceContainerHigh = surface.lighten(0.17f)
    val surfaceContainerHighest = surface.lighten(0.22f)
    val surfaceVariant = surface.lighten(0.25f)
    val onSurfaceVariant = surface.lighten(0.55f)
    val outline = surface.lighten(0.4f)
    val outlineVariant = surface.lighten(0.25f)

    return darkColorScheme(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = primaryContainer,
        onPrimaryContainer = onPrimaryContainer,
        secondary = secondary,
        onSecondary = onSecondary,
        secondaryContainer = secondaryContainer,
        onSecondaryContainer = onSecondaryContainer,
        tertiary = tertiary,
        onTertiary = onTertiary,
        tertiaryContainer = tertiaryContainer,
        onTertiaryContainer = onTertiaryContainer,
        background = surface,
        onBackground = onSurface,
        surface = surface,
        onSurface = onSurface,
        surfaceVariant = surfaceVariant,
        onSurfaceVariant = onSurfaceVariant,
        surfaceTint = primary,
        inverseSurface = Color(0xFFE2E2E9),
        inverseOnSurface = Color(0xFF2E3038),
        inversePrimary = seed,
        error = Color(0xFFFFB4AB),
        onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6),
        outline = outline,
        outlineVariant = outlineVariant,
        scrim = Color.Black,
        surfaceBright = surface.lighten(0.35f),
        surfaceDim = surface,
        surfaceContainer = surfaceContainer,
        surfaceContainerHigh = surfaceContainerHigh,
        surfaceContainerHighest = surfaceContainerHighest,
        surfaceContainerLow = surfaceContainerLow,
        surfaceContainerLowest = surfaceContainerLowest,
        primaryFixed = primaryContainer,
        primaryFixedDim = seed.lighten(0.25f),
        onPrimaryFixed = onPrimaryContainer,
        onPrimaryFixedVariant = seed.lighten(0.6f),
        secondaryFixed = secondaryContainer,
        secondaryFixedDim = secondary.darken(0.15f),
        onSecondaryFixed = onSecondaryContainer,
        onSecondaryFixedVariant = secondary.lighten(0.5f),
        tertiaryFixed = tertiaryContainer,
        tertiaryFixedDim = tertiary.darken(0.15f),
        onTertiaryFixed = onTertiaryContainer,
        onTertiaryFixedVariant = tertiary.lighten(0.5f)
    )
}
