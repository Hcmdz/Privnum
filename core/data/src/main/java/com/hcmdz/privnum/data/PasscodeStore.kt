package com.hcmdz.privnum.data

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.annotation.RequiresApi
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.InvalidKeyException
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
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
    companion object {
        const val MAX_FAILED_ATTEMPTS = 5
        const val LOCKOUT_MS = 60_000L
        private val secureRandom = SecureRandom()
        const val BIOMETRIC_KEY_ALIAS = "privnum_biometric_key"
        private const val BIOMETRIC_TOKEN_PREF = "biometric_token"
        private const val BIOMETRIC_HASH_PREF = "biometric_token_hash"
        private const val KDF_PREF = "kdf"
    }

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

    var forceLocked: Boolean
        get() = prefs.getBoolean("force_locked", false)
        set(value) = prefs.edit().putBoolean("force_locked", value).apply()

    fun lockNow() {
        forceLocked = true
    }

    var lastUnlockedAt: Long
        get() = prefs.getLong("last_unlocked", 0L)
        set(value) = prefs.edit().putLong("last_unlocked", value).apply()

    var failedAttempts: Int
        get() = prefs.getInt("failed", 0)
        set(value) = prefs.edit().putInt("failed", value).apply()

    fun setPin(pin: String) {
        val salt = ByteArray(16).also { secureRandom.nextBytes(it) }
        prefs.edit()
            .putString("salt", salt.toHex())
            .putString(KDF_PREF, PIN_KDF)
            .putString("hash", newPinHash(pin, salt))
            .apply()
        biometricEnabled = false
    }

    fun verifyPin(pin: String): Boolean {
        if (isLockedOut()) return false
        val salt = runCatching { prefs.getString("salt", null)?.hexToBytes() }.getOrNull()
            ?: return false
        val expected = prefs.getString("hash", null) ?: return false
        val matched = pinMatches(pin, salt, expected, prefs.getString(KDF_PREF, null)) {
            // Upgrade the legacy hash as soon as the owner proves the PIN, so
            // the weak scheme only ever exists on installs that predate it.
            prefs.edit()
                .putString(KDF_PREF, PIN_KDF)
                .putString("hash", newPinHash(pin, salt))
                .apply()
        }
        return if (matched) {
            failedAttempts = 0
            forceLocked = false
            prefs.edit().remove("lockout_until").apply()
            true
        } else {
            val attempts = failedAttempts + 1
            failedAttempts = attempts
            if (attempts >= MAX_FAILED_ATTEMPTS) {
                prefs.edit()
                    .putLong("lockout_until", System.currentTimeMillis() + LOCKOUT_MS)
                    .apply()
            }
            false
        }
    }

    fun hasPin(): Boolean = prefs.contains("hash")

    fun isLockedOut(now: Long = System.currentTimeMillis()): Boolean =
        now < prefs.getLong("lockout_until", 0L)

    fun lockoutRemainingMs(now: Long = System.currentTimeMillis()): Long =
        (prefs.getLong("lockout_until", 0L) - now).coerceAtLeast(0L)

    fun clear() {
        prefs.edit().clear().apply()
        deleteBiometricKey()
        passcodeEnabled = false
        biometricEnabled = false
    }

    /** Per-use biometric binding needs API 30+; below that the boolean flow applies. */
    fun isCryptoBiometricSupported(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

    fun biometricEnrolled(): Boolean = prefs.contains(BIOMETRIC_TOKEN_PREF)

    /**
     * ENCRYPT cipher for enrollment (generates the auth-bound key on first
     * use). Cipher init needs no prior auth; only doFinal() unlocks the key.
     */
    /** Fresh random bytes from the shared generator (single SecureRandom owner). */
    fun freshEnrollBytes(): ByteArray =
        ByteArray(32).also { secureRandom.nextBytes(it) }

    fun biometricCipherForEnroll(): Cipher? {
        if (!isCryptoBiometricSupported()) return null
        return try {
            val key = ensureBiometricKey() ?: return null
            Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(Cipher.ENCRYPT_MODE, key)
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * DECRYPT cipher when enrolled. A broken key (e.g. biometric enrollment
     * changed) clears the enrollment and yields null for transparent re-enroll.
     */
    fun biometricCipherForDecrypt(): Cipher? {
        if (!isCryptoBiometricSupported()) return null
        val parts = prefs.getString(BIOMETRIC_TOKEN_PREF, null)?.split(".")
            ?: return null
        if (parts.size != 2) return null
        return try {
            val key = loadBiometricKey() ?: run {
                clearBiometricEnrollment()
                return null
            }
            val iv = Base64.decode(parts[0], Base64.DEFAULT)
            Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
            }
        } catch (e: InvalidKeyException) {
            clearBiometricEnrollment()
            null
        } catch (e: Exception) {
            null
        }
    }

    /** Encrypts raw bytes with an authenticated cipher, persists IV+ciphertext+hash. */
    fun completeBiometricEnroll(cipher: Cipher, rawBytes: ByteArray): Boolean =
        try {
            val ct = cipher.doFinal(rawBytes) ?: return false
            val iv = cipher.iv ?: return false
            prefs.edit()
                .putString(
                    BIOMETRIC_TOKEN_PREF,
                    Base64.encodeToString(iv, Base64.NO_WRAP) + "." +
                        Base64.encodeToString(ct, Base64.NO_WRAP)
                )
                .putString(BIOMETRIC_HASH_PREF, sha256(rawBytes))
                .apply()
            true
        } catch (e: Exception) {
            false
        }

    /** Decrypts with an authenticated cipher and checks the token. Never throws. */
    fun verifyBiometricToken(cipher: Cipher): Boolean =
        try {
            val parts = prefs.getString(BIOMETRIC_TOKEN_PREF, null)?.split(".")
                ?: return false
            if (parts.size != 2) return false
            val expected = prefs.getString(BIOMETRIC_HASH_PREF, null) ?: return false
            val pt = cipher.doFinal(Base64.decode(parts[1], Base64.DEFAULT))
            biometricTokenMatches(pt, expected)
        } catch (e: Exception) {
            false
        }

    fun clearBiometricEnrollment() {
        prefs.edit()
            .remove(BIOMETRIC_TOKEN_PREF)
            .remove(BIOMETRIC_HASH_PREF)
            .apply()
        deleteBiometricKey()
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun ensureBiometricKey(): SecretKey? =
        try {
            loadBiometricKey() ?: KeyGenerator
                .getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
                .run {
                    init(
                        KeyGenParameterSpec.Builder(
                            BIOMETRIC_KEY_ALIAS,
                            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                        )
                            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                            .setUserAuthenticationRequired(true)
                            .setUserAuthenticationParameters(
                                0,
                                KeyProperties.AUTH_BIOMETRIC_STRONG
                            )
                            .setInvalidatedByBiometricEnrollment(true)
                            .build()
                    )
                    generateKey()
                }
        } catch (e: Exception) {
            android.util.Log.e("PasscodeStore", "ensureBiometricKey failed", e)
            null
        }

    private fun loadBiometricKey(): SecretKey? =
        try {
            val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            (ks.getEntry(BIOMETRIC_KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey
        } catch (e: Exception) {
            null
        }

    private fun deleteBiometricKey() {
        runCatching {
            val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            ks.deleteEntry(BIOMETRIC_KEY_ALIAS)
        }
    }

    fun shouldLock(now: Long = System.currentTimeMillis()): Boolean {
        if (!passcodeEnabled) return false
        if (forceLocked) return true
        return when (val timeout = autoLockTimeout) {
            AutoLockTimeout.DISABLED -> false
            AutoLockTimeout.IMMEDIATELY -> true
            else -> now - lastUnlockedAt > timeout.minutes * 60_000L
        }
    }

    private fun sha256(bytes: ByteArray): String = sha256Hex(bytes)
}

/** KDF identifier persisted next to the PIN hash. */
internal const val PIN_KDF = "pbkdf2-hmac-sha256"

/** High enough to make an offline guess of a short PIN costly. */
private const val PIN_KDF_ITERATIONS = 120_000

internal fun sha256Hex(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).toHex()

/** Salted PBKDF2 hash of [pin]. */
internal fun newPinHash(pin: String, salt: ByteArray): String =
    SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        .generateSecret(PBEKeySpec(pin.toCharArray(), salt, PIN_KDF_ITERATIONS, 256))
        .encoded
        .toHex()

/**
 * Verify [pin] against the stored hash. A null [kdf] means the legacy
 * single-round SHA-256 scheme: it stays readable so installs that predate the
 * KDF are not locked out, and [onLegacyMatch] lets the caller upgrade the hash.
 */
internal fun pinMatches(
    pin: String,
    salt: ByteArray,
    expectedHashHex: String,
    kdf: String?,
    onLegacyMatch: () -> Unit = {}
): Boolean = runCatching {
    if (expectedHashHex.isBlank()) return@runCatching false
    val expected = expectedHashHex.hexToBytes()
    val candidate =
        if (kdf == PIN_KDF) newPinHash(pin, salt) else sha256Hex(salt + pin.toByteArray())
    // Constant-time: a short PIN is exactly where a timing leak pays off.
    if (!MessageDigest.isEqual(candidate.hexToBytes(), expected)) return@runCatching false
    if (kdf == null) onLegacyMatch()
    true
}.getOrDefault(false)

/** Constant-time token check; pure, total, and JVM-testable. */
internal fun biometricTokenMatches(decrypted: ByteArray?, expectedHashHex: String): Boolean =
    runCatching {
        if (decrypted == null || expectedHashHex.isBlank()) return@runCatching false
        val actual = MessageDigest.getInstance("SHA-256").digest(decrypted)
        MessageDigest.isEqual(actual, expectedHashHex.hexToBytes())
    }.getOrDefault(false)

internal fun ByteArray.toHex(): String =
    joinToString("") { "%02x".format(it) }

internal fun String.hexToBytes(): ByteArray =
    chunked(2).map { it.toInt(16).toByte() }.toByteArray()

private operator fun ByteArray.plus(other: ByteArray): ByteArray {
    val result = ByteArray(size + other.size)
    copyInto(result)
    other.copyInto(result, size)
    return result
}
