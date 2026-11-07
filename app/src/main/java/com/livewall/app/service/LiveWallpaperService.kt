package com.livewall.app.service

import android.app.KeyguardManager
import android.app.WallpaperColors
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.service.wallpaper.WallpaperService
import android.util.Log
import android.view.SurfaceHolder
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.livewall.app.data.PlaybackTrigger
import com.livewall.app.data.ScalingMode
import com.livewall.app.data.WallpaperPreferences

open class LiveWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine {
        return VideoWallpaperEngine()
    }

    private inner class VideoWallpaperEngine : Engine(), SharedPreferences.OnSharedPreferenceChangeListener {

        private val tag = "LiveWallEngine"
        private lateinit var prefs: WallpaperPreferences
        private var player: ExoPlayer? = null
        private var keyguardManager: KeyguardManager? = null
        private var isScreenVisible = false
        private var hasPlayedThisSession = false
        private var cachedWallpaperColors: WallpaperColors? = null

        private val screenReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_SCREEN_ON -> handleScreenOn()
                    Intent.ACTION_USER_PRESENT -> handleUserPresent()
                    Intent.ACTION_SCREEN_OFF -> handleScreenOff()
                }
            }
        }

        @OptIn(UnstableApi::class)
        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            prefs = WallpaperPreferences(applicationContext)
            keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager

            initPlayer()
            prefs.registerListener(this)

            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_USER_PRESENT)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(screenReceiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                registerReceiver(screenReceiver, filter)
            }
        }

        @OptIn(UnstableApi::class)
        private fun initPlayer() {
            try {
                player?.release()
                player = ExoPlayer.Builder(applicationContext).build().apply {
                    repeatMode = Player.REPEAT_MODE_OFF
                    volume = 0f // Wallpapers are completely silent by design
                    videoScalingMode = if (prefs.scalingMode == ScalingMode.CROP) {
                        C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING
                    } else {
                        C.VIDEO_SCALING_MODE_SCALE_TO_FIT
                    }

                    addListener(object : Player.Listener {
                        override fun onPlaybackStateChanged(playbackState: Int) {
                            if (playbackState == Player.STATE_ENDED) {
                                Log.d(tag, "Video completed, paused at final frame.")
                            }
                        }
                    })
                }

                loadCurrentWallpaper()
            } catch (e: Exception) {
                Log.e(tag, "Error initializing ExoPlayer", e)
            }
        }

        private fun loadCurrentWallpaper() {
            val p = player ?: return
            val uriStr = prefs.selectedWallpaperUri
            try {
                val mediaItem = MediaItem.fromUri(Uri.parse(uriStr))
                p.setMediaItem(mediaItem)
                p.prepare()
                updateWallpaperColors()
            } catch (e: Exception) {
                Log.e(tag, "Error loading media item: $uriStr", e)
            }
        }

        override fun onComputeColors(): WallpaperColors? {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                if (cachedWallpaperColors == null) {
                    cachedWallpaperColors = WallpaperColorHelper.computeColors(applicationContext, prefs.selectedWallpaperUri)
                }
                return cachedWallpaperColors ?: super.onComputeColors()
            }
            return null
        }

        private fun updateWallpaperColors() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                val newColors = WallpaperColorHelper.computeColors(applicationContext, prefs.selectedWallpaperUri)
                if (newColors != null) {
                    cachedWallpaperColors = newColors
                    notifyColorsChanged()
                    Log.d(tag, "Notified Android OS & Samsung One UI of updated WallpaperColors: $newColors")
                }
            }
        }

        override fun onSurfaceCreated(holder: SurfaceHolder) {
            super.onSurfaceCreated(holder)
            player?.setVideoSurface(holder.surface)
            updateWallpaperColors()
            if (isPreview) {
                replayVideo()
            } else if (!hasPlayedThisSession && isScreenVisible) {
                val isLocked = keyguardManager?.isKeyguardLocked ?: false
                if (!isLocked || prefs.trigger == PlaybackTrigger.SCREEN_ON) {
                    hasPlayedThisSession = true
                    replayVideo()
                }
            }
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            super.onSurfaceDestroyed(holder)
            player?.clearVideoSurface()
        }

        override fun onVisibilityChanged(visible: Boolean) {
            super.onVisibilityChanged(visible)
            isScreenVisible = visible
            if (visible) {
                if (isPreview) {
                    replayVideo()
                    return
                }

                val isLocked = keyguardManager?.isKeyguardLocked ?: false
                if (isLocked && prefs.trigger == PlaybackTrigger.SCREEN_UNLOCK) {
                    // Holding on lock screen
                    player?.seekTo(0)
                    player?.pause()
                    return
                }

                if (hasPlayedThisSession) {
                    if (prefs.replayOnHomeReturn) {
                        replayVideo()
                    } else {
                        // Stays static at final frame! DO NOT seek to 0 or replay.
                        // Only resume if it was paused midway before finishing.
                        if (player?.playbackState != Player.STATE_ENDED) {
                            player?.play()
                        }
                    }
                } else {
                    hasPlayedThisSession = true
                    replayVideo()
                }
            } else {
                player?.pause()
            }
        }

        private fun handleScreenOn() {
            val isLocked = keyguardManager?.isKeyguardLocked ?: false
            when (prefs.trigger) {
                PlaybackTrigger.SCREEN_ON -> {
                    if (!hasPlayedThisSession) {
                        hasPlayedThisSession = true
                        replayVideo()
                    }
                }
                PlaybackTrigger.SCREEN_UNLOCK -> {
                    if (isLocked) {
                        hasPlayedThisSession = false
                        player?.seekTo(0)
                        player?.pause()
                    }
                }
            }
        }

        private fun handleUserPresent() {
            // Fired whenever the device is unlocked!
            if (prefs.trigger == PlaybackTrigger.SCREEN_UNLOCK) {
                if (!hasPlayedThisSession) {
                    hasPlayedThisSession = true
                    replayVideo()
                }
            }
        }

        private fun handleScreenOff() {
            player?.pause()
            hasPlayedThisSession = false
            player?.seekTo(0)
        }

        private fun replayVideo() {
            player?.let { p ->
                p.seekTo(0)
                p.play()
            }
        }

        @OptIn(UnstableApi::class)
        override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
            when (key) {
                WallpaperPreferences.KEY_SELECTED_URI -> {
                    cachedWallpaperColors = null
                    hasPlayedThisSession = false
                    loadCurrentWallpaper()
                    replayVideo()
                }
                WallpaperPreferences.KEY_SCALING -> {
                    player?.videoScalingMode = if (prefs.scalingMode == ScalingMode.CROP) {
                        C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING
                    } else {
                        C.VIDEO_SCALING_MODE_SCALE_TO_FIT
                    }
                }
                WallpaperPreferences.KEY_TRIGGER -> {
                    hasPlayedThisSession = false
                    val isLocked = keyguardManager?.isKeyguardLocked ?: false
                    if (prefs.trigger == PlaybackTrigger.SCREEN_UNLOCK && isLocked) {
                        player?.seekTo(0)
                        player?.pause()
                    }
                }
            }
        }

        override fun onDestroy() {
            super.onDestroy()
            try {
                unregisterReceiver(screenReceiver)
            } catch (e: Exception) {
                Log.w(tag, "Receiver already unregistered", e)
            }
            prefs.unregisterListener(this)
            player?.release()
            player = null
        }
    }
}
