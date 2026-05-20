import {
  createServiceClient,
  decryptAesGcm,
  errorResponse,
  handleCors,
  jsonResponse,
  parseProfileId,
  resolveOwnerId,
  requireAuth,
} from "../_shared/source_cloud.ts";

/**
 * Return the user-facing slice of the AIOStreams config that the panel
 * forms render/edit. Does not expose the full config — just the values
 * our forms bind to.
 *
 * Request: POST { profileId }
 * Response (200): {
 *   tmdbApiKey: string | null,
 *   tmdbAccessToken: string | null,
 *   tvdbApiKey: string | null,
 *   rpdbApiKey: string | null,
 *   animeToshoEnabled: boolean,
 *   debridioApiKey: string | null,
 *   presets: Array<{ instanceId, type, name, enabled }>,
 *   excludedResolutions: string[],
 *   preferredResolutions: string[],
 *   excludedQualities: string[],
 *   preferredQualities: string[],
 *   sortCriteria: Array<{ key, direction }>,
 *   provisioned: boolean,
 * }
 *
 * When the user has no AIOStreams config yet, returns all fields as
 * null/false/[] with provisioned=false.
 */

interface PresetSummary {
  instanceId: string;
  type: string;
  name: string;
  enabled: boolean;
  timeout: number | null;
  mediaTypes: string[];
  useMultipleInstances: boolean;
}

function stringArray(raw: unknown): string[] {
  if (!Array.isArray(raw)) return [];
  return raw.filter((v): v is string => typeof v === "string" && v.length > 0);
}

interface SortEntry {
  key: string;
  direction: "asc" | "desc";
}

function summariseSortCriteria(raw: unknown): SortEntry[] {
  // AIOStreams stores sort as { global: SortCriterion[] }.
  const global = raw && typeof raw === "object"
    ? (raw as Record<string, unknown>).global
    : null;
  if (!Array.isArray(global)) return [];
  const out: SortEntry[] = [];
  for (const entry of global) {
    if (!entry || typeof entry !== "object") continue;
    const e = entry as Record<string, unknown>;
    const key = typeof e.key === "string" ? e.key : null;
    const direction = e.direction === "asc" || e.direction === "desc" ? e.direction : null;
    if (!key || !direction) continue;
    out.push({ key, direction });
  }
  return out;
}

function summarisePresets(raw: unknown): PresetSummary[] {
  if (!Array.isArray(raw)) return [];
  const out: PresetSummary[] = [];
  for (const entry of raw) {
    if (!entry || typeof entry !== "object") continue;
    const p = entry as Record<string, unknown>;
    const type = typeof p.type === "string" ? p.type : null;
    const instanceId = typeof p.instanceId === "string" ? p.instanceId : null;
    if (!type || !instanceId) continue;
    const options = (p.options && typeof p.options === "object")
      ? p.options as Record<string, unknown>
      : {};
    const name = typeof options.name === "string" && options.name.length > 0
      ? options.name
      : type;
    const timeout = typeof options.timeout === "number" && Number.isFinite(options.timeout)
      ? options.timeout
      : null;
    const mediaTypes = Array.isArray(options.mediaTypes)
      ? (options.mediaTypes as unknown[]).filter((v): v is string => typeof v === "string")
      : [];
    out.push({
      instanceId,
      type,
      name,
      enabled: p.enabled === true,
      timeout,
      mediaTypes,
      useMultipleInstances: options.useMultipleInstances === true,
    });
  }
  return out;
}
Deno.serve(async (request) => {
  const cors = handleCors(request);
  if (cors) return cors;

  if (request.method !== "POST") return errorResponse(405, "Method not allowed");

  const authResult = requireAuth(request);
  if (authResult instanceof Response) return authResult;
  const { userId } = authResult;

  let body: { profileId?: unknown };
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

  const empty = {
    tmdbApiKey: null,
    tmdbAccessToken: null,
    tvdbApiKey: null,
    rpdbApiKey: null,
    animeToshoEnabled: false,
    debridioApiKey: null,
    presets: [] as PresetSummary[],
    excludedResolutions: [] as string[],
    preferredResolutions: [] as string[],
    excludedQualities: [] as string[],
    preferredQualities: [] as string[],
    sortCriteria: [] as SortEntry[],
    provisioned: false,
  };

  const baseUrl = (Deno.env.get("AIOSTREAMS_BASE_URL") ?? "").replace(/\/+$/, "");
  if (!baseUrl) return jsonResponse(200, empty);

  const client = createServiceClient();
  const ownerId = await resolveOwnerId(client, userId);

  const { data: configRow } = await client
    .from("source_cloud_configs")
    .select("aiostreams_config_id, aiostreams_config_secret_ciphertext, aiostreams_config_secret_nonce")
    .eq("user_id", ownerId)
    .eq("profile_id", profileId)
    .maybeSingle();

  const aioConfigId = configRow?.aiostreams_config_id as string | null | undefined;
  if (
    !aioConfigId ||
    typeof configRow?.aiostreams_config_secret_ciphertext !== "string" ||
    typeof configRow?.aiostreams_config_secret_nonce !== "string"
  ) {
    return jsonResponse(200, empty);
  }

  const aiostreamsPassword = await decryptAesGcm(
    configRow.aiostreams_config_secret_ciphertext,
    configRow.aiostreams_config_secret_nonce,
  );
  if (!aiostreamsPassword) return jsonResponse(200, empty);

  const url = new URL(`${baseUrl}/api/v1/user`);
  url.searchParams.set("uuid", aioConfigId);
  url.searchParams.set("password", aiostreamsPassword);
  url.searchParams.set("raw", "true");
  const fetchResponse = await fetch(url.toString(), { method: "GET" });
  if (!fetchResponse.ok) return jsonResponse(200, empty);

  const fetchJson = await fetchResponse.json() as { data?: { userData?: Record<string, unknown> } };
  const config = fetchJson.data?.userData ?? null;
  if (!config) return jsonResponse(200, empty);

  const presets = Array.isArray(config.presets) ? config.presets : [];
  const animeToshoPreset = presets.find(
    (p) => p && typeof p === "object" && (p as Record<string, unknown>).type === "animetosho",
  ) as Record<string, unknown> | undefined;
  const debridioPreset = presets.find(
    (p) => p && typeof p === "object" && (p as Record<string, unknown>).type === "debridio",
  ) as Record<string, unknown> | undefined;
  const debridioOptions = debridioPreset?.options as Record<string, unknown> | undefined;

  return jsonResponse(200, {
    tmdbApiKey: typeof config.tmdbApiKey === "string" ? config.tmdbApiKey : null,
    tmdbAccessToken: typeof config.tmdbAccessToken === "string" ? config.tmdbAccessToken : null,
    tvdbApiKey: typeof config.tvdbApiKey === "string" ? config.tvdbApiKey : null,
    rpdbApiKey: typeof config.rpdbApiKey === "string" ? config.rpdbApiKey : null,
    animeToshoEnabled: animeToshoPreset
      ? (animeToshoPreset.enabled as boolean | undefined) ?? false
      : false,
    debridioApiKey: typeof debridioOptions?.debridioApiKey === "string"
      ? debridioOptions.debridioApiKey as string
      : null,
    presets: summarisePresets(config.presets),
    excludedResolutions: stringArray(config.excludedResolutions),
    preferredResolutions: stringArray(config.preferredResolutions),
    excludedQualities: stringArray(config.excludedQualities),
    preferredQualities: stringArray(config.preferredQualities),
    sortCriteria: summariseSortCriteria(config.sortCriteria),
    provisioned: true,
  });
});
