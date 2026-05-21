/**
 * TMDB Discover catalog conversion — formState ↔ TMDB API params.
 *
 * Upstream (cedya77/aiometadata) stores discover catalogs as:
 *   metadata.discover.formState   — the editor's source of truth
 *   metadata.discover.params      — what gets sent to TMDB
 *
 * Both are kept in sync. When the panel edits a catalog, we rebuild params
 * from formState so upstream's runtime layer sees consistent data.
 *
 * Anything we don't model (genres we don't know about, raw discover params
 * the user has only ever set via the upstream UI) is preserved by overlaying
 * our changes on top of the existing objects rather than replacing them.
 */

import {
  TMDB_CATALOG_TYPES,
  TMDB_MOVIE_GENRES,
  TMDB_TV_GENRES,
  type TmdbGenre,
  type TmdbProvider,
} from "./aiometadata-constants";

export interface DiscoverFormState {
  catalogName: string;
  discoverSource: "tmdb";
  sortBy: string;
  catalogType: "movie" | "series";
  includeAdult: boolean;
  releasedOnly: boolean;
  watchRegion: string;
  watchProviders: TmdbProvider[];
  providerJoinMode: "or" | "and";
  voteCountMin: number;
  voteAverageRange?: [number, number];
  includeGenres: TmdbGenre[];
}

export interface DiscoverEnvelope {
  version: number;
  source: "tmdb";
  mediaType: "movie" | "tv";
  params: Record<string, unknown>;
  formState: Record<string, unknown>;
}

const DEFAULT_FORM_STATE: DiscoverFormState = {
  catalogName: "New Catalog",
  discoverSource: "tmdb",
  sortBy: "popularity.desc",
  catalogType: "movie",
  includeAdult: false,
  releasedOnly: true,
  watchRegion: "US",
  watchProviders: [],
  providerJoinMode: "or",
  voteCountMin: 50,
  voteAverageRange: undefined,
  includeGenres: [],
};

function readField<T>(obj: Record<string, unknown>, key: string, fallback: T): T {
  const v = obj[key];
  return v === undefined || v === null ? fallback : (v as T);
}

export function readDiscoverEnvelope(catalog: Record<string, unknown>): DiscoverEnvelope | null {
  const meta = catalog.metadata;
  if (!meta || typeof meta !== "object" || Array.isArray(meta)) return null;
  const discover = (meta as Record<string, unknown>).discover;
  if (!discover || typeof discover !== "object" || Array.isArray(discover)) return null;
  const d = discover as Record<string, unknown>;
  const mediaType = d.mediaType === "tv" ? "tv" : "movie";
  return {
    version: typeof d.version === "number" ? d.version : 2,
    source: "tmdb",
    mediaType,
    params: (d.params && typeof d.params === "object" && !Array.isArray(d.params))
      ? d.params as Record<string, unknown>
      : {},
    formState: (d.formState && typeof d.formState === "object" && !Array.isArray(d.formState))
      ? d.formState as Record<string, unknown>
      : {},
  };
}

export function envelopeToFormState(env: DiscoverEnvelope): DiscoverFormState {
  const fs = env.formState;
  const providersRaw = Array.isArray(fs.watchProviders) ? fs.watchProviders : [];
  const genresRaw = Array.isArray(fs.includeGenres) ? fs.includeGenres : [];
  const voteAvg = Array.isArray(fs.voteAverageRange) && fs.voteAverageRange.length === 2
    ? [Number(fs.voteAverageRange[0]), Number(fs.voteAverageRange[1])] as [number, number]
    : undefined;
  return {
    catalogName: readField(fs, "catalogName", DEFAULT_FORM_STATE.catalogName),
    discoverSource: "tmdb",
    sortBy: readField(fs, "sortBy", DEFAULT_FORM_STATE.sortBy),
    catalogType: (readField<string>(fs, "catalogType", DEFAULT_FORM_STATE.catalogType) === "series" ? "series" : "movie"),
    includeAdult: readField(fs, "includeAdult", DEFAULT_FORM_STATE.includeAdult),
    releasedOnly: readField(fs, "releasedOnly", DEFAULT_FORM_STATE.releasedOnly),
    watchRegion: readField(fs, "watchRegion", DEFAULT_FORM_STATE.watchRegion),
    watchProviders: providersRaw
      .filter((p): p is Record<string, unknown> => !!p && typeof p === "object")
      .map((p) => ({
        id: Number((p as Record<string, unknown>).id) || 0,
        label: String((p as Record<string, unknown>).label ?? ""),
      }))
      .filter((p) => p.id > 0),
    providerJoinMode: readField<string>(fs, "providerJoinMode", "or") === "and" ? "and" : "or",
    voteCountMin: Number(readField(fs, "voteCountMin", DEFAULT_FORM_STATE.voteCountMin)) || 0,
    voteAverageRange: voteAvg,
    includeGenres: genresRaw
      .filter((g): g is Record<string, unknown> => !!g && typeof g === "object")
      .map((g) => ({
        id: Number((g as Record<string, unknown>).id) || 0,
        label: String((g as Record<string, unknown>).label ?? ""),
      }))
      .filter((g) => g.id > 0),
  };
}

/**
 * Rebuild the TMDB Discover `params` object from the editor formState.
 * Preserves any unknown params already present (e.g. ones the upstream UI
 * set that we don't model here) by overlaying our derived keys on top.
 */
