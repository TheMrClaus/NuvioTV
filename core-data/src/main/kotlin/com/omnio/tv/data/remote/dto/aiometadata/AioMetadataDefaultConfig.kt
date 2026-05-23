package com.omnio.tv.data.remote.dto.aiometadata

import android.content.Context
import com.omnio.tv.data.R
import com.omnio.tv.domain.model.AioConfigInnerDto
import org.json.JSONArray
import org.json.JSONObject

/**
 * Builds the default AIOMetadata configuration from the bundled template
 * (res/raw/aiometadata_default_config.json), injects user-supplied API keys,
 * and hard-codes the free RPDB key so poster ratings work out of the box.
 *
 * The template is the "Stremio Perfect Setup" guide config:
 * https://luckynumb3rs.github.io/stremio-perfect-setup/guide/4-AIOMetadata-Setup/
 *
 * Note: the built DTO groups non-routing/non-key/non-catalog template fields
 * under [AioConfigInnerDto.settings] for internal ergonomics. The custom
 * Moshi adapter flattens those entries up to the root on the wire, which is
 * the shape upstream's web `/configure` UI expects.
 */
object AioMetadataDefaultConfig {

    private const val RPDB_FREE_KEY = "t0-free-rpdb"
    const val TEMPLATE_VERSION_KEY = "nuvio_template_version"
    const val TEMPLATE_VERSION = "1"

    fun isTemplateApplied(config: AioConfigInnerDto): Boolean =
        config.settings[TEMPLATE_VERSION_KEY] != null

    fun build(context: Context, userApiKeys: Map<String, String>): AioConfigInnerDto {
        val json = readTemplateJson(context, R.raw.aiometadata_default_config)
        return buildFromTemplate(json, userApiKeys, applyDefaultProviderToggles = true)
    }

    /**
     * Builds a Kids profile AIOMetadata config from the bundled kids template
     * ([R.raw.aiometadata_kids_config]). The kids template is exported from the
     * upstream `/configure` UI and wraps the config payload under a top-level
     * `config` key — we unwrap it here so the rest of the pipeline sees the
     * same shape as the default template.
     *
     * Kids profiles always inherit Main's API keys (TMDB / TVDB / etc.); the
     * caller is responsible for handing them in via [userApiKeys].
     */
    fun buildKids(context: Context, userApiKeys: Map<String, String>): AioConfigInnerDto {
        val raw = readTemplateJson(context, R.raw.aiometadata_kids_config)
        // Kids template is shaped { version, exportedAt, config: { ... }, metadata: {...} }.
        // Unwrap to match the default template's flat shape.
        val inner = raw.optJSONObject("config") ?: raw
        return buildFromTemplate(inner, userApiKeys, applyDefaultProviderToggles = false)
    }

    private fun readTemplateJson(context: Context, rawResId: Int): JSONObject {
        val text = context.resources.openRawResource(rawResId).bufferedReader().use { it.readText() }
        return JSONObject(text)
    }

    private fun buildFromTemplate(
        json: JSONObject,
        userApiKeys: Map<String, String>,
        applyDefaultProviderToggles: Boolean,
    ): AioConfigInnerDto {
        // Routing providers (e.g. "movie" → "tmdb"). Keep this pure routing config —
        // NuvioTV toggle states go into settings under nuvio_provider_* keys.
        val providers: Map<String, Any?> = jsonObjectToMap(json.optJSONObject("providers") ?: JSONObject())

        // Default API keys from template (RPDB is already set to the free key there,
        // but we also enforce it here in case the template ever changes).
        val defaultApiKeys = mutableMapOf<String, String>()
        json.optJSONObject("apiKeys")?.keys()?.forEach { key ->
            val v = json.getJSONObject("apiKeys").optString(key, "")
            if (v.isNotBlank()) defaultApiKeys[key] = v
        }
        if (defaultApiKeys["rpdb"].isNullOrBlank()) {
            defaultApiKeys["rpdb"] = RPDB_FREE_KEY
        }

        // User-supplied keys override defaults (TMDB / TVDB come from the user).
        val finalApiKeys = (defaultApiKeys + userApiKeys).filterValues { it.isNotBlank() }

        val catalogs = jsonArrayToList(json.optJSONArray("catalogs") ?: JSONArray())
            .filterIsInstance<Map<String, Any?>>()

        // Everything else becomes the settings catch-all (language, search config, etc.).
        val excludedKeys = setOf("providers", "apiKeys", "catalogs")
        val baseSettings = json.keys().asSequence()
            .filter { it !in excludedKeys }
            .associate { key -> key to jsonToKotlin(json.get(key)) }

        val extraSettings = mutableMapOf<String, Any?>(
            TEMPLATE_VERSION_KEY to TEMPLATE_VERSION,
        )
        if (applyDefaultProviderToggles) {
            // The default template ships without these markers; the kids template
            // already encodes its own provider-toggle preferences in baseSettings,
            // so we only force them on for the default template.
            extraSettings["nuvio_provider_tmdb"] = true
            extraSettings["nuvio_provider_tvdb"] = true
        }

        return AioConfigInnerDto(
            providers = providers,
            apiKeys = finalApiKeys,
            catalogs = catalogs,
            settings = baseSettings + extraSettings,
        )
    }

    private fun jsonToKotlin(value: Any): Any? = when (value) {
        is JSONObject -> jsonObjectToMap(value)
        is JSONArray -> jsonArrayToList(value)
        JSONObject.NULL -> null
        else -> value
    }

    private fun jsonObjectToMap(obj: JSONObject): Map<String, Any?> =
        obj.keys().asSequence().associate { key -> key to jsonToKotlin(obj.get(key)) }

    private fun jsonArrayToList(arr: JSONArray): List<Any?> =
        (0 until arr.length()).map { i -> jsonToKotlin(arr.get(i)) }
}
