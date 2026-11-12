package com.example.smartgallery.profiler

import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

enum class DeviceTier {
    TIER_LOW,
    TIER_MID,
    TIER_HIGH
}

@Singleton
class DeviceCapabilityProfiler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "DeviceProfiler"
    }

    val totalRamGb: Float
    val cpuCores: Int
    val isLowRamDevice: Boolean
    val deviceTier: DeviceTier

    private val _isThermalThrottling = MutableStateFlow(false)
    val isThermalThrottling: StateFlow<Boolean> = _isThermalThrottling.asStateFlow()

    private val _isBatteryLowNotCharging = MutableStateFlow(false)
    val isBatteryLowNotCharging: StateFlow<Boolean> = _isBatteryLowNotCharging.asStateFlow()

    private val _shouldThrottleBackgroundTasks = MutableStateFlow(false)
    val shouldThrottleBackgroundTasks: StateFlow<Boolean> = _shouldThrottleBackgroundTasks.asStateFlow()

    init {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)

        totalRamGb = memInfo.totalMem / (1024f * 1024f * 1024f)
        cpuCores = Runtime.getRuntime().availableProcessors()
        isLowRamDevice = activityManager.isLowRamDevice

        deviceTier = when {
            isLowRamDevice || totalRamGb <= 4.1f || cpuCores <= 4 -> DeviceTier.TIER_LOW
            totalRamGb <= 8.1f -> DeviceTier.TIER_MID
            else -> if (cpuCores >= 8) DeviceTier.TIER_HIGH else DeviceTier.TIER_MID
        }

        Log.i(
            TAG,
            "Hardware Profile initialized -> Tier: $deviceTier (RAM: %.2f GB, Cores: %d, LowRamFlag: %b)".format(
                totalRamGb, cpuCores, isLowRamDevice
            )
        )

        initThermalListener()
        initBatteryObserver()
    }

    // ─── Thermal Awareness ─────────────────────────────────────────────────
    private fun initThermalListener() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            powerManager?.let { pm ->
                try {
                    pm.addThermalStatusListener { status ->
                        val isThrottling = status >= PowerManager.THERMAL_STATUS_MODERATE
                        _isThermalThrottling.value = isThrottling
                        updateThrottleDecision()
                        Log.w(TAG, "Thermal status changed: $status (throttling: $isThrottling)")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to register thermal status listener: ${e.message}")
                }
            }
        }
    }

    // ─── Battery Awareness ─────────────────────────────────────────────────
    private fun initBatteryObserver() {
        try {
            val batteryFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val currentBattery = context.registerReceiver(null, batteryFilter)
            currentBattery?.let { inspectBatteryIntent(it) }

            val receiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context?, intent: Intent?) {
                    intent?.let { inspectBatteryIntent(it) }
                }
            }
            context.registerReceiver(receiver, batteryFilter)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to register battery observer: ${e.message}")
        }
    }

    private fun inspectBatteryIntent(intent: Intent) {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL

        val percent = if (scale > 0) (level * 100f / scale) else 100f
        val isCritical = percent < 15f && !isCharging

        if (_isBatteryLowNotCharging.value != isCritical) {
            _isBatteryLowNotCharging.value = isCritical
            updateThrottleDecision()
            Log.d(TAG, "Battery status update: %.1f%% (charging: %b, critical: %b)".format(percent, isCharging, isCritical))
        }
    }

    private fun updateThrottleDecision() {
        _shouldThrottleBackgroundTasks.value = _isThermalThrottling.value || _isBatteryLowNotCharging.value
    }

    // ─── Dynamic Adaptive Tier Parameters ─────────────────────────────────

    val targetGridThumbnailSize: Int
        get() = when (deviceTier) {
            DeviceTier.TIER_LOW -> 240
            DeviceTier.TIER_MID -> 320
            DeviceTier.TIER_HIGH -> 400
        }

    val preferredBitmapConfig: Bitmap.Config
        get() = when (deviceTier) {
            DeviceTier.TIER_LOW, DeviceTier.TIER_MID -> Bitmap.Config.RGB_565
            DeviceTier.TIER_HIGH -> Bitmap.Config.ARGB_8888
        }

    val crossfadeDurationMs: Int
        get() = when (deviceTier) {
            DeviceTier.TIER_LOW -> 0
            DeviceTier.TIER_MID -> 100
            DeviceTier.TIER_HIGH -> 150
        }

    val memoryCachePercent: Double
        get() = when (deviceTier) {
            DeviceTier.TIER_LOW -> 0.12
            DeviceTier.TIER_MID -> 0.18
            DeviceTier.TIER_HIGH -> 0.25
        }

    val diskCacheMaxBytes: Long
        get() = when (deviceTier) {
            DeviceTier.TIER_LOW -> 120L * 1024 * 1024
            DeviceTier.TIER_MID -> 200L * 1024 * 1024
            DeviceTier.TIER_HIGH -> 300L * 1024 * 1024
        }

    val useHardwareBlur: Boolean
        get() = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) && (deviceTier != DeviceTier.TIER_LOW)

    val maxAria2cConnections: Int
        get() = when (deviceTier) {
            DeviceTier.TIER_LOW -> 6
            DeviceTier.TIER_MID, DeviceTier.TIER_HIGH -> 16
        }

    val aiWorkerBatchSize: Int
        get() = if (shouldThrottleBackgroundTasks.value || deviceTier == DeviceTier.TIER_LOW) 25 else 100

    val aiWorkerParallelism: Int
        get() = if (shouldThrottleBackgroundTasks.value || deviceTier == DeviceTier.TIER_LOW) 1 else 2

    val aiWorkerYieldInterval: Int
        get() = if (shouldThrottleBackgroundTasks.value || deviceTier == DeviceTier.TIER_LOW) 5 else 15

    val aiBreathingDelayMs: Long
        get() = if (shouldThrottleBackgroundTasks.value) 150L else 0L

    val exoPlayerMinBufferMs: Int
        get() = if (deviceTier == DeviceTier.TIER_LOW) 8_000 else 15_000

    val exoPlayerMaxBufferMs: Int
        get() = if (deviceTier == DeviceTier.TIER_LOW) 20_000 else 50_000

    val exoPlayerBufferForPlaybackMs: Int
        get() = 1_000

    val exoPlayerBufferForPlaybackAfterRebufferMs: Int
        get() = if (deviceTier == DeviceTier.TIER_LOW) 1_500 else 2_000
}

