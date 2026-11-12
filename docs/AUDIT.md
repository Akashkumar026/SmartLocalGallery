# SmartLocalGallery — Comprehensive Technical Audit Report (Phase 0)

> **Date:** November 12, 2026  
> **Package:** `com.example.smartgallery`  
> **Target SDK:** 35 | **Compile SDK:** 35 | **Min SDK:** 26 (Android 8.0)  
> **Kotlin:** 1.9.23 | **JVM:** 17 | **Gradle:** 8.7 | **AGP:** 8.4.1  
> **Auditor:** Senior Android Systems, Multimedia & Security Architect  

---

## 1. Build Health & Compiler Diagnostics

### 1.1 Build Execution Result
- **Command:** `./gradlew assembleRelease`
- **Status:** **SUCCEEDED** (Duration: 1m 54s, Artifact: `app/build/outputs/apk/release/app-arm64-v8a-release.apk`)
- **APK Size:** `101,773,636 bytes` (~97.06 MB)
- **SHA-256 Checksum:** `763C64B579B9D98C4230D26DDB688F07C1D6C62FE5FBE5F872D3E481A2DD4BB4`

### 1.2 Deprecated APIs & Compiler Warnings
1. **Security Crypto Deprecations (`androidx.security:security-crypto:1.1.0-alpha06`)**:
   - `MasterKeys` deprecated in Java: `VaultAndFileManager.kt:20, 54`.
   - `EncryptedFile.Builder(File, Context, String, EncryptedFile.FileEncryptionScheme)` deprecated: `VaultAndFileManager.kt:70, 100, 178, 229, 330`.
2. **Audio & Media Deprecations**:
   - `android.media.audiofx.Virtualizer` deprecated: `PlaybackService.kt:10`.
   - `TAG_ISO_SPEED_RATINGS: String` deprecated in Android Exif: `MainActivity.kt:744`.
   - `getLatLong(FloatArray!): Boolean` deprecated: `VaultAndFileManager.kt:401`.
   - `TRIM_MEMORY_RUNNING_LOW: Int` deprecated: `SmartGalleryApp.kt:105`.
3. **Compose Material Icons Deprecations**:
   - Icons deprecated in favor of `Icons.AutoMirrored.Filled.*`:
     - `Icons.Default.ArrowBack` (`MainActivity.kt:427, 1008, 1073`, `PhotoEditorScreen.kt:115`, `VaultScreen.kt:182`)
     - `Icons.Default.RotateRight` (`MainActivity.kt:435`, `PhotoEditorScreen.kt:251`)
     - `Icons.Default.Undo` (`PhotoEditorScreen.kt:124`)
     - `Icons.Default.Article` (`SmartCategoryClassifier.kt:59`)
     - `Icons.Default.VolumeOff`, `VolumeUp`, `VolumeDown` (`SmartVideoPlayerScreen.kt:1127-1129`)
4. **Manifest & AGP Warnings**:
   - `android:extractNativeLibs="true"` specified in `AndroidManifest.xml:61`: AGP warns this attribute should not be manually set in `AndroidManifest.xml` when `useLegacyPackaging = true` is already configured in Gradle packaging.
   - TensorFlow Lite namespace collision: `Namespace 'org.tensorflow.lite.support' is used in multiple modules: org.tensorflow:tensorflow-lite-support:0.4.4 and org.tensorflow:tensorflow-lite-support-api:0.4.4`.
5. **Lint Suppression**:
   - `lint.checkReleaseBuilds = false`, `abortOnError = false`, `ignoreWarnings = true`, and `lintVital` tasks disabled in `app/build.gradle.kts:79-100`.

---

## 2. Feature Matrix & Specification Compliance

