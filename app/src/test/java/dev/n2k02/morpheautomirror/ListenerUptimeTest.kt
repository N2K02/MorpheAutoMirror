package dev.n2k02.morpheautomirror

import org.junit.Assert.assertEquals
import org.junit.Test

class ListenerUptimeTest {
    @Test fun disconnectedHasNoUptime() = assertEquals("Not connected", ListenerUptime.format(null, 5000))
    @Test fun secondsAndMinutes() {
        assertEquals("00:09", ListenerUptime.format(1000, 10000))
        assertEquals("02:05", ListenerUptime.format(1000, 126000))
    }
    @Test fun hours() = assertEquals("1:01:01", ListenerUptime.format(1000, 3662000))
    @Test fun monotonicSafety() = assertEquals("00:00", ListenerUptime.format(9000, 1000))
}
