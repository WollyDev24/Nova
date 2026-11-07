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

class LiveWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine {
        return VideoWallpaperEngine()
    }

    private inner class VideoWallpaperEngine : Engine(), SharedPreferences.OnSharedPreferenceChangeListener {

        private val tag = "LiveWallEngine"
        private lateinit var prefs: WallpaperPreferences
        private var player: ExoPlayer? = null
        private var keyguardManager: KeyguardManager? = null
        private var isScreenVisible = false
        private var hasPlayedThisUnlock = false
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
                    volume = if (prefs.isMuted) 0f else 1f
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
            triggerPlaybackCheck(isSurfaceCreated = true)
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
                if (prefs.replayOnHomeReturn && hasPlayedThisUnlock) {
                    replayVideo()
                } else {
                    triggerPlaybackCheck(isSurfaceCreated = false)
                }
            } else {
                player?.pause()
            }
        }

        private fun triggerPlaybackCheck(isSurfaceCreated: Boolean) {
            val isLocked = keyguardManager?.isKeyguardLocked ?: false
            when (prefs.trigger) {
                PlaybackTrigger.SCREEN_ON -> {
                    if (isScreenVisible || isPreview) {
                        replayVideo()
                    }
                }
                PlaybackTrigger.SCREEN_UNLOCK -> {
                    if (isPreview) {
                        replayVideo()
                    } else if (isLocked) {
                        // Keep paused at start frame on lock screen
                        hasPlayedThisUnlock = false
                        player?.seekTo(0)
                        player?.pause()
                    } else {
                        // Screen is unlocked and visible
                        if (!hasPlayedThisUnlock || isSurfaceCreated) {
                            replayVideo()
                            hasPlayedThisUnlock = true
                        }
                    }
                }
            }
        }

        private fun handleScreenOn() {
            val isLocked = keyguardManager?.isKeyguardLocked ?: false
            when (prefs.trigger) {
                PlaybackTrigger.SCREEN_ON -> {
                    replayVideo()
                }
                PlaybackTrigger.SCREEN_UNLOCK -> {
                    if (isLocked) {
                        hasPlayedThisUnlock = false
                        player?.seekTo(0)
                        player?.pause()
                    } else {
                        replayVideo()
                        hasPlayedThisUnlock = true
                    }
                }
            }
        }

        private fun handleUserPresent() {
            // Fired whenever the device is unlocked!
            if (prefs.trigger == PlaybackTrigger.SCREEN_UNLOCK) {
                hasPlayedThisUnlock = true
                replayVideo()
            }
        }

        private fun handleScreenOff() {
            player?.pause()
            if (prefs.trigger == PlaybackTrigger.SCREEN_UNLOCK) {
                hasPlayedThisUnlock = false
                player?.seekTo(0)
            }
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
                    loadCurrentWallpaper()
                    replayVideo()
                }
                WallpaperPreferences.KEY_MUTED -> {
                    player?.volume = if (prefs.isMuted) 0f else 1f
                }
                WallpaperPreferences.KEY_SCALING -> {
                    player?.videoScalingMode = if (prefs.scalingMode == ScalingMode.CROP) {
                        C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING
                    } else {
                        C.VIDEO_SCALING_MODE_SCALE_TO_FIT
                    }
                }
                WallpaperPreferences.KEY_TRIGGER -> {
                    triggerPlaybackCheck(isSurfaceCreated = false)
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
