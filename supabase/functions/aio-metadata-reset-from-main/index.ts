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
  aioMetadataBaseUrl,
  configureUrl,
  fallbackManifestUrl,
  fetchAioMetadataLink,
  generateConfigPassword,
  upsertAioMetadataLink,
  upstreamLoadConfig,
  upstreamSaveConfig,
} from "../_shared/aio_metadata.ts";

/**
 * Re-seed a non-primary profile's AIOMetadata config from Main's. Mirrors
 * AioMetadataRepositoryImpl.provisionFromMain on the TV side — but does NOT
 * apply the Kids-tier catalog overlay (AioMetadataKidsConfig). For Kids
 * profiles that need the overlay, the TV-initiated reset remains the right
 * path; the panel just copies Main's config verbatim and lets the user prune
 * catalogs from the new catalog form if needed.
 *
 * Body: { profileId: number } — must be != 1
 */
Deno.serve(async (request) => {
  const cors = handleCors(request);
  if (cors) return cors;

  if (request.method !== "POST") return errorResponse(405, "Method not allowed");

  const authResult = requireAuth(request);
  if (authResult instanceof Response) return authResult;
  const { userId } = authResult;

  let body: { profileId?: unknown };
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
  if (profileId === 1) return errorResponse(400, "Cannot reset Main from itself");

  if (!aioMetadataBaseUrl()) return errorResponse(500, "AIOMETADATA_BASE_URL not configured");

  const client = createServiceClient();
  const ownerId = await resolveOwnerId(client, userId);

  const mainLink = await fetchAioMetadataLink(client, ownerId, 1);
  if (!mainLink || !mainLink.aio_uuid || !mainLink.config_password) {
    return errorResponse(400, "Main profile has no AIOMetadata config to copy from");
  }

  let mainConfig: Record<string, unknown>;
  try {
    const loaded = await upstreamLoadConfig(mainLink.aio_uuid, mainLink.config_password);
    mainConfig = loaded.config ?? {};
  } catch (err) {
    const message = err instanceof Error ? err.message : "Unknown error";
    return errorResponse(502, `Failed to load Main's config: ${message}`);
  }

  const existing = await fetchAioMetadataLink(client, ownerId, profileId);
  const newPassword = generateConfigPassword();

  let userUuid: string;
  let manifestUrl: string;
  try {
    const resp = await upstreamSaveConfig(mainConfig, newPassword);
    userUuid = resp.userUUID;
    manifestUrl = resp.installUrl ?? fallbackManifestUrl(userUuid);
  } catch (err) {
    const message = err instanceof Error ? err.message : "Unknown error";
    return errorResponse(502, `Upstream save failed: ${message}`);
  }

  await upsertAioMetadataLink(client, {
    ownerId,
    profileId,
    aioUuid: userUuid,
    manifestUrl,
    enabled: existing?.enabled ?? true,
    configPassword: newPassword,
    configStatus: "ready",
    markProvisioned: true,
    markValidated: true,
  });

  const labels = AIO_CONFIG_STATUS_LABELS.ready;
  return jsonResponse(200, {
    config: {
      enabled: existing?.enabled ?? true,
      status: "ready",
      label: labels.label,
      message: labels.message,
      hasConfig: true,
      canReset: true,
      uuid: userUuid,
      manifestUrl,
      configureUrl: configureUrl(userUuid),
      configPassword: newPassword,
    },
  });
});
