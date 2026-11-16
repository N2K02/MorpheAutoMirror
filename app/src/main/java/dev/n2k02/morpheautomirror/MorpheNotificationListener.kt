package dev.n2k02.morpheautomirror

import android.content.ComponentName
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService

/** Tracks only the selected Morphe package; no notification contents are read. */
class MorpheNotificationListener : NotificationListenerService() {
    companion object {
        @Volatile private var active: MorpheNotificationListener? = null
        fun refreshPackage() {
            active?.let { service -> service.handler.post { service.refresh() } }
        }
        fun updateEnabled(enabled: Boolean) {
            active?.let { service ->
                service.handler.post {
                    if (enabled) service.beginObserving() else service.stopObserving()
                }
            }
        }
    }
    private var observing = false
    private val handler = Handler(Looper.getMainLooper())
    private var sessionManager: MediaSessionManager? = null
    private var activeController: MediaController? = null
    private val callbacks = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) = publish(state)
        override fun onSessionDestroyed() = refresh()
    }
    private val sessions = MediaSessionManager.OnActiveSessionsChangedListener { refresh(it) }

    override fun onListenerConnected() {
        super.onListenerConnected()
        MirrorGateway.init(applicationContext)
        active = this
        MirrorGateway.listenerConnection(true)
        sessionManager = getSystemService(MEDIA_SESSION_SERVICE) as MediaSessionManager
        if (MirrorGateway.prefs.enabled) beginObserving()
    }
    private fun beginObserving() {
        if (observing || !MirrorGateway.prefs.enabled) return
        runCatching {
            sessionManager?.addOnActiveSessionsChangedListener(
                sessions, ComponentName(this, javaClass), handler)
            observing = true
            refresh()
        }.onFailure { AppLog.add("MEDIA", "Unable to watch media sessions: ${it.javaClass.simpleName}") }
    }

    override fun onNotificationPosted(sbn: android.service.notification.StatusBarNotification?) {
        if (observing && sbn != null && EnginePolicy.isMorphe(sbn.packageName, MirrorGateway.prefs.morphePackage)) refresh()
    }
    override fun onNotificationRemoved(sbn: android.service.notification.StatusBarNotification?) {
        if (observing && sbn != null && EnginePolicy.isMorphe(sbn.packageName, MirrorGateway.prefs.morphePackage)) refresh()
    }

    private fun refresh(active: List<MediaController>? = null) {
        if (!MirrorGateway.prefs.enabled) { unwatch(); return }
        val name = MirrorGateway.prefs.morphePackage
        val controllers = active ?: runCatching {
            sessionManager?.getActiveSessions(ComponentName(this, javaClass))
        }.getOrNull().orEmpty()
        val chosen = controllers.firstOrNull { EnginePolicy.isMorphe(it.packageName, name) }
        if (chosen?.sessionToken != activeController?.sessionToken) {
            activeController?.unregisterCallback(callbacks)
            activeController = chosen
            chosen?.registerCallback(callbacks, handler)
        }
        MirrorGateway.mediaSessionActive.value = chosen != null
        publish(chosen?.playbackState)
    }
    private fun publish(playback: PlaybackState?) {
        MirrorGateway.playing(MirrorGateway.prefs.enabled && playback?.state == PlaybackState.STATE_PLAYING)
    }
    private fun stopObserving() {
        if (observing) sessionManager?.removeOnActiveSessionsChangedListener(sessions)
        observing = false
        unwatch()
    }
    private fun unwatch() {
        activeController?.unregisterCallback(callbacks)
        activeController = null
        MirrorGateway.mediaSessionActive.value = false
        MirrorGateway.playing(false)
    }
    override fun onListenerDisconnected() {
        stopObserving()
        if (active === this) {
            active = null
            MirrorGateway.listenerConnection(false)
        }
        super.onListenerDisconnected()
    }
    override fun onDestroy() {
        stopObserving()
        if (active === this) active = null
        super.onDestroy()
    }
}

