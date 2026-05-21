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

// --- Kids overlay -----------------------------------------------------------
//
// Direct port of core-data/.../AioMetadataKidsConfig.kt. Builds a Kids-tuned
// AIOMetadata config by mutating each TMDB-sourced discover catalog's
// `params` (and `formState`) to clamp certification + restrict/exclude
// genres according to the profile's maxAgeRating tier.
//
// Non-TMDB catalogs (Trakt recs, etc.) are left alone — the TV's runtime
// KidsContentFilter is the safety net for those.
//
// Mirrors the Kotlin enum AgeRatingTier in core-domain. "None" is the
// out-of-band "no rating filter" choice surfaced by the panel's Display
// form; treated equivalently to passing null for tier-resolution purposes.

export type AgeRatingTier = "G" | "PG" | "PG-13" | "TV-14" | "R" | "NC-17";

const CERT_COUNTRY = "US";

// TMDB movie genre IDs.
const MOVIE_HORROR = "27";
const MOVIE_THRILLER = "53";
const MOVIE_CRIME = "80";
const MOVIE_WAR = "10752";
const MOVIE_WESTERN = "37";

// TMDB tv genre IDs.
const TV_ACTION_ADVENTURE = "10759";
const TV_ANIMATION = "16";
const TV_CRIME = "80";
const TV_MYSTERY = "9648";
const TV_WAR_POLITICS = "10768";
const TV_WESTERN = "37";
const TV_FAMILY = "10751";
const TV_KIDS = "10762";

interface TierFilters {
  movieCertificationLte: string | null;
  movieWithoutGenres: string[];
  tvWithGenres: string[] | null;
  tvWithoutGenres: string[];
}

export function filtersForTier(tier: AgeRatingTier | null): TierFilters {
  // Strictest two tiers (G/PG): force TV to family/animation/kids and
  // exclude a wide swath of mature-leaning genres on both axes.
  // Middle tiers (PG-13/TV-14): keep the cert ladder for movies, drop
  // the with-list for TV so non-cartoon kid-appropriate shows can come
  // through, but still exclude the obviously adult genres.
  // Top tiers (R/NC-17): we don't really expect Kids profiles to pick
  // these — fall through to no clamps.
  // null tier: conservative defaults so a miswired Kids profile never
  // accidentally serves unfiltered catalogs.
  if (tier === "G" || tier === "PG") {
    return {
      movieCertificationLte: tier === "G" ? "G" : "PG",
      movieWithoutGenres: [MOVIE_HORROR, MOVIE_THRILLER, MOVIE_CRIME, MOVIE_WAR, MOVIE_WESTERN],
      tvWithGenres: [TV_FAMILY, TV_ANIMATION, TV_KIDS],
      tvWithoutGenres: [TV_ACTION_ADVENTURE, TV_CRIME, TV_MYSTERY, TV_WAR_POLITICS, TV_WESTERN],
    };
  }
  if (tier === "PG-13") {
    return {
      movieCertificationLte: "PG-13",
      movieWithoutGenres: [MOVIE_HORROR, MOVIE_THRILLER, MOVIE_WAR],
      tvWithGenres: null,
      tvWithoutGenres: [TV_CRIME, TV_WAR_POLITICS],
    };
  }
  if (tier === "TV-14") {
    // TMDB cert ladder for movies stops at PG-13 below R; map TV-14 to
    // PG-13 to stay conservative for movies.
    return {
      movieCertificationLte: "PG-13",
      movieWithoutGenres: [MOVIE_HORROR, MOVIE_WAR],
      tvWithGenres: null,
      tvWithoutGenres: [TV_WAR_POLITICS],
    };
  }
  if (tier === "R" || tier === "NC-17") {
    return {
      movieCertificationLte: null,
      movieWithoutGenres: [],
      tvWithGenres: null,
      tvWithoutGenres: [],
    };
  }
  // null
  return {
    movieCertificationLte: "PG",
    movieWithoutGenres: [MOVIE_HORROR, MOVIE_THRILLER, MOVIE_CRIME, MOVIE_WAR],
    tvWithGenres: [TV_FAMILY, TV_ANIMATION, TV_KIDS],
    tvWithoutGenres: [TV_ACTION_ADVENTURE, TV_CRIME, TV_WAR_POLITICS],
  };
}

function appendCommaSeparated(
  params: Record<string, unknown>,
  key: string,
  valuesToAdd: string,
): void {
  const current = typeof params[key] === "string" ? params[key] as string : "";
  const combined = current.length === 0 ? valuesToAdd : `${current},${valuesToAdd}`;
  const deduped = Array.from(
    new Set(
      combined
        .split(",")
        .map((v) => v.trim())
        .filter((v) => v.length > 0),
    ),
  );
  params[key] = deduped.join(",");
}

