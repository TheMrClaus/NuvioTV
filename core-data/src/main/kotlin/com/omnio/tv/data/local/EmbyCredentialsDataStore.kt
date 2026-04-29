package com.omnio.tv.data.local

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.omnio.tv.domain.profile.ProfileManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class EmbyCredentials(
    val serverUrl: String = "",
    val apiKey: String = "",
    val userId: String = "",
    val deviceId: String = ""
) {
    val isConfigured: Boolean
        get() = serverUrl.isNotBlank() && apiKey.isNotBlank() && userId.isNotBlank()
}

@Singleton
class EmbyCredentialsDataStore @Inject constructor(
    private val factory: ProfileDataStoreFactory,
    private val profileManager: ProfileManager
) {
    companion object {
        private const val FEATURE = "emby_credentials"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun store(profileId: Int = profileManager.activeProfileId.value) =
        factory.get(profileId, FEATURE)

    private val serverUrlKey = stringPreferencesKey("emby_server_url")
    private val apiKeyKey = stringPreferencesKey("emby_api_key")
    private val userIdKey = stringPreferencesKey("emby_user_id")
    private val deviceIdKey = stringPreferencesKey("emby_device_id")

    val credentials: Flow<EmbyCredentials> = profileManager.activeProfileId.flatMapLatest { pid ->
        factory.get(pid, FEATURE).data.map { prefs ->
            EmbyCredentials(
                serverUrl = prefs[serverUrlKey]?.trimEnd('/') ?: "",
                apiKey = prefs[apiKeyKey] ?: "",
                userId = prefs[userIdKey] ?: "",
                deviceId = prefs[deviceIdKey] ?: ""
            )
        }
    }

    @Volatile
    var cachedCredentials: EmbyCredentials = EmbyCredentials()
        private set

    init {
        scope.launch {
            credentials.collect { latest ->
                cachedCredentials = latest
            }
        }
    }

    suspend fun saveCredentials(
        serverUrl: String,
        apiKey: String,
        userId: String,
        deviceId: String? = null
    ) {
        val normalizedServerUrl = serverUrl.trim().trimEnd('/')
        val normalizedApiKey = apiKey.trim()
        val normalizedUserId = userId.trim()
        val resolvedDeviceId = deviceId?.trim()?.takeIf { it.isNotBlank() }
            ?: cachedCredentials.deviceId.takeIf { it.isNotBlank() }
            ?: UUID.randomUUID().toString()

        store().edit { prefs ->
            prefs[serverUrlKey] = normalizedServerUrl
            prefs[apiKeyKey] = normalizedApiKey
            prefs[userIdKey] = normalizedUserId
            prefs[deviceIdKey] = resolvedDeviceId
        }

        cachedCredentials = cachedCredentials.copy(
            serverUrl = normalizedServerUrl,
            apiKey = normalizedApiKey,
            userId = normalizedUserId,
            deviceId = resolvedDeviceId
        )
    }

    suspend fun setServerUrl(serverUrl: String) {
        val normalizedServerUrl = serverUrl.trim().trimEnd('/')
        store().edit { prefs ->
            prefs[serverUrlKey] = normalizedServerUrl
        }
        cachedCredentials = cachedCredentials.copy(serverUrl = normalizedServerUrl)
    }

    suspend fun setApiKey(apiKey: String) {
        val normalizedApiKey = apiKey.trim()
        store().edit { prefs ->
            prefs[apiKeyKey] = normalizedApiKey
        }
        cachedCredentials = cachedCredentials.copy(apiKey = normalizedApiKey)
    }

    suspend fun setUserId(userId: String) {
        val normalizedUserId = userId.trim()
        store().edit { prefs ->
            prefs[userIdKey] = normalizedUserId
        }
        cachedCredentials = cachedCredentials.copy(userId = normalizedUserId)
    }

    /**
     * Reads credentials for [profileId] without changing the active profile.
     * Returns null if the profile has no Emby credentials configured.
     */
    suspend fun credentialsForProfile(profileId: Int): EmbyCredentials? {
        val prefs = factory.get(profileId, FEATURE).data.first()
        val creds = EmbyCredentials(
            serverUrl = prefs[serverUrlKey]?.trimEnd('/') ?: "",
            apiKey = prefs[apiKeyKey] ?: "",
            userId = prefs[userIdKey] ?: "",
            deviceId = prefs[deviceIdKey] ?: ""
        )
        return creds.takeIf { it.isConfigured }
    }

    /**
     * Copies the [sourceProfileId]'s Emby credentials onto the active profile
     * with a freshly minted deviceId so each profile shows up as its own
     * client in the Emby server's session list. Returns false when the source
     * has no credentials to copy.
     */
    suspend fun copyFromProfile(sourceProfileId: Int): Boolean {
        val source = credentialsForProfile(sourceProfileId) ?: return false
        saveCredentials(
            serverUrl = source.serverUrl,
            apiKey = source.apiKey,
            userId = source.userId,
            deviceId = UUID.randomUUID().toString()
        )
        return true
    }

    suspend fun clearCredentials() {
        store().edit { prefs ->
            prefs.remove(serverUrlKey)
            prefs.remove(apiKeyKey)
            prefs.remove(userIdKey)
        }
        cachedCredentials = cachedCredentials.copy(
            serverUrl = "",
            apiKey = "",
            userId = ""
        )
    }
}
