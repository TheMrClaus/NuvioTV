package com.omnio.tv.data.local

import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.omnio.tv.domain.model.StreamPreferences
import com.omnio.tv.domain.profile.ProfileManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StreamPreferencesDataStore @Inject constructor(
    private val factory: ProfileDataStoreFactory,
    private val profileManager: ProfileManager
) {
    companion object {
        private const val FEATURE = "stream_preferences"

        private val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    }

    private fun store(profileId: Int = profileManager.activeProfileId.value) =
        factory.get(profileId, FEATURE)

    private val preferencesKey = stringPreferencesKey("stream_preferences_json")

    val preferences: Flow<StreamPreferences> = profileManager.activeProfileId.flatMapLatest { pid ->
        factory.get(pid, FEATURE).data.map { prefs ->
            val raw = prefs[preferencesKey]
            if (raw.isNullOrBlank()) {
                StreamPreferences.DEFAULT
            } else {
                runCatching {
                    json.decodeFromString<StreamPreferences>(raw)
                }.getOrElse { error ->
                    Log.e("StreamPreferencesDS", "Failed to decode preferences", error)
                    StreamPreferences.DEFAULT
                }
            }
        }
    }

    suspend fun setPreferences(prefs: StreamPreferences) {
        val serialized = json.encodeToString(StreamPreferences.serializer(), prefs)
        store().edit { it[preferencesKey] = serialized }
    }

    suspend fun update(transform: (StreamPreferences) -> StreamPreferences) {
        val current = preferences.first()
        setPreferences(transform(current))
    }
}
