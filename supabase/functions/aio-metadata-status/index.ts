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
  configureUrl,
  fetchAioMetadataLink,
  fetchProfileKidsState,
} from "../_shared/aio_metadata.ts";

/**
 * Cheap status endpoint for the panel — reads the aio_metadata_links bridge
 * row and returns the bits needed to render the page shell (enable toggle,
 * manifest URL, configure deep-link, lifecycle state). Does NOT hit upstream
 * — that's `aio-metadata-get-config`'s job.
 */
Deno.serve(async (request) => {
  const cors = handleCors(request);
  if (cors) return cors;

  if (request.method !== "GET") {
    return errorResponse(405, "Method not allowed");
  }

  const authResult = requireAuth(request);
  if (authResult instanceof Response) return authResult;
  const { userId } = authResult;

  const url = new URL(request.url);
  const profileId = parseProfileId(url.searchParams.get("profileId"));
  if (profileId === null) {
    return errorResponse(400, "Invalid profileId");
  }

  const client = createServiceClient();
  const ownerId = await resolveOwnerId(client, userId);
  const [link, profile] = await Promise.all([
    fetchAioMetadataLink(client, ownerId, profileId),
    fetchProfileKidsState(client, ownerId, profileId),
  ]);

  const status = link?.config_status ?? "not_provisioned";
  const labels = AIO_CONFIG_STATUS_LABELS[status] ?? AIO_CONFIG_STATUS_LABELS.unknown;
  const hasConfig = !!(link?.aio_uuid && link.aio_uuid.length > 0);

  return jsonResponse(200, {
    config: {
      enabled: link?.enabled ?? false,
      status,
      label: labels.label,
      message: labels.message,
      hasConfig,
      canReset: profileId !== 1 && hasConfig,
      uuid: link?.aio_uuid ?? null,
      manifestUrl: link?.manifest_url ?? null,
      configureUrl: link ? configureUrl(link.aio_uuid) : null,
      configPassword: link?.config_password ?? null,
      lastProvisionedAt: link?.last_provisioned_at ?? null,
      lastValidatedAt: link?.last_validated_at ?? null,
      isKids: profile.isKids,
      maxAgeRating: profile.maxAgeRating,
    },
  });
});
