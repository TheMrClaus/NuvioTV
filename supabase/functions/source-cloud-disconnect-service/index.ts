import {
  SERVICE_LABELS,
  SUPPORTED_SERVICES,
  CONFIG_STATUS_LABELS,
  aioBasicAuth,
  createServiceClient,
  decryptAesGcm,
  errorResponse,
  handleCors,
  jsonResponse,
  parseProfileId,
  resolveOwnerId,
  requireAuth,
  type SupportedService,
} from "../_shared/source_cloud.ts";

/**
 * Disconnect a debrid service for the active profile. Removes the service
 * row in `source_cloud_service_tokens`, then PUTs the AIOStreams user
 * config with the remaining (still-connected) services so the credential
 * is no longer applied.
 *
 * Request: POST { profileId: number, service: "real_debrid" | "torbox" }
 * Response: same shape as `source-cloud-status`.
 */

const SERVICE_ID_MAP: Record<SupportedService, string> = {
  real_debrid: "realdebrid",
  torbox: "torbox",
};

interface ServiceEntry {
  id: string;
  enabled: boolean;
  credentials: Record<string, string>;
}

function bumpTorrentioTimeout(config: Record<string, unknown>): void {
  const presets = config.presets;
  if (!Array.isArray(presets)) return;
  for (const preset of presets) {
    if (!preset || typeof preset !== "object") continue;
    const p = preset as Record<string, unknown>;
    if (p.type !== "torrentio") continue;
    const opts = p.options;
    if (opts && typeof opts === "object") {
      (opts as Record<string, unknown>).timeout = 15000;
    }
  }
}

function applyTmdbPolicy(config: Record<string, unknown>): void {
  const tmdbKey = Deno.env.get("AIOSTREAMS_TMDB_API_KEY") ?? "";
  const tmdbToken = Deno.env.get("AIOSTREAMS_TMDB_ACCESS_TOKEN") ?? "";
  if (tmdbKey) config.tmdbApiKey = tmdbKey;
  if (tmdbToken) config.tmdbAccessToken = tmdbToken;
  if (config.tmdbApiKey === "<template_placeholder>") delete config.tmdbApiKey;
  if (config.tmdbAccessToken === "<template_placeholder>") delete config.tmdbAccessToken;
  const hasTmdb = !!(config.tmdbApiKey || config.tmdbAccessToken);
  if (hasTmdb) return;
  const disable = (key: string) => {
    const existing = config[key];
    if (existing && typeof existing === "object") {
      config[key] = { ...(existing as Record<string, unknown>), enabled: false };
    }
  };
  disable("titleMatching");
  disable("yearMatching");
  disable("digitalReleaseFilter");
}

