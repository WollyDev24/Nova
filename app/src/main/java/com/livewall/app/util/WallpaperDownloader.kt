package com.livewall.app.util

import android.content.Context
import android.net.Uri
import android.util.Log
import com.livewall.app.model.WallpaperItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

object WallpaperDownloader {
    private const val TAG = "WallpaperDownloader"
    const val GITHUB_RAW_BASE = "https://raw.githubusercontent.com/WollyDev24/Nova/main/wallpapers/"

    /**
     * Resolves the relative path inside the wallpapers directory,
     * e.g. "Pixel-11-Fold/Pine.mp4" from "asset:///wallpapers/Pixel-11-Fold/Pine.mp4".
     */
    fun getRelativePath(item: WallpaperItem): String {
        return when {
            item.videoUri.startsWith("asset:///wallpapers/") -> item.videoUri.removePrefix("asset:///wallpapers/")
            item.videoUri.startsWith("asset:///") -> item.videoUri.removePrefix("asset:///")
            else -> item.videoUri.substringAfterLast("/")
        }
    }

    /**
     * Target permanent offline storage file for the forward video.
     */
    fun getLocalVideoFile(context: Context, item: WallpaperItem): File {
        val rel = getRelativePath(item)
        val wallpapersDir = File(context.filesDir, "wallpapers")
        return File(wallpapersDir, rel)
    }

    /**
     * Target permanent offline storage file for the reverse video (_rev.mp4).
     */
    fun getLocalReverseFile(context: Context, item: WallpaperItem): File {
        val rel = getRelativePath(item)
        val revRel = if (rel.endsWith(".mp4")) {
            rel.substringBeforeLast(".mp4") + "_rev.mp4"
        } else {
            rel + "_rev.mp4"
        }
        val wallpapersDir = File(context.filesDir, "wallpapers")
        return File(wallpapersDir, revRel)
    }

