package dev.n2k02.morpheautomirror

/** Strict routing for the local MJPEG and GeckoView viewer endpoints. */
internal object StreamAccess {
    fun authorized(requestLine: String, token: String): Boolean =
        exactGet(requestLine, "/stream?token=$token", token)

    fun authorizedViewer(requestLine: String, token: String): Boolean =
        exactGet(requestLine, "/viewer?token=$token", token)

    fun viewerHtml(token: String): String {
        require(token.matches(Regex("[0-9a-f]{48}"))) { "Invalid session token" }
        return """<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <style>
    html, body { margin:0; width:100%; height:100%; overflow:hidden; background:#000; }
    body { display:grid; place-items:center; }
    img { width:100vw; height:100vh; object-fit:contain; }
  </style>
</head>
<body><img src="/stream?token=$token" alt="Morphe video"></body>
</html>"""
    }

    private fun exactGet(requestLine: String, path: String, token: String): Boolean {
        if (token.isEmpty()) return false
        val parts = requestLine.split(' ')
        return parts.size == 3 && parts[0] == "GET" &&
            parts[1] == path && parts[2].startsWith("HTTP/1.")
    }
}
