package com.livewall.app.data

import android.content.Context
import android.net.Uri
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import com.livewall.app.model.CommunityWallpaper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.util.UUID

class CommunityRepository(
    private val context: Context,
    private val authManager: CommunityAuthManager = CommunityAuthManager(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val storage: FirebaseStorage = FirebaseStorage.getInstance()
) {

    private val collection = firestore.collection("community_wallpapers")
    private val usersCollection = firestore.collection("users")

    /**
     * Fetches trending wallpapers ordered by likes.
     */
    suspend fun getTrendingWallpapers(limit: Long = 30): Result<List<CommunityWallpaper>> = withContext(Dispatchers.IO) {
        try {
            authManager.ensureAuthenticated()
            val snapshot = collection
                .orderBy("likesCount", Query.Direction.DESCENDING)
                .limit(limit)
                .get()
                .await()
            val list = snapshot.toObjects(CommunityWallpaper::class.java)
            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Fetches newest community wallpapers.
     */
    suspend fun getRecentWallpapers(limit: Long = 30): Result<List<CommunityWallpaper>> = withContext(Dispatchers.IO) {
        try {
            authManager.ensureAuthenticated()
            val snapshot = collection
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(limit)
                .get()
                .await()
            val list = snapshot.toObjects(CommunityWallpaper::class.java)
            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Real-time flow observing whether the current user has liked [wallpaperId].
     */
    fun isWallpaperLikedFlow(wallpaperId: String): Flow<Boolean> = callbackFlow {
        val uid = authManager.currentUserId
        if (uid == null) {
            trySend(false)
            close()
            return@callbackFlow
        }

        val docRef = usersCollection.document(uid).collection("liked").document(wallpaperId)
        val registration = docRef.addSnapshotListener { snapshot, _ ->
            trySend(snapshot?.exists() == true)
        }

        awaitClose { registration.remove() }
    }

    /**
     * Toggles the like status of a wallpaper in an atomic Firestore transaction.
     * Returns true if now liked, false if unliked.
     */
    suspend fun toggleLike(wallpaperId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val user = authManager.ensureAuthenticated().getOrThrow()
            val uid = user.uid

            val wallpaperRef = collection.document(wallpaperId)
            val likeRef = usersCollection.document(uid).collection("liked").document(wallpaperId)

            val nowLiked = firestore.runTransaction { tx ->
                val likeDoc = tx.get(likeRef)
                if (likeDoc.exists()) {
                    // Unlike
                    tx.delete(likeRef)
                    tx.update(wallpaperRef, "likesCount", FieldValue.increment(-1))
                    false
                } else {
                    // Like
                    tx.set(likeRef, mapOf("likedAt" to FieldValue.serverTimestamp()))
                    tx.update(wallpaperRef, "likesCount", FieldValue.increment(1))
                    true
                }
            }.await()

            Result.success(nowLiked)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Increments download counter for a wallpaper.
     */
    suspend fun recordDownload(wallpaperId: String) = withContext(Dispatchers.IO) {
        try {
            collection.document(wallpaperId).update("downloadsCount", FieldValue.increment(1))
        } catch (_: Exception) {
            // Non-critical metric
        }
    }

    /**
     * Uploads an MP4 video & WebP thumbnail to Firebase Storage and publishes metadata to Firestore.
     */
    suspend fun uploadWallpaper(
        title: String,
        videoUri: Uri,
        thumbnailBytes: ByteArray,
        primaryHex: String,
        secondaryHex: String,
        tertiaryHex: String,
        durationSec: Float,
        tags: List<String> = emptyList()
    ): Result<CommunityWallpaper> = withContext(Dispatchers.IO) {
        try {
            val user = authManager.ensureAuthenticated().getOrThrow()
            val uid = user.uid
            val authorName = user.displayName?.takeIf { it.isNotBlank() } ?: "Creator"

            val newId = UUID.randomUUID().toString().substring(0, 12)
            val wallpaperStorageRef = storage.reference.child("community_wallpapers/$newId")

            // 1. Upload Video
            val videoRef = wallpaperStorageRef.child("video.mp4")
            val videoMetadata = StorageMetadata.Builder()
                .setContentType("video/mp4")
                .setCustomMetadata("authorId", uid)
                .build()

            videoRef.putFile(videoUri, videoMetadata).await()
            val videoDownloadUrl = videoRef.downloadUrl.await().toString()

            // 2. Upload Thumbnail
            val thumbRef = wallpaperStorageRef.child("thumbnail.webp")
            val thumbMetadata = StorageMetadata.Builder()
                .setContentType("image/webp")
                .build()

            thumbRef.putBytes(thumbnailBytes, thumbMetadata).await()
            val thumbDownloadUrl = thumbRef.downloadUrl.await().toString()

            // 3. Write Firestore record
            val communityWallpaper = CommunityWallpaper(
                id = newId,
                title = title.trim(),
                authorId = uid,
                authorName = authorName,
                videoUrl = videoDownloadUrl,
                thumbnailUrl = thumbDownloadUrl,
                primaryColorHex = primaryHex,
                secondaryColorHex = secondaryHex,
                tertiaryColorHex = tertiaryHex,
                durationSec = durationSec,
                fileSizeBytes = thumbnailBytes.size.toLong(),
                downloadsCount = 0L,
                likesCount = 0L,
                tags = tags
            )

            collection.document(newId).set(communityWallpaper).await()
            Result.success(communityWallpaper)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Downloads a community video file to local app storage so ExoPlayer and
     * the Live Wallpaper engine can play it offline with zero latency.
     */
    suspend fun downloadWallpaperToLocal(wallpaper: CommunityWallpaper): Result<File> = withContext(Dispatchers.IO) {
        try {
            val communityDir = File(context.filesDir, "community_videos").apply { mkdirs() }
            val destinationFile = File(communityDir, "${wallpaper.id}.mp4")

            if (destinationFile.exists() && destinationFile.length() > 0) {
                recordDownload(wallpaper.id)
                return@withContext Result.success(destinationFile)
            }

            val connection = URL(wallpaper.videoUrl).openConnection()
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000

            connection.getInputStream().use { input ->
                FileOutputStream(destinationFile).use { output ->
                    input.copyTo(output)
                }
            }

            recordDownload(wallpaper.id)
            Result.success(destinationFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
