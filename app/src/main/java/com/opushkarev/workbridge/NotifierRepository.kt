package com.opushkarev.workbridge

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "workbridge.preferences")

data class DiagnosticsState(
    val lifecycleEvent: String? = null,
    val lifecycleAt: Long? = null,
    val lastCallbackPackage: String? = null,
    val lastCallbackAt: Long? = null,
    val lastForwardedPackage: String? = null,
    val lastForwardedAt: Long? = null,
    val lastSkipPackage: String? = null,
    val lastSkipReason: String? = null,
    val lastSkipAt: Long? = null,
    val lastError: String? = null,
    val lastErrorAt: Long? = null,
    val callbackCount: Int = 0,
    val forwardedCount: Int = 0,
)

data class AppPreferencesState(
    val monitoringEnabled: Boolean = true,
    val cancelOriginal: Boolean = false,
    val monitoredPackages: List<String> = emptyList(),
    val recentPackages: List<String> = emptyList(),
    val diagnostics: DiagnosticsState = DiagnosticsState(),
)

class NotifierRepository(context: Context) {
    private val appContext = context.applicationContext
    private val dataStore = appContext.dataStore

    val state: Flow<AppPreferencesState> = dataStore.data.map(::toState)

    suspend fun snapshot(): AppPreferencesState = state.first()

    suspend fun addMonitoredPackage(input: String): String? {
        val normalized = normalizePackageInput(input) ?: return null
        dataStore.edit { preferences ->
            val packages = preferences[Keys.MONITORED_PACKAGES].orEmpty().toMutableSet()
            packages.add(normalized)
            preferences[Keys.MONITORED_PACKAGES] = packages
        }
        return normalized
    }

    suspend fun removeMonitoredPackage(packageName: String) {
        dataStore.edit { preferences ->
            val packages = preferences[Keys.MONITORED_PACKAGES].orEmpty().toMutableSet()
            packages.remove(packageName)
            preferences[Keys.MONITORED_PACKAGES] = packages
        }
    }

