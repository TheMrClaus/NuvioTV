import {
  CONFIG_STATUS_LABELS,
  SERVICE_LABELS,
  SUPPORTED_SERVICES,
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
 * Update the user-configurable bits of their AIOStreams config from the
 * in-app API Keys section. All body fields are optional — only the
 * provided ones get applied. Returns status-shaped response so the app
 * can refresh.
 *
 * Body: {
 *   profileId: number,
 *   tmdbApiKey?: string | null,        // null clears
 *   tmdbAccessToken?: string | null,
 *   tvdbApiKey?: string | null,
 *   rpdbApiKey?: string | null,
 *   animeToshoEnabled?: boolean,
 *   debridioApiKey?: string | null,    // null clears + disables preset
 *   presetToggles?: Record<string, boolean>,  // instanceId → enabled
 *   presetOptions?: Record<string, {           // instanceId → option overrides
 *     name?: string,
 *     timeout?: number,
 *     mediaTypes?: string[],
 *     useMultipleInstances?: boolean,
 *   }>,
 *   excludedResolutions?: string[],
 *   preferredResolutions?: string[],
 *   excludedQualities?: string[],
 *   preferredQualities?: string[],
 *   excludedLanguages?: string[],
 *   preferredLanguages?: string[],
 *   sortCriteria?: Array<{ key: string, direction: "asc"|"desc" }>,
 *   titleMatching?: { enabled?, mode?, similarityThreshold? },
 *   yearMatching?: { enabled?, tolerance?, strict? },
 *   digitalReleaseFilter?: { enabled?, tolerance? },
 *   excludedKeywords?: string[],
 *   excludedRegexPatterns?: string[],
 *   includedRegexPatterns?: string[],
 *   requiredRegexPatterns?: string[],
 *   deduplicator?: { enabled?, multiGroupBehaviour?, keys?, cached?, uncached? },
 *   addPresets?: string[],           // preset types to add from starter template
 * }
 *
 * Edge cases:
 * - No existing AIOStreams config → 400 "Connect a service first"
 * - GET/PUT failure → returns provisioning_failed in the response
 */

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

function randomInstanceId(): string {
  return Math.random().toString(16).slice(2, 5);
}

function applyTopLevel(
  config: Record<string, unknown>,
  key: string,
  value: string | null | undefined,
): void {
  if (value === undefined) return;
  if (value === null || value === "") {
    delete config[key];
  } else {
    config[key] = value;
  }
}

function applyAnimeToshoToggle(
  config: Record<string, unknown>,
  enabled: boolean | undefined,
): void {
  if (enabled === undefined) return;
  const presets = Array.isArray(config.presets) ? config.presets as Array<Record<string, unknown>> : [];
  const idx = presets.findIndex((p) => p && p.type === "animetosho");
  if (idx >= 0) {
    presets[idx] = { ...presets[idx], enabled };
  } else if (enabled) {
    presets.push({
      type: "animetosho",
      instanceId: randomInstanceId(),
      enabled: true,
      options: {
        name: "AnimeTosho",
        timeout: 7000,
        mediaTypes: ["anime"],
        useMultipleInstances: false,
      },
    });
  }
  config.presets = presets;
}

// Allowlists mirror AIOStreams' constants. Updating these requires bumping
// when AIOStreams adds new enum values upstream.
const VALID_RESOLUTIONS = new Set([
  "2160p", "1440p", "1080p", "720p", "576p", "480p", "360p", "240p", "144p", "Unknown",
]);
const VALID_QUALITIES = new Set([
  "BluRay REMUX", "BluRay", "WEB-DL", "WEBRip", "HDRip", "HC HD-Rip",
  "DVDRip", "HDTV", "CAM", "TS", "TC", "SCR", "Unknown",
]);
const VALID_LANGUAGES = new Set([
  "English", "Japanese", "Chinese", "Russian", "Arabic", "Portuguese",
  "Portuguese (Brazil)", "Spanish", "French", "German", "Italian", "Korean",
  "Hindi", "Bengali", "Punjabi", "Marathi", "Gujarati", "Tamil", "Telugu",
  "Kannada", "Malayalam", "Thai", "Vietnamese", "Indonesian", "Turkish",
  "Hebrew", "Persian", "Ukrainian", "Greek", "Lithuanian", "Latvian",
  "Estonian", "Polish", "Czech", "Slovak", "Hungarian", "Romanian",
  "Bulgarian", "Serbian", "Croatian", "Slovenian", "Dutch", "Danish",
  "Finnish", "Swedish", "Norwegian", "Malay", "Latino", "Dual Audio",
  "Dubbed", "Multi", "Original", "Unknown",
]);

const VALID_DEDUP_KEYS = new Set(["filename", "infoHash", "smartDetect"]);
const VALID_DEDUP_MODES = new Set(["single_result", "per_service", "per_addon", "disabled"]);
const VALID_DEDUP_MGB = new Set(["keep_all", "aggressive", "conservative"]);
const MAX_KEYWORD_LEN = 100;
const MAX_REGEX_LEN = 500;
const MAX_LIST_ITEMS = 200;

const VALID_MEDIA_TYPES = new Set(["movie", "series", "channel", "tv", "anime"]);
const MIN_TIMEOUT_MS = 1000;
const MAX_TIMEOUT_MS = 300_000;
const MAX_PRESET_NAME_LEN = 200;

const VALID_SORT_KEYS = new Set([
  "quality", "resolution", "language", "subtitle", "visualTag", "audioTag",
  "audioChannel", "streamType", "encode", "size", "service", "seeders",
  "private", "age", "addon", "regexPatterns", "cached", "library", "keyword",
  "streamExpressionMatched", "streamExpressionScore", "regexScore", "seadex",
  "bitrate", "releaseGroup",
]);

function applyStringArray(
  config: Record<string, unknown>,
  key: string,
  value: string[] | undefined,
  allowed: Set<string>,
): void {
  if (value === undefined) return;
  const sanitised = value.filter((v) => typeof v === "string" && allowed.has(v));
  if (sanitised.length === 0) {
    delete config[key];
  } else {
    config[key] = Array.from(new Set(sanitised));
  }
}

interface SortCriterionInput {
  key: string;
  direction: "asc" | "desc";
}

function applySortCriteria(
  config: Record<string, unknown>,
  criteria: SortCriterionInput[] | undefined,
): void {
  if (criteria === undefined) return;
  const sanitised: SortCriterionInput[] = [];
  const seen = new Set<string>();
  for (const c of criteria) {
    if (!c || typeof c !== "object") continue;
    if (!VALID_SORT_KEYS.has(c.key)) continue;
    if (c.direction !== "asc" && c.direction !== "desc") continue;
    if (seen.has(c.key)) continue;
    seen.add(c.key);
    sanitised.push({ key: c.key, direction: c.direction });
  }
  const existing = (config.sortCriteria && typeof config.sortCriteria === "object")
    ? config.sortCriteria as Record<string, unknown>
    : {};
  config.sortCriteria = { ...existing, global: sanitised };
}

interface TitleMatchingPatch {
  enabled?: boolean;
  mode?: "exact" | "contains";
  similarityThreshold?: number;
}
interface YearMatchingPatch {
  enabled?: boolean;
  tolerance?: number;
  strict?: boolean;
}
interface DigitalReleaseFilterPatch {
  enabled?: boolean;
  tolerance?: number;
}

function applyFreeFormStringArray(
  config: Record<string, unknown>,
  key: string,
  value: string[] | undefined,
  maxLen: number,
): void {
  if (value === undefined) return;
  const sanitised = value
    .filter((v): v is string => typeof v === "string")
    .map((v) => v.trim())
    .filter((v) => v.length > 0 && v.length <= maxLen)
    .slice(0, MAX_LIST_ITEMS);
  const unique = Array.from(new Set(sanitised));
  if (unique.length === 0) {
    delete config[key];
  } else {
    config[key] = unique;
  }
}

function applyDeduplicator(
  config: Record<string, unknown>,
  patch: Record<string, unknown> | undefined,
): void {
  if (!patch) return;
  const existing = (config.deduplicator && typeof config.deduplicator === "object")
    ? config.deduplicator as Record<string, unknown>
    : {};
  const next: Record<string, unknown> = { ...existing };
  if (typeof patch.enabled === "boolean") next.enabled = patch.enabled;
  if (typeof patch.multiGroupBehaviour === "string" && VALID_DEDUP_MGB.has(patch.multiGroupBehaviour)) {
    next.multiGroupBehaviour = patch.multiGroupBehaviour;
  }
  if (Array.isArray(patch.keys)) {
    next.keys = Array.from(
      new Set(
        (patch.keys as unknown[]).filter(
          (v): v is string => typeof v === "string" && VALID_DEDUP_KEYS.has(v),
        ),
      ),
    );
  }
  if (typeof patch.cached === "string" && VALID_DEDUP_MODES.has(patch.cached)) {
    next.cached = patch.cached;
  }
  if (typeof patch.uncached === "string" && VALID_DEDUP_MODES.has(patch.uncached)) {
    next.uncached = patch.uncached;
  }
  config.deduplicator = next;
}

function applyObjectPatch(
  config: Record<string, unknown>,
  key: string,
  patch: Record<string, unknown> | undefined,
): void {
  if (!patch) return;
  const existing = (config[key] && typeof config[key] === "object")
    ? config[key] as Record<string, unknown>
    : {};
  config[key] = { ...existing, ...patch };
}

async function applyAddPresets(
  config: Record<string, unknown>,
  baseUrl: string,
  types: string[] | undefined,
): Promise<void> {
  if (!types || types.length === 0) return;
  let starterPresets: Array<Record<string, unknown>> = [];
  try {
    const response = await fetch(`${baseUrl}/api/v1/templates`);
    if (response.ok) {
      const json = await response.json() as {
        data?: Array<{ metadata?: { id?: string }; config?: { presets?: unknown } }>;
      };
      const starter = (json.data ?? []).find((t) => t.metadata?.id === "builtin.debrid-starter");
      if (Array.isArray(starter?.config?.presets)) {
        starterPresets = starter!.config!.presets as Array<Record<string, unknown>>;
      }
    }
  } catch {
    // fall through with empty starter list
  }
  if (starterPresets.length === 0) return;
  const presets = Array.isArray(config.presets) ? config.presets as Array<Record<string, unknown>> : [];
  const existingTypes = new Set(
    presets
      .map((p) => (p && typeof p === "object" ? (p as Record<string, unknown>).type : null))
      .filter((t): t is string => typeof t === "string"),
  );
  let added = false;
  for (const type of types) {
    if (typeof type !== "string" || existingTypes.has(type)) continue;
    const template = starterPresets.find((p) => p && typeof p === "object" && p.type === type);
    if (!template) continue;
    presets.push({
      ...template,
      instanceId: Math.random().toString(16).slice(2, 5) + Date.now().toString(16).slice(-3),
      enabled: true,
    });
    existingTypes.add(type);
    added = true;
  }
  if (added) config.presets = presets;
}

interface PresetOptionOverride {
  name?: string;
  timeout?: number;
  mediaTypes?: string[];
  useMultipleInstances?: boolean;
}

function applyPresetOptions(
  config: Record<string, unknown>,
  overrides: Record<string, PresetOptionOverride> | undefined,
): void {
  if (!overrides) return;
  const presets = Array.isArray(config.presets) ? config.presets as Array<Record<string, unknown>> : [];
  let changed = false;
  for (let i = 0; i < presets.length; i++) {
    const preset = presets[i];
    if (!preset || typeof preset !== "object") continue;
    const instanceId = typeof preset.instanceId === "string" ? preset.instanceId : null;
    if (!instanceId || !(instanceId in overrides)) continue;
    const patch = overrides[instanceId];
    const existingOptions = (preset.options && typeof preset.options === "object")
      ? preset.options as Record<string, unknown>
      : {};
    const nextOptions: Record<string, unknown> = { ...existingOptions };
    if (typeof patch.name === "string") {
      const trimmed = patch.name.trim().slice(0, MAX_PRESET_NAME_LEN);
      if (trimmed.length > 0) nextOptions.name = trimmed;
    }
    if (typeof patch.timeout === "number" && Number.isFinite(patch.timeout)) {
      const t = Math.max(MIN_TIMEOUT_MS, Math.min(MAX_TIMEOUT_MS, Math.round(patch.timeout)));
      nextOptions.timeout = t;
    }
    if (Array.isArray(patch.mediaTypes)) {
      nextOptions.mediaTypes = Array.from(
        new Set(
          patch.mediaTypes.filter((v): v is string => typeof v === "string" && VALID_MEDIA_TYPES.has(v)),
        ),
      );
    }
    if (typeof patch.useMultipleInstances === "boolean") {
      nextOptions.useMultipleInstances = patch.useMultipleInstances;
    }
    presets[i] = { ...preset, options: nextOptions };
    changed = true;
  }
  if (changed) config.presets = presets;
}

function applyPresetToggles(
  config: Record<string, unknown>,
  toggles: Record<string, boolean> | undefined,
): void {
  if (!toggles) return;
  const presets = Array.isArray(config.presets) ? config.presets as Array<Record<string, unknown>> : [];
  let changed = false;
  for (let i = 0; i < presets.length; i++) {
    const preset = presets[i];
    if (!preset || typeof preset !== "object") continue;
    const instanceId = typeof preset.instanceId === "string" ? preset.instanceId : null;
    if (!instanceId) continue;
    if (instanceId in toggles) {
      presets[i] = { ...preset, enabled: toggles[instanceId] };
      changed = true;
    }
  }
  if (changed) config.presets = presets;
}

function applyDebridioKey(
  config: Record<string, unknown>,
  apiKey: string | null | undefined,
  servicesList: Array<{ id: string }>,
): void {
  if (apiKey === undefined) return;
  const presets = Array.isArray(config.presets) ? config.presets as Array<Record<string, unknown>> : [];
  const idx = presets.findIndex((p) => p && p.type === "debridio");
  if (apiKey === null || apiKey === "") {
    // Clear key, disable preset (don't delete — preserves user toggle state).
    if (idx >= 0) {
      const existing = presets[idx];
      const opts = (existing.options as Record<string, unknown> | undefined) ?? {};
      presets[idx] = {
        ...existing,
        enabled: false,
        options: { ...opts, debridioApiKey: "" },
      };
    }
  } else if (idx >= 0) {
    const existing = presets[idx];
    const opts = (existing.options as Record<string, unknown> | undefined) ?? {};
    presets[idx] = {
      ...existing,
      enabled: true,
      options: { ...opts, debridioApiKey: apiKey },
    };
  } else {
    presets.push({
      type: "debridio",
      instanceId: randomInstanceId(),
      enabled: true,
      options: {
        name: "Debridio",
        timeout: 7000,
        resources: ["stream"],
        services: servicesList.map((s) => s.id),
        mediaTypes: [],
        useMultipleInstances: false,
        debridioApiKey: apiKey,
      },
    });
  }
  config.presets = presets;
}

Deno.serve(async (request) => {
  const cors = handleCors(request);
  if (cors) return cors;

  if (request.method !== "POST") return errorResponse(405, "Method not allowed");

  const authResult = requireAuth(request);
  if (authResult instanceof Response) return authResult;
  const { userId } = authResult;

  let body: {
    profileId?: unknown;
    tmdbApiKey?: unknown;
    tmdbAccessToken?: unknown;
    tvdbApiKey?: unknown;
    rpdbApiKey?: unknown;
    animeToshoEnabled?: unknown;
    debridioApiKey?: unknown;
    presetToggles?: unknown;
    presetOptions?: unknown;
    excludedResolutions?: unknown;
    preferredResolutions?: unknown;
    excludedQualities?: unknown;
    preferredQualities?: unknown;
    excludedLanguages?: unknown;
    preferredLanguages?: unknown;
    sortCriteria?: unknown;
    titleMatching?: unknown;
    yearMatching?: unknown;
    digitalReleaseFilter?: unknown;
    excludedKeywords?: unknown;
    excludedRegexPatterns?: unknown;
    includedRegexPatterns?: unknown;
    requiredRegexPatterns?: unknown;
    deduplicator?: unknown;
    addPresets?: unknown;
  };
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

  // Coerce body fields with explicit null vs undefined semantics:
  // - missing field → undefined → no-op
  // - null → clear / disable
  // - string/boolean → apply value
  const tmdbApiKey = "tmdbApiKey" in body
    ? (body.tmdbApiKey === null ? null : typeof body.tmdbApiKey === "string" ? body.tmdbApiKey : undefined)
    : undefined;
  const tmdbAccessToken = "tmdbAccessToken" in body
    ? (body.tmdbAccessToken === null ? null : typeof body.tmdbAccessToken === "string" ? body.tmdbAccessToken : undefined)
    : undefined;
  const tvdbApiKey = "tvdbApiKey" in body
    ? (body.tvdbApiKey === null ? null : typeof body.tvdbApiKey === "string" ? body.tvdbApiKey : undefined)
    : undefined;
  const rpdbApiKey = "rpdbApiKey" in body
    ? (body.rpdbApiKey === null ? null : typeof body.rpdbApiKey === "string" ? body.rpdbApiKey : undefined)
    : undefined;
  const animeToshoEnabled = typeof body.animeToshoEnabled === "boolean" ? body.animeToshoEnabled : undefined;
  const debridioApiKey = "debridioApiKey" in body
    ? (body.debridioApiKey === null ? null : typeof body.debridioApiKey === "string" ? body.debridioApiKey : undefined)
    : undefined;

  let presetToggles: Record<string, boolean> | undefined;
  if (body.presetToggles && typeof body.presetToggles === "object" && !Array.isArray(body.presetToggles)) {
    const sanitised: Record<string, boolean> = {};
    for (const [k, v] of Object.entries(body.presetToggles as Record<string, unknown>)) {
      if (typeof v === "boolean" && typeof k === "string" && k.length > 0) sanitised[k] = v;
    }
    if (Object.keys(sanitised).length > 0) presetToggles = sanitised;
  }

  let presetOptions: Record<string, PresetOptionOverride> | undefined;
  if (body.presetOptions && typeof body.presetOptions === "object" && !Array.isArray(body.presetOptions)) {
    const sanitised: Record<string, PresetOptionOverride> = {};
    for (const [k, raw] of Object.entries(body.presetOptions as Record<string, unknown>)) {
      if (typeof k !== "string" || k.length === 0) continue;
      if (!raw || typeof raw !== "object") continue;
      const patch = raw as Record<string, unknown>;
      const override: PresetOptionOverride = {};
      if (typeof patch.name === "string") override.name = patch.name;
      if (typeof patch.timeout === "number") override.timeout = patch.timeout;
      if (Array.isArray(patch.mediaTypes)) {
        override.mediaTypes = (patch.mediaTypes as unknown[]).filter(
          (v): v is string => typeof v === "string",
        );
      }
      if (typeof patch.useMultipleInstances === "boolean") {
        override.useMultipleInstances = patch.useMultipleInstances;
      }
      if (Object.keys(override).length > 0) sanitised[k] = override;
    }
    if (Object.keys(sanitised).length > 0) presetOptions = sanitised;
  }

  const readStrArr = (raw: unknown): string[] | undefined =>
    Array.isArray(raw) ? raw.filter((v): v is string => typeof v === "string") : undefined;
  const excludedResolutions = "excludedResolutions" in body ? readStrArr(body.excludedResolutions) : undefined;
  const preferredResolutions = "preferredResolutions" in body ? readStrArr(body.preferredResolutions) : undefined;
  const excludedQualities = "excludedQualities" in body ? readStrArr(body.excludedQualities) : undefined;
  const preferredQualities = "preferredQualities" in body ? readStrArr(body.preferredQualities) : undefined;
  const excludedLanguages = "excludedLanguages" in body ? readStrArr(body.excludedLanguages) : undefined;
  const preferredLanguages = "preferredLanguages" in body ? readStrArr(body.preferredLanguages) : undefined;

  function readTitleMatching(raw: unknown): Record<string, unknown> | undefined {
    if (!raw || typeof raw !== "object") return undefined;
    const o = raw as Record<string, unknown>;
    const out: Record<string, unknown> = {};
    if (typeof o.enabled === "boolean") out.enabled = o.enabled;
    if (o.mode === "exact" || o.mode === "contains") out.mode = o.mode;
    if (typeof o.similarityThreshold === "number" && Number.isFinite(o.similarityThreshold)) {
      out.similarityThreshold = Math.max(0, Math.min(1, o.similarityThreshold));
    }
    return Object.keys(out).length > 0 ? out : undefined;
  }
  function readYearMatching(raw: unknown): Record<string, unknown> | undefined {
    if (!raw || typeof raw !== "object") return undefined;
    const o = raw as Record<string, unknown>;
    const out: Record<string, unknown> = {};
    if (typeof o.enabled === "boolean") out.enabled = o.enabled;
    if (typeof o.tolerance === "number" && Number.isFinite(o.tolerance)) {
      out.tolerance = Math.max(0, Math.min(100, Math.round(o.tolerance)));
    }
    if (typeof o.strict === "boolean") out.strict = o.strict;
    return Object.keys(out).length > 0 ? out : undefined;
  }
  function readDigitalReleaseFilter(raw: unknown): Record<string, unknown> | undefined {
    if (!raw || typeof raw !== "object") return undefined;
    const o = raw as Record<string, unknown>;
    const out: Record<string, unknown> = {};
    if (typeof o.enabled === "boolean") out.enabled = o.enabled;
    if (typeof o.tolerance === "number" && Number.isFinite(o.tolerance)) {
      out.tolerance = Math.max(0, Math.min(365, Math.round(o.tolerance)));
    }
    return Object.keys(out).length > 0 ? out : undefined;
  }
  const titleMatchingPatch = "titleMatching" in body ? readTitleMatching(body.titleMatching) : undefined;
  const yearMatchingPatch = "yearMatching" in body ? readYearMatching(body.yearMatching) : undefined;
  const digitalReleaseFilterPatch = "digitalReleaseFilter" in body
    ? readDigitalReleaseFilter(body.digitalReleaseFilter)
    : undefined;

  let addPresets: string[] | undefined;
  if ("addPresets" in body && Array.isArray(body.addPresets)) {
    addPresets = (body.addPresets as unknown[]).filter((v): v is string => typeof v === "string");
    if (addPresets.length === 0) addPresets = undefined;
  }

  const excludedKeywords = "excludedKeywords" in body ? readStrArr(body.excludedKeywords) : undefined;
  const excludedRegexPatterns = "excludedRegexPatterns" in body ? readStrArr(body.excludedRegexPatterns) : undefined;
  const includedRegexPatterns = "includedRegexPatterns" in body ? readStrArr(body.includedRegexPatterns) : undefined;
  const requiredRegexPatterns = "requiredRegexPatterns" in body ? readStrArr(body.requiredRegexPatterns) : undefined;

  const deduplicatorPatch = "deduplicator" in body && body.deduplicator && typeof body.deduplicator === "object" && !Array.isArray(body.deduplicator)
    ? body.deduplicator as Record<string, unknown>
    : undefined;

  let sortCriteria: SortCriterionInput[] | undefined;
  if ("sortCriteria" in body && Array.isArray(body.sortCriteria)) {
    sortCriteria = [];
    for (const entry of body.sortCriteria as unknown[]) {
      if (!entry || typeof entry !== "object") continue;
      const e = entry as Record<string, unknown>;
      if (typeof e.key !== "string") continue;
      if (e.direction !== "asc" && e.direction !== "desc") continue;
      sortCriteria.push({ key: e.key, direction: e.direction });
    }
  }

  const baseUrl = (Deno.env.get("AIOSTREAMS_BASE_URL") ?? "").replace(/\/+$/, "");
  const addonPassword = Deno.env.get("AIOSTREAMS_ADDON_PASSWORD") ?? "";
  if (!baseUrl) return errorResponse(500, "AIOSTREAMS_BASE_URL not configured");

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
    return errorResponse(400, "No AIOStreams config yet — connect a service first");
  }

  const aiostreamsPassword = await decryptAesGcm(
    configRow.aiostreams_config_secret_ciphertext,
    configRow.aiostreams_config_secret_nonce,
  );
  if (!aiostreamsPassword) return errorResponse(500, "Failed to decrypt AIOStreams password");

  // Fetch current config so we mutate-in-place and don't drop other fields.
  const fetchUrl = new URL(`${baseUrl}/api/v1/user`);
  fetchUrl.searchParams.set("uuid", aioConfigId);
  fetchUrl.searchParams.set("password", aiostreamsPassword);
  fetchUrl.searchParams.set("raw", "true");
  const fetchResponse = await fetch(fetchUrl.toString(), { method: "GET" });
  if (!fetchResponse.ok) {
    return errorResponse(502, `Failed to fetch existing AIOStreams config: HTTP ${fetchResponse.status}`);
  }
  const fetchJson = await fetchResponse.json() as { data?: { userData?: Record<string, unknown> } };
  const config = fetchJson.data?.userData;
  if (!config) return errorResponse(502, "AIOStreams returned empty config");

  applyTopLevel(config, "tmdbApiKey", tmdbApiKey);
  applyTopLevel(config, "tmdbAccessToken", tmdbAccessToken);
  applyTopLevel(config, "tvdbApiKey", tvdbApiKey);
  applyTopLevel(config, "rpdbApiKey", rpdbApiKey);
  applyAnimeToshoToggle(config, animeToshoEnabled);
  const services = Array.isArray(config.services)
    ? (config.services as Array<{ id?: string }>).filter((s) => typeof s.id === "string") as Array<{ id: string }>
    : [];
  applyDebridioKey(config, debridioApiKey, services);
  await applyAddPresets(config, baseUrl, addPresets);
  applyPresetToggles(config, presetToggles);
  applyPresetOptions(config, presetOptions);
  applyStringArray(config, "excludedResolutions", excludedResolutions, VALID_RESOLUTIONS);
  applyStringArray(config, "preferredResolutions", preferredResolutions, VALID_RESOLUTIONS);
  applyStringArray(config, "excludedQualities", excludedQualities, VALID_QUALITIES);
  applyStringArray(config, "preferredQualities", preferredQualities, VALID_QUALITIES);
  applyStringArray(config, "excludedLanguages", excludedLanguages, VALID_LANGUAGES);
  applyStringArray(config, "preferredLanguages", preferredLanguages, VALID_LANGUAGES);
  applyObjectPatch(config, "titleMatching", titleMatchingPatch);
  applyObjectPatch(config, "yearMatching", yearMatchingPatch);
  applyObjectPatch(config, "digitalReleaseFilter", digitalReleaseFilterPatch);
  applyFreeFormStringArray(config, "excludedKeywords", excludedKeywords, MAX_KEYWORD_LEN);
  applyFreeFormStringArray(config, "excludedRegexPatterns", excludedRegexPatterns, MAX_REGEX_LEN);
  applyFreeFormStringArray(config, "includedRegexPatterns", includedRegexPatterns, MAX_REGEX_LEN);
  applyFreeFormStringArray(config, "requiredRegexPatterns", requiredRegexPatterns, MAX_REGEX_LEN);
  applyDeduplicator(config, deduplicatorPatch);
  applySortCriteria(config, sortCriteria);

  applyTmdbPolicy(config);
  bumpTorrentioTimeout(config);
  if (addonPassword) config.addonPassword = addonPassword;

  const putResponse = await fetch(`${baseUrl}/api/v1/user`, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ uuid: aioConfigId, password: aiostreamsPassword, config }),
  });

  let configStatus = "ready";
  let provisioningError: string | null = null;
  if (!putResponse.ok) {
    const text = await putResponse.text().catch(() => "");
    console.warn(`update-config PUT failed: ${putResponse.status} ${text.slice(0, 500)}`);
    provisioningError = `HTTP ${putResponse.status}: ${text.slice(0, 300)}`;
    configStatus = "provisioning_failed";
  }

  await client
    .from("source_cloud_configs")
    .update({
      config_status: configStatus,
      last_validated_at: new Date().toISOString(),
    })
    .eq("user_id", ownerId)
    .eq("profile_id", profileId);

  const { data: refreshedTokens } = await client
    .from("source_cloud_service_tokens")
    .select("service, status, label")
    .eq("user_id", ownerId)
    .eq("profile_id", profileId);

  const statusInfo = CONFIG_STATUS_LABELS[configStatus] ?? CONFIG_STATUS_LABELS.unknown;
  const servicesPayload = SUPPORTED_SERVICES.map((s: SupportedService) => {
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
