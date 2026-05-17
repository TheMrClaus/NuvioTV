import {
  CONFIG_STATUS_LABELS,
  SERVICE_LABELS,
  SUPPORTED_SERVICES,
  createServiceClient,
  errorResponse,
  handleCors,
  jsonResponse,
  parseProfileId,
  resolveOwnerId,
  requireAuth,
  type SupportedService,
} from "../_shared/source_cloud.ts";

Deno.serve(async (request) => {
  const cors = handleCors(request);
  if (cors) return cors;

  if (request.method !== "POST") {
    return errorResponse(405, "Method not allowed");
  }

  const authResult = requireAuth(request);
  if (authResult instanceof Response) return authResult;
  const { userId } = authResult;

  let body: { profileId?: unknown };
  try {
    body = await request.json() as { profileId?: unknown };
  } catch {
    return errorResponse(400, "Invalid JSON body");
  }

  const rawProfileId = body.profileId;
  const profileId = parseProfileId(
    typeof rawProfileId === "number"
      ? String(rawProfileId)
      : typeof rawProfileId === "string"
        ? rawProfileId
        : null,
  );
  if (profileId === null) {
    return errorResponse(400, "Invalid profileId");
  }

  const client = createServiceClient();
  const ownerId = await resolveOwnerId(client, userId);

  const [configResult] = await Promise.all([
    client
      .from("source_cloud_configs")
      .delete()
      .eq("user_id", ownerId)
      .eq("profile_id", profileId),
    client
      .from("source_cloud_advanced_sessions")
      .delete()
      .eq("user_id", ownerId)
      .eq("profile_id", profileId),
  ]);

  if (configResult.error) {
    return errorResponse(500, "Failed to reset config");
  }

  const settingsResult = await client
    .from("source_cloud_settings")
    .select("enabled")
    .eq("user_id", ownerId)
    .eq("profile_id", profileId)
    .maybeSingle();

  const configStatus = "not_provisioned";
  const statusInfo = CONFIG_STATUS_LABELS[configStatus];

  const services = SUPPORTED_SERVICES.map((service: SupportedService) => ({
    service,
    connected: false,
    label: SERVICE_LABELS[service],
    message: null,
  }));

  return jsonResponse(200, {
    config: {
      status: configStatus,
      label: statusInfo.label,
      message: statusInfo.message,
      advancedConfigAvailable: false,
      canReset: false,
    },
    services,
  });
});
