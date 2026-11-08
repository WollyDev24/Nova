<div align="center">

# Nova ✨

**HyperOS & ColorOS OEM Super Wallpapers for Android**
[![Stars](https://m3-markdown-badges.vercel.app/stars/6/2/WollyDev24/Nova)](https://github.com/WollyDev24/Nova)
[![Issues](https://m3-markdown-badges.vercel.app/issues/1/2/WollyDev24/Nova)](https://github.com/WollyDev24/Nova/issues)
[![License](https://ziadoua.github.io/m3-Markdown-Badges/badges/Apache/apache2.svg)](LICENSE)
[![Android](https://ziadoua.github.io/m3-Markdown-Badges/badges/Android/android3.svg)](https://github.com/WollyDev24/Nova/releases/latest)
[![Kotlin](https://ziadoua.github.io/m3-Markdown-Badges/badges/Kotlin/kotlin2.svg)](https://kotlinlang.org)

</div>

A modern, native **Material 3 Expressive** live wallpaper engine that brings official OEM super wallpaper experiences (HyperOS and ColorOS) to any Android device.

Nova smoothly plays cinematic unlock transitions as your phone wakes or unlocks, then rests on the final frame for a crisp, battery-efficient static wallpaper with zero idle battery drain.

---

## ✨ Features

- 🌌 **Authentic OEM Super Wallpapers** — curated geometric refraction and dynamic light flare wallpapers from Xiaomi 18 Pro Max, Xiaomi 18 Pro, and ColorOS
- 🔄 **Always-On Display (AOD) Reverse Playback** — plays smooth rewind animations back to the initial frame when locking the screen or transitioning into AOD
- ⚡ **Zero Idle Battery Drain** — video decoders hold cleanly on the final frame, dropping to 0Hz / 0% CPU consumption on your home screen
- 🚀 **Dynamic 120Hz Refresh Rate** — automatically requests high refresh rate during motion and releases upon rest
- 🎬 **Custom MP4 Video Import** — import any personal video with automatic thumbnail generation and color extraction
- 🌐 **Community Hub** — discover, preview, like, and publish user-created live wallpapers with Firebase cloud sync
- 📦 **In-App GitHub Update System** — checks GitHub Releases directly, downloads APKs via Android's DownloadManager, and prompts one-tap installs
- 🎨 **Material 3 Expressive UI** — fluid modern layout with dynamic color theming, wallpaper color palette extraction, and dark mode

## ⬇️ Download

| Source | Link |
| ------ | ---- |
| **GitHub Releases** | [Latest release](https://github.com/WollyDev24/Nova/releases/latest) |
| **Direct APK** | [Nova Releases](https://github.com/WollyDev24/Nova/releases) |

## 🧱 Built with

- [Kotlin](https://kotlinlang.org) + [Jetpack Compose](https://developer.android.com/jetpack/compose) / Material 3 Expressive
- [Android Media3 / ExoPlayer](https://developer.android.com/media/media3) — hardware-accelerated video decoding and zero-latency dual-player architecture
- [Coil 3](https://coil-kt.github.io/coil/) — modern asynchronous image loading
- [Firebase Firestore & Storage](https://firebase.google.com/) — Community Hub backend
- [Google Identity Credentials](https://developer.android.com/training/sign-in/credential-manager) — lightweight creator authentication

## 📂 Project structure

```text
app/src/main/
├── assets/
│   ├── thumbnails/                 # High-resolution WebP preview covers
│   └── wallpapers/                 # Bundled OEM forward and reverse MP4 video streams
├── res/
│   ├── drawable/                   # Adaptive launcher icons, GitHub badge, static wallpapers
│   ├── xml/                        # WallpaperService metadata & FileProvider paths
│   └── values/                     # Strings, theme definitions, colors
└── java/com/livewall/app/
    ├── data/
    │   ├── CommunityAuthManager.kt # Creator profile & Google identity sync
    │   ├── CommunityRepository.kt  # Firestore & Cloud Storage community backend
    │   ├── CustomWallpaperManager.kt # User MP4 importing & thumbnail generation
    │   └── WallpaperPreferences.kt # SharedPreferences storage for triggers & settings
    ├── model/
    │   ├── CommunityWallpaper.kt   # Community wallpaper data models
    │   └── WallpaperItem.kt        # Bundled and custom wallpaper definitions
    ├── service/
    │   ├── LiveWallpaperService.kt # Core dual-player live wallpaper engine (AOD + 120Hz)
    │   ├── SpecificWallpaperServices.kt # Per-wallpaper services for Samsung One UI previews
    │   └── WallpaperColorHelper.kt # System color palette extraction & Samsung synchronization
    ├── ui/
    │   ├── components/             # Reusable cards, video players, navigation bar, update dialog
    │   ├── screens/                # Home, PreviewSheet, Community, Settings, Upload, Profile
    │   └── theme/                  # Expressive shapes, typography, and theme provider
    ├── util/
    │   ├── Extensions.kt           # Context wrapper utilities
    │   └── UpdateManager.kt        # In-app GitHub release checker and APK installer
    └── MainActivity.kt             # Main entry point & Compose root
```

## 🛠️ Building from source

1. **Clone the repository**
   ```bash
   git clone https://github.com/WollyDev24/Nova.git
   ```
2. **Open in Android Studio** — *Open an Existing Project*, select the cloned directory
3. **Sync and build** — let Gradle resolve dependencies, then *Build → Make Project*
4. **Run** — connect an Android device via USB/Wi-Fi, then press *Run* ▶️

> Prefer the command line?
> ```bash
> ./gradlew assembleDebug
> # or install directly to an ADB device:
> ./gradlew installDebug
> ```

## 🤝 Contributing

Contributions are welcome! Please feel free to submit a pull request.

1. Fork the repository
2. Create your feature branch: `git checkout -b feature/AmazingFeature`
3. Commit your changes: `git commit -m 'Add some AmazingFeature'`
4. Push to the branch: `git push origin feature/AmazingFeature`
5. Open a pull request

## 📄 License

This project is licensed under the **Apache 2.0 License** — see the [LICENSE](LICENSE) file for details.

---

<div align="center">

Made with ❤️ by [WollyDev24](https://github.com/WollyDev24)

</div>
