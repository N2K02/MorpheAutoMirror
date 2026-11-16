package dev.n2k02.morpheautomirror

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.ComponentName
import android.content.ServiceConnection
import android.content.pm.ServiceInfo
import android.media.MediaCodec
import android.media.MediaFormat
import android.net.LocalSocket
import android.net.LocalSocketAddress
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.IOException
import java.security.SecureRandom
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Experimental same-device scrcpy client: shell-owned capture, Android MediaCodec decode
 * straight onto the Android Auto Surface. No browser, audio duplication or remote network.
 * A live, explicitly granted LocalAdb/Shizuku Binder is required.
 */
class ScrcpyMirrorService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var socket: LocalSocket? = null
    @Volatile private var shell: IScrcpyShellService? = null
    private var shellConnection: ServiceConnection? = null
    private val shellArgs by lazy {
        Shizuku.UserServiceArgs(ComponentName(this, ScrcpyShellUserService::class.java))
            .daemon(false).tag("morphe-scrcpy").version(1).processNameSuffix("scrcpy")
    }
    @Volatile private var decoder: MediaCodec? = null
    private var job: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action != ACTION_START || job != null) return START_NOT_STICKY
        try {
            enterForeground()
        } catch (e: Exception) {
            AppLog.add("SCRCPY", "Foreground startup failed: ${e.javaClass.simpleName}")
            stopSelf()
            return START_NOT_STICKY
        }
        job = scope.launch {
            try {
                check(ShizukuAccess.status.value.readyForShell) { "LocalAdb Shizuku permission unavailable" }
                val target = MirrorGateway.surface.value ?: error("Android Auto surface unavailable")
                runScrcpy(target)
            } catch (e: Exception) {
                AppLog.add("SCRCPY", "Mirror stopped: ${e.javaClass.simpleName}: ${e.message.orEmpty().take(120)}")
                withContext(Dispatchers.Main) {
                    MirrorGateway.captureStopped("scrcpy: ${e.javaClass.simpleName}")
                }
            } finally {
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun enterForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Morphe scrcpy mirror", NotificationManager.IMPORTANCE_LOW))
        val stop = PendingIntent.getService(this, 721,
            Intent(this, ScrcpyMirrorService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_morphe)
            .setContentTitle("MorpheAutoMirror")
            .setContentText("scrcpy screen mirror active")
            .setOngoing(true)
            .addAction(R.drawable.ic_stat_morphe, "Stop mirroring", stop)
            .build()
        if (Build.VERSION.SDK_INT >= 29)
            startForeground(721, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        else startForeground(721, notification)
    }

    private fun runScrcpy(target: CarTarget) {
        // Do not attempt a second capture if the car session has already gone away.
        check(MirrorGateway.state.value.carSession && MirrorGateway.state.value.surfaceReady) {
            "Android Auto car session unavailable"
        }
        // Shizuku 13.1.5 makes newProcess private; use the supported UserService
        // process (which runs as UID 2000) instead of invoking private APIs.
        val privileged = bindShell()
        assets.open("scrcpy-server-v3.3.4").use { source ->
            privileged.stageServer(source.readBytes())
        }
        val id = SecureRandom().nextInt(Int.MAX_VALUE)
        val scid = String.format(Locale.ROOT, "%08x", id)
        privileged.startServer(scid)

        val client = LocalSocket()
        var connected = false
        for (attempt in 0 until 40) {
            if (!scope.isActive) break
            try {
                client.connect(LocalSocketAddress("scrcpy_$scid", LocalSocketAddress.Namespace.ABSTRACT))
                connected = true
                break
            } catch (_: IOException) {
                Thread.sleep(100)
            }
        }
        check(connected) {
            "Could not connect to local scrcpy socket: ${runCatching { privileged.lastError }.getOrDefault("").take(100)}"
        }
        socket = client
        client.soTimeout = 5000
        val input = DataInputStream(BufferedInputStream(client.inputStream))
        val header = ScrcpyWire.readHeader(input)
        AppLog.add("SCRCPY", "H264 stream ${header.width}x${header.height}")

        val mediaFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, header.width, header.height)
        val codec = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        decoder = codec
        codec.configure(mediaFormat, target.surface, null, 0)
        codec.start()
        val info = MediaCodec.BufferInfo()
        var rendered = false
        while (scope.isActive && MirrorGateway.surface.value?.surface === target.surface) {
            val packet = ScrcpyWire.readPacket(input)
            val index = codec.dequeueInputBuffer(20_000L)
            if (index >= 0) {
                val buffer = codec.getInputBuffer(index) ?: error("Missing decoder buffer")
                buffer.clear()
                check(packet.payload.size <= buffer.remaining()) { "scrcpy frame larger than decoder buffer" }
                buffer.put(packet.payload)
                val flags = when {
                    packet.isConfig -> MediaCodec.BUFFER_FLAG_CODEC_CONFIG
                    packet.isKeyFrame -> MediaCodec.BUFFER_FLAG_KEY_FRAME
                    else -> 0
                }
                codec.queueInputBuffer(index, 0, packet.payload.size, packet.ptsUs, flags)
            }
            while (true) {
                val output = codec.dequeueOutputBuffer(info, 0)
                if (output >= 0) {
                    codec.releaseOutputBuffer(output, true)
                    if (!rendered) {
                        rendered = true
                        AppLog.add("SCRCPY", "First frame decoded to Android Auto Surface")
                        scope.launch(Dispatchers.Main) { MirrorGateway.captureStarted() }
                    }
                } else if (output == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    AppLog.add("SCRCPY", "Decoder output format changed")
                } else break
            }
        }
    }

    private fun bindShell(): IScrcpyShellService {
        check(ShizukuAccess.status.value.readyForShell) { "Shizuku not authorized" }
        val latch = CountDownLatch(1)
        var remote: IScrcpyShellService? = null
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                remote = IScrcpyShellService.Stub.asInterface(binder)
                latch.countDown()
            }
            override fun onServiceDisconnected(name: ComponentName) {
                shell = null
                latch.countDown()
            }
        }
        shellConnection = connection
        Shizuku.bindUserService(shellArgs, connection)
        check(latch.await(12, TimeUnit.SECONDS)) { "Shizuku UserService connection timed out" }
        val bridge = remote ?: error("LocalAdb Shizuku UserService unavailable")
        check(bridge.processUid == 2000 || bridge.processUid == 0) {
            "Shizuku UserService did not run as shell/root"
        }
        shell = bridge
        return bridge
    }

    override fun onDestroy() {
        job?.cancel()
        runCatching { socket?.close() }
        runCatching { shell?.stopServer() }
        shellConnection?.let { connection ->
            runCatching { Shizuku.unbindUserService(shellArgs, connection, true) }
        }
        shell = null
        shellConnection = null
        runCatching { decoder?.stop() }
        runCatching { decoder?.release() }
        decoder = null
        scope.cancel()
        MirrorGateway.captureStopped()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "morphe_scrcpy"
        private const val ACTION_START = "dev.n2k02.morpheautomirror.SCRCPY_START"
        private const val ACTION_STOP = "dev.n2k02.morpheautomirror.SCRCPY_STOP"

        fun start(context: Context) {
            ContextCompat.startForegroundService(context,
                Intent(context, ScrcpyMirrorService::class.java).setAction(ACTION_START))
        }
        fun stop(context: Context) {
            context.stopService(Intent(context, ScrcpyMirrorService::class.java))
        }
    }
}
