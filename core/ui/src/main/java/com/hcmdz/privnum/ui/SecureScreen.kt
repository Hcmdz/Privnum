package com.hcmdz.privnum.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView

/**
 * Blocks screenshots, screen recording and screen sharing for as long as the
 * calling screen stays visible. Anything rendering private data opts in with
 * this one call: contact lists, contact details, the editor, search results and
 * the passcode screens. The flag is dropped again when the screen leaves the
 * composition, so a screen without private data stays shareable.
 */
@Composable
fun SecureScreen() {
    val window = LocalView.current.context.findActivity()?.window
    DisposableEffect(window) {
        window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
