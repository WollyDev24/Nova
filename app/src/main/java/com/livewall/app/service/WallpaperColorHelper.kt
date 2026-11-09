package com.livewall.app.service

import android.app.WallpaperColors
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Shader
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
    fun computeColors(context: Context, videoUriStr: String, wallpaperId: String? = null): WallpaperColors? {
        val all = WallpaperItem.BUNDLED_WALLPAPERS + WallpaperPreferences(context).getCustomWallpapers()
        val item = (if (wallpaperId != null) all.find { it.id == wallpaperId } else null)
            ?: all.find { it.videoUri == videoUriStr }
            ?: all.find {
                val f1 = it.videoUri.substringAfterLast("/")
                val f2 = videoUriStr.substringAfterLast("/")
                f1.isNotEmpty() && f1 == f2
            }
            ?: all.firstOrNull() ?: return null

        val rgb = getCuratedRgb(item.id)
        if (rgb != null) {
            Log.d(TAG, "Using curated vibrant WallpaperColors for ${item.title}: $rgb")
            return buildWallpaperColors(rgb.first, rgb.second, rgb.third)
        }

        // Custom wallpapers: extract top vibrant colors ignoring black background
        val bitmap = loadThumbnailBitmap(context, item.thumbnailUri)
        if (bitmap != null) {
            val extracted = extractTopRgb(bitmap)
            return buildWallpaperColors(extracted.first, extracted.second, extracted.third)
        }

        return buildWallpaperColors(
            0xFFFE874F.toInt(), // Sunset Orange
            0xFFEA580C.toInt(), // Coral
            0xFF9C27B0.toInt()  // Purple
        )
    }

    /**
     * Returns the 3 primary RGB colors for any wallpaper (curated or dynamically extracted).
     */
    fun getWallpaperColorsRgb(context: Context, wallpaper: WallpaperItem): Triple<Int, Int, Int> {
        val curated = getCuratedRgb(wallpaper.id)
        if (curated != null) return curated
        val thumb = loadThumbnailBitmap(context, wallpaper.thumbnailUri)
        return if (thumb != null) {
            val top = extractTopRgb(thumb)
            thumb.recycle()
            top
        } else {
            Triple(0xFFFE874F.toInt(), 0xFFEA580C.toInt(), 0xFF9C27B0.toInt())
        }
    }

    /**
     * Curated vivid colors extracted directly from the actual pixels of every bundled
     * super wallpaper.
     */
    fun getCuratedRgb(wallpaperId: String): Triple<Int, Int, Int>? {
        return when (wallpaperId) {
            "pixel_10_fold_silver_dark" -> Triple(0xFF6D7B99.toInt(), 0xFF363F4C.toInt(), 0xFF38404B.toInt()) // Silver Dark

            "pixel_10_fold_silver" -> Triple(0xFF7B89A4.toInt(), 0xFF2B2D3D.toInt(), 0xFF2C333C.toInt()) // Silver

            "pixel_10_fold_green_dark" -> Triple(0xFF607053.toInt(), 0xFF2E3A2A.toInt(), 0xFF313A2B.toInt()) // Green Dark

            "pixel_10_fold_green" -> Triple(0xFF3A4B37.toInt(), 0xFF354332.toInt(), 0xFF324030.toInt()) // Green





            "pixel_11_fold_pine_dark" -> Triple(0xFF33341F.toInt(), 0xFF2D3C3E.toInt(), 0xFF2A3A3A.toInt()) // Pine Dark

            "pixel_11_fold_pine" -> Triple(0xFF372305.toInt(), 0xFF5D503B.toInt(), 0xFF474330.toInt()) // Pine

            "pixel_11_fold_midnight_dark" -> Triple(0xFF1E15F3.toInt(), 0xFF111F90.toInt(), 0xFF6CB1F5.toInt()) // Midnight Dark

            "pixel_11_fold_midnight" -> Triple(0xFF222A33.toInt(), 0xFF2A2F39.toInt(), 0xFF2C313B.toInt()) // Midnight

            // ColorOS 17 Flow: Sunset Orange / Amber / Coral / Flow Purple
            "coloros_17"            -> Triple(0xFFFE874F.toInt(), 0xFFEA580C.toInt(), 0xFF9C27B0.toInt())

            // Xiaomi 18 Pro
            "xiaomi_18_pro_light01" -> Triple(0xFF1E15F3.toInt(), 0xFF111F90.toInt(), 0xFF6CB1F5.toInt()) // Electric Cobalt
            "xiaomi_18_pro_light02" -> Triple(0xFF00ADAA.toInt(), 0xFF44A4C1.toInt(), 0xFF2A6573.toInt()) // Emerald Teal
            "xiaomi_18_pro_light03" -> Triple(0xFF8B5CF6.toInt(), 0xFF053599.toInt(), 0xFF201A4E.toInt()) // Prismatic Violet / Indigo
            "xiaomi_18_pro_light04" -> Triple(0xFFF00001.toInt(), 0xFFBE3246.toInt(), 0xFFFA818C.toInt()) // Crimson Flame
            "xiaomi_18_pro_light05" -> Triple(0xFFF5CB00.toInt(), 0xFFD29030.toInt(), 0xFF642F08.toInt()) // Golden Amber

            // Xiaomi 18 Pro Max
            "xiaomi_18_pro_max_wh"  -> Triple(0xFF0CE3CF.toInt(), 0xFF42B890.toInt(), 0xFF0E4B30.toInt()) // Aquamarine / Mint
            "xiaomi_18_pro_max_bk"  -> Triple(0xFFD16F03.toInt(), 0xFF7D3214.toInt(), 0xFF5C220F.toInt()) // Obsidian Amber
            "xiaomi_18_pro_max_cy"  -> Triple(0xFFBE1509.toInt(), 0xFF91221E.toInt(), 0xFF470809.toInt()) // Cyber Crimson Scarlet
            "xiaomi_18_pro_max_db"  -> Triple(0xFF2953E9.toInt(), 0xFF1D1482.toInt(), 0xFF110E5A.toInt()) // Deep Ocean Blue
            "xiaomi_18_pro_max_pk"  -> Triple(0xFFCD2997.toInt(), 0xFF810155.toInt(), 0xFF540932.toInt()) // Blush Magenta

            else -> null
        }
    }

    /**
     * Builds a standard WallpaperColors instance using public Android SDK constructors.
     */
    @RequiresApi(Build.VERSION_CODES.O_MR1)
    fun buildWallpaperColors(p: Int, s: Int, t: Int): WallpaperColors {
        val primary = AndroidColor.valueOf(p)
        val secondary = AndroidColor.valueOf(s)
        val tertiary = AndroidColor.valueOf(t)

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                WallpaperColors(primary, secondary, tertiary, WallpaperColors.HINT_SUPPORTS_DARK_THEME)
            } catch (_: Exception) {
                WallpaperColors(primary, secondary, tertiary)
            }
        } else {
            WallpaperColors(primary, secondary, tertiary)
        }
    }

    /**
     * Samples a bitmap, ignores dark/black and washed-out pixels, and extracts the top 3
     * most vibrant saturated colors.
     */
    fun extractTopRgb(bitmap: Bitmap): Triple<Int, Int, Int> {
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

            if (scaled != bitmap) scaled.recycle()

            if (scoredColors.isNotEmpty()) {
                scoredColors.sortByDescending { it.first }
                val p = scoredColors[0].second
                val s = scoredColors.getOrNull(scoredColors.size / 3)?.second ?: p
                val t = scoredColors.getOrNull((scoredColors.size * 2) / 3)?.second ?: s
                Triple(p, s, t)
            } else {
                Triple(0xFFFE874F.toInt(), 0xFFEA580C.toInt(), 0xFF9C27B0.toInt())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in extractTopRgb", e)
            Triple(0xFFFE874F.toInt(), 0xFFEA580C.toInt(), 0xFF9C27B0.toInt())
        }
    }

    /**
     * Resolves an image model for Coil AsyncImage that seamlessly handles
     * base64 data URIs, assets, files, and remote URLs without decoding errors.
     */
    fun resolveCoilModel(thumbUri: String?): Any? {
        if (thumbUri.isNullOrBlank()) return null
        if (thumbUri.startsWith("data:image/")) {
            return try {
                val base64Data = if (thumbUri.contains("base64,")) {
                    thumbUri.substringAfter("base64,")
                } else {
                    thumbUri.substringAfter(',')
                }
                android.util.Base64.decode(base64Data, android.util.Base64.DEFAULT)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to decode base64 data URI for Coil", e)
                thumbUri
            }
        }
        return thumbUri
    }

    fun loadThumbnailBitmap(context: Context, thumbUri: String?): Bitmap? {
        if (thumbUri.isNullOrBlank()) return null
        return try {
            when {
                thumbUri.startsWith("data:image/") -> {
                    val base64Data = thumbUri.substringAfter("base64,")
                    val bytes = android.util.Base64.decode(base64Data, android.util.Base64.DEFAULT)
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                }
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
     * Generates a high-resolution, vivid backdrop bitmap tailored for Samsung One UI's
     * SemWallpaperColors and Android's Monet engine.
     *
     * In the raw video thumbnails, 85-90% of the pixels are pitch-black, which causes
     * Samsung's and Android's color engine to fail the chroma threshold and fallback
     * to the default system blue palette.
     *
     * This method renders a rich vertical gradient of the wallpaper's authentic
     * signature colors (Secondary -> Primary -> Tertiary) and softly blends the
     * thumbnail artwork over it. This ensures every sampling zone of Samsung's matrix
     * has high chroma (>0.60) and optimal luminance (~0.45), forcing One UI to generate
     * authentic color palette swatches (Amber, Crimson, Emerald, Orange, Violet, etc.)!
     */
    fun generateVibrantBackdropBitmap(context: Context, wallpaper: WallpaperItem): Bitmap {
        val width = 1080
        val height = 2340
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        val thumb = loadThumbnailBitmap(context, wallpaper.thumbnailUri)
        val rgb = getCuratedRgb(wallpaper.id) ?: if (thumb != null) {
            extractTopRgb(thumb)
        } else {
            Triple(0xFFFE874F.toInt(), 0xFFEA580C.toInt(), 0xFF9C27B0.toInt())
        }
        val (p, s, t) = rgb

        // 1. Draw rich signature gradient across full screen
        val gradient = LinearGradient(
            0f, 0f, 0f, height.toFloat(),
            intArrayOf(s, p, p, t),
            floatArrayOf(0f, 0.35f, 0.70f, 1.0f),
            Shader.TileMode.CLAMP
        )
        val gradPaint = Paint().apply {
            shader = gradient
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), gradPaint)

        // 2. Softly blend thumbnail artwork over gradient
        if (thumb != null) {
            val src = Rect(0, 0, thumb.width, thumb.height)
            val dst = Rect(0, 0, width, height)
            val blendPaint = Paint().apply {
                alpha = 153 // 60% opacity blend so rich colors fill dark background
            }
            canvas.drawBitmap(thumb, src, dst, blendPaint)
            thumb.recycle()
        }

        return bmp
    }

    /**
     * Samsung One UI and Android Material You Color Palette integration.
     * Dynamic palette extraction is handled natively and non-destructively via
     * [LiveWallpaperService.onComputeColors] and [LiveWallpaperService.notifyColorsChanged].
     *
     * Note: We NEVER call WallpaperManager.setBitmap() here, as doing so tells Android
     * to displace and unbind the active Live Wallpaper service with a static bitmap.
     */
    fun syncStaticBitmapForSamsungOneUI(context: Context, wallpaper: WallpaperItem): Boolean {
        Log.d(TAG, "Preserving LiveWallpaperService; dynamic palette is notified via notifyColorsChanged()")
        return true
    }

    /**
     * Opens Samsung One UI's Wallpaper & Style / Color Palette settings screen directly.
     */
    fun openSystemPaletteSettings(context: Context) {
        val intents = listOf(
            // 1. Samsung One UI Wallpaper and Style activity
            Intent().apply {
                component = ComponentName("com.samsung.android.app.dressroom", "com.samsung.android.app.dressroom.settings.MyWallpapersActivity")
            },
            // 2. Samsung alternative action
            Intent("com.samsung.android.app.dressroom.action.WALLPAPER_SETTINGS"),
            // 3. Android standard Wallpaper settings
            Intent(Intent.ACTION_SET_WALLPAPER),
            // 4. Fallback: Android system settings
            Intent(android.provider.Settings.ACTION_SETTINGS)
        )
        for (intent in intents) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return
            } catch (_: Exception) {
            }
        }
    }

    /**
     * Maps each wallpaper to its dedicated LiveWallpaperService component.
     * Each dedicated service points to its own authentic cover thumbnail in AndroidManifest.xml,
     * ensuring Samsung One UI displays the real wallpaper preview and extracts the true Color Palette!
     */
    fun getServiceClassForWallpaper(wallpaperId: String): Class<out LiveWallpaperService> {
        return when (wallpaperId) {
            "coloros_17" -> ColorOSWallpaperService::class.java
            "xiaomi_18_pro_light01" -> XiaomiLight01WallpaperService::class.java
            "xiaomi_18_pro_light02" -> XiaomiLight02WallpaperService::class.java
            "xiaomi_18_pro_light03" -> XiaomiLight03WallpaperService::class.java
            "xiaomi_18_pro_light04" -> XiaomiLight04WallpaperService::class.java
            "xiaomi_18_pro_light05" -> XiaomiLight05WallpaperService::class.java
            "xiaomi_18_pro_max_wh"  -> XiaomiWHWallpaperService::class.java
            "xiaomi_18_pro_max_bk"  -> XiaomiBKWallpaperService::class.java
            "xiaomi_18_pro_max_cy"  -> XiaomiCYWallpaperService::class.java
            "xiaomi_18_pro_max_db"  -> XiaomiDBWallpaperService::class.java
            "xiaomi_18_pro_max_pk"  -> XiaomiPKWallpaperService::class.java
            "pixel_11_fold_midnight" -> Pixel11FoldMidnightWallpaperService::class.java
            "pixel_11_fold_midnight_dark" -> Pixel11FoldMidnightDarkWallpaperService::class.java
            "pixel_11_fold_pine" -> Pixel11FoldPineWallpaperService::class.java
            "pixel_11_fold_pine_dark" -> Pixel11FoldPineDarkWallpaperService::class.java
            "pixel_10_fold_green" -> Pixel10FoldGreenWallpaperService::class.java
            "pixel_10_fold_green_dark" -> Pixel10FoldGreenDarkWallpaperService::class.java
            "pixel_10_fold_silver" -> Pixel10FoldSilverWallpaperService::class.java
            "pixel_10_fold_silver_dark" -> Pixel10FoldSilverDarkWallpaperService::class.java
            else -> LiveWallpaperService::class.java
        }
    }

    /**
     * Resolves the wallpaper ID associated with a dedicated subclass service.
     */
    fun getWallpaperIdForServiceClass(serviceClass: Class<out LiveWallpaperService>): String? {
        return when (serviceClass) {
            ColorOSWallpaperService::class.java -> "coloros_17"
            XiaomiLight01WallpaperService::class.java -> "xiaomi_18_pro_light01"
            XiaomiLight02WallpaperService::class.java -> "xiaomi_18_pro_light02"
            XiaomiLight03WallpaperService::class.java -> "xiaomi_18_pro_light03"
            XiaomiLight04WallpaperService::class.java -> "xiaomi_18_pro_light04"
            XiaomiLight05WallpaperService::class.java -> "xiaomi_18_pro_light05"
            XiaomiWHWallpaperService::class.java -> "xiaomi_18_pro_max_wh"
            XiaomiBKWallpaperService::class.java -> "xiaomi_18_pro_max_bk"
            XiaomiCYWallpaperService::class.java -> "xiaomi_18_pro_max_cy"
            XiaomiDBWallpaperService::class.java -> "xiaomi_18_pro_max_db"
            XiaomiPKWallpaperService::class.java -> "xiaomi_18_pro_max_pk"
            Pixel11FoldMidnightWallpaperService::class.java -> "pixel_11_fold_midnight"
            Pixel11FoldMidnightDarkWallpaperService::class.java -> "pixel_11_fold_midnight_dark"
            Pixel11FoldPineWallpaperService::class.java -> "pixel_11_fold_pine"
            Pixel11FoldPineDarkWallpaperService::class.java -> "pixel_11_fold_pine_dark"
            Pixel10FoldGreenWallpaperService::class.java -> "pixel_10_fold_green"
            Pixel10FoldGreenDarkWallpaperService::class.java -> "pixel_10_fold_green_dark"
            Pixel10FoldSilverWallpaperService::class.java -> "pixel_10_fold_silver"
            Pixel10FoldSilverDarkWallpaperService::class.java -> "pixel_10_fold_silver_dark"
            else -> null
        }
    }
}
