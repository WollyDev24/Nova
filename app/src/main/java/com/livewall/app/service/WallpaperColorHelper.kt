package com.livewall.app.service

import android.app.WallpaperColors
import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.livewall.app.data.WallpaperPreferences
import com.livewall.app.model.WallpaperItem

object WallpaperColorHelper {

    private const val TAG = "WallpaperColorHelper"

    /**
     * Computes WallpaperColors tailored for Samsung One UI & Android Material You.
     * Prioritizes vibrant, saturated tones over dark/black backgrounds so Samsung
     * One UI generates fitting, colorful palette swatches instead of dull grays.
     */
    @RequiresApi(Build.VERSION_CODES.O_MR1)
    fun computeColors(context: Context, videoUriStr: String): WallpaperColors? {
        val all = WallpaperItem.BUNDLED_WALLPAPERS + WallpaperPreferences(context).getCustomWallpapers()
        val item = all.find { it.videoUri == videoUriStr } ?: all.firstOrNull() ?: return null

        // 1. If it's a bundled wallpaper, use our exact curated vibrant palette
        val curatedColors = getCuratedColors(item.id)
        if (curatedColors != null) {
            Log.d(TAG, "Using curated vibrant WallpaperColors for ${item.title}: $curatedColors")
            return curatedColors
        }

        // 2. For custom wallpapers: Smart saturation-filtered extraction (ignoring black background)
        val bitmap = loadThumbnailBitmap(context, item.thumbnailUri)
        if (bitmap != null) {
            val extractedColors = extractVibrantFromBitmap(bitmap)
            if (extractedColors != null) {
                Log.d(TAG, "Extracted vibrant WallpaperColors for custom wallpaper ${item.title}: $extractedColors")
                return extractedColors
            }
            try {
                return WallpaperColors.fromBitmap(bitmap)
            } catch (e: Exception) {
                Log.e(TAG, "Error with WallpaperColors.fromBitmap", e)
            }
        }

        return buildWallpaperColors(
            0xFF1E15F3.toInt(), // Electric Cobalt
            0xFF111F90.toInt(),
            0xFF6CB1F5.toInt()
        )
    }

    /**
     * Curated vivid colors extracted directly from the actual pixels of every bundled
     * super wallpaper so Samsung One UI and Android Material You extract authentic,
     * matching Color Palette swatches.
     */
    @RequiresApi(Build.VERSION_CODES.O_MR1)
    private fun getCuratedColors(wallpaperId: String): WallpaperColors? {
        val (p, s, t) = when (wallpaperId) {
            // Xiaomi 18 Pro
            "xiaomi_18_pro_light01" -> Triple(0xFF1E15F3.toInt(), 0xFF111F90.toInt(), 0xFF6CB1F5.toInt()) // Electric Cobalt
            "xiaomi_18_pro_light02" -> Triple(0xFF00ADAA.toInt(), 0xFF44A4C1.toInt(), 0xFF2A6573.toInt()) // Emerald Teal
            "xiaomi_18_pro_light03" -> Triple(0xFF053599.toInt(), 0xFF272171.toInt(), 0xFF201A4E.toInt()) // Deep Indigo
            "xiaomi_18_pro_light04" -> Triple(0xFFF00001.toInt(), 0xFFBE3246.toInt(), 0xFFFA818C.toInt()) // Crimson Flame
            "xiaomi_18_pro_light05" -> Triple(0xFFF5CB00.toInt(), 0xFFD29030.toInt(), 0xFF642F08.toInt()) // Golden Amber

            // Xiaomi 18 Pro Max
            "xiaomi_18_pro_max_wh"  -> Triple(0xFF0CE3CF.toInt(), 0xFF42B890.toInt(), 0xFF0E4B30.toInt()) // Aquamarine / Mint
            "xiaomi_18_pro_max_bk"  -> Triple(0xFFD16F03.toInt(), 0xFF7D3214.toInt(), 0xFF5C220F.toInt()) // Obsidian Amber
            "xiaomi_18_pro_max_cy"  -> Triple(0xFFBE1509.toInt(), 0xFF91221E.toInt(), 0xFF470809.toInt()) // Cyber Crimson Scarlet
            "xiaomi_18_pro_max_db"  -> Triple(0xFF2953E9.toInt(), 0xFF1D1482.toInt(), 0xFF110E5A.toInt()) // Deep Ocean Blue
            "xiaomi_18_pro_max_pk"  -> Triple(0xFFCD2997.toInt(), 0xFF810155.toInt(), 0xFF540932.toInt()) // Blush Magenta

            // ColorOS
            "coloros_17"            -> Triple(0xFFFE874F.toInt(), 0xFF6A4EB0.toInt(), 0xFFD783B4.toInt()) // Sunset Orange & Violet

            else -> return null
        }

        return buildWallpaperColors(p, s, t)
    }

