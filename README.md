# 🐱 CatKeeper

**CatKeeper** is a premium Android application designed to help you break free from the "infinite scroll" of Instagram Reels. It monitors your usage, tracks your scrolling habits, and enforces healthy boundaries with beautiful, non-intrusive (yet firm) interventions.

![CatKeeper Banner](https://img.shields.io/badge/Status-Active-brightgreen)
![Kotlin](https://img.shields.io/badge/Kotlin-2.2.0-purple)
![Compose](https://img.shields.io/badge/UI-Jetpack_Compose-blue)

## ✨ Features

- **🕒 Smart Monitoring**: Tracks Instagram usage in real-time.
- **🚫 Break Enforcement**: Automatically overlays a calming (or firm) video break when you exceed your session or daily limits.
- **📊 Advanced Stats**: 
    - Hourly usage visualization for the last 24 hours.
    - **Swipe Density Heatmap**: Bar colors transition from Green to Red based on how many Reels you've swiped per hour.
- **📜 Scroll Tracking**: High-accuracy Reels swipe detection (optimized to ignore comment scrolling and accidental touches).
- **📄 Data Export**: Export your usage history to PDF or share a beautiful stats card with your friends.
- **🎨 Premium UI**: Dark-themed, glassmorphic design built with modern Jetpack Compose.

## 🛠 Tech Stack

- **Core**: Kotlin 2.2.x
- **UI**: Jetpack Compose (Clean Architecture)
- **Database**: Room Persistence Library (KSP)
- **Monitoring**: Accessibility Service (for scroll tracking) & UsageStats
- **Media**: Media3 ExoPlayer for blocking overlays
- **Dependency Management**: Gradle Version Catalog (libs.versions.toml)

## 🚀 Getting Started

1. **Clone the repo**:
   ```bash
   git clone git@github.com:towhaEL/CatKeeper.git
   ```
2. **Open in Android Studio**: Use the latest Ladybug or Jellyfish build.
3. **Permissions**: The app requires:
    - **Accessibility Service**: To detect Reels swipes and show the blocking overlay.
    - **Usage Access**: To track app foreground time.
    - **Overlay Permission**: To show the break screen over Instagram.

## 🛡 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

---
*Built with ❤️ to keep you focused.*
