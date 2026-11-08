# Nova — Cinematic Super Wallpapers for Android

[![GitHub Release](https://img.shields.io/github/v/release/WollyDev24/Nova?style=flat-square)](https://github.com/WollyDev24/Nova/releases)
[![License: Apache-2.0](https://img.shields.io/badge/License-Apache%202.0-blue.svg?style=flat-square)](LICENSE)
[![Platform](https://img.shields.io/badge/Platform-Android%208.0%2B-green.svg?style=flat-square)](https://developer.android.com)

**Nova** brings authentic HyperOS and ColorOS OEM super wallpaper experiences to any Android phone. Videos play fluidly on wake/unlock, pausing right at the final frame for a stunning, battery-efficient static wallpaper.

---

## ✨ Features

- **Fluid OEM Super Wallpapers**: Curated high-fidelity unlock animations from Xiaomi 18 Pro Max, Xiaomi 18 Pro, and ColorOS.
- **Smart AOD & Rewind Animation**: Automatically plays reverse animations on screen lock / Always-On Display transitions.
- **Zero Idle Battery Drain**: Decoders pause cleanly at the final frame, dropping to 0Hz / 0% CPU consumption while on home screen.
- **Dynamic 120Hz Refresh Rate**: Automatically requests native high-refresh rate during motion and releases upon rest.
- **Custom Video Support**: Import and optimize your own MP4 videos with auto-generated cover thumbnails.
- **Community Hub**: Discover, preview, and share custom live wallpapers with fellow enthusiasts.
- **In-App Update System**: Directly checks [GitHub Releases](https://github.com/WollyDev24/Nova/releases) for new features and one-tap updates.
- **Material 3 Expressive UI**: Modern fluid design supporting Dynamic Color theming and AMOLED dark mode.

---

## 🚀 GitHub Repository & Releases

- **GitHub Repository**: [https://github.com/WollyDev24/Nova](https://github.com/WollyDev24/Nova)
- **Releases & APK Downloads**: [https://github.com/WollyDev24/Nova/releases](https://github.com/WollyDev24/Nova/releases)

---

## 🛠️ Building From Source

```bash
git clone https://github.com/WollyDev24/Nova.git
cd Nova
./gradlew assembleDebug
```

The debug APK will be generated at `app/build/outputs/apk/debug/app-debug.apk`.

Install directly to an ADB-connected device:
```bash
./gradlew installDebug
```

---

## 📄 License

Licensed under the Apache License, Version 2.0.
