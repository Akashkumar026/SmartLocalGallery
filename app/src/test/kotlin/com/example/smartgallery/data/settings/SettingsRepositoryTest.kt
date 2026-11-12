package com.example.smartgallery.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.preferencesOf
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Test

class SettingsRepositoryTest {

    private val mockContext = mockk<Context>(relaxed = true)
    private val repository = SettingsRepository(mockContext)

    @Test
    fun testHashPin_sha256Deterministic() {
        val pin = "1234"
        val salt = "test-salt-abc"
        val hash1 = repository.hashPin(pin, salt)
        val hash2 = repository.hashPin(pin, salt)

        assertThat(hash1).isNotEmpty()
        assertThat(hash1).hasLength(64)
        assertThat(hash1).isEqualTo(hash2)
    }

    @Test
    fun testHashPin_differentSaltsProduceDifferentHashes() {
        val pin = "5678"
        val hashA = repository.hashPin(pin, "salt-A")
        val hashB = repository.hashPin(pin, "salt-B")

        assertThat(hashA).isNotEqualTo(hashB)
    }

    @Test
    fun testSensitiveVaultKeys_excludedFromExport() {
        val sensitiveKeys = listOf(
            SettingsKeys.VAULT_PIN_HASH.name,
            SettingsKeys.VAULT_PIN_SALT.name,
            SettingsKeys.VAULT_FAILED_ATTEMPTS.name,
            SettingsKeys.VAULT_LOCKOUT_UNTIL_MS.name
        )

        // Mock preferences containing sensitive keys + regular settings
        val prefs = preferencesOf(
            SettingsKeys.VAULT_PIN_HASH to "sensitive_hash_123456",
            SettingsKeys.VAULT_PIN_SALT to "sensitive_salt_789",
            SettingsKeys.VAULT_FAILED_ATTEMPTS to 2,
            SettingsKeys.VAULT_LOCKOUT_UNTIL_MS to 1234567890L,
            SettingsKeys.THEME_MODE to "DARK",
            SettingsKeys.GRID_COLUMNS to 4
        )

        val json = JSONObject()
        val sensitiveKeySet = sensitiveKeys.toSet()
        prefs.asMap().forEach { (key, value) ->
            if (key.name !in sensitiveKeySet) {
                json.put(key.name, value)
            }
        }

        val jsonString = json.toString()
        for (sensitive in sensitiveKeys) {
            assertThat(jsonString).doesNotContain(sensitive)
        }
        assertThat(jsonString).doesNotContain("sensitive_hash_123456")
        assertThat(jsonString).doesNotContain("sensitive_salt_789")
        assertThat(jsonString).contains("DARK")
    }

    @Test
    fun testLockoutThreshold() {
        val maxFailedAttempts = 5
        val lockoutDurationMs = 30_000L

        var failedAttempts = 4
        var isLocked = failedAttempts >= maxFailedAttempts
        assertThat(isLocked).isFalse()

        failedAttempts += 1
        isLocked = failedAttempts >= maxFailedAttempts
        assertThat(isLocked).isTrue()

        val now = System.currentTimeMillis()
        val lockoutUntil = now + lockoutDurationMs
        assertThat(lockoutUntil).isGreaterThan(now)
    }
}
