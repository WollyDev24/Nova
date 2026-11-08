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
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.service.wallpaper.WallpaperService
import android.util.Log
import android.view.Surface
import android.view.SurfaceHolder
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import com.livewall.app.data.PlaybackTrigger
import com.livewall.app.data.ScalingMode
import com.livewall.app.data.WallpaperPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

open class LiveWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine {
        return VideoWallpaperEngine()
    }

    private inner class VideoWallpaperEngine : Engine(), SharedPreferences.OnSharedPreferenceChangeListener {

        private val tag = "LiveWallEngine"
        private lateinit var prefs: WallpaperPreferences
        private var forwardPlayer: ExoPlayer? = null
        private var reversePlayer: ExoPlayer? = null
        private var keyguardManager: KeyguardManager? = null
        private var isScreenVisible = false
        private var hasPlayedThisSession = false
        private var cachedWallpaperColors: WallpaperColors? = null
        private var reversePlaybackJob: Job? = null
        private var isReversing = false
        private var wakeLock: PowerManager.WakeLock? = null
        private var currentSurfaceHolder: SurfaceHolder? = null
        private var hasReverseVideo = false
        private var videoDurationMs = 2000L

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
            currentSurfaceHolder = surfaceHolder
            prefs = WallpaperPreferences(applicationContext)
            keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
            val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
            wakeLock = pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "LiveWall:AodReverseWakeLock")

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
                forwardPlayer?.release()
                reversePlayer?.release()

                val scaling = if (prefs.scalingMode == ScalingMode.CROP) {
                    C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING
                } else {
                    C.VIDEO_SCALING_MODE_SCALE_TO_FIT
                }

                forwardPlayer = ExoPlayer.Builder(applicationContext).build().apply {
                    repeatMode = Player.REPEAT_MODE_OFF
                    volume = 0f
                    videoScalingMode = scaling

                    addListener(object : Player.Listener {
                        override fun onPlaybackStateChanged(playbackState: Int) {
                            if (playbackState == Player.STATE_READY) {
                                val d = duration
                                if (d > 0L) {
                                    videoDurationMs = d
                                }
                            } else if (playbackState == Player.STATE_ENDED) {
                                Log.d(tag, "Forward video completed, paused at final frame.")
                            }
                        }
                    })
                }

                reversePlayer = ExoPlayer.Builder(applicationContext).build().apply {
                    repeatMode = Player.REPEAT_MODE_OFF
                    volume = 0f
                    videoScalingMode = scaling

                    addListener(object : Player.Listener {
                        override fun onPlaybackStateChanged(playbackState: Int) {
                            if (playbackState == Player.STATE_ENDED) {
                                if (isReversing) {
                                    Log.d(tag, "Reverse player reached end of animation")
                                    finishReversePlayback()
                                }
                            }
                        }
                    })
                }

                loadCurrentWallpaper()
            } catch (e: Exception) {
                Log.e(tag, "Error initializing ExoPlayers", e)
            }
        }

        private fun loadCurrentWallpaper() {
            val fp = forwardPlayer ?: return
            val uriStr = prefs.selectedWallpaperUri
            try {
                val forwardItem = MediaItem.fromUri(Uri.parse(uriStr))
                fp.setMediaItem(forwardItem)
                fp.prepare()

                val reverseUri = getReverseUri(uriStr)
                if (reverseUri != null && reversePlayer != null) {
                    val reverseItem = MediaItem.fromUri(Uri.parse(reverseUri))
                    reversePlayer?.setMediaItem(reverseItem)
                    reversePlayer?.prepare()
                    hasReverseVideo = true
                    Log.d(tag, "Pre-buffered dual players: forward=$uriStr, reverse=$reverseUri")
                } else {
                    hasReverseVideo = false
                    Log.d(tag, "Single forward player loaded: $uriStr")
                }
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
            currentSurfaceHolder = holder
            forwardPlayer?.setVideoSurface(holder.surface)
            setSurfaceFrameRate(120f)
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
            currentSurfaceHolder = holder
            setSurfaceFrameRate(120f)
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            super.onSurfaceDestroyed(holder)
            currentSurfaceHolder = null
            forwardPlayer?.clearVideoSurface()
            reversePlayer?.clearVideoSurface()
        }

        override fun onVisibilityChanged(visible: Boolean) {
            super.onVisibilityChanged(visible)
            Log.d(tag, "onVisibilityChanged: visible=$visible, isReversing=$isReversing")
            isScreenVisible = visible
            if (visible) {
                stopReversePlayback()
                if (isPreview) {
                    replayVideo()
                    return
                }

                val isLocked = keyguardManager?.isKeyguardLocked ?: false
                if (isLocked && prefs.trigger == PlaybackTrigger.SCREEN_UNLOCK) {
                    // Holding on lock screen
                    forwardPlayer?.seekTo(0)
                    forwardPlayer?.pause()
                    return
                }

                if (hasPlayedThisSession) {
                    if (prefs.replayOnHomeReturn) {
                        replayVideo()
                    } else {
                        // Stays static at final frame! DO NOT seek to 0 or replay.
                        // Only resume if it was paused midway before finishing.
                        if (forwardPlayer?.playbackState != Player.STATE_ENDED) {
                            forwardPlayer?.play()
                        }
                    }
                } else {
                    hasPlayedThisSession = true
                    replayVideo()
                }
            } else {
                if (!isReversing) {
                    forwardPlayer?.pause()
                }
            }
        }

        private fun isAodEnabled(): Boolean {
            return try {
                val cr = applicationContext.contentResolver
                val samsungAod = Settings.System.getInt(cr, "aod_mode", -1)
                if (samsungAod != -1) {
                    return samsungAod == 1
                }
                val dozeAlwaysOn = Settings.Secure.getInt(cr, "doze_always_on", -1)
                if (dozeAlwaysOn != -1) {
                    return dozeAlwaysOn == 1
                }
                false
            } catch (e: Exception) {
                Log.w(tag, "Failed to check AOD setting", e)
                false
            }
        }

        private fun setSurfaceFrameRate(fps: Float) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                try {
                    val surface = currentSurfaceHolder?.surface
                    if (surface != null && surface.isValid) {
                        val strategy = if (fps > 0f) {
                            Surface.CHANGE_FRAME_RATE_ALWAYS
                        } else {
                            Surface.CHANGE_FRAME_RATE_ONLY_IF_SEAMLESS
                        }
                        surface.setFrameRate(
                            fps,
                            Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE,
                            strategy
                        )
                        Log.d(tag, "Set surface frame rate to ${fps}Hz (strategy=$strategy)")
                    }
                } catch (e: Exception) {
                    Log.w(tag, "Failed to set surface frame rate: $fps", e)
                }
            }
        }

        private fun getReverseUri(forwardUriStr: String): String? {
            if (forwardUriStr.endsWith(".mp4")) {
                val revUriStr = forwardUriStr.substringBeforeLast(".mp4") + "_rev.mp4"
                if (revUriStr.startsWith("asset:///")) {
                    val assetPath = revUriStr.removePrefix("asset:///")
                    return try {
                        applicationContext.assets.open(assetPath).use { }
                        revUriStr
                    } catch (_: Exception) {
                        null
                    }
                }
                if (revUriStr.startsWith("file://")) {
                    val path = Uri.parse(revUriStr).path
                    if (path != null && java.io.File(path).exists()) {
                        return revUriStr
                    }
                }
            }
            return null
        }

        private fun stopReversePlayback() {
            if (isReversing || reversePlaybackJob?.isActive == true) {
                Log.d(tag, "Stopping reverse playback early (screen on or interrupted)")
                reversePlaybackJob?.cancel()
                reversePlaybackJob = null
                isReversing = false

                try {
                    if (wakeLock?.isHeld == true) {
                        wakeLock?.release()
                    }
                } catch (_: Exception) {}

                val surface = currentSurfaceHolder?.surface
                val rp = reversePlayer
                val fp = forwardPlayer

                if (rp != null && fp != null && surface != null && surface.isValid) {
                    val revPos = rp.currentPosition
                    val fwdPos = (videoDurationMs - revPos).coerceIn(0L, videoDurationMs)
                    Log.d(tag, "Transferring surface back to forwardPlayer at fwdPos $fwdPos")
                    rp.pause()
                    rp.clearVideoSurface()
                    fp.setVideoSurface(surface)
                    fp.seekTo(fwdPos)
                }
                setSurfaceFrameRate(120f)
            }
        }

        private fun finishReversePlayback() {
            Log.d(tag, "finishReversePlayback: reverse video reached first frame")
            isReversing = false
            reversePlaybackJob?.cancel()
            reversePlaybackJob = null

            val surface = currentSurfaceHolder?.surface
            val rp = reversePlayer
            val fp = forwardPlayer

            if (rp != null) {
                rp.pause()
                rp.clearVideoSurface()
            }
            if (fp != null && surface != null && surface.isValid) {
                fp.setVideoSurface(surface)
                fp.seekTo(0)
                fp.pause()
            }

            setSurfaceFrameRate(0f)
            try {
                if (wakeLock?.isHeld == true) {
                    wakeLock?.release()
                }
            } catch (_: Exception) {}
        }

        private fun playInReverse() {
            stopReversePlayback()
            val fp = forwardPlayer ?: return
            val surface = currentSurfaceHolder?.surface

            val currentForwardPos = fp.currentPosition
            val duration = if (videoDurationMs > 0) videoDurationMs else 2000L
            Log.d(tag, "playInReverse requested: currentForwardPos=$currentForwardPos, duration=$duration, hasReverseVideo=$hasReverseVideo")

            if (currentForwardPos <= 50L) {
                fp.seekTo(0)
                fp.pause()
                return
            }

            isReversing = true
            try {
                wakeLock?.acquire(3500L)
            } catch (e: Exception) {
                Log.w(tag, "Failed to acquire wakeLock", e)
            }
            setSurfaceFrameRate(120f)

            val rp = reversePlayer
            if (hasReverseVideo && rp != null && surface != null && surface.isValid) {
                // INSTANT 0ms latency hardware-accelerated reverse playback!
                val revStartPos = (duration - currentForwardPos).coerceIn(0L, duration)
                Log.d(tag, "Instant surface transfer to reversePlayer at $revStartPos")
                fp.pause()
                fp.clearVideoSurface()
                rp.setVideoSurface(surface)
                rp.seekTo(revStartPos)
                rp.play()

                reversePlaybackJob = CoroutineScope(Dispatchers.Main.immediate).launch {
                    while (isActive && isReversing) {
                        if (rp.playbackState == Player.STATE_ENDED || rp.currentPosition >= duration - 30L) {
                            Log.d(tag, "Reverse playback completed naturally")
                            break
                        }
                        delay(20)
                    }
                    if (isReversing) {
                        finishReversePlayback()
                    }
                }
                return
            }

            // Fallback: Seek backwards smoothly for any custom video without a reverse track
            fp.pause()
            val durationMs = currentForwardPos
            val startTime = SystemClock.uptimeMillis()

            reversePlaybackJob = CoroutineScope(Dispatchers.Main.immediate).launch {
                try {
                    while (isActive) {
                        val elapsed = SystemClock.uptimeMillis() - startTime
                        val fraction = (elapsed.toFloat() / durationMs).coerceIn(0f, 1f)
                        val targetPos = (currentForwardPos * (1f - fraction)).toLong()

                        fp.seekTo(targetPos)

                        if (fraction >= 1f || targetPos <= 0L) {
                            fp.seekTo(0)
                            fp.pause()
                            break
                        }
                        delay(33)
                    }
                } finally {
                    if (isReversing) {
                        finishReversePlayback()
                    }
                }
            }
        }

        private fun handleScreenOn() {
            Log.d(tag, "handleScreenOn: trigger=${prefs.trigger}")
            stopReversePlayback()
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
                        forwardPlayer?.seekTo(0)
                        forwardPlayer?.pause()
                    }
                }
            }
        }

        private fun handleUserPresent() {
            Log.d(tag, "handleUserPresent: trigger=${prefs.trigger}")
            stopReversePlayback()
            // Fired whenever the device is unlocked!
            if (prefs.trigger == PlaybackTrigger.SCREEN_UNLOCK) {
                if (!hasPlayedThisSession) {
                    hasPlayedThisSession = true
                    replayVideo()
                }
            }
        }

        private fun handleScreenOff() {
            val aodOn = isAodEnabled()
            Log.d(tag, "handleScreenOff: AOD enabled=$aodOn")
            hasPlayedThisSession = false
            if (aodOn) {
                playInReverse()
            } else {
                stopReversePlayback()
                forwardPlayer?.pause()
                forwardPlayer?.seekTo(0)
            }
        }

        private fun replayVideo() {
            val surface = currentSurfaceHolder?.surface
            val fp = forwardPlayer
            val rp = reversePlayer
            if (rp != null) {
                rp.pause()
                rp.clearVideoSurface()
            }
            if (fp != null && surface != null && surface.isValid) {
                fp.setVideoSurface(surface)
                fp.seekTo(0)
                fp.play()
            }
        }

        @OptIn(UnstableApi::class)
        override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
            when (key) {
                WallpaperPreferences.KEY_SELECTED_URI -> {
                    cachedWallpaperColors = null
                    hasPlayedThisSession = false
                    stopReversePlayback()
                    loadCurrentWallpaper()
                    replayVideo()
                }
                WallpaperPreferences.KEY_SCALING -> {
                    val scaling = if (prefs.scalingMode == ScalingMode.CROP) {
                        C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING
                    } else {
                        C.VIDEO_SCALING_MODE_SCALE_TO_FIT
                    }
                    forwardPlayer?.videoScalingMode = scaling
                    reversePlayer?.videoScalingMode = scaling
                }
                WallpaperPreferences.KEY_TRIGGER -> {
                    hasPlayedThisSession = false
                    val isLocked = keyguardManager?.isKeyguardLocked ?: false
                    if (prefs.trigger == PlaybackTrigger.SCREEN_UNLOCK && isLocked) {
                        forwardPlayer?.seekTo(0)
                        forwardPlayer?.pause()
                    }
                }
            }
        }

        override fun onDestroy() {
            super.onDestroy()
            stopReversePlayback()
            try {
                if (wakeLock?.isHeld == true) {
                    wakeLock?.release()
                }
            } catch (_: Exception) {}
            try {
                unregisterReceiver(screenReceiver)
            } catch (e: Exception) {
                Log.w(tag, "Receiver already unregistered", e)
            }
            prefs.unregisterListener(this)
            forwardPlayer?.release()
            reversePlayer?.release()
            forwardPlayer = null
            reversePlayer = null
        }
    }
}
