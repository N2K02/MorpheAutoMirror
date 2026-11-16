package dev.n2k02.morpheautomirror

import android.content.pm.PackageManager
import kotlinx.coroutines.flow.MutableStateFlow
import rikka.shizuku.Shizuku

/** Optional permission check for LocalAdb's embedded Shizuku-compatible manager.
 * An installed manager is not itself a permission grant or a working scrcpy stream.
 */
internal data class ShizukuStatus(
    val binderAlive: Boolean = false,
    val permissionGranted: Boolean = false,
    val shellUid: Int? = null,
    val deniedPermanently: Boolean = false,
) {
    val readyForShell: Boolean get() = binderAlive && permissionGranted && (shellUid == 2000 || shellUid == 0)
    val label: String get() = when {
        !binderAlive -> "Not connected"
        deniedPermanently -> "Permission denied"
        !permissionGranted -> "Permission required"
        !readyForShell -> "Unexpected UID"
        else -> "Authorized (UID $shellUid)"
    }
}

internal object ShizukuAccess {
    private const val REQUEST_CODE = 2902
    val status = MutableStateFlow(ShizukuStatus())
    private var initialized = false

    private val received = Shizuku.OnBinderReceivedListener { refresh() }
    private val died = Shizuku.OnBinderDeadListener { status.value = ShizukuStatus() }
    private val permission = Shizuku.OnRequestPermissionResultListener { requestCode, _ ->
        if (requestCode == REQUEST_CODE) refresh()
    }

    @Synchronized fun initialize() {
        if (initialized) return
        initialized = true
        Shizuku.addBinderReceivedListenerSticky(received)
        Shizuku.addBinderDeadListener(died)
        Shizuku.addRequestPermissionResultListener(permission)
        refresh()
    }

    fun refresh() {
        val binder = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
        if (!binder) {
            status.value = ShizukuStatus()
            return
        }
        val granted = runCatching {
            !Shizuku.isPreV11() &&
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)
        val uid = if (granted) runCatching { Shizuku.getUid() }.getOrNull() else null
        val denied = if (granted) false else runCatching {
            Shizuku.shouldShowRequestPermissionRationale()
        }.getOrDefault(false)
        val next = ShizukuStatus(true, granted, uid, denied)
        if (status.value != next) {
            status.value = next
            MirrorGateway.sync()
        }
        AppLog.add("SHIZUKU", "Manager ready=$binder, granted=$granted, shellUid=${uid ?: "unknown"}")
    }

    /** Explicit user action only; never prompt silently or treat LocalAdb as blanket consent. */
    fun request(): Boolean {
        refresh()
        val s = status.value
        if (!s.binderAlive || s.deniedPermanently || s.readyForShell) return false
        return runCatching {
            Shizuku.requestPermission(REQUEST_CODE)
            true
        }.getOrElse {
            AppLog.add("SHIZUKU", "Permission request failed: ${it.javaClass.simpleName}")
            false
        }
    }

    @Synchronized fun shutdown() {
        if (!initialized) return
        initialized = false
        Shizuku.removeBinderReceivedListener(received)
        Shizuku.removeBinderDeadListener(died)
        Shizuku.removeRequestPermissionResultListener(permission)
    }
}
