package com.hcmdz.privnum

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.hcmdz.privnum.contacts.ContactsScreen
import com.hcmdz.privnum.data.ThemeMode
import com.hcmdz.privnum.editor.EditorScreen
import com.hcmdz.privnum.lock.LockScreen
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
data object LockSetup : NavKey

@Composable
fun AppNav(
    startLocked: Boolean,
    themeMode: ThemeMode,
    onUnlocked: () -> Unit
) {
    val backStack = rememberNavBackStack(if (startLocked) LockSetup else Contacts)
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
                        onAdd = { backStack.add(NewContact) },
                        onEdit = { backStack.add(EditContact(it.fullPhoneNumber)) },
                        onOpenSearch = { backStack.add(Search) },
                        onOpenSettings = { backStack.add(Settings) }
                    )
                }
                entry<NewContact> {
                    EditorScreen(
                        fullPhoneNumber = null,
                        onSaved = { backStack.removeLastOrNull() },
                        onBack = { backStack.removeLastOrNull() }
                    )
                }
                entry<EditContact> { key ->
                    EditorScreen(
                        fullPhoneNumber = key.fullPhoneNumber,
                        onSaved = { backStack.removeLastOrNull() },
                        onBack = { backStack.removeLastOrNull() }
                    )
                }
                entry<Search> {
                    SearchScreen()
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
            }
        )
    }
}
