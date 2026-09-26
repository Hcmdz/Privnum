package com.hcmdz.privnum.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.room.Room
import com.hcmdz.privnum.data.db.MIGRATION_1_2
import com.hcmdz.privnum.data.db.PrivnumDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import net.zetetic.database.DatabaseErrorHandler
import net.zetetic.database.sqlcipher.SQLiteDatabase
import net.zetetic.database.sqlcipher.SQLiteDatabaseHook
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import java.io.File
import java.security.KeyStore
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
 * The stored key can no longer be unwrapped. Raised instead of regenerating:
 * replacing the wrap would leave an encrypted database that no key can ever
 * open again, so a clear failure is the only outcome that keeps the data
 * recoverable.
 */
internal class DatabaseKeyUnavailable(cause: Throwable) :
    IllegalStateException("The database key can no longer be read from the platform keystore", cause)

/**
 * Database passphrase, generated once and never stored in clear.
 *
 * The 32 raw bytes live wrapped under a non-exportable platform keystore key;
 * only the wrapped form is written to the preferences file, so a copy of the
 * app data is not enough to open the contact database. Losing the keystore key
 * (a factory reset, a wiped device) makes the database unreadable by design -
 * the same trade the passcode store and the photo store already make. Every
 * scenario that destroys the key also wipes the app data the contacts live in,
 * so there is no window where the data outlives its key except keystore
 * corruption, which surfaces as [DatabaseKeyUnavailable].
 */
internal object DatabasePassphrase {

    private const val KEY_ALIAS = "privnum_db_key"
    private const val PREFS = "database_key"
    private const val ENTRY = "wrapped"

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
        if (wrapped != null) {
            return runCatching { unwrap(wrapped) }
                .getOrElse { throw DatabaseKeyUnavailable(it) }
        }
        val fresh = ByteArray(32).also { java.security.SecureRandom().nextBytes(it) }
        prefs.edit().putString(ENTRY, wrap(fresh)).apply()
        return fresh
    }

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

private fun isEncrypted(databaseFile: File, passphrase: ByteArray): Boolean = runCatching {
    // Opening with the real key only succeeds on an already encrypted file,
    // which is the same check Room is about to do.
    SQLiteDatabase.openDatabase(
        databaseFile.path,
        passphrase,
        null as SQLiteDatabase.CursorFactory?,
        SQLiteDatabase.OPEN_READONLY,
        null as DatabaseErrorHandler?,
        null as SQLiteDatabaseHook?
    ).close()
}.isSuccess

private fun File.journalFiles() = listOf("$path-wal", "$path-shm")

/**
 * Turns an existing plaintext database into an encrypted one, in place.
 *
 * The schema is untouched, so no Room migration is involved: the rows are read
 * and written back through the app's own database, and the FTS index is rebuilt
 * by the insert triggers. The plaintext file is only moved aside once the
 * encrypted copy is complete and holds the same number of rows, and put back if
 * the swap fails, so a crash can never leave the app with neither.
 *
 * Every failure raises. The earlier version swallowed a failed read into an
 * empty list, then swapped the empty copy in and deleted the original: with no
 * rows to write, Room never materialised the target file, the conversion
 * returned as if it had succeeded, and Room opened the untouched plaintext file
 * with a key.
 */
internal fun encryptDatabaseIfPlaintext(
    context: Context,
    databaseFile: File,
    passphrase: ByteArray
) {
    // No file yet: Room creates the encrypted one on first write, nothing to keep.
    if (!databaseFile.exists() || databaseFile.length() == 0L) return
    if (isEncrypted(databaseFile, passphrase)) return

    val contacts = runBlocking(Dispatchers.IO) {
        val source = Room.databaseBuilder(context, PrivnumDatabase::class.java, databaseFile.name)
            .addMigrations(MIGRATION_1_2)
            .build()
        try {
            source.contactDao().getAll().map { it.toContact() }
        } finally {
            source.close()
        }
    }

    val encryptedCopy = File(databaseFile.parentFile, "${databaseFile.name}.encrypted")
    encryptedCopy.delete()
    runBlocking(Dispatchers.IO) {
        val target = Room.databaseBuilder(context, PrivnumDatabase::class.java, encryptedCopy.name)
            .addMigrations(MIGRATION_1_2)
            .openHelperFactory(SupportOpenHelperFactory(passphrase))
            .build()
        try {
            // Room materialises the file on first write only, so an empty source
            // has to be forced open or no file is produced at all.
            target.openHelper.writableDatabase
            contacts.forEach { contact ->
                val id = target.contactDao().insertContact(contact.toEntity().copy(id = 0))
                target.contactDao().insertNumbers(contact.toNumberEntities(id))
            }
        } finally {
            target.close()
        }
    }

    check(encryptedCopy.exists() && encryptedCopy.length() > 0L) {
        "encrypted copy was not created from ${databaseFile.path}"
    }
    val copied = runBlocking(Dispatchers.IO) {
        val check = Room.databaseBuilder(context, PrivnumDatabase::class.java, encryptedCopy.name)
            .addMigrations(MIGRATION_1_2)
            .openHelperFactory(SupportOpenHelperFactory(passphrase))
            .build()
        try {
            check.contactDao().getAll().size
        } finally {
            check.close()
        }
    }
    check(copied == contacts.size) {
        "encrypted copy holds $copied of ${contacts.size} rows, leaving the plaintext in place"
    }
    // SQLite checkpoints when the last connection closes, so the copy is normally
    // self-contained. Renaming moves the main file only: pages still sitting in a
    // sidecar would be stranded under a name nothing reads again, and the row
    // count above would already have passed them.
    check(encryptedCopy.journalFiles().none { File(it).exists() }) {
        "encrypted copy still holds unflushed pages, leaving the plaintext in place"
    }

    val original = File(databaseFile.parentFile, "${databaseFile.name}.plain")
    if (!databaseFile.renameTo(original)) {
        encryptedCopy.delete()
        error("cannot move ${databaseFile.path} aside, the plaintext database is left untouched")
    }
    // The plaintext write-ahead log belongs to the file that just moved away.
    // Leaving it behind would be replayed into the encrypted file on first open.
    databaseFile.journalFiles().forEach { File(it).delete() }
    original.journalFiles().forEach { File(it).delete() }
    if (!encryptedCopy.renameTo(databaseFile)) {
        original.renameTo(databaseFile)
        encryptedCopy.delete()
        error("cannot move the encrypted copy into place, the plaintext database was restored")
    }
    original.delete()
}
