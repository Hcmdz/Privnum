package com.hcmdz.privnum.data

import android.content.Context
import java.io.File

/**
 * Files handed to other apps through the FileProvider.
 *
 * They live in filesDir rather than cacheDir: a full VCF export is exactly the
 * kind of data that has no business in a cache directory, and a dedicated
 * directory lets the provider expose one narrow path instead of the whole
 * cache. Callers pass a fixed name so each export overwrites the previous file
 * instead of piling up.
 */
fun shareFile(context: Context, name: String): File =
    File(File(context.filesDir, "shared_exports").also { it.mkdirs() }, name)
