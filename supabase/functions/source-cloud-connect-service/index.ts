import {
  SERVICE_LABELS,
  SUPPORTED_SERVICES,
  CONFIG_STATUS_LABELS,
  createServiceClient,
  decryptAesGcm,
  encryptAesGcm,
  errorResponse,
  handleCors,
  jsonResponse,
  parseProfileId,
  resolveOwnerId,
  requireAuth,
  type SupportedService,
} from "../_shared/source_cloud.ts";

/**
 * Connect (or refresh) a debrid service for the active profile. Writes the
 * encrypted credential into `source_cloud_service_tokens`, then either
 * provisions a new AIOStreams user (first-time connect) or fetches the
 * existing user and PUTs an updated config that includes our service.
 *
 * Request: POST { profileId: number, service: "real_debrid" | "torbox", apiKey: string }
 * Response: same shape as `source-cloud-status` (refreshes the panel in one round trip).
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

function randomHex(byteLength: number): string {
  const bytes = new Uint8Array(byteLength);
  crypto.getRandomValues(bytes);
  return Array.from(bytes)
    .map((b) => b.toString(16).padStart(2, "0"))
    .join("");
}

async function aioCreateUser(
  baseUrl: string,
  services: ServiceEntry[],
  password: string,
): Promise<{ uuid: string } | null> {
  const config = {
    services,
    presets: [],
    sortCriteria: { global: [] },
    formatter: { id: "gdrive" },
  };
  const response = await fetch(`${baseUrl}/api/v1/user`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ config, password }),
  });
  if (!response.ok) return null;
  const json = await response.json() as { data?: { uuid?: string } };
  const uuid = json.data?.uuid;
  return uuid ? { uuid } : null;
}

async function aioFetchConfig(
  baseUrl: string,
  uuid: string,
  password: string,
): Promise<Record<string, unknown> | null> {
  const url = new URL(`${baseUrl}/api/v1/user`);
  url.searchParams.set("uuid", uuid);
  url.searchParams.set("password", password);
  url.searchParams.set("raw", "true");
  const response = await fetch(url.toString(), { method: "GET" });
  if (!response.ok) return null;
  const json = await response.json() as { data?: Record<string, unknown> };
  return json.data ?? null;
}

async function aioUpdateUser(
  baseUrl: string,
  uuid: string,
  password: string,
  config: Record<string, unknown>,
): Promise<boolean> {
  const response = await fetch(`${baseUrl}/api/v1/user`, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ uuid, password, config }),
  });
  return response.ok;
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

  let body: { profileId?: unknown; service?: unknown; apiKey?: unknown };
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

  const apiKey = typeof body.apiKey === "string" ? body.apiKey.trim() : "";
  if (!apiKey) return errorResponse(400, "Invalid apiKey");

  const baseUrl = (Deno.env.get("AIOSTREAMS_BASE_URL") ?? "").replace(/\/+$/, "");
  if (!baseUrl) return errorResponse(500, "AIOSTREAMS_BASE_URL not configured");

  const client = createServiceClient();
  const ownerId = await resolveOwnerId(client, userId);

  // Encrypt the new credential for storage in source_cloud_service_tokens.
  const encryptedCredential = await encryptAesGcm(apiKey);
  if (!encryptedCredential) {
    return errorResponse(500, "Encryption key not configured");
  }

  // Build the services array from the current source_cloud_service_tokens
  // (the latest credential per service) plus our incoming credential. This
  // table is treated as the app's source of truth.
  const { data: existingTokens } = await client
    .from("source_cloud_service_tokens")
    .select("service, access_token_ciphertext, token_nonce, status")
    .eq("user_id", ownerId)
    .eq("profile_id", profileId);

  const services: ServiceEntry[] = [];
  for (const row of existingTokens ?? []) {
    const rowService = row.service as SupportedService | null;
    if (!rowService || rowService === service) continue; // skip the row we're overwriting
    if (row.status !== "connected") continue;
    if (typeof row.access_token_ciphertext !== "string" || typeof row.token_nonce !== "string") continue;
    const plain = await decryptAesGcm(row.access_token_ciphertext, row.token_nonce);
    if (!plain) continue;
    const aioId = SERVICE_ID_MAP[rowService];
    if (!aioId) continue;
    services.push({ id: aioId, enabled: true, credentials: { apiKey: plain } });
  }
  services.push({
    id: SERVICE_ID_MAP[service],
    enabled: true,
    credentials: { apiKey },
  });

  // Look up the existing AIOStreams config (if any) so we can update vs create.
  const { data: configRow } = await client
    .from("source_cloud_configs")
    .select("aiostreams_config_id, aiostreams_config_secret_ciphertext, aiostreams_config_secret_nonce")
    .eq("user_id", ownerId)
    .eq("profile_id", profileId)
    .maybeSingle();

  let aiostreamsConfigId: string | null = (configRow?.aiostreams_config_id as string | null) ?? null;
  let aiostreamsPassword: string | null = null;
  if (
    aiostreamsConfigId &&
    typeof configRow?.aiostreams_config_secret_ciphertext === "string" &&
    typeof configRow?.aiostreams_config_secret_nonce === "string"
  ) {
    aiostreamsPassword = await decryptAesGcm(
      configRow.aiostreams_config_secret_ciphertext,
      configRow.aiostreams_config_secret_nonce,
    );
  }

  let provisioningError: string | null = null;
  let configStatus: string = "ready";

  if (aiostreamsConfigId && aiostreamsPassword) {
    // Update existing user: fetch their config, splice our services array in, PUT back.
    const current = await aioFetchConfig(baseUrl, aiostreamsConfigId, aiostreamsPassword);
    if (!current) {
      provisioningError = "Failed to fetch existing AIOStreams config";
      configStatus = "provisioning_failed";
    } else {
      const merged = { ...current, services };
      const ok = await aioUpdateUser(baseUrl, aiostreamsConfigId, aiostreamsPassword, merged);
      if (!ok) {
        provisioningError = "Failed to update AIOStreams config";
        configStatus = "provisioning_failed";
      }
    }
  } else {
    // First-time provision.
    const newPassword = randomHex(32);
    const created = await aioCreateUser(baseUrl, services, newPassword);
    if (!created) {
      provisioningError = "Failed to create AIOStreams user";
      configStatus = "provisioning_failed";
    } else {
      aiostreamsConfigId = created.uuid;
      const encPassword = await encryptAesGcm(newPassword);
      if (!encPassword) {
        provisioningError = "Encryption key not configured";
        configStatus = "provisioning_failed";
      } else {
        await client
          .from("source_cloud_configs")
          .upsert(
            {
              user_id: ownerId,
              profile_id: profileId,
              aiostreams_config_id: aiostreamsConfigId,
              aiostreams_config_secret_ciphertext: encPassword.ciphertext,
              aiostreams_config_secret_nonce: encPassword.nonce,
              config_status: "ready",
              last_provisioned_at: new Date().toISOString(),
              last_validated_at: new Date().toISOString(),
            },
            { onConflict: "user_id,profile_id" },
          );
      }
    }
  }

  // Write the per-service token row. We persist the row even when AIOStreams
  // provisioning fails — it lets a subsequent retry pick up where we left off.
  const tokenStatus = provisioningError ? "error" : "connected";
  await client
    .from("source_cloud_service_tokens")
    .upsert(
      {
        user_id: ownerId,
        profile_id: profileId,
        service,
        access_token_ciphertext: encryptedCredential.ciphertext,
        token_nonce: encryptedCredential.nonce,
        status: tokenStatus,
        label: SERVICE_LABELS[service],
        last_checked_at: new Date().toISOString(),
      },
      { onConflict: "user_id,profile_id,service" },
    );

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

  // Build the status-shaped response so the app can refresh in one round trip.
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
      canReset: configStatus !== "unknown",
    },
    services: servicesPayload,
  });
});
