package dev.n2k02.morpheautomirror

/** Pure validation for the loopback-only video endpoint. No broad URL prefix matches. */
internal object StreamAccess {
    fun authorized(requestLine: String, token: String): Boolean {
        if (token.isEmpty()) return false
        val parts = requestLine.split(' ')
        return parts.size == 3 && parts[0] == "GET" &&
            parts[1] == "/stream?token=$token" &&
            parts[2].startsWith("HTTP/1.")
    }
}
