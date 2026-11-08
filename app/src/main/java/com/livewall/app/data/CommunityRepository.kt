package com.livewall.app.data

import android.content.Context
import android.net.Uri
import android.util.Base64
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
import org.json.JSONObject
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

class CommunityRepository(
    private val context: Context,
    val authManager: CommunityAuthManager = CommunityAuthManager(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val storage: FirebaseStorage = FirebaseStorage.getInstance()
) {

    private val collection = firestore.collection("community_wallpapers")
    private val usersCollection = firestore.collection("users")

    /**
     * Fetches trending wallpapers ordered by likes (only approved content).
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
            val approved = list.filter { it.isApproved }
            Result.success(approved)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Fetches newest community wallpapers (only approved content).
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
            val approved = list.filter { it.isApproved }
            Result.success(approved)
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
     * Uploads an MP4 video to free direct media hosting (Cloudflare R2 via tmpfile / Litterbox fallback)
     * and embeds the lightweight JPEG thumbnail directly as a Base64 data URI in Firestore.
     * Completely free: requires zero paid plans, zero credit cards, and zero Firebase Storage billing.
     * Thumbnails load instantly with 0 latency and 0 external network requests!
     */
    suspend fun uploadWallpaper(
        title: String,
        videoUri: Uri,
        thumbnailBytes: ByteArray,
        primaryHex: String,
        secondaryHex: String,
        tertiaryHex: String,
        durationSec: Float,
        authorName: String = "",
        tags: List<String> = emptyList()
    ): Result<CommunityWallpaper> = withContext(Dispatchers.IO) {
        try {
            val user = authManager.ensureAuthenticated().getOrThrow()
            val uid = user.uid
            val resolvedAuthorName = authorName.trim().ifEmpty { authManager.displayName }
            val newId = UUID.randomUUID().toString().substring(0, 12)

            // 1. Upload Video to Free Direct Media Host (Cloudflare R2 backed, with fallback)
            val videoDownloadUrl = uploadVideoStream(videoUri, "$newId.mp4")

            // 2. Encode thumbnail as Base64 data URI directly in Firestore document
            // A 360x640 JPEG is ~10-18 KB, easily fitting within Firestore's 1 MB document limit.
            // This eliminates broken 0-byte images, image hosting rate limits, and external CDN downtime!
            val thumbDataUri = "data:image/jpeg;base64," + Base64.encodeToString(thumbnailBytes, Base64.NO_WRAP)

            // 3. Write Firestore record (100% free Spark plan)
            val initialStatus = if (authManager.isCurrentUserAdmin(context)) {
                CommunityWallpaper.STATUS_APPROVED
            } else {
                CommunityWallpaper.STATUS_PENDING
            }

            val communityWallpaper = CommunityWallpaper(
                id = newId,
                title = title.trim(),
                authorId = uid,
                authorName = resolvedAuthorName,
                videoUrl = videoDownloadUrl,
                thumbnailUrl = thumbDataUri,
                primaryColorHex = primaryHex,
                secondaryColorHex = secondaryHex,
                tertiaryColorHex = tertiaryHex,
                durationSec = durationSec,
                fileSizeBytes = thumbnailBytes.size.toLong(),
                downloadsCount = 0L,
                likesCount = 0L,
                tags = tags,
                status = initialStatus
            )

            collection.document(newId).set(communityWallpaper).await()
            Result.success(communityWallpaper)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Deletes a community wallpaper if the requesting user is the original creator or an administrator.
     */
    suspend fun deleteWallpaper(wallpaperId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val user = authManager.ensureAuthenticated().getOrThrow()
            val docRef = collection.document(wallpaperId)
            val doc = docRef.get().await()
            val authorId = doc.getString("authorId")
            if (authorId != user.uid && !authManager.isCurrentUserAdmin(context)) {
                return@withContext Result.failure(IllegalStateException("You can only delete wallpapers you published."))
            }
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Moderation Console: Fetches all community submissions across all statuses.
     */
    suspend fun getAllWallpapersForModeration(limit: Long = 100): Result<List<CommunityWallpaper>> = withContext(Dispatchers.IO) {
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
     * Moderation Console: Approves a pending wallpaper so it appears live in the community feed.
     */
    suspend fun approveWallpaper(wallpaperId: String, reviewerName: String = "Admin"): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            authManager.ensureAuthenticated()
            collection.document(wallpaperId).update(
                mapOf(
                    "status" to CommunityWallpaper.STATUS_APPROVED,
                    "reviewedBy" to reviewerName,
                    "reviewedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Moderation Console: Rejects a wallpaper with an optional review explanation.
     */
    suspend fun rejectWallpaper(
        wallpaperId: String,
        reason: String,
        reviewerName: String = "Admin"
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            authManager.ensureAuthenticated()
            collection.document(wallpaperId).update(
                mapOf(
                    "status" to CommunityWallpaper.STATUS_REJECTED,
                    "reviewNote" to reason.trim(),
                    "reviewedBy" to reviewerName,
                    "reviewedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Moderation Console: Force-deletes any community wallpaper as an administrator.
     */
    suspend fun adminDeleteWallpaper(wallpaperId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            authManager.ensureAuthenticated()
            collection.document(wallpaperId).delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Real-time flow of the count of pending wallpaper submissions.
     */
    fun getPendingCountFlow(): Flow<Int> = callbackFlow {
        val registration = collection
            .whereEqualTo("status", CommunityWallpaper.STATUS_PENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(0)
                    return@addSnapshotListener
                }
                trySend(snapshot?.size() ?: 0)
            }
        awaitClose { registration.remove() }
    }

    /**
     * Uploads the video stream with primary host (tmpfile.link / Cloudflare R2) and
     * fallbacks to Litterbox (catbox.moe).
     */
    private fun uploadVideoStream(uri: Uri, filename: String): String {
        return try {
            uploadStreamToTmpFile(uri, filename)
        } catch (e: Exception) {
            try {
                uploadStreamToLitterbox(uri, filename)
            } catch (fallbackErr: Exception) {
                throw IOException("All free media upload hosts failed: ${e.message} / ${fallbackErr.message}")
            }
        }
    }

    private fun uploadStreamToTmpFile(uri: Uri, filename: String): String {
        val fileSize = context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize }
            ?: context.contentResolver.openInputStream(uri)?.use { it.available().toLong() }
            ?: throw IOException("Could not determine video file size")

        val boundary = "----WebKitFormBoundary" + UUID.randomUUID().toString().replace("-", "").take(16)
        val header = (
            "--$boundary\r\n" +
            "Content-Disposition: form-data; name=\"file\"; filename=\"$filename\"\r\n" +
            "Content-Type: video/mp4\r\n\r\n"
        ).toByteArray(Charsets.UTF_8)
        val footer = "\r\n--$boundary--\r\n".toByteArray(Charsets.UTF_8)
        val totalLength = header.size + fileSize + footer.size

        val url = URL("https://tmpfile.link/api/upload")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            doInput = true
            doOutput = true
            useCaches = false
            requestMethod = "POST"
            setRequestProperty("Connection", "Keep-Alive")
            setRequestProperty("User-Agent", "NovaApp/2.0")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            setFixedLengthStreamingMode(totalLength)
            connectTimeout = 30_000
            readTimeout = 90_000
        }

        connection.outputStream.use { os ->
            os.write(header)
            context.contentResolver.openInputStream(uri)?.use { input ->
                input.copyTo(os)
            } ?: throw IOException("Could not open video file input stream")
            os.write(footer)
            os.flush()
        }

        val code = connection.responseCode
        val rawResponse = if (code in 200..299) {
            connection.inputStream.bufferedReader().use { it.readText() }.trim()
        } else {
            val err = connection.errorStream?.bufferedReader()?.use { it.readText() }?.trim() ?: "HTTP $code"
            throw IOException("tmpfile upload failed ($code): $err")
        }

        val json = JSONObject(rawResponse)
        val downloadLink = json.optString("downloadLink")
        if (downloadLink.isNotBlank() && (downloadLink.startsWith("http://") || downloadLink.startsWith("https://"))) {
            return downloadLink
        } else {
            throw IOException("Invalid downloadLink in tmpfile response: $rawResponse")
        }
    }

    private fun uploadStreamToLitterbox(uri: Uri, filename: String): String {
        val fileSize = context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize }
            ?: context.contentResolver.openInputStream(uri)?.use { it.available().toLong() }
            ?: throw IOException("Could not determine video file size")

        val boundary = "------------------------" + UUID.randomUUID().toString().replace("-", "").take(24)
        val header = (
            "--$boundary\r\n" +
            "Content-Disposition: form-data; name=\"reqtype\"\r\n\r\n" +
            "fileupload\r\n" +
            "--$boundary\r\n" +
            "Content-Disposition: form-data; name=\"time\"\r\n\r\n" +
            "72h\r\n" +
            "--$boundary\r\n" +
            "Content-Disposition: form-data; name=\"fileToUpload\"; filename=\"$filename\"\r\n" +
            "Content-Type: video/mp4\r\n\r\n"
        ).toByteArray(Charsets.UTF_8)
        val footer = "\r\n--$boundary--\r\n".toByteArray(Charsets.UTF_8)
        val totalLength = header.size + fileSize + footer.size

        val url = URL("https://litterbox.catbox.moe/resources/internals/api.php")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            doInput = true
            doOutput = true
            useCaches = false
            requestMethod = "POST"
            setRequestProperty("Connection", "Keep-Alive")
            setRequestProperty("User-Agent", "curl/8.23.0")
            setRequestProperty("Accept", "*/*")
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            setFixedLengthStreamingMode(totalLength)
            connectTimeout = 30_000
            readTimeout = 90_000
        }

        connection.outputStream.use { os ->
            os.write(header)
            context.contentResolver.openInputStream(uri)?.use { input ->
                input.copyTo(os)
            } ?: throw IOException("Could not open video file input stream")
            os.write(footer)
            os.flush()
        }

        val code = connection.responseCode
        val rawResponse = if (code in 200..299) {
            connection.inputStream.bufferedReader().use { it.readText() }.trim()
        } else {
            val err = connection.errorStream?.bufferedReader()?.use { it.readText() }?.trim() ?: "HTTP $code"
            throw IOException("Litterbox upload error ($code): $err")
        }

        if (code in 200..299 && (rawResponse.startsWith("http://") || rawResponse.startsWith("https://"))) {
            return rawResponse
        } else {
            throw IOException("Litterbox response error ($code): $rawResponse")
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
