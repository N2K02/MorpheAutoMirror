package dev.n2k02.morpheautomirror

/** Single-level mRadio-style settings submenu hierarchy. */
internal object PageNavigation {
    fun parent(page: String): String = when (page) {
        "Monitoring", "Updates" -> "Settings"
        "Settings" -> "Home"
        else -> "Home"
    }
}
