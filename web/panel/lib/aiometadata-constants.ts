/**
 * Static metadata about the AIOMetadata config shape, used by the panel
 * forms. Mirrors core-domain/AioMetadataSettings.kt (AioMetadataProvider
 * enum) plus the AIOMetadataDefaultConfig template apiKeys layout.
 *
 * When upstream (cedya77/aiometadata) adds new providers or fields, extend
 * this file rather than scattering literals across the form components.
 */

export interface AioMetadataProviderDef {
  key: string;
  label: string;
  requiresApiKey: boolean;
  /** Key in the inner config's `apiKeys` map. null if the provider has no key (e.g. Kitsu). */
  apiKeyField: string | null;
}

/**
 * Identity providers shown as a per-provider enable + key row. Matches the
 * AioMetadataProvider enum in core-domain.
 */
export const AIO_METADATA_PROVIDERS: AioMetadataProviderDef[] = [
  { key: "tmdb",    label: "TMDB",        requiresApiKey: true,  apiKeyField: "tmdb" },
  { key: "tvdb",    label: "TVDB",        requiresApiKey: true,  apiKeyField: "tvdb" },
  { key: "fanart",  label: "Fanart.tv",   requiresApiKey: true,  apiKeyField: "fanart" },
  { key: "mal",     label: "MyAnimeList", requiresApiKey: true,  apiKeyField: "mal" },
  { key: "anilist", label: "AniList",     requiresApiKey: false, apiKeyField: "anilistTokenId" },
  { key: "kitsu",   label: "Kitsu",       requiresApiKey: false, apiKeyField: null },
];

/** Settings-flag key in `settings` (root) that controls whether a provider is enabled in the UI. */
export function providerEnabledSettingsKey(providerKey: string): string {
  return `nuvio_provider_${providerKey}`;
}

/**
 * Additional API keys that don't correspond to a provider toggle. Surfaced
 * in the API Keys form alongside the per-provider keys.
 */
export interface AioMetadataExtraKeyDef {
  field: string;
  label: string;
  helpText?: string;
  /** Some keys (RPDB) ship a free default value upstream — UI shows them as "managed" when unchanged. */
  hasManagedDefault?: boolean;
}

export const AIO_METADATA_EXTRA_KEYS: AioMetadataExtraKeyDef[] = [
  { field: "rpdb",                    label: "RPDB",                hasManagedDefault: true, helpText: "Poster ratings overlay. A free key is bundled by default." },
  { field: "gemini",                  label: "Gemini",              helpText: "Google Gemini — used for AI search." },
  { field: "mdblist",                 label: "MDBList",             helpText: "Aggregate ratings + watch-tracking." },
  { field: "traktTokenId",            label: "Trakt token id",      helpText: "Bound on the TV via OAuth — copy from there." },
  { field: "simklTokenId",            label: "Simkl token id" },
  { field: "topPoster",               label: "TopPoster" },
  { field: "customDescriptionBlurb",  label: "Custom description blurb" },
];

/** Source choices for the per-media-type routing config (`providers` in the inner config). */
export const AIO_METADATA_ROUTING: Array<{
  field: string;
  label: string;
  helpText?: string;
  choices: string[];
}> = [
  { field: "movie",              label: "Movies",               choices: ["tmdb", "tvdb"] },
  { field: "series",             label: "Series",               choices: ["tvdb", "tmdb"] },
  { field: "anime",              label: "Anime",                choices: ["tvdb", "mal", "anilist", "kitsu"] },
  { field: "anime_id_provider",  label: "Anime ID provider",    choices: ["kitsu", "anilist", "mal"], helpText: "Identifier source used to bridge anime catalogs." },
];

/** Age rating tiers — mirrors core-domain AgeRatingTier. Used by the Display form. */
export const AIO_METADATA_AGE_TIERS = [
  { value: "None",   label: "No rating filter (allow all)" },
  { value: "G",      label: "G" },
  { value: "PG",     label: "PG" },
  { value: "PG-13",  label: "PG-13" },
  { value: "TV-14",  label: "TV-14" },
  { value: "R",      label: "R" },
  { value: "NC-17",  label: "NC-17" },
];

