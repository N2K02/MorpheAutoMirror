package dev.n2k02.morpheautomirror

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamAccessTest {
    private val token = "abc123def456"

    @Test fun allowsOnlyExactAuthenticatedStreamUrl() {
        assertTrue(StreamAccess.authorized("GET /stream?token=$token HTTP/1.1", token))
    }
    @Test fun rejectsMissingOrIncorrectToken() {
        assertFalse(StreamAccess.authorized("GET /stream HTTP/1.1", token))
        assertFalse(StreamAccess.authorized("GET /stream?token=wrong HTTP/1.1", token))
        assertFalse(StreamAccess.authorized("GET /stream?token=$token HTTP/1.1", ""))
    }
    @Test fun rejectsExtraQueryParametersAndPrefixes() {
        assertFalse(StreamAccess.authorized("GET /stream?token=${token}evil HTTP/1.1", token))
        assertFalse(StreamAccess.authorized("GET /stream?token=$token&other=1 HTTP/1.1", token))
    }
    @Test fun rejectsOtherMethodsAndMalformedRequests() {
        assertFalse(StreamAccess.authorized("POST /stream?token=$token HTTP/1.1", token))
        assertFalse(StreamAccess.authorized("GET /stream?token=$token", token))
        assertFalse(StreamAccess.authorized("GET /stream?token=$token HTTP/2", token))
    }
}
