import type {
  SeriesGraphEpisodeRatingDto,
  SeriesGraphSeasonRatingsDto,
} from './types.js';

// IMDB's own frontend hits caching.graphql.imdb.com from the browser. It
// returns clean JSON, isn't gated by the Akamai bot challenge that 202s the
// HTML pages, and exposes everything we need in one round-trip per season.
const GRAPHQL_URL = 'https://caching.graphql.imdb.com/';
const UA =
  'Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) ' +
  'Chrome/127.0.0.0 Safari/537.36';

const QUERY = `query TitleEpisodes($titleId: ID!, $season: String!, $first: Int) {
  title(id: $titleId) {
    id
    episodes {
      seasons { number }
      episodes(
        filter: {
          releasedOnOrAfter: { day: 1, month: 1, year: 1900 }
          includeSeasons: [$season]
        }
        first: $first
      ) {
        total
        edges {
          node {
            id
            titleText { text }
            series {
              displayableEpisodeNumber {
                episodeNumber { text }
                displayableSeason { season }
              }
            }
            ratingsSummary { aggregateRating voteCount }
          }
        }
      }
    }
  }
}`;

interface GraphQLEpisodesResponse {
  data?: {
    title?: {
      id?: string;
      episodes?: {
        seasons?: Array<{ number?: number | string | null }> | null;
        episodes?: {
          total?: number;
          edges?: Array<{
            node?: {
              id?: string;
              titleText?: { text?: string | null } | null;
              series?: {
                displayableEpisodeNumber?: {
                  episodeNumber?: { text?: string | null } | null;
                  displayableSeason?: { season?: string | null } | null;
                } | null;
              } | null;
              ratingsSummary?: {
                aggregateRating?: number | null;
                voteCount?: number | null;
              } | null;
            } | null;
          }> | null;
        } | null;
      } | null;
    } | null;
  };
  errors?: Array<{ message?: string }>;
}

async function graphql(
  titleId: string,
  season: string,
  first: number,
  timeoutMs: number,
): Promise<GraphQLEpisodesResponse> {
  const ctrl = new AbortController();
  const timer = setTimeout(() => ctrl.abort(), timeoutMs);
  try {
    const resp = await fetch(GRAPHQL_URL, {
      method: 'POST',
      headers: {
        'User-Agent': UA,
        'Content-Type': 'application/json',
        Origin: 'https://www.imdb.com',
        Referer: 'https://www.imdb.com/',
        'x-imdb-client-name': 'imdb-web-next-localized',
      },
      body: JSON.stringify({
        operationName: 'TitleEpisodes',
        variables: { titleId, season, first },
        query: QUERY,
      }),
      signal: ctrl.signal,
    });
    if (!resp.ok) {
      throw new Error(`imdb_gql_http_${resp.status}`);
    }
    return (await resp.json()) as GraphQLEpisodesResponse;
  } finally {
    clearTimeout(timer);
  }
}

function toInt(v: string | number | null | undefined): number | null {
  if (v == null) return null;
  const n = typeof v === 'string' ? Number(v) : v;
  return typeof n === 'number' && Number.isFinite(n) ? n : null;
}

export async function fetchSeasonsList(
  tconst: string,
  timeoutMs: number,
): Promise<number[]> {
  // The seasons array is returned even when we only ask for one season's
  // episodes, so this is a single cheap call.
  const resp = await graphql(tconst, '1', 1, timeoutMs);
  const seasons = resp.data?.title?.episodes?.seasons ?? [];
  const out = new Set<number>();
  for (const s of seasons) {
    const n = toInt(s?.number ?? null);
    if (n != null && n > 0) out.add(n);
  }
  if (out.size === 0) return [1];
  return [...out].sort((a, b) => a - b);
}

export async function fetchSeasonEpisodes(
  tconst: string,
  season: number,
  timeoutMs: number,
): Promise<SeriesGraphEpisodeRatingDto[]> {
  // 250 covers every show shipping in 2026; daily soaps with 500+ ep seasons
  // are out of scope. Bump if real users hit it.
  const resp = await graphql(tconst, String(season), 250, timeoutMs);
  const edges = resp.data?.title?.episodes?.episodes?.edges ?? [];
  const out: SeriesGraphEpisodeRatingDto[] = [];
  for (const e of edges) {
    const node = e?.node;
    if (!node) continue;
    const id = node.id ?? null;
    const episodeNum = toInt(
      node.series?.displayableEpisodeNumber?.episodeNumber?.text ?? null,
    );
    const seasonNum =
      toInt(
        node.series?.displayableEpisodeNumber?.displayableSeason?.season ?? null,
      ) ?? season;
    const rating = node.ratingsSummary?.aggregateRating;
    const name = node.titleText?.text ?? null;
    if (episodeNum == null || rating == null) continue;
    out.push({
      season_number: seasonNum,
      episode_number: episodeNum,
      vote_average: rating,
      name,
      tconst: id,
    });
  }
  out.sort((a, b) => a.episode_number - b.episode_number);
  return out;
}

export async function fetchAllSeasons(
  tconst: string,
  timeoutMs: number,
  concurrency: number,
): Promise<SeriesGraphSeasonRatingsDto[]> {
  const seasons = await fetchSeasonsList(tconst, timeoutMs);
  const out: SeriesGraphSeasonRatingsDto[] = new Array(seasons.length);
  for (let i = 0; i < seasons.length; i += concurrency) {
    const batch = seasons.slice(i, i + concurrency);
    const results = await Promise.all(
      batch.map(async (sn) => ({
        episodes: await fetchSeasonEpisodes(tconst, sn, timeoutMs),
      })),
    );
    for (let j = 0; j < results.length; j++) {
      out[i + j] = results[j];
    }
  }
  return out;
}
