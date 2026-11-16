package dev.n2k02.morpheautomirror

/** Stable UI labels derived from actual listener, install, and permission state. */
internal object MonitoringStatus {
    fun label(enabled: Boolean, installed: Boolean, permission: Boolean, connected: Boolean): String =
        when {
            !enabled -> "Monitoring off"
            !installed -> "Morphe not found"
            !permission -> "Access required"
            !connected -> "Connecting"
            else -> "Monitoring active"
        }
}
