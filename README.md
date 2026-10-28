# 🎵 Player (v1.1 beta)

<div align="center">

  <p><b>A native Android Hi-Fi local music player built with Kotlin, Jetpack Compose, and Material 3.</b></p>
  
</div>

**Player** is a modern, high-fidelity offline music player designed specifically for Android. It focuses on absolute audio metadata accuracy, reliable background playback, and a fluid, gesture-driven Material Design 3 interface inspired by modern aesthetic standards.

---

## ✨ Key Features (v1.1 beta)

### 🎧 Hi-Fi Audio & Format Support
* **Wide Codec Support**: Native and software-backed playback for **FLAC** (16-bit / 24-bit up to 192 kHz), **ALAC** (Apple Lossless in M4A), **WAV** (PCM), **APE** (Monkey's Audio), **AAC**, **OGG**, **OPUS**, and **MP3** (CBR & VBR).
* **Comprehensive CUE Sheet Support**: Full support for `.cue` disc images (FLAC+CUE, APE+CUE, WAV+CUE) with real-time lossless clipping via ExoPlayer. Multi-charset detection supporting UTF-8 (with BOM), Windows-1251 (Cyrillic EAC rips), and Windows-1252.
* **Precise Technical Inspection**: Deep binary header parsing (`AudioHeaderInspector`) extracts exact source container formats, sample rates, true bit depths, and bitrates without relying solely on incomplete `MediaStore` tags.
* **ReplayGain Normalization**: Lossless PCM gain scaling with peak clipping prevention for Track and Album modes.
* **Hardware Equalizer**: Direct binding to active ExoPlayer audio sessions with customizable frequency bands and presets.
* **High-Resolution Output Strategy**: Capability-aware output modes supporting standard Android mixing or high-resolution float PCM routing.

### 🖼️ Automatic Artwork Download & Caching
* **Smart Artwork Downloader**: Automatically queries **iTunes Search API** (high-res 600×600 px) and **Deezer API** (up to 1000×1000 px) for albums and tracks lacking embedded art.
* **Local Offline Cache**: Downloaded covers are saved to app-isolated storage and persisted in Room database for instant offline viewing.
* **Batch Downloader**: One-tap "Download Art" in Settings scans your entire library and downloads missing covers in the background.

### 🎨 Material Design 3 & Fluid Gestures
* **Material You (Dynamic Color)**: Adapts seamlessly to system wallpapers on Android 12+ alongside a deep true-black AMOLED dark theme.
* **Jitter-Free Dismiss Animation**: 1-to-1 touch-driven sheet pull down to collapse Now Playing screen smoothly to the bottom.
* **Gesture-Driven Player Controls**:
  * Swipe horizontally across the album artwork to skip tracks with haptic feedback.
  * Double-tap the left or right side of the square cover to jump backward or forward by 10 seconds.
  * Long-press artwork to trigger instant signal path and metadata inspection.
* **Fullscreen Synced Lyrics**: Real-time karaoke-style scrolling with tap-to-seek synchronization from companion `.lrc` files and embedded metadata.
* **Card-Based Folder Navigation**: Smooth horizontal page transitions into folders (album-style) instead of accordion expansion.

### 🛠️ Architecture & Reliability
* **Modern Tech Stack**: Written 100% in Kotlin, Jetpack Compose, Room Database, DataStore Preferences, and Kotlin Coroutines/Flows.
* **Robust Background Service**: Powered by Media3 `MediaSessionService` with full system media notifications, lock-screen controls, and audio focus management.
* **Standalone Sleep Timer**: Configurable countdown with smooth volume fade-out and "End of Track" mode.

---

## 🚀 Getting Started

1. Clone the repository:
   ```bash
   git clone https://github.com/goshan1444/player-android.git
   ```
2. Open in **Android Studio Jellyfish / Koala or later**.
3. Build and run debug or install the release APK from:
   ```
   app/release/app-release.apk
   ```
