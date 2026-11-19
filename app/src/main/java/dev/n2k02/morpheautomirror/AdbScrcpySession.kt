package dev.n2k02.morpheautomirror

import java.security.MessageDigest
import java.util.Base64

/** Stages a verified server into a unique shell-owned directory and owns its process. */
internal class AdbScrcpySession(
    private val id: String,
    private val shell: suspend (String, Long) -> String,
    private val expectedHash: String = SERVER_HASH,
) {
    init { require(id.matches(Regex("[0-9a-f]{8}"))) }

    private val directory = "/data/local/tmp/morphe-scrcpy-$id"
    private val jar = "$directory/server.jar"
    private var stagingAttempted = false

    suspend fun start(server: ByteArray) {
        require(server.size in 10_000..300_000) { "Invalid scrcpy server payload size" }
        val hash = MessageDigest.getInstance("SHA-256")
            .digest(server).joinToString("") { "%02x".format(it) }
        check(hash == expectedHash) { "scrcpy server asset checksum mismatch" }
        runChecked("umask 077; mkdir $directory")
        stagingAttempted = true
        for (offset in server.indices step 2_048) {
            val chunk = server.copyOfRange(offset, minOf(offset + 2_048, server.size))
            val encoded = Base64.getEncoder().encodeToString(chunk)
            runChecked("printf '%s' '$encoded' | base64 -d >> $jar")
        }
        runChecked("test \"\$(sha256sum $jar | cut -d ' ' -f 1)\" = '$expectedHash'")
        runChecked(
            "umask 077; CLASSPATH=$jar nohup /system/bin/app_process / " +
                "com.genymobile.scrcpy.Server 3.3.4 scid=$id tunnel_forward=true " +
                "audio=false control=false send_dummy_byte=false send_device_meta=false " +
                "video_codec=h264 max_size=1280 max_fps=30 video_bit_rate=2500000 " +
                "> $directory/server.log 2>&1 </dev/null & echo \$! > $directory/pid"
        )
    }

    suspend fun close() {
        if (!stagingAttempted) return
        val pidPath = "$directory/pid"
        // Validate the command line before signalling, in case the PID was reused.
        runChecked(
            "if [ -f $pidPath ]; then " +
                "pid=\$(cat $pidPath); case \"\$pid\" in ''|*[!0-9]*) ;; *) " +
                "if tr '\\000' '\\n' < /proc/\$pid/cmdline 2>/dev/null | grep -Fxq 'scid=$id'; " +
                "then kill \"\$pid\" 2>/dev/null || true; fi ;; esac; fi; rm -rf $directory"
        )
        stagingAttempted = false
    }

    private suspend fun runChecked(command: String) {
        val result = shell("$command\nstatus=\$?; printf 'morphe-exit:%s\\n' \"\$status\"", 5_000)
        check(result.lineSequence().lastOrNull { it.isNotBlank() }?.trim() == "morphe-exit:0") {
            "ADB scrcpy command failed"
        }
    }

    companion object {
        private const val SERVER_HASH = "8588238c9a5a00aa542906b6ec7e6d5541d9ffb9b5d0f6e1bc0e365e2303079e"
    }
}
