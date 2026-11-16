package dev.n2k02.morpheautomirror

import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.view.Surface
import java.io.ByteArrayOutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.SecureRandom
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Experimental local-only MJPEG transport for Android Auto GeckoView.
 * Bounded resolution/frame rate and one connection, no Internet listener.
 */
internal class FrameCaptureServer : AutoCloseable {
    val width = 640
    val height = 360
    val dpi = 160
    private val running = AtomicBoolean(true)
    private val frameThread = HandlerThread("morphe-frames").apply { start() }
    private val imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
    val inputSurface: Surface get() = imageReader.surface
    private val socket = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
    val port: Int = socket.localPort
    val accessToken: String = ByteArray(24).also { SecureRandom().nextBytes(it) }
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }
    @Volatile private var activeClient: Socket? = null
    @Volatile private var latestFrame: ByteArray? = null
    private var lastFrameAt = 0L
    private val acceptThread = Thread({
        while (running.get()) {
            val client = try { socket.accept() } catch (_: Exception) { break }
            activeClient = client
            try { client.use { deliver(it) } } finally { activeClient = null }
        }
    }, "morphe-loopback").apply { isDaemon = true; start() }

    init {
        imageReader.setOnImageAvailableListener({ r ->
            val image = r.acquireLatestImage() ?: return@setOnImageAvailableListener
            try {
                val now = android.os.SystemClock.elapsedRealtime()
                if (now - lastFrameAt < 200L) return@setOnImageAvailableListener
                lastFrameAt = now
                val plane = image.planes[0]
                val buffer = plane.buffer
                buffer.rewind()
                val rowPadding = plane.rowStride - plane.pixelStride * width
                val paddedWidth = width + rowPadding / plane.pixelStride
                val padded = Bitmap.createBitmap(paddedWidth, height, Bitmap.Config.ARGB_8888)
                try {
                    padded.copyPixelsFromBuffer(buffer)
                    val cropped = Bitmap.createBitmap(padded, 0, 0, width, height)
                    try {
                        val bytes = ByteArrayOutputStream(40_000)
                        cropped.compress(Bitmap.CompressFormat.JPEG, 65, bytes)
                        latestFrame = bytes.toByteArray()
                    } finally { cropped.recycle() }
                } finally { padded.recycle() }
            } catch (e: Exception) {
                AppLog.add("WEB", "Frame conversion failed: ${e.javaClass.simpleName}")
            } finally { image.close() }
        }, Handler(frameThread.looper))
    }
    private fun deliver(client: Socket) {
        try {
            client.soTimeout = 5000
            val reader = client.getInputStream().bufferedReader()
            val request = reader.readLine()?.takeIf { it.length <= 4096 } ?: return
            var headerCount = 0
            while (true) {
                val line = reader.readLine()?.takeIf { it.length <= 4096 } ?: return
                if (line.isEmpty()) break
                if (++headerCount > 40) return
            }
            if (StreamAccess.authorizedViewer(request, accessToken)) {
                val body = StreamAccess.viewerHtml(accessToken).toByteArray(Charsets.UTF_8)
                val headers = (
                    "HTTP/1.1 200 OK\r\n" +
                    "Content-Type: text/html; charset=utf-8\r\n" +
                    "Content-Length: ${body.size}\r\n" +
                    "Cache-Control: no-store\r\n" +
                    "X-Content-Type-Options: nosniff\r\n" +
                    "Referrer-Policy: no-referrer\r\n" +
                    "Content-Security-Policy: default-src 'none'; img-src 'self'; style-src 'unsafe-inline'; base-uri 'none'\r\n" +
                    "Connection: close\r\n\r\n"
                )
                client.getOutputStream().apply {
                    write(headers.toByteArray(Charsets.US_ASCII))
                    write(body)
                    flush()
                }
            } else if (StreamAccess.authorized(request, accessToken)) {
                val output = client.getOutputStream()
                output.write(("HTTP/1.1 200 OK\r\nContent-Type: multipart/x-mixed-replace; boundary=frame\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n").toByteArray())
                while (running.get()) {
                    val data = latestFrame
                    if (data != null) {
                        output.write(("--frame\r\nContent-Type: image/jpeg\r\nContent-Length: ${data.size}\r\n\r\n").toByteArray())
                        output.write(data)
                        output.write("\r\n".toByteArray())
                        output.flush()
                    }
                    Thread.sleep(200)
                }
            } else {
                client.getOutputStream().write("HTTP/1.1 403 Forbidden\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
            }
        } catch (_: Exception) { /* client disconnected */ }
    }
    override fun close() {
        running.set(false)
        runCatching { socket.close() }
        runCatching { activeClient?.close() }
        imageReader.setOnImageAvailableListener(null, null)
        imageReader.close()
        frameThread.quitSafely()
    }
}

