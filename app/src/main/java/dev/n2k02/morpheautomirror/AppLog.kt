package dev.n2k02.morpheautomirror

import android.util.Log

/** Android Logcat only; diagnostics are viewed through McpAdbAndroidLogger. */
internal object AppLog {
    fun add(tag: String, message: String) {
        val safe = message.take(240).replace(Regex("""https?://\S+"""), "<url>")
        Log.i("MorpheAutoMirror/$tag", safe)
    }
}
