package dev.n2k02.morpheautomirror

import android.app.Activity
import android.content.Context
import android.os.Build
import dev.n2k02.updater.UpdateConfig
import dev.n2k02.updater.UpdateInstallGuard
import dev.n2k02.updater.UpdaterController
import kotlinx.coroutines.flow.MutableStateFlow

/** Same shared updater as mRadio, with independent per-app stable/experimental selection. */
internal object LiveUpdater {
    private lateinit var app: Context
    private var current: UpdaterController? = null
    val experimentalEnabled = MutableStateFlow(false)
    val installGuard = UpdateInstallGuard { !MirrorGateway.state.value.capturing }
    fun init(context: Context) {
        app = context.applicationContext
        if (current != null) return
        experimentalEnabled.value = prefs().getBoolean("experimentalEnabled", false)
        current = createController()
    }
    private fun prefs() = app.getSharedPreferences("app_updater", Context.MODE_PRIVATE)
    @Suppress("DEPRECATION")
    private fun versionCode(): Long {
        val info = app.packageManager.getPackageInfo(app.packageName, 0)
        return if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
    }
    private fun createController() = UpdaterController(
        context = app,
        config = UpdateConfig(repository = "N2K02/MorpheAutoMirror",
            packageName = app.packageName, installedVersionCode = versionCode(),
            experimentalEnabled = experimentalEnabled.value),
        offerPreferences = prefs()
    )
    val controller: UpdaterController get() = checkNotNull(current)
    fun setExperimentalEnabled(value: Boolean) {
        if (experimentalEnabled.value == value) return
        current?.cancel()
        prefs().edit().putBoolean("experimentalEnabled", value).apply()
        experimentalEnabled.value = value
        current = createController()
        controller.check(manual = true)
    }
    fun onResume(activity: Activity) = controller.onHostResume(activity, installGuard)
}

