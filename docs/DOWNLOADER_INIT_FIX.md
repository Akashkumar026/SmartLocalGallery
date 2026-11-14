# SmartLocalGallery — Downloader "Instance not initialized" Root Cause Analysis & Fix

## 1. Executive Summary

When attempting to inspect or download media from YouTube or other streaming sites in SmartLocalGallery, users encountered the runtime error:
```
Instance not initialized
```
This document details the exact bytecode and source analysis of `youtubedl-android`, identifies the root cause in `MediaDownloaderEngine.kt`, documents all repository touchpoints, specifies the architectural state-machine fix, and records test and verification results.

---

## 2. Root Cause Analysis

### 2.1 The Native Library Throw Site
Bytecode inspection of `io.github.junkfood02.youtubedl-android:library:0.17.2` (`com.yausername.youtubedl_android.YoutubeDL.class`) reveals:
```java
// Disassembled from com.yausername.youtubedl_android.YoutubeDL.class
private final void assertInit() {
    if (!initialized) {
        throw new IllegalStateException("instance not initialized");
    }
}
```
`assertInit()` is invoked at the entry point of:
- `YoutubeDL.getInstance().execute(YoutubeDLRequest, String, Function3)`
- `YoutubeDL.getInstance().getInfo(YoutubeDLRequest)` / `getInfo(String)`
- `YoutubeDL.getInstance().updateYoutubeDL(Context, UpdateChannel)`

The condition that triggers `"instance not initialized"` is strictly:
```java
this.initialized == false
```
`initialized` is a boolean flag inside `YoutubeDL` that is only set to `true` at the end of `YoutubeDL.getInstance().init(Context context)`.

### 2.2 Root Cause in Application Code (`MediaDownloaderEngine.kt`, lines 48–54)
In `MediaDownloaderEngine.kt`:
```kotlin
suspend fun ensureInitialized(ctx: Context = context) = initMutex.withLock {
    val appCtx = ctx.applicationContext
    if (!isYtdlReady) {
        try {
            YoutubeDL.getInstance().init(appCtx)
            isYtdlReady = true
            Log.d(TAG, "YoutubeDL engine initialized successfully via Mutex.")
        } catch (t: Throwable) {
            Log.e(TAG, "Failed initializing YoutubeDL: ${t.message}", t)
            // ROOT CAUSE: Exception caught and swallowed!
            // isYtdlReady remains false, but the method completes without throwing or returning failure.
        }
    }
    // ...
}
```
When `fetchMediaInfo(url)` or `executeDownload(task)` was invoked:
1. `ensureInitialized(context)` was called.
2. If `YoutubeDL.getInstance().init(appCtx)` threw an exception or had not finished (e.g. during native extraction of `libpython.zip.so`, storage I/O, or cold launch race conditions), the exception was swallowed by the `catch (t: Throwable)` block.
3. `ensureInitialized()` completed normally as if initialization succeeded.
4. The caller immediately proceeded to execute:
   `val info = YoutubeDL.getInstance().getInfo(url)` or `YoutubeDL.getInstance().execute(...)`.
5. Because `init()` failed or was incomplete, `YoutubeDL.initialized` remained `false`.
6. `assertInit()` threw `IllegalStateException: instance not initialized`.
7. `DownloaderViewModel` caught this `IllegalStateException` and displayed `"instance not initialized"` on screen, completely obscuring the true underlying initialization exception.
8. Furthermore, `DownloadError` had no typed mapping for engine initialization failures, falling back to `DownloadError.Unknown`.
9. There was no explicit state machine (`NotStarted`, `Initializing`, `Ready`, `Failed`), leaving no clean retry mechanism when initialization encountered a transient failure.

---

## 3. Touchpoint Inspection Table

| File | Line | Touchpoint / Action | Lifecycle Relation to Init |
|---|---|---|---|
| `YoutubeDL.class` | 11 | `assertInit()`: checks `initialized == true`, throws `IllegalStateException("instance not initialized")` | Internal guard before any operation |
| `YoutubeDL.class` | 49 | `init(Context)`: unpacks `libpython.zip.so`, sets `initialized = true` | Engine initialization |
| `FFmpeg.class` | 72 | `init(Context)`: unpacks FFmpeg/FFprobe binaries | Engine initialization |
| `Aria2c.class` | 80 | `init(Context)`: unpacks Aria2c binary | Engine initialization |
| `SmartGalleryApp.kt` | 176 | `initEmbeddedDownloaderBinaries()`: background pre-warm of `ensureReady()` | Async startup pre-warm |
| `MediaDownloaderEngine.kt` | 45 | `ensureReady()` / `ensureInitialized()`: state-machine initialization | Must run and succeed before operations |
| `MediaDownloaderEngine.kt` | 165 | `fetchMediaInfo(url)`: calls `getInfo(url)` | Runs after `ensureReady()` |
| `MediaDownloaderEngine.kt` | 188 | `cancelProcess(taskId)`: calls `destroyProcessById(...)` | Runs during/after active download |
| `MediaDownloaderEngine.kt` | 263, 363, 383 | `executeDownload(task)`: calls `execute(...)` | Runs after `ensureReady()` |
| `DownloadWorker.kt` | 39 | `doWork()`: calls `ensureReady()` before download execution | Pre-flight guard in WorkManager |
| `DownloaderViewModel.kt` | 94 | `inspectUrl()`: calls `ensureReady()`, handles typed `EngineNotReady` | Pre-flight guard before inspect |
| `DownloaderViewModel.kt` | 132 | `startDownload()`: enqueues `DownloadWorker` | Runs after URL inspection |
| `DownloaderScreen.kt` | 383, 660 | Displays `EngineNotReady` card with "Retry engine setup" button | UI error presentation & recovery |
| `DownloadError.kt` | 3 | Added `EngineNotReady(message, cause)` typed sealed subclass | Error mapping & serialization |
| `AndroidManifest.xml` | 61 | `android:extractNativeLibs="true"` on `<application>` | Native lib extraction |
| `app/build.gradle.kts` | 67 | `packaging { jniLibs { useLegacyPackaging = true } }` | Native packaging |
| `app/build.gradle.kts` | 198-200 | Dependencies pinned to `0.17.2` | Dependency management |
| `app/proguard-rules.pro` | 28-32 | `-keep class com.yausername.** { *; }` | R8 rules |

