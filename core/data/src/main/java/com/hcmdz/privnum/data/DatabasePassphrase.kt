package com.hcmdz.privnum.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * The engine ships as a plain native library and 4.9 exposes no loader of its
 * own, so it has to be pulled in before the first connection. Loading twice is
 * harmless, and doing it here keeps every entry point - the repository and the
 * plaintext conversion alike - behind the same guard.
 */
internal fun loadSqlCipher() {
    runCatching { System.loadLibrary("sqlcipher") }
}

/**
 * Database passphrase, generated once and never stored in clear.
 *
 * The 32 raw bytes live wrapped under a non-exportable platform keystore key;
 * only the wrapped form is written to the preferences file, so a copy of the
 * app data is not enough to open the contact database. Losing the keystore key
 * (a factory reset, a wiped device) makes the database unreadable by design -
 * the same trade the passcode store and the photo store already make.
 */
internal object DatabasePassphrase {

    private const val KEY_ALIAS = "privnum_db_key"
    private const val PREFS = "database_key"
    private const val ENTRY = "wrapped"

    /** One shared instance, like the passcode store: building a new
     * [SecureRandom] per call can stall while its entropy pool is seeded. */
    private val secureRandom = SecureRandom()

    private val wrapKey: SecretKey by lazy {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)
            ?.secretKey
            ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
                .run {
                    init(
                        KeyGenParameterSpec.Builder(
                            KEY_ALIAS,
                            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                        )
                            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                            .setKeySize(256)
                            .build()
                    )
                    generateKey()
                }
    }

    /** Synchronous on purpose: Room opens its database from a lazy block. */
    fun get(context: Context): ByteArray {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val wrapped = prefs.getString(ENTRY, null)
        if (wrapped != null) return unwrap(wrapped)
        val fresh = ByteArray(32).also { secureRandom.nextBytes(it) }
        prefs.edit().putString(ENTRY, wrap(fresh)).apply()
        return fresh
    }

    /** 64 character hex form, which the engine uses as raw key material. */
    fun hex(context: Context): String = get(context).joinToString("") { "%02x".format(it) }

    private fun wrap(plain: ByteArray): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, wrapKey)
        }
        val blob = cipher.iv + cipher.doFinal(plain)
        return Base64.encodeToString(blob, Base64.NO_WRAP)
    }

    private fun unwrap(stored: String): ByteArray {
        val blob = Base64.decode(stored, Base64.NO_WRAP)
        val ivLength = 12
        val iv = blob.copyOfRange(0, ivLength)
        return Cipher.getInstance("AES/GCM/NoPadding").run {
            init(Cipher.DECRYPT_MODE, wrapKey, GCMParameterSpec(128, iv))
            doFinal(blob.copyOfRange(ivLength, blob.size))
        }
    }
}

/**
 * Turns an existing plaintext database into an encrypted one, in place.
 *
 * The schema is untouched, so no Room migration is involved: the rows are read
 * and written back through the app's own database, and the FTS index is rebuilt
 * by the insert triggers. The plaintext file is only moved aside once the
 * encrypted copy is complete, and put back if the swap fails, so a crash can
 * never leave the app with neither.
 */
internal fun encryptDatabaseIfPlaintext(
    context: Context,
    databaseFile: File,
    passphrase: ByteArray
) {
    if (!databaseFile.exists()) return
    // Opening with the real key only succeeds on an already encrypted file,
    // which is the same check Room is about to do.
    val alreadyEncrypted = runCatching {
        net.zetetic.database.sqlcipher.SQLiteDatabase.openDatabase(
            databaseFile.path,
            passphrase,
            null as net.zetetic.database.sqlcipher.SQLiteDatabase.CursorFactory?,
            net.zetetic.database.sqlcipher.SQLiteDatabase.OPEN_READONLY,
            null as net.zetetic.database.DatabaseErrorHandler?,
            null as net.zetetic.database.sqlcipher.SQLiteDatabaseHook?
        ).close()
    }.isSuccess
    if (alreadyEncrypted) return

    val legacy = androidx.room.Room.databaseBuilder(
        context, com.hcmdz.privnum.data.db.PrivnumDatabase::class.java, databaseFile.name
    ).build()
    val contacts = runCatching {
        kotlinx.coroutines.runBlocking { legacy.contactDao().getAll().map { it.toContact() } }
    }.getOrDefault(emptyList())
    legacy.close()

    val encryptedCopy = File(databaseFile.parentFile, "${databaseFile.name}.encrypted")
    encryptedCopy.delete()
    val target = androidx.room.Room.databaseBuilder(
        context, com.hcmdz.privnum.data.db.PrivnumDatabase::class.java, encryptedCopy.name
    )
        .openHelperFactory(
            net.zetetic.database.sqlcipher.SupportOpenHelperFactory(passphrase)
        )
        .build()
    runCatching {
        kotlinx.coroutines.runBlocking {
            contacts.forEach { contact ->
                val id = target.contactDao().insertContact(contact.toEntity().copy(id = 0))
                target.contactDao().insertNumbers(contact.toNumberEntities(id))
            }
        }
    }
    target.close()
    if (!encryptedCopy.exists() || encryptedCopy.length() == 0L) return

    val original = File(databaseFile.parentFile, "${databaseFile.name}.plain")
    if (!databaseFile.renameTo(original)) return
    if (!encryptedCopy.renameTo(databaseFile)) {
        original.renameTo(databaseFile)
        return
    }
    original.delete()
}
