import {
  CONFIG_STATUS_LABELS,
  aioBasicAuth,
  applyConfigAccessKey,
  createServiceClient,
  stripDisallowedPresets,
  decryptAesGcm,
  encryptAesGcm,
  errorResponse,
  handleCors,
  jsonResponse,
  parseProfileId,
  resolveOwnerId,
  requireAuth,
} from "../_shared/source_cloud.ts";

import kidsTemplateSetup from "../_shared/templates/aiostreams_kids_template.json" with { type: "json" };

/**
 * Provision a starter AIOStreams config for a profile that has just been
 * created. The new TV-side profile-create flow calls this once per profile,
 * regardless of whether the user has connected a debrid service yet. The
 * source-cloud-connect-service handler later updates this same config with
 * service credentials.
 *
 * Request: POST {
 *   profileId: number,
 *   kids?: boolean,                // default false
 *   copyKeysFromMain?: boolean,    // ignored when kids=true (forced true)
 * }
 * Response: { config: { status, label, message }, aiostreamsConfigId?: string }
 *
 * Template selection:
 *  - kids=true   → bundled aiostreams_kids_template.json (Setup export — we
 *                  unwrap the .config payload).
 *  - kids=false  → AIOStreams' builtin.debrid-starter, fetched at runtime.
 *
 * Idempotency: if a source_cloud_configs row with aiostreams_config_id
 * already exists, we return early with the existing config (status ready).
 * Re-provisioning is intentionally a separate code path (see source-cloud-reset).
 */

const STARTER_TEMPLATE_ID = "builtin.debrid-starter";

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

async function fetchStarterConfig(baseUrl: string): Promise<Record<string, unknown> | null> {
  try {
    const response = await fetch(`${baseUrl}/api/v1/templates`);
    if (!response.ok) return null;
    const json = await response.json() as {
      data?: Array<{ metadata?: { id?: string }; config?: Record<string, unknown> }>
    };
    const tpl = (json.data ?? []).find((t) => t.metadata?.id === STARTER_TEMPLATE_ID);
    return tpl?.config ?? null;
  } catch {
    return null;
  }
}

function loadKidsConfig(): Record<string, unknown> | null {
  // The Setup JSON wraps the actual payload under `config`. The TS JSON import
  // is typed as unknown; runtime-validate the shape before returning.
  const setup = kidsTemplateSetup as { config?: unknown };
  const config = setup?.config;
  if (!config || typeof config !== "object" || Array.isArray(config)) return null;
  return config as Record<string, unknown>;
}

/**
 * AIOStreams rejects configs that enable titleMatching / yearMatching /
 * digitalReleaseFilter without a TMDB API key. If we have a key (env
 * var AIOSTREAMS_TMDB_API_KEY) plumb it through; otherwise disable
 * those features so validation passes.
 */
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

const COPYABLE_API_KEYS = [
  "tmdbApiKey",
  "tmdbAccessToken",
  "tvdbApiKey",
  "rpdbApiKey",
  "debridioApiKey",
] as const;

/**
 * Read the Main profile's existing AIOStreams config (if provisioned) and
 * return just the API key fields. Used to seed a new profile's config when
 * the user opted in to "copy API keys from Main".
 */
async function readMainApiKeys(
  client: ReturnType<typeof createServiceClient>,
  ownerId: string,
  baseUrl: string,
): Promise<Record<string, string>> {
  const { data: mainRow } = await client
    .from("source_cloud_configs")
    .select("aiostreams_config_id, aiostreams_config_secret_ciphertext, aiostreams_config_secret_nonce")
    .eq("user_id", ownerId)
    .eq("profile_id", 1)
    .maybeSingle();

  const configId = mainRow?.aiostreams_config_id as string | null | undefined;
  const cipher = mainRow?.aiostreams_config_secret_ciphertext as string | null | undefined;
  const nonce = mainRow?.aiostreams_config_secret_nonce as string | null | undefined;
  if (!configId || !cipher || !nonce) return {};

  const password = await decryptAesGcm(cipher, nonce);
  if (!password) return {};

  const url = new URL(`${baseUrl}/api/v1/user`);
  url.searchParams.set("raw", "true");
  try {
    const response = await fetch(url.toString(), {
      method: "GET",
      headers: { Authorization: aioBasicAuth(configId, password) },
    });
    if (!response.ok) return {};
    const json = await response.json() as { data?: { userData?: Record<string, unknown> } };
    const userData = json.data?.userData;
    if (!userData) return {};
    const keys: Record<string, string> = {};
    for (const k of COPYABLE_API_KEYS) {
      const v = userData[k];
      if (typeof v === "string" && v.length > 0 && v !== "<template_placeholder>") {
        keys[k] = v;
      }
    }
    return keys;
  } catch {
    return {};
  }
}

