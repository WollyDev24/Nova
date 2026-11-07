package com.livewall.app.model

data class WallpaperItem(
    val id: String,
    val title: String,
    val collection: String,
    val videoUri: String,
    val thumbnailUri: String,
    val durationSec: Float,
    val resolution: String,
    val description: String = "",
    val isCustom: Boolean = false
) {
    companion object {
        val BUNDLED_WALLPAPERS = listOf(
            // Xiaomi 18 Pro Max
            WallpaperItem(
                id = "xiaomi_18_pro_max_wh",
                title = "Pure White",
                collection = "Xiaomi 18 Pro Max",
                videoUri = "asset:///wallpapers/Xiaomi-18-Pro-Max/wallpaper_WH.mp4",
                thumbnailUri = "file:///android_asset/thumbnails/Xiaomi-18-Pro-Max/wallpaper_WH.webp",
                durationSec = 2.0f,
                resolution = "1120x2436",
                description = "Clean minimalist glass refraction wallpaper."
            ),
            WallpaperItem(
                id = "xiaomi_18_pro_max_bk",
                title = "Obsidian Black",
                collection = "Xiaomi 18 Pro Max",
                videoUri = "asset:///wallpapers/Xiaomi-18-Pro-Max/wallpaper_BK.mp4",
                thumbnailUri = "file:///android_asset/thumbnails/Xiaomi-18-Pro-Max/wallpaper_BK.webp",
                durationSec = 2.0f,
                resolution = "1120x2436",
                description = "Deep dark luxury obsidian geometric animation."
            ),
            WallpaperItem(
                id = "xiaomi_18_pro_max_cy",
                title = "Cyber Cyan",
                collection = "Xiaomi 18 Pro Max",
                videoUri = "asset:///wallpapers/Xiaomi-18-Pro-Max/wallpaper_CY.mp4",
                thumbnailUri = "file:///android_asset/thumbnails/Xiaomi-18-Pro-Max/wallpaper_CY.webp",
                durationSec = 2.0f,
                resolution = "1120x2436",
                description = "Vibrant electric cyan crystal reveal animation."
            ),
            WallpaperItem(
                id = "xiaomi_18_pro_max_db",
                title = "Deep Ocean Blue",
                collection = "Xiaomi 18 Pro Max",
                videoUri = "asset:///wallpapers/Xiaomi-18-Pro-Max/wallpaper_DB.mp4",
                thumbnailUri = "file:///android_asset/thumbnails/Xiaomi-18-Pro-Max/wallpaper_DB.webp",
                durationSec = 2.0f,
                resolution = "1120x2436",
                description = "Rich sapphire gradient transition."
            ),
            WallpaperItem(
                id = "xiaomi_18_pro_max_pk",
                title = "Blush Pink",
                collection = "Xiaomi 18 Pro Max",
                videoUri = "asset:///wallpapers/Xiaomi-18-Pro-Max/wallpaper_PK.mp4",
                thumbnailUri = "file:///android_asset/thumbnails/Xiaomi-18-Pro-Max/wallpaper_PK.webp",
                durationSec = 2.0f,
                resolution = "1120x2436",
                description = "Soft warm metallic rose petal unlock transition."
            ),

            // Xiaomi 18 Pro
            WallpaperItem(
                id = "xiaomi_18_pro_light01",
                title = "Amber Light",
                collection = "Xiaomi 18 Pro",
                videoUri = "asset:///wallpapers/Xiaomi-18-Pro/Light01.mp4",
                thumbnailUri = "file:///android_asset/thumbnails/Xiaomi-18-Pro/Light01.webp",
                durationSec = 2.0f,
                resolution = "1120x2436",
                description = "Radiant amber light beam expanding on wake."
            ),
            WallpaperItem(
                id = "xiaomi_18_pro_light02",
                title = "Emerald Aurora",
                collection = "Xiaomi 18 Pro",
                videoUri = "asset:///wallpapers/Xiaomi-18-Pro/Light02.mp4",
                thumbnailUri = "file:///android_asset/thumbnails/Xiaomi-18-Pro/Light02.webp",
                durationSec = 2.0f,
                resolution = "1120x2436",
                description = "Flowing emerald green cosmic light."
            ),
            WallpaperItem(
                id = "xiaomi_18_pro_light03",
                title = "Violet Prism",
                collection = "Xiaomi 18 Pro",
                videoUri = "asset:///wallpapers/Xiaomi-18-Pro/Light03.mp4",
                thumbnailUri = "file:///android_asset/thumbnails/Xiaomi-18-Pro/Light03.webp",
                durationSec = 2.0f,
                resolution = "1120x2436",
                description = "Prismatic violet beam with neon refraction."
            ),
            WallpaperItem(
                id = "xiaomi_18_pro_light04",
                title = "Solar Flare",
                collection = "Xiaomi 18 Pro",
                videoUri = "asset:///wallpapers/Xiaomi-18-Pro/Light04.mp4",
                thumbnailUri = "file:///android_asset/thumbnails/Xiaomi-18-Pro/Light04.webp",
                durationSec = 2.0f,
                resolution = "1120x2436",
                description = "Warm golden horizon flare expanding to static."
            ),
            WallpaperItem(
                id = "xiaomi_18_pro_light05",
                title = "Glacier Mist",
                collection = "Xiaomi 18 Pro",
                videoUri = "asset:///wallpapers/Xiaomi-18-Pro/Light05.mp4",
                thumbnailUri = "file:///android_asset/thumbnails/Xiaomi-18-Pro/Light05.webp",
                durationSec = 2.0f,
                resolution = "1120x2436",
                description = "Cool glacial blue light dispersion."
            ),

            // ColorOS
            WallpaperItem(
                id = "coloros_17",
                title = "ColorOS 17 Flow",
                collection = "ColorOS",
                videoUri = "asset:///wallpapers/ColorOS/ColorOS17.mp4",
                thumbnailUri = "file:///android_asset/thumbnails/ColorOS/ColorOS17.webp",
                durationSec = 4.0f,
                resolution = "1080x1080",
                description = "Signature ColorOS fluid silk wave dynamic animation."
            )
        )

        val DEFAULT_WALLPAPER = BUNDLED_WALLPAPERS[0]
    }
}