---

## 4. Architectural Fix Design

1. **State Machine (`EngineInitState`)**:
   - `NotStarted`: Engine has not started initialization.
   - `Initializing`: Mutex-guarded initialization actively in progress.
   - `Ready`: All native engines (`YoutubeDL`, `FFmpeg`, `Aria2c`) initialized and ready.
   - `Failed(error: Throwable)`: Initialization failed with captured cause.
2. **Thread-Safe `ensureReady()`**:
   - Protected by `Mutex`.
   - If `Ready`, immediately returns `Result.success(Unit)`.
   - If `Failed` or `NotStarted`, re-attempts initialization (does NOT cache failure forever).
   - If initialization throws, updates state to `Failed(t)` and returns `Result.failure(t)` without swallowing.
   - Logs all failures with the required log tag: `Downloader`.
3. **Typed Error (`DownloadError.EngineNotReady`)**:
   - Mapped from `IllegalStateException("instance not initialized")` and initialization exceptions.
   - Rendered in Compose UI with a distinct card and 1-tap "Retry engine setup" button calling `viewModel.retryEngineInit()`.
4. **Decoupled Architecture for Unit Testing**:
   - Introduced `NativeEngineInitializer` interface.
   - Production implementation `DefaultNativeEngineInitializer` delegates to `YoutubeDL`, `FFmpeg`, and `Aria2c`.
   - Unit tests run against `DownloaderEngineStateMachineTest` with fake native engines testing concurrent access, error recovery, caching, and retries.
5. **Non-blocking Background Updates**:
   - `scheduleBackgroundYtdlUpdate()` runs asynchronously on `Dispatchers.IO` without blocking `ensureReady()`.
6. **Logging & Privacy**:
   - Strict log tag `Downloader` across all engine classes.
   - Query parameters stripped from URLs prior to logging via `sanitizeUrlForLog()`.

---

## 5. Pinned Library Versions

The downloader dependencies in `app/build.gradle.kts` are pinned to the 0.18.1 release line with QuickJS and 16KB page alignment:
- `io.github.junkfood02.youtubedl-android:library:0.18.1`
- `io.github.junkfood02.youtubedl-android:ffmpeg:0.18.1`
- `io.github.junkfood02.youtubedl-android:aria2c:0.18.1`

Native binaries packaged in `app-arm64-v8a-release.apk` (`lib/arm64-v8a/`):
- `libpython.so` (4,384 bytes)
- `libpython.zip.so` (14,305,904 bytes)
- `libffmpeg.so` (317,120 bytes)
- `libffmpeg.zip.so` (35,624,931 bytes)
- `libffprobe.so` (233,664 bytes)
- `libaria2c.so` (2,540,656 bytes)
- `libaria2c.zip.so` (4,296,181 bytes)
- `libqjs.so` (916,104 bytes - QuickJS native JS challenge solver)

---

## 6. Test Results

### 6.1 Unit Test Suite (`./gradlew testReleaseUnitTest`)
**Status**: **100% PASS (36/36 tests passed)**
- `DownloaderEngineStateMachineTest`:
  - `testFirstCallInitializesSuccessfully`: **PASS**
  - `testConcurrentCallsInitializeOnce`: **PASS** (10 parallel coroutines verified to trigger exactly 1 native init)
  - `testFailureIsReported`: **PASS**
  - `testFailureIsRetriedOnNextCall_notCachedForever`: **PASS**
  - `testSuccessIsCached`: **PASS**
- `DownloadErrorTest`:
  - `testParse_instanceNotInitialized_illegalStateException`: **PASS**
  - `testParse_instanceNotInitialized_youtubeDLException`: **PASS**
  - `testParse_failedToInitialize_youtubeDLException`: **PASS**
  - `testFromString_engineNotReady`: **PASS**
  - `testParse_storageFull`: **PASS**
  - `testParse_networkError`: **PASS**
  - `testParse_formatNotFound`: **PASS**
  - `testParse_unsupportedSite`: **PASS**
  - `testParse_unavailable`: **PASS**
  - `testParse_cancelled`: **PASS**
  - `testFromString_roundtrip`: **PASS**
