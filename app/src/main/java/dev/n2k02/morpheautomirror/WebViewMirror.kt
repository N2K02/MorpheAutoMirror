package dev.n2k02.morpheautomirror

import android.app.Presentation
import android.content.Context
import android.graphics.Color
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.view.Surface
import android.webkit.WebSettings
import android.webkit.WebView

/**
 * WebView rendered on a private virtual display whose output is Android Auto's surface.
 * The HTML is static and renders a local loopback image stream only.
 */
internal class WebViewMirror(private val context: Context, private val port: Int, private val token: String) : AutoCloseable {
    private val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
    private var display: VirtualDisplay? = null
    private var presentation: Presentation? = null
    private var browser: WebView? = null

    fun attach(target: CarTarget) {
        detach()
        val virtualDisplay = displayManager.createVirtualDisplay("morphe-car-webview",
            target.width, target.height, target.dpi, target.surface,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION)
        display = virtualDisplay
        val dialog = Presentation(context, virtualDisplay.display)
        val browser = WebView(dialog.context)
        this.browser = browser
        browser.setBackgroundColor(Color.BLACK)
        browser.settings.javaScriptEnabled = false
        browser.settings.allowFileAccess = false
        browser.settings.allowContentAccess = false
        browser.settings.cacheMode = WebSettings.LOAD_NO_CACHE
        browser.settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        val html = """<!doctype html><meta name="viewport" content="width=device-width, initial-scale=1">
            <style>body{margin:0;background:#000;display:grid;place-items:center;height:100vh}
            img{width:100vw;height:100vh;object-fit:contain}</style>
            <img src="http://127.0.0.1:$port/stream?token=$token" alt="Morphe screen">"""
        browser.loadDataWithBaseURL("http://127.0.0.1:$port/", html, "text/html", "utf-8", null)
        dialog.setContentView(browser)
        dialog.show()
        presentation = dialog
    }

    fun detach() {
        // Close the MJPEG request and renderer before releasing its output display.
        browser?.stopLoading()
        presentation?.dismiss()
        presentation = null
        browser?.destroy()
        browser = null
        display?.release()
        display = null
    }
    override fun close() = detach()
}

