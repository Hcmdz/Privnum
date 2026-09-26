package com.hcmdz.privnum

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.hcmdz.privnum.contacts.ContactsScreen
import com.hcmdz.privnum.data.PasscodeStore
import com.hcmdz.privnum.data.ThemeMode
import com.hcmdz.privnum.editor.EditorScreen
import com.hcmdz.privnum.lock.LockScreen
import com.hcmdz.privnum.preview.PreviewScreen
import com.hcmdz.privnum.search.SearchScreen
import com.hcmdz.privnum.settings.SettingsScreen
import com.hcmdz.privnum.ui.PrivnumTheme
import com.hcmdz.privnum.ui.SecureScreen
import kotlinx.serialization.Serializable

@Serializable
data object Contacts : NavKey

@Serializable
data object NewContact : NavKey

@Serializable
data class EditContact(val contactId: Long) : NavKey

@Serializable
data object Search : NavKey

@Serializable
data object Settings : NavKey

@Serializable
data class PreviewContact(val contactId: Long) : NavKey

@Serializable
data object LockSetup : NavKey

@Serializable
data object LockVerify : NavKey

@Composable
fun AppNav(
    startLocked: Boolean,
    themeMode: ThemeMode,
    dynamicColor: Boolean,
    amoledBlack: Boolean,
    passcode: PasscodeStore,
    onUnlocked: () -> Unit,
    onLanguageSelected: (String?) -> Unit
) {
    val backStack = rememberNavBackStack(if (startLocked) LockSetup else Contacts)
    var newContactNonce by remember { mutableIntStateOf(0) }
    val darkTheme = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    PrivnumTheme(
        darkTheme = darkTheme,
        dynamicColor = dynamicColor,
        amoledBlack = amoledBlack
    ) {
        NavDisplay(
            backStack = backStack,
            onBack = {
                // Locked gates never pop via back: authenticate to proceed.
                // (LockSetup with siblings is the Settings setup flow: back cancels it.)
                val top = backStack.lastOrNull()
                val lockedGate = top is LockVerify ||
                    (top is LockSetup && backStack.size == 1)
                if (!lockedGate) backStack.removeLastOrNull()
            },
            entryProvider = entryProvider {
                entry<Contacts> {
                    SecureScreen()
                    ContactsScreen(
                        onAdd = {
                            newContactNonce++
                            backStack.add(NewContact)
                        },
                        onPreview = { backStack.add(PreviewContact(it.id)) },
                        onOpenSearch = { backStack.add(Search) },
                        onOpenSettings = { backStack.add(Settings) },
                        onLockNow = {
                            passcode.lockNow()
                            backStack.add(LockVerify)
                        }
                    )
                }
                entry<NewContact> {
                    SecureScreen()
                    EditorScreen(
                        contactId = null,
                        entryNonce = newContactNonce,
                        onSaved = { backStack.removeLastOrNull() },
                        onBack = { backStack.removeLastOrNull() }
                    )
                }
                entry<EditContact> { key ->
                    SecureScreen()
                    EditorScreen(
                        contactId = key.contactId,
                        entryNonce = 0,
                        // Pop editor + preview, back to the list (source popToTop).
                        onSaved = {
                            backStack.removeLastOrNull()
                            backStack.removeLastOrNull()
                        },
                        onBack = { backStack.removeLastOrNull() }
                    )
                }
                entry<Search> {
                    SecureScreen()
                    SearchScreen(
                        onPreview = { backStack.add(PreviewContact(it.id)) }
                    )
                }
                entry<PreviewContact> { key ->
                    SecureScreen()
                    PreviewScreen(
                        contactId = key.contactId,
                        onBack = { backStack.removeLastOrNull() },
                        onEdit = { backStack.add(EditContact(it.id)) }
                    )
                }
                entry<Settings> {
                    SettingsScreen(
                        onBack = { backStack.removeLastOrNull() },
                        onOpenLock = { backStack.add(LockSetup) },
                        onLanguageSelected = onLanguageSelected
                    )
                }
                entry<LockSetup> {
                    // Opened from Settings: always run the setup flow.
                    // (Cold-start gate only pushes LockSetup when locked,
                    // so reaching it fresh means setup was requested.)
                    SecureScreen()
                    LockScreen(
                        startSetup = backStack.size > 1,
                        onUnlocked = {
                            onUnlocked()
                            if (backStack.size > 1) {
                                backStack.removeLastOrNull()
                            } else {
                                backStack.clear()
                                backStack.add(Contacts)
                            }
                        }
                    )
                }
                entry<LockVerify> {
                    SecureScreen()
                    LockScreen(
                        startSetup = false,
                        onUnlocked = {
                            onUnlocked()
                            backStack.removeLastOrNull()
                        }
                    )
                }
            }
        )
    }
}
