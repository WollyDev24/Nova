package com.livewall.app.util

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(
    val currentVersion: String,
    val latestVersion: String,
    val isUpdateAvailable: Boolean,
    val releaseTitle: String,
    val releaseNotes: String,
    val htmlUrl: String,
    val apkDownloadUrl: String?,
    val apkFileName: String?,
    val apkSize: Long = 0L,
    val publishedAt: String = ""
)

sealed class UpdateCheckResult {
    data class Available(val updateInfo: UpdateInfo) : UpdateCheckResult()
    data class UpToDate(val currentVersion: String) : UpdateCheckResult()
    data class Error(val message: String) : UpdateCheckResult()
}

object UpdateManager {

    private const val TAG = "UpdateManager"

    const val GITHUB_REPO_URL = "https://github.com/WollyDev24/Nova"
    const val GITHUB_API_LATEST_RELEASE = "https://api.github.com/repos/WollyDev24/Nova/releases/latest"
    const val GITHUB_RELEASES_URL = "https://github.com/WollyDev24/Nova/releases"

    fun getCurrentVersion(context: Context): String {
        return try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            pInfo.versionName ?: "1.0.0"
        } catch (_: Exception) {
            "1.0.0"
        }
    }

    /**
     * Compares two semantic version strings (e.g. "1.1.0" vs "1.0.0").
     * Returns > 0 if v1 > v2, < 0 if v1 < v2, and 0 if equal.
     */
    fun compareVersions(v1: String, v2: String): Int {
        val clean1 = v1.trim().removePrefix("v").removePrefix("V")
        val clean2 = v2.trim().removePrefix("v").removePrefix("V")

        val parts1 = clean1.split(Regex("[.-]")).mapNotNull { it.toIntOrNull() }
        val parts2 = clean2.split(Regex("[.-]")).mapNotNull { it.toIntOrNull() }

        val maxLen = maxOf(parts1.size, parts2.size)
        for (i in 0 until maxLen) {
            val num1 = parts1.getOrElse(i) { 0 }
            val num2 = parts2.getOrElse(i) { 0 }
            if (num1 != num2) {
                return num1.compareTo(num2)
            }
        }
        return 0
    }

    /**
     * Checks GitHub API for the latest release in WollyDev24/Nova.
     */
    suspend fun checkForUpdates(context: Context): UpdateCheckResult = withContext(Dispatchers.IO) {
        val currentVersion = getCurrentVersion(context)
        try {
            val url = URL(GITHUB_API_LATEST_RELEASE)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8000
                readTimeout = 12000
                setRequestProperty("User-Agent", "Nova-App/$currentVersion")
                setRequestProperty("Accept", "application/vnd.github.v3+json")
            }

            val responseCode = conn.responseCode
            if (responseCode == 404) {
                // No releases published yet on the GitHub repository
                return@withContext UpdateCheckResult.UpToDate(currentVersion)
            }
            if (responseCode !in 200..299) {
                val errorMsg = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                Log.w(TAG, "GitHub API HTTP $responseCode: $errorMsg")
                return@withContext UpdateCheckResult.Error("GitHub responded with status $responseCode")
            }

            val jsonStr = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(jsonStr)

            val tagName = json.optString("tag_name", "").trim()
            val cleanLatest = tagName.removePrefix("v").removePrefix("V").trim()
            val title = json.optString("name", "Nova $tagName").ifBlank { "Nova $tagName" }
            val body = json.optString("body", "").trim()
            val htmlUrl = json.optString("html_url", GITHUB_RELEASES_URL).ifBlank { GITHUB_RELEASES_URL }
            val publishedAt = json.optString("published_at", "")

            var apkUrl: String? = null
            var apkName: String? = null
            var apkSize = 0L

            val assets = json.optJSONArray("assets")
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val name = asset.optString("name", "")
                    val downloadUrl = asset.optString("browser_download_url", "")
                    if (name.endsWith(".apk", ignoreCase = true) && downloadUrl.isNotBlank()) {
                        apkUrl = downloadUrl
                        apkName = name
                        apkSize = asset.optLong("size", 0L)
                        break
                    }
                }
            }

            val isNewer = compareVersions(cleanLatest, currentVersion) > 0
            val info = UpdateInfo(
                currentVersion = currentVersion,
                latestVersion = if (cleanLatest.isNotBlank()) cleanLatest else tagName,
                isUpdateAvailable = isNewer,
                releaseTitle = title,
                releaseNotes = body,
                htmlUrl = htmlUrl,
                apkDownloadUrl = apkUrl,
                apkFileName = apkName,
                apkSize = apkSize,
                publishedAt = publishedAt
            )

            if (isNewer) {
                UpdateCheckResult.Available(info)
            } else {
                UpdateCheckResult.UpToDate(currentVersion)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check for updates", e)
            UpdateCheckResult.Error(e.localizedMessage ?: "Failed to connect to GitHub")
        }
    }

    /**
     * Downloads the APK via DownloadManager and schedules installation.
     */
    fun startDownloadAndInstall(context: Context, updateInfo: UpdateInfo) {
        val apkUrl = updateInfo.apkDownloadUrl
        if (apkUrl.isNullOrBlank()) {
            openInBrowser(context, updateInfo.htmlUrl)
            return
        }

        try {
            val fileName = updateInfo.apkFileName ?: "Nova-${updateInfo.latestVersion}.apk"
            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
            if (downloadManager == null) {
                openInBrowser(context, apkUrl)
                return
            }

            val request = DownloadManager.Request(Uri.parse(apkUrl)).apply {
                setTitle("Nova Update v${updateInfo.latestVersion}")
                setDescription("Downloading latest Nova update...")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                setMimeType("application/vnd.android.package-archive")
            }

            val downloadId = downloadManager.enqueue(request)
            Toast.makeText(context, "Downloading Nova v${updateInfo.latestVersion}...", Toast.LENGTH_SHORT).show()

            // Register completion receiver to prompt APK installation automatically
            val onCompleteReceiver = object : BroadcastReceiver() {
                override fun onReceive(c: Context?, intent: Intent?) {
                    val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L) ?: -1L
                    if (id == downloadId && c != null) {
                        try {
                            c.applicationContext.unregisterReceiver(this)
                        } catch (_: Exception) {}

                        val file = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), fileName)
                        if (file.exists()) {
                            promptInstall(c, file)
                        } else {
                            val uri = downloadManager.getUriForDownloadedFile(downloadId)
                            if (uri != null) {
                                promptInstallUri(c, uri)
                            }
                        }
                    }
                }
            }

            val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.applicationContext.registerReceiver(onCompleteReceiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                context.applicationContext.registerReceiver(onCompleteReceiver, filter)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error starting download, falling back to browser", e)
            openInBrowser(context, apkUrl)
        }
    }

    fun promptInstall(context: Context, file: File) {
        try {
            val contentUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            promptInstallUri(context, contentUri)
        } catch (e: Exception) {
            Log.e(TAG, "Error prompting install via FileProvider", e)
            val fileUri = Uri.fromFile(file)
            promptInstallUri(context, fileUri)
        }
    }

    private fun promptInstallUri(context: Context, uri: Uri) {
        try {
            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            context.startActivity(installIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Error starting install activity", e)
            Toast.makeText(context, "Download complete: check your Downloads folder to install", Toast.LENGTH_LONG).show()
        }
    }

    fun openInBrowser(context: Context, url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(context, "Could not open browser", Toast.LENGTH_SHORT).show()
        }
    }

    fun openGitHubRepo(context: Context) {
        openInBrowser(context, GITHUB_REPO_URL)
    }
}
