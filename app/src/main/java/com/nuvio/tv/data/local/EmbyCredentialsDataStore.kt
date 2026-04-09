package com.nuvio.tv.data.local

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.nuvio.tv.core.profile.ProfileManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

data class EmbyCredentials(
    val serverUrl: String = "",
    val apiKey: String = "",
    val userId: String = ""
) {
    val isConfigured: Boolean
        get() = serverUrl.isNotBlank() && apiKey.isNotBlank()
}

@Singleton
class EmbyCredentialsDataStore @Inject constructor(
    private val factory: ProfileDataStoreFactory,
    private val profileManager: ProfileManager
) {
    companion object {
        private const val FEATURE = "emby_credentials"
    }

    private fun store(profileId: Int = profileManager.activeProfileId.value) =
        factory.get(profileId, FEATURE)

    private val serverUrlKey = stringPreferencesKey("emby_server_url")
    private val apiKeyKey = stringPreferencesKey("emby_api_key")
    private val userIdKey = stringPreferencesKey("emby_user_id")

    val credentials: Flow<EmbyCredentials> = profileManager.activeProfileId.flatMapLatest { pid ->
        factory.get(pid, FEATURE).data.map { prefs ->
            EmbyCredentials(
                serverUrl = prefs[serverUrlKey]?.trimEnd('/') ?: "",
                apiKey = prefs[apiKeyKey] ?: "",
                userId = prefs[userIdKey] ?: ""
            )
        }
    }

    suspend fun setServerUrl(serverUrl: String) {
        store().edit { it[serverUrlKey] = serverUrl.trimEnd('/') }
    }

    suspend fun setApiKey(apiKey: String) {
        store().edit { it[apiKeyKey] = apiKey.trim() }
    }

    suspend fun setUserId(userId: String) {
        store().edit { it[userIdKey] = userId.trim() }
    }

    suspend fun clearCredentials() {
        store().edit { prefs ->
            prefs.remove(serverUrlKey)
            prefs.remove(apiKeyKey)
            prefs.remove(userIdKey)
        }
    }
}
