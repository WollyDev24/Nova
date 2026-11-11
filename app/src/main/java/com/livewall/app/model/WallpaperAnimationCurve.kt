package com.livewall.app.model

import org.json.JSONObject

/**
 * Encapsulates spring physics curves and segmentation data for interactive wallpapers
 * (such as ColorOS interactive lock screen scrub and unlock transitions).
 */
data class WallpaperAnimationCurve(
    val slideDampingRatio: Float = 0.9f,
    val slideStiffness: Float = 575f,
    val pauseDampingRatio: Float = 2.0f,
    val pauseStiffness: Float = 100f,
    val unlockDampingRatio: Float = 1.0f,
    val unlockStiffness: Float = 631f,
    val startPauseValueMs: Long = 1061L,
    val slideCountThreshold: Int = 110,
    val drawTime: Long = 10L,
    val unlockType: Int = 1,
    val splitIndex: Int = 180,
    val isSplit: Boolean = true,
    val reverseStiffness: Float = 70f,
    val isSupportCurve: Boolean = true
) {
    companion object {
        fun fromJson(jsonStr: String): WallpaperAnimationCurve? {
            return try {
                val json = JSONObject(jsonStr)
                WallpaperAnimationCurve(
                    slideDampingRatio = json.optDouble("slideDampingRatio", 0.9).toFloat(),
                    slideStiffness = json.optDouble("slideStiffness", 575.0).toFloat(),
                    pauseDampingRatio = json.optDouble("pauseDampingRatio", 2.0).toFloat(),
                    pauseStiffness = json.optDouble("pauseStiffness", 100.0).toFloat(),
                    unlockDampingRatio = json.optDouble("unlockDampingRatio", 1.0).toFloat(),
                    unlockStiffness = json.optDouble("unlockStiffness", 631.0).toFloat(),
                    startPauseValueMs = json.optLong("startPauseValue", 1061L),
                    slideCountThreshold = json.optInt("slideCountThreshold", 110),
                    drawTime = json.optLong("drawTime", 10L),
                    unlockType = json.optInt("unlockType", 1),
                    splitIndex = json.optInt("splitIndex", 180),
                    isSplit = json.optBoolean("isSplit", true),
                    reverseStiffness = json.optDouble("reverseStiffness", 70.0).toFloat(),
                    isSupportCurve = json.optBoolean("isSupportCurve", true)
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}
