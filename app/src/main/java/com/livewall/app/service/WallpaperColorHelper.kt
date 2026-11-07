package com.livewall.app.service

import android.app.WallpaperColors
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.livewall.app.data.WallpaperPreferences
import com.livewall.app.model.WallpaperItem

object WallpaperColorHelper {

    private const val TAG = "WallpaperColorHelper"

    @RequiresApi(Build.VERSION_CODES.O_MR1)
    fun computeColors(context: Context, videoUriStr: String): WallpaperColors? {
        val all = WallpaperItem.BUNDLED_WALLPAPERS + WallpaperPreferences(context).getCustomWallpapers()
        val item = all.find { it.videoUri == videoUriStr } ?: all.firstOrNull()

        // 1. Try to load thumbnail and extract with WallpaperColors.fromBitmap
        val bitmap = loadThumbnailBitmap(context, item?.thumbnailUri)
        if (bitmap != null) {
            try {
                val colors = WallpaperColors.fromBitmap(bitmap)
                Log.d(TAG, "Computed WallpaperColors from bitmap for ${item?.title}: $colors")
                return colors
            } catch (e: Exception) {
                Log.w(TAG, "WallpaperColors.fromBitmap failed, falling back to curated palette", e)
            }
        }

        // 2. Curated fallback colors for bundled wallpapers if bitmap decoding fails
        return getCuratedFallbackColors(item?.id)
    }

    private fun loadThumbnailBitmap(context: Context, thumbUri: String?): Bitmap? {
        if (thumbUri.isNullOrBlank()) return null
        return try {
            when {
                thumbUri.startsWith("file:///android_asset/") -> {
                    val assetPath = thumbUri.removePrefix("file:///android_asset/")
                    context.assets.open(assetPath).use { BitmapFactory.decodeStream(it) }
                }
                thumbUri.startsWith("asset:///") -> {
                    val assetPath = thumbUri.removePrefix("asset:///")
                    context.assets.open(assetPath).use { BitmapFactory.decodeStream(it) }
                }
                thumbUri.startsWith("file://") -> {
                    val filePath = Uri.parse(thumbUri).path ?: thumbUri.removePrefix("file://")
                    BitmapFactory.decodeFile(filePath)
                }
                else -> {
                    context.contentResolver.openInputStream(Uri.parse(thumbUri))?.use {
                        BitmapFactory.decodeStream(it)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error decoding thumbnail bitmap: $thumbUri", e)
            null
        }
    }

    @RequiresApi(Build.VERSION_CODES.O_MR1)
    private fun getCuratedFallbackColors(wallpaperId: String?): WallpaperColors? {
        return try {
            val (p, s, t) = when (wallpaperId) {
                "xiaomi_18_pro_light01" -> Triple(0xFFF59E0B.toInt(), 0xFFFBBF24.toInt(), 0xFF78350F.toInt()) // Amber
                "xiaomi_18_pro_light02" -> Triple(0xFF10B981.toInt(), 0xFF34D399.toInt(), 0xFF064E3B.toInt()) // Emerald
                "xiaomi_18_pro_light03" -> Triple(0xFF8B5CF6.toInt(), 0xFFA855F7.toInt(), 0xFF1E1B4B.toInt()) // Violet
                "xiaomi_18_pro_light04" -> Triple(0xFFF97316.toInt(), 0xFFFACC15.toInt(), 0xFF7C2D12.toInt()) // Solar Flare
                "xiaomi_18_pro_light05" -> Triple(0xFF06B6D4.toInt(), 0xFF38BDF8.toInt(), 0xFF083344.toInt()) // Glacier
                "xiaomi_18_pro_max_wh"  -> Triple(0xFFE2E8F0.toInt(), 0xFF94A3B8.toInt(), 0xFF0F172A.toInt()) // Pure White
                "xiaomi_18_pro_max_bk"  -> Triple(0xFF1E293B.toInt(), 0xFFF59E0B.toInt(), 0xFF0F172A.toInt()) // Obsidian
                "xiaomi_18_pro_max_cy"  -> Triple(0xFF00E5FF.toInt(), 0xFF14B8A6.toInt(), 0xFF042F2E.toInt()) // Cyber Cyan
                "xiaomi_18_pro_max_db"  -> Triple(0xFF2563EB.toInt(), 0xFF60A5FA.toInt(), 0xFF1E3A8A.toInt()) // Deep Blue
                "xiaomi_18_pro_max_pk"  -> Triple(0xFFF43F5E.toInt(), 0xFFFB7185.toInt(), 0xFF881337.toInt()) // Blush Pink
                "coloros_17"            -> Triple(0xFFFF6F61.toInt(), 0xFFFFB74D.toInt(), 0xFF311B92.toInt()) // ColorOS Sunset
                else                    -> Triple(0xFF38BDF8.toInt(), 0xFFFBBF24.toInt(), 0xFF1E293B.toInt())
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                WallpaperColors(
                    AndroidColor.valueOf(p),
                    AndroidColor.valueOf(s),
                    AndroidColor.valueOf(t)
                )
            } else {
                val primaryColor = AndroidColor.valueOf(p)
                val secondaryColor = AndroidColor.valueOf(s)
                val tertiaryColor = AndroidColor.valueOf(t)
                // Use reflection or constructor for API 27
                WallpaperColors(primaryColor, secondaryColor, tertiaryColor)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error constructing curated WallpaperColors", e)
            null
        }
    }
}
