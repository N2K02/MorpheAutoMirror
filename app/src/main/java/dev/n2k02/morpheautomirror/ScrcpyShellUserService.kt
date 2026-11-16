package dev.n2k02.morpheautomirror

import android.os.Process
import android.util.Log
import androidx.annotation.Keep
import java.io.File
import java.security.MessageDigest
import kotlin.system.exitProcess

/**
 * Shizuku UserService: runs under UID 2000/root, not as an ordinary Android Service.
 * Only four narrowly defined scrcpy operations are accepted; no arbitrary shell execution.
 */
@Keep
class ScrcpyShellUserService : IScrcpyShellService.Stub() {
    private val jarFile = File("/data/local/tmp/morpheautomirror-scrcpy-3.3.4.jar")
    private var process: java.lang.Process? = null
    @Volatile private var lastError = ""

    private fun requirePrivilege() {
        check(Process.myUid() == Process.SHELL_UID || Process.myUid() == Process.ROOT_UID) {
            "Shizuku user service is not privileged"
        }
    }

    @Synchronized override fun getProcessUid(): Int = Process.myUid()

    @Synchronized override fun stageServer(serverJar: ByteArray) {
        requirePrivilege()
        require(serverJar.size in 10_000..300_000) { "Invalid scrcpy server payload size" }
        val hash = MessageDigest.getInstance("SHA-256")
            .digest(serverJar).joinToString("") { "%02x".format(it) }
        check(hash == EXPECTED_SHA256) { "scrcpy server asset checksum mismatch" }
        stopServer()
        jarFile.outputStream().use { it.write(serverJar) }
        jarFile.setReadable(true, true)
        jarFile.setWritable(true, true)
        lastError = ""
    }

    @Synchronized override fun startServer(scid: String) {
        requirePrivilege()
        require(scid.matches(Regex("[0-9a-f]{8}"))) { "Invalid scrcpy session ID" }
        check(jarFile.isFile) { "scrcpy server not staged" }
        stopServer()
        val command = listOf(
            "/system/bin/app_process", "/", "com.genymobile.scrcpy.Server", "3.3.4",
            "scid=$scid", "tunnel_forward=true", "audio=false", "control=false",
            "send_dummy_byte=false", "send_device_meta=false", "video_codec=h264",
            "max_size=1280", "max_fps=30", "video_bit_rate=2500000"
        )
        val builder = ProcessBuilder(command).redirectErrorStream(true)
        builder.environment()["CLASSPATH"] = jarFile.absolutePath
        val running = builder.start()
        process = running
        Thread {
            runCatching {
                running.inputStream.bufferedReader().useLines { lines ->
                    lines.forEach {
                        lastError = it.take(240)
                        Log.i("MorpheScrcpyServer", lastError)
                    }
                }
            }
        }.apply { name = "morphe-scrcpy-server-output"; isDaemon = true; start() }
    }

    @Synchronized override fun stopServer() {
        process?.destroy()
        process = null
    }

    override fun getLastError(): String = lastError

    override fun destroy() {
        stopServer()
        jarFile.delete()
        exitProcess(0)
    }

    private companion object {
        const val EXPECTED_SHA256 =
            "8588238c9a5a00aa542906b6ec7e6d5541d9ffb9b5d0f6e1bc0e365e2303079e"
    }
}
