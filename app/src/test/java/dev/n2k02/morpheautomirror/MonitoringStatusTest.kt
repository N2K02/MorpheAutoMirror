package dev.n2k02.morpheautomirror

import org.junit.Assert.assertEquals
import org.junit.Test

class MonitoringStatusTest {
    @Test fun badgesDescribeActualReadiness() {
        assertEquals("Monitoring off", MonitoringStatus.label(false, true, true, true))
        assertEquals("Morphe not found", MonitoringStatus.label(true, false, true, true))
        assertEquals("Access required", MonitoringStatus.label(true, true, false, false))
        assertEquals("Connecting", MonitoringStatus.label(true, true, true, false))
        assertEquals("Monitoring active", MonitoringStatus.label(true, true, true, true))
    }
}
