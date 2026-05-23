import {
  createServiceClient,
  errorResponse,
  handleCors,
  jsonResponse,
  parseProfileId,
  requireAuth,
  resolveOwnerId,
} from "../_shared/source_cloud.ts";
import {
  aioMetadataBaseUrl,
  fallbackManifestUrl,
  fetchAioMetadataLink,
  generateConfigPassword,
  upsertAioMetadataLink,
  upstreamLoadConfig,
  upstreamSaveConfig,
} from "../_shared/aio_metadata.ts";
import defaultTemplateJson from "../_shared/templates/aiometadata_default_config.json" with { type: "json" };
import kidsTemplateJson from "../_shared/templates/aiometadata_kids_config.json" with { type: "json" };

/**
 * Provision a fresh AIOMetadata config for a non-primary profile. Mirrors
 * the TV-side `AioMetadataRepositoryImpl.provisionForNewProfile` so the
 * panel can create profiles end-to-end without delegating to the TV.
 *
 * Request: POST {
 *   profileId: number,                     // 2..N (primary excluded)
 *   kids?: boolean,                        // default false
 *   copyKeysFromMain?: boolean,            // ignored when kids=true (forced)
 *   maxAgeRating?: "G"|"PG"|"PG-13"|"TV-14"|"R"|"NC-17" | null  // kids only
 * }
 * Response: { aioUuid, manifestUrl, reused }
 *
 * Idempotency: if an `aio_metadata_links` row already exists for this
 * (user, profile), return it (`reused: true`) without minting a new
 * upstream config. The panel UI is expected to fan this out per
 * newly-created profile after `sync_push_profiles`.
 */

const RPDB_FREE_KEY = "t0-free-rpdb";
const TEMPLATE_VERSION = "1";
const TEMPLATE_VERSION_KEY = "nuvio_template_version";
const RESERVED_ROOT_KEYS = new Set(["providers", "apiKeys", "catalogs"]);

function buildConfigFromTemplate(
  template: Record<string, unknown>,
  userApiKeys: Record<string, string>,
  applyDefaultProviderToggles: boolean,
): Record<string, unknown> {
  const providers =
    template.providers && typeof template.providers === "object" && !Array.isArray(template.providers)
      ? { ...(template.providers as Record<string, unknown>) }
      : {};

  const defaultApiKeys: Record<string, string> = {};
  if (
    template.apiKeys &&
    typeof template.apiKeys === "object" &&
    !Array.isArray(template.apiKeys)
  ) {
    for (const [k, v] of Object.entries(template.apiKeys as Record<string, unknown>)) {
      if (typeof v === "string" && v.length > 0) defaultApiKeys[k] = v;
    }
  }
  // Mirror the TV: ensure RPDB free key is set when template doesn't carry one.
  if (!defaultApiKeys.rpdb || defaultApiKeys.rpdb.length === 0) {
    defaultApiKeys.rpdb = RPDB_FREE_KEY;
  }

  // User keys override defaults; blanks dropped.
  const apiKeys: Record<string, string> = {};
  for (const [k, v] of Object.entries({ ...defaultApiKeys, ...userApiKeys })) {
    if (typeof v === "string" && v.length > 0) apiKeys[k] = v;
  }

  const catalogs: unknown[] = Array.isArray(template.catalogs) ? (template.catalogs as unknown[]) : [];

  // Settings = template root minus providers/apiKeys/catalogs.
  const settings: Record<string, unknown> = {};
  for (const [k, v] of Object.entries(template)) {
    if (!RESERVED_ROOT_KEYS.has(k)) settings[k] = v;
  }
  settings[TEMPLATE_VERSION_KEY] = TEMPLATE_VERSION;
  if (applyDefaultProviderToggles) {
    settings.nuvio_provider_tmdb = true;
    settings.nuvio_provider_tvdb = true;
  }

  // Wire format upstream expects: providers + apiKeys + catalogs +
  // settings flattened to root (RESERVED keys win over settings if a
  // template ever conflicts).
  return {
    ...settings,
    providers,
    apiKeys,
    catalogs,
  };
}

function loadKidsTemplate(): Record<string, unknown> {
  // Kids template is shaped { version, exportedAt, config: {...}, metadata: {...} } —
  // unwrap to match the default template's flat shape.
  const raw = kidsTemplateJson as Record<string, unknown>;
  const inner = raw.config;
  if (inner && typeof inner === "object" && !Array.isArray(inner)) {
    return inner as Record<string, unknown>;
  }
  return raw;
}

function buildDefaultConfig(userApiKeys: Record<string, string>): Record<string, unknown> {
  return buildConfigFromTemplate(
    defaultTemplateJson as Record<string, unknown>,
    userApiKeys,
    true,
  );
}