Deno.serve(async (request) => {
  const cors = handleCors(request);
  if (cors) return cors;

  if (request.method !== "POST") {
    return errorResponse(405, "Method not allowed");
  }

  const authResult = requireAuth(request);
  if (authResult instanceof Response) return authResult;
  const { userId } = authResult;

  let body: { profileId?: unknown; service?: unknown };
  try {
    body = await request.json();
  } catch {
    return errorResponse(400, "Invalid JSON body");
  }

  const profileId = parseProfileId(
    typeof body.profileId === "number" ? String(body.profileId) : typeof body.profileId === "string" ? body.profileId : null,
  );
  if (profileId === null) return errorResponse(400, "Invalid profileId");

  const serviceKey = typeof body.service === "string" ? body.service : "";
  if (!SUPPORTED_SERVICES.includes(serviceKey as SupportedService)) {
    return errorResponse(400, "Invalid service");
  }
  const service = serviceKey as SupportedService;

  const baseUrl = (Deno.env.get("AIOSTREAMS_BASE_URL") ?? "").replace(/\/+$/, "");

  const client = createServiceClient();
  const ownerId = await resolveOwnerId(client, userId);

  // Mark the row as disconnected (don't delete; keeps audit + lets the user
  // re-enable later by re-sending credentials).
  await client
    .from("source_cloud_service_tokens")
    .update({ status: "disconnected", last_checked_at: new Date().toISOString() })
    .eq("user_id", ownerId)
    .eq("profile_id", profileId)
    .eq("service", service);

  // Reapply to AIOStreams if a config exists.
  const { data: configRow } = await client
    .from("source_cloud_configs")
    .select("aiostreams_config_id, aiostreams_config_secret_ciphertext, aiostreams_config_secret_nonce")
    .eq("user_id", ownerId)
    .eq("profile_id", profileId)
    .maybeSingle();

  let configStatus: string = "ready";
  let provisioningError: string | null = null;

  if (
    baseUrl &&
    configRow?.aiostreams_config_id &&
    typeof configRow.aiostreams_config_secret_ciphertext === "string" &&
    typeof configRow.aiostreams_config_secret_nonce === "string"
  ) {
    const aiostreamsPassword = await decryptAesGcm(
      configRow.aiostreams_config_secret_ciphertext,
      configRow.aiostreams_config_secret_nonce,
    );
    if (aiostreamsPassword) {
      const uuid = configRow.aiostreams_config_id as string;

      // Rebuild the services array from remaining connected tokens.
      const { data: remainingTokens } = await client
        .from("source_cloud_service_tokens")
        .select("service, access_token_ciphertext, token_nonce, status")
        .eq("user_id", ownerId)
        .eq("profile_id", profileId);

      const services: ServiceEntry[] = [];
      for (const row of remainingTokens ?? []) {
        const rowService = row.service as SupportedService | null;
        if (!rowService || row.status !== "connected") continue;
        if (typeof row.access_token_ciphertext !== "string" || typeof row.token_nonce !== "string") continue;
        const plain = await decryptAesGcm(row.access_token_ciphertext, row.token_nonce);
        if (!plain) continue;
        const aioId = SERVICE_ID_MAP[rowService];
        if (!aioId) continue;
        services.push({ id: aioId, enabled: true, credentials: { apiKey: plain } });
      }

      // Fetch current config so we don't wipe other settings, then splice services.
      const fetchUrl = new URL(`${baseUrl}/api/v1/user`);
      fetchUrl.searchParams.set("raw", "true");
      const fetchResponse = await fetch(fetchUrl.toString(), {
        method: "GET",
        headers: { Authorization: aioBasicAuth(uuid, aiostreamsPassword) },
      });
      if (!fetchResponse.ok) {
        provisioningError = "Failed to fetch existing AIOStreams config";
        configStatus = "provisioning_failed";
      } else {
        const fetchJson = await fetchResponse.json() as { data?: { userData?: Record<string, unknown> } };
        const current = fetchJson.data?.userData ?? null;
        if (!current) {
          provisioningError = "AIOStreams returned empty config";
          configStatus = "provisioning_failed";
        } else {
          const merged: Record<string, unknown> = { ...current, services };
          applyTmdbPolicy(merged);
          bumpTorrentioTimeout(merged);
          const putResponse = await fetch(`${baseUrl}/api/v1/user`, {
            method: "PUT",
            headers: {
              "Content-Type": "application/json",
              Authorization: aioBasicAuth(uuid, aiostreamsPassword),
            },
            body: JSON.stringify({ config: merged }),
          });
          if (!putResponse.ok) {
            provisioningError = "Failed to update AIOStreams config";
            configStatus = "provisioning_failed";
          }
        }
      }
    }
  }

  if (configStatus === "provisioning_failed") {
    await client
      .from("source_cloud_configs")
      .upsert(
        {
          user_id: ownerId,
          profile_id: profileId,
          config_status: "provisioning_failed",
          last_provisioned_at: new Date().toISOString(),
        },
        { onConflict: "user_id,profile_id" },
      );
  }

  // Return status-shaped response.
  const { data: refreshedTokens } = await client
    .from("source_cloud_service_tokens")
    .select("service, status, label")
    .eq("user_id", ownerId)
    .eq("profile_id", profileId);

  const statusInfo = CONFIG_STATUS_LABELS[configStatus] ?? CONFIG_STATUS_LABELS.unknown;

  const servicesPayload = SUPPORTED_SERVICES.map((s) => {
    const tokenRow = (refreshedTokens ?? []).find((t: { service: string }) => t.service === s);
    const connected = tokenRow?.status === "connected";
    return {
      service: s,
      connected,
      label: tokenRow?.label ?? SERVICE_LABELS[s],
      message: connected ? null : tokenRow?.status === "error" ? (provisioningError ?? "Connection error") : null,
    };
  });

  return jsonResponse(200, {
    config: {
      status: configStatus,
      label: statusInfo.label,
      message: statusInfo.message,
      advancedConfigAvailable: true,
      canReset: true,
    },
    services: servicesPayload,
  });
});