/** Languages users are likely to want. Upstream supports the full TMDB list — extend as needed. */
export const AIO_METADATA_LANGUAGES = [
  { value: "en-US", label: "English (US)" },
  { value: "en-GB", label: "English (UK)" },
  { value: "es-ES", label: "Spanish (Spain)" },
  { value: "es-MX", label: "Spanish (Mexico)" },
  { value: "fr-FR", label: "French" },
  { value: "de-DE", label: "German" },
  { value: "it-IT", label: "Italian" },
  { value: "pt-BR", label: "Portuguese (Brazil)" },
  { value: "pt-PT", label: "Portuguese (Portugal)" },
  { value: "ja-JP", label: "Japanese" },
  { value: "ko-KR", label: "Korean" },
  { value: "zh-CN", label: "Chinese (Simplified)" },
  { value: "ru-RU", label: "Russian" },
  { value: "ar-SA", label: "Arabic" },
  { value: "hi-IN", label: "Hindi" },
  { value: "tr-TR", label: "Turkish" },
  { value: "nl-NL", label: "Dutch" },
  { value: "pl-PL", label: "Polish" },
  { value: "sv-SE", label: "Swedish" },
];

export const AIO_METADATA_POSTER_PROVIDERS = [
  { value: "rpdb", label: "RPDB" },
  { value: "imdb", label: "IMDb" },
  { value: "tmdb", label: "TMDB" },
];

export const AIO_METADATA_TVDB_SEASON_TYPES = [
  { value: "default",   label: "Default" },
  { value: "official",  label: "Official" },
  { value: "dvd",       label: "DVD" },
  { value: "absolute",  label: "Absolute" },
  { value: "alternate", label: "Alternate" },
];

// --- TMDB Discover catalog authoring -----------------------------------------
//
// Curated subsets of TMDB's Discover knobs surfaced in the catalog editor.
// Users can pick from these lists; advanced params remain editable on the
// upstream /configure UI for anything we don't model here.

export interface TmdbDiscoverOption {
  value: string;
  label: string;
}

export const TMDB_SORT_OPTIONS: TmdbDiscoverOption[] = [
  { value: "popularity.desc",      label: "Popularity ↓" },
  { value: "popularity.asc",       label: "Popularity ↑" },
  { value: "vote_average.desc",    label: "Rating ↓" },
  { value: "vote_average.asc",     label: "Rating ↑" },
  { value: "vote_count.desc",      label: "Vote count ↓" },
  { value: "release_date.desc",    label: "Release date ↓ (movies)" },
  { value: "release_date.asc",     label: "Release date ↑ (movies)" },
  { value: "first_air_date.desc",  label: "First air date ↓ (series)" },
  { value: "first_air_date.asc",   label: "First air date ↑ (series)" },
  { value: "revenue.desc",         label: "Revenue ↓ (movies)" },
  { value: "original_title.asc",   label: "Title A-Z (movies)" },
];

export const TMDB_WATCH_REGIONS: TmdbDiscoverOption[] = [
  { value: "US", label: "United States" },
  { value: "GB", label: "United Kingdom" },
  { value: "CA", label: "Canada" },
  { value: "AU", label: "Australia" },
  { value: "DE", label: "Germany" },
  { value: "FR", label: "France" },
  { value: "IT", label: "Italy" },
  { value: "ES", label: "Spain" },
  { value: "BR", label: "Brazil" },
  { value: "MX", label: "Mexico" },
  { value: "NL", label: "Netherlands" },
  { value: "PL", label: "Poland" },
  { value: "JP", label: "Japan" },
  { value: "KR", label: "South Korea" },
  { value: "IN", label: "India" },
];

export interface TmdbProvider {
  id: number;
  label: string;
}

/** Common TMDB watch providers (US-region ids). Users can paste raw provider
 *  ids on the upstream UI for anything we don't surface here. */
