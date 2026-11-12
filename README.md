# SmartLocalGallery 📸 🎬

A modern, production-grade Android Gallery, Cinema Media Player, Universal Downloader, and Encrypted Vault built with **Jetpack Compose**, **AndroidX Media3 (ExoPlayer)**, **LibVLC 3.7.5 Universal Fallback**, **100% Offline ML Kit**, and **Hardware-Backed AES-256-GCM Encryption**.

[![Release](https://img.shields.io/badge/Release-v2.0.0-blue.svg)](https://github.com/Akashkumar026/SmartLocalGallery/releases/tag/v2.0.0)
[![Direct Download APK](https://img.shields.io/badge/Download-APK%20v2.0.0%20(arm64--v8a)-brightgreen.svg)](https://github.com/Akashkumar026/SmartLocalGallery/releases/download/v2.0.0/app-arm64-v8a-release.apk)
[![compileSdk](https://img.shields.io/badge/compileSdk-35-orange.svg)]()
[![minSdk](https://img.shields.io/badge/minSdk-26-yellow.svg)]()

---

## 📥 Direct APK Download

Download the production-ready APK using any of the direct download options below:

- 🚀 **[Download v2.0.0 from GitHub Releases (Fastest CDN)](https://github.com/Akashkumar026/SmartLocalGallery/releases/download/v2.0.0/app-arm64-v8a-release.apk)**
- 📦 **[Download from Repository (Raw Mirror)](https://github.com/Akashkumar026/SmartLocalGallery/raw/main/release/app-arm64-v8a-release.apk)**
- 📂 **[View in Repository Files](https://github.com/Akashkumar026/SmartLocalGallery/blob/main/release/app-arm64-v8a-release.apk)**

### 🛡️ Build Verification Details
- **Version**: `2.0.0` (versionCode `2`)
- **Filename**: `app-arm64-v8a-release.apk`
- **File Size**: `101,803,908 bytes` (~97.08 MB)
- **SHA-256 Checksum**: `bb2cdde48e9a494ab451185eb57b39a385917760cee20e9da0d87c0d4edee0c8`
- **Architecture**: `arm64-v8a` (Android 8.0+ / API 26–35)

---

## 🌟 Core Feature Subsystems

### 1. 🤖 12 Practical Smart Collections (Multi-Signal On-Device AI)
- Combines **ML Kit Text Recognition (OCR)**, **ML Kit Image Labeling**, **ML Kit Barcode Scanning**, **Face Detection**, and aspect ratio heuristics.
- Automatically organizes photo libraries into 12 everyday categories:
  - 📄 **Documents & IDs** (Passports, Aadhaar, PAN card, driving licenses, contracts)
  - 🧾 **Bills & Receipts** (Invoices, payment receipts, UPI/tax receipts, GST)
  - 🎓 **Certificates & Awards** (Degrees, diplomas, competition honors)
  - 📢 **Brochures & Flyers** (Discounts, menus, event schedules)
  - 📝 **Notes & Whiteboards** (Handwritten notes, study lectures, diagrams)
  - 📱 **QR & Barcodes** (Boarding passes, tickets, scannable codes)
  - 🖼️ **Screenshots** (Screen captures with auto-filtering)
  - 🤳 **Selfies & Portraits** (Front camera & facial area clustering)
  - 👥 **Group Photos** (Group gatherings, celebrations, reunions)
  - 🐾 **Pets & Animals** (Dogs, cats, wildlife, birds)
  - 🍜 **Food & Dining** (Cuisine, meals, drinks, restaurant photos)
  - ✈️ **Travel & Vehicles** (Cars, flights, landscapes, mountains, beaches)
- **Bilingual OCR Engine**: Integrated English + Hindi (Devanagari and Romanized) OCR dictionaries for everyday Indian documents.
- **Strict Indian PII Redaction**: Automatic in-memory masking for Aadhaar (`XXXX-XXXX-1234`), PAN (`XXXXX1234X`), Passport, and Voter ID before database indexing.
- Manual user override persistence (`isUserOverridden`) prevents automated re-classification.

### 2. ⚡ High-Speed Universal Downloader
- **Thread-Safe Initialization State Machine**: Mutex-guarded engine lifecycle (`NotStarted`, `Initializing`, `Ready`, `Failed`) with automatic lazy initialization, non-blocking background pre-warming, and retryable `EngineNotReady` recovery with a 1-tap UI retry action.
- **Scoped Storage Compliant**: Stages downloads in app-private cache before streaming into public `Movies/SmartGallery` or `Music/SmartGallery` via `ContentResolver` and `IS_PENDING`.
- **Full Format & Quality Selectors**:
  - Video resolutions: `4K (2160p)`, `1440p`, `1080p`, `720p`, `480p`
  - Containers: `MP4` & `MKV`
  - Audio extraction: `MP3` & `M4A` at `320 kbps`, `192 kbps`, `128 kbps`
- **Multi-Connection Aria2c Acceleration** with resume flag (`-c`).
- **Resilient Fallback Chains**: Automatically falls back to `best/bestvideo+bestaudio` if preferred stream format fails.
- **Typed Error Classification**: Categorizes errors into `EngineNotReady`, `Network`, `StorageFull`, `FormatNotFound`, `UnsupportedSite`, `Unavailable`, `Cancelled`, and `Unknown`, with 1-tap retry for retryable failures.
- **Legal Notice**: Compliance disclaimer banner reminding users to adhere to source site Terms of Service and applicable copyright laws.

### 3. 🧵 Concurrent Multi-Task AI Orchestrator
- Background `SupervisorJob` orchestrating 3 independent, fault-isolated pipelines in parallel:
  - **Pipeline 1**: Duplicate & Blur Engine (`Dispatchers.IO.limitedParallelism(2)`)
  - **Pipeline 2**: Smart Document & Image Categorizer (`Dispatchers.Default.limitedParallelism(2)`)
  - **Pipeline 3**: Face Clustering & Embedding Engine (`Dispatchers.Default.limitedParallelism(1)`)
- Throttled automatically by media playback status (`ActivePlayerRegistry.isPlaying`) and low OS background priority to guarantee locked 60/120 FPS UI.
- Process death checkpoint persistence using `lastScanCheckpointId` ensuring uninterrupted background resuming.
- Live Multi-Task HUD with 3-channel visual progress bars, items/second throughput, error badges, and diagnostic failure details dialog.

### 4. 🔒 Zero-Leak Private Vault
- **Hardware-Backed AES-256-GCM**: Uses `MasterKey.Builder` requesting StrongBox HSM with automatic fallback to Android Keystore.
- **Pre-Deletion SHA-256 Integrity Verification**: Asserts decrypted stream checksum strictly matches the public original before deleting public files.
- **Streaming ExoPlayer Decryption (`vault://`)**: Custom `EncryptedFileDataSource` enables seeking and instant playback of encrypted videos without writing unencrypted temporary files or causing heap OOM crashes.
- **Brute-Force Lockout Shield**: Salted SHA-256 PIN authentication with persistent 5-attempt lockout (30-second penalty) surviving app restarts.
- **Zero Plaintext Leakage**: Sensitive keys (`VAULT_PIN_HASH`, `VAULT_PIN_SALT`, etc.) are excluded from settings backup JSON.
- **1-Tap Restore to Gallery**: Re-streams decrypted content back into public storage with MediaScanner re-indexing.

### 5. 🎥 Cinema Multimedia Player
- **AndroidX Media3 ExoPlayer Surface Rendering** with hardware acceleration.
- **Universal LibVLC 3.7.5 Fallback**: Automatically switches to LibVLC (`VLCVideoLayout`) if hardware decoders fail on legacy or non-standard container formats (e.g. AVI, WMV, FLV, RMVB).
- **Direct Hardware Audio Effects**: Binds active `audioSessionId` directly to `android.media.audiofx.Equalizer` (10-band) and `LoudnessEnhancer` (up to +3000 mB gain above 100%).
- **Strict Scoped Storage Media Exports**: Video trimming, audio extraction, screenshots, and frame-accurate GIF maker stage output in cache before publishing to public MediaStore.
- **Seamless Orientation & Touch Re-Binding**: Dynamic container dimension tracking guarantees gesture bounds (brightness swipe, volume swipe, double-tap seek, single-tap controls) remain responsive across orientation changes.
- **Picture-in-Picture (PiP)**: Full hardware-accelerated PiP mode supported in manifest and lifecycle.

---

## 📱 Permissions Breakdown

| Permission | Category | Purpose |
| :--- | :--- | :--- |
| `READ_MEDIA_IMAGES` | Media Access | Scans and displays local images on Android 13+ (API 33+) |
| `READ_MEDIA_VIDEO` | Media Access | Scans and streams local videos on Android 13+ (API 33+) |
| `READ_MEDIA_AUDIO` | Media Access | Scans and plays local audio files on Android 13+ (API 33+) |
| `READ_EXTERNAL_STORAGE` | Legacy Media | Scans local media files on Android 8.0–12 (API 26–32) |
| `WRITE_EXTERNAL_STORAGE` | Legacy Storage | Media export and trash management on Android 8.0–9.0 |
| `INTERNET` | Networking | Universal Downloader streaming via embedded yt-dlp & aria2c |
| `ACCESS_NETWORK_STATE` | Networking | Network connectivity monitoring for download queue |
| `POST_NOTIFICATIONS` | Notifications | Download progress and multi-task pipeline alerts (Android 13+) |
| `USE_BIOMETRIC` | Security | Fingerprint / face unlock for Private Vault authentication |
| `FOREGROUND_SERVICE` | Background Execution | Ensures uninterrupted download processing in background |
| `FOREGROUND_SERVICE_DATA_SYNC` | Background Execution | WorkManager background tasks on Android 14+ (API 34+) |

---

## 🛠️ Tech Stack & Architecture

- **UI**: Jetpack Compose, Material 3, Material Icons Extended
- **Language**: Kotlin 1.9.23, JVM 17
- **Database**: Room 2.6.1 with FTS4 Full-Text Search
- **DI**: Dagger Hilt 2.51.1
- **Async**: Kotlin Coroutines & Flows
- **Image & Video**: Coil 3, AndroidX Media3 ExoPlayer 1.3.1, LibVLC 3.7.5 Fallback
- **Offline ML**: Google ML Kit (Text Recognition, Barcode Scanning, Face Detection, Image Labeling), LiteRT / TensorFlow Lite
- **Downloader**: Embedded youtubedl-android, FFmpeg, Aria2c
- **Security**: AndroidX Security Crypto (`EncryptedFile`, `MasterKey`), AndroidX Biometric, AES-256-GCM

---

## ⚖️ Legal Disclaimer

SmartLocalGallery is distributed strictly via GitHub as an open-source multimedia management utility. Users must comply with source platform Terms of Service and applicable local copyright laws when utilizing the Universal Downloader.

---

## 📄 License
Open source under the [Apache License 2.0](LICENSE).
