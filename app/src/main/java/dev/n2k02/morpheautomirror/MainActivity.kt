package dev.n2k02.morpheautomirror

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.widget.ImageView
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Update
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import android.os.SystemClock
import kotlinx.coroutines.delay
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationManagerCompat
import dev.n2k02.theme.N2K02Theme
import dev.n2k02.updater.N2K02UpdateScreen
import kotlinx.coroutines.flow.MutableStateFlow

/** mRadio navigation pattern: one header on every page; Home / Settings / Quit. */
class MainActivity : ComponentActivity() {
    companion object { const val ACTION_CAPTURE = "dev.n2k02.morpheautomirror.CAPTURE" }
    private val actionCapture = MutableStateFlow(false)
    private val uiRefresh = MutableStateFlow(0)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MirrorGateway.init(this)
        LiveUpdater.init(this)
        actionCapture.value = intent?.action == ACTION_CAPTURE
        setContent { N2K02Theme { MorpheUi() } }
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        actionCapture.value = intent.action == ACTION_CAPTURE
    }
    override fun onResume() {
        super.onResume()
        LiveUpdater.onResume(this)
        uiRefresh.value = uiRefresh.value + 1
    }
    private fun quit() {
        MirrorGateway.quit()
        finishAndRemoveTask()
    }

    @Composable
    private fun MorpheUi() {
        val s by MirrorGateway.state.collectAsState()
        val available by MirrorGateway.carAvailable.collectAsState()
        val error by MirrorGateway.lastError.collectAsState()
        val experimental by LiveUpdater.experimentalEnabled.collectAsState()
        var page by rememberSaveable { mutableStateOf("Home") }
        val listenerStarted by MirrorGateway.listenerConnectedAt.collectAsState()
        val carStarted by MirrorGateway.carConnectedAt.collectAsState()
        val sessionFound by MirrorGateway.mediaSessionActive.collectAsState()
        var uptimeNow by remember { mutableStateOf(SystemClock.elapsedRealtime()) }
        LaunchedEffect(page) {
            while (page == "Home") {
                uptimeNow = SystemClock.elapsedRealtime()
                delay(1000)
            }
        }
        BackHandler(enabled = page != "Home") { page = PageNavigation.parent(page) }
        var monitoring by remember { mutableStateOf(MirrorGateway.prefs.enabled) }
        var installedMorphe by remember { mutableStateOf<String?>(null) }
        var listenerPermission by remember { mutableStateOf(false) }
        val refresh by uiRefresh.collectAsState()
        LaunchedEffect(refresh) {
            listenerPermission = NotificationManagerCompat.getEnabledListenerPackages(this@MainActivity)
                .contains(this@MainActivity.packageName)
            val found = MorpheAppDiscovery.find(this@MainActivity)
            installedMorphe = found
            if (found != null && found != MirrorGateway.prefs.morphePackage) {
                MirrorGateway.prefs.morphePackage = found
                MorpheNotificationListener.refreshPackage()
            }
        }
        var captureAttempted by remember { mutableStateOf(false) }
        val launchCapture = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK && result.data != null) {
                try { MirrorForegroundService.start(this, result.resultCode, result.data!!) }
                catch (e: Exception) { AppLog.add("CAPTURE", "Start failed: ${e.javaClass.simpleName}"); MirrorGateway.parked(false) }
            } else {
                AppLog.add("CAPTURE", "User declined capture permission")
                MirrorGateway.parked(false)
            }
        }
        val notificationRequest = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()) { granted ->
            AppLog.add("PERMISSION", "Notification permission=$granted")
        }
        LaunchedEffect(Unit) {
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                notificationRequest.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        val incomingCapture by actionCapture.collectAsState()
        LaunchedEffect(incomingCapture) { if (incomingCapture) page = "Home" }
        val next = EnginePolicy.plan(s)
        // Launch projection consent only when the Activity is already visible.
        // Never start an Activity from a background notification listener.
        LaunchedEffect(next) {
            if (next != NextStep.ASK_PERMISSION) captureAttempted = false
            if (next == NextStep.ASK_PERMISSION && !captureAttempted) {
                captureAttempted = true
                val m = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                launchCapture.launch(m.createScreenCaptureIntent())
            }
        }
        Scaffold(
            topBar = {
                Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    if (page != "Home") {
                        IconButton(onClick = { page = PageNavigation.parent(page) }) {
                            Icon(Icons.Rounded.ArrowBack, "Back")
                        }
                    }
                    val icon = when (page) {
                        "Home" -> Icons.Rounded.Home
                        "Updates" -> Icons.Rounded.Update
                        "Monitoring" -> Icons.Rounded.NotificationsActive
                        else -> Icons.Rounded.Settings
                    }
                    Icon(icon, null)
                    Text(page, style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f).padding(start = 8.dp))
                    Icon(painterResource(R.drawable.ic_header_screen), null,
                        modifier = Modifier.size(34.dp), tint = androidx.compose.ui.graphics.Color.Unspecified)
                    Text("MorpheAutoMirror", style = MaterialTheme.typography.labelMedium)
                }
            },
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(selected = page == "Home", onClick = { page = "Home" },
                        icon = { Icon(Icons.Rounded.Home, null) }, label = { Text("Home") })
                    NavigationBarItem(selected = page != "Home", onClick = { page = "Settings" },
                        icon = { Icon(Icons.Rounded.Settings, null) }, label = { Text("Settings") })
                    NavigationBarItem(selected = false, onClick = ::quit,
                        icon = { Icon(Icons.Rounded.PowerSettingsNew, null) }, label = { Text("Quit") })
                }
            }
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
                .padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when (page) {
                    "Home" -> {
                        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Icon(Icons.Rounded.DirectionsCar, contentDescription = null,
                                        modifier = Modifier.size(30.dp))
                                    Text("Morphe Auto Mirror", style = MaterialTheme.typography.titleLarge,
                                        modifier = Modifier.weight(1f))
                                    HomeBadge(if (s.capturing) "MIRRORING" else "STANDBY", s.capturing)
                                }
                                Text("Play in YouTube Morphe, then open the app on Android Auto while parked.",
                                    style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Rounded.NotificationsActive, contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary)
                                    Text("Morphe listener", style = MaterialTheme.typography.titleMedium,
                                        modifier = Modifier.weight(1f).padding(start = 10.dp))
                                    HomeBadge(if (listenerStarted != null && s.monitoring) "CONNECTED" else "OFFLINE",
                                        listenerStarted != null && s.monitoring)
                                }
                                HomeStatusRow("Monitoring", if (s.monitoring) "Enabled" else "Disabled")
                                HomeStatusRow("Morphe app", if (installedMorphe != null) "Installed" else "Not installed")
                                HomeStatusRow("Listener access", if (listenerPermission) "Granted" else "Not granted")
                                HomeStatusRow("Connected for",
                                    if (s.monitoring) ListenerUptime.format(listenerStarted, uptimeNow) else "Not monitoring")
                                HomeStatusRow("Media session", if (sessionFound) "Detected" else "Not detected")
                                HomeStatusRow("Playback", if (s.morphePlaying) "Playing" else "Idle")
                                if (listenerStarted == null && s.monitoring) {
                                    Button(onClick = {
                                        startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                                    }, modifier = Modifier.fillMaxWidth()) { Text("Grant notification access") }
                                }
                            }
                        }
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Rounded.DirectionsCar, contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary)
                                    Text("Android Auto", style = MaterialTheme.typography.titleMedium,
                                        modifier = Modifier.weight(1f).padding(start = 10.dp))
                                    HomeBadge(if (available) "CONNECTED" else "DISCONNECTED", available)
                                }
                                HomeStatusRow("Connection time", ListenerUptime.format(carStarted, uptimeNow))
                                HomeStatusRow("Car app", if (s.carSession) "Open" else "Not open")
                                HomeStatusRow("Display surface", if (s.surfaceReady) "Ready" else "Waiting")
                            }
                        }
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Rounded.PlayCircle, contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary)
                                    Text("Video mirroring", style = MaterialTheme.typography.titleMedium,
                                        modifier = Modifier.weight(1f).padding(start = 10.dp))
                                    HomeBadge(if (s.capturing) "ACTIVE" else "INACTIVE", s.capturing)
                                }
                                HomeStatusRow("Renderer", "Direct surface")
                                HomeStatusRow("Capture", if (s.permissionGranted) "Granted" else "Not granted")
                                Text("Capture permission is required per session. Audio stays in Morphe.",
                                    style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        if (error.isNotBlank()) {
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(16.dp)) {
                                    Text("Capture issue", style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.error)
                                    Text(error, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                    "Settings" -> {
                        Text("Preferences", style = MaterialTheme.typography.titleMedium)
                        ListItem(
                            headlineContent = { Text("Playback monitoring") },
                            supportingContent = { Text("Automatic app detection and notification access") },
                            leadingContent = { Icon(Icons.Rounded.PlayCircle, null) },
                            trailingContent = { Icon(Icons.Rounded.ChevronRight, null) },
                            modifier = Modifier.fillMaxWidth().clickable { page = "Monitoring" }
                        )
                        HorizontalDivider()
                        Text("Updates", style = MaterialTheme.typography.titleMedium)
                        ListItem(
                            headlineContent = { Text("Updates") },
                            supportingContent = { Text("Stable and experimental releases") },
                            leadingContent = { Icon(Icons.Rounded.Update, null) },
                            trailingContent = { Icon(Icons.Rounded.ChevronRight, null) },
                            modifier = Modifier.fillMaxWidth().clickable { page = "Updates" }
                        )
                    }
                    "Monitoring" -> {
                        val readiness = MonitoringStatus.label(
                            monitoring, installedMorphe != null,
                            listenerPermission, listenerStarted != null
                        )
                        Text(
                            "Configure automatic monitoring and required access for Morphe playback detection.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            HomeBadge(readiness, readiness == "Monitoring active")
                        }
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                MonitoringIconTile { Icon(Icons.Rounded.PlayCircle, contentDescription = null) }
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("Automatic Morphe listener", style = MaterialTheme.typography.titleMedium)
                                    Text("Detect Morphe playback without polling.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Switch(
                                    checked = monitoring,
                                    onCheckedChange = { enabled ->
                                        monitoring = enabled
                                        MirrorGateway.monitoring(enabled)
                                    }
                                )
                            }
                        }
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                MorpheInstalledIcon(installedMorphe, refresh)
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("Morphe app detection", style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        if (installedMorphe != null) "Morphe YouTube detected automatically"
                                        else "Morphe YouTube is not installed or not visible",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                HomeBadge(if (installedMorphe != null) "Installed" else "Not installed",
                                    installedMorphe != null)
                            }
                        }
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                        ) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    MonitoringIconTile {
                                        Icon(Icons.Rounded.NotificationsActive, contentDescription = null)
                                    }
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text("Notification listener access",
                                            style = MaterialTheme.typography.titleMedium)
                                        Text("Required to detect Morphe playback events",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    HomeBadge(if (listenerPermission) "Granted" else "Not granted",
                                        listenerPermission)
                                }
                                if (!listenerPermission) {
                                    Button(
                                        onClick = {
                                            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) { Text("Grant access") }
                                }
                            }
                        }
                    }
                    "Updates" -> {
                        N2K02UpdateScreen(
                            controller = LiveUpdater.controller,
                            installGuard = LiveUpdater.installGuard,
                            modifier = Modifier.fillMaxWidth(),
                            experimentalEnabled = experimental,
                            onExperimentalEnabledChange = LiveUpdater::setExperimentalEnabled
                        )
                    }
                }
            }
        }
    }
}
@Composable
private fun HomeStatusRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun HomeBadge(label: String, active: Boolean) {
    Surface(shape = RoundedCornerShape(50),
        color = if (active) MaterialTheme.colorScheme.tertiaryContainer
            else MaterialTheme.colorScheme.surfaceVariant) {
        Text(label, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelSmall,
            color = if (active) MaterialTheme.colorScheme.onTertiaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MonitoringIconTile(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.size(54.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
    ) {
        Box(contentAlignment = Alignment.Center) { content() }
    }
}

@Composable
private fun MorpheInstalledIcon(packageName: String?, refresh: Int) {
    val context = LocalContext.current
    val icon = remember(packageName, refresh) {
        packageName?.let { name ->
            runCatching { context.packageManager.getApplicationIcon(name) }.getOrNull()
        }
    }
    if (icon == null) {
        MonitoringIconTile { Icon(Icons.Rounded.PlayCircle, contentDescription = "Morphe YouTube") }
    } else {
        AndroidView(
            factory = { ctx -> ImageView(ctx).apply { scaleType = ImageView.ScaleType.FIT_CENTER } },
            update = { image -> image.setImageDrawable(icon) },
            modifier = Modifier.size(54.dp).clip(RoundedCornerShape(16.dp))
        )
    }
}
