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
  extractSettings,
  fetchAioMetadataLink,
  markAioMetadataStatus,
  upstreamLoadConfig,
} from "../_shared/aio_metadata.ts";

/**
 * Load the full inner config from the upstream cedya77/aiometadata instance
 * and return it shaped like AioConfigInnerDto in the Kotlin domain layer:
 *   {
 *     providers: {...},        // routing config: movie→tmdb, etc.
 *     apiKeys: {...},          // gemini, tmdb, tvdb, fanart, rpdb, mdblist, …
 *     catalogs: [...],         // catalog list (deep upstream shape)
 *     settings: {...},         // every other flat top-level field
 *   }
 *
 * Returns 404 if the profile has no AIOMetadata config yet — the panel uses
 * that to render the "set keys to provision" empty state.
 *
 * Body: { profileId: number }
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

  const client = createServiceClient();
  const ownerId = await resolveOwnerId(client, userId);
  const link = await fetchAioMetadataLink(client, ownerId, profileId);
  if (!link || !link.aio_uuid || !link.config_password) {
    return jsonResponse(404, {
      error: "No AIOMetadata config provisioned for this profile",
      provisioned: false,
    });
  }

  try {
    const upstream = await upstreamLoadConfig(link.aio_uuid, link.config_password);
    const config = upstream.config ?? {};
    const providers = (config.providers && typeof config.providers === "object")
      ? config.providers as Record<string, unknown>
      : {};
    const apiKeys = (config.apiKeys && typeof config.apiKeys === "object")
      ? config.apiKeys as Record<string, unknown>
      : {};
    const catalogs = Array.isArray(config.catalogs) ? config.catalogs : [];
    const settings = extractSettings(config);

    await markAioMetadataStatus(client, ownerId, profileId, "ready", {
      markValidated: true,
    });

    return jsonResponse(200, {
      provisioned: true,
      uuid: link.aio_uuid,
      manifestUrl: link.manifest_url,
      config: { providers, apiKeys, catalogs, settings },
    });
  } catch (err) {
    const message = err instanceof Error ? err.message : "Unknown error";
    console.warn(`aio-metadata-get-config upstream failure: ${message}`);
    await markAioMetadataStatus(client, ownerId, profileId, "invalid");
    return errorResponse(502, `Failed to load upstream config: ${message}`);
  }
});
