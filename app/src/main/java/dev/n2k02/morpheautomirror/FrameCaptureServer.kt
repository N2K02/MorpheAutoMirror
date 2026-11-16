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
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Experimental local-only MJPEG transport for Android Auto WebView.
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
    @Volatile private var latestFrame: ByteArray? = null
    private var lastFrameAt = 0L
    private val acceptThread = Thread({
        while (running.get()) {
            val client = try { socket.accept() } catch (_: Exception) { break }
            client.use { deliver(it) }
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
            val request = reader.readLine() ?: return
            while (true) { val line = reader.readLine() ?: return; if (line.isEmpty()) break }
            if (request.startsWith("GET /stream ")) {
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
                client.getOutputStream().write("HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\n\r\n".toByteArray())
            }
        } catch (_: Exception) { /* client disconnected */ }
    }
    override fun close() {
        running.set(false)
        runCatching { socket.close() }
        imageReader.setOnImageAvailableListener(null, null)
        imageReader.close()
        frameThread.quitSafely()
    }
}

