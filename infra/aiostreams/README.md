# AIOStreams (Fly.io)

Deploys a private self-hosted AIOStreams instance for Omnio Source Cloud to use as an internal backend provider. Android clients should not call this service directly; Source Cloud owns user auth, per-profile config ownership, filtering, ranking, and response normalization.

## Upstream

- Repo: [`Viren070/AIOStreams`](https://github.com/Viren070/AIOStreams)
- Image: `docker.io/viren070/aiostreams`
- Pinned version: AIOStreams `v2.29.6`
- Pinned image digest: `docker.io/viren070/aiostreams@sha256:4b0e529664354382d4a496b168ecfbd1973d4dc33e1de3e652adf79f35ec1285`

The digest is used in `fly.toml` instead of `latest` or `v2` so deploys are reproducible. Upstream also publishes GHCR images, but this config uses the Docker Hub digest verified from the public Docker Hub tag API.

## Topology

Required Fly.io resources:

- AIOStreams app: placeholder `omnio-aiostreams`
- Postgres: required for production user/config storage via `DATABASE_URI`
- Volume: required as `/app/data` for AIOStreams runtime data and safe fallback storage
- Redis: optional for a single Fly Machine, recommended before running multiple Machines or regions via `REDIS_URI`

Recommended access model:

- Source Cloud calls AIOStreams over Fly private networking using `http://omnio-aiostreams.internal:3000` when both services run in the same Fly organization.
- If Source Cloud is not on Fly, use HTTPS and protect access with a high-entropy `ADDON_PASSWORD`, short-lived Source Cloud-side advanced sessions, and strict secret handling.
- Keep AIOStreams as an implementation detail. Do not put raw AIOStreams config URLs, config passwords, debrid tokens, or playback URLs in app-visible config.

## Secrets

Set these as Fly secrets or GitHub repository secrets for the deploy workflow. Use placeholder names only in docs and code; never commit real values.

| GitHub Secret | Fly env var | Required | Notes |
|---|---|---:|---|
| `FLY_API_TOKEN` | - | CI only | Fly token with deploy access |
| `AIOSTREAMS_BASE_URL` | `BASE_URL` | Yes | Public or internal base URL used by AIOStreams for generated URLs |
| `AIOSTREAMS_DATABASE_URI` | `DATABASE_URI` | Yes | Fly Postgres connection string |
| `AIOSTREAMS_SECRET_KEY` | `SECRET_KEY` | Yes | 64-character hex, `openssl rand -hex 32` |
| `AIOSTREAMS_ADDON_PASSWORD` | `ADDON_PASSWORD` | Yes | High-entropy service password; supports comma-separated rotation window |
| `AIOSTREAMS_REDIS_URI` | `REDIS_URI` | No | Required only when using Redis |
| `AIOSTREAMS_TMDB_ACCESS_TOKEN` | `TMDB_ACCESS_TOKEN` | No | Optional metadata matching fallback; avoid if user-scoped tokens are preferred |
| `AIOSTREAMS_TRAKT_CLIENT_ID` | `TRAKT_CLIENT_ID` | No | Optional alias lookup support |
| `AIOSTREAMS_FORCED_SERVICE_CREDENTIALS` | `FORCED_SERVICE_CREDENTIALS` | No | Avoid for user debrid tokens; prefer Source Cloud per-user config writes |

Do not set `LOG_SENSITIVE_INFO=true` in production. The committed config pins it to `false`.

## One-Time Fly Setup

Run these commands manually. They can create paid resources, so the workflow does not run them.

```bash
cd infra/aiostreams

# Create the app from the committed config without deploying yet.
fly apps create omnio-aiostreams --org <fly-org>

# Create persistent storage for /app/data. Keep it in the primary region.
fly volumes create aiostreams_data --app omnio-aiostreams --region iad --size 1

# Create Postgres. Pick a size that matches expected Source Cloud traffic.
fly postgres create --name omnio-aiostreams-db --org <fly-org> --region iad

# Attach Postgres. This normally sets DATABASE_URL; copy or set it as DATABASE_URI for AIOStreams.
fly postgres attach --app omnio-aiostreams omnio-aiostreams-db
```

If `fly postgres attach` only creates `DATABASE_URL`, set `DATABASE_URI` explicitly:

```bash
fly secrets set --app omnio-aiostreams \
  DATABASE_URI='<postgres-uri-from-fly>'
```

## Optional Redis Setup

AIOStreams can run a single instance without Redis using in-memory cache. Add Redis before horizontal scaling or multi-region deployment.

```bash
fly redis create --name omnio-aiostreams-cache --org <fly-org> --region iad

# Fly prints a redis:// or rediss:// URL. Store it as REDIS_URI.
fly secrets set --app omnio-aiostreams \
  REDIS_URI='<redis-or-rediss-url>'
```

## Secrets Setup

Generate secrets locally and set them in Fly:

```bash
fly secrets set --app omnio-aiostreams \
  BASE_URL='https://aiostreams.omnio.tv' \
  SECRET_KEY="$(openssl rand -hex 32)" \
  ADDON_PASSWORD="$(openssl rand -hex 32)"
```

If deploying through GitHub Actions, set repository secrets instead:

```bash
gh secret set FLY_API_TOKEN --body '<fly-api-token>'
gh secret set AIOSTREAMS_BASE_URL --body 'https://aiostreams.omnio.tv'
gh secret set AIOSTREAMS_DATABASE_URI --body '<postgres-uri>'
gh secret set AIOSTREAMS_SECRET_KEY --body "$(openssl rand -hex 32)"
gh secret set AIOSTREAMS_ADDON_PASSWORD --body "$(openssl rand -hex 32)"
gh secret set AIOSTREAMS_REDIS_URI --body '<redis-or-rediss-url>'
```

If Redis is not used, omit `AIOSTREAMS_REDIS_URI` and do not set `REDIS_URI`.

## Deploy

Manual deploy:

```bash
fly deploy --config infra/aiostreams/fly.toml --app omnio-aiostreams
```

GitHub Actions deploy:

```bash
gh workflow run deploy-aiostreams.yml --ref dev
```

The workflow stages secrets with `flyctl secrets set --stage` and then deploys the pinned image.

## Health Check

The Fly health check uses AIOStreams' API health route:

```bash
fly status --app omnio-aiostreams
curl -fsS https://aiostreams.omnio.tv/api/v1/health
```

Expected JSON shape:

```json
{"success":true,"detail":"OK","error":null,"data":null}
```

For private Fly access from another Fly app:

```bash
fly ssh console --app <source-cloud-app>
curl -fsS http://omnio-aiostreams.internal:3000/api/v1/health
```

## Logs

```bash
fly logs --app omnio-aiostreams
fly logs --app omnio-aiostreams | grep -Ei 'error|warn|health|database|redis'
```

Logs must not contain debrid tokens, AIOStreams config passwords, advanced config URLs, authorization headers, or resolved playback URLs. If sensitive values appear, rotate affected secrets immediately and keep `LOG_SENSITIVE_INFO=false`.

## Rollback And Restart

```bash
# Show releases.
fly releases --app omnio-aiostreams

# Roll back to a known good release version.
fly deploy --config infra/aiostreams/fly.toml --app omnio-aiostreams --image docker.io/viren070/aiostreams@sha256:<previous-digest>

# Restart current Machines if the image is good but runtime state is wedged.
fly machine restart --app omnio-aiostreams --select
```

Prefer rollback by changing `fly.toml` back to the previous pinned digest and deploying, then commit that config change when requested.

## DNS And Custom Domain

Use a stable custom domain if AIOStreams must generate public URLs for advanced config UI, OAuth callbacks, built-in addons, or Stremio-shaped links.

```bash
fly certs add aiostreams.omnio.tv --app omnio-aiostreams
fly certs show aiostreams.omnio.tv --app omnio-aiostreams
```

Add the DNS records Fly prints at the DNS provider for `omnio.tv`. Then set:

```bash
fly secrets set --app omnio-aiostreams \
  BASE_URL='https://aiostreams.omnio.tv'
```

If Source Cloud is the only caller and no public AIOStreams UI is exposed, prefer the private URL `http://omnio-aiostreams.internal:3000` in Source Cloud and keep public use limited to short-lived Source Cloud wrapper pages.

## Source Cloud Integration

Preferred:

```text
SOURCE_CLOUD_AIOSTREAMS_BASE_URL=http://omnio-aiostreams.internal:3000
SOURCE_CLOUD_AIOSTREAMS_ADDON_PASSWORD=<same value as ADDON_PASSWORD when needed>
```

Fallback when Source Cloud cannot use Fly private networking:

```text
SOURCE_CLOUD_AIOSTREAMS_BASE_URL=https://aiostreams.omnio.tv
SOURCE_CLOUD_AIOSTREAMS_ADDON_PASSWORD=<same value as ADDON_PASSWORD>
```

Source Cloud should use AIOStreams user APIs to create one private config per Omnio user/profile, store UUID/password material encrypted, and call `/api/v1/search` with Basic Auth or `x-aiostreams-user-data`. Do not expose raw AIOStreams credentials to Android.

## Security Notes

- Do not expose public raw config access unless it is explicitly needed for the advanced configuration flow.
- Protect backend-to-AIOStreams traffic with Fly private networking where possible and a service/addon password where public HTTPS is required.
- Treat AIOStreams config URLs, UUIDs, encrypted passwords, plaintext config passwords, debrid tokens, advanced URLs, auth headers, and playback URLs as secrets.
- Do not commit `.env` files or copied values from `infra/aiostreams-selfhost/.env`.
- Keep `LOG_SENSITIVE_INFO=false`; never enable debug logging in production while handling user credentials.
- Prefer Source Cloud wrapper pages for advanced config so Omnio auth, expiration, audit logging, and recovery controls stay first-party.

## Operations

Health checks:

- Fly checks `/api/v1/health` every 30 seconds.
- That endpoint checks database access, so failures usually indicate app startup, Postgres, or migration problems.

Secret rotation:

- Rotate `ADDON_PASSWORD` with a temporary comma-separated overlap: `old,new`.
- Update Source Cloud to use the new password.
- Remove the old password from `ADDON_PASSWORD` after all callers have switched.
- Rotating `SECRET_KEY` can invalidate encrypted AIOStreams config passwords; plan it as a maintenance event with config regeneration.

Backups:

- Postgres contains the production AIOStreams user/config data. Configure Fly Postgres snapshots or an external `pg_dump` backup job before real users depend on it.
- Redis is cache only for this topology unless upstream changes its persistence semantics. It can be recreated, but expect cache misses and slower searches after restore.
- The Fly volume stores `/app/data`; with Postgres configured it should not be the primary config database, but keep it for runtime compatibility and inspect before deletion.

Image updates:

```bash
# Check upstream releases and image tags.
# https://github.com/Viren070/AIOStreams/releases
# https://hub.docker.com/r/viren070/aiostreams/tags

# Update infra/aiostreams/fly.toml to a new immutable digest.
fly deploy --config infra/aiostreams/fly.toml --app omnio-aiostreams
curl -fsS https://aiostreams.omnio.tv/api/v1/health
```

Do not use floating tags like `latest`, `v2`, or `v2.29` in production.