function buildKidsConfig(
  userApiKeys: Record<string, string>,
  maxAgeRating: string | null,
): Record<string, unknown> {
  const config = buildConfigFromTemplate(loadKidsTemplate(), userApiKeys, false);
  if (maxAgeRating) config.ageRating = maxAgeRating;
  return config;
}

Deno.serve(async (request) => {
  const cors = handleCors(request);
  if (cors) return cors;

  if (request.method !== "POST") return errorResponse(405, "Method not allowed");

  const authResult = requireAuth(request);
  if (authResult instanceof Response) return authResult;
  const { userId } = authResult;

  let body: {
    profileId?: unknown;
    kids?: unknown;
    copyKeysFromMain?: unknown;
    maxAgeRating?: unknown;
  };
  try {
    body = await request.json();
  } catch {
    return errorResponse(400, "Invalid JSON body");
  }

  const profileId = parseProfileId(
    typeof body.profileId === "number"
      ? String(body.profileId)
      : typeof body.profileId === "string"
        ? body.profileId
        : null,
  );
  if (profileId === null) return errorResponse(400, "Invalid profileId");
  if (profileId === 1) {
    return errorResponse(400, "Cannot provision AIOMetadata for the primary profile via this endpoint");
  }

  const kids = body.kids === true;
  // Kids profiles always inherit Main's keys; otherwise honour the toggle.
  const copyKeysFromMain = kids || body.copyKeysFromMain === true;
  const maxAgeRating = typeof body.maxAgeRating === "string" ? body.maxAgeRating : null;

  const baseUrl = aioMetadataBaseUrl();
  if (!baseUrl) return errorResponse(500, "AIOMETADATA_BASE_URL not configured");

  const client = createServiceClient();
  const ownerId = await resolveOwnerId(client, userId);

  // Idempotent: existing link → return it without minting a new upstream config.
  const existing = await fetchAioMetadataLink(client, ownerId, profileId);
  if (existing && existing.aio_uuid) {
    return jsonResponse(200, {
      aioUuid: existing.aio_uuid,
      manifestUrl: existing.manifest_url,
      reused: true,
    });
  }

  // Optionally pull Main's API keys (TMDB / TVDB / etc.) so the new profile
  // inherits them. Kids profiles strictly require Main to have a config; for
  // non-kids the inherit-from-Main toggle is best-effort.
  const userApiKeys: Record<string, string> = {};
  if (copyKeysFromMain) {
    const mainLink = await fetchAioMetadataLink(client, ownerId, 1);
    if (mainLink?.config_password && mainLink.aio_uuid) {
      try {
        const loadResp = await upstreamLoadConfig(mainLink.aio_uuid, mainLink.config_password);
        const mainConfig = loadResp.config as Record<string, unknown>;
        const mainKeys = mainConfig.apiKeys;
        if (mainKeys && typeof mainKeys === "object" && !Array.isArray(mainKeys)) {
          for (const [k, v] of Object.entries(mainKeys as Record<string, unknown>)) {
            if (typeof v === "string" && v.length > 0) userApiKeys[k] = v;
          }
        }
      } catch (err) {
        if (kids) {
          return errorResponse(
            502,
            `Failed to load Main's AIOMetadata config: ${err instanceof Error ? err.message : String(err)}`,
          );
        }
        // non-kids: tolerate failure, fall through with empty keys (default
        // template's RPDB-free baseline still works).
      }
    } else if (kids) {
      return errorResponse(
        412,
        "Main profile has no AIOMetadata config to copy keys from. Provision Main's AIOMetadata config first.",
      );
    }
  }

  const initialConfig = kids
    ? buildKidsConfig(userApiKeys, maxAgeRating)
    : buildDefaultConfig(userApiKeys);

  const password = generateConfigPassword();
  let saveResp;
  try {
    saveResp = await upstreamSaveConfig(initialConfig, password);
  } catch (err) {
    return errorResponse(
      502,
      `upstream saveConfig failed: ${err instanceof Error ? err.message : String(err)}`,
    );
  }

  const aioUuid = saveResp.userUUID;
  if (!aioUuid) return errorResponse(502, "upstream saveConfig: no userUUID in response");
  const manifestUrl = saveResp.installUrl ?? fallbackManifestUrl(aioUuid);

  await upsertAioMetadataLink(client, {
    ownerId,
    profileId,
    aioUuid,
    manifestUrl,
    enabled: true,
    configPassword: password,
    configStatus: "ready",
    markProvisioned: true,
    markValidated: true,
  });

  return jsonResponse(200, {
    aioUuid,
    manifestUrl,
    reused: false,
  });
});
