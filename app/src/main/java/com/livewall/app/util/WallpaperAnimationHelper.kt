package com.livewall.app.util

import android.content.Context
import android.util.Log
import com.livewall.app.model.WallpaperAnimationCurve
import java.io.File

object WallpaperAnimationHelper {
    private const val TAG = "WallpaperAnimHelper"
    private val cachedCurves = mutableMapOf<String, WallpaperAnimationCurve?>()

    /**
     * Resolves the animation curve for a given wallpaper ID and/or playable URI.
     * Searches:
     * 1. Memory cache
     * 2. Assets under `curves/{wallpaperId}.json` or mapped known assets
     * 3. Filesystem directory containing the wallpaper (e.g. `animation_curve.json` or `{name}_curve.json`)
     */
    fun getCurveForWallpaper(context: Context, wallpaperId: String?, videoUriStr: String? = null): WallpaperAnimationCurve? {
        val key = wallpaperId ?: videoUriStr ?: return null
        if (cachedCurves.containsKey(key)) {
            return cachedCurves[key]
        }

        // 1. Check bundled assets in curves/
        val assetNameCandidates = mutableListOf<String>()
        if (wallpaperId != null) {
            assetNameCandidates.add("$wallpaperId.json")
            if (wallpaperId.equals("coloros_horizon", ignoreCase = true)) {
                assetNameCandidates.add("ColorOS_Horizon.json")
            }
        }
        if (videoUriStr != null) {
            val fileName = videoUriStr.substringAfterLast("/").substringBeforeLast(".")
            assetNameCandidates.add("$fileName.json")
            assetNameCandidates.add("${fileName}_curve.json")
        }

        for (cand in assetNameCandidates) {
            try {
                val jsonStr = context.assets.open("curves/$cand").bufferedReader().use { it.readText() }
                val curve = WallpaperAnimationCurve.fromJson(jsonStr)
                if (curve != null) {
                    Log.d(TAG, "Loaded curve from assets: curves/$cand for $key")
                    cachedCurves[key] = curve
                    return curve
                }
            } catch (_: Exception) {}
        }

        // 2. Check local filesystem next to downloaded or custom wallpaper file
        if (videoUriStr != null) {
            val cleanPath = when {
                videoUriStr.startsWith("file://") -> videoUriStr.removePrefix("file://")
                videoUriStr.startsWith("/") -> videoUriStr
                else -> null
            }
            if (cleanPath != null) {
                val videoFile = File(cleanPath)
                val dir = videoFile.parentFile
                if (dir != null && dir.exists()) {
                    val fileCandidates = listOf(
                        File(dir, "${videoFile.nameWithoutExtension}_curve.json"),
                        File(dir, "animation_curve.json"),
                        File(dir, "animation_data.json"),
                        File(dir, "${videoFile.nameWithoutExtension}.json")
                    )
                    for (cand in fileCandidates) {
                        if (cand.exists() && cand.length() > 0) {
                            try {
                                val curve = WallpaperAnimationCurve.fromJson(cand.readText())
                                if (curve != null) {
                                    Log.d(TAG, "Loaded curve from disk: ${cand.absolutePath} for $key")
                                    cachedCurves[key] = curve
                                    return curve
                                }
                            } catch (e: Exception) {
                                Log.w(TAG, "Failed parsing curve file ${cand.absolutePath}", e)
                            }
                        }
                    }
                }
            }
        }

        cachedCurves[key] = null
        return null
    }

    /**
     * Clear cached curves when reloading or testing.
     */
    fun clearCache() {
        cachedCurves.clear()
    }
}
