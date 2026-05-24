# IMDB Ratings (Fly.io)

A small Node + Hono service that serves per-episode IMDB ratings for TV
series. It is a drop-in replacement for the upstream `imdb-tapframe` and
`series-graph` endpoints that OmnioTV inherited from the
[`tapframe/NuvioStreaming`](https://github.com/NuvioMedia/NuvioMobile) fork
chain — both of which are closed-source services we don't control.

The Android client calls this from the series details screen
(`MetaDetailsViewModel` → `ImdbEpisodeRatingsRepository`) to populate the
per-episode rating column. Movies do not use this service.

## Wire contract

```
GET /api/shows/{id}/season-ratings
```

`{id}` is either:

- an IMDB title id like `tt0944947` — used directly, or
- a numeric TMDB id like `1399` — resolved to an IMDB id via TMDB's
  `external_ids` endpoint (TV first, then movie).

Response is a JSON array of season objects matching
`SeriesGraphSeasonRatingsDto`:

```json
[
  {
    "episodes": [
      {
        "season_number": 1,
        "episode_number": 1,
        "vote_average": 9.1,
        "name": "Winter Is Coming",
        "tconst": "tt1480055"
      }
    ]
  }
]
```

Empty list (`[]`) is returned for unknown ids or when scraping finds nothing
— the client treats that as "no data" rather than an error.

`X-Cache: hit|miss` is set on every response so cache behavior is debuggable.

## How it works

1. Inbound id is validated (`tt\d+` or `\d+`).
2. Postgres cache lookup keyed by the IMDB tconst (`imdb_season_cache` table,
   24h default TTL).
3. On miss: one GraphQL call discovers the season list, then a parallel fan-out
   (default 4 in flight) pulls each season's episodes. Each call returns clean
   JSON with `aggregateRating`, episode number, season, tconst, and title.
4. Response is wire-compatible with the legacy contract so the Android side
   only needs a `BuildConfig` URL flip.

The scraper targets `https://caching.graphql.imdb.com/`, which is the same
endpoint IMDB's own React frontend uses. The HTML pages
(`/title/{tt}/episodes/`) are gated by Akamai Bot Manager — they 202 every
request from a non-browser client. The GraphQL endpoint is not gated the
same way and returns the data we need in one round trip per season.

Tracked operation: `TitleEpisodes` with variables `{titleId, season, first}`.
The query string is checked into `src/imdb.ts`; if IMDB ever requires a
persisted-query hash, expect a `502 scrape_failed` from this service and
re-derive the hash from a fresh page load.

## Environment

| Env var | Default | Notes |
|---|---|---|
| `DATABASE_URI` | _required_ | Supavisor pooled Postgres URI, same shape as `aiostreams`. `sslmode=no-verify` recommended. |
| `TMDB_API_KEY` | _empty_ | v3 API key for TMDB → IMDB id resolution. If unset, numeric ids return `501`. |
| `PORT` | `3000` | |
| `HOST` | `0.0.0.0` | |
| `CACHE_TTL_SECONDS` | `86400` | 24h. Ratings change slowly. |
| `SCRAPE_TIMEOUT_MS` | `10000` | Per IMDB request. |
| `SCRAPE_CONCURRENCY` | `4` | Parallel season fetches per show. |
| `RATE_LIMIT_PER_IP_PER_MIN` | `120` | Per-IP token bucket. |
| `PG_POOL_MAX` | `5` | pg pool size. |

## Secrets

The deploy workflow **reuses existing repo secrets** so there is nothing new
to provision before the first deploy:

| GitHub Secret (reused) | Fly env var | Required | Notes |
|---|---|---:|---|
| `FLY_API_TOKEN` | – | CI only | Fly token with deploy access (already set for `aiometadata` / `aiostreams`). |
| `AIOSTREAMS_DATABASE_URI` | `DATABASE_URI` | Yes | Same Supavisor pooler URI used by `aiostreams`. The service auto-creates its own `imdb_season_cache` table in the `public` schema; it does not collide with `aiostreams` tables. |
| `TMDB_API_KEY` | `TMDB_API_KEY` | No | Same TMDB v3 key used by the Android release build (`app-tv/build.gradle.kts`). Enables numeric (TMDB id) lookups. Without it, only `tt<digits>` ids work. |

No new GitHub Secrets need to be created. If you ever want to isolate this
service from `aiostreams` later, create a dedicated secret (e.g.
`IMDB_RATINGS_DATABASE_URI`) and update the workflow to read it instead.

## One-time Fly setup

```bash
cd infra/imdb-ratings

fly apps create omnio-imdb-ratings --org <fly-org>
```

The service is stateless beyond the Postgres cache — no Fly volume needed.

## Deploy

Manual:

```bash
fly deploy --config infra/imdb-ratings/fly.toml --app omnio-imdb-ratings
```

GitHub Actions (preferred):

```bash
gh workflow run deploy-imdb-ratings.yml --ref dev
```

The workflow stages secrets with `flyctl secrets set --stage` then deploys.

## Health check

```bash
curl -fsS https://imdb-ratings.omnio.tv/health
# {"status":"ok"}
```

`/health` runs `CREATE TABLE IF NOT EXISTS ...` so a `200` also means
Postgres is reachable. Fly polls it every 30s.

## Local development

```bash
npm install
DATABASE_URI='postgresql://...' TMDB_API_KEY='...' npm run dev
curl 'http://localhost:3000/api/shows/tt0944947/season-ratings' | jq '.[0].episodes[:3]'
```

## Custom domain

```bash
fly certs add imdb-ratings.omnio.tv --app omnio-imdb-ratings
fly certs show imdb-ratings.omnio.tv --app omnio-imdb-ratings
```

Add the A/AAAA records Fly prints at the DNS provider for `omnio.tv`.

## Android wiring

Once the service is reachable, point both rating BuildConfig fields at it
and remove the dual-API fallback in `ImdbEpisodeRatingsRepository.kt`.

```properties
# local.properties (or GitHub Secrets for CI builds)
IMDB_TAPFRAME_API_BASE_URL=https://imdb-ratings.omnio.tv/
IMDB_RATINGS_API_BASE_URL=https://imdb-ratings.omnio.tv/
```

Then the `fetchFromImdbTapframe` → `fetchFromSeriesGraph` fallback in
`ImdbEpisodeRatingsRepository.kt` collapses to a single path. That cleanup
is a follow-up PR (one file, one test).

## Operational notes

- **IMDB TOS**: This service consumes IMDB's own GraphQL endpoint, the same
  one `imdb.com` calls from the browser. We pose as `imdb-web-next-localized`
  per the official `x-imdb-client-name` header. The legal posture is identical
  to the upstream `imdb-tapframe` it replaces; aggressive caching (24h) plus
  per-IP rate limits keep traffic well below what any single IMDB visitor
  generates. If IMDB blocks the Fly egress IP, rotate via `fly machine clone`
  to a new region.
- **GraphQL drift**: If IMDB introduces a persisted-query requirement, the
  service will start returning `502 scrape_failed`. The query string in
  `src/imdb.ts` is the only thing to update; the rest of the pipeline is
  schema-agnostic.
- **No Redis**: The 24h Postgres cache is the only cache. If we ever need
  sub-second cold-path responses, add an in-process LRU before the pg hit.
- **Stateless**: Safe to scale horizontally. The in-memory rate-limit bucket
  is per-machine, which is fine for the soft limits we set.
- **Logs**: `console.log/error` to stdout. View with
  `fly logs --app omnio-imdb-ratings`.

## Rollback

```bash
fly releases --app omnio-imdb-ratings
fly deploy --config infra/imdb-ratings/fly.toml --app omnio-imdb-ratings --image registry.fly.io/omnio-imdb-ratings:<previous-tag>
```

Or revert the commit that touched `infra/imdb-ratings/` — the deploy
workflow will redeploy the previous image build.