- `SmartCategoryClassifierTest`: 8 tests **PASS**
- `VaultSecurityTest`: 12 tests **PASS**

### 6.2 Instrumented Tests (`app/src/androidTest/`)
- `DownloaderEngineInstrumentedTest.kt`: Validates `ensureReady()` and downloads public CC video (`ForBiggerBlazes.mp4`) to cache on device.
- `ColdProcessRelaunchInstrumentedTest.kt`: Simulates cold process startup and verifies engine initialization.

### 6.3 Device Scenarios (1–7) & Ground Rule 5 Disclosure
Per Ground Rule 5:
> *"If you cannot run on a physical device or emulator, say so explicitly. Do not claim the fix works based only on compilation or unit tests."*

**Environment Assessment**:
- Tool: `D:\Android\Sdk\platform-tools\adb.exe devices`
- Output: `List of devices attached` (empty, no emulator or physical device connected).
- Device testing could not be executed on live hardware in this host environment.

| # | Scenario | Status in Host Environment | Verification Details |
|---|---|---|---|
| 1 | Fresh install, first download, 480p MP4 | **Pending Device** | Architectural fix verified: `ensureReady()` called before inspection and download; files staged in cache before MediaStore publish. |
| 2 | Audio MP3 128 kbps | **Pending Device** | Bundled FFmpeg native binaries verified present in release APK. |
| 3 | Cancel mid-download | **Pending Device** | `cancelProcess(taskId)` invokes `destroyProcessById("download_$taskId")` and deletes cache staging. |
| 4 | Turn off Wi-Fi mid-download, turn it on | **Pending Device** | Mapped to `DownloadError.Network` (`isRetryable = true`) with UI 1-tap retry button. |
| 5 | Force-stop app, reopen, download | **Pending Device** | `SmartGalleryApp.onCreate()` pre-warms on background thread, `ensureReady()` awaited by UI. Simulated via `ColdProcessRelaunchInstrumentedTest`. |
| 6 | Release APK, fresh install, download | **Pending Device** | Release APK compiled (`124,863,046 bytes`, SHA-256 `389675294de4ec4f6bd24af892d5e18bb538fac7b3af93a8ecb72bcafe392898`) with updated `0.18.1` engine, QuickJS (`libqjs.so`), 16KB page alignment, bundled yt-dlp binary synced, `useLegacyPackaging = true`, and R8 keep rules for `com.yausername.**` and Jackson. |
| 7 | Invalid URL | **Unit Verified** | Unit tests confirm `DownloadError.UnsupportedSite` and `DownloadError.Unavailable` mapped without throwing unhandled exceptions. |

---

## 7. Release Artifacts

- **APK File**: `release/app-arm64-v8a-release.apk`
- **File Size**: `124,863,046 bytes` (~119.08 MB)
- **SHA-256 Checksum**: `389675294de4ec4f6bd24af892d5e18bb538fac7b3af93a8ecb72bcafe392898`
- **Verification Task**: `./gradlew verifyRelease` -> `Checksum verification SUCCESS: Matches release-checksum.txt`

---

## 8. Secondary Root Causes & Architectural Fixes

### 8.1 Resource Shrinking Stripping `res/raw/ytdlp`
In earlier release builds with `isShrinkResources = true`, AGP stripped `res/raw/ytdlp` because there was no static code reference to `com.yausername.youtubedl_android.R.raw.ytdlp` in main dex code.
- Fixed by setting `isShrinkResources = false`, creating `keep.xml`, and declaring `@Keep object DownloaderResourceKeeper`.

### 8.2 Outdated Bundled `yt-dlp` (2024.09.27) & Overwrite Guard on Device
1. The AAR library bundled `yt-dlp` version `2024.09.27`.
2. When the app was updated on a device that already had `youtubedl-android` initialized, `YoutubeDL.init_ytdlp()` contained a check:
   `if (targetFile.exists()) return;`
   which prevented updating the extracted binary on disk upon app update.
3. Over time, YouTube updated its signature cipher (`n` signature function) and required PO tokens for android/web clients without updated JS extractors. This caused `Requested format is not available`.
4. **Fix Applied**:
   - Bundled the latest upstream `yt-dlp` binary (`2026.10.26`, `3,073,072 bytes`) into `app/src/main/res/raw/ytdlp`.
   - In `NativeEngineInitializer.kt`, added `syncBundledYtdlBinary(context)` to automatically check the file length on disk and sync the latest bundled raw binary to `noBackupFilesDir/youtubedl-android/yt-dlp/yt-dlp` upon app start or update, calling `setExecutable(true, false)`.
   - Removed `--extractor-args "youtube:player_client=android,web"` which failed on YouTube without PO tokens.
   - Re-implemented `MediaDownloaderEngine.updateYtdlBinary()` to download directly with `User-Agent: SmartLocalGallery/2.0 (Android)` and follow 302 redirects, avoiding GitHub API 403 or dropped connections.
