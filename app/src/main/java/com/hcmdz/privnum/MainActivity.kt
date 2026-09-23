package com.hcmdz.privnum

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hcmdz.privnum.data.PasscodeStore
import com.hcmdz.privnum.data.SettingsStore
import com.hcmdz.privnum.data.ThemeMode
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var settings: SettingsStore

    @Inject
    lateinit var passcode: PasscodeStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val startLocked = passcode.shouldLock()
        setContent {
            val themeMode by settings.themeMode.collectAsStateWithLifecycle(
                initialValue = ThemeMode.SYSTEM
            )
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                AppNav(
                    startLocked = startLocked,
                    themeMode = themeMode,
                    onUnlocked = {}
                )
            }
        }
    }
}
