package com.omnio.tv.core.sync

import android.util.Log
import com.omnio.tv.domain.auth.AuthManager
import com.omnio.tv.domain.model.AuthState
import com.omnio.tv.domain.profile.ProfileManager
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "RealtimeChannelMgr"
private const val PULL_DEBOUNCE_MS = 1500L

/**
 * Subscribes to Supabase Realtime channels for tables the panel can edit, so
 * that panel writes propagate to a running TV without waiting for app resume /
 * Sync Now / startup.
 *
 * Subscriptions are gated on (foregrounded && AuthState.FullAccount). When the
 * app goes background or the user signs out, all channels are unsubscribed. On
 * active-profile change, channels are torn down and re-subscribed with the new
 * profile_id filter so we don't get notifications for other profiles.
 *
 * On any realtime event we just call StartupSyncService.requestSyncNow() —
 * which already debounces internally — instead of doing a per-table pull here.
 * Optimistic-concurrency on the relational push RPCs (migrations 016–019)
 * protects against panel/TV write races; this manager only needs to ensure the
 * TV's pull is timely.
 */
@Singleton
class RealtimeChannelManager @Inject constructor(
    private val supabaseClient: SupabaseClient,
    private val authManager: AuthManager,
    private val profileManager: ProfileManager,
    private val startupSyncService: StartupSyncService,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val foregroundFlow = MutableStateFlow(false)
    private var managerJob: Job? = null
    private val activeChannels = mutableMapOf<String, RealtimeChannel>()
    private var debounceJob: Job? = null
    @Volatile private var pendingIncludeProfileSettings: Boolean = false

    /** Call once from Application.onCreate(). Idempotent. */
    fun start() {
        if (managerJob != null) return
        managerJob = scope.launch {
            combine(
                foregroundFlow,
                authManager.authState,
                profileManager.activeProfileId,
            ) { foreground, auth, profileId ->
                Triple(foreground, auth, profileId)
            }.distinctUntilChanged().collect { (foreground, auth, profileId) ->
                if (foreground && auth is AuthState.FullAccount) {
                    subscribeAll(profileId)
                } else {
                    unsubscribeAll()
                }
            }
        }
    }

    /** Hooked from MainActivity.onResume() / onPause(). */
    fun setForegrounded(value: Boolean) {
        foregroundFlow.value = value
    }

    private suspend fun subscribeAll(profileId: Int) {
        unsubscribeAll()
        try {
            supabaseClient.realtime.connect()
            registerChannel("addons", profileId) {
                it.postgresChangeFlow<PostgresAction>(schema = "public") {
                    table = "addons"
                    filter("profile_id", FilterOperator.EQ, profileId)
                }
            }
            registerChannel("plugins", profileId) {
                it.postgresChangeFlow<PostgresAction>(schema = "public") {
                    table = "plugins"
                    filter("profile_id", FilterOperator.EQ, profileId)
                }
            }
            registerChannel("collections", profileId) {
                it.postgresChangeFlow<PostgresAction>(schema = "public") {
                    table = "collections"
                    filter("profile_id", FilterOperator.EQ, profileId)
                }
            }
            registerChannel("profile_settings", profileId, includeProfileSettings = true) {
                it.postgresChangeFlow<PostgresAction>(schema = "public") {
                    table = "profile_settings"
                    filter("profile_id", FilterOperator.EQ, profileId)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to subscribe realtime channels", e)
        }
    }

    private suspend fun registerChannel(
        key: String,
        profileId: Int,
        includeProfileSettings: Boolean = false,
        flowFactory: (RealtimeChannel) -> kotlinx.coroutines.flow.Flow<PostgresAction>,
    ) {
        val channel = supabaseClient.channel("$key:p$profileId")
        val changes = flowFactory(channel)
        scope.launch {
            changes.collect {
                Log.d(TAG, "Realtime event on $key, scheduling pull")
                schedulePull(includeProfileSettings)
            }
        }
        channel.subscribe()
        activeChannels[key] = channel
    }

    private suspend fun unsubscribeAll() {
        debounceJob?.cancel()
        debounceJob = null
        for ((_, channel) in activeChannels) {
            runCatching { channel.unsubscribe() }
        }
        activeChannels.clear()
        runCatching { supabaseClient.realtime.disconnect() }
    }

    private fun schedulePull(includeProfileSettings: Boolean) {
        if (includeProfileSettings) pendingIncludeProfileSettings = true
        debounceJob?.cancel()
        debounceJob = scope.launch {
            delay(PULL_DEBOUNCE_MS)
            val include = pendingIncludeProfileSettings
            pendingIncludeProfileSettings = false
            startupSyncService.requestSyncNow(includeProfileSettings = include)
        }
    }
}