function applyKidsFiltersToCatalog(
  catalog: Record<string, unknown>,
  filters: TierFilters,
): Record<string, unknown> {
  const metadata = catalog.metadata;
  if (!metadata || typeof metadata !== "object" || Array.isArray(metadata)) return catalog;
  const meta = metadata as Record<string, unknown>;
  const discover = meta.discover;
  if (!discover || typeof discover !== "object" || Array.isArray(discover)) return catalog;
  const dsc = discover as Record<string, unknown>;
  const mediaType = typeof dsc.mediaType === "string" ? dsc.mediaType : null;
  const source = typeof dsc.source === "string" ? dsc.source : null;

  // Only TMDB-sourced discover catalogs carry params we know how to tune.
  if (source !== "tmdb") return catalog;

  const params: Record<string, unknown> =
    dsc.params && typeof dsc.params === "object" && !Array.isArray(dsc.params)
      ? { ...(dsc.params as Record<string, unknown>) }
      : {};
  params.include_adult = false;

  if (mediaType === "movie") {
    if (filters.movieCertificationLte !== null) {
      params.certification_country = CERT_COUNTRY;
      params["certification.lte"] = filters.movieCertificationLte;
    }
    if (filters.movieWithoutGenres.length > 0) {
      appendCommaSeparated(params, "without_genres", filters.movieWithoutGenres.join(","));
    }
  } else if (mediaType === "tv") {
    if (filters.tvWithGenres !== null) {
      // TMDB joins with_genres values with `|` for OR semantics. Replace
      // any existing list (rather than appending) so catalogs the user
      // tuned for, say, an Action niche don't conflict — for a Kids
      // profile we want family/animation/kids regardless of niche.
      params.with_genres = filters.tvWithGenres.join("|");
    }
    if (filters.tvWithoutGenres.length > 0) {
      appendCommaSeparated(params, "without_genres", filters.tvWithoutGenres.join(","));
    }
  }

  const formState =
    dsc.formState && typeof dsc.formState === "object" && !Array.isArray(dsc.formState)
      ? { ...(dsc.formState as Record<string, unknown>) }
      : null;
  if (formState) {
    formState.includeAdult = false;
    if (mediaType === "movie" && filters.movieCertificationLte !== null) {
      formState.certificationCountry = CERT_COUNTRY;
      formState.maxCertification = filters.movieCertificationLte;
    }
  }

  const newDiscover: Record<string, unknown> = {
    ...dsc,
    params,
  };
  if (formState) newDiscover.formState = formState;

  return {
    ...catalog,
    metadata: {
      ...meta,
      discover: newDiscover,
    },
  };
}

/**
 * Apply the Kids overlay across an inner config's catalogs list. Returns
 * the catalogs array; caller is responsible for swapping it onto the
 * config object.
 */
export function applyKidsOverlayToCatalogs(
  catalogs: Array<Record<string, unknown>>,
  tier: AgeRatingTier | null,
): Array<Record<string, unknown>> {
  const filters = filtersForTier(tier);
  return catalogs.map((c) => applyKidsFiltersToCatalog(c, filters));
}

/**
 * The TMDB cert label format used both by `settings.ageRating` on the
 * inner config and by the panel's Display form's ageRating select.
 * "None" means "no filter" — the Display form's choice for non-Kids
 * profiles; on a Kids profile we map this to a null tier (still applies
 * default conservative overlay per filtersForTier).
 */
export function ageRatingFromString(raw: unknown): AgeRatingTier | null {
  if (raw === "G" || raw === "PG" || raw === "PG-13" || raw === "TV-14" || raw === "R" || raw === "NC-17") {
    return raw;
  }
  return null;
}

// --- Profile lookup ---------------------------------------------------------

export interface ProfileKidsState {
  isKids: boolean;
  maxAgeRating: AgeRatingTier | null;
}

/**
 * Read the per-profile Kids flag + maxAgeRating from public.profiles.
 * Used by the edge functions to decide whether settings.ageRating changes
 * should trigger the overlay re-application path.
 */
export async function fetchProfileKidsState(
  client: import("https://esm.sh/@supabase/supabase-js@2.49.8").SupabaseClient,
  ownerId: string,
  profileId: number,
): Promise<ProfileKidsState> {
  const { data } = await client
    .from("profiles")
    .select("is_kids, max_age_rating")
    .eq("user_id", ownerId)
    .eq("profile_index", profileId)
    .maybeSingle();
  if (!data) return { isKids: false, maxAgeRating: null };
  return {
    isKids: Boolean(data.is_kids),
    maxAgeRating: ageRatingFromString(data.max_age_rating),
  };
}

/**
 * Update public.profiles.max_age_rating for a given profile. No-op for
 * the primary profile (id=1) since Main isn't a Kids profile. Pass null
 * to clear the column (e.g. when leaving Kids mode).
 */
export async function updateProfileMaxAgeRating(
  client: import("https://esm.sh/@supabase/supabase-js@2.49.8").SupabaseClient,
  ownerId: string,
  profileId: number,
  tier: AgeRatingTier | null,
): Promise<void> {
  if (profileId === 1) return;
  await client
    .from("profiles")
    .update({ max_age_rating: tier })
    .eq("user_id", ownerId)
    .eq("profile_index", profileId);
}

// --- Reserved root keys (used by extractSettings) ---------------------------

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
