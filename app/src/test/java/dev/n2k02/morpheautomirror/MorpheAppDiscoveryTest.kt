package dev.n2k02.morpheautomirror

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MorpheAppDiscoveryTest {
    @Test fun findsKnownMorpheApp() =
        assertEquals("app.morphe.android.youtube",
            MorpheAppPolicy.select(listOf("com.google.android.youtube", "app.morphe.android.youtube")))
    @Test fun prefersKnownPackageOverOtherMorpheVariant() =
        assertEquals("app.morphe.android.youtube",
            MorpheAppPolicy.select(listOf("dev.morphe.youtube", "app.morphe.android.youtube")))
    @Test fun ignoresOfficialYouTubeAndMusic() {
        assertFalse(MorpheAppPolicy.eligible("com.google.android.youtube"))
        assertFalse(MorpheAppPolicy.eligible("app.morphe.android.music"))
        assertNull(MorpheAppPolicy.select(listOf("com.google.android.youtube")))
    }
    @Test fun findsDistinctMorpheVariant() {
        assertTrue(MorpheAppPolicy.eligible("dev.morphe.youtube"))
        assertEquals("dev.morphe.youtube", MorpheAppPolicy.select(listOf("dev.morphe.youtube")))
    }
}
