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
  AIO_CONFIG_STATUS_LABELS,
  ageRatingFromString,
  aioMetadataBaseUrl,
  applyKidsOverlayToCatalogs,
  applyShallowPatch,
  applySettingsPatch,
  configureUrl,
  fallbackManifestUrl,
  fetchAioMetadataLink,
  fetchProfileKidsState,
  generateConfigPassword,
  updateProfileMaxAgeRating,
  upsertAioMetadataLink,
  upstreamLoadConfig,
  upstreamSaveConfig,
  upstreamUpdateConfig,
} from "../_shared/aio_metadata.ts";

/**
 * Apply a partial patch to the upstream AIOMetadata config and persist the
 * result. Auto-provisions on first save (no existing link row), mirroring
 * AioMetadataRepositoryImpl.createConfig on the TV side.
 *
 * Body: {
 *   profileId: number,
 *   apiKeys?: Record<string, string | null>,   // null/empty clears
 *   providers?: Record<string, unknown>,        // routing config patch
 *   settings?: Record<string, unknown>,         // flat-root field patch
 *   catalogs?: Array<Record<string, unknown>>,  // full replacement
 * }
 *
 * Returns the same shape as aio-metadata-status so the panel can refresh
 * without a second round-trip.
 */
Deno.serve(async (request) => {
  const cors = handleCors(request);
  if (cors) return cors;

  if (request.method !== "POST") return errorResponse(405, "Method not allowed");

  const authResult = requireAuth(request);
  if (authResult instanceof Response) return authResult;
  const { userId } = authResult;

  let body: {
    profileId?: unknown;
    apiKeys?: unknown;
    providers?: unknown;
    settings?: unknown;
    catalogs?: unknown;
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

  const apiKeysPatch = isPlainObject(body.apiKeys) ? body.apiKeys : undefined;
  const providersPatch = isPlainObject(body.providers) ? body.providers : undefined;
  const settingsPatch = isPlainObject(body.settings) ? body.settings : undefined;
  const catalogsReplacement = Array.isArray(body.catalogs)
    ? (body.catalogs as Array<unknown>).filter(isPlainObject) as Array<Record<string, unknown>>
    : undefined;

  if (
    apiKeysPatch === undefined &&
    providersPatch === undefined &&
    settingsPatch === undefined &&
    catalogsReplacement === undefined
  ) {
    return errorResponse(400, "Patch body is empty");
  }

  if (!aioMetadataBaseUrl()) return errorResponse(500, "AIOMETADATA_BASE_URL not configured");

  const client = createServiceClient();
  const ownerId = await resolveOwnerId(client, userId);
  const existing = await fetchAioMetadataLink(client, ownerId, profileId);

  // Load the current config (or seed an empty one for first-save) and apply
  // the patch.
  let baseConfig: Record<string, unknown> = {};
  let uuid: string | null = existing?.aio_uuid ?? null;
  let password = existing?.config_password && existing.config_password.length > 0
    ? existing.config_password
    : null;

  if (uuid && password) {
    try {
      const loaded = await upstreamLoadConfig(uuid, password);
      baseConfig = loaded.config ?? {};
    } catch (err) {
      const message = err instanceof Error ? err.message : "Unknown error";
      console.warn(`aio-metadata-update-config load failed: ${message}`);
      return errorResponse(502, `Failed to load existing config: ${message}`);
    }
  }

  const providers = isPlainObject(baseConfig.providers)
    ? { ...(baseConfig.providers as Record<string, unknown>) }
    : {};
  const apiKeys = isPlainObject(baseConfig.apiKeys)
    ? { ...(baseConfig.apiKeys as Record<string, unknown>) }
    : {};

  applyShallowPatch(providers, providersPatch);
  applyShallowPatch(apiKeys, apiKeysPatch);

  const nextConfig: Record<string, unknown> = { ...baseConfig };
  nextConfig.providers = providers;
  nextConfig.apiKeys = apiKeys;
  if (catalogsReplacement !== undefined) {
    nextConfig.catalogs = catalogsReplacement;
  }
  applySettingsPatch(nextConfig, settingsPatch);

  // --- Two-way Kids overlay sync -------------------------------------------
  //
  // When `settings.ageRating` is in the patch AND this profile is flagged as
  // Kids in public.profiles, the rating change should propagate to:
  //   (a) public.profiles.max_age_rating  — so the TV's profile screen mirrors it
  //   (b) every TMDB Discover catalog's params/formState — via the Kids overlay
  //       (cert.lte clamp, with_genres / without_genres restrictions)
  //
  // For non-Kids profiles this whole block is a no-op — settings.ageRating
  // is just a flat setting like any other.
  const ageRatingInPatch =
    settingsPatch !== undefined &&
    Object.prototype.hasOwnProperty.call(settingsPatch, "ageRating");
  if (ageRatingInPatch) {
    const profile = await fetchProfileKidsState(client, ownerId, profileId);
    if (profile.isKids) {
      const newTier = ageRatingFromString(settingsPatch?.ageRating);
      const catalogs = Array.isArray(nextConfig.catalogs)
        ? nextConfig.catalogs as Array<Record<string, unknown>>
        : [];
      nextConfig.catalogs = applyKidsOverlayToCatalogs(catalogs, newTier);
      // Mirror to public.profiles. The TV next sync sees it and the Kids
      // profile chip row updates without manual intervention.
      if (profile.maxAgeRating !== newTier) {
        await updateProfileMaxAgeRating(client, ownerId, profileId, newTier);
      }
    }
  }

  // First-save path mints the password and POSTs save; subsequent updates PUT.
  let manifestUrl = existing?.manifest_url ?? "";
  let configStatus = "ready";

  try {
    if (uuid && password) {
      const resp = await upstreamUpdateConfig(uuid, nextConfig, password);
      if (resp.installUrl && resp.installUrl.length > 0) manifestUrl = resp.installUrl;
    } else {
      const fresh = generateConfigPassword();
      const resp = await upstreamSaveConfig(nextConfig, fresh);
      uuid = resp.userUUID;
      password = fresh;
      manifestUrl = resp.installUrl ?? fallbackManifestUrl(uuid);
    }
  } catch (err) {
    const message = err instanceof Error ? err.message : "Unknown error";
    console.warn(`aio-metadata-update-config upstream failure: ${message}`);
    if (uuid) {
      await upsertAioMetadataLink(client, {
        ownerId,
        profileId,
        aioUuid: uuid,
        manifestUrl,
        enabled: existing?.enabled ?? false,
        configPassword: password ?? "",
        configStatus: "provisioning_failed",
        markValidated: false,
      });
    }
    return errorResponse(502, `Upstream save/update failed: ${message}`);
  }

  if (!uuid || !password) {
    return errorResponse(500, "Upstream returned an empty UUID or password");
  }

  await upsertAioMetadataLink(client, {
    ownerId,
    profileId,
    aioUuid: uuid,
    manifestUrl: manifestUrl ?? "",
    enabled: existing?.enabled ?? false,
    configPassword: password,
    configStatus,
    markProvisioned: !existing?.aio_uuid,
    markValidated: true,
  });

  const labels = AIO_CONFIG_STATUS_LABELS[configStatus] ?? AIO_CONFIG_STATUS_LABELS.unknown;
  return jsonResponse(200, {
    config: {
      enabled: existing?.enabled ?? false,
      status: configStatus,
      label: labels.label,
      message: labels.message,
      hasConfig: true,
      canReset: profileId !== 1,
      uuid,
      manifestUrl,
      configureUrl: configureUrl(uuid),
      configPassword: password,
    },
  });
});

function isPlainObject(value: unknown): value is Record<string, unknown> {
  return !!value && typeof value === "object" && !Array.isArray(value);
}
