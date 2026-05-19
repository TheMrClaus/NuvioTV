import {
  CONFIG_STATUS_LABELS,
  SERVICE_LABELS,
  SUPPORTED_SERVICES,
  createServiceClient,
  ensureAioStreamsConfig,
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

  if (request.method !== "GET") {
    return errorResponse(405, "Method not allowed");
  }

  const authResult = requireAuth(request);
  if (authResult instanceof Response) return authResult;
  const { userId } = authResult;

  const url = new URL(request.url);
  const rawProfileId = url.searchParams.get("profileId");
  const profileId = parseProfileId(rawProfileId);
  if (profileId === null) {
    return errorResponse(400, "Invalid profileId");
  }

  const client = createServiceClient();
  const ownerId = await resolveOwnerId(client, userId);

  const [settingsResult, configsResult, tokensResult] = await Promise.all([
    client
      .from("source_cloud_settings")
      .select("enabled")
      .eq("user_id", ownerId)
      .eq("profile_id", profileId)
      .maybeSingle(),
    client
      .from("source_cloud_configs")
      .select("config_status")
      .eq("user_id", ownerId)
      .eq("profile_id", profileId)
      .maybeSingle(),
    client
      .from("source_cloud_service_tokens")
      .select("service, status, label")
      .eq("user_id", ownerId)
      .eq("profile_id", profileId),
  ]);

  if (settingsResult.error) {
    return errorResponse(500, "Failed to load settings");
  }

  const hasConnectedToken =
    (tokensResult.data ?? []).some(
      (t: { status: string }) => t.status === "connected",
    );

  const configRow = configsResult.data;
  let configStatus = configRow?.config_status ?? "unknown";

  if (hasConnectedToken && (!configRow || configStatus === "not_provisioned" || configStatus === "unknown")) {
    const { status } = await ensureAioStreamsConfig(
      client,
      ownerId,
      profileId,
    );
    configStatus = status;
  }

  const statusInfo = CONFIG_STATUS_LABELS[configStatus] ?? CONFIG_STATUS_LABELS.unknown;

  // The advanced-session endpoint only requires auth + valid profileId, both
  // already validated above. Reporting "available" here means "the client may
  // press the button to create a new short-lived session"; it does not mean a
  // session URL is currently cached.
  const advancedConfigAvailable = true;

  const canReset = configRow != null && configStatus !== "unknown";

  const services = SUPPORTED_SERVICES.map((service: SupportedService) => {
    const tokenRow = (tokensResult.data ?? []).find(
      (t: { service: string }) => t.service === service,
    );
    const connected = tokenRow?.status === "connected";
    return {
      service,
      connected,
      label: tokenRow?.label ?? SERVICE_LABELS[service],
      message: connected
        ? null
        : tokenRow?.status === "error"
          ? "Connection error"
          : tokenRow?.status === "expired"
            ? "Token expired"
            : null,
    };
  });

  return jsonResponse(200, {
    config: {
      status: configStatus,
      label: statusInfo.label,
      message: statusInfo.message,
      advancedConfigAvailable,
      canReset,
    },
    services,
  });
});