    /**
     * Checks if a specific asset path is physically bundled in the APK's assets directory.
     */
    fun isAssetBundled(context: Context, assetPath: String): Boolean {
        val cleanPath = assetPath.removePrefix("asset:///")
        return try {
            context.assets.open(cleanPath).use { }
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Checks whether the wallpaper is available locally (either bundled in APK assets,
     * downloaded into private app storage, or a local custom file).
     */
    fun isDownloaded(context: Context, item: WallpaperItem): Boolean {
        if (item.isCustom) return true

        if (item.videoUri.startsWith("asset:///")) {
            val assetPath = item.videoUri.removePrefix("asset:///")
            if (isAssetBundled(context, assetPath)) {
                return true
            }
            val localFile = getLocalVideoFile(context, item)
            return localFile.exists() && localFile.length() > 0L
        }

        if (item.videoUri.startsWith("file://") || item.videoUri.startsWith("/")) {
            val path = if (item.videoUri.startsWith("file://")) item.videoUri.removePrefix("file://") else item.videoUri
            val f = File(path)
            return f.exists() && f.length() > 0L
        }

        if (item.videoUri.startsWith("http://") || item.videoUri.startsWith("https://")) {
            val communityDir = File(context.filesDir, "community_videos")
            val dest = File(communityDir, "${item.id}.mp4")
            return dest.exists() && dest.length() > 0L
        }

        return false
    }

    /**
     * Resolves the playable URI string for ExoPlayer or LiveWallpaperService:
     * - Returns original asset URI if physically bundled
     * - Returns file:// URI pointing to downloaded file in private app storage
     * - Returns file:// URI for custom imported videos
     */
    fun getPlayableUri(context: Context, item: WallpaperItem): String {
        return getPlayableUri(context, item.videoUri, item.id)
    }

    /**
     * Resolves any raw wallpaper URI string (asset:///, file:///, etc.) into a locally playable URI.
     */
    fun getPlayableUri(context: Context, rawUri: String, wallpaperId: String? = null): String {
        if (rawUri.startsWith("asset:///")) {
            val assetPath = rawUri.removePrefix("asset:///")
            if (isAssetBundled(context, assetPath)) {
                return rawUri
            }
            val rel = rawUri.removePrefix("asset:///wallpapers/")
            val localFile = File(File(context.filesDir, "wallpapers"), rel)
            if (localFile.exists() && localFile.length() > 0L) {
                return "file://${localFile.absolutePath}"
            }
        }

        if (rawUri.startsWith("file://")) return rawUri
        if (rawUri.startsWith("/")) return "file://$rawUri"

        if (rawUri.startsWith("http://") || rawUri.startsWith("https://")) {
            if (wallpaperId != null) {
                val communityDir = File(context.filesDir, "community_videos")
                val dest = File(communityDir, "$wallpaperId.mp4")
                if (dest.exists() && dest.length() > 0L) {
                    return "file://${dest.absolutePath}"
                }
            }
        }

        return rawUri
    }

    /**
     * Resolves the reverse playback video URI (_rev.mp4) if available.
     */
    fun getPlayableReverseUri(context: Context, forwardUriStr: String): String? {
        if (!forwardUriStr.endsWith(".mp4")) return null

        val revUriStr = forwardUriStr.substringBeforeLast(".mp4") + "_rev.mp4"

        // 1. Bundled asset check
        if (revUriStr.startsWith("asset:///")) {
            val assetPath = revUriStr.removePrefix("asset:///")
            if (isAssetBundled(context, assetPath)) {
                return revUriStr
            }
            // If asset not bundled, check downloaded location in filesDir/wallpapers/
            val rel = revUriStr.removePrefix("asset:///wallpapers/")
            val localRev = File(File(context.filesDir, "wallpapers"), rel)
            if (localRev.exists() && localRev.length() > 0L) {
                return "file://${localRev.absolutePath}"
            }
        }

        // 2. Local file:// check
        if (revUriStr.startsWith("file://")) {
            val path = Uri.parse(revUriStr).path
            if (path != null && File(path).exists() && File(path).length() > 0L) {
                return revUriStr
            }
        }

        // 3. Absolute path check
        if (revUriStr.startsWith("/")) {
            val f = File(revUriStr)
            if (f.exists() && f.length() > 0L) {
                return "file://${f.absolutePath}"
            }
        }

        return null
    }

    /**
     * Downloads a wallpaper (both forward video and reverse video) to app-private storage,
     * reporting download progress from 0.0 to 1.0.
     */
    suspend fun downloadWallpaper(
        context: Context,
        item: WallpaperItem,
        onProgress: (Float) -> Unit = {}
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            if (isDownloaded(context, item)) {
                val playable = getLocalVideoFile(context, item)
                onProgress(1.0f)
                return@withContext Result.success(playable)
            }

            // Determine forward download URL and destination
            val (forwardUrl, destFile) = if (item.videoUri.startsWith("http://") || item.videoUri.startsWith("https://")) {
                val communityDir = File(context.filesDir, "community_videos").apply { mkdirs() }
                item.videoUri to File(communityDir, "${item.id}.mp4")
            } else {
                val rel = getRelativePath(item)
                val dest = getLocalVideoFile(context, item)
                dest.parentFile?.mkdirs()
                "$GITHUB_RAW_BASE$rel" to dest
            }

            Log.d(TAG, "Downloading forward video from $forwardUrl to ${destFile.absolutePath}")

            // Download forward video (weights 0.0 -> 0.70 of total progress)
            downloadFileWithProgress(forwardUrl, destFile) { p ->
                onProgress(p * 0.70f)
            }

            // Determine reverse video URL and destination (if applicable)
            if (item.videoUri.startsWith("asset:///")) {
                val rel = getRelativePath(item)
                val revRel = if (rel.endsWith(".mp4")) rel.substringBeforeLast(".mp4") + "_rev.mp4" else rel + "_rev.mp4"
                val revUrl = "$GITHUB_RAW_BASE$revRel"
                val revDest = getLocalReverseFile(context, item)
                revDest.parentFile?.mkdirs()

                try {
                    Log.d(TAG, "Checking reverse video from $revUrl")
                    downloadFileWithProgress(revUrl, revDest) { p ->
                        onProgress(0.70f + p * 0.30f)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Reverse video not available or failed (non-critical): ${e.message}")
                    revDest.delete()
                }
            }

            onProgress(1.0f)
            Result.success(destFile)
        } catch (e: Exception) {
            Log.e(TAG, "Failed downloading wallpaper ${item.id}", e)
            Result.failure(e)
        }
    }

    private fun downloadFileWithProgress(
        urlStr: String,
        destination: File,
        onProgress: (Float) -> Unit
    ) {
        val tempFile = File(destination.parentFile, "${destination.name}.tmp")
        var connection: HttpURLConnection? = null
        try {
            val url = URL(urlStr)
            connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 30_000
                instanceFollowRedirects = true
            }

            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                throw IllegalStateException("HTTP error $responseCode for $urlStr")
            }

            val totalBytes = connection.contentLength.takeIf { it > 0 } ?: -1
            var bytesReadTotal = 0L

            connection.inputStream.use { input: InputStream ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(16 * 1024)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        bytesReadTotal += bytesRead
                        if (totalBytes > 0) {
                            val progress = (bytesReadTotal.toFloat() / totalBytes).coerceIn(0f, 1f)
                            onProgress(progress)
                        }
                    }
                    output.flush()
                }
            }

            if (!tempFile.renameTo(destination)) {
                tempFile.copyTo(destination, overwrite = true)
                tempFile.delete()
            }
        } catch (e: Exception) {
            tempFile.delete()
            throw e
        } finally {
            connection?.disconnect()
        }
    }
}
