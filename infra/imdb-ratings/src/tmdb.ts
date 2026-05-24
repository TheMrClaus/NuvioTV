const TMDB_BASE = 'https://api.themoviedb.org/3';

interface ExternalIds {
  imdb_id?: string | null;
}

async function fetchExternalIds(
  kind: 'tv' | 'movie',
  id: number,
  apiKey: string,
  timeoutMs: number,
): Promise<string | null> {
  const ctrl = new AbortController();
  const timer = setTimeout(() => ctrl.abort(), timeoutMs);
  try {
    const url = `${TMDB_BASE}/${kind}/${id}/external_ids?api_key=${encodeURIComponent(apiKey)}`;
    const resp = await fetch(url, { signal: ctrl.signal });
    if (!resp.ok) return null;
    const data = (await resp.json()) as ExternalIds;
    const imdb = data.imdb_id?.trim();
    return imdb && imdb.startsWith('tt') ? imdb : null;
  } catch {
    return null;
  } finally {
    clearTimeout(timer);
  }
}

export async function resolveTmdbToImdb(
  tmdbId: number,
  apiKey: string,
  timeoutMs: number,
): Promise<string | null> {
  // Episode ratings only make sense for TV. Try tv first; fall back to movie
  // so a misrouted movie ID at least resolves something usable (we'll return
  // an empty list downstream since movies have no episodes).
  const tv = await fetchExternalIds('tv', tmdbId, apiKey, timeoutMs);
  if (tv) return tv;
  return fetchExternalIds('movie', tmdbId, apiKey, timeoutMs);
}
