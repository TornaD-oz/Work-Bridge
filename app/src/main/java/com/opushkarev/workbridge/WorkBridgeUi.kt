package com.opushkarev.workbridge

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class RootTab(
    val label: String,
    val icon: ImageVector,
) {
    Home("Bridge", Icons.Outlined.NotificationsActive),
    Diagnostics("Diagnostics", Icons.Outlined.Analytics),
    Settings("Settings", Icons.Outlined.Tune),
}

private const val WORK_BRIDGE_REPOSITORY_URL = "https://github.com/TornaD-oz/Work-Bridge"
private const val WORK_BRIDGE_DONATE_URL = "https://destream.net/live/TornaDoz/donate"
private const val WORK_BRIDGE_LICENSE_URL = "$WORK_BRIDGE_REPOSITORY_URL/blob/main/LICENSE"
private const val WORK_BRIDGE_LICENSE_SUMMARY =
    "Apache License 2.0. Work Bridge is open source and may be used, modified, and redistributed under the terms of the Apache 2.0 license."

@Composable
fun WorkBridgeApp(
    state: AppPreferencesState,
    runtimeSnapshot: RuntimeSnapshot,
    onAddPackage: suspend (String) -> String?,
    onRemovePackage: suspend (String) -> Unit,
    onSetMonitoringEnabled: suspend (Boolean) -> Unit,
    onSetCancelOriginal: suspend (Boolean) -> Unit,
    onResetDiagnostics: suspend () -> Unit,
    onOpenListenerSettings: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onRequestListenerRebind: () -> Unit,
    onOpenBatteryOptimizationSettings: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var currentTab by rememberSaveable { mutableStateOf(RootTab.Home) }

    Scaffold(
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onBackground,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        bottomBar = {
            NavigationBar(
                windowInsets = WindowInsets.navigationBars,
                tonalElevation = 0.dp,
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
            ) {
                RootTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = currentTab == tab,
                        onClick = { currentTab = tab },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.20f),
                            MaterialTheme.colorScheme.background,
                            MaterialTheme.colorScheme.secondary.copy(alpha = 0.10f),
                        )
                    )
                )
                .padding(innerPadding)
        ) {
            when (currentTab) {
                RootTab.Home -> HomeScreen(
                    state = state,
                    snackbarHostState = snackbarHostState,
                    onAddPackage = onAddPackage,
                    onRemovePackage = onRemovePackage,
                )

                RootTab.Diagnostics -> DiagnosticsScreen(
                    state = state,
                    runtimeSnapshot = runtimeSnapshot,
                    snackbarHostState = snackbarHostState,
                    onOpenListenerSettings = onOpenListenerSettings,
                    onOpenNotificationSettings = onOpenNotificationSettings,
                    onRequestNotificationPermission = onRequestNotificationPermission,
                    onRequestListenerRebind = onRequestListenerRebind,
                    onOpenBatteryOptimizationSettings = onOpenBatteryOptimizationSettings,
                    onResetDiagnostics = {
                        scope.launch {
                            onResetDiagnostics()
                            snackbarHostState.showSnackbar("Diagnostics reset.")
                        }
                    },
                )

                RootTab.Settings -> SettingsScreen(
                    state = state,
                    snackbarHostState = snackbarHostState,
                    onSetMonitoringEnabled = onSetMonitoringEnabled,
                    onSetCancelOriginal = onSetCancelOriginal,
                )
            }
        }
    }
}

