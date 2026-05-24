import {
  CONFIG_STATUS_LABELS,
  SERVICE_LABELS,
  SUPPORTED_SERVICES,
  aioBasicAuth,
  applyConfigAccessKey,
  createServiceClient,
  stripDisallowedPresets,
  stripParentConfig,
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
 * Soft reset: re-apply the AIOStreams "Debrid Starter" template to the
 * existing AIOStreams user (preserving uuid, password, and bookmarkable
 * configure URL). The user's stored debrid credentials are spliced into
 * the template's services[] so they don't have to retype anything.
 *
 * If no AIOStreams user exists yet (no aiostreams_config_id), this
 * degrades to a clean "not_provisioned" state.
 *
 * Why not delete? Hard delete leaks orphan AIOStreams users every time
 * a user resets, and invalidates any URL they bookmarked.
 */

// OmnioTV service key -> AIOStreams service id.
const SERVICE_ID_MAP: Record<SupportedService, string> = {
  real_debrid: "realdebrid",
  torbox: "torbox",
};

interface ServiceEntry {
  id: string;
  enabled: boolean;
  credentials: Record<string, string>;
}

const STARTER_TEMPLATE_ID = "builtin.debrid-starter";

async function fetchStarterConfig(baseUrl: string): Promise<Record<string, unknown> | null> {
  try {
    const response = await fetch(`${baseUrl}/api/v1/templates`);
    if (!response.ok) return null;
    const json = await response.json() as { data?: Array<{ metadata?: { id?: string }; config?: Record<string, unknown> }> };
    return (json.data ?? []).find((t) => t.metadata?.id === STARTER_TEMPLATE_ID)?.config ?? null;
  } catch {
    return null;
  }
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

  let body: { profileId?: unknown };
  try {
    body = await request.json() as { profileId?: unknown };
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

  const baseUrl = (Deno.env.get("AIOSTREAMS_BASE_URL") ?? "").replace(/\/+$/, "");

  const client = createServiceClient();
  const ownerId = await resolveOwnerId(client, userId);

  // Drop any active advanced-session rows — they're per-redirect tokens
  // and there's no reason to keep stale ones around after a reset.
  await client
    .from("source_cloud_advanced_sessions")
    .delete()
    .eq("user_id", ownerId)
    .eq("profile_id", profileId);

  const { data: configRow } = await client
    .from("source_cloud_configs")
    .select("aiostreams_config_id, aiostreams_config_secret_ciphertext, aiostreams_config_secret_nonce")
    .eq("user_id", ownerId)
    .eq("profile_id", profileId)
    .maybeSingle();

  const aioConfigId = configRow?.aiostreams_config_id as string | null | undefined;
  let aiostreamsPassword: string | null = null;
  if (
    aioConfigId &&
    typeof configRow?.aiostreams_config_secret_ciphertext === "string" &&
    typeof configRow?.aiostreams_config_secret_nonce === "string"
  ) {
    aiostreamsPassword = await decryptAesGcm(
      configRow.aiostreams_config_secret_ciphertext,
      configRow.aiostreams_config_secret_nonce,
    );
  }

  // Without an existing AIOStreams user we can't do a soft reset. Just
  // mark the row as not_provisioned and let the next Connect handle
  // creation.
  if (!aioConfigId || !aiostreamsPassword || !baseUrl) {
    await client
      .from("source_cloud_configs")
      .upsert(
        {
          user_id: ownerId,
          profile_id: profileId,
          config_status: "not_provisioned",
          last_provisioned_at: new Date().toISOString(),
        },
        { onConflict: "user_id,profile_id" },
      );
    await client
      .from("source_cloud_service_tokens")
      .update({ status: "disconnected", last_checked_at: new Date().toISOString() })
      .eq("user_id", ownerId)
      .eq("profile_id", profileId);

    const statusInfo = CONFIG_STATUS_LABELS.not_provisioned;
    return jsonResponse(200, {
      config: {
        status: "not_provisioned",
        label: statusInfo.label,
        message: statusInfo.message,
        advancedConfigAvailable: true,
        canReset: false,
      },
      services: SUPPORTED_SERVICES.map((s) => ({
        service: s,
        connected: false,
        label: SERVICE_LABELS[s],
        message: null,
      })),
    });
  }

  // Rebuild services[] from the (already-connected) debrid credentials so
  // the user doesn't lose them when the config gets overwritten.
  const { data: existingTokens } = await client
    .from("source_cloud_service_tokens")
    .select("service, access_token_ciphertext, token_nonce, status")
    .eq("user_id", ownerId)
    .eq("profile_id", profileId);

  const services: ServiceEntry[] = [];
  for (const row of existingTokens ?? []) {
    const rowService = row.service as SupportedService | null;
    if (!rowService) continue;
    if (row.status !== "connected" && row.status !== "error") continue;
    if (typeof row.access_token_ciphertext !== "string" || typeof row.token_nonce !== "string") continue;
    const plain = await decryptAesGcm(row.access_token_ciphertext, row.token_nonce);
    if (!plain) continue;
    const aioId = SERVICE_ID_MAP[rowService];
    if (!aioId) continue;
    services.push({ id: aioId, enabled: true, credentials: { apiKey: plain } });
  }

  const starter = await fetchStarterConfig(baseUrl);
  const merged: Record<string, unknown> = starter
    ? { ...starter, services }
    : {
        services,
        presets: [],
        sortCriteria: { global: [] },
        formatter: { id: "gdrive" },
      };
  applyTmdbPolicy(merged);
  bumpTorrentioTimeout(merged);
  stripDisallowedPresets(merged);
  stripParentConfig(merged);
  applyConfigAccessKey(merged);

  // PUT the merged config onto the existing AIOStreams user.
  const putResponse = await fetch(`${baseUrl}/api/v1/user`, {
    method: "PUT",
    headers: {
      "Content-Type": "application/json",
      Authorization: aioBasicAuth(aioConfigId, aiostreamsPassword),
    },
    body: JSON.stringify({ config: merged }),
  });

  let configStatus: string = "ready";
  let provisioningError: string | null = null;
  if (!putResponse.ok) {
    const text = await putResponse.text().catch(() => "");
    console.warn(`reset PUT failed: ${putResponse.status} ${text.slice(0, 500)}`);
    provisioningError = `HTTP ${putResponse.status}: ${text.slice(0, 300)}`;
    configStatus = "provisioning_failed";
  }

  await client
    .from("source_cloud_configs")
    .update({
      config_status: configStatus,
      last_provisioned_at: new Date().toISOString(),
      last_validated_at: new Date().toISOString(),
    })
    .eq("user_id", ownerId)
    .eq("profile_id", profileId);

  // On a successful PUT, promote any "error" token rows back to "connected"
  // since the credentials are now applied. On failure, leave the rows as-is
  // so the user can see the error state and retry.
  if (configStatus === "ready") {
    await client
      .from("source_cloud_service_tokens")
      .update({ status: "connected", last_checked_at: new Date().toISOString() })
      .eq("user_id", ownerId)
      .eq("profile_id", profileId)
      .eq("status", "error");
  }

  // Return status-shaped response so the client can refresh.
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
      message: provisioningError ?? statusInfo.message,
      advancedConfigAvailable: true,
      canReset: true,
    },
    services: servicesPayload,
  });
});
