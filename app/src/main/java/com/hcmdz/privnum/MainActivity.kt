package com.hcmdz.privnum

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.hcmdz.privnum.data.ContactRepository
import com.hcmdz.privnum.data.PasscodeStore
import com.hcmdz.privnum.data.SettingsStore
import com.hcmdz.privnum.data.ThemeMode
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    @Inject
    lateinit var settings: SettingsStore

    @Inject
    lateinit var passcode: PasscodeStore

    @Inject
    lateinit var repository: ContactRepository

    private val splashReady = MutableStateFlow(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        // Screenshots and screen sharing stay blocked for the whole app: every
        // screen shows contact data, and toggling the flag per screen made
        // WindowManager rebuild the surface on each navigation, which flickered.
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        val startLocked = passcode.shouldLock()
        lifecycleScope.launch {
            splashReady.value = awaitReady(repository.observeContacts())
        }
        // The lock gate keeps its original timing: only the contacts
        // destination waits for the first load.
        splash.setKeepOnScreenCondition { !splashReady.value && !startLocked }
        setContent {
            val themeMode by settings.themeMode.collectAsStateWithLifecycle(
                initialValue = ThemeMode.SYSTEM
            )
            val dynamicColor by settings.dynamicColor.collectAsStateWithLifecycle(
                initialValue = true
            )
            val amoledBlack by settings.amoledBlack.collectAsStateWithLifecycle(
                initialValue = false
            )
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                AppNav(
                    startLocked = startLocked,
                    themeMode = themeMode,
                    dynamicColor = dynamicColor,
                    amoledBlack = amoledBlack,
                    passcode = passcode,
                    onUnlocked = {},
                    onLanguageSelected = { languageTag ->
                        val locales = languageTag?.let { LocaleListCompat.forLanguageTags(it) }
                            ?: LocaleListCompat.getEmptyLocaleList()
                        AppCompatDelegate.setApplicationLocales(locales)
                    }
                )
            }
        }
    }
}

private const val SPLASH_READY_TIMEOUT_MILLIS = 2500L

/**
 * Waits for the first value of [source] up to [timeoutMillis]. Every outcome
 * counts as ready — emission, timeout, or failure — because downstream empty
 * and error states own those cases, never the splash. Single-flight of the
 * underlying load is guaranteed by the callee (here the repository
 * singleton's lazy database), so timing out abandons nothing.
 */
internal suspend fun awaitReady(
    source: Flow<*>,
    timeoutMillis: Long = SPLASH_READY_TIMEOUT_MILLIS
): Boolean {
    runCatching {
        withTimeoutOrNull(timeoutMillis) {
            source.first()
        }
    }
    return true
}
