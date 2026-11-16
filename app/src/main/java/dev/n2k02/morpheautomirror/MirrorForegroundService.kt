package dev.n2k02.morpheautomirror

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.IBinder
import android.view.Surface
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * One projection permission token -> one createVirtualDisplay (Android 14+).
 * Subsequent car-surface changes use resize/setSurface, never a second projection display.
 */
class MirrorForegroundService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var web: WebViewMirror? = null
    private var reader: FrameCaptureServer? = null
    private var currentTarget: CarTarget? = null
    private var targetJob: Job? = null
    private var shuttingDown = false

    private val projectionListener = object : MediaProjection.Callback() {
        override fun onStop() {
            AppLog.add("CAPTURE", "MediaProjection revoked")
            MirrorGateway.parked(false)
            MirrorGateway.captureStopped("Capture permission ended")
            stopSelf()
        }
    }

    override fun onCreate() {
        super.onCreate()
        MirrorGateway.init(this)
        targetJob = scope.launch {
            MirrorGateway.surface.collectLatest { target ->
                if (target == null) detach() else attach(target)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                MirrorGateway.parked(false)
                stopSelf()
            }
            ACTION_START -> {
                if (projection != null || shuttingDown) return START_NOT_STICKY
                try {
                    enterForeground()
                    beginProjection(intent)
                } catch (e: Exception) {
                    AppLog.add("CAPTURE", "Cannot start capture: ${e.javaClass.simpleName}")
                    MirrorGateway.parked(false)
                    MirrorGateway.captureStopped("Capture unavailable: ${e.javaClass.simpleName}")
                    stopSelf()
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun enterForeground() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Morphe mirroring",
            NotificationManager.IMPORTANCE_LOW))
        val stopIntent = PendingIntent.getService(this, 7,
            Intent(this, MirrorForegroundService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val openIntent = PendingIntent.getActivity(this, 8, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_app)
            .setContentTitle("MorpheAutoMirror")
            .setContentText("Mirroring Morphe video while parked")
            .setContentIntent(openIntent)
            .setOngoing(true)
            .addAction(R.drawable.ic_stat_app, "Stop mirroring", stopIntent)
            .build()
        if (Build.VERSION.SDK_INT >= 29)
            startForeground(2203, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        else startForeground(2203, notification)
    }

    private fun beginProjection(intent: Intent) {
        if (EnginePolicy.plan(MirrorGateway.state.value.copy(permissionGranted = true)) != NextStep.MIRROR) {
            AppLog.add("CAPTURE", "Conditions changed; not starting")
            stopSelf()
            return
        }
        val code = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
        val data = if (Build.VERSION.SDK_INT >= 33)
            intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_RESULT_DATA)
        } ?: throw IllegalArgumentException("Missing capture consent result")
        val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val p = manager.getMediaProjection(code, data)
            ?: throw SecurityException("Capture consent not accepted")
        p.registerCallback(projectionListener, mainHandler)
        projection = p
        MirrorGateway.captureStarted()
        MirrorGateway.surface.value?.let { attach(it) }
        AppLog.add("CAPTURE", "Projection acquired; mode=${if (MirrorGateway.prefs.webViewMode) "WebView" else "direct"}")
    }

    private fun attach(target: CarTarget) {
        if (target.width <= 0 || target.height <= 0) return
        currentTarget = target
        val p = projection ?: return
        try {
            if (MirrorGateway.prefs.webViewMode) {
                if (reader == null) {
                    val r = FrameCaptureServer()
                    reader = r
                    virtualDisplay = p.createVirtualDisplay("morphe-web-frames",
                        r.width, r.height, r.dpi,
                        DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                        r.inputSurface, null, mainHandler)
                }
                if (web == null) web = WebViewMirror(this, reader!!.port)
                web?.attach(target)
            } else {
                val display = virtualDisplay
                if (display == null) {
                    virtualDisplay = p.createVirtualDisplay("morphe-direct",
                        target.width, target.height, target.dpi,
                        DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                        target.surface, null, mainHandler)
                } else {
                    display.resize(target.width, target.height, target.dpi)
                    display.setSurface(target.surface)
                }
            }
        } catch (e: Exception) {
            AppLog.add("CAPTURE", "Car render failed: ${e.javaClass.simpleName}")
            MirrorGateway.parked(false)
            MirrorGateway.captureStopped("Car render failed: ${e.javaClass.simpleName}")
            stopSelf()
        }
    }

    private fun detach() {
        currentTarget = null
        if (MirrorGateway.prefs.webViewMode) web?.detach()
        else virtualDisplay?.setSurface(null)
    }

    override fun onDestroy() {
        shuttingDown = true
        targetJob?.cancel()
        scope.cancel()
        web?.close()
        web = null
        virtualDisplay?.setSurface(null)
        virtualDisplay?.release()
        virtualDisplay = null
        reader?.close()
        reader = null
        projection?.unregisterCallback(projectionListener)
        projection?.stop()
        projection = null
        MirrorGateway.captureStopped()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "dev.n2k02.morpheautomirror.START"
        const val ACTION_STOP = "dev.n2k02.morpheautomirror.STOP"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"
        private const val CHANNEL = "morphe_mirror"
        fun start(context: Context, code: Int, data: Intent) {
            val i = Intent(context, MirrorForegroundService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_RESULT_CODE, code).putExtra(EXTRA_RESULT_DATA, data)
            androidx.core.content.ContextCompat.startForegroundService(context, i)
        }
        fun stop(context: Context) {
            context.stopService(Intent(context, MirrorForegroundService::class.java))
        }
    }
}

