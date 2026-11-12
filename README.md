# SmartLocalGallery 📸 🎬

A modern, production-grade Android Gallery, Cinema Media Player, Universal Downloader, and Encrypted Vault built with **Jetpack Compose**, **AndroidX Media3 (ExoPlayer)**, **100% Offline ML Kit**, and **Hardware-Backed AES-256-GCM Encryption**.

[![Release](https://img.shields.io/badge/Release-v1.0.0-blue.svg)](https://github.com/Akashkumar026/SmartLocalGallery/releases/tag/v1.0.0)
[![Direct Download APK](https://img.shields.io/badge/Download-APK%20(arm64--v8a)-brightgreen.svg)](https://github.com/Akashkumar026/SmartLocalGallery/releases/download/v1.0.0/app-arm64-v8a-release.apk)
[![compileSdk](https://img.shields.io/badge/compileSdk-35-orange.svg)]()
[![minSdk](https://img.shields.io/badge/minSdk-26-yellow.svg)]()

---

## 📥 Direct APK Download

Download the production-ready APK using any of the direct download options below:

- 🚀 **[Download from GitHub Releases (Fastest CDN)](https://github.com/Akashkumar026/SmartLocalGallery/releases/download/v1.0.0/app-arm64-v8a-release.apk)**
- 📦 **[Download from Repository (Raw Mirror)](https://github.com/Akashkumar026/SmartLocalGallery/raw/main/release/app-arm64-v8a-release.apk)**
- 📂 **[View in Repository Files](https://github.com/Akashkumar026/SmartLocalGallery/blob/main/release/app-arm64-v8a-release.apk)**

### 🛡️ Build Verification Details
- **Filename**: `app-arm64-v8a-release.apk`
- **File Size**: `101,773,636 bytes` (~97.06 MB)
- **SHA-256 Checksum**: `763C64B579B9D98C4230D26DDB688F07C1D6C62FE5FBE5F872D3E481A2DD4BB4`
- **Architecture**: `arm64-v8a` (Android 8.0+ / API 26–35)

---

## 🌟 Core Feature Subsystems

### 1. 🤖 12 Practical Smart Collections (Multi-Signal On-Device AI)
- Combines **ML Kit Text Recognition (OCR)**, **ML Kit Image Labeling**, **ML Kit Barcode Scanning**, **Face Detection**, and aspect ratio heuristics.
- Automatically organizes photo libraries into 12 everyday categories:
  - 📄 **Documents & IDs** (Passports, Aadhaar, PAN card, driving licenses, contracts)
  - 🧾 **Bills & Receipts** (Invoices, payment slips, UPI/tax receipts)
  - 🎓 **Certificates & Awards** (Degrees, diplomas, competition honors)
  - 📢 **Brochures & Flyers** (Discounts, menus, event schedules)
  - 📝 **Notes & Whiteboards** (Handwritten notes, study lectures, diagrams)
  - 📱 **QR & Barcodes** (Boarding passes, tickets, scannable codes)
  - 🖼️ **Screenshots** (Screen captures with auto-filtering)
  - 🤳 **Selfies & Portraits** (Front camera & facial area clustering)
  - 🐾 **Pets & Animals** (Dogs, cats, wildlife, birds)
  - 🍜 **Food & Dining** (Cuisine, meals, drinks, restaurant photos)
  - ✈️ **Travel & Vehicles** (Cars, planes, mountains, beaches, landscapes)
  - 😂 **Memes & Social** (Comics, cartoons, downloaded humor)
- Automatically hides empty collections from the UI.

### 2. ⚡ High-Speed Universal Downloader
- **Scoped Storage Compliant**: Stages downloads in app-private cache before streaming into public `Movies/SmartGallery` or `Music/SmartGallery` via `ContentResolver` and `IS_PENDING`.
- **Full Format & Quality Selectors**:
  - Video resolutions: `4K (2160p)`, `1440p`, `1080p`, `720p`, `480p`
  - Containers: `MP4` & `MKV`
  - Audio extraction: `MP3` & `M4A` at `320 kbps`, `192 kbps`, `128 kbps`
- **Multi-Connection Aria2c Acceleration** with resume flag (`-c`).
- Resilient format fallback chains (`best/bestvideo+bestaudio`).
- Actionable error reporting with 1-tap retry.

### 3. 🧵 Concurrent Multi-Task AI Orchestrator
- Background `SupervisorJob` orchestrating 3 independent, fault-isolated pipelines in parallel:
  - **Pipeline 1**: Duplicate & Blur Engine (`Dispatchers.IO.limitedParallelism(2)`)
  - **Pipeline 2**: Smart Document & Image Categorizer (`Dispatchers.Default.limitedParallelism(2)`)
  - **Pipeline 3**: Face Clustering & Embedding Engine (`Dispatchers.Default.limitedParallelism(1)`)
- Throttled automatically by media playback status (`ActivePlayerRegistry.isPlaying`) and low OS background priority to guarantee locked 60/120 FPS UI.
- Live Multi-Task HUD with 3-channel visual progress bars, items/second throughput, and independent Pause/Resume/Cancel triggers.

### 4. 🔒 Zero-Leak Private Vault
- **Hardware-Backed AES-256-GCM**: Encrypts files directly to app-internal storage (`private_vault/$uuid.vault`) + encrypted thumbnails.
- **Permanent Public Original Wipe**: Removes original files completely from public MediaStore upon user consent.
- **Biometric & PIN Lockout Shield**: Auto-locks on `ON_PAUSE`, with 5-attempt brute-force protection and a 30-second lockout timer.
- **In-Memory Stream Decryption**: Zero unencrypted temporary disk writes.
- **1-Tap Restore to Gallery**: Re-streams decrypted content back into public storage with MediaScanner re-indexing.

### 5. 🎥 Cinema Multimedia Player
- AndroidX Media3 ExoPlayer surface rendering with hardware acceleration.
- Modern minimalist cinema controls, Marquee title, Orientation switcher, Speed slider, A-B repeat loop, Subtitle/Audio track selector, Picture-in-Picture (PiP), 10-Band EQ with 200% volume boost, and frame-accurate GIF maker.

---

## 🛠️ Tech Stack & Architecture

- **UI**: Jetpack Compose, Material 3, Material Icons Extended
- **Language**: Kotlin 1.9.23, JVM 17
- **Database**: Room 2.6.1 + FTS4 Full-Text Search
- **DI**: Dagger Hilt 2.51.1
- **Async**: Kotlin Coroutines & Flows
- **Image & Video**: Coil 3, AndroidX Media3 ExoPlayer 1.3.1, LibVLC 3.7.5 Fallback
- **Offline ML**: Google ML Kit (Text Recognition, Barcode Scanning, Face Detection, Image Labeling), LiteRT / TensorFlow Lite
- **Downloader**: Embedded youtubedl-android, FFmpeg, Aria2c
- **Security**: AndroidX Security Crypto (`EncryptedFile`, `MasterKeys`), AndroidX Biometric

---

## 📄 License
Open source under the [Apache License 2.0](LICENSE).

