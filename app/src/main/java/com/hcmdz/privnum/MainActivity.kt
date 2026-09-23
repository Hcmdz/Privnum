package com.hcmdz.privnum

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.hcmdz.privnum.caller.CallerPermissions
import com.hcmdz.privnum.caller.ScreeningRole
import com.hcmdz.privnum.data.Contact
import com.hcmdz.privnum.data.ContactRepository
import com.hcmdz.privnum.ui.PrivnumTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

// P2 debug UI — replaced by the settings screen in P3. Verifies the
// caller wiring (role, permissions, repository) on real devices.
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private var status by mutableStateOf("")

    private val phonePermission = CallerPermissions.registerPhoneStateRequest(this) { granted ->
        status = "phone=$granted " + status()
    }
    private val overlayPermission = CallerPermissions.registerOverlayRequest(this) { granted ->
        status = "overlay=$granted " + status()
    }
    private val roleRequest = ScreeningRole.registerRequest(this) { granted ->
        status = "role=$granted " + status()
    }

    private fun status(): String =
        "role=${ScreeningRole.isHeld(this)} " +
            "phone=${CallerPermissions.hasPhoneState(this)} " +
            "overlay=${CallerPermissions.hasOverlay(this)}"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        status = status()
        setContent {
            PrivnumTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("Privnum P2 harness")
                        Text(status)
                        Button(onClick = {
                            ScreeningRole.requestIntent(this@MainActivity)?.let {
                                roleRequest.launch(it)
                            } ?: run { status = "role-unsupported " + status() }
                        }) { Text("Request screening role") }
                        Button(onClick = {
                            phonePermission.launch(android.Manifest.permission.READ_PHONE_STATE)
                        }) { Text("Grant phone permission") }
                        Button(onClick = {
                            overlayPermission.launch(CallerPermissions.overlaySettingsIntent())
                        }) { Text("Grant overlay permission") }
                        Button(onClick = {
                            lifecycleScope.launch {
                                ContactRepository(this@MainActivity).add(
                                    Contact(
                                        fullPhoneNumber = "15555215556",
                                        phoneNumber = "5555215556",
                                        countryCode = "US",
                                        name = "Emulator Test"
                                    )
                                )
                                status = "seeded " + status()
                            }
                        }) { Text("Seed test contact") }
                    }
                }
            }
        }
    }
}

@Preview
@Composable
fun PlaceholderPreview() {
    PrivnumTheme {
        Text("Privnum")
    }
}
