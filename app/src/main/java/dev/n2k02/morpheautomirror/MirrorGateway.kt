package dev.n2k02.morpheautomirror

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.view.Surface
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

internal data class CarTarget(val surface: Surface, val width: Int, val height: Int, val dpi: Int)

/** Single source of truth for playback, car host, parked approval and capture lifetime. */
internal object MirrorGateway {
    val state = MutableStateFlow(MirrorState())
    val surface = MutableStateFlow<CarTarget?>(null)
    val carAvailable = MutableStateFlow(false)
    val listenerConnectedAt = MutableStateFlow<Long?>(null)
    val carConnectedAt = MutableStateFlow<Long?>(null)
    val mediaSessionActive = MutableStateFlow(false)
    fun listenerConnection(connected: Boolean) {
        listenerConnectedAt.value = if (connected) listenerConnectedAt.value ?: SystemClock.elapsedRealtime() else null
        if (!connected) mediaSessionActive.value = false
        AppLog.add("MEDIA", "Notification listener connected=$connected")
    }
    fun carConnection(connected: Boolean) {
        carAvailable.value = connected
        carConnectedAt.value = if (connected) carConnectedAt.value ?: SystemClock.elapsedRealtime() else null
    }
    val lastError = MutableStateFlow("")
    lateinit var prefs: Preferences
        private set
    private var context: Context? = null
    private var permissionNotificationShown = false

    fun init(ctx: Context) {
        if (context == null) {
            context = ctx.applicationContext
            prefs = Preferences(ctx)
            state.update { it.copy(monitoring = prefs.enabled) }
        }
    }

    fun playing(value: Boolean, paused: Boolean = false) {
        if (state.value.morphePlaying == value && state.value.morphePaused == paused) return
        state.update { it.copy(morphePlaying = value, morphePaused = paused) }
        AppLog.add("MEDIA", "Morphe state=" + when {
            value -> "playing"
            paused -> "paused"
            else -> "stopped"
        })
        sync()
    }
    fun carSession(value: Boolean) {
        state.update { it.copy(carSession = value) }
        if (!value) {
            surface.value = null
            state.update { it.copy(surfaceReady = false) }
        }
        AppLog.add("CAR", "Car app session active=$value")
        sync()
    }
    fun parked(value: Boolean) {
        // Legacy callers retained; parked confirmation is no longer part of MirrorState.
        AppLog.add("SAFE", "Parked confirmation=$value")
        sync()
    }
    fun attach(target: CarTarget) {
        surface.value = target
        state.update { it.copy(surfaceReady = true) }
        AppLog.add("CAR", "Car surface ${target.width}x${target.height}")
        sync()
    }
    fun detach() {
        surface.value = null
        state.update { it.copy(surfaceReady = false) }
        sync()
    }
    fun monitoring(value: Boolean) {
        prefs.enabled = value
        state.update { it.copy(monitoring = value) }
        MorpheNotificationListener.updateEnabled(value)
        val app = context as? MorpheApplication
        if (value) app?.startConnectionMonitoring() else app?.stopConnectionMonitoring()
        if (!value) {
            parked(false)
            playing(false)
        } else sync()
    }
    fun captureStarted() {
        permissionNotificationShown = false
        state.update { it.copy(permissionGranted = true, capturing = true) }
        sync()
    }
    fun captureStopped(message: String = "") {
        if (message.isNotEmpty()) {
            lastError.value = message
            AppLog.add("CAPTURE", message)
        }
        state.update { it.copy(permissionGranted = false, capturing = false) }
    }
    fun stopCapture() {
        context?.let { MirrorForegroundService.stop(it) }
        state.update { it.copy(permissionGranted = false, capturing = false) }
    }
    fun quit() {
        state.value = MirrorState(monitoring = false)
        listenerConnectedAt.value = null
        carConnectedAt.value = null
        mediaSessionActive.value = false
        surface.value = null
        prefs.enabled = false
        permissionNotificationShown = false
        stopCapture()
        MorpheNotificationListener.updateEnabled(false)
        (context as? MorpheApplication)?.stopConnectionMonitoring()
        context?.getSystemService(NotificationManager::class.java)?.cancel(PERMISSION_NOTIFICATION)
        AppLog.add("APP", "Quit requested; monitoring disabled")
    }

    fun sync() {
        val ctx = context ?: return
        val s = state.value
        when (EnginePolicy.plan(s)) {
            NextStep.STOP -> {
                stopCapture()
                permissionNotificationShown = false
            }
            NextStep.ASK_PERMISSION -> if (!permissionNotificationShown) {
                permissionNotificationShown = true
                askByNotification(ctx)
            }
            else -> {
                permissionNotificationShown = false
                ctx.getSystemService(NotificationManager::class.java).cancel(PERMISSION_NOTIFICATION)
            }
        }
    }

    private fun askByNotification(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(
            "morphe_setup", "Morphe Auto Mirror", NotificationManager.IMPORTANCE_DEFAULT))
        val intent = Intent(ctx, MainActivity::class.java).apply {
            action = MainActivity.ACTION_CAPTURE
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pending = PendingIntent.getActivity(ctx, 12, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(ctx, "morphe_setup")
            .setSmallIcon(R.drawable.ic_stat_morphe)
            .setContentTitle("Morphe video ready to mirror")
            .setContentText("Open on phone to grant screen capture (parked only)")
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        try { nm.notify(PERMISSION_NOTIFICATION, n) } catch (e: SecurityException) {
            AppLog.add("PERMISSION", "Notification permission missing")
        }
    }
    private const val PERMISSION_NOTIFICATION = 2202
}

