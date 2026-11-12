# ─── SmartLocalGallery Production ProGuard / R8 Rules ────────────────────────

# Keep Room Database entities and DAOs
-keep class com.example.smartgallery.data.db.** { *; }
-keep interface com.example.smartgallery.data.db.** { *; }
-dontwarn androidx.room.paging.**

# Keep DataStore & AppSettings model
-keep class com.example.smartgallery.data.settings.** { *; }
-keep class com.example.smartgallery.profiler.** { *; }

# Keep Dagger / Hilt
-keep class * extends dagger.hilt.android.HiltAndroidApp
-keep class * extends androidx.hilt.work.HiltWorker
-dontwarn com.google.errorprone.annotations.**

# Keep Media3 & Audio / Subtitle engines
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**
-keep class com.example.smartgallery.player.** { *; }

# Keep LibVLC Native JNI & wrappers
-keep class org.videolan.libvlc.** { *; }
-keep class org.videolan.medialibrary.** { *; }
-dontwarn org.videolan.libvlc.**

# Keep Embedded Downloader Engines (yt-dlp, FFmpeg, Aria2c)
-keep class com.yausername.** { *; }
-keep class com.yausername.youtubedl_android.** { *; }
-keep class com.yausername.ffmpeg.** { *; }
-keep class com.yausername.aria2c.** { *; }
-dontwarn com.yausername.**

# Keep TensorFlow Lite & LiteRT
-keep class org.tensorflow.lite.** { *; }
-keep class com.google.ai.edge.litert.** { *; }
-dontwarn org.tensorflow.lite.**
-dontwarn com.google.ai.edge.litert.**

# Keep ML Kit Face Detection, Text Recognition, Barcode Scanning
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# Keep Coil 3 Image Loaders
-keep class coil3.** { *; }
-dontwarn coil3.**
-keep class io.coil-kt.coil3.** { *; }
-dontwarn io.coil-kt.coil3.**
-dontwarn okio.**

# Keep Fast Hashing (xxHash64)
-keep class net.openhft.hashing.** { *; }
-dontwarn net.openhft.hashing.**

# Native methods retention
-keepclasseswithmembernames class * {
    native <methods>;
}

