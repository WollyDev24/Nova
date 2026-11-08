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
    val status: String = STATUS_APPROVED,
    val reviewNote: String = "",
    val reviewedBy: String = "",
    val reviewedAt: Date? = null,
    @ServerTimestamp
    val createdAt: Date? = null
) {
    val isApproved: Boolean
        get() = status.isBlank() || status.equals(STATUS_APPROVED, ignoreCase = true)

    val isPending: Boolean
        get() = status.equals(STATUS_PENDING, ignoreCase = true)

    val isRejected: Boolean
        get() = status.equals(STATUS_REJECTED, ignoreCase = true)

    companion object {
        const val STATUS_PENDING = "PENDING"
        const val STATUS_APPROVED = "APPROVED"
        const val STATUS_REJECTED = "REJECTED"
    }
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
