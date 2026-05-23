import { createClient, SupabaseClient } from "https://esm.sh/@supabase/supabase-js@2.49.8";
import { corsHeaders } from "./cors.ts";

const SUPABASE_URL = Deno.env.get("SUPABASE_URL") ?? "";
const SUPABASE_SERVICE_ROLE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";

const JSON_HEADERS: Record<string, string> = {
  ...corsHeaders,
  "Content-Type": "application/json",
};

export function jsonResponse(status: number, body: Record<string, unknown>): Response {
  return new Response(JSON.stringify(body), { status, headers: JSON_HEADERS });
}

export function errorResponse(status: number, error: string): Response {
  return jsonResponse(status, { error });
}

export function handleCors(request: Request): Response | null {
  if (request.method === "OPTIONS") {
    return new Response(null, { headers: corsHeaders });
  }
  return null;
}

export function requireAuth(request: Request): { userId: string } | Response {
  const authHeader = request.headers.get("Authorization");
  if (!authHeader?.startsWith("Bearer ")) {
    return errorResponse(401, "Missing or invalid authorization header");
  }

  const jwt = authHeader.slice(7);
  try {
    const parts = jwt.split(".");
    if (parts.length !== 3) {
      return errorResponse(401, "Invalid token format");
    }
    const payload = JSON.parse(atob(parts[1]));
    const sub = payload?.sub;
    if (!sub) {
      return errorResponse(401, "Invalid token: missing subject");
    }
    return { userId: sub };
  } catch {
    return errorResponse(401, "Invalid token");
  }
}

export function createServiceClient(): SupabaseClient {
  return createClient(SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY, {
    auth: { autoRefreshToken: false, persistSession: false },
  });
}

export async function resolveOwnerId(
  client: SupabaseClient,
  userId: string
): Promise<string> {
  const { data } = await client
    .from("linked_devices")
    .select("owner_id")
    .eq("device_user_id", userId)
    .limit(1);

  if (data && data.length > 0 && data[0].owner_id) {
    return data[0].owner_id as string;
  }
  return userId;
}

export async function sha256Hex(value: string): Promise<string> {
  const encoded = new TextEncoder().encode(value);
  const digest = await crypto.subtle.digest("SHA-256", encoded);
  return Array.from(new Uint8Array(digest))
    .map((byte) => byte.toString(16).padStart(2, "0"))
    .join("");
}

export function parseProfileId(raw: string | null | undefined): number | null {
  if (raw === null || raw === undefined || raw === "") return 1;
  const n = Number(raw);
  if (!Number.isInteger(n) || n < 1) return null;
  return n;
}

export const SUPPORTED_SERVICES = ["real_debrid", "torbox"] as const;
export type SupportedService = (typeof SUPPORTED_SERVICES)[number];

export const SERVICE_LABELS: Record<SupportedService, string> = {
  real_debrid: "Real-Debrid",
  torbox: "Torbox",
};

export const CONFIG_STATUS_LABELS: Record<string, { label: string; message: string }> = {
  unknown: {
    label: "Status unknown",
    message: "Source cloud status could not be determined",
  },
  not_provisioned: {
    label: "No source config yet",
    message: "Connect a service to enable source cloud",
  },
  ready: {
    label: "Private source config ready",
    message: "Configured for this profile",
  },
  provisioning_failed: {
    label: "Config setup failed",
    message: "Could not create source config; try resetting",
  },
  unavailable: {
    label: "Source cloud unavailable",
    message: "Source cloud is temporarily unavailable",
  },
  invalid: {
    label: "Config invalid",
    message: "Source config is invalid; try resetting",
  },
};

export const ADVANCED_SESSION_TTL_MS = 10 * 60 * 1000;
export const ADVANCED_SESSION_TOKEN_BYTES = 32;

const AIOSTREAMS_BASE_URL = Deno.env.get("AIOSTREAMS_BASE_URL") ?? "";
const AIOSTREAMS_ADDON_PASSWORD = Deno.env.get("AIOSTREAMS_ADDON_PASSWORD") ?? "";

function base64ToUint8Array(b64: string): Uint8Array {
  const binary = atob(b64);
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i++) {
    bytes[i] = binary.charCodeAt(i);
  }
  return bytes;
}

function uint8ArrayToBase64(bytes: Uint8Array): string {
  let binary = "";
  for (let i = 0; i < bytes.length; i++) {
    binary += String.fromCharCode(bytes[i]);
  }
  return btoa(binary);
}

async function getEncryptionKey(): Promise<CryptoKey | null> {
  const envKey = Deno.env.get("SOURCE_CLOUD_ENCRYPTION_KEY");
  if (!envKey) return null;
  try {
    const rawKey = base64ToUint8Array(envKey);
    return await crypto.subtle.importKey(
      "raw",
      rawKey,
      { name: "AES-GCM" },
      false,
      ["encrypt", "decrypt"],
    );
  } catch {
    return null;
  }
}