| Feature | Status | Primary Files & Lines | Core Compliance Issues & Gap Analysis |
| :--- | :---: | :--- | :--- |
| **Feature A: Media Library & Indexing** | **Partial** | `LocalGalleryEngine.kt:136-224`<br>`GalleryDatabase.kt:233-245, 319-325`<br>`MainViewModel.kt:118-121, 418-431` | • No MediaStore cursor paging (`QUERY_ARG_LIMIT`/`QUERY_ARG_OFFSET`); queries whole library into RAM list.<br>• `GENERATION_MODIFIED` is not queried or stored; incremental re-indexing only uses `DATE_MODIFIED`.<br>• `MediaFts` table only indexes `displayName`, `ocrText`, `bucketName`; detected image labels are completely missing.<br>• `MediaFts` is only populated inside OCR scanner; media additions, renames, and deletions do not update FTS.<br>• `MainViewModel` loads entire library into RAM and runs `buildAlbums()` synchronously on Main thread. |
| **Feature B: Smart Collections (12 Categories)** | **Partial** | `SmartCategoryClassifier.kt:16-318`<br>`LocalGalleryEngine.kt:1120-1230`<br>`MainScreen.kt:530-610` | • Not a multi-signal scoring system; returns single `String?` via early-return if-branches; individual category scores are never calculated or stored.<br>• No classifier version stored in `MediaItem` or Room entities.<br>• No manual user override flag (`isUserOverridden`) or persistence; re-scan overwrites manual user edits.<br>• **CRITICAL PRIVACY/LEGAL**: Aadhaar and ID numbers are NOT masked before saving to `MediaItem.ocrText` and `MediaFts.ocrText`. Full numbers stored in plaintext database.<br>• Dictionaries are English-only; Hindi keyword dictionaries are completely absent.<br>• No labeled 300-image evaluation dataset or benchmark test harness committed to repository. |
| **Feature C: Concurrent AI Orchestrator** | **Partial** | `ParallelTaskOrchestrator.kt:48-420`<br>`OrchestratorHudSheet.kt:1-216`<br>`LocalGalleryEngine.kt:704-728` | • Progress checkpoints are NOT persisted across process death; progress state is purely in-memory `MutableStateFlow`.<br>• Categorizer pipeline always re-scans from index 0 across all images on restart.<br>• HUD sheet is MISSING error counts and the "view errors" action dialog.<br>• Pipeline cancellation does not release ML Kit detectors or recycle in-flight bitmaps.<br>• `mobilefacenet.tflite` model license is not documented. |
| **Feature D: Universal Downloader** | **Partial** | `MediaDownloaderEngine.kt:45-472`<br>`DownloadWorker.kt:38-168`<br>`DownloaderScreen.kt:1-618` | • Errors are raw strings (`e.message`), NOT mapped to typed sealed `DownloadError` (Network, Unavailable, FormatNotFound, UnsupportedSite, StorageFull, Cancelled).<br>• No 1-tap retry mechanism for retryable failures.<br>• Compliance gate: No legal notice / terms of service disclaimer in `DownloaderScreen.kt` or `README.md`.<br>• GitHub-only distribution notice missing from `README.md`. |
| **Feature E: Private Vault** | **Broken / Insecure** | `VaultAndFileManager.kt:20-360`<br>`VaultScreen.kt:63-260`<br>`SettingsRepository.kt:105-181` | • **CRITICAL BACKDOOR**: `VaultScreen.kt:248` unlocks if input is `"1234"` or if `targetHash.isBlank()`. Since user PIN setup UI is missing, `targetHash` is always blank, allowing anyone to unlock the vault.<br>• Failed attempts are never incremented; 5-attempt lockout NEVER triggers.<br>• Lockout timestamp is in-memory only and lost on process restart.<br>• Decryption loads ENTIRE file into RAM `input.readBytes()` (`VaultAndFileManager.kt:186`); no `CipherDataSource` for Media3 streaming, causing OOM crashes on large media.<br>• Uses deprecated `MasterKeys` and `EncryptedFile.Builder` instead of direct hardware-backed Android Keystore.<br>• Pre-delete verification only checks file size > 0, does not decrypt and verify hash before deleting public original. |
| **Feature F: Cinema Player** | **Partial** | `SmartVideoPlayerScreen.kt:416, 728-945`<br>`SmartVideoPlayerViewModel.kt:390-650`<br>`PlaybackService.kt:65-180`<br>`AndroidManifest.xml:72-102` | • **ARCHITECTURAL DISCONNECT**: `SmartVideoPlayerScreen` instantiates a standalone local `ExoPlayer`, while `PlaybackService` creates its own player and effects; Equalizer & Volume Boost in UI control `PlaybackService`, having ZERO effect on the video player surface.<br>• **PiP DISABLED**: Manifest `<activity>` is MISSING `android:supportsPictureInPicture="true"`, causing `enterPictureInPictureMode()` to fail on standard devices.<br>• **LibVLC Missing**: LibVLC 3.7.5 dependency is in `build.gradle.kts`, but 0 lines of code reference or implement LibVLC fallback.<br>• **Scoped Storage Violation in GIF Maker**: `SmartVideoPlayerViewModel.kt:604-608` calls `File.mkdirs()` in public `Pictures` directory using `ProcessBuilder`, causing permission denied crashes on Android 11+. |
| **Feature G: Dependency & Build Integration** | **Partial** | `app/build.gradle.kts:85-96`<br>`app/proguard-rules.pro:1-59`<br>Repository root | • Missing Gradle `verifyRelease` checksum verification task.<br>• Missing GitHub Actions CI workflow (`.github/workflows` does not exist).<br>• Release buildType hardcodes debug signing config (`signingConfig = signingConfigs.getByName("debug")`).<br>• No automated script or task syncing build checksums into `README.md`. |

