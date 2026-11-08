package com.livewall.app.ui.components

import android.net.Uri
import android.util.Log
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.livewall.app.data.ScalingMode
import com.livewall.app.ui.theme.ShapePill

private const val TAG = "VideoPlayerView"

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerView(
    videoUri: String,
    modifier: Modifier = Modifier,
    scalingMode: ScalingMode = ScalingMode.CROP,
    isMuted: Boolean = true,
    autoPlay: Boolean = true,
    repeatMode: Int = Player.REPEAT_MODE_OFF,
    onPlayerReady: (ExoPlayer) -> Unit = {}
) {
    val context = LocalContext.current

    var isFirstFrameRendered by remember(videoUri) { mutableStateOf(false) }
    var isBuffering by remember(videoUri) { mutableStateOf(true) }
    var hasPlaybackError by remember(videoUri) { mutableStateOf(false) }

    val exoPlayer = remember(videoUri) {
        ExoPlayer.Builder(context).build().apply {
            this.repeatMode = repeatMode
            this.volume = if (isMuted) 0f else 1f
            this.videoScalingMode = if (scalingMode == ScalingMode.CROP) {
                C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING
            } else {
                C.VIDEO_SCALING_MODE_SCALE_TO_FIT
            }
            try {
                val uri = Uri.parse(videoUri)
                val mediaItem = if (videoUri.startsWith("http://") || videoUri.startsWith("https://")) {
                    MediaItem.Builder()
                        .setUri(uri)
                        .setMimeType(MimeTypes.VIDEO_MP4)
                        .build()
                } else {
                    MediaItem.fromUri(uri)
                }
                setMediaItem(mediaItem)
                prepare()
                playWhenReady = autoPlay
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing player for uri: $videoUri", e)
                hasPlaybackError = true
            }
        }
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onRenderedFirstFrame() {
                isFirstFrameRendered = true
                isBuffering = false
                hasPlaybackError = false
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_BUFFERING -> {
                        isBuffering = true
                    }
                    Player.STATE_READY -> {
                        isBuffering = false
                    }
                    Player.STATE_ENDED -> {
                        if (repeatMode != Player.REPEAT_MODE_OFF) {
                            exoPlayer.seekTo(0)
                            exoPlayer.play()
                        }
                    }
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                Log.e(TAG, "Playback error for $videoUri: ${error.errorCodeName}", error)
                isBuffering = false
                hasPlaybackError = true
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    LaunchedEffect(exoPlayer) {
        onPlayerReady(exoPlayer)
    }

    var isFirstLaunch by remember(videoUri) { mutableStateOf(true) }
    LaunchedEffect(autoPlay) {
        exoPlayer.playWhenReady = autoPlay
        if (autoPlay) {
            if (!isFirstLaunch && exoPlayer.playbackState == Player.STATE_ENDED) {
                exoPlayer.seekTo(0)
            }
            exoPlayer.play()
        } else {
            exoPlayer.pause()
        }
        isFirstLaunch = false
    }

    LaunchedEffect(scalingMode) {
        exoPlayer.videoScalingMode = if (scalingMode == ScalingMode.CROP) {
            C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING
        } else {
            C.VIDEO_SCALING_MODE_SCALE_TO_FIT
        }
    }

    // Instantly reveal PlayerView (alpha = 1) the exact millisecond the first frame is decoded.
    // Since the backdrop underneath matches frame 0, there is zero crossfade ghosting or reverse flicker!
    val playerAlpha by animateFloatAsState(
        targetValue = if (isFirstFrameRendered) 1f else 0f,
        animationSpec = snap(),
        label = "playerAlpha"
    )

    Box(modifier = modifier) {
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .alpha(playerAlpha),
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                    // Completely transparent shutter so nothing behind is occluded by grey
                    setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                    setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                    resizeMode = if (scalingMode == ScalingMode.CROP) {
                        AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    } else {
                        AspectRatioFrameLayout.RESIZE_MODE_FIT
                    }
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            update = { playerView ->
                playerView.resizeMode = if (scalingMode == ScalingMode.CROP) {
                    AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                } else {
                    AspectRatioFrameLayout.RESIZE_MODE_FIT
                }
            }
        )

        // Subtle buffering indicator shown over the thumbnail while streaming remote video
        if (isBuffering && !isFirstFrameRendered && !hasPlaybackError) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.50f),
                    modifier = Modifier.size(52.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(26.dp),
                            color = Color.White,
                            strokeWidth = 2.5.dp
                        )
                    }
                }
            }
        }

        // Retry chip if streaming failed (thumbnail remains intact underneath)
        if (hasPlaybackError && !isFirstFrameRendered) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 120.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                Surface(
                    shape = ShapePill,
                    color = Color.Black.copy(alpha = 0.75f),
                    contentColor = Color.White,
                    shadowElevation = 4.dp,
                    modifier = Modifier.clickable {
                        hasPlaybackError = false
                        isBuffering = true
                        try {
                            exoPlayer.seekTo(0)
                            exoPlayer.prepare()
                            exoPlayer.play()
                        } catch (_: Exception) {}
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = "Retry playback",
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Stream slow • Tap to retry",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }
        }
    }
}
