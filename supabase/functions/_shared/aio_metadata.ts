import type { SupabaseClient } from "https://esm.sh/@supabase/supabase-js@2.49.8";

/**
 * AIOMetadata edge-function helpers. Mirrors the surface of _shared/source_cloud.ts
 * but for the cedya77/aiometadata self-hosted upstream (see infra/aiometadata/).
 *
 * Upstream protocol:
 *   POST  {base}/api/config/save             body: { config, password, addonPassword? } → { userUUID, installUrl }
 *   POST  {base}/api/config/load/{uuid}      body: { password, addonPassword? }          → { userUUID, config }
 *   PUT   {base}/api/config/update/{uuid}    body: { config, password, addonPassword? } → { userUUID, installUrl }
 *
 * The DB bridge is public.aio_metadata_links (composite PK user_id, profile_id):
 *   - aio_uuid: opaque upstream user id minted on first save
 *   - config_password: secret needed for subsequent save/update/load calls
 *   - manifest_url: addon manifest the TV/phone consumes
 *   - enabled: whether the addon is currently mounted in the user's profile
 *   - config_status / last_provisioned_at / last_validated_at: panel lifecycle state
 *     (added in migration 014)
 */

export const AIOMETADATA_TABLE = "aio_metadata_links";

const AIOMETADATA_BASE_URL = (Deno.env.get("AIOMETADATA_BASE_URL") ?? "").replace(/\/+$/, "");

export function aioMetadataBaseUrl(): string {
  return AIOMETADATA_BASE_URL;
}

export const AIO_CONFIG_STATUS_LABELS: Record<string, { label: string; message: string }> = {
  unknown: {
    label: "Status unknown",
    message: "AIOMetadata status could not be determined",
  },
  not_provisioned: {
    label: "Not provisioned",
    message: "Save your provider keys to create an AIOMetadata config",
  },
  ready: {
    label: "AIOMetadata ready",
    message: "Configured for this profile",
  },
  provisioning_failed: {
    label: "Provisioning failed",
    message: "Could not create the AIOMetadata config; try saving again",
  },
  unavailable: {
    label: "AIOMetadata unavailable",
    message: "The AIOMetadata service is temporarily unavailable",
  },
  invalid: {
    label: "Config invalid",
    message: "Upstream rejected the config; check provider keys and retry",
  },
};

export interface AioMetadataLinkRow {
  user_id: string;
  profile_id: number;
  aio_uuid: string;
  enabled: boolean;
  manifest_url: string | null;
  config_password: string | null;
  config_status: string;
  last_provisioned_at: string | null;
  last_validated_at: string | null;
}

export async function fetchAioMetadataLink(
  client: SupabaseClient,
  ownerId: string,
  profileId: number,
): Promise<AioMetadataLinkRow | null> {
  const { data } = await client
    .from(AIOMETADATA_TABLE)
    .select(
      "user_id, profile_id, aio_uuid, enabled, manifest_url, config_password, config_status, last_provisioned_at, last_validated_at",
    )
    .eq("user_id", ownerId)
    .eq("profile_id", profileId)
    .maybeSingle();
  return (data as AioMetadataLinkRow | null) ?? null;
}

export interface UpsertAioMetadataLinkInput {
  ownerId: string;
  profileId: number;
  aioUuid: string;
  manifestUrl: string;
  enabled: boolean;
  configPassword: string;
  configStatus: string;
  markProvisioned?: boolean;
  markValidated?: boolean;
}

export async function upsertAioMetadataLink(
  client: SupabaseClient,
  input: UpsertAioMetadataLinkInput,
): Promise<void> {
  const now = new Date().toISOString();
  const row: Record<string, unknown> = {
    user_id: input.ownerId,
    profile_id: input.profileId,
    aio_uuid: input.aioUuid,
    enabled: input.enabled,
    manifest_url: input.manifestUrl,
    config_password: input.configPassword,
    config_status: input.configStatus,
  };
  if (input.markProvisioned) row.last_provisioned_at = now;
  if (input.markValidated) row.last_validated_at = now;
  await client.from(AIOMETADATA_TABLE).upsert(row, { onConflict: "user_id,profile_id" });
}

export async function markAioMetadataStatus(
  client: SupabaseClient,
  ownerId: string,
  profileId: number,
  status: string,
  options: { markProvisioned?: boolean; markValidated?: boolean } = {},
): Promise<void> {
  const now = new Date().toISOString();
  const patch: Record<string, unknown> = { config_status: status };
  if (options.markProvisioned) patch.last_provisioned_at = now;
  if (options.markValidated) patch.last_validated_at = now;
  await client
    .from(AIOMETADATA_TABLE)
    .update(patch)
    .eq("user_id", ownerId)
    .eq("profile_id", profileId);
}

