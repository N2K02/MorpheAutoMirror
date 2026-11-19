package dev.n2k02.morpheautomirror

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import dev.n2k02.adb.core.AdbPhase
import dev.n2k02.adb.core.AdbState
import kotlinx.coroutines.launch

@Composable
internal fun IntegratedAdbSettings(state: AdbState) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val core = IntegratedAdb.core
    var port by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    val pairingPort = port.toIntOrNull() ?: state.pairingPort
    val busy = state.phase == AdbPhase.PAIRING || state.phase == AdbPhase.CONNECTING
    DisposableEffect(core) {
        core.enterPage()
        onDispose { core.leavePage() }
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(if (state.connected) "Connected · shell UID 2000" else state.message)
        if (!state.connected) {
            Text("Enable Wireless debugging, then open Pair device with pairing code. Keep the pairing dialog open in split screen while entering its details.")
            OutlinedButton(onClick = {
                context.startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
            }) { Text("Open Developer options") }
            val numbers = KeyboardOptions(keyboardType = KeyboardType.Number)
            OutlinedTextField(
                value = port,
                onValueChange = { port = it.filter { c -> c in '0'..'9' }.take(5) },
                label = { Text("Pairing port") },
                placeholder = { Text(state.pairingPort?.toString().orEmpty()) },
                singleLine = true,
                keyboardOptions = numbers,
            )
            OutlinedTextField(
                value = code,
                onValueChange = { code = it.filter { c -> c in '0'..'9' }.take(6) },
                label = { Text("Six-digit pairing code") },
                singleLine = true,
                keyboardOptions = numbers,
                visualTransformation = PasswordVisualTransformation(),
            )
            Button(
                enabled = !busy && pairingPort != null && pairingPort in 1..65535 && code.length == 6,
                onClick = {
                    val entered = code
                    val selected = requireNotNull(pairingPort)
                    code = ""
                    scope.launch { core.pair(selected, entered) }
                },
            ) { Text("Pair this phone") }
            OutlinedButton(enabled = !busy, onClick = { scope.launch { core.retry() } }) {
                Text("Retry connection")
            }
        } else {
            OutlinedButton(onClick = {
                ScrcpyMirrorService.stop(context)
                scope.launch { core.stop() }
            }) { Text("Disconnect ADB") }
        }
    }
}
