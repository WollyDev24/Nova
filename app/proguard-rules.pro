# Proguard rules for Nova Live Wallpaper

# Keep WallpaperService and Android components
-keep class * extends android.service.wallpaper.WallpaperService { *; }
-keep class * extends android.app.Service { *; }
-keep class * extends android.app.Activity { *; }
-keep class * extends android.content.BroadcastReceiver { *; }
-keep class * extends androidx.core.content.FileProvider { *; }

# Keep data models
-keep class com.livewall.app.model.** { *; }
-keep class com.livewall.app.data.PlaybackTrigger { *; }
-keep class com.livewall.app.data.ScalingMode { *; }
-keep class com.livewall.app.util.UpdateInfo { *; }
-keep class com.livewall.app.util.UpdateCheckResult { *; }

# Keep Media3 / ExoPlayer
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# Keep Coil image loading
-keep class coil3.** { *; }
-dontwarn coil3.**

# Keep Firebase Firestore models and annotations
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-keepclassmembers class * {
    @com.google.firebase.firestore.PropertyName <fields>;
    @com.google.firebase.firestore.PropertyName <methods>;
}

# AndroidX Compose runtime & compiler rules
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**