export function generateConfigPassword(): string {
  const bytes = new Uint8Array(32);
  crypto.getRandomValues(bytes);
  let binary = "";
  for (const b of bytes) binary += String.fromCharCode(b);
  return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

export function fallbackManifestUrl(uuid: string): string {
  if (!AIOMETADATA_BASE_URL || !uuid) return "";
  return `${AIOMETADATA_BASE_URL}/api/manifest/${uuid}/manifest.json`;
}

export function configureUrl(uuid: string): string {
  if (!AIOMETADATA_BASE_URL || !uuid) return "";
  return `${AIOMETADATA_BASE_URL}/stremio/${uuid}/configure`;
}

// --- Upstream API wrappers --------------------------------------------------

export interface UpstreamConfigResponse {
  userUUID: string;
  installUrl?: string | null;
  success?: boolean | null;
  message?: string | null;
}

export interface UpstreamLoadResponse {
  userUUID: string;
  config: Record<string, unknown>;
  success?: boolean | null;
}

export async function upstreamSaveConfig(
  config: Record<string, unknown>,
  password: string,
): Promise<UpstreamConfigResponse> {
  if (!AIOMETADATA_BASE_URL) throw new Error("AIOMETADATA_BASE_URL not configured");
  const response = await fetch(`${AIOMETADATA_BASE_URL}/api/config/save`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ config, password }),
  });
  if (!response.ok) {
    const text = await response.text().catch(() => "");
    throw new Error(`upstream saveConfig HTTP ${response.status}: ${text.slice(0, 300)}`);
  }
  return (await response.json()) as UpstreamConfigResponse;
}

export async function upstreamLoadConfig(
  uuid: string,
  password: string,
): Promise<UpstreamLoadResponse> {
  if (!AIOMETADATA_BASE_URL) throw new Error("AIOMETADATA_BASE_URL not configured");
  const response = await fetch(`${AIOMETADATA_BASE_URL}/api/config/load/${encodeURIComponent(uuid)}`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ password }),
  });
  if (!response.ok) {
    const text = await response.text().catch(() => "");
    throw new Error(`upstream loadConfig HTTP ${response.status}: ${text.slice(0, 300)}`);
  }
  return (await response.json()) as UpstreamLoadResponse;
}

export async function upstreamUpdateConfig(
  uuid: string,
  config: Record<string, unknown>,
  password: string,
): Promise<UpstreamConfigResponse> {
  if (!AIOMETADATA_BASE_URL) throw new Error("AIOMETADATA_BASE_URL not configured");
  const response = await fetch(`${AIOMETADATA_BASE_URL}/api/config/update/${encodeURIComponent(uuid)}`, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ config, password }),
  });
  if (!response.ok) {
    const text = await response.text().catch(() => "");
    throw new Error(`upstream updateConfig HTTP ${response.status}: ${text.slice(0, 300)}`);
  }
  return (await response.json()) as UpstreamConfigResponse;
}

// --- Patch helpers ----------------------------------------------------------

/**
 * Apply a shallow patch to a nested object field on the AIOMetadata inner
 * config. Used for `apiKeys`, `providers`, and the flat-key catch-all that
 * upstream calls "settings".
 *
 * Semantics:
 *  - undefined value → no-op
 *  - null OR empty string → delete the key
 *  - any other value → set the key
 */
export function applyShallowPatch(
  target: Record<string, unknown>,
  patch: Record<string, unknown> | undefined,
): void {
  if (!patch) return;
  for (const [key, value] of Object.entries(patch)) {
    if (value === undefined) continue;
    if (value === null || value === "") {
      delete target[key];
    } else {
      target[key] = value;
    }
  }
}

/**
 * The AIOMetadata inner config has a flat shape: `providers`, `apiKeys`,
 * `catalogs` are explicit, and *every other top-level key* is a "setting".
 * Our DTO groups those non-routing/non-key/non-catalog fields under
 * `settings` for ergonomics, but on the wire (and in upstream's view)
 * they live at the root.
 *
 * This helper applies a settings patch by writing each entry directly at
 * the root of the config.
 */
export function applySettingsPatch(
  config: Record<string, unknown>,
  patch: Record<string, unknown> | undefined,
): void {
  applyShallowPatch(config, patch);
}

const RESERVED_ROOT_KEYS = new Set(["providers", "apiKeys", "catalogs"]);

/**
 * Strip the routing/keys/catalogs keys from the inner config and return
 * the remaining flat root entries as a single "settings" map — matching
 * the shape AioConfigInnerDto carries in the Kotlin domain layer.
 */
export function extractSettings(
  config: Record<string, unknown>,
): Record<string, unknown> {
  const out: Record<string, unknown> = {};
  for (const [key, value] of Object.entries(config)) {
    if (RESERVED_ROOT_KEYS.has(key)) continue;
    out[key] = value;
  }
  return out;
}
