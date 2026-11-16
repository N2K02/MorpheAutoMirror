package dev.n2k02.morpheautomirror

import android.app.Application
import androidx.car.app.connection.CarConnection
import androidx.lifecycle.Observer

class MorpheApplication : Application() {
    private var connection: CarConnection? = null
    private var observer: Observer<Int>? = null
    private var activeConnection = false
    fun startConnectionMonitoring() {
        if (activeConnection) return
        val c = connection ?: return
        val o = observer ?: return
        c.type.observeForever(o)
        activeConnection = true
    }
    fun stopConnectionMonitoring() {
        if (!activeConnection) return
        val c = connection ?: return
        val o = observer ?: return
        c.type.removeObserver(o)
        activeConnection = false
        MirrorGateway.carConnection(false)
        MirrorGateway.carSession(false)
    }
    override fun onCreate() {
        super.onCreate()
        MirrorGateway.init(this)
        LiveUpdater.init(this)
        ShizukuAccess.initialize()
        val car = CarConnection(this)
        val o = Observer<Int> { mode ->
            MirrorGateway.carConnection(mode == CarConnection.CONNECTION_TYPE_PROJECTION ||
                mode == CarConnection.CONNECTION_TYPE_NATIVE)
            if (!MirrorGateway.carAvailable.value) {
                // Do not keep capturing to a stale Android Auto surface after disconnect.
                MirrorGateway.detach()
                MirrorGateway.carSession(false)
            }
            AppLog.add("CAR", "Connection state changed: ${if (MirrorGateway.carAvailable.value) "connected" else "disconnected"}")
        }
        connection = car
        observer = o
        if (MirrorGateway.prefs.enabled) startConnectionMonitoring()
    }
    override fun onTerminate() {
        stopConnectionMonitoring()
        ShizukuAccess.shutdown()
        super.onTerminate()
    }
}

