# AIOStreams (self-hosted on Hetzner)

Self-hosted instance of [Viren070/AIOStreams](https://github.com/Viren070/AIOStreams),
running as a `docker compose` stack with local Postgres and Redis. Fronted by
whatever reverse proxy lives on the host (its config is kept out of this repo).
CI deploys via a self-hosted GitHub Actions runner on the same box.

## One-time setup on the box

Prereqs: Docker Engine + Compose plugin installed, and a user in the `docker`
group that the runner will run as.

### Reuse the existing self-hosted runner

The AIOMetadata runner at `~/git/actions-runner` can be reused. Just add the
`aiostreams` label:

```bash
cd ~/git/actions-runner
sudo ./svc.sh stop
./config.sh remove --token "$(gh api -X POST /repos/TheMrClaus/OmnioTV/actions/runners/remove-token --jq .token)"
./config.sh \
  --url https://github.com/TheMrClaus/OmnioTV \
  --token "$(gh api -X POST /repos/TheMrClaus/OmnioTV/actions/runners/registration-token --jq .token)" \
  --name aio-runner \
  --labels self-hosted,aiometadata,aiostreams \
  --unattended
sudo ./svc.sh start
```

Verify with `gh api /repos/TheMrClaus/OmnioTV/actions/runners` that both labels
appear and `status: online`.

### Create the host-private override file (optional but usually needed)

The committed `docker-compose.yml` does not publish ports or join any external
network — that wiring is host-specific and stays off the repo. Put it in
`~/aiostreams-override.yml`; the workflow copies it into the working directory
as `docker-compose.override.yml` if present, and Compose auto-merges it.

See "Reverse proxy / network exposure" below for the two common shapes.

## Reverse proxy / network exposure

The committed `docker-compose.yml` deliberately does **not** publish any ports
or attach to any external network — that wiring depends on how your host's
reverse proxy reaches services and is kept out of the repo.

Drop a `docker-compose.override.yml` in the same directory on the host (it's
gitignored). Compose auto-merges it on `up`. Two common shapes:

```yaml
# Option A: reverse proxy reaches the addon on a shared Docker network
services:
  aiostreams:
    networks:
      - default
      - <your-proxy-network>

networks:
  <your-proxy-network>:
    external: true
    name: <your-proxy-network>
```

```yaml
# Option B: reverse proxy lives on the host and reaches loopback
services:
  aiostreams:
    ports:
      - "127.0.0.1:3000:3000"
```

The proxy resource itself should:
- Front `aiostreams.omnio.tv` (must match `BASE_URL` in `.env` — Stremio
  clients persist this hostname into saved manifest URLs).
- Terminate TLS via Let's Encrypt (or whatever the proxy provides).
- Be configured as **public / no auth** — Stremio clients can't do
  interactive SSO.

## GitHub Secrets

Four new secrets:

| Secret | Value |
|---|---|
| `AIOSTREAMS_POSTGRES_PASSWORD` | `openssl rand -hex 24` |
| `AIOSTREAMS_REDIS_PASSWORD` | `openssl rand -hex 24` |
| `AIOSTREAMS_SECRET_KEY` | `openssl rand -hex 32` |
| `AIOSTREAMS_BASE_URL` | `https://aiostreams.omnio.tv` |

Set them with `gh secret set` from the box itself:

```bash
gh secret set AIOSTREAMS_POSTGRES_PASSWORD --repo TheMrClaus/OmnioTV --body "$(openssl rand -hex 24)"
gh secret set AIOSTREAMS_REDIS_PASSWORD    --repo TheMrClaus/OmnioTV --body "$(openssl rand -hex 24)"
gh secret set AIOSTREAMS_SECRET_KEY        --repo TheMrClaus/OmnioTV --body "$(openssl rand -hex 32)"
gh secret set AIOSTREAMS_BASE_URL          --repo TheMrClaus/OmnioTV --body "https://aiostreams.omnio.tv"
```

## First deploy

Manually trigger the workflow from the Actions tab (`workflow_dispatch`) or
edit any file under `infra/aiostreams-selfhost/` and push to `dev`. The
workflow runs on the self-hosted runner: it renders `.env` from secrets,
copies in the override (if present), and runs `docker compose pull && up -d`
from the runner's checkout directory. Named volumes
(`aiostreams_postgres_data`, etc.) live under `/var/lib/docker/volumes/`
so they survive across runs.

Verify after the run completes:

```bash
docker ps --filter "name=aiostreams"     # three containers, all healthy
docker logs -f aiostreams-aiostreams-1  # addon logs
curl -s http://127.0.0.1:3000/configure/ | head -20   # only works if override binds the port
```

## Managing the GitHub Actions runner

Installed at `~/git/actions-runner/` as a systemd service:
`actions.runner.TheMrClaus-OmnioTV.aio-runner.service`.

### Start / stop / status

From the runner directory:

```bash
cd ~/git/actions-runner
sudo ./svc.sh start      # start the service
sudo ./svc.sh stop       # stop it (queued workflows will wait)
sudo ./svc.sh status     # is it running?
```

Equivalent via systemd if you'd rather skip the cd:

```bash
sudo systemctl start  actions.runner.TheMrClaus-OmnioTV.aio-runner.service
sudo systemctl stop   actions.runner.TheMrClaus-OmnioTV.aio-runner.service
sudo systemctl status actions.runner.TheMrClaus-OmnioTV.aio-runner.service
```

The service is enabled at boot, so it survives reboots without intervention.

### Confirm GitHub sees the runner

```bash
gh api /repos/TheMrClaus/OmnioTV/actions/runners \
  --jq '.runners[] | {name, status, busy, labels: [.labels[].name]}'
```

`status: online, busy: false` with the `aiostreams` label means it's ready
to pick up the next workflow run.

### Logs

```bash
# Service / lifecycle logs
journalctl -u actions.runner.TheMrClaus-OmnioTV.aio-runner.service -f

# Per-job execution logs (rotates per workflow run)
ls -lth ~/git/actions-runner/_diag/
tail -f ~/git/actions-runner/_diag/Worker_*.log
```

For workflow output, `gh run view <id> --log` or the Actions UI is more
useful — those are the same files but rendered.

### Updating the runner binary

The runner auto-updates within a major version when GitHub publishes a new
build, so this is rarely needed. For a manual upgrade (or major version bump):

```bash
cd ~/git/actions-runner
sudo ./svc.sh stop

# Pick a version from https://github.com/actions/runner/releases
VERSION=2.334.0
curl -fsSL -o runner.tar.gz \
  https://github.com/actions/runner/releases/download/v$VERSION/actions-runner-linux-x64-$VERSION.tar.gz
tar xzf runner.tar.gz && rm runner.tar.gz

sudo ./svc.sh start
```

Existing config + labels survive — no re-registration needed.

### Removing the runner

Only when decommissioning or moving the runner elsewhere:

```bash
TOKEN=$(gh api -X POST /repos/TheMrClaus/OmnioTV/actions/runners/remove-token --jq .token)

cd ~/git/actions-runner
sudo ./svc.sh stop
sudo ./svc.sh uninstall
./config.sh remove --token "$TOKEN"
cd .. && rm -rf actions-runner
```

### Common issues

- **Workflow stays "Queued" forever** → runner is offline, or its labels
doesn't match `runs-on: [self-hosted, aiostreams]`. Check `svc.sh status`
and the runners page in GitHub Settings.
- **`docker: permission denied`** in the deploy step → the runner's user
isn't in the `docker` group. `sudo usermod -aG docker $USER` and restart
the service (group membership is read at process start).
- **"Runner connect error"** in service logs → outbound HTTPS to
`*.actions.githubusercontent.com` is blocked. The runner only needs
egress; never inbound.

## Updating the addon image

To pull a new upstream version, edit the `image:` tag in
`docker-compose.yml` (use a SHA-suffixed tag from
https://github.com/Viren070/AIOStreams/pkgs/container/aiostreams, never a
floating tag like `latest` or a date tag — those can be re-pushed) and
commit. CI runs `docker compose pull && up -d`, which recreates only the
`aiostreams` service. `~/aiostreams-override.yml` on the host is never
touched by the workflow.

## Backups

`pg_dump` nightly to a Hetzner Storage Box via cron on the host — the volume
itself is on the local SSD with no offsite copy. Restic to a Storage Box is
about €3/mo for 1TB and is the cheapest credible disaster-recovery target.
