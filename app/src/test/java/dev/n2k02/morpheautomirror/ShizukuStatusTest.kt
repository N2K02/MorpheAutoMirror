package dev.n2k02.morpheautomirror

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShizukuStatusTest {
    @Test fun disconnectedManagerIsNotAuthorized() {
        val state = ShizukuStatus()
        assertFalse(state.readyForShell)
        assertEquals("Not connected", state.label)
    }

    @Test fun installedOrRunningManagerIsNotImplicitPermission() {
        val state = ShizukuStatus(binderAlive = true)
        assertFalse(state.readyForShell)
        assertEquals("Permission required", state.label)
    }

    @Test fun explicitGrantPlusShellIdentityIsReady() {
        val state = ShizukuStatus(binderAlive = true, permissionGranted = true, shellUid = 2000)
        assertTrue(state.readyForShell)
        assertEquals("Authorized (UID 2000)", state.label)
    }

    @Test fun rootModeCanBeReadyWhenExplicitlyAuthorized() {
        assertTrue(ShizukuStatus(true, true, 0).readyForShell)
    }

    @Test fun unauthorizedAppUidCannotPass() {
        val state = ShizukuStatus(binderAlive = true, permissionGranted = true, shellUid = 10380)
        assertFalse(state.readyForShell)
        assertEquals("Unexpected UID", state.label)
    }

    @Test fun deniedPermissionIsVisible() {
        val state = ShizukuStatus(binderAlive = true, deniedPermanently = true)
        assertFalse(state.readyForShell)
        assertEquals("Permission denied", state.label)
    }
}
