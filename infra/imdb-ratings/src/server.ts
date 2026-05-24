import { Hono } from 'hono';
import { serve } from '@hono/node-server';
import { fetchAllSeasons } from './imdb.js';
import { resolveTmdbToImdb } from './tmdb.js';
import { closePool, ensureSchema, readCache, writeCache } from './cache.js';

const PORT = Number(process.env.PORT ?? 3000);
const HOST = process.env.HOST ?? '0.0.0.0';
const CACHE_TTL_SECONDS = Number(process.env.CACHE_TTL_SECONDS ?? 86400);
const SCRAPE_TIMEOUT_MS = Number(process.env.SCRAPE_TIMEOUT_MS ?? 10000);
const SCRAPE_CONCURRENCY = Number(process.env.SCRAPE_CONCURRENCY ?? 4);
const RATE_LIMIT_PER_IP_PER_MIN = Number(
  process.env.RATE_LIMIT_PER_IP_PER_MIN ?? 120,
);
const TMDB_API_KEY = (process.env.TMDB_API_KEY ?? '').trim();

const TT_RE = /^tt\d{5,}$/;
const NUM_RE = /^\d+$/;

const app = new Hono();

// Per-IP token bucket. In-memory is fine: Fly serves the app from a single
// machine in lhr (min_machines_running=1), and limits reset every minute.
type Bucket = { count: number; resetAt: number };
const buckets = new Map<string, Bucket>();
const WINDOW_MS = 60_000;

app.use('*', async (c, next) => {
  const ip =
    c.req.header('fly-client-ip') ??
    c.req.header('x-forwarded-for')?.split(',')[0]?.trim() ??
    'unknown';
  const now = Date.now();
  let b = buckets.get(ip);
  if (!b || b.resetAt < now) {
    b = { count: 0, resetAt: now + WINDOW_MS };
    buckets.set(ip, b);
  }
  b.count++;
  if (b.count > RATE_LIMIT_PER_IP_PER_MIN) {
    return c.json({ error: 'rate_limited' }, 429);
  }
  if (buckets.size > 10_000) {
    for (const [k, v] of buckets) if (v.resetAt < now) buckets.delete(k);
  }
  await next();
});

app.get('/health', async (c) => {
  try {
    await ensureSchema();
    return c.json({ status: 'ok' });
  } catch (e) {
    return c.json(
      { status: 'degraded', error: (e as Error).message },
      503,
    );
  }
});

app.get('/api/shows/:id/season-ratings', async (c) => {
  const raw = c.req.param('id').trim();
  let tconst: string | null = null;

  if (TT_RE.test(raw)) {
    tconst = raw;
  } else if (NUM_RE.test(raw)) {
    if (!TMDB_API_KEY) {
      return c.json({ error: 'tmdb_resolution_disabled' }, 501);
    }
    tconst = await resolveTmdbToImdb(
      Number(raw),
      TMDB_API_KEY,
      SCRAPE_TIMEOUT_MS,
    );
    if (!tconst) {
      // Wire-compatible empty list: clients treat empty as "no data" and
      // fall back to whatever path they have. Don't 404.
      c.header('cache-control', 'no-store');
      return c.json([]);
    }
  } else {
    return c.json(
      { error: 'invalid_id', detail: 'expected tt<digits> or numeric tmdb id' },
      400,
    );
  }

  const cached = await readCache(tconst, CACHE_TTL_SECONDS);
  if (cached) {
    c.header('x-cache', 'hit');
    return c.json(cached);
  }

  c.header('x-cache', 'miss');
  try {
    const seasons = await fetchAllSeasons(
      tconst,
      SCRAPE_TIMEOUT_MS,
      SCRAPE_CONCURRENCY,
    );
    await writeCache(tconst, seasons);
    return c.json(seasons);
  } catch (e) {
    const msg = (e as Error).message ?? 'scrape_failed';
    console.error('scrape failed', { tconst, msg });
    return c.json({ error: 'scrape_failed', detail: msg }, 502);
  }
});

app.notFound((c) => c.json({ error: 'not_found' }, 404));
app.onError((err, c) => {
  console.error('unhandled error', err);
  return c.json({ error: 'server_error' }, 500);
});

const server = serve(
  { fetch: app.fetch, port: PORT, hostname: HOST },
  (info) => {
    console.log(
      `omnio-imdb-ratings listening on ${info.address}:${info.port}`,
    );
  },
);

let shuttingDown = false;
const shutdown = async (signal: string) => {
  if (shuttingDown) return;
  shuttingDown = true;
  console.log(`received ${signal}, draining...`);
  server.close();
  await closePool().catch((e) => console.error('pool close failed', e));
  process.exit(0);
};
process.on('SIGTERM', () => void shutdown('SIGTERM'));
process.on('SIGINT', () => void shutdown('SIGINT'));
