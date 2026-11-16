package dev.n2k02.morpheautomirror

import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.car.app.AppManager
import androidx.car.app.CarAppService
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.SurfaceCallback
import androidx.car.app.SurfaceContainer
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.Template
import androidx.car.app.navigation.model.NavigationTemplate
import androidx.car.app.validation.HostValidator
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

/** Personal-use projected-car integration. The host cannot be programmatically launched. */
class MorpheCarAppService : CarAppService() {
    override fun createHostValidator(): HostValidator = HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
    override fun onCreateSession(): Session = MorpheCarSession()
}
private class MorpheCarSession : Session(), DefaultLifecycleObserver {
    init { lifecycle.addObserver(this) }
    override fun onCreateScreen(intent: Intent): Screen {
        MirrorGateway.init(carContext)
        return CarMirrorScreen(carContext)
    }
    override fun onDestroy(owner: LifecycleOwner) {
        MirrorGateway.detach()
        MirrorGateway.carSession(false)
    }
}

/** One SurfaceCallback for the whole car screen lifetime; no duplicate ownership. */
private class CarMirrorScreen(carContext: CarContext) : Screen(carContext), DefaultLifecycleObserver {
    private var heldSurface: android.view.Surface? = null
    private val callback = object : SurfaceCallback {
        override fun onSurfaceAvailable(container: SurfaceContainer) {
            val s = container.surface ?: return
            heldSurface = s
            if (!MirrorGateway.state.value.capturing) drawIdle(s, container.width, container.height)
            MirrorGateway.attach(CarTarget(s, container.width.coerceAtLeast(1),
                container.height.coerceAtLeast(1), container.dpi.coerceAtLeast(160)))
        }
        override fun onSurfaceDestroyed(container: SurfaceContainer) {
            MirrorGateway.detach()
            heldSurface = null
            container.surface?.release()
        }
    }
    init { lifecycle.addObserver(this) }
    override fun onStart(owner: LifecycleOwner) {
        MirrorGateway.carSession(true)
        runCatching {
            carContext.getCarService(AppManager::class.java).setSurfaceCallback(callback)
        }.onFailure { AppLog.add("CAR", "Surface access unavailable: ${it.javaClass.simpleName}") }
    }
    override fun onStop(owner: LifecycleOwner) {
        MirrorGateway.detach()
        MirrorGateway.carSession(false)
        runCatching { carContext.getCarService(AppManager::class.java).setSurfaceCallback(null) }
    }
    override fun onGetTemplate(): Template {
        val strip = ActionStrip.Builder()
            .addAction(Action.Builder().setTitle("Stop").setOnClickListener {
                MirrorGateway.parked(false)
                MirrorGateway.stopCapture()
            }.build())
            .build()
        return NavigationTemplate.Builder().setActionStrip(strip).build()
    }
    private fun drawIdle(surface: android.view.Surface, width: Int, height: Int) {
        var canvas: Canvas? = null
        try {
            canvas = surface.lockCanvas(null)
            canvas.drawColor(Color.BLACK)
            val paint = Paint(3).apply {
                color = Color.WHITE
                textSize = (width * .045f).coerceIn(18f, 36f)
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText("MorpheAutoMirror · Parked only", width / 2f, height / 2f, paint)
        } catch (e: Exception) {
            AppLog.add("CAR", "Unable to draw standby surface: ${e.javaClass.simpleName}")
        } finally {
            canvas?.let { runCatching { surface.unlockCanvasAndPost(it) } }
        }
    }
}

