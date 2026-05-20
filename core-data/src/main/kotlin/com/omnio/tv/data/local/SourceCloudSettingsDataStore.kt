package com.omnio.tv.data.local

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.omnio.tv.domain.model.SourceCloudService
import com.omnio.tv.domain.model.SourceCloudSettings
import com.omnio.tv.domain.profile.ProfileManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SourceCloudSettingsDataStore @Inject constructor(
    private val factory: ProfileDataStoreFactory,
    private val profileManager: ProfileManager
) {
    companion object {
        private const val FEATURE = "source_cloud_settings"
        private val enabledKey = booleanPreferencesKey("enabled")
        private val connectedServicesKey = stringPreferencesKey("connected_services")

        fun parseServiceKeys(raw: String): Set<SourceCloudService> = raw
            .split(',')
            .mapNotNull { SourceCloudService.fromKey(it.trim()) }
            .toSet()

        fun encodeServiceKeys(services: Set<SourceCloudService>): String = services
            .sortedBy { it.key }
            .joinToString(",") { it.key }
    }

    private fun store(profileId: Int = profileManager.activeProfileId.value) =
        factory.get(profileId, FEATURE)

    val settings: Flow<SourceCloudSettings> = profileManager.activeProfileId.flatMapLatest { pid ->
        factory.get(pid, FEATURE).data.map { prefs ->
            SourceCloudSettings(
                enabled = prefs[enabledKey] ?: true,
                connectedServices = parseServiceKeys(prefs[connectedServicesKey].orEmpty())
            )
        }
    }

    suspend fun setEnabled(enabled: Boolean) {
        store().edit { it[enabledKey] = enabled }
    }

    suspend fun setServiceConnected(service: SourceCloudService, connected: Boolean) {
        store().edit { prefs ->
            val current = parseServiceKeys(prefs[connectedServicesKey].orEmpty()).toMutableSet()
            if (connected) current += service else current -= service
            prefs[connectedServicesKey] = encodeServiceKeys(current)
        }
    }

    suspend fun setConnectedServices(services: Set<SourceCloudService>) {
        store().edit { prefs ->
            prefs[connectedServicesKey] = encodeServiceKeys(services)
        }
    }
}
