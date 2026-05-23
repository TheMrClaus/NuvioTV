// Fetches and parses Stremio addon manifests for catalog enumeration.
// The TV resolves a catalog key as `${manifest.id}_${catalog.type}_${catalog.id}`
// (see app-tv/.../ui/screens/home/HomeViewModelCatalogUtils.kt) — this module
// returns parsed manifests in a shape the panel can use to build that key set.

export interface ManifestCatalog {
  type: string;
  id: string;
  name: string;
}

export interface ParsedManifest {
  id: string;
  name: string;
  catalogs: ManifestCatalog[];
}

const FETCH_TIMEOUT_MS = 5000;
const MANIFEST_CACHE_TTL_SECONDS = 3600;

function withTimeout<T>(p: Promise<T>, ms: number): Promise<T> {
  return new Promise((resolve, reject) => {
    const t = setTimeout(() => reject(new Error("manifest fetch timeout")), ms);
    p.then((v) => {
      clearTimeout(t);
      resolve(v);
    }).catch((e) => {
      clearTimeout(t);
      reject(e);
    });
  });
}

export async function fetchManifest(baseUrl: string): Promise<ParsedManifest | null> {
  try {
    const clean = baseUrl.trim().replace(/\/+$/, "");
    if (clean.length === 0) return null;
    const url = clean.endsWith("/manifest.json") ? clean : `${clean}/manifest.json`;
    const res = await withTimeout(
      fetch(url, { next: { revalidate: MANIFEST_CACHE_TTL_SECONDS } }),
      FETCH_TIMEOUT_MS,
    );
    if (!res.ok) return null;
    const json: unknown = await res.json();
    return parseManifest(json);
  } catch {
    return null;
  }
}

function parseManifest(json: unknown): ParsedManifest | null {
  if (typeof json !== "object" || json === null) return null;
  const m = json as { id?: unknown; name?: unknown; catalogs?: unknown };
  if (typeof m.id !== "string" || typeof m.name !== "string") return null;
  const catalogs: ManifestCatalog[] = Array.isArray(m.catalogs)
    ? m.catalogs.flatMap((c) => {
        if (
          typeof c === "object" &&
          c !== null &&
          typeof (c as { type?: unknown }).type === "string" &&
          typeof (c as { id?: unknown }).id === "string"
        ) {
          const cat = c as { type: string; id: string; name?: unknown };
          return [
            {
              type: cat.type,
              id: cat.id,
              name: typeof cat.name === "string" ? cat.name : cat.id,
            },
          ];
        }
        return [];
      })
    : [];
  return { id: m.id, name: m.name, catalogs };
}
