// Standalone scraper smoke test. Hits real IMDB with no DB dependency.
//   npx tsx src/smoke.ts tt0944947     # Game of Thrones
//   npx tsx src/smoke.ts tt0903747     # Breaking Bad

import { fetchAllSeasons, fetchSeasonsList } from './imdb.js';

const tconst = process.argv[2] ?? 'tt0944947';
const timeoutMs = Number(process.env.SCRAPE_TIMEOUT_MS ?? 15000);
const concurrency = Number(process.env.SCRAPE_CONCURRENCY ?? 2);

(async () => {
  console.log(`tconst=${tconst}`);
  const seasons = await fetchSeasonsList(tconst, timeoutMs);
  console.log('seasons:', seasons);

  const all = await fetchAllSeasons(tconst, timeoutMs, concurrency);
  console.log(`got ${all.length} seasons`);
  all.forEach((s, i) => {
    const seasonNo = s.episodes[0]?.season_number ?? `?(${i + 1})`;
    const eps = s.episodes;
    console.log(`  S${seasonNo}: ${eps.length} eps`);
    eps.slice(0, 3).forEach((e) => {
      console.log(
        `    s${e.season_number}e${e.episode_number} ${e.vote_average}  ${e.tconst}  ${e.name}`,
      );
    });
  });
})().catch((e) => {
  console.error('smoke failed:', e);
  process.exit(1);
});