export async function encryptAesGcm(plaintext: string): Promise<{
  ciphertext: string;
  nonce: string;
} | null> {
  const key = await getEncryptionKey();
  if (!key) return null;
  const nonce = crypto.getRandomValues(new Uint8Array(12));
  const encoded = new TextEncoder().encode(plaintext);
  const ciphertext = await crypto.subtle.encrypt(
    { name: "AES-GCM", iv: nonce },
    key,
    encoded,
  );
  return {
    ciphertext: uint8ArrayToBase64(new Uint8Array(ciphertext)),
    nonce: uint8ArrayToBase64(nonce),
  };
}

export function aioBasicAuth(uuid: string, password: string): string {
  return `Basic ${btoa(`${uuid}:${password}`)}`;
}

export async function decryptAesGcm(
  ciphertextB64: string,
  nonceB64: string,
): Promise<string | null> {
  const key = await getEncryptionKey();
  if (!key) return null;
  try {
    const ciphertext = base64ToUint8Array(ciphertextB64);
    const nonce = base64ToUint8Array(nonceB64);
    const decrypted = await crypto.subtle.decrypt(
      { name: "AES-GCM", iv: nonce },
      key,
      ciphertext,
    );
    return new TextDecoder().decode(decrypted);
  } catch {
    return null;
  }
}

function extractUuidFromInstallUrl(installUrl: string | undefined): string | null {
  if (!installUrl) return null;
  const match = installUrl.match(/\/stremio\/([a-f0-9-]+)\//i);
  return match?.[1] ?? null;
}

export async function ensureAioStreamsConfig(
  client: SupabaseClient,
  ownerId: string,
  profileId: number,
): Promise<{ configId: string | null; status: string }> {
  const baseUrl = AIOSTREAMS_BASE_URL;
  const addonPassword = AIOSTREAMS_ADDON_PASSWORD;

  const { data: existing } = await client
    .from("source_cloud_configs")
    .select("aiostreams_config_id, config_status")
    .eq("user_id", ownerId)
    .eq("profile_id", profileId)
    .maybeSingle();

  if (existing && existing.config_status === "ready" && existing.aiostreams_config_id) {
    return { configId: existing.aiostreams_config_id as string, status: "ready" };
  }

  if (!baseUrl || !addonPassword) {
    return { configId: null, status: "unavailable" };
  }

  try {
    const saveResponse = await fetch(`${baseUrl}/api/v1/config/save`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        "X-Addon-Password": addonPassword,
      },
    });

    if (!saveResponse.ok) {
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
      return { configId: null, status: "provisioning_failed" };
    }

    const data = await saveResponse.json() as Record<string, unknown>;
    const configId =
      (data.uuid as string) ??
      extractUuidFromInstallUrl(data.installUrl as string | undefined);

    if (!configId) {
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
      return { configId: null, status: "provisioning_failed" };
    }

    const encrypted = await encryptAesGcm(addonPassword);
    await client
      .from("source_cloud_configs")
      .upsert(
        {
          user_id: ownerId,
          profile_id: profileId,
          aiostreams_config_id: configId,
          aiostreams_config_secret_ciphertext: encrypted?.ciphertext ?? null,
          aiostreams_config_secret_nonce: encrypted?.nonce ?? null,
          config_status: "ready",
          last_provisioned_at: new Date().toISOString(),
          last_validated_at: new Date().toISOString(),
        },
        { onConflict: "user_id,profile_id" },
      );

    return { configId, status: "ready" };
  } catch {
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
    return { configId: null, status: "provisioning_failed" };
  }
}

export interface NormalizedStream {
  name?: string;
  title?: string;
  description?: string;
  url?: string;
  ytId?: string | null;
  infoHash?: string;
  fileIdx?: number;
  externalUrl?: string;
  behaviorHints?: {
    notWebReady?: boolean;
    bingeGroup?: string;
    countryWhitelist?: string[];
    requestHeaders?: Record<string, string>;
    responseHeaders?: Record<string, string>;
    videoHash?: string;
    videoSize?: number;
    filename?: string;
  };
  metadata?: {
    quality?: string;
    sizeBytes?: number;
    codec?: string;
    audio?: string;
    hdr?: string;
    language?: string;
    cached?: boolean;
    sourceConfidence?: number;
    sourceService?: string;
  };
}

function parseQuality(title: string): string | undefined {
  if (/\b4k\b|2160p/i.test(title)) return "4K";
  if (/\b1080p\b/i.test(title)) return "1080p";
  if (/\b720p\b/i.test(title)) return "720p";
  if (/\b480p\b/i.test(title)) return "480p";
  return undefined;
}

function parseCodec(title: string): string | undefined {
  if (/hevc|h\.?265|x265/i.test(title)) return "HEVC";
  if (/av1/i.test(title)) return "AV1";
  if (/h\.?264|x264/i.test(title)) return "H.264";
  return undefined;
}

