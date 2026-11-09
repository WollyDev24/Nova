package com.livewall.app.data

import android.content.Context
import android.content.SharedPreferences
import com.livewall.app.model.WallpaperItem
import org.json.JSONArray
import org.json.JSONObject

enum class PlaybackTrigger(val key: String, val title: String, val description: String) {
    SCREEN_ON("screen_on", "When Screen Turns On", "Plays immediately as soon as you wake or tap the screen"),
    SCREEN_UNLOCK("screen_unlock", "When Unlocked", "Shows first frame on lock screen, then smoothly animates once you unlock");

    companion object {
        fun fromKey(key: String): PlaybackTrigger {
            return entries.find { it.key == key } ?: SCREEN_UNLOCK
        }
    }
}

enum class ScalingMode(val key: String, val title: String) {
    CROP("crop", "Center Crop (Fill Screen)"),
    FIT("fit", "Fit Aspect Ratio");

    companion object {
        fun fromKey(key: String): ScalingMode {
            return entries.find { it.key == key } ?: CROP
        }
    }
}

class WallpaperPreferences(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var trigger: PlaybackTrigger
        get() = PlaybackTrigger.fromKey(prefs.getString(KEY_TRIGGER, PlaybackTrigger.SCREEN_UNLOCK.key)!!)
        set(value) = prefs.edit().putString(KEY_TRIGGER, value.key).apply()

    var scalingMode: ScalingMode
        get() = ScalingMode.fromKey(prefs.getString(KEY_SCALING, ScalingMode.CROP.key)!!)
        set(value) = prefs.edit().putString(KEY_SCALING, value.key).apply()

    var isMuted: Boolean
        get() = prefs.getBoolean(KEY_MUTED, true)
        set(value) = prefs.edit().putBoolean(KEY_MUTED, value).apply()

    var replayOnHomeReturn: Boolean
        get() = prefs.getBoolean(KEY_REPLAY_ON_HOME, false)
        set(value) = prefs.edit().putBoolean(KEY_REPLAY_ON_HOME, value).apply()

    var selectedWallpaperId: String
        get() = prefs.getString(KEY_SELECTED_ID, WallpaperItem.DEFAULT_WALLPAPER.id)!!
        set(value) = prefs.edit().putString(KEY_SELECTED_ID, value).apply()

    var selectedWallpaperUri: String
        get() = prefs.getString(KEY_SELECTED_URI, WallpaperItem.DEFAULT_WALLPAPER.videoUri)!!
        set(value) = prefs.edit().putString(KEY_SELECTED_URI, value).apply()

    var selectedWallpaperTitle: String
        get() = prefs.getString(KEY_SELECTED_TITLE, WallpaperItem.DEFAULT_WALLPAPER.title)!!
        set(value) = prefs.edit().putString(KEY_SELECTED_TITLE, value).apply()

    var googleWebClientId: String?
        get() = prefs.getString(KEY_GOOGLE_WEB_CLIENT_ID, null)
        set(value) = prefs.edit().putString(KEY_GOOGLE_WEB_CLIENT_ID, value).apply()

    var customDisplayName: String?
        get() = prefs.getString(KEY_CUSTOM_DISPLAY_NAME, null)
        set(value) = prefs.edit().putString(KEY_CUSTOM_DISPLAY_NAME, value).apply()

    var customPhotoUrl: String?
        get() = prefs.getString(KEY_CUSTOM_PHOTO_URL, null)
        set(value) = prefs.edit().putString(KEY_CUSTOM_PHOTO_URL, value).apply()

    var autoCheckUpdates: Boolean
        get() = prefs.getBoolean(KEY_AUTO_CHECK_UPDATES, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_CHECK_UPDATES, value).apply()

    fun selectWallpaper(item: WallpaperItem) {
        prefs.edit()
            .putString(KEY_SELECTED_ID, item.id)
            .putString(KEY_SELECTED_URI, item.videoUri)
            .putString(KEY_SELECTED_TITLE, item.title)
            .commit()
    }

    fun getCustomWallpapers(): List<WallpaperItem> {
        val raw = prefs.getString(KEY_CUSTOM_WALLPAPERS, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            val list = mutableListOf<WallpaperItem>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    WallpaperItem(
                        id = obj.getString("id"),
                        title = obj.getString("title"),
                        collection = "Custom",
                        videoUri = obj.getString("videoUri"),
                        thumbnailUri = obj.optString("thumbnailUri", ""),
                        durationSec = obj.optDouble("durationSec", 0.0).toFloat(),
                        resolution = obj.optString("resolution", "Custom"),
                        description = "Imported custom video wallpaper",
                        isCustom = true
                    )
                )
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun addCustomWallpaper(item: WallpaperItem) {
        val current = getCustomWallpapers().toMutableList()
        current.removeAll { it.id == item.id }
        current.add(0, item)
        saveCustomWallpapers(current)
    }

    fun removeCustomWallpaper(id: String) {
        val current = getCustomWallpapers().toMutableList()
        current.removeAll { it.id == id }
        saveCustomWallpapers(current)
    }

    private fun saveCustomWallpapers(list: List<WallpaperItem>) {
        val array = JSONArray()
        for (item in list) {
            val obj = JSONObject().apply {
                put("id", item.id)
                put("title", item.title)
                put("videoUri", item.videoUri)
                put("thumbnailUri", item.thumbnailUri)
                put("durationSec", item.durationSec.toDouble())
                put("resolution", item.resolution)
            }
            array.put(obj)
        }
        prefs.edit().putString(KEY_CUSTOM_WALLPAPERS, array.toString()).apply()
    }

    fun registerListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    fun unregisterListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
    }

    companion object {
        const val PREFS_NAME = "livewall_preferences"
        const val KEY_TRIGGER = "playback_trigger"
        const val KEY_SCALING = "scaling_mode"
        const val KEY_MUTED = "is_muted"
        const val KEY_REPLAY_ON_HOME = "replay_on_home"
        const val KEY_SELECTED_ID = "selected_wallpaper_id"
        const val KEY_SELECTED_URI = "selected_wallpaper_uri"
        const val KEY_SELECTED_TITLE = "selected_wallpaper_title"
        const val KEY_CUSTOM_WALLPAPERS = "custom_wallpapers"
        const val KEY_GOOGLE_WEB_CLIENT_ID = "google_web_client_id"
        const val KEY_CUSTOM_DISPLAY_NAME = "custom_display_name"
        const val KEY_CUSTOM_PHOTO_URL = "custom_photo_url"
        const val KEY_AUTO_CHECK_UPDATES = "auto_check_updates"
    }
}
