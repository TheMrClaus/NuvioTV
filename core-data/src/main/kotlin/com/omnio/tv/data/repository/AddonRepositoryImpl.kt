package com.omnio.tv.data.repository

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.omnio.tv.domain.result.NetworkResult
import com.omnio.tv.core.network.safeApiCall
import com.omnio.tv.data.local.AddonPreferences
import com.omnio.tv.data.mapper.toDomain
import com.omnio.tv.data.remote.api.AddonApi
import com.omnio.tv.domain.model.Addon
import com.omnio.tv.domain.repository.AddonRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import com.omnio.tv.domain.auth.AuthManager
import com.omnio.tv.domain.sync.AddonSyncService
import com.omnio.tv.domain.sync.RemoteAddon
import javax.inject.Inject

class AddonRepositoryImpl @Inject constructor(
    private val api: AddonApi,
    private val preferences: AddonPreferences,
    private val addonSyncService: AddonSyncService,
    private val authManager: AuthManager,
    @ApplicationContext private val context: Context
) : AddonRepository {

    companion object {
        private const val TAG = "AddonRepository"
        private const val MANIFEST_CACHE_PREFS = "addon_manifest_cache"
        private const val MANIFEST_CACHE_KEY = "manifests_v2"
        private const val LEGACY_MANIFEST_CACHE_KEY = "manifests"
        private const val MANIFEST_SUFFIX = "/manifest.json"
        private const val MANIFEST_CACHE_TTL_MS = 6 * 60 * 60 * 1000L 
    }

    private val syncScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var syncJob: Job? = null
    var isSyncingFromRemote = false

    private fun canonicalizeUrl(url: String): String {
        val trimmed = url.trim().trimEnd('/')
        return if (trimmed.endsWith(MANIFEST_SUFFIX, ignoreCase = true)) {
            trimmed.dropLast(MANIFEST_SUFFIX.length).trimEnd('/')
        } else {
            trimmed
        }
    }

    private fun normalizeUrl(url: String): String = canonicalizeUrl(url).lowercase()

    private fun triggerRemoteSync() {
        if (isSyncingFromRemote) {
            Log.d(TAG, "triggerRemoteSync: skipped (syncing from remote)")
            return
        }
        if (!authManager.isAuthenticated) {
            Log.d(TAG, "triggerRemoteSync: skipped (not authenticated, state=${authManager.authState.value})")
            return
        }
        Log.d(TAG, "triggerRemoteSync: scheduling push in 500ms")
        syncJob?.cancel()
        syncJob = syncScope.launch {
            delay(500)
            val result = addonSyncService.pushToRemote()
            Log.d(TAG, "triggerRemoteSync: push result=${result.isSuccess} ${result.exceptionOrNull()?.message ?: ""}")
        }
    }

    private val gson = Gson()
    private val manifestCache = mutableMapOf<String, Addon>()
    @Volatile
    private var lastManifestRefreshTime = 0L
    private var manifestRefreshJob: Job? = null

    init {
        syncScope.launch { loadManifestCacheFromDisk() }
    }

    private fun isCacheStale(): Boolean =
        System.currentTimeMillis() - lastManifestRefreshTime > MANIFEST_CACHE_TTL_MS

    private fun scheduleManifestRefresh(urls: List<String>) {
        if (manifestRefreshJob?.isActive == true) return
        manifestRefreshJob = syncScope.launch {
            val refreshed = urls.map { url ->
                async {
                    fetchAddon(url)
                }
            }.awaitAll()
            val anyUpdated = refreshed.any { it is NetworkResult.Success }
            if (anyUpdated) {
                lastManifestRefreshTime = System.currentTimeMillis()
                Log.d(TAG, "Background manifest refresh completed")
            }
        }
    }

    private suspend fun loadManifestCacheFromDisk() = kotlinx.coroutines.withContext(Dispatchers.IO) {
        try {
            val prefs = context.getSharedPreferences(MANIFEST_CACHE_PREFS, Context.MODE_PRIVATE)
            if (prefs.contains(LEGACY_MANIFEST_CACHE_KEY)) {
                prefs.edit().remove(LEGACY_MANIFEST_CACHE_KEY).apply()
            }
            val json = prefs.getString(MANIFEST_CACHE_KEY, null) ?: return@withContext
            val type = object : TypeToken<Map<String, Addon>>() {}.type
            val cached: Map<String, Addon> = gson.fromJson(json, type) ?: return@withContext
            manifestCache.putAll(cached)
            Log.d(TAG, "Loaded ${cached.size} cached manifests from disk")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load manifest cache from disk", e)
        }
    }

    private fun persistManifestCacheToDisk() {
        syncScope.launch {
            try {
                val snapshot = manifestCache.toMap()
                val prefs = context.getSharedPreferences(MANIFEST_CACHE_PREFS, Context.MODE_PRIVATE)
                prefs.edit().putString(MANIFEST_CACHE_KEY, gson.toJson(snapshot)).apply()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to persist manifest cache to disk", e)
            }
        }
    }

    override fun getInstalledAddons(): Flow<List<Addon>> =
        combine(
            preferences.installedAddonUrls,
            preferences.disabledAddonUrls
        ) { urls, disabledRaw -> urls to disabledRaw.map(::canonicalizeUrl).toSet() }
            .flatMapLatest { (urls, disabled) ->
                flow {
                    val cached = urls.mapNotNull { manifestCache[canonicalizeUrl(it)] }
                    if (cached.isNotEmpty()) {
                        emit(applyEnabledState(applyDisplayNames(cached), disabled))
                    }

                    val hasCacheMiss = cached.size < urls.size
                    if (hasCacheMiss) {
                        val fresh = coroutineScope {
                            urls.map { url ->
                                async {
                                    val canonical = canonicalizeUrl(url)
                                    manifestCache[canonical] ?: when (val result = fetchAddon(url)) {
                                        is NetworkResult.Success -> result.data
                                        else -> null
                                    }
                                }
                            }.awaitAll().filterNotNull()
                        }

                        if (fresh != cached) {
                            emit(applyEnabledState(applyDisplayNames(fresh), disabled))
                        }
                    } else if (isCacheStale() && urls.isNotEmpty()) {
                        scheduleManifestRefresh(urls)
                    }
                }.flowOn(Dispatchers.IO)
            }

    override suspend fun fetchAddon(baseUrl: String): NetworkResult<Addon> {
        val cleanBaseUrl = canonicalizeUrl(baseUrl)
        val manifestUrl = "$cleanBaseUrl/manifest.json"

        return when (val result = safeApiCall { api.getManifest(manifestUrl) }) {
            is NetworkResult.Success -> {
                val addon = result.data.toDomain(cleanBaseUrl)
                manifestCache[cleanBaseUrl] = addon
                persistManifestCacheToDisk()
                NetworkResult.Success(addon)
            }
            is NetworkResult.Error -> {
                Log.w(TAG, "Failed to fetch addon manifest for url=$manifestUrl code=${result.code} message=${result.message}")
                result
            }
            NetworkResult.Loading -> NetworkResult.Loading
        }
    }

    override suspend fun addAddon(url: String) {
        val cleanUrl = canonicalizeUrl(url)
        preferences.addAddon(cleanUrl)
        triggerRemoteSync()
    }

    override suspend fun removeAddon(url: String) {
        val cleanUrl = canonicalizeUrl(url)
        manifestCache.remove(cleanUrl)
        preferences.removeAddon(cleanUrl)
        triggerRemoteSync()
    }

    override suspend fun setAddonOrder(urls: List<String>) {
        preferences.setAddonOrder(urls)
        triggerRemoteSync()
    }

    override suspend fun setAddonEnabled(url: String, enabled: Boolean) {
        preferences.setAddonEnabled(canonicalizeUrl(url), enabled)
        triggerRemoteSync()
    }

    suspend fun reconcileWithRemoteAddons(
        remoteAddons: List<RemoteAddon>,
        removeMissingLocal: Boolean = true
    ) {
        reconcileWithRemoteAddonUrls(
            remoteUrls = remoteAddons.map { it.url },
            removeMissingLocal = removeMissingLocal
        )
        // Apply remote disabled state. Local URLs that aren't in the remote
        // payload keep their current enabled/disabled state.
        val remoteDisabledByNormalized = remoteAddons
            .filterNot { it.enabled }
            .associate { normalizeUrl(it.url) to canonicalizeUrl(it.url) }
        if (remoteDisabledByNormalized.isEmpty() && remoteAddons.all { it.enabled }) {
            // Remote says everything enabled — clear any locally disabled URLs that
            // appear in the remote list.
            val remoteSet = remoteAddons.map { normalizeUrl(it.url) }.toSet()
            val currentDisabled = preferences.disabledAddonUrls.first()
            val kept = currentDisabled.filter { normalizeUrl(it) !in remoteSet }
            if (kept.size != currentDisabled.size) {
                preferences.setDisabledAddonUrls(kept)
            }
            return
        }
        val currentDisabled = preferences.disabledAddonUrls.first()
        val remoteUrlSet = remoteAddons.map { normalizeUrl(it.url) }.toSet()
        val keptDisabled = currentDisabled.filter { normalizeUrl(it) !in remoteUrlSet }
        val merged = (keptDisabled + remoteDisabledByNormalized.values).toSet()
        preferences.setDisabledAddonUrls(merged)
    }

    suspend fun reconcileWithRemoteAddonUrls(
        remoteUrls: List<String>,
        removeMissingLocal: Boolean = true
    ) {
        val normalizedRemote = remoteUrls
            .map { canonicalizeUrl(it) }
            .filter { it.isNotBlank() }
            .distinctBy { normalizeUrl(it) }
        val remoteSet = normalizedRemote.map { normalizeUrl(it) }.toSet()

        val initialLocalUrls = preferences.installedAddonUrls.first()
        val initialLocalSet = initialLocalUrls.map { normalizeUrl(it) }.toSet()
        val shouldRemoveMissingLocal = if (removeMissingLocal && normalizedRemote.isEmpty() && initialLocalUrls.isNotEmpty()) {
            Log.w(
                TAG,
                "reconcileWithRemoteAddonUrls: remote list empty while local has ${initialLocalUrls.size} entries; preserving local addons"
            )
            false
        } else {
            removeMissingLocal
        }

     
        val localByNormalized = linkedMapOf<String, String>()
        initialLocalUrls.forEach { url ->
            localByNormalized.putIfAbsent(normalizeUrl(url), canonicalizeUrl(url))
        }

        val remoteOrdered = normalizedRemote.map { remote ->
            localByNormalized[normalizeUrl(remote)] ?: remote
        }

        val finalList = if (shouldRemoveMissingLocal) {
            remoteOrdered
        } else {
            val extras = initialLocalUrls
                .map { canonicalizeUrl(it) }
                .filter { normalizeUrl(it) !in remoteSet }
            remoteOrdered + extras
        }

        if (shouldRemoveMissingLocal) {
            initialLocalUrls
                .filter { normalizeUrl(it) !in remoteSet }
                .forEach { manifestCache.remove(canonicalizeUrl(it)) }
        }


        val currentCanonical = initialLocalUrls.map { canonicalizeUrl(it) }
        if (finalList != currentCanonical) {
            preferences.setAddonOrder(finalList)
        }
    }

    private fun applyEnabledState(addons: List<Addon>, disabled: Set<String>): List<Addon> {
        if (disabled.isEmpty()) return addons
        return addons.map { addon ->
            val isEnabled = canonicalizeUrl(addon.baseUrl) !in disabled
            if (addon.enabled == isEnabled) addon else addon.copy(enabled = isEnabled)
        }
    }

    private fun applyDisplayNames(addons: List<Addon>): List<Addon> {
        val nameCounts = mutableMapOf<String, Int>()
        for (addon in addons) {
            nameCounts[addon.name] = (nameCounts[addon.name] ?: 0) + 1
        }

        val nameCounters = mutableMapOf<String, Int>()
        return addons.map { addon ->
            if ((nameCounts[addon.name] ?: 0) <= 1) {
                addon.copy(displayName = addon.name)
            } else {
                val occurrence = (nameCounters[addon.name] ?: 0) + 1
                nameCounters[addon.name] = occurrence
                if (occurrence == 1) {
                    addon.copy(displayName = addon.name)
                } else {
                    addon.copy(displayName = "${addon.name} ($occurrence)")
                }
            }
        }
    }
}
