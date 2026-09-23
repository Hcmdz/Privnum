package com.hcmdz.privnum.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.MessageDigest
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

enum class AutoLockTimeout(val minutes: Long) {
    DISABLED(-1),
    IMMEDIATELY(0),
    MIN_1(1),
    MIN_5(5),
    HOUR_1(60),
    HOUR_5(300)
}

@Singleton
class PasscodeStore @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "passcode_store",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    var passcodeEnabled: Boolean
        get() = prefs.getBoolean("enabled", false)
        set(value) = prefs.edit().putBoolean("enabled", value).apply()

    var biometricEnabled: Boolean
        get() = prefs.getBoolean("biometric", false)
        set(value) = prefs.edit().putBoolean("biometric", value).apply()

    var autoLockTimeout: AutoLockTimeout
        get() = runCatching {
            AutoLockTimeout.valueOf(prefs.getString("auto_lock", "MIN_5")!!)
        }.getOrDefault(AutoLockTimeout.MIN_5)
        set(value) = prefs.edit().putString("auto_lock", value.name).apply()

    var lastUnlockedAt: Long
        get() = prefs.getLong("last_unlocked", 0L)
        set(value) = prefs.edit().putLong("last_unlocked", value).apply()

    var failedAttempts: Int
        get() = prefs.getInt("failed", 0)
        set(value) = prefs.edit().putInt("failed", value).apply()

    fun setPin(pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        prefs.edit()
            .putString("salt", salt.toHex())
            .putString("hash", sha256(salt + pin.toByteArray()))
            .apply()
    }

    fun verifyPin(pin: String): Boolean {
        val saltHex = prefs.getString("salt", null) ?: return false
        val expected = prefs.getString("hash", null) ?: return false
        return sha256(saltHex.hexToBytes() + pin.toByteArray()) == expected
    }

    fun hasPin(): Boolean = prefs.contains("hash")

    fun clear() {
        prefs.edit().clear().apply()
        passcodeEnabled = false
        biometricEnabled = false
    }

    fun shouldLock(now: Long = System.currentTimeMillis()): Boolean {
        if (!passcodeEnabled) return false
        return when (val timeout = autoLockTimeout) {
            AutoLockTimeout.DISABLED -> false
            AutoLockTimeout.IMMEDIATELY -> true
            else -> now - lastUnlockedAt > timeout.minutes * 60_000L
        }
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).toHex()

    private fun ByteArray.toHex(): String =
        joinToString("") { "%02x".format(it) }

    private fun String.hexToBytes(): ByteArray =
        chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}

private operator fun ByteArray.plus(other: ByteArray): ByteArray {
    val result = ByteArray(size + other.size)
    copyInto(result)
    other.copyInto(result, size)
    return result
}
