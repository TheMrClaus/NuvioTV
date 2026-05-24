import pg from 'pg';
import type { SeriesGraphSeasonRatingsDto } from './types.js';

const { Pool } = pg;

const CREATE_TABLE_SQL = `
  CREATE TABLE IF NOT EXISTS imdb_season_cache (
    tconst TEXT PRIMARY KEY,
    payload JSONB NOT NULL,
    fetched_at TIMESTAMPTZ NOT NULL DEFAULT now()
  );
  CREATE INDEX IF NOT EXISTS imdb_season_cache_fetched_at
    ON imdb_season_cache (fetched_at);
`;

let pool: pg.Pool | null = null;
let schemaReady: Promise<void> | null = null;

export function getPool(): pg.Pool {
  if (pool) return pool;
  const dbUri = process.env.DATABASE_URI;
  if (!dbUri) throw new Error('DATABASE_URI is not set');
  pool = new Pool({
    connectionString: dbUri,
    max: Number(process.env.PG_POOL_MAX ?? 5),
    idleTimeoutMillis: 30_000,
  });
  pool.on('error', (err) => {
    console.error('pg pool error', err);
  });
  return pool;
}

export async function ensureSchema(): Promise<void> {
  if (!schemaReady) {
    schemaReady = (async () => {
      await getPool().query(CREATE_TABLE_SQL);
    })().catch((err) => {
      // Reset so a transient failure (e.g. pooler hiccup) can be retried.
      schemaReady = null;
      throw err;
    });
  }
  return schemaReady;
}

interface CacheRow {
  payload: SeriesGraphSeasonRatingsDto[];
  fetched_at: Date;
}

export async function readCache(
  tconst: string,
  ttlSeconds: number,
): Promise<SeriesGraphSeasonRatingsDto[] | null> {
  await ensureSchema();
  const res = await getPool().query<CacheRow>(
    'SELECT payload, fetched_at FROM imdb_season_cache WHERE tconst = $1',
    [tconst],
  );
  if (res.rowCount === 0) return null;
  const row = res.rows[0];
  const ageSec = (Date.now() - row.fetched_at.getTime()) / 1000;
  if (ageSec > ttlSeconds) return null;
  return row.payload;
}

export async function writeCache(
  tconst: string,
  payload: SeriesGraphSeasonRatingsDto[],
): Promise<void> {
  await ensureSchema();
  await getPool().query(
    `INSERT INTO imdb_season_cache (tconst, payload, fetched_at)
     VALUES ($1, $2::jsonb, now())
     ON CONFLICT (tconst) DO UPDATE
       SET payload = EXCLUDED.payload, fetched_at = now()`,
    [tconst, JSON.stringify(payload)],
  );
}

export async function closePool(): Promise<void> {
  if (!pool) return;
  await pool.end();
  pool = null;
  schemaReady = null;
}
