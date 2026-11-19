package dev.n2k02.morpheautomirror

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import java.util.Locale

/** Only Morphe-patched YouTube candidates are eligible; never select regular YouTube. */
internal object MorpheAppPolicy {
    const val DEFAULT_PACKAGE = "app.morphe.android.youtube"

    fun eligible(name: String): Boolean {
        val packageName = name.lowercase(Locale.ROOT)
        return packageName == DEFAULT_PACKAGE ||
            (packageName.contains("morphe") && packageName.contains("youtube") &&
                !packageName.contains("music"))
    }

    fun select(installed: Collection<String>): String? =
        installed.asSequence()
            .filter(::eligible)
            .distinct()
            .sortedWith(compareBy<String> { if (it == DEFAULT_PACKAGE) 0 else 1 }.thenBy { it })
            .firstOrNull()
}

/** Discovers the installed app from PackageManager; no manually entered package needed. */
internal object MorpheAppDiscovery {
    fun find(context: Context): String? {
        val pm = context.packageManager
        val installed = mutableSetOf<String>()
        val candidates = pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0
        )
        installed.addAll(candidates.mapNotNull { it.activityInfo?.packageName })
        // Some patched variants have no launcher activity visible to this query.
        if (isInstalled(pm, MorpheAppPolicy.DEFAULT_PACKAGE)) {
            installed.add(MorpheAppPolicy.DEFAULT_PACKAGE)
        }
        return MorpheAppPolicy.select(installed)
    }

    private fun isInstalled(pm: PackageManager, packageName: String): Boolean =
        try {
            @Suppress("DEPRECATION")
            pm.getApplicationInfo(packageName, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
}
