package com.livewall.app.ui.components

import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.livewall.app.data.ScalingMode

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
                val mediaItem = MediaItem.fromUri(Uri.parse(videoUri))
                setMediaItem(mediaItem)
                prepare()
                playWhenReady = autoPlay
            } catch (_: Exception) {}
        }
    }

    LaunchedEffect(exoPlayer) {
        onPlayerReady(exoPlayer)
    }

    LaunchedEffect(autoPlay) {
        exoPlayer.playWhenReady = autoPlay
        if (autoPlay) {
            exoPlayer.seekTo(0)
            exoPlayer.play()
        } else {
            exoPlayer.pause()
        }
    }

    LaunchedEffect(isMuted) {
        exoPlayer.volume = if (isMuted) 0f else 1f
    }

    LaunchedEffect(scalingMode) {
        exoPlayer.videoScalingMode = if (scalingMode == ScalingMode.CROP) {
            C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING
        } else {
            C.VIDEO_SCALING_MODE_SCALE_TO_FIT
        }
    }

    DisposableEffect(exoPlayer) {
        onDispose {
            exoPlayer.release()
        }
    }

    Box(modifier = modifier) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
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
    }
}
