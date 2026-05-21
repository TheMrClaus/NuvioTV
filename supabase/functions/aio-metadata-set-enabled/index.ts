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
  AIOMETADATA_TABLE,
  fetchAioMetadataLink,
} from "../_shared/aio_metadata.ts";

/**
 * Toggle the per-profile `enabled` flag on aio_metadata_links. This is the
 * bridge state the TV reads to decide whether to mount the AIOMetadata
 * manifest in the user's addon list — we do NOT mutate the addons table
 * from here. The TV side syncs addon membership when it next opens the
 * AIOMetadata settings screen (see AioMetadataRepositoryImpl.setEnabled).
 *
 * Body: { profileId: number, enabled: boolean }
 */
Deno.serve(async (request) => {
  const cors = handleCors(request);
  if (cors) return cors;

  if (request.method !== "POST") return errorResponse(405, "Method not allowed");

  const authResult = requireAuth(request);
  if (authResult instanceof Response) return authResult;
  const { userId } = authResult;

  let body: { profileId?: unknown; enabled?: unknown };
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
  if (typeof body.enabled !== "boolean") return errorResponse(400, "Missing or invalid 'enabled'");

  const client = createServiceClient();
  const ownerId = await resolveOwnerId(client, userId);
  const link = await fetchAioMetadataLink(client, ownerId, profileId);
  if (!link) return errorResponse(404, "No AIOMetadata link to toggle");

  await client
    .from(AIOMETADATA_TABLE)
    .update({ enabled: body.enabled })
    .eq("user_id", ownerId)
    .eq("profile_id", profileId);

  return jsonResponse(200, { enabled: body.enabled });
});