    suspend fun setMonitoringEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.MONITORING_ENABLED] = enabled }
    }

    suspend fun setCancelOriginal(enabled: Boolean) {
        dataStore.edit { it[Keys.CANCEL_ORIGINAL] = enabled }
    }

    suspend fun resetDiagnostics() {
        dataStore.edit { preferences ->
            preferences.remove(Keys.LAST_CALLBACK_PACKAGE)
            preferences.remove(Keys.LAST_CALLBACK_AT)
            preferences.remove(Keys.LAST_FORWARDED_PACKAGE)
            preferences.remove(Keys.LAST_FORWARDED_AT)
            preferences.remove(Keys.LAST_SKIP_PACKAGE)
            preferences.remove(Keys.LAST_SKIP_REASON)
            preferences.remove(Keys.LAST_SKIP_AT)
            preferences.remove(Keys.LAST_ERROR)
            preferences.remove(Keys.LAST_ERROR_AT)
            preferences[Keys.CALLBACK_COUNT] = 0
            preferences[Keys.FORWARDED_COUNT] = 0
        }
    }

    suspend fun recordLifecycle(event: String) {
        val now = System.currentTimeMillis()
        dataStore.edit { preferences ->
            preferences[Keys.LIFECYCLE_EVENT] = event
            preferences[Keys.LIFECYCLE_AT] = now
        }
    }

    suspend fun recordCallback(packageName: String, includeInRecents: Boolean = true) {
        val now = System.currentTimeMillis()
        dataStore.edit { preferences ->
            preferences[Keys.LAST_CALLBACK_PACKAGE] = packageName
            preferences[Keys.LAST_CALLBACK_AT] = now
            preferences[Keys.CALLBACK_COUNT] = (preferences[Keys.CALLBACK_COUNT] ?: 0) + 1
            if (includeInRecents) {
                preferences[Keys.RECENT_PACKAGES] = encodeRecentPackages(
                    prependRecentPackage(
                        packageName = packageName,
                        existing = decodeRecentPackages(preferences[Keys.RECENT_PACKAGES])
                    )
                )
            }
        }
    }

    suspend fun recordForwarded(packageName: String) {
        val now = System.currentTimeMillis()
        dataStore.edit { preferences ->
            preferences[Keys.LAST_FORWARDED_PACKAGE] = packageName
            preferences[Keys.LAST_FORWARDED_AT] = now
            preferences[Keys.FORWARDED_COUNT] = (preferences[Keys.FORWARDED_COUNT] ?: 0) + 1
        }
    }

    suspend fun recordSkip(packageName: String, reason: String) {
        val now = System.currentTimeMillis()
        dataStore.edit { preferences ->
            preferences[Keys.LAST_SKIP_PACKAGE] = packageName
            preferences[Keys.LAST_SKIP_REASON] = reason
            preferences[Keys.LAST_SKIP_AT] = now
        }
    }

    suspend fun recordError(packageName: String?, error: String) {
        val now = System.currentTimeMillis()
        dataStore.edit { preferences ->
            val label = packageName?.takeIf { it.isNotBlank() }?.let { "$it: " }.orEmpty()
            preferences[Keys.LAST_ERROR] = label + error
            preferences[Keys.LAST_ERROR_AT] = now
        }
    }

    private fun toState(preferences: Preferences): AppPreferencesState {
        return AppPreferencesState(
            monitoringEnabled = preferences[Keys.MONITORING_ENABLED] ?: true,
            cancelOriginal = preferences[Keys.CANCEL_ORIGINAL] ?: false,
            monitoredPackages = preferences[Keys.MONITORED_PACKAGES].orEmpty().toList().sorted(),
            recentPackages = decodeRecentPackages(preferences[Keys.RECENT_PACKAGES]),
            diagnostics = DiagnosticsState(
                lifecycleEvent = preferences[Keys.LIFECYCLE_EVENT],
                lifecycleAt = preferences[Keys.LIFECYCLE_AT],
                lastCallbackPackage = preferences[Keys.LAST_CALLBACK_PACKAGE],
                lastCallbackAt = preferences[Keys.LAST_CALLBACK_AT],
                lastForwardedPackage = preferences[Keys.LAST_FORWARDED_PACKAGE],
                lastForwardedAt = preferences[Keys.LAST_FORWARDED_AT],
                lastSkipPackage = preferences[Keys.LAST_SKIP_PACKAGE],
                lastSkipReason = preferences[Keys.LAST_SKIP_REASON],
                lastSkipAt = preferences[Keys.LAST_SKIP_AT],
                lastError = preferences[Keys.LAST_ERROR],
                lastErrorAt = preferences[Keys.LAST_ERROR_AT],
                callbackCount = preferences[Keys.CALLBACK_COUNT] ?: 0,
                forwardedCount = preferences[Keys.FORWARDED_COUNT] ?: 0,
            ),
        )
    }

    private fun normalizePackageInput(input: String): String? {
        val trimmed = input.trim()
        if (trimmed.isBlank()) {
            return null
        }

        return trimmed
            .removePrefix("package:")
            .trim()
            .takeIf { it.isNotBlank() }
    }

    private fun prependRecentPackage(packageName: String, existing: List<String>): List<String> {
        return buildList {
            add(packageName)
            existing.forEach { candidate ->
                if (candidate != packageName && size < 8) {
                    add(candidate)
                }
            }
        }
    }

    private fun encodeRecentPackages(packages: List<String>): String = packages.joinToString("|")

    private fun decodeRecentPackages(encoded: String?): List<String> {
        return encoded
            .orEmpty()
            .split('|')
            .map(String::trim)
            .filter(String::isNotBlank)
    }

    private object Keys {
        val MONITORING_ENABLED = booleanPreferencesKey("monitoring_enabled")
        val CANCEL_ORIGINAL = booleanPreferencesKey("cancel_original")
        val MONITORED_PACKAGES = stringSetPreferencesKey("monitored_packages")
        val RECENT_PACKAGES = stringPreferencesKey("recent_packages")
        val LIFECYCLE_EVENT = stringPreferencesKey("lifecycle_event")
        val LIFECYCLE_AT = longPreferencesKey("lifecycle_at")
        val LAST_CALLBACK_PACKAGE = stringPreferencesKey("last_callback_package")
        val LAST_CALLBACK_AT = longPreferencesKey("last_callback_at")
        val LAST_FORWARDED_PACKAGE = stringPreferencesKey("last_forwarded_package")
        val LAST_FORWARDED_AT = longPreferencesKey("last_forwarded_at")
        val LAST_SKIP_PACKAGE = stringPreferencesKey("last_skip_package")
        val LAST_SKIP_REASON = stringPreferencesKey("last_skip_reason")
        val LAST_SKIP_AT = longPreferencesKey("last_skip_at")
        val LAST_ERROR = stringPreferencesKey("last_error")
        val LAST_ERROR_AT = longPreferencesKey("last_error_at")
        val CALLBACK_COUNT = intPreferencesKey("callback_count")
        val FORWARDED_COUNT = intPreferencesKey("forwarded_count")
    }
}
