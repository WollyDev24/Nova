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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.livewall.app.data.PlaybackTrigger
import com.livewall.app.data.ScalingMode
import com.livewall.app.data.WallpaperPreferences
import com.livewall.app.model.WallpaperItem
import com.livewall.app.service.WallpaperColorHelper
import com.livewall.app.ui.components.VideoPlayerView
import com.livewall.app.ui.theme.ShapePill
import com.livewall.app.util.WallpaperAnimationHelper
import com.livewall.app.util.WallpaperDownloader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PreviewSheet(
    initialWallpaper: WallpaperItem,
    wallpapers: List<WallpaperItem> = listOf(initialWallpaper),
    prefs: WallpaperPreferences,
    onDismiss: () -> Unit,
    onApplied: (WallpaperItem) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var playerInstance by remember { mutableStateOf<ExoPlayer?>(null) }
    var currentTrigger by remember { mutableStateOf(prefs.trigger) }
    var showPaletteFlyout by remember { mutableStateOf(false) }
    var isDownloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableFloatStateOf(0f) }
    var downloadVersion by remember { mutableIntStateOf(0) }

    val initialIndex = remember(initialWallpaper.id, wallpapers) {
        val idx = wallpapers.indexOfFirst { it.id == initialWallpaper.id }
        if (idx >= 0) idx else 0
    }
    val pagerState = rememberPagerState(initialPage = initialIndex) { wallpapers.size }

    LaunchedEffect(initialWallpaper.id, initialIndex) {
        if (initialIndex in 0 until wallpapers.size && pagerState.currentPage != initialIndex) {
            pagerState.scrollToPage(initialIndex)
        }
    }

    val currentWallpaper = wallpapers.getOrNull(pagerState.currentPage) ?: initialWallpaper

    val isCurrentDownloaded = remember(currentWallpaper.id, downloadVersion) {
        WallpaperDownloader.isDownloaded(context, currentWallpaper)
    }

    LaunchedEffect(pagerState.currentPage) {
        isDownloading = false
    }

    val currentRgb = remember(currentWallpaper.id) {
        WallpaperColorHelper.getWallpaperColorsRgb(context, currentWallpaper)
    }

    val currentCurve = remember(currentWallpaper.id) {
        WallpaperAnimationHelper.getCurveForWallpaper(context, currentWallpaper.id)
    }
    var previewCurveMode by remember(currentWallpaper.id) { mutableIntStateOf(0) } // 0 = Lock Breathing, 1 = Unlock Transition

    LaunchedEffect(currentWallpaper.id, playerInstance, previewCurveMode) {
        val p = playerInstance ?: return@LaunchedEffect
        val curve = currentCurve
        if (curve != null && curve.isSplit) {
            if (previewCurveMode == 0) {
                p.pause()
                val restPos = curve.startPauseValueMs
                val amplitude = 280L
                val cycleDuration = 3200.0
                val baseTime = android.os.SystemClock.uptimeMillis()
                while (true) {
                    val elapsed = android.os.SystemClock.uptimeMillis() - baseTime
                    val phase = (elapsed % cycleDuration) / cycleDuration
                    val offset = (kotlin.math.sin(phase * 2.0 * Math.PI) * amplitude).toLong()
                    val targetPos = (restPos + offset).coerceIn(0L, ((curve.splitIndex.toFloat() / 260f) * 4333L).toLong())
                    p.seekTo(targetPos)
                    kotlinx.coroutines.delay(25)
                }
            } else {
                val dur = if (p.duration > 0) p.duration else 4333L
                val splitMs = ((curve.splitIndex.toFloat() / 260f) * dur).toLong()
                p.seekTo(splitMs)
                p.play()
            }
        }
    }

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
            // 1. SWIPEABLE FULL-SCREEN HORIZONTAL PAGER
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                key = { page -> wallpapers.getOrNull(page)?.id ?: page.toString() }
            ) { page ->
                val item = wallpapers[page]
                val isCurrent = page == pagerState.currentPage

                Box(modifier = Modifier.fillMaxSize()) {
                    // Start-frame static backdrop displayed immediately while video initializes
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(WallpaperColorHelper.resolveCoilModel(item.effectivePreviewThumbnailUri))
                            .crossfade(false)
                            .build(),
                        contentDescription = item.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )

                    val isDownloaded = remember(item.id, downloadVersion) {
                        WallpaperDownloader.isDownloaded(context, item)
                    }

                    if (isCurrent && isDownloaded) {
                        val playableUri = remember(item.id, downloadVersion) {
                            WallpaperDownloader.getPlayableUri(context, item)
                        }
                        val isCurve = currentCurve != null && currentCurve.isSplit
                        VideoPlayerView(
                            videoUri = playableUri,
                            scalingMode = ScalingMode.CROP,
                            isMuted = true,
                            autoPlay = !isCurve,
                            repeatMode = Player.REPEAT_MODE_OFF,
                            onPlayerReady = { playerInstance = it },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }

            // 2. Navigation Chevrons (if multiple wallpapers available)
            if (wallpapers.size > 1) {
                if (pagerState.currentPage > 0) {
                    Surface(
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.50f),
                        contentColor = Color.White,
                        shadowElevation = 4.dp,
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = 12.dp)
                            .size(42.dp)
                            .clip(CircleShape)
                            .clickable {
                                scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                            }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "Previous Wallpaper",
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }

                if (pagerState.currentPage < wallpapers.size - 1) {
                    Surface(
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.50f),
                        contentColor = Color.White,
                        shadowElevation = 4.dp,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 12.dp)
                            .size(42.dp)
                            .clip(CircleShape)
                            .clickable {
                                scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                            }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                                contentDescription = "Next Wallpaper",
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }

            // 3. Top Scrim Gradient & Top Bar
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
                            text = currentWallpaper.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        val counterText = if (wallpapers.size > 1) " • ${pagerState.currentPage + 1}/${wallpapers.size}" else ""
                        val curveBadge = if (currentCurve != null && currentCurve.isSplit) " • Dynamic Curve" else ""
                        Text(
                            text = "${currentWallpaper.collection}$counterText$curveBadge",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                }

                // Action Pills: Mode Toggle (if curve), Palette Flyout & Replay
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (currentCurve != null && currentCurve.isSplit && isCurrentDownloaded) {
                        Surface(
                            shape = ShapePill,
                            color = if (previewCurveMode == 0) MaterialTheme.colorScheme.primary.copy(alpha = 0.85f) else Color.Black.copy(alpha = 0.60f),
                            contentColor = Color.White,
                            shadowElevation = 6.dp,
                            modifier = Modifier
                                .height(44.dp)
                                .clip(ShapePill)
                                .clickable {
                                    previewCurveMode = if (previewCurveMode == 0) 1 else 0
                                }
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 14.dp)) {
                                Text(
                                    text = if (previewCurveMode == 0) "Lock: Breathing" else "Unlock Transition",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Palette Flyout Trigger Button (Rewired to open colors flyout)
                    Surface(
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.60f),
                        contentColor = Color.White,
                        shadowElevation = 6.dp,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .clickable {
                                showPaletteFlyout = true
                            }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(imageVector = Icons.Rounded.Palette, contentDescription = "View Palette", modifier = Modifier.size(20.dp))
                        }
                    }

                    // Replay Button (only visible when video is downloaded)
                    if (isCurrentDownloaded) {
                        Surface(
                            shape = CircleShape,
                            color = Color.Black.copy(alpha = 0.60f),
                            contentColor = Color.White,
                            shadowElevation = 6.dp,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .clickable {
                                    val curve = currentCurve
                                    if (curve != null && curve.isSplit) {
                                        if (previewCurveMode == 1) {
                                            val dur = playerInstance?.duration ?: 4333L
                                            val splitMs = if (dur > 0) ((curve.splitIndex.toFloat() / 260f) * dur).toLong() else 3000L
                                            playerInstance?.seekTo(splitMs)
                                            playerInstance?.play()
                                        } else {
                                            previewCurveMode = 1
                                        }
                                    } else {
                                        playerInstance?.seekTo(0)
                                        playerInstance?.play()
                                    }
                                }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(imageVector = Icons.Rounded.Replay, contentDescription = "Replay", modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }

            // 4. Bottom Controls Overlay
            Box(
                modifier = Modifier
                    .fillMaxWidth()
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
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 20.dp, top = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Floating Trigger Selector Pill
                    FloatingTriggerPill(
                        selectedTrigger = currentTrigger,
                        onTriggerSelected = { newTrigger ->
                            currentTrigger = newTrigger
                            prefs.trigger = newTrigger
                        }
                    )

                    // Action Button: Download Wallpaper OR Apply as Live Wallpaper
                    if (!isCurrentDownloaded) {
                        Button(
                            onClick = {
                                if (!isDownloading) {
                                    isDownloading = true
                                    downloadProgress = 0f
                                    scope.launch {
                                        val result = WallpaperDownloader.downloadWallpaper(context, currentWallpaper) { p ->
                                            downloadProgress = p
                                        }
                                        isDownloading = false
                                        if (result.isSuccess) {
                                            downloadVersion++
                                            Toast.makeText(context, "Ready to apply \"${currentWallpaper.title}\"!", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "Download failed: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                }
                            },
                            enabled = !isDownloading,
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
                            if (isDownloading) {
                                CircularProgressIndicator(
                                    progress = { downloadProgress },
                                    modifier = Modifier.size(20.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.5.dp
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "Downloading ${(downloadProgress * 100).toInt()}%...",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Rounded.Download,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Download Wallpaper",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    } else {
                        // Apply Button
                        Button(
                            onClick = {
                                val playableUri = WallpaperDownloader.getPlayableUri(context, currentWallpaper)
                                val readyItem = if (currentWallpaper.videoUri != playableUri) {
                                    currentWallpaper.copy(videoUri = playableUri)
                                } else {
                                    currentWallpaper
                                }
                                prefs.selectWallpaper(readyItem)
                                prefs.trigger = currentTrigger

                                // Sync static frame for Samsung One UI Color Palette daemon
                                scope.launch(Dispatchers.IO) {
                                    WallpaperColorHelper.syncStaticBitmapForSamsungOneUI(context, readyItem)
                                }

                                try {
                                    val serviceClass = WallpaperColorHelper.getServiceClassForWallpaper(currentWallpaper.id)
                                    val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
                                        putExtra(
                                            WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                                            ComponentName(context, serviceClass)
                                        )
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    context.startActivity(intent)
                                    Toast.makeText(context, "Applying \"${currentWallpaper.title}\"...", Toast.LENGTH_SHORT).show()
                                } catch (_: Exception) {
                                    try {
                                        val fallbackIntent = Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER).apply {
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                        context.startActivity(fallbackIntent)
                                    } catch (_: Exception) {
                                        Toast.makeText(context, "Selected \"${currentWallpaper.title}\"", Toast.LENGTH_SHORT).show()
                                    }
                                }

                                onApplied(readyItem)
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

            // 5. PALETTE FLYOUT DIALOG (Shows the 3 main wallpaper colors)
            if (showPaletteFlyout) {
                PaletteFlyoutDialog(
                    wallpaper = currentWallpaper,
                    primaryColor = currentRgb.first,
                    secondaryColor = currentRgb.second,
                    tertiaryColor = currentRgb.third,
                    onDismiss = { showPaletteFlyout = false }
                )
            }
        }
    }
}

/**
 * Modern floating flyout dialog showing the 3 main extracted wallpaper colors with swatches & hex codes.
 */
@Composable
fun PaletteFlyoutDialog(
    wallpaper: WallpaperItem,
    primaryColor: Int,
    secondaryColor: Int,
    tertiaryColor: Int,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = Color(0xFF1E222B),
            contentColor = Color.White,
            shadowElevation = 16.dp,
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .fillMaxWidth()
                .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(28.dp))
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.Palette,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Column {
                            Text(
                                text = "Wallpaper Palette",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = wallpaper.title,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.70f)
                            )
                        }
                    }

                    FilledTonalIconButton(
                        onClick = onDismiss,
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = Color.White.copy(alpha = 0.10f),
                            contentColor = Color.White
                        ),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(imageVector = Icons.Rounded.Close, contentDescription = "Close", modifier = Modifier.size(18.dp))
                    }
                }

                // 3 Main Extracted Color Rows
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    PaletteColorRow(
                        title = "Primary Tone",
                        role = "System buttons, toggles & accents",
                        colorInt = primaryColor
                    )
                    PaletteColorRow(
                        title = "Secondary Tone",
                        role = "Tonal containers, cards & highlights",
                        colorInt = secondaryColor
                    )
                    PaletteColorRow(
                        title = "Tertiary Tone",
                        role = "Ambient contrast & badge surfaces",
                        colorInt = tertiaryColor
                    )
                }
            }
        }
    }
}

@Composable
private fun PaletteColorRow(
    title: String,
    role: String,
    colorInt: Int
) {
    val composeColor = Color(colorInt)
    val hexString = "#%06X".format(0xFFFFFF and colorInt)

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.White.copy(alpha = 0.07f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Circular color swatch
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(composeColor)
                    .border(2.dp, Color.White.copy(alpha = 0.35f), CircleShape)
            )

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = hexString,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace,
                        color = Color.White.copy(alpha = 0.85f)
                    )
                }
                Text(
                    text = role,
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.60f)
                )
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
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = contentColor
            )
        }
    }
}
