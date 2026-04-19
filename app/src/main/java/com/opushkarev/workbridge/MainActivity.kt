package com.opushkarev.workbridge

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.service.notification.NotificationListenerService
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationManagerCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen

data class RuntimeSnapshot(
    val listenerAccess: Boolean = false,
    val appNotificationsEnabled: Boolean = false,
    val postNotificationsGranted: Boolean = true,
    val ignoringBatteryOptimizations: Boolean = false,
)

class MainActivity : ComponentActivity() {
    private val repository by lazy { NotifierRepository(applicationContext) }
    private var runtimeSnapshot by mutableStateOf(RuntimeSnapshot())

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        NotificationChannels.ensureForwardingChannel(this)
        refreshRuntimeSnapshot()

        setContent {
            val state by repository.state.collectAsState(initial = AppPreferencesState())
            val permissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission()
            ) {
                refreshRuntimeSnapshot()
            }

            WorkBridgeTheme {
                WorkBridgeApp(
                    state = state,
                    runtimeSnapshot = runtimeSnapshot,
                    onAddPackage = repository::addMonitoredPackage,
                    onRemovePackage = repository::removeMonitoredPackage,
                    onSetMonitoringEnabled = repository::setMonitoringEnabled,
                    onSetCancelOriginal = repository::setCancelOriginal,
                    onResetDiagnostics = repository::resetDiagnostics,
                    onOpenListenerSettings = {
                        startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    },
                    onOpenNotificationSettings = {
                        startActivity(
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                            }
                        )
                    },
                    onRequestNotificationPermission = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            refreshRuntimeSnapshot()
                        }
                    },
                    onRequestListenerRebind = {
                        restartNotificationListener()
                    },
                    onOpenBatteryOptimizationSettings = {
                        startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                    },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshRuntimeSnapshot()
    }

    private fun refreshRuntimeSnapshot() {
        val powerManager = getSystemService(PowerManager::class.java)
        runtimeSnapshot = RuntimeSnapshot(
            listenerAccess = isNotificationListenerEnabled(),
            appNotificationsEnabled = NotificationManagerCompat.from(this).areNotificationsEnabled(),
            postNotificationsGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            },
            ignoringBatteryOptimizations = powerManager?.isIgnoringBatteryOptimizations(packageName) == true,
        )
    }

    private fun restartNotificationListener() {
        val componentName = ComponentName(this, WorkBridgeListenerService::class.java)

        startService(
            Intent(this, WorkBridgeListenerService::class.java).apply {
                action = WorkBridgeListenerService.ACTION_REQUEST_UNBIND
            }
        )

        Handler(Looper.getMainLooper()).postDelayed({
            NotificationListenerService.requestRebind(componentName)
            refreshRuntimeSnapshot()
        }, 400)
    }

    private fun isNotificationListenerEnabled(): Boolean {
        val enabledListeners = Settings.Secure.getString(
            contentResolver,
            "enabled_notification_listeners"
        ).orEmpty()
        val componentName = ComponentName(this, WorkBridgeListenerService::class.java)
        return enabledListeners.contains(componentName.flattenToString()) ||
            enabledListeners.contains(componentName.flattenToShortString())
    }
}