function parseHdr(title: string): string | undefined {
  if (/hdr10\+/i.test(title)) return "HDR10+";
  if (/hdr10/i.test(title)) return "HDR10";
  if (/hdr/i.test(title)) return "HDR";
  if (/\bdv\b|dolby\s*vision/i.test(title)) return "DV";
  return undefined;
}

function parseAudio(title: string): string | undefined {
  if (/atmos/i.test(title)) return "Atmos";
  if (/dts[\s-]?hd/i.test(title)) return "DTS-HD";
  if (/\bdts\b/i.test(title)) return "DTS";
  if (/\baac\b/i.test(title)) return "AAC";
  return undefined;
}

function parseLanguage(title: string): string | undefined {
  const match = title.match(/\b(MULTi|DUAL|VFI|VF2|VFQ|VF|VOSTFR?|TRUE?FRENCH|FRENCH)\b/i);
  return match?.[1]?.toUpperCase();
}

function inferSourceService(name: string, title: string): string | undefined {
  const combined = `${name} ${title}`.toLowerCase();
  if (/real.?debrid/i.test(combined)) return "real_debrid";
  if (/torbox/i.test(combined)) return "torbox";
  return undefined;
}

function normalizeOneStream(raw: Record<string, unknown>): NormalizedStream {
  const title = (raw.title as string) ?? "";
  const rawName = (raw.name as string) ?? "";
  const service = inferSourceService(rawName, title);
  const isDebrid = service === "real_debrid" || service === "torbox";
  const description = (raw.description as string) ?? "";
  const isLikelyCached = isDebrid && !/(download|uncached)/i.test(description);
  const hints = raw.behaviorHints as Record<string, unknown> | undefined;

  return {
    name: rawName || undefined,
    title: title || undefined,
    description: description || undefined,
    url: (raw.url as string) ?? undefined,
    infoHash: (raw.infoHash as string) ?? undefined,
    fileIdx: typeof raw.fileIdx === "number" ? raw.fileIdx : undefined,
    externalUrl: (raw.externalUrl as string) ?? undefined,
    behaviorHints: hints ? {
      notWebReady: typeof hints.notWebReady === "boolean" ? hints.notWebReady : undefined,
      bingeGroup: (hints.bingeGroup as string) ?? undefined,
      countryWhitelist: Array.isArray(hints.countryWhitelist)
        ? hints.countryWhitelist.filter((v): v is string => typeof v === "string")
        : undefined,
      requestHeaders: hints.requestHeaders && typeof hints.requestHeaders === "object"
        ? hints.requestHeaders as Record<string, string>
        : undefined,
      responseHeaders: hints.responseHeaders && typeof hints.responseHeaders === "object"
        ? hints.responseHeaders as Record<string, string>
        : undefined,
      videoHash: (hints.videoHash as string) ?? undefined,
      videoSize: typeof hints.videoSize === "number" ? hints.videoSize : undefined,
      filename: (hints.filename as string) ?? undefined,
    } : undefined,
    metadata: {
      quality: parseQuality(title),
      sizeBytes: typeof hints?.videoSize === "number" ? hints.videoSize : undefined,
      codec: parseCodec(title),
      audio: parseAudio(title),
      hdr: parseHdr(title),
      language: parseLanguage(title),
      cached: isLikelyCached ? true : undefined,
      sourceConfidence: 0.5,
      sourceService: service,
    },
  };
}

export function normalizeAioStreamsResult(
  rawStreams: unknown,
): NormalizedStream[] {
  const list = Array.isArray(rawStreams) ? rawStreams : [];
  return list
    .filter((s): s is Record<string, unknown> =>
      s != null && typeof s === "object" && !Array.isArray(s))
    .map(normalizeOneStream);
}

export async function fetchAioStreamsSearch(
  configId: string,
  encryptedPassword: string,
  type: string,
  videoId: string,
  season: number | null | undefined,
  episode: number | null | undefined,
): Promise<NormalizedStream[]> {
  const baseUrl = AIOSTREAMS_BASE_URL;
  if (!baseUrl || !configId || !encryptedPassword) return [];

  // AIOStreams' Stremio endpoints live under the authenticated route
  // /stremio/:uuid/:encryptedPassword/... — the unauthenticated
  // /stremio/<uuid>/stream/... path doesn't load the user's config and
  // returns nothing useful. encryptedPassword is the AES-encrypted form
  // we stashed on the source_cloud_configs row during provisioning.
  let path: string;
  if (type === "movie") {
    path = `/stremio/${configId}/${encryptedPassword}/stream/movie/${encodeURIComponent(videoId)}.json`;
  } else if (type === "series") {
    const id =
      season != null && episode != null
        ? `${videoId}:${season}:${episode}`
        : videoId;
    path = `/stremio/${configId}/${encryptedPassword}/stream/series/${encodeURIComponent(id)}.json`;
  } else {
    return [];
  }

  try {
    const response = await fetch(`${baseUrl}${path}`);
    if (!response.ok) return [];
    const data = await response.json() as Record<string, unknown>;
    return normalizeAioStreamsResult(data?.streams);
  } catch {
    return [];
  }
}