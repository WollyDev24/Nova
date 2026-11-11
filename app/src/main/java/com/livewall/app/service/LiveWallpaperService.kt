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
import android.os.Bundle
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.service.wallpaper.WallpaperService
import android.util.Log
import android.view.MotionEvent
import android.view.Surface
import android.view.SurfaceHolder
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.livewall.app.data.PlaybackTrigger
import com.livewall.app.data.ScalingMode
import com.livewall.app.data.WallpaperPreferences
import com.livewall.app.model.WallpaperAnimationCurve
import com.livewall.app.model.WallpaperItem
import com.livewall.app.util.WallpaperAnimationHelper
import com.livewall.app.util.WallpaperDownloader
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
        private var player: ExoPlayer? = null
        private var forwardMediaItem: MediaItem? = null
        private var reverseMediaItem: MediaItem? = null
        private var keyguardManager: KeyguardManager? = null
        private var isScreenVisible = false
        private var isUserUnlocked = false
        private var hasPlayedThisSession = false
        private var unlockMonitorJob: Job? = null
        private var cachedWallpaperColors: WallpaperColors? = null
        private var reversePlaybackJob: Job? = null
        private var isReversing = false
        private var wakeLock: PowerManager.WakeLock? = null
        private var currentSurfaceHolder: SurfaceHolder? = null
        private var hasReverseVideo = false
        private var videoDurationMs = 2000L
        private var activeCurve: WallpaperAnimationCurve? = null
        private var splitPositionMs: Long = 3000L
        private var touchStartY = 0f
        private var isTouchDragging = false
        private var springSnapJob: Job? = null

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
            setTouchEventsEnabled(true)
            currentSurfaceHolder = surfaceHolder
            prefs = WallpaperPreferences(applicationContext)
            keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
            isUserUnlocked = !(keyguardManager?.isKeyguardLocked ?: false)
            val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
            wakeLock = pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "LiveWall:AodReverseWakeLock")

            initPlayer()
            prefs.registerListener(this)

            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_USER_PRESENT)
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    registerReceiver(screenReceiver, filter, Context.RECEIVER_EXPORTED)
                } else {
                    registerReceiver(screenReceiver, filter)
                }
            } catch (e: Exception) {
                Log.e(tag, "Failed to register screenReceiver", e)
            }
        }

        @OptIn(UnstableApi::class)
        private fun initPlayer() {
            try {
                player?.release()

                val scaling = getDesiredScalingMode()

                player = ExoPlayer.Builder(applicationContext).build().apply {
                    repeatMode = Player.REPEAT_MODE_OFF
                    volume = 0f
                    videoScalingMode = scaling

                    addListener(object : Player.Listener {
                        override fun onPlaybackStateChanged(playbackState: Int) {
                            if (playbackState == Player.STATE_READY) {
                                val d = duration
                                if (d > 0L) {
                                    videoDurationMs = d
                                    activeCurve?.let { curve ->
                                        splitPositionMs = ((curve.splitIndex.toFloat() / 260f) * d).toLong().coerceIn(0L, d)
                                    }
                                }
                            } else if (playbackState == Player.STATE_ENDED) {
                                if (isReversing) {
                                    Log.d(tag, "Reverse playback reached end of animation")
                                    finishReversePlayback()
                                } else {
                                    Log.d(tag, "Forward video completed, paused at final frame.")
                                }
                            }
                        }

                        override fun onVideoSizeChanged(videoSize: VideoSize) {
                            videoScalingMode = getDesiredScalingMode()
                            Log.d(tag, "onVideoSizeChanged: w=${videoSize.width}, h=${videoSize.height}, scaling=${getDesiredScalingMode()}")
                        }

                        override fun onRenderedFirstFrame() {
                            videoScalingMode = getDesiredScalingMode()
                            Log.d(tag, "onRenderedFirstFrame: reinforced videoScalingMode=${getDesiredScalingMode()}")
                        }

                        override fun onPlayerError(error: PlaybackException) {
                            Log.e(tag, "Player error, attempting recovery", error)
                            try {
                                loadCurrentWallpaper()
                                val surface = currentSurfaceHolder?.surface
                                if (surface != null && surface.isValid) {
                                    player?.setVideoSurface(surface)
                                    player?.videoScalingMode = getDesiredScalingMode()
                                    if (isScreenVisible) {
                                        replayVideo()
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e(tag, "Failed recovering player", e)
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

            // If running a dedicated subclass service, resolve its specific wallpaper item
            val boundWallpaperId = WallpaperColorHelper.getWallpaperIdForServiceClass(this@LiveWallpaperService::class.java)
            val dedicatedItem = if (boundWallpaperId != null) {
                WallpaperItem.BUNDLED_WALLPAPERS.find { it.id == boundWallpaperId }
            } else {
                null
            }

            val uriStr = dedicatedItem?.videoUri ?: prefs.selectedWallpaperUri.ifBlank {
                WallpaperItem.DEFAULT_WALLPAPER.videoUri
            }
            val wallpaperId = dedicatedItem?.id ?: prefs.selectedWallpaperId
            val playableUriStr = WallpaperDownloader.getPlayableUri(applicationContext, uriStr, wallpaperId)
            try {
                val fwdItem = MediaItem.fromUri(Uri.parse(playableUriStr))
                forwardMediaItem = fwdItem

                val reverseUri = getReverseUri(playableUriStr)
                if (reverseUri != null) {
                    reverseMediaItem = MediaItem.fromUri(Uri.parse(reverseUri))
                    hasReverseVideo = true
                    Log.d(tag, "Loaded forward=$playableUriStr and reverse=$reverseUri")
                } else {
                    reverseMediaItem = null
                    hasReverseVideo = false
                    Log.d(tag, "Single forward player loaded: $playableUriStr")
                }

                p.setMediaItem(fwdItem)
                p.prepare()
                updateWallpaperColors()

                activeCurve = WallpaperAnimationHelper.getCurveForWallpaper(applicationContext, wallpaperId, playableUriStr)
                if (activeCurve != null && activeCurve!!.isSupportCurve) {
                    val d = if (videoDurationMs > 0) videoDurationMs else 4333L
                    splitPositionMs = ((activeCurve!!.splitIndex.toFloat() / 260f) * d).toLong().coerceIn(0L, d)
                    Log.d(tag, "Loaded animation curve for $wallpaperId: split=$splitPositionMs ms, rest=${activeCurve!!.startPauseValueMs} ms")
                } else {
                    activeCurve = null
                }
            } catch (e: Exception) {
                Log.e(tag, "Error loading media item: $playableUriStr, attempting default wallpaper fallback", e)
                try {
                    val fallbackUri = WallpaperDownloader.getPlayableUri(applicationContext, WallpaperItem.DEFAULT_WALLPAPER.videoUri)
                    val fallbackItem = MediaItem.fromUri(Uri.parse(fallbackUri))
                    forwardMediaItem = fallbackItem
                    reverseMediaItem = null
                    hasReverseVideo = false
                    p.setMediaItem(fallbackItem)
                    p.prepare()
                } catch (e2: Exception) {
                    Log.e(tag, "Critical failure during wallpaper load fallback", e2)
                }
            }
        }

        override fun onComputeColors(): WallpaperColors? {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                if (cachedWallpaperColors == null) {
                    val boundWallpaperId = WallpaperColorHelper.getWallpaperIdForServiceClass(this@LiveWallpaperService::class.java)
                    val wallpaperId = boundWallpaperId ?: prefs.selectedWallpaperId
                    val uriStr = boundWallpaperId?.let { id -> WallpaperItem.BUNDLED_WALLPAPERS.find { it.id == id }?.videoUri } ?: prefs.selectedWallpaperUri
                    cachedWallpaperColors = WallpaperColorHelper.computeColors(applicationContext, uriStr, wallpaperId)
                }
                return cachedWallpaperColors ?: super.onComputeColors()
            }
            return null
        }

        private fun updateWallpaperColors() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                val boundWallpaperId = WallpaperColorHelper.getWallpaperIdForServiceClass(this@LiveWallpaperService::class.java)
                val wallpaperId = boundWallpaperId ?: prefs.selectedWallpaperId
                val uriStr = boundWallpaperId?.let { id -> WallpaperItem.BUNDLED_WALLPAPERS.find { it.id == id }?.videoUri } ?: prefs.selectedWallpaperUri
                val newColors = WallpaperColorHelper.computeColors(applicationContext, uriStr, wallpaperId)
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
            val scaling = getDesiredScalingMode()
            player?.setVideoSurface(holder.surface)
            player?.videoScalingMode = scaling
            setSurfaceFrameRate(120f)
            updateWallpaperColors()
            if (isPreview) {
                replayVideo()
            } else if (isScreenVisible) {
                val isLocked = keyguardManager?.isKeyguardLocked ?: false
                if (!isLocked) {
                    isUserUnlocked = true
                }
                val isLockScreen = !isUserUnlocked && isLocked
                if (!isLockScreen || prefs.trigger == PlaybackTrigger.SCREEN_ON) {
                    if (!hasPlayedThisSession || prefs.replayOnHomeReturn) {
                        if (playUnlockTransition()) {
                            hasPlayedThisSession = true
                        }
                    } else if (player?.playbackState != Player.STATE_ENDED) {
                        player?.play()
                    }
                }
            }
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            currentSurfaceHolder = holder
            player?.videoScalingMode = getDesiredScalingMode()
            setSurfaceFrameRate(120f)
            if (isScreenVisible && !hasPlayedThisSession) {
                val isLocked = keyguardManager?.isKeyguardLocked ?: false
                if (!isLocked) {
                    isUserUnlocked = true
                }
                val isLockScreen = !isUserUnlocked && isLocked
                if (!isLockScreen || prefs.trigger == PlaybackTrigger.SCREEN_ON) {
                    if (playUnlockTransition()) {
                        hasPlayedThisSession = true
                    }
                }
            }
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            super.onSurfaceDestroyed(holder)
            currentSurfaceHolder = null
            player?.clearVideoSurface()
        }

        override fun onVisibilityChanged(visible: Boolean) {
            super.onVisibilityChanged(visible)
            Log.d(tag, "onVisibilityChanged: visible=$visible, isUserUnlocked=$isUserUnlocked, isReversing=$isReversing")
            isScreenVisible = visible
            if (visible) {
                stopReversePlayback()
                if (isPreview) {
                    replayVideo()
                    return
                }

                val isLocked = keyguardManager?.isKeyguardLocked ?: false
                if (!isLocked) {
                    isUserUnlocked = true
                }
                val isLockScreen = !isUserUnlocked && isLocked
                if (isLockScreen && prefs.trigger == PlaybackTrigger.SCREEN_UNLOCK) {
                    // Holding on lock screen
                    val p = player
                    val fwdItem = forwardMediaItem
                    if (p != null && fwdItem != null && p.currentMediaItem != fwdItem) {
                        p.setMediaItem(fwdItem)
                        p.videoScalingMode = getDesiredScalingMode()
                    }
                    val restPos = activeCurve?.startPauseValueMs ?: 0L
                    player?.seekTo(restPos)
                    player?.pause()
                    startUnlockMonitor()
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
                    if (replayVideo()) {
                        hasPlayedThisSession = true
                    }
                }
            } else {
                if (!isReversing) {
                    player?.pause()
                }
                val isLocked = keyguardManager?.isKeyguardLocked ?: false
                if (isLocked) {
                    isUserUnlocked = false
                }
            }
        }

        override fun onOffsetsChanged(
            xOffset: Float,
            yOffset: Float,
            xOffsetStep: Float,
            yOffsetStep: Float,
            xPixelOffset: Int,
            yPixelOffset: Int
        ) {
            super.onOffsetsChanged(xOffset, yOffset, xOffsetStep, yOffsetStep, xPixelOffset, yPixelOffset)
            if (prefs.trigger == PlaybackTrigger.SCREEN_UNLOCK && !isUserUnlocked && isScreenVisible) {
                val isLocked = keyguardManager?.isKeyguardLocked ?: false
                if (!isLocked) {
                    Log.d(tag, "onOffsetsChanged detected Keyguard unlocked!")
                    handleUserPresent()
                }
            }
        }

        override fun onCommand(
            action: String,
            x: Int,
            y: Int,
            z: Int,
            extras: Bundle?,
            resultRequested: Boolean
        ): Bundle? {
            if (prefs.trigger == PlaybackTrigger.SCREEN_UNLOCK && !isUserUnlocked && isScreenVisible) {
                val isLocked = keyguardManager?.isKeyguardLocked ?: false
                if (!isLocked) {
                    Log.d(tag, "onCommand($action) detected Keyguard unlocked!")
                    handleUserPresent()
                }
            }
            return super.onCommand(action, x, y, z, extras, resultRequested)
        }

        override fun onTouchEvent(event: MotionEvent) {
            super.onTouchEvent(event)
            val curve = activeCurve
            val isLocked = keyguardManager?.isKeyguardLocked ?: false
            val isLockScreen = !isUserUnlocked && isLocked

            // Only enable interactive scrubbing on the lock screen when a curve is present
            if (curve == null || !isLockScreen || !curve.isSupportCurve) {
                return
            }

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    touchStartY = event.y
                    isTouchDragging = true
                    springSnapJob?.cancel()
                    springSnapJob = null
                }
                MotionEvent.ACTION_MOVE -> {
                    if (isTouchDragging) {
                        val deltaY = touchStartY - event.y // positive when swiping up
                        val sensitivity = splitPositionMs.toFloat() / (curve.slideCountThreshold * 8f).coerceAtLeast(600f)
                        val targetOffset = (deltaY * sensitivity * (1f / curve.slideDampingRatio.coerceAtLeast(0.1f))).toLong()
                        val restPos = curve.startPauseValueMs
                        val newPos = (restPos + targetOffset).coerceIn(0L, splitPositionMs)

                        player?.seekTo(newPos)
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (isTouchDragging) {
                        isTouchDragging = false
                        animateSpringBackToRest(curve)
                    }
                }
            }
        }

        private fun animateSpringBackToRest(curve: WallpaperAnimationCurve) {
            val p = player ?: return
            val currentPos = p.currentPosition
            val restPos = curve.startPauseValueMs
            if (currentPos == restPos) return

            springSnapJob?.cancel()
            springSnapJob = CoroutineScope(Dispatchers.Main.immediate).launch {
                val startPos = currentPos
                val startTime = SystemClock.uptimeMillis()
                val durationMs = 350L

                while (isActive) {
                    val elapsed = SystemClock.uptimeMillis() - startTime
                    val t = (elapsed.toFloat() / durationMs).coerceIn(0f, 1f)
                    // Overdamped cubic spring curve matching pauseDampingRatio
                    val factor = 1f - (1f - t) * (1f - t) * (1f - t)
                    val pos = (startPos + (restPos - startPos) * factor).toLong()
                    p.seekTo(pos)

                    if (t >= 1f) {
                        p.seekTo(restPos)
                        p.pause()
                        break
                    }
                    delay(16)
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

        private fun getDesiredScalingMode(): Int {
            return if (prefs.scalingMode == ScalingMode.CROP) {
                C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING
            } else {
                C.VIDEO_SCALING_MODE_SCALE_TO_FIT
            }
        }

        private fun getReverseUri(forwardUriStr: String): String? {
            return WallpaperDownloader.getPlayableReverseUri(applicationContext, forwardUriStr)
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

                val p = player
                val fwdItem = forwardMediaItem
                if (p != null && fwdItem != null) {
                    val revPos = p.currentPosition
                    val fwdPos = (videoDurationMs - revPos).coerceIn(0L, videoDurationMs)
                    Log.d(tag, "Transferring back to forwardMediaItem at fwdPos $fwdPos")
                    if (p.currentMediaItem != fwdItem) {
                        p.setMediaItem(fwdItem)
                    }
                    p.videoScalingMode = getDesiredScalingMode()
                    p.seekTo(fwdPos)
                }
                setSurfaceFrameRate(120f)
            }
        }

        private fun finishReversePlayback() {
            Log.d(tag, "finishReversePlayback: reverse video reached first frame")
            isReversing = false
            reversePlaybackJob?.cancel()
            reversePlaybackJob = null

            val p = player
            val fwdItem = forwardMediaItem
            if (p != null && fwdItem != null) {
                if (p.currentMediaItem != fwdItem) {
                    p.setMediaItem(fwdItem)
                }
                p.videoScalingMode = getDesiredScalingMode()
                p.seekTo(0)
                p.pause()
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
            val p = player ?: return

            val currentForwardPos = p.currentPosition
            val duration = if (videoDurationMs > 0) videoDurationMs else 2000L
            Log.d(tag, "playInReverse requested: currentForwardPos=$currentForwardPos, duration=$duration, hasReverseVideo=$hasReverseVideo")

            if (currentForwardPos <= 50L) {
                p.seekTo(0)
                p.pause()
                return
            }

            isReversing = true
            try {
                wakeLock?.acquire(3500L)
            } catch (e: Exception) {
                Log.w(tag, "Failed to acquire wakeLock", e)
            }
            setSurfaceFrameRate(120f)

            val revItem = reverseMediaItem
            if (hasReverseVideo && revItem != null) {
                val revStartPos = (duration - currentForwardPos).coerceIn(0L, duration)
                Log.d(tag, "Playing reverse track at $revStartPos")
                p.setMediaItem(revItem)
                p.videoScalingMode = getDesiredScalingMode()
                p.prepare()
                p.seekTo(revStartPos)
                p.play()

                reversePlaybackJob = CoroutineScope(Dispatchers.Main.immediate).launch {
                    while (isActive && isReversing) {
                        if (p.playbackState == Player.STATE_ENDED || p.currentPosition >= duration - 30L) {
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
            p.pause()
            val durationMs = currentForwardPos
            val startTime = SystemClock.uptimeMillis()

            reversePlaybackJob = CoroutineScope(Dispatchers.Main.immediate).launch {
                try {
                    while (isActive) {
                        val elapsed = SystemClock.uptimeMillis() - startTime
                        val fraction = (elapsed.toFloat() / durationMs).coerceIn(0f, 1f)
                        val targetPos = (currentForwardPos * (1f - fraction)).toLong()

                        p.seekTo(targetPos)

                        if (fraction >= 1f || targetPos <= 0L) {
                            p.seekTo(0)
                            p.pause()
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

        private fun startUnlockMonitor() {
            unlockMonitorJob?.cancel()
            if (prefs.trigger != PlaybackTrigger.SCREEN_UNLOCK || isUserUnlocked) return
            Log.d(tag, "startUnlockMonitor: actively watching for Keyguard unlock")
            unlockMonitorJob = CoroutineScope(Dispatchers.Main.immediate).launch {
                while (isActive && isScreenVisible && !isUserUnlocked) {
                    val isLocked = keyguardManager?.isKeyguardLocked ?: false
                    if (!isLocked) {
                        Log.d(tag, "Unlock monitor detected Keyguard unlocked!")
                        handleUserPresent()
                        break
                    }
                    delay(50)
                }
            }
        }

        private fun handleScreenOn() {
            Log.d(tag, "handleScreenOn: trigger=${prefs.trigger}")
            stopReversePlayback()
            val isLocked = keyguardManager?.isKeyguardLocked ?: false
            if (!isLocked) {
                isUserUnlocked = true
            }
            when (prefs.trigger) {
                PlaybackTrigger.SCREEN_ON -> {
                    if (!hasPlayedThisSession) {
                        if (replayVideo()) {
                            hasPlayedThisSession = true
                        }
                    }
                }
                PlaybackTrigger.SCREEN_UNLOCK -> {
                    val isLockScreen = !isUserUnlocked && isLocked
                    if (isLockScreen) {
                        hasPlayedThisSession = false
                        val p = player
                        val fwdItem = forwardMediaItem
                        if (p != null && fwdItem != null && p.currentMediaItem != fwdItem) {
                            p.setMediaItem(fwdItem)
                            p.videoScalingMode = getDesiredScalingMode()
                        }
                        val restPos = activeCurve?.startPauseValueMs ?: 0L
                        player?.seekTo(restPos)
                        player?.pause()
                        startUnlockMonitor()
                    } else if (isUserUnlocked && !hasPlayedThisSession) {
                        if (playUnlockTransition()) {
                            hasPlayedThisSession = true
                        }
                    }
                }
            }
        }

        private fun handleUserPresent() {
            Log.d(tag, "handleUserPresent: trigger=${prefs.trigger}")
            unlockMonitorJob?.cancel()
            unlockMonitorJob = null
            springSnapJob?.cancel()
            springSnapJob = null
            isTouchDragging = false
            stopReversePlayback()
            isUserUnlocked = true
            // Fired whenever the device is unlocked!
            if (prefs.trigger == PlaybackTrigger.SCREEN_UNLOCK) {
                if (playUnlockTransition()) {
                    hasPlayedThisSession = true
                }
            }
        }

        private fun handleScreenOff() {
            val aodOn = isAodEnabled()
            Log.d(tag, "handleScreenOff: AOD enabled=$aodOn")
            unlockMonitorJob?.cancel()
            unlockMonitorJob = null
            springSnapJob?.cancel()
            springSnapJob = null
            isTouchDragging = false
            isUserUnlocked = false
            hasPlayedThisSession = false
            if (aodOn) {
                playInReverse()
            } else {
                stopReversePlayback()
                val p = player
                val fwdItem = forwardMediaItem
                if (p != null && fwdItem != null && p.currentMediaItem != fwdItem) {
                    p.setMediaItem(fwdItem)
                    p.videoScalingMode = getDesiredScalingMode()
                }
                val restPos = activeCurve?.startPauseValueMs ?: 0L
                player?.pause()
                player?.seekTo(restPos)
            }
        }

        private fun playUnlockTransition(): Boolean {
            val curve = activeCurve
            val p = player ?: return false
            val fwdItem = forwardMediaItem ?: return false
            val surface = currentSurfaceHolder?.surface
            if (surface != null && surface.isValid) {
                p.setVideoSurface(surface)
                if (p.currentMediaItem != fwdItem) {
                    p.setMediaItem(fwdItem)
                    p.prepare()
                } else if (p.playbackState == Player.STATE_IDLE) {
                    p.prepare()
                }
                p.videoScalingMode = getDesiredScalingMode()
                if (curve != null && curve.isSplit) {
                    val startUnlockPos = splitPositionMs
                    Log.d(tag, "playUnlockTransition: curve active, playing unlock segment from $startUnlockPos ms to end")
                    p.seekTo(startUnlockPos)
                    p.play()
                } else {
                    p.seekTo(0)
                    p.play()
                }
                return true
            }
            Log.d(tag, "playUnlockTransition deferred: surface is not yet valid")
            return false
        }

        private fun replayVideo(): Boolean {
            val surface = currentSurfaceHolder?.surface
            val p = player ?: return false
            val fwdItem = forwardMediaItem ?: return false
            if (surface != null && surface.isValid) {
                p.setVideoSurface(surface)
                if (p.currentMediaItem != fwdItem) {
                    p.setMediaItem(fwdItem)
                    p.prepare()
                } else if (p.playbackState == Player.STATE_IDLE) {
                    p.prepare()
                }
                p.videoScalingMode = getDesiredScalingMode()
                p.seekTo(0)
                p.play()
                return true
            }
            Log.d(tag, "replayVideo deferred: surface is not yet valid")
            return false
        }

        @OptIn(UnstableApi::class)
        override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
            when (key) {
                WallpaperPreferences.KEY_SELECTED_URI,
                WallpaperPreferences.KEY_SELECTED_ID -> {
                    // Only reload if this is a generic LiveWallpaperService instance, or matches this dedicated subclass
                    val boundWallpaperId = WallpaperColorHelper.getWallpaperIdForServiceClass(this@LiveWallpaperService::class.java)
                    if (boundWallpaperId == null || boundWallpaperId == prefs.selectedWallpaperId) {
                        cachedWallpaperColors = null
                        hasPlayedThisSession = false
                        stopReversePlayback()
                        loadCurrentWallpaper()
                        replayVideo()
                    }
                }
                WallpaperPreferences.KEY_SCALING -> {
                    player?.videoScalingMode = getDesiredScalingMode()
                }
                WallpaperPreferences.KEY_TRIGGER -> {
                    hasPlayedThisSession = false
                    val isLocked = keyguardManager?.isKeyguardLocked ?: false
                    isUserUnlocked = !isLocked
                    if (prefs.trigger == PlaybackTrigger.SCREEN_UNLOCK && isLocked) {
                        val p = player
                        val fwdItem = forwardMediaItem
                        if (p != null && fwdItem != null && p.currentMediaItem != fwdItem) {
                            p.setMediaItem(fwdItem)
                            p.videoScalingMode = getDesiredScalingMode()
                        }
                        player?.seekTo(0)
                        player?.pause()
                    }
                }
            }
        }

        override fun onDestroy() {
            super.onDestroy()
            unlockMonitorJob?.cancel()
            unlockMonitorJob = null
            springSnapJob?.cancel()
            springSnapJob = null
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
            player?.release()
            player = null
        }
    }
}