    /**
     * Builds a WallpaperColors object.
     * Generates a synthetic multi-color bitmap and calls WallpaperColors.fromBitmap(),
     * which populates Android's internal mAllColors map with the proper color populations.
     * This is required by Android 12+ Monet and Samsung One UI's Color Palette engine
     * so that the full range of dynamic palette swatches is generated!
     */
    @RequiresApi(Build.VERSION_CODES.O_MR1)
    private fun buildWallpaperColors(p: Int, s: Int, t: Int): WallpaperColors {
        return try {
            val bmp = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            val paint = Paint()

            // 60% primary, 25% secondary, 15% tertiary to provide proper population weighting
            paint.color = p
            canvas.drawRect(0f, 0f, 100f, 60f, paint)

            paint.color = s
            canvas.drawRect(0f, 60f, 100f, 85f, paint)

            paint.color = t
            canvas.drawRect(0f, 85f, 100f, 100f, paint)

            val colors = WallpaperColors.fromBitmap(bmp)
            bmp.recycle()
            colors
        } catch (_: Exception) {
            val primary = AndroidColor.valueOf(p)
            val secondary = AndroidColor.valueOf(s)
            val tertiary = AndroidColor.valueOf(t)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                try {
                    val constructor = WallpaperColors::class.java.getConstructor(
                        AndroidColor::class.java,
                        AndroidColor::class.java,
                        AndroidColor::class.java,
                        Int::class.javaPrimitiveType
                    )
                    constructor.newInstance(primary, secondary, tertiary, WallpaperColors.HINT_SUPPORTS_DARK_THEME)
                } catch (_: Exception) {
                    WallpaperColors(primary, secondary, tertiary)
                }
            } else {
                WallpaperColors(primary, secondary, tertiary)
            }
        }
    }

    /**
     * Samples a bitmap, ignores dark/black and gray pixels, and extracts the top 3
     * most vibrant saturated colors.
     */
    @RequiresApi(Build.VERSION_CODES.O_MR1)
    private fun extractVibrantFromBitmap(bitmap: Bitmap): WallpaperColors? {
        return try {
            val scaled = Bitmap.createScaledBitmap(bitmap, 64, 64, false)
            val pixels = IntArray(scaled.width * scaled.height)
            scaled.getPixels(pixels, 0, scaled.width, 0, 0, scaled.width, scaled.height)

            val hsv = FloatArray(3)
            val scoredColors = mutableListOf<Pair<Float, Int>>()

            for (pixel in pixels) {
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                AndroidColor.RGBToHSV(r, g, b, hsv)

                val sat = hsv[1]
                val value = hsv[2]

                // Filter out black/near-black (<0.20) and washed-out gray (<0.30)
                if (sat >= 0.35f && value >= 0.25f) {
                    val score = sat * 1.5f + value
                    scoredColors.add(score to pixel)
                }
            }

            if (scoredColors.isNotEmpty()) {
                scoredColors.sortByDescending { it.first }
                val p = scoredColors[0].second
                val s = scoredColors.getOrNull(scoredColors.size / 3)?.second ?: p
                val t = scoredColors.getOrNull((scoredColors.size * 2) / 3)?.second ?: s
                buildWallpaperColors(p, s, t)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in extractVibrantFromBitmap", e)
            null
        }
    }

    fun loadThumbnailBitmap(context: Context, thumbUri: String?): Bitmap? {
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

    /**
     * Writes the static wallpaper frame directly to WallpaperManager.
     * On Samsung One UI, this triggers Samsung's Wallpaper & Style daemon to immediately
     * generate matching Color Palette swatches for the current wallpaper!
     */
    fun syncStaticBitmapForSamsungOneUI(context: Context, wallpaper: WallpaperItem): Boolean {
        return try {
            val bitmap = loadThumbnailBitmap(context, wallpaper.thumbnailUri) ?: return false
            val wm = WallpaperManager.getInstance(context)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                try {
                    wm.setBitmap(bitmap, null, false, WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK)
                } catch (_: Exception) {
                    wm.setBitmap(bitmap, null, false, WallpaperManager.FLAG_SYSTEM)
                }
            } else {
                wm.setBitmap(bitmap)
            }
            Log.d(TAG, "Successfully synced static wallpaper bitmap for Samsung One UI Color Palette")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sync static bitmap for Samsung One UI", e)
            false
        }
    }
}