@Composable
private fun HomeScreen(
    state: AppPreferencesState,
    snackbarHostState: SnackbarHostState,
    onAddPackage: suspend (String) -> String?,
    onRemovePackage: suspend (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var packageInput by rememberSaveable { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            HeroCard(
                label = "Work Bridge",
                title = "Get work alerts onto your watch",
                description = "Mirror the work-profile apps you care about into your personal profile so your watch can receive them.",
            )
        }

        item {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        text = "Add a monitored package",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "Type a package name like ru.oneme.app. Packages the listener has already seen appear below so setup stays quick.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = packageInput,
                        onValueChange = { packageInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Package name") },
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = {
                                scope.launch {
                                    val normalized = onAddPackage(packageInput)
                                    if (normalized == null) {
                                        snackbarHostState.showSnackbar("Enter a package name like ru.oneme.app.")
                                    } else {
                                        packageInput = ""
                                        snackbarHostState.showSnackbar("Monitoring $normalized")
                                    }
                                }
                            }
                        ) {
                            Text("Add package")
                        }

                        OutlinedButton(onClick = { packageInput = "" }) {
                            Text("Clear")
                        }
                    }
                }
            }
        }

        val suggestedPackages = state.recentPackages.filterNot(state.monitoredPackages::contains)
        if (suggestedPackages.isNotEmpty()) {
            item {
                ObservedPackagesCard(
                    packages = suggestedPackages,
                    onChipClick = { packageName ->
                        scope.launch {
                            onAddPackage(packageName)
                            snackbarHostState.showSnackbar("Monitoring $packageName")
                        }
                    },
                )
            }
        }

        item {
            BackgroundAccessCard()
        }

        if (state.monitoredPackages.isEmpty()) {
            item {
                EmptyForwardingListCard()
            }
        } else {
            item {
                ActionSectionCard(
                    title = "Forwarding list",
                    description = "These apps are currently allowed to mirror notifications through Work Bridge.",
                ) {
                    state.monitoredPackages.forEachIndexed { index, packageName ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .background(
                                        color = MaterialTheme.colorScheme.secondary,
                                        shape = CircleShape,
                                    ),
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = packageName,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = "Added to your forwarding list.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(
                                onClick = {
                                    scope.launch {
                                        onRemovePackage(packageName)
                                        snackbarHostState.showSnackbar("Removed $packageName")
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.DeleteOutline,
                                    contentDescription = "Remove package",
                                )
                            }
                        }
                        if (index != state.monitoredPackages.lastIndex) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DiagnosticsScreen(
    state: AppPreferencesState,
    runtimeSnapshot: RuntimeSnapshot,
    snackbarHostState: SnackbarHostState,
    onOpenListenerSettings: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onRequestListenerRebind: () -> Unit,
    onOpenBatteryOptimizationSettings: () -> Unit,
    onResetDiagnostics: () -> Unit,
) {
    val scope = rememberCoroutineScope()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            HeroCard(
                label = "Diagnostics",
                title = "Check what's working",
                description = "Use diagnostics when forwarding stops working. They confirm access, permissions, and recent listener activity.",
            )
        }

        item {
            StatusOverviewCard(runtimeSnapshot = runtimeSnapshot)
        }

        item {
            ActionSectionCard(title = "Quick actions") {
                Button(onClick = onOpenListenerSettings, modifier = Modifier.fillMaxWidth()) {
                    Text("Open listener settings")
                }
                OutlinedButton(
                    onClick = onOpenNotificationSettings,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Open notification settings")
                }
                OutlinedButton(
                    onClick = onOpenBatteryOptimizationSettings,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Open battery settings")
                }
                OutlinedButton(
                    onClick = {
                        onRequestListenerRebind()
                        scope.launch {
                            snackbarHostState.showSnackbar(
                                "Requested listener restart. Wait a few seconds."
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Restart listener")
                }
                OutlinedButton(
                    onClick = {
                        onRequestNotificationPermission()
                        scope.launch {
                            snackbarHostState.showSnackbar(
                                "Requested notification permission. Check the system prompt."
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Request notification permission")
                }
                TextButton(
                    onClick = onResetDiagnostics,
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text("Reset diagnostics")
                }
            }
        }

        item {
            ActionSectionCard(
                title = "Recent activity",
                description = "This explains what Work Bridge saw, skipped, and forwarded most recently.",
            ) {
                DiagnosticsMetricRow(
                    values = listOf(
                        "Tracked" to state.monitoredPackages.size.toString(),
                        "Seen" to state.diagnostics.callbackCount.toString(),
                        "Forwarded" to state.diagnostics.forwardedCount.toString(),
                    ),
                )
                HorizontalDivider()
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    DiagnosticsRow(
                        "Listener state",
                        formatWithTimestamp(state.diagnostics.lifecycleEvent, state.diagnostics.lifecycleAt),
                    )
                    DiagnosticsRow(
                        "Last seen package",
                        formatWithTimestamp(
                            state.diagnostics.lastCallbackPackage,
                            state.diagnostics.lastCallbackAt,
                        ),
                    )
                    DiagnosticsRow(
                        "Last forwarded package",
                        formatWithTimestamp(
                            state.diagnostics.lastForwardedPackage,
                            state.diagnostics.lastForwardedAt,
                        ),
                    )
                    DiagnosticsRow(
                        "Last skipped",
                        formatWithTimestamp(
                            listOfNotNull(
                                state.diagnostics.lastSkipPackage,
                                state.diagnostics.lastSkipReason,
                            ).joinToString(" / ").ifBlank { null },
                            state.diagnostics.lastSkipAt,
                        ),
                    )
                    DiagnosticsRow(
                        "Last error",
                        formatWithTimestamp(
                            state.diagnostics.lastError,
                            state.diagnostics.lastErrorAt,
                        ),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SettingsScreen(
    state: AppPreferencesState,
    snackbarHostState: SnackbarHostState,
    onSetMonitoringEnabled: suspend (Boolean) -> Unit,
    onSetCancelOriginal: suspend (Boolean) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    val openExternalLink: (String, String) -> Unit = { url, label ->
        runCatching {
            uriHandler.openUri(url)
        }.onFailure {
            scope.launch {
                snackbarHostState.showSnackbar("Couldn't open $label.")
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            HeroCard(
                label = "Settings",
                title = "Choose how forwarding behaves",
                description = "These settings control whether Work Bridge is active and whether the original notification should be hidden after a clone succeeds.",
            )
        }

        item {
            ActionSectionCard(
                title = "Forwarding behavior",
                description = "One switch controls the bridge, the other controls whether the original work notification stays visible.",
            ) {
                SettingsToggleRow(
                    title = "Monitoring enabled",
                    body = "Master switch for the listener forwarding pipeline.",
                    checked = state.monitoringEnabled,
                    onCheckedChange = { enabled ->
                        scope.launch {
                            onSetMonitoringEnabled(enabled)
                            snackbarHostState.showSnackbar(
                                if (enabled) "Forwarding enabled" else "Forwarding paused"
                            )
                        }
                    },
                )
                HorizontalDivider()
                SettingsToggleRow(
                    title = "Cancel original after clone",
                    body = "Hide the original work-profile notification after Work Bridge posts its copy.",
                    checked = state.cancelOriginal,
                    onCheckedChange = { enabled ->
                        scope.launch {
                            onSetCancelOriginal(enabled)
                            snackbarHostState.showSnackbar(
                                if (enabled) "Original notifications will be cancelled after cloning"
                                else "Original notifications will remain visible"
                            )
                        }
                    },
                )
            }
        }

        item {
            ActionSectionCard(
                title = "About",
                description = "Version, project links, support, and license details for Work Bridge.",
            ) {
                AboutRow(
                    icon = Icons.Outlined.Info,
                    title = "Version",
                    body = BuildConfig.VERSION_NAME,
                )
                HorizontalDivider()
                AboutRow(
                    icon = Icons.Outlined.Code,
                    title = "GitHub",
                    body = WORK_BRIDGE_REPOSITORY_URL,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedButton(
                        onClick = {
                            openExternalLink(
                                WORK_BRIDGE_REPOSITORY_URL,
                                "the GitHub repository",
                            )
                        },
                    ) {
                        Text("Open GitHub")
                    }
                    Button(
                        onClick = {
                            openExternalLink(
                                WORK_BRIDGE_DONATE_URL,
                                "the donation page",
                            )
                        },
                    ) {
                        Text("Donate")
                    }
                }
                HorizontalDivider()
                AboutRow(
                    icon = Icons.Outlined.Gavel,
                    title = "License",
                    body = WORK_BRIDGE_LICENSE_SUMMARY,
                )
                TextButton(
                    onClick = {
                        openExternalLink(
                            WORK_BRIDGE_LICENSE_URL,
                            "the license page",
                        )
                    },
                    contentPadding = PaddingValues(0.dp),
                ) {
                    Text("View full license")
                }
            }
        }
    }
}

@Composable
private fun HeroCard(
    label: String? = null,
    title: String,
    description: String,
    compact: Boolean = false,
) {
    Card(
        shape = RoundedCornerShape(if (compact) 24.dp else 32.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.secondary,
                        )
                    )
                )
                .padding(
                    horizontal = if (compact) 16.dp else 20.dp,
                    vertical = if (compact) 14.dp else 18.dp,
                ),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 14.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 8.dp)) {
                    if (label != null) {
                        Text(
                            text = label.uppercase(Locale.getDefault()),
                            style = if (compact) {
                                MaterialTheme.typography.labelMedium
                            } else {
                                MaterialTheme.typography.labelLarge
                            },
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.76f),
                        )
                    }
                    Text(
                        text = title,
                        style = if (compact) {
                            MaterialTheme.typography.titleLarge
                        } else {
                            MaterialTheme.typography.headlineMedium
                        },
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Text(
                        text = description,
                        style = if (compact) {
                            MaterialTheme.typography.bodySmall
                        } else {
                            MaterialTheme.typography.bodyLarge
                        },
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.92f),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StatusOverviewCard(runtimeSnapshot: RuntimeSnapshot) {
    ActionSectionCard(
        title = "Current status",
        description = "These checks usually explain whether forwarding should work right now.",
    ) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            StatusPill("Listener", runtimeSnapshot.listenerAccess)
            StatusPill("App notifications", runtimeSnapshot.appNotificationsEnabled)
            StatusPill("Permission", runtimeSnapshot.postNotificationsGranted)
            StatusPill("Background access", runtimeSnapshot.ignoringBatteryOptimizations)
        }
    }
}

@Composable
private fun ActionSectionCard(
    title: String,
    description: String? = null,
    compact: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(if (compact) 20.dp else 24.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = if (compact) 16.dp else 20.dp,
                vertical = if (compact) 16.dp else 20.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 14.dp),
        ) {
            Text(
                text = title,
                style = if (compact) {
                    MaterialTheme.typography.titleSmall
                } else {
                    MaterialTheme.typography.titleLarge
                },
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (description != null) {
                Text(
                    text = description,
                    style = if (compact) {
                        MaterialTheme.typography.bodySmall
                    } else {
                        MaterialTheme.typography.bodyMedium
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            content()
        }
    }
}

@Composable
private fun ObservedPackagesCard(
    packages: List<String>,
    onChipClick: (String) -> Unit,
) {
    ActionSectionCard(
        title = "Observed packages",
        description = "These packages were already seen by the listener on this device.",
    ) {
        packages.forEachIndexed { index, packageName ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = packageName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                AssistChip(
                    onClick = { onChipClick(packageName) },
                    label = { Text("Add") },
                )
            }
            if (index != packages.lastIndex) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            }
        }
    }
}

@Composable
private fun EmptyForwardingListCard() {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "Nothing monitored yet",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "Add a package manually or tap one of the recent callback suggestions the next time a work-profile app sends a notification.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun BackgroundAccessCard() {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.40f),
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Background access matters",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "If forwarding only starts after opening Work Bridge, allow it to run in the background and remove battery restrictions in your phone's system settings.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StatusPill(label: String, active: Boolean) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = if (active) {
            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.88f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.88f)
        },
        modifier = Modifier.border(
            width = 1.dp,
            color = if (active) {
                MaterialTheme.colorScheme.secondary.copy(alpha = 0.24f)
            } else {
                MaterialTheme.colorScheme.outline.copy(alpha = 0.18f)
            },
            shape = RoundedCornerShape(18.dp),
        ),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = if (active) {
                    MaterialTheme.colorScheme.onSecondaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            Text(
                text = yesNo(active),
                style = MaterialTheme.typography.titleMedium,
                color = if (active) {
                    MaterialTheme.colorScheme.onSecondaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
        }
    }
}

@Composable
private fun DiagnosticsMetricRow(values: List<Pair<String, String>>) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        values.forEach { (label, value) ->
            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.56f),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = value,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                    )
                }
            }
        }
    }
}

@Composable
private fun DiagnosticsRow(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun SettingsToggleRow(
    title: String,
    body: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun AboutRow(
    icon: ImageVector,
    title: String,
    body: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(10.dp),
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun yesNo(value: Boolean): String = if (value) "Yes" else "No"

private fun formatWithTimestamp(value: String?, timestamp: Long?): String {
    val body = value?.takeIf { it.isNotBlank() } ?: "None"
    val time = timestamp?.let(::formatTimestamp) ?: "Never"
    return "$body ($time)"
}

private fun formatTimestamp(timestamp: Long): String {
    return DateTimeFormatter.ofPattern("MMM d, uuuu HH:mm:ss", Locale.getDefault())
        .withZone(ZoneId.systemDefault())
        .format(Instant.ofEpochMilli(timestamp))
}
