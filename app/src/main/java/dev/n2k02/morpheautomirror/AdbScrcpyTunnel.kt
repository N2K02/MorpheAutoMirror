package dev.n2k02.morpheautomirror

import android.content.Context
import android.os.Build
import io.github.muntashirakon.adb.AbsAdbConnectionManager
import io.github.muntashirakon.adb.AdbStream
import java.io.IOException
import java.io.InputStream
import java.security.PrivateKey
import java.security.cert.Certificate
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withTimeoutOrNull

/** Reads the abstract scrcpy socket through local adbd, whose shell owns the server. */
internal class AdbScrcpyTunnel(context: Context) {
    private val identity = AdbClientIdentity.load(context)
    private val closed = AtomicBoolean()
    @Volatile private var stream: AdbStream? = null

    private val manager = object : AbsAdbConnectionManager() {
        init {
            setApi(Build.VERSION.SDK_INT)
            setTimeout(5, TimeUnit.SECONDS)
        }

        override fun getPrivateKey(): PrivateKey = identity.privateKey
        override fun getCertificate(): Certificate = identity.certificate
        override fun getDeviceName(): String = "MorpheAutoMirror scrcpy"
    }

    suspend fun open(port: Int, sessionId: String): InputStream {
        require(port in 1..65535)
        require(sessionId.matches(Regex("[0-9a-f]{8}")))
        check(runInterruptible(Dispatchers.IO) { manager.connect("127.0.0.1", port) }) {
            "ADB video transport could not connect"
        }
        val destination = "localabstract:scrcpy_$sessionId"
        return withTimeoutOrNull(8_000) {
            var connected: AdbStream? = null
            while (connected == null) {
                check(!closed.get()) { "ADB video transport closed" }
                try {
                    connected = runInterruptible(Dispatchers.IO) { manager.openStream(destination) }
                } catch (error: IOException) {
                    if (!manager.isConnected) throw error
                    delay(100)
                }
            }
            stream = connected
            if (closed.get()) {
                connected.close()
                error("ADB video transport closed")
            }
            connected.openInputStream()
        } ?: throw IOException("Timed out opening the scrcpy ADB stream")
    }

    fun close() {
        if (!closed.compareAndSet(false, true)) return
        runCatching { stream?.close() }
        runCatching { manager.disconnect() }
    }
}