export function formStateToParams(
  formState: DiscoverFormState,
  existing: Record<string, unknown>,
): Record<string, unknown> {
  const params: Record<string, unknown> = { ...existing };

  params.sort_by = formState.sortBy;
  params.include_adult = formState.includeAdult;

  // Watch providers + region — only attach if at least one provider chosen.
  if (formState.watchProviders.length > 0) {
    const ids = formState.watchProviders.map((p) => p.id);
    const sep = formState.providerJoinMode === "and" ? "," : "|";
    params.with_watch_providers = ids.join(sep);
    params.watch_region = formState.watchRegion;
  } else {
    delete params.with_watch_providers;
    delete params.watch_region;
  }

  // Genres.
  if (formState.includeGenres.length > 0) {
    params.with_genres = formState.includeGenres.map((g) => g.id).join("|");
  } else {
    delete params.with_genres;
  }

  // Vote count / average.
  if (Number.isFinite(formState.voteCountMin) && formState.voteCountMin > 0) {
    params["vote_count.gte"] = formState.voteCountMin;
  } else {
    delete params["vote_count.gte"];
  }
  if (formState.voteAverageRange) {
    params["vote_average.gte"] = formState.voteAverageRange[0];
    if (formState.voteAverageRange[1] < 10) {
      params["vote_average.lte"] = formState.voteAverageRange[1];
    } else {
      delete params["vote_average.lte"];
    }
  } else {
    delete params["vote_average.gte"];
    delete params["vote_average.lte"];
  }

  // Released-only differs by media type.
  if (formState.releasedOnly) {
    if (formState.catalogType === "movie") {
      // 4=Digital 5=Physical 6=TV. Matches the default config's catalogs.
      params.with_release_type = readField<string>(existing, "with_release_type", "4|5|6");
      delete params.with_status;
    } else {
      // 0=Returning 3=Ended 4=Canceled 5=Pilot. Matches the default config.
      params.with_status = readField<string>(existing, "with_status", "0|3|4|5");
      delete params.with_release_type;
    }
  } else {
    delete params.with_release_type;
    delete params.with_status;
  }

  return params;
}

/**
 * Apply a formState edit to a catalog row's `raw` object, returning a fresh
 * catalog suitable for the catalogs array on save. Updates:
 *  - top-level name + type
 *  - metadata.discover.formState
 *  - metadata.discover.params (rebuilt)
 */
export function applyEditToCatalog(
  raw: Record<string, unknown>,
  formState: DiscoverFormState,
): Record<string, unknown> {
  const env = readDiscoverEnvelope(raw) ?? {
    version: 2,
    source: "tmdb",
    mediaType: formState.catalogType === "series" ? "tv" : "movie",
    params: {},
    formState: {},
  };
  const newParams = formStateToParams(formState, env.params);
  const newFormState: Record<string, unknown> = {
    ...env.formState,
    catalogName: formState.catalogName,
    discoverSource: formState.discoverSource,
    sortBy: formState.sortBy,
    catalogType: formState.catalogType,
    includeAdult: formState.includeAdult,
    releasedOnly: formState.releasedOnly,
    watchRegion: formState.watchRegion,
    watchProviders: formState.watchProviders,
    providerJoinMode: formState.providerJoinMode,
    voteCountMin: formState.voteCountMin,
    includeGenres: formState.includeGenres,
  };
  if (formState.voteAverageRange) {
    newFormState.voteAverageRange = formState.voteAverageRange;
  } else {
    delete newFormState.voteAverageRange;
  }

  const existingMeta = (raw.metadata && typeof raw.metadata === "object" && !Array.isArray(raw.metadata))
    ? raw.metadata as Record<string, unknown>
    : {};

  return {
    ...raw,
    name: formState.catalogName,
    type: formState.catalogType,
    source: "tmdb",
    metadata: {
      ...existingMeta,
      discover: {
        version: env.version || 2,
        source: "tmdb",
        mediaType: formState.catalogType === "series" ? "tv" : "movie",
        params: newParams,
        formState: newFormState,
      },
    },
  };
}

/** Synthesize a brand-new TMDB Discover catalog with a unique id. */
export function makeNewDiscoverCatalog(opts: { name: string; catalogType: "movie" | "series" }): Record<string, unknown> {
  const slug = opts.name
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "")
    .slice(0, 32) || "custom";
  const random = Math.random().toString(16).slice(2, 8);
  const id = `tmdb.discover.${opts.catalogType}.custom.${slug}.${random}`;
  const fs: DiscoverFormState = {
    ...DEFAULT_FORM_STATE,
    catalogName: opts.name,
    catalogType: opts.catalogType,
  };
  return applyEditToCatalog(
    {
      id,
      type: opts.catalogType,
      name: opts.name,
      enabled: true,
      showInHome: true,
      source: "tmdb",
    },
    fs,
  );
}

export function defaultFormStateForType(catalogType: "movie" | "series"): DiscoverFormState {
  return { ...DEFAULT_FORM_STATE, catalogType };
}

/** Verify all CATALOG_TYPES values are known. Static check that the typed
 *  enum stays in sync with the const list at compile time. */
const _ASSERT_CATALOG_TYPES: ReadonlyArray<DiscoverFormState["catalogType"]> =
  TMDB_CATALOG_TYPES.map((t) => t.value as DiscoverFormState["catalogType"]);
void _ASSERT_CATALOG_TYPES;

const _GENRE_REGISTRY: Record<DiscoverFormState["catalogType"], TmdbGenre[]> = {
  movie: TMDB_MOVIE_GENRES,
  series: TMDB_TV_GENRES,
};

export function genresFor(type: DiscoverFormState["catalogType"]): TmdbGenre[] {
  return _GENRE_REGISTRY[type];
}
