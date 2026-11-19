package dev.n2k02.morpheautomirror

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest

class AdbScrcpySessionTest {
    private val asset = ByteArray(10_001) { it.toByte() }
    private val hash = MessageDigest.getInstance("SHA-256")
        .digest(asset).joinToString("") { "%02x".format(it) }

    @Test fun stagesStartsAndCleansUpOnlyTheOwnedSession() = runBlocking {
        val commands = mutableListOf<String>()
        val session = AdbScrcpySession("1234abcd", { command, _ ->
            commands += command
            "morphe-exit:0"
        }, hash)
        session.start(asset)
        session.close()
        session.close()
        assertEquals(5, commands.count { "base64 -d" in it })
        assertTrue("sha256sum" in commands[6])
        assertTrue("app_process" in commands[7] && "scid=1234abcd" in commands[7])
        assertTrue("grep -Fxq 'scid=1234abcd'" in commands.last())
        assertEquals(9, commands.size)
    }

    @Test fun badAssetNeverRunsPrivilegedCommands() = runBlocking {
        val session = AdbScrcpySession("1234abcd", { _, _ -> error("unexpected shell command") })
        assertEquals("scrcpy server asset checksum mismatch", runCatching { session.start(asset) }.exceptionOrNull()?.message)
        session.close()
    }

    @Test fun failedStagingCanCleanUpBeforeANewSession() = runBlocking {
        val commands = mutableListOf<String>()
        val session = AdbScrcpySession("1234abcd", { command, _ ->
            commands += command
            if ("base64 -d" in command) "morphe-exit:1" else "morphe-exit:0"
        }, hash)
        assertEquals("ADB scrcpy command failed", runCatching { session.start(asset) }.exceptionOrNull()?.message)
        session.close()
        assertEquals(3, commands.size)
        assertTrue(commands.none { "app_process" in it })
        assertTrue("rm -rf /data/local/tmp/morphe-scrcpy-1234abcd" in commands.last())
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsUnsafeSessionId() {
        AdbScrcpySession("bad;id", { _, _ -> error("unexpected shell command") })
    }
}
