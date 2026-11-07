package com.livewall.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.livewall.app.data.WallpaperPreferences
import com.livewall.app.ui.screens.HomeScreen
import com.livewall.app.ui.theme.LiveWallTheme
import com.livewall.app.ui.theme.rememberLiveWallThemeState

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val prefs = WallpaperPreferences(applicationContext)

        setContent {
            val themeState = rememberLiveWallThemeState()

            LiveWallTheme(state = themeState) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    HomeScreen(
                        prefs = prefs,
                        themeState = themeState
                    )
                }
            }
        }
    }
}
