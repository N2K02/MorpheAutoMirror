package dev.n2k02.morpheautomirror

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Capped in-memory diagnostic log, no web requests or persistent sensitive logs. */
internal object AppLog {
    private val lock = Any()
    private val entries = ArrayDeque<String>()
    private val _lines = MutableStateFlow(emptyList<String>())
    val lines: StateFlow<List<String>> = _lines
    fun add(tag: String, message: String) {
        val safe = message.take(240).replace(Regex("https?://\\S+"), "<url>")
        val line = "${System.currentTimeMillis()} [$tag] $safe"
        synchronized(lock) {
            if (entries.size >= 250) entries.removeFirst()
            entries.addLast(line)
            _lines.value = entries.toList()
        }
        Log.i("MorpheAutoMirror/$tag", safe)
    }
    fun clear() = synchronized(lock) { entries.clear(); _lines.value = emptyList() }
}

