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
        MirrorGateway.carAvailable.value = false
    }
    override fun onCreate() {
        super.onCreate()
        MirrorGateway.init(this)
        LiveUpdater.init(this)
        val car = CarConnection(this)
        val o = Observer<Int> { mode ->
            MirrorGateway.carAvailable.value = mode == CarConnection.CONNECTION_TYPE_PROJECTION ||
                mode == CarConnection.CONNECTION_TYPE_NATIVE
            AppLog.add("CAR", "Connection state changed: ${if (MirrorGateway.carAvailable.value) "connected" else "disconnected"}")
        }
        connection = car
        observer = o
        if (MirrorGateway.prefs.enabled) startConnectionMonitoring()
    }
    override fun onTerminate() {
        stopConnectionMonitoring()
        super.onTerminate()
    }
}

