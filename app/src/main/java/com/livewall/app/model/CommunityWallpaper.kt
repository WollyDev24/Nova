package com.livewall.app.model

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

/**
 * Cloud model representing a community-submitted live wallpaper in Firestore.
 */
data class CommunityWallpaper(
    @DocumentId
    val id: String = "",
    val title: String = "",
    val authorId: String = "",
    val authorName: String = "Nova Creator",
    val videoUrl: String = "",
    val thumbnailUrl: String = "",
    val primaryColorHex: String = "#1E88E5",
    val secondaryColorHex: String = "#90CAF9",
    val tertiaryColorHex: String = "#42A5F5",
    val durationSec: Float = 3.0f,
    val fileSizeBytes: Long = 0L,
    val downloadsCount: Long = 0L,
    val likesCount: Long = 0L,
    val tags: List<String> = emptyList(),
    @ServerTimestamp
    val createdAt: Date? = null
) {
    /**
     * Converts to internal [WallpaperItem] for previewing & setting as active live wallpaper.
     */
    fun toWallpaperItem(localCachedUri: String? = null): WallpaperItem {
        return WallpaperItem(
            id = "community_$id",
            title = title,
            collection = "Community",
            videoUri = localCachedUri ?: videoUrl,
            thumbnailUri = thumbnailUrl,
            durationSec = durationSec,
            resolution = "1080x2400",
            description = "By $authorName • $likesCount likes",
            isCustom = true
        )
    }
}
