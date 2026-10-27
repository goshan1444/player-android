# 🎵 Player

<div align="center">

  <p><b>A native Android Hi-Fi local music player built with Kotlin, Jetpack Compose, and Material 3.</b></p>
  

**Player** is a modern, high-fidelity offline music player designed specifically for Android. It focuses on absolute audio metadata accuracy, reliable background playback, and a fluid, gesture-driven Material Design 3 interface inspired by modern aesthetic standards.

---

## ✨ Key Features

### 🎧 Hi-Fi Audio & Format Support
* **Wide Codec Support**: Native and software-backed playback for **FLAC** (16-bit / 24-bit up to 192 kHz), **ALAC** (in M4A containers via MediaCodec & bundled FFmpeg extension), **WAV** (PCM), **AAC**, and **MP3** (CBR & VBR).
* **Precise Technical Inspection**: Deep binary header parsing (`AudioHeaderInspector`) extracts exact source container formats, sample rates, true bit depths, and bitrates without relying solely on incomplete `MediaStore` tags.
* **ReplayGain Normalization**: Lossless PCM gain scaling with peak clipping prevention for Track and Album modes.
* **Hardware Equalizer**: Direct binding to active ExoPlayer audio sessions with customizable frequency bands and presets.
* **High-Resolution Output Strategy**: Capability-aware output modes supporting standard Android mixing or high-resolution float PCM routing.

### 🎨 Material Design 3 & Fluid Gestures
* **Material You (Dynamic Color)**: Adapts seamlessly to system wallpapers on Android 12+ alongside a deep true-black AMOLED dark theme.
* **Gesture-Driven Interface**:
  * Swipe horizontally across the album artwork to skip tracks with haptic feedback.
  * Double-tap the left or right side of the square cover to jump backward or forward by 10 seconds.
  * Long-press artwork to trigger instant signal path and metadata inspection.
  * Pull down on the Now Playing screen to minimize smoothly.
  * Swipe items in library lists for quick actions (`Play Next`, `Favorite`).
* **Synced Lyrics Support**: Automatic parsing of companion `.lrc` files or embedded metadata with real-time scrolling and tap-to-seek synchronization.

### 🛠️ Architecture & Reliability
* **Offline-First & Privacy Focused**: Zero cloud accounts, zero ads, zero telemetry, and no internet permission required.
* **Robust Background Service**: Powered by Media3 `MediaSessionService` with full system media notifications, lock-screen controls, and audio focus management.
* **Smart Sleep Timer**: Configurable countdown with smooth volume fade-out over the last 10 seconds before pausing.
* **Modern Tech Stack**: Written 100% in Kotlin, Jetpack Compose, Room Database, DataStore Preferences, and Kotlin Coroutines/Flows.

---

## 🚀 Getting Started

1. Clone the repository:
   ```bash
   git clone [https://github.com/your-username/player-android.git](https://github.com/goshan1444/player-android.git)
