package dev.n2k02.morpheautomirror

/** Pure state machine: all side-effects live in Android services. */
internal data class MirrorState(
    val monitoring: Boolean = true,
    val morphePlaying: Boolean = false,
    val morphePaused: Boolean = false,
    val carSession: Boolean = false,
    val parkedConfirmed: Boolean = false,
    val permissionGranted: Boolean = false,
    val surfaceReady: Boolean = false,
    val capturing: Boolean = false,
)
internal enum class NextStep { IDLE, WAIT_FOR_CAR, WAIT_FOR_PARKED, WAIT_FOR_SURFACE, ASK_PERMISSION, MIRROR, STOP }
internal object EnginePolicy {
    fun plan(s: MirrorState): NextStep = when {
        // An existing capture stays alive while Morphe is paused. No automatic
        // media commands or re-consent on resume; stopped/destroyed sessions end it.
        !s.monitoring || (!s.morphePlaying && !(s.capturing && s.morphePaused)) ->
            if (s.capturing) NextStep.STOP else NextStep.IDLE
        !s.carSession -> if (s.capturing) NextStep.STOP else NextStep.WAIT_FOR_CAR
        !s.parkedConfirmed -> if (s.capturing) NextStep.STOP else NextStep.WAIT_FOR_PARKED
        !s.surfaceReady -> if (s.capturing) NextStep.STOP else NextStep.WAIT_FOR_SURFACE
        !s.permissionGranted -> NextStep.ASK_PERMISSION
        else -> NextStep.MIRROR
    }
    fun isMorphe(packageName: String?, configured: String): Boolean =
        !packageName.isNullOrBlank() && configured.isNotBlank() && packageName == configured
}