---

## 3. Architecture & Code Quality Review

### 3.1 Layering & Architecture Pattern
- **Current State:** The application claims a clean MVI/MVVM Clean Architecture, but layers are heavily blurred:
  - `MainScreen.kt` contains 2,548 lines of UI, business logic, JSON parsing, and MediaStore triggers.
  - `SmartVideoPlayerScreen.kt` contains 3,283 lines with inline dialogs, bottom sheets, canvas math, and state mutation.
  - There is no domain layer: no UseCase or Interactor classes exist. ViewModels communicate directly with Room DAOs and monolithic Engine singletons (`LocalGalleryEngine`, `VaultAndFileManager`).
  - No unified `UiState` with `Loading`, `Success`, `Error`, `Empty` sealed hierarchies. ViewModels expose numerous disconnected `MutableStateFlow`s.

### 3.2 Dependency Injection (Dagger Hilt)
- **Status:** Functional for database and singletons, but has anti-patterns:
  - `SmartGalleryApp` manually manages worker factories and engine initializers.
  - `SmartVideoPlayerScreen` uses both `hiltViewModel()` and `@Composable remember` for components that should be injected or tied to ViewModel scope.

### 3.3 Threading & Coroutine Safety
- **Defect:** In `MainViewModel.kt:118-121`, `db.mediaItemDao().getAll().collectLatest { items -> buildAlbums(items) }` executes album aggregation and sorting on `Dispatchers.Main.immediate`. In libraries with 10,000+ items, this drops frames and causes ANRs.
- **Defect:** `LocalGalleryEngine.kt:159-224` reads cursors in a tight loop and constructs Room entity objects synchronously without chunking.

### 3.4 Memory Leaks & Resource Management
- **Bitmap Recycling:** In `LocalGalleryEngine.kt` and `PhotoEditorScreen.kt`, multiple intermediate bitmaps are allocated during facial alignment, Laplacian variance, and drawing without explicit `recycle()` calls, leading to high garbage collection pressure.
- **Player Lifecycle:** `SmartVideoPlayerScreen.kt` creates a local `ExoPlayer` in `remember`. While `DisposableEffect` calls `release()`, if the screen is recomposed rapidly or orientation transitions glitch, multiple audio sessions can briefly leak.
- **Vault In-Memory Loading:** `VaultAndFileManager.loadDecryptedBytes()` calls `input.readBytes()`. For a 500 MB video, this immediately allocates a 500 MB byte array on the Java heap, triggering an instant `OutOfMemoryError`.

---

## 4. Security & Privacy Review

### 4.1 Key Storage & Cryptographic Primitives
- **MasterKey Deprecation:** The vault relies on `MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)` (`VaultAndFileManager.kt:54`), which is deprecated in `androidx.security:security-crypto:1.1.0-alpha06`.
- **Hardware-Backed Keystore:** The current implementation does not configure `KeyGenParameterSpec.Builder` with `setIsStrongBoxBacked(true)` or `setUserAuthenticationRequired(true)`.
- **IV Generation:** Handled by `EncryptedFile` (AES-256-GCM with HKDF-4KB), but custom streaming or chunking is absent.

### 4.2 Plaintext Leakage & Temporary Files
- **Aadhaar / PII Leakage:** ML Kit OCR scans documents and extracts full Aadhaar numbers (12-digit Indian national UID), PAN numbers, and passport numbers. These are stored in plaintext in the SQLite database (`media_items.ocrText` and `media_fts.ocrText`). This violates the Aadhaar Act and privacy compliance.
- **Logging Sensitive Filenames:** In `VaultAndFileManager.kt:162`, `Log.i(TAG, "Finalized vault migration for #${item.id} (${staged.vaultFile.name})")` logs the physical vault filename to system logcat.
- **Thumbnail Leakage:** Thumbnails generated for vault items (`$uuid.thumb`) are encrypted, but if thumbnail generation fails or during video frame retrieval via `MediaMetadataRetriever`, temporary frames may remain in system bitmap cache.

