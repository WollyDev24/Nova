package com.livewall.app.data

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.livewall.app.model.WallpaperItem
import java.io.File
import java.io.FileOutputStream

object CustomWallpaperManager {

    fun importVideo(context: Context, videoUri: Uri): WallpaperItem? {
        return try {
            val dir = File(context.filesDir, "custom_wallpapers").apply { mkdirs() }
            val timestamp = System.currentTimeMillis()
            val videoFile = File(dir, "wall_$timestamp.mp4")
            val thumbFile = File(dir, "thumb_$timestamp.webp")

            // Copy video file to internal storage
            context.contentResolver.openInputStream(videoUri)?.use { input ->
                FileOutputStream(videoFile).use { output ->
                    input.copyTo(output)
                }
            } ?: return null

            // Extract metadata & thumbnail using MediaMetadataRetriever
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(videoFile.absolutePath)

            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 3000L
            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH) ?: "1080"
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT) ?: "1920"

            val frame = retriever.getFrameAtTime(500_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: retriever.frameAtTime

            frame?.let { bmp ->
                FileOutputStream(thumbFile).use { out ->
                    bmp.compress(Bitmap.CompressFormat.WEBP, 85, out)
                }
            }
            retriever.release()

            WallpaperItem(
                id = "custom_$timestamp",
                title = "Custom Video ${dir.listFiles()?.size ?: 1}",
                collection = "Custom",
                videoUri = Uri.fromFile(videoFile).toString(),
                thumbnailUri = Uri.fromFile(thumbFile).toString(),
                durationSec = (durationMs / 1000f),
                resolution = "${width}x${height}",
                description = "Imported from device gallery",
                isCustom = true
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
