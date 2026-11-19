package dev.n2k02.morpheautomirror

import android.content.Context
import dev.n2k02.adb.core.AdbCore
import dev.n2k02.adb.core.AdbIdentitySource
import dev.n2k02.adb.core.AdbOption
import dev.n2k02.adb.core.LibAdbWire
import dev.n2k02.adb.core.LocalAdbDiscovery
import dev.n2k02.adb.core.SharedAdbPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

internal object IntegratedAdb {
    lateinit var core: AdbCore
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    fun initialize(context: Context) {
        val preferences = SharedAdbPreferences(context).apply {
            // This architecture uses Wireless debugging's local TLS transport.
            setEnabled(AdbOption.TCP_5555, false)
            setEnabled(AdbOption.AUTO_PAIR, false)
            setEnabled(AdbOption.START_ON_BOOT, false)
        }
        core = AdbCore(
            LibAdbWire(context, AdbIdentitySource(AdbClientIdentity::load), "MorpheAutoMirror"),
            preferences,
            LocalAdbDiscovery(context),
        )
        scope.launch {
            core.state.map { it.connected }.distinctUntilChanged().collect { connected ->
                if (connected) MirrorGateway.sync()
            }
        }
    }

    fun startRecovery() = core.acquireService()
    fun stopRecovery() = core.releaseService()
    fun shutdown() { scope.launch { core.quit() } }

}