### 4.3 Authentication & Biometric Security
- **Hardcoded Backdoor:** `VaultScreen.kt:248` explicitly contains:
  ```kotlin
  if (targetHash.isBlank() || input == targetHash || input == "1234") {
      viewModel.isUnlocked = true
  }
  ```
  This is a critical security vulnerability that renders the private vault completely accessible to any unauthorized user.
- **Missing Lockout Enforcement:** Failed PIN attempts are never incremented or saved. The 5-attempt / 30-second lockout is non-functional.

---

## 5. Permissions Review

| Permission in Manifest | Declared | Actually Used? | Assessment / Remediation |
| :--- | :---: | :---: | :--- |
| `READ_MEDIA_IMAGES` (API 33+) | Yes | Yes | Correctly requested in `MainActivity` and `MainViewModel`. |
| `READ_MEDIA_VIDEO` (API 33+) | Yes | Yes | Correctly requested. |
| `READ_MEDIA_AUDIO` (API 33+) | Yes | Yes | Correctly requested. |
| `READ_MEDIA_VISUAL_USER_SELECTED` (API 34+) | Yes | Yes | Handled in Android 14 partial access flow. |
| `READ_EXTERNAL_STORAGE` (maxSdk 32) | Yes | Yes | Correctly restricted to API <= 32. |
| `WRITE_EXTERNAL_STORAGE` (maxSdk 28) | Yes | Yes | Correctly restricted to API <= 28. |
| `MANAGE_MEDIA` | Yes | **No** | Declared in manifest line 26, but never requested via `MediaStore.createManageMediaIntent()` or checked at runtime. Unused over-broad permission. |
| `INTERNET` | Yes | Yes | Used for Downloader. |
| `ACCESS_NETWORK_STATE` | Yes | Yes | Used for connectivity checks. |
| `FOREGROUND_SERVICE` | Yes | Yes | Used for Playback and Downloader. |
| `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | Yes | Yes | Bound to `PlaybackService`. |
| `FOREGROUND_SERVICE_DATA_SYNC` | Yes | Yes | Bound to `DownloadWorker`. |
| `WAKE_LOCK` | Yes | Yes | ExoPlayer and DownloadWorker. |
| `RECEIVE_BOOT_COMPLETED` | Yes | **No** | Declared in line 40, but zero `<receiver>` components exist in manifest. Unused permission. |
| `USE_BIOMETRIC` | Yes | Yes | Used in `VaultScreen`. |
| `USE_FINGERPRINT` | Yes | **No** | Deprecated in API 28. Redundant with `USE_BIOMETRIC`. Should be removed. |
| `MODIFY_AUDIO_SETTINGS` | Yes | Yes | Equalizer & volume control. |
| `SET_WALLPAPER` | Yes | **No** | Declared in line 44, never invoked in code. Unused permission. |
| `VIBRATE` | Yes | Yes | Haptic feedback on scrub/gestures. |
| `POST_NOTIFICATIONS` (API 33+) | Yes | Yes | Downloader and playback notifications. |

---

## 6. Test Coverage Assessment

- **Current Unit Tests:** **0** (Directory `app/src/test` does not exist).
- **Current Instrumented Tests:** **0** (Directory `app/src/androidTest` does not exist).
- **Untested Core Modules:**
  1. `SmartCategoryClassifier`: Zero tests for 12 categories, multi-signal scoring, or keyword matching.
  2. `VaultAndFileManager`: Zero tests for AES-256-GCM encryption, decryption, tamper detection, or hash verification.
  3. `LocalGalleryEngine`: Zero tests for xxHash64, pHash duplicate matching, or Laplacian blur calculation.
  4. `MediaDownloaderEngine`: Zero tests for format fallback chains, Aria2c parsing, or Scoped Storage staging.
  5. `GalleryDatabase`: Zero Room migration tests or FTS4 query verification tests.

---

## 7. Top 15 Defects Ranked by Severity

| Rank | Severity | Subsystem | File & Line | Defect Description |
| :---: | :---: | :---: | :--- | :--- |
| **1** | **CRITICAL** | Security / Vault | `VaultScreen.kt:248` | **Hardcoded PIN Backdoor & Blank Bypass:** `input == "1234"` or `targetHash.isBlank()` immediately unlocks private vault. |
| **2** | **CRITICAL** | Security / Vault | `VaultScreen.kt:83-84, 243-255` | **Lockout Non-Functional:** Failed attempts are never incremented, and lockout timestamp is not persisted; brute-force attacks are unrestricted. |
| **3** | **HIGH** | Security / Privacy | `LocalGalleryEngine.kt:1134`<br>`SmartCategoryClassifier.kt:269` | **Unmasked Aadhaar / PII Leakage:** Extracted Aadhaar (12 digits), PAN, and passport numbers are stored unmasked in plaintext database, violating the Aadhaar Act. |
| **4** | **HIGH** | Crash / Memory | `VaultAndFileManager.kt:186` | **OOM Crash on Decryption:** `input.readBytes()` buffers the entire encrypted file in Java heap; decrypting videos > 200 MB causes immediate app crash. |
| **5** | **HIGH** | Correctness / Audio | `SmartVideoPlayerScreen.kt:416`<br>`PlaybackService.kt:65, 178` | **Player Equalizer Disconnect:** `SmartVideoPlayerScreen` plays audio via an independent local `ExoPlayer` instance, while Equalizer & Volume Boost operate on `PlaybackService`'s unused audio session. |
| **6** | **HIGH** | Crash / Scoped Storage | `SmartVideoPlayerViewModel.kt:604-608`<br>`VideoToolsEngine.kt:37, 103` | **Scoped Storage Violation in GIF & Video Tools:** Direct `File.mkdirs()` on public external storage without MediaStore `insert` fails with `Permission Denied` on Android 11+. |
| **7** | **HIGH** | Stability / Manifest | `AndroidManifest.xml:72-102` | **PiP Crash / Non-Functional:** `MainActivity` lacks `android:supportsPictureInPicture="true"`, causing `enterPictureInPictureMode()` to fail or crash. |
| **8** | **MEDIUM** | Code Completeness | `SmartVideoPlayerScreen.kt`<br>`app/build.gradle.kts:180` | **Missing LibVLC Fallback:** `libvlc-all:3.7.5` dependency is packaged in APK (~30MB), but zero fallback logic exists in code when ExoPlayer fails. |
| **9** | **MEDIUM** | Performance / ANR | `LocalGalleryEngine.kt:137-160`<br>`MainViewModel.kt:118-121` | **No MediaStore / Room Paging:** Loads all library items into memory at once and executes album grouping on the Main thread. |
| **10** | **MEDIUM** | Correctness / AI | `SmartCategoryClassifier.kt:180-317` | **Missing Multi-Signal Scoring & Versioning:** Classifier returns a single string with early exits instead of computing and persisting multi-signal scores per category. |
| **11** | **MEDIUM** | Correctness / Search | `GalleryDatabase.kt:233-245`<br>`LocalGalleryEngine.kt:1135` | **FTS4 Incomplete & Out-of-Sync:** `media_fts` does not index detected image labels and is only written during OCR, missing new, renamed, or deleted media. |
| **12** | **MEDIUM** | Resilience / AI | `ParallelTaskOrchestrator.kt:50, 97-235` | **Unpersisted Orchestrator Checkpoints:** Killing the app resets progress to 0; categorizer re-scans the entire library from scratch on restart. |
| **13** | **MEDIUM** | Correctness / UI | `OrchestratorHudSheet.kt:1-216` | **Missing HUD Error Dialog:** Orchestrator HUD lacks error count indicators and "view errors" inspection action. |
| **14** | **MEDIUM** | Resilience / Downloader | `DownloadWorker.kt:114-122`<br>`DownloaderScreen.kt:1-618` | **Unstructured Downloader Errors & No Retry:** Errors are unmapped raw strings; lacks typed error mapping and 1-tap retry UI. |
| **15** | **LOW** | Build / Quality | Root & `app/build.gradle.kts` | **Missing CI & Release Verification:** Zero automated tests, no GitHub Actions CI workflow, and missing Gradle `verifyRelease` checksum verification task. |

---

## 8. Audit Conclusion & Next Steps

Phase 0 audit is complete and written to `docs/AUDIT.md`. In accordance with ground rules, **Phase 1 (Defect Repair & Subsystem Hardening)** will commence only after review, proceeding in the exact required order:
1. Fix Critical & High defects (Backdoor removal, Aadhaar masking, hardware Keystore migration, streaming CipherDataSource, PiP manifest fix, Equalizer binding).
2. Feature completion: Library Paging & FTS → Vault Hardening → Collections Multi-Signal Scoring → Cinema Player & LibVLC → Downloader Typed Errors → Build & CI.

