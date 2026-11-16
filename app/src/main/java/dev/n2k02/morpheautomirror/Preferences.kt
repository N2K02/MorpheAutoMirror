package dev.n2k02.morpheautomirror

import android.content.Context
internal class Preferences(context: Context) {
    private val p = context.applicationContext.getSharedPreferences("morphe_auto_mirror", Context.MODE_PRIVATE)
    var enabled: Boolean
        get() = p.getBoolean("enabled", true)
        set(v) { p.edit().putBoolean("enabled", v).apply() }
    var morphePackage: String
        get() = p.getString("package", "app.morphe.android.youtube") ?: "app.morphe.android.youtube"
        set(v) { if (v.matches(Regex("[a-zA-Z_][a-zA-Z0-9_.]{2,150}"))) p.edit().putString("package", v).apply() }
    var webViewMode: Boolean
        get() = p.getBoolean("webview", false)
        set(v) { p.edit().putBoolean("webview", v).apply() }
}

