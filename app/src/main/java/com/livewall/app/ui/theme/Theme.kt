package com.livewall.app.ui.theme

import android.os.Build
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalOverscrollConfiguration
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

@Stable
class LiveWallThemeState {
    var isDarkTheme by mutableStateOf(false)
    var useDynamicColor by mutableStateOf(true)
    var seedPalette by mutableStateOf(SeedPalettes[0])
    var useExpressiveMotion by mutableStateOf(true)
}

@Composable
fun rememberLiveWallThemeState(): LiveWallThemeState {
    val systemDark = isSystemInDarkTheme()
    return remember {
        LiveWallThemeState().apply {
            isDarkTheme = systemDark
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalFoundationApi::class)
@Composable
fun LiveWallTheme(
    state: LiveWallThemeState = rememberLiveWallThemeState(),
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = when {
        state.useDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (state.isDarkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        else -> state.seedPalette.scheme(state.isDarkTheme)
    }

    val motionScheme = if (state.useExpressiveMotion) {
        MotionScheme.expressive()
    } else {
        MotionScheme.standard()
    }

    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        motionScheme = motionScheme
    ) {
        @Suppress("DEPRECATION")
        CompositionLocalProvider(
            LocalOverscrollConfiguration provides null,
            LocalOverscrollFactory provides null,
            content = content
        )
    }
}
