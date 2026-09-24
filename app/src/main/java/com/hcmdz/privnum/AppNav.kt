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
import kotlinx.serialization.Serializable

@Serializable
data object Contacts : NavKey

@Serializable
data object NewContact : NavKey

@Serializable
data class EditContact(val fullPhoneNumber: String) : NavKey

@Serializable
data object Search : NavKey

@Serializable
data object Settings : NavKey

@Serializable
data class PreviewContact(val fullPhoneNumber: String) : NavKey

@Serializable
data object LockSetup : NavKey

@Serializable
data object LockVerify : NavKey

@Composable
fun AppNav(
    startLocked: Boolean,
    themeMode: ThemeMode,
    passcode: PasscodeStore,
    onUnlocked: () -> Unit
) {
    val backStack = rememberNavBackStack(if (startLocked) LockSetup else Contacts)
    var newContactNonce by remember { mutableIntStateOf(0) }
    val darkTheme = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    PrivnumTheme(darkTheme = darkTheme) {
        NavDisplay(
            backStack = backStack,
            onBack = { backStack.removeLastOrNull() },
            entryProvider = entryProvider {
                entry<Contacts> {
                    ContactsScreen(
                        onAdd = {
                            newContactNonce++
                            backStack.add(NewContact)
                        },
                        onPreview = { backStack.add(PreviewContact(it.fullPhoneNumber)) },
                        onOpenSearch = { backStack.add(Search) },
                        onOpenSettings = { backStack.add(Settings) },
                        onLockNow = {
                            passcode.lockNow()
                            backStack.add(LockVerify)
                        }
                    )
                }
                entry<NewContact> {
                    EditorScreen(
                        fullPhoneNumber = null,
                        entryNonce = newContactNonce,
                        onSaved = { backStack.removeLastOrNull() },
                        onBack = { backStack.removeLastOrNull() }
                    )
                }
                entry<EditContact> { key ->
                    EditorScreen(
                        fullPhoneNumber = key.fullPhoneNumber,
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
                    SearchScreen(
                        onPreview = { backStack.add(PreviewContact(it.fullPhoneNumber)) }
                    )
                }
                entry<PreviewContact> { key ->
                    PreviewScreen(
                        fullPhoneNumber = key.fullPhoneNumber,
                        onBack = { backStack.removeLastOrNull() },
                        onEdit = { backStack.add(EditContact(it.fullPhoneNumber)) }
                    )
                }
                entry<Settings> {
                    SettingsScreen(
                        onBack = { backStack.removeLastOrNull() },
                        onOpenLock = { backStack.add(LockSetup) }
                    )
                }
                entry<LockSetup> {
                    // Opened from Settings: always run the setup flow.
                    // (Cold-start gate only pushes LockSetup when locked,
                    // so reaching it fresh means setup was requested.)
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
