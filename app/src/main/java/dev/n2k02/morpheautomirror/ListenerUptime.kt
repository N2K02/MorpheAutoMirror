package dev.n2k02.morpheautomirror

/** ElapsedRealtime-based uptime; survives clock changes and resets on disconnect. */
internal object ListenerUptime {
    fun format(since: Long?, now: Long): String {
        if (since == null) return "Not connected"
        val seconds = ((now - since).coerceAtLeast(0L)) / 1000
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val remainder = seconds % 60
        return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, remainder)
        else "%02d:%02d".format(minutes, remainder)
    }
}
