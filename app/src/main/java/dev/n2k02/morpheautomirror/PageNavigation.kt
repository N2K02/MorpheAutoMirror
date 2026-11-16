package dev.n2k02.morpheautomirror

/** Single-level mRadio-style settings submenu hierarchy. */
internal object PageNavigation {
    fun parent(page: String): String = when (page) {
        "Monitoring", "Rendering", "Logs", "Updates" -> "Settings"
        "Settings" -> "Home"
        else -> "Home"
    }
}
