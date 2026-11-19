package dev.n2k02.morpheautomirror

import org.junit.Assert.assertEquals
import org.junit.Test

class PageNavigationTest {
    @Test fun settingsReturnsToHome() =
        assertEquals("Home", PageNavigation.parent("Settings"))

    @Test fun everySettingsSubpageReturnsToSettings() {
        listOf("Monitoring", "ADB", "Updates").forEach {
            assertEquals("Settings", PageNavigation.parent(it))
        }
    }

    @Test fun unknownPageRecoversToHome() =
        assertEquals("Home", PageNavigation.parent("unknown"))
}
