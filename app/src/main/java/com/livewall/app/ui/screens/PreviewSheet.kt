package com.livewall.app.ui.screens

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeMute
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.exoplayer.ExoPlayer
import com.livewall.app.data.PlaybackTrigger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.livewall.app.data.ScalingMode
import com.livewall.app.data.WallpaperPreferences
import com.livewall.app.model.WallpaperItem
import com.livewall.app.service.LiveWallpaperService
import com.livewall.app.service.WallpaperColorHelper
import com.livewall.app.ui.components.VideoPlayerView
import com.livewall.app.ui.theme.ShapePill

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PreviewSheet(
    wallpaper: WallpaperItem,
    prefs: WallpaperPreferences,
    onDismiss: () -> Unit,
    onApplied: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var playerInstance by remember { mutableStateOf<ExoPlayer?>(null) }
    var isMuted by remember { mutableStateOf(prefs.isMuted) }
    var currentTrigger by remember { mutableStateOf(prefs.trigger) }

    // Full-screen immersive dialog: preview fills the entire display edge-to-edge
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            // 1. FULL-SCREEN VIDEO PLAYER (Edge-to-Edge)
            VideoPlayerView(
                videoUri = wallpaper.videoUri,
                scalingMode = ScalingMode.CROP,
                isMuted = isMuted,
                autoPlay = true,
                onPlayerReady = { playerInstance = it },
                modifier = Modifier.fillMaxSize()
            )

            // 2. Top Scrim Gradient & Top Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Black.copy(alpha = 0.75f),
                                Color.Black.copy(alpha = 0.40f),
                                Color.Transparent
                            )
                        )
                    )
            )

            // Top Controls Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Floating Close Pill
                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.60f),
                    contentColor = Color.White,
                    shadowElevation = 6.dp,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onDismiss)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(imageVector = Icons.Rounded.Close, contentDescription = "Close", modifier = Modifier.size(22.dp))
                    }
                }

                // Title & Info Floating Pill
                Surface(
                    shape = ShapePill,
                    color = Color.Black.copy(alpha = 0.60f),
                    contentColor = Color.White,
                    shadowElevation = 6.dp
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = wallpaper.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "${wallpaper.collection} • ${wallpaper.durationSec}s",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                }

                // Action Pills: Sync Palette, Replay & Sound
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Sync Samsung / Material You Color Palette Button
                    Surface(
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.60f),
                        contentColor = Color.White,
                        shadowElevation = 6.dp,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .clickable {
                                scope.launch(Dispatchers.IO) {
                                    val ok = WallpaperColorHelper.syncStaticBitmapForSamsungOneUI(context, wallpaper)
                                    kotlinx.coroutines.withContext(Dispatchers.Main) {
                                        if (ok) {
                                            Toast.makeText(
                                                context,
                                                "Synced ${wallpaper.title} colors! Opening Wallpaper & style...",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                            WallpaperColorHelper.openSystemPaletteSettings(context)
                                        } else {
                                            Toast.makeText(context, "Could not sync static wallpaper palette", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(imageVector = Icons.Rounded.Palette, contentDescription = "Sync Palette", modifier = Modifier.size(20.dp))
                        }
                    }

                    // Replay Button
                    Surface(
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.60f),
                        contentColor = Color.White,
                        shadowElevation = 6.dp,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .clickable {
                                playerInstance?.seekTo(0)
                                playerInstance?.play()
                            }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(imageVector = Icons.Rounded.Replay, contentDescription = "Replay", modifier = Modifier.size(20.dp))
                        }
                    }

                    // Mute Button
                    Surface(
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.60f),
                        contentColor = Color.White,
                        shadowElevation = 6.dp,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .clickable { isMuted = !isMuted }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isMuted) Icons.AutoMirrored.Rounded.VolumeMute else Icons.AutoMirrored.Rounded.VolumeUp,
                                contentDescription = "Toggle Mute",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            // 3. Bottom Scrim Gradient for floating controls
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.50f),
                                Color.Black.copy(alpha = 0.85f)
                            )
                        )
                    )
            )

            // 4. FLOATING TRIGGER PILL & APPLY BUTTON (Aligned at bottom)
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 36.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // FLOATING TRIGGER PILL
                FloatingTriggerPill(
                    selectedTrigger = currentTrigger,
                    onTriggerSelected = { newTrigger ->
                        currentTrigger = newTrigger
                        prefs.trigger = newTrigger
                        // Test the trigger animation on selection
                        playerInstance?.seekTo(0)
                        playerInstance?.play()
                    }
                )

                // Big Floating "Apply as Live Wallpaper" Pill Button
                Button(
                    onClick = {
                        prefs.selectWallpaper(wallpaper)
                        prefs.trigger = currentTrigger
                        prefs.isMuted = isMuted

                        // Sync static frame for Samsung One UI Color Palette daemon
                        scope.launch(Dispatchers.IO) {
                            WallpaperColorHelper.syncStaticBitmapForSamsungOneUI(context, wallpaper)
                        }

                        try {
                            val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
                                putExtra(
                                    WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                                    ComponentName(context, LiveWallpaperService::class.java)
                                )
                            }
                            context.startActivity(intent)
                            Toast.makeText(context, "Applying \"${wallpaper.title}\"...", Toast.LENGTH_SHORT).show()
                        } catch (_: Exception) {
                            try {
                                val fallbackIntent = Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER)
                                context.startActivity(fallbackIntent)
                            } catch (_: Exception) {
                                Toast.makeText(context, "Selected \"${wallpaper.title}\"", Toast.LENGTH_SHORT).show()
                            }
                        }

                        onApplied()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = ShapePill,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Wallpaper,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Apply as Live Wallpaper",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

/**
 * Sleek floating segmented pill for selecting the playback trigger over the full-screen video.
 */
@Composable
fun FloatingTriggerPill(
    selectedTrigger: PlaybackTrigger,
    onTriggerSelected: (PlaybackTrigger) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = ShapePill,
        color = Color(0xFF1E222B).copy(alpha = 0.85f),
        shadowElevation = 10.dp,
        modifier = modifier
            .border(1.dp, Color.White.copy(alpha = 0.15f), ShapePill)
    ) {
        Row(
            modifier = Modifier.padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FloatingTriggerSegment(
                title = "Screen On",
                icon = Icons.Rounded.WbSunny,
                isSelected = selectedTrigger == PlaybackTrigger.SCREEN_ON,
                onClick = { onTriggerSelected(PlaybackTrigger.SCREEN_ON) }
            )

            FloatingTriggerSegment(
                title = "On Unlock",
                icon = Icons.Rounded.LockOpen,
                isSelected = selectedTrigger == PlaybackTrigger.SCREEN_UNLOCK,
                onClick = { onTriggerSelected(PlaybackTrigger.SCREEN_UNLOCK) }
            )
        }
    }
}

@Composable
private fun FloatingTriggerSegment(
    title: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val bgColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
        animationSpec = spring(),
        label = "pillSegmentBg"
    )
    val contentColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimary else Color.White.copy(alpha = 0.80f),
        animationSpec = spring(),
        label = "pillSegmentContent"
    )

    Box(
        modifier = Modifier
            .clip(ShapePill)
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                fontSize = 13.sp,
                color = contentColor
            )
        }
    }
}
