package dev.n2k02.morpheautomirror

import android.app.Application
import androidx.car.app.connection.CarConnection
import androidx.lifecycle.Observer

class MorpheApplication : Application() {
    private var connection: CarConnection? = null
    private var observer: Observer<Int>? = null
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
        car.type.observeForever(o)
        connection = car
        observer = o
    }
    override fun onTerminate() {
        observer?.let { o -> connection?.type?.removeObserver(o) }
        super.onTerminate()
    }
}