async function aioCreateUser(
  baseUrl: string,
  services: ServiceEntry[],
  password: string,
  config: Record<string, unknown>,
): Promise<{ uuid: string; encryptedPassword?: string; error?: string }> {
  const merged: Record<string, unknown> = { ...config, services };
  applyTmdbPolicy(merged);
  bumpTorrentioTimeout(merged);
  stripDisallowedPresets(merged);
  applyConfigAccessKey(merged);
  const response = await fetch(`${baseUrl}/api/v1/user`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ config: merged, password }),
  });
  if (!response.ok) {
    const text = await response.text().catch(() => "");
    console.warn(`aioCreateUser failed: ${response.status} ${text.slice(0, 500)}`);
    return { uuid: "", error: `HTTP ${response.status}: ${text.slice(0, 300)}` };
  }
  const json = await response.json() as { data?: { uuid?: string; encryptedPassword?: string } };
  const uuid = json.data?.uuid;
  if (!uuid) return { uuid: "", error: "No uuid in response" };
  return { uuid, encryptedPassword: json.data?.encryptedPassword };
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

  let body: { profileId?: unknown; kids?: unknown; copyKeysFromMain?: unknown };
  try {
    body = await request.json();
  } catch {
    return errorResponse(400, "Invalid JSON body");
  }

  const profileId = parseProfileId(
    typeof body.profileId === "number" ? String(body.profileId)
      : typeof body.profileId === "string" ? body.profileId
      : null,
  );
  if (profileId === null) return errorResponse(400, "Invalid profileId");
  if (profileId === 1) return errorResponse(400, "Cannot provision the primary profile via this endpoint");

  const kids = body.kids === true;
  // Kids profiles always inherit Main's keys; otherwise honour the toggle.
  const copyKeysFromMain = kids || body.copyKeysFromMain === true;

  const baseUrl = (Deno.env.get("AIOSTREAMS_BASE_URL") ?? "").replace(/\/+$/, "");
  if (!baseUrl) return errorResponse(500, "AIOSTREAMS_BASE_URL not configured");

  const client = createServiceClient();
  const ownerId = await resolveOwnerId(client, userId);

  // Idempotent: if this profile already has a config, return it. The TV
  // create flow shouldn't fire twice but make sure we don't mint duplicate
  // upstream users on a retry.
  const { data: existingRow } = await client
    .from("source_cloud_configs")
    .select("aiostreams_config_id, config_status")
    .eq("user_id", ownerId)
    .eq("profile_id", profileId)
    .maybeSingle();
  if (existingRow?.aiostreams_config_id) {
    const statusInfo = CONFIG_STATUS_LABELS[existingRow.config_status as string] ?? CONFIG_STATUS_LABELS.ready;
    return jsonResponse(200, {
      config: {
        status: existingRow.config_status ?? "ready",
        label: statusInfo.label,
        message: statusInfo.message,
      },
      aiostreamsConfigId: existingRow.aiostreams_config_id as string,
      reused: true,
    });
  }

  // Pick template. Kids template ships with the repo; non-kids fetches from
  // upstream so a template change on AIOStreams' side flows through.
  const templateConfig: Record<string, unknown> | null = kids
    ? loadKidsConfig()
    : await fetchStarterConfig(baseUrl);
  if (!templateConfig) {
    return errorResponse(500, kids
      ? "Kids template missing or malformed"
      : "Failed to load AIOStreams starter template");
  }

  // Copy keys from Main if requested. We mutate a shallow copy of the
  // template so the imported JSON stays untouched between invocations.
  const config: Record<string, unknown> = { ...templateConfig };
  if (copyKeysFromMain) {
    const mainKeys = await readMainApiKeys(client, ownerId, baseUrl);
    for (const [k, v] of Object.entries(mainKeys)) {
      config[k] = v;
    }
  }

  const newPassword = randomHex(32);
  const created = await aioCreateUser(baseUrl, [], newPassword, config);
  if (!created.uuid) {
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
    return jsonResponse(200, {
      config: {
        status: "provisioning_failed",
        label: CONFIG_STATUS_LABELS.provisioning_failed.label,
        message: created.error ?? CONFIG_STATUS_LABELS.provisioning_failed.message,
      },
    });
  }

  const encPassword = await encryptAesGcm(newPassword);
  if (!encPassword) {
    return errorResponse(500, "Encryption key not configured");
  }

  await client
    .from("source_cloud_configs")
    .upsert(
      {
        user_id: ownerId,
        profile_id: profileId,
        aiostreams_config_id: created.uuid,
        aiostreams_config_secret_ciphertext: encPassword.ciphertext,
        aiostreams_config_secret_nonce: encPassword.nonce,
        aiostreams_encrypted_password: created.encryptedPassword ?? null,
        config_status: "ready",
        last_provisioned_at: new Date().toISOString(),
        last_validated_at: new Date().toISOString(),
      },
      { onConflict: "user_id,profile_id" },
    );

  const statusInfo = CONFIG_STATUS_LABELS.ready;
  return jsonResponse(200, {
    config: {
      status: "ready",
      label: statusInfo.label,
      message: statusInfo.message,
    },
    aiostreamsConfigId: created.uuid,
  });
});