export const TMDB_WATCH_PROVIDERS: TmdbProvider[] = [
  { id: 8,    label: "Netflix" },
  { id: 1796, label: "Netflix Standard with Ads" },
  { id: 175,  label: "Netflix Kids" },
  { id: 337,  label: "Disney Plus" },
  { id: 9,    label: "Amazon Prime Video" },
  { id: 613,  label: "Amazon Prime Video Free with Ads" },
  { id: 2100, label: "Amazon Prime Video with Ads" },
  { id: 350,  label: "Apple TV+" },
  { id: 2243, label: "Apple TV+ Amazon Channel" },
  { id: 1899, label: "HBO Max" },
  { id: 1825, label: "HBO Max Amazon Channel" },
  { id: 15,   label: "Hulu" },
  { id: 2303, label: "Paramount+ Premium" },
  { id: 2616, label: "Paramount+ Essential" },
  { id: 582,  label: "Paramount+ Amazon Channel" },
  { id: 633,  label: "Paramount+ Roku Premium Channel" },
  { id: 1853, label: "Paramount+ Apple TV Channel" },
  { id: 386,  label: "Peacock Premium" },
  { id: 387,  label: "Peacock Premium Plus" },
  { id: 283,  label: "Crunchyroll" },
  { id: 1968, label: "Crunchyroll Amazon Channel" },
  { id: 520,  label: "Discovery+" },
  { id: 584,  label: "Discovery+ Amazon Channel" },
  { id: 190,  label: "Curiosity Stream" },
  { id: 603,  label: "CuriosityStream Amazon Channel" },
];

export interface TmdbGenre {
  id: number;
  label: string;
}

/** Standard TMDB movie genres. */
export const TMDB_MOVIE_GENRES: TmdbGenre[] = [
  { id: 28,    label: "Action" },
  { id: 12,    label: "Adventure" },
  { id: 16,    label: "Animation" },
  { id: 35,    label: "Comedy" },
  { id: 80,    label: "Crime" },
  { id: 99,    label: "Documentary" },
  { id: 18,    label: "Drama" },
  { id: 10751, label: "Family" },
  { id: 14,    label: "Fantasy" },
  { id: 36,    label: "History" },
  { id: 27,    label: "Horror" },
  { id: 10402, label: "Music" },
  { id: 9648,  label: "Mystery" },
  { id: 10749, label: "Romance" },
  { id: 878,   label: "Science Fiction" },
  { id: 10770, label: "TV Movie" },
  { id: 53,    label: "Thriller" },
  { id: 10752, label: "War" },
  { id: 37,    label: "Western" },
];

/** Standard TMDB TV genres. */
export const TMDB_TV_GENRES: TmdbGenre[] = [
  { id: 10759, label: "Action & Adventure" },
  { id: 16,    label: "Animation" },
  { id: 35,    label: "Comedy" },
  { id: 80,    label: "Crime" },
  { id: 99,    label: "Documentary" },
  { id: 18,    label: "Drama" },
  { id: 10751, label: "Family" },
  { id: 10762, label: "Kids" },
  { id: 9648,  label: "Mystery" },
  { id: 10763, label: "News" },
  { id: 10764, label: "Reality" },
  { id: 10765, label: "Sci-Fi & Fantasy" },
  { id: 10766, label: "Soap" },
  { id: 10767, label: "Talk" },
  { id: 10768, label: "War & Politics" },
  { id: 37,    label: "Western" },
];

export const TMDB_PROVIDER_JOIN_MODES: TmdbDiscoverOption[] = [
  { value: "or",  label: "OR (any of the providers)" },
  { value: "and", label: "AND (all of the providers)" },
];

/** Provided for the catalogType picker — what we call it on the wire vs the panel. */
export const TMDB_CATALOG_TYPES = [
  { value: "movie",  label: "Movies",  tmdbMediaType: "movie" as const },
  { value: "series", label: "Series",  tmdbMediaType: "tv" as const },
];

