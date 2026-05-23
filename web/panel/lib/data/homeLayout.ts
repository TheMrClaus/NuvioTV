import { listAddons } from "./addons";
import { fetchManifest, type ParsedManifest } from "./manifest";
import { getSettingsSnapshot } from "./settings";

export interface CatalogRow {
  key: string;
  addonId: string;
  addonName: string;
  addonUrl: string;
  catalogType: string;
  catalogId: string;
  catalogName: string;
}

export interface HomeLayoutSnapshot {
  rows: CatalogRow[];
  unknownOrderedKeys: string[];
  layoutSettings: Record<string, unknown>;
  orderKeys: string[];
  disabledKeys: string[];
  heroKeys: string[];
  expectedUpdatedAt: string | null;
}

function parseKeyList(jsonStr: unknown): string[] {
  if (typeof jsonStr !== "string" || jsonStr.length === 0) return [];
  try {
    const arr = JSON.parse(jsonStr);
    return Array.isArray(arr) ? arr.filter((x): x is string => typeof x === "string") : [];
  } catch {
    return [];
  }
}

// Loads everything the home-layout editor needs in one shot:
//   - All installed addons (skipping the AIO meta addon — its manifest isn't
//     surfaced as user-orderable home catalogs)
//   - Each addon's fetched manifest (cached upstream by Next's fetch cache)
//   - The current layout_settings feature blob plus its updated_at, for
//     optimistic-concurrency on save
// Catalog rows that the user previously ordered/disabled but whose manifest
// no longer resolves (uninstalled addon, broken URL) are surfaced as
// `unknownOrderedKeys` so the form can show them as removable stale entries
// instead of silently dropping them.
export async function getHomeLayoutSnapshot(profileId: number): Promise<HomeLayoutSnapshot> {
  const [addons, snapshot] = await Promise.all([
    listAddons(profileId),
    getSettingsSnapshot(profileId),
  ]);

  const manifests = await Promise.all(
    addons.map(
      async (a): Promise<{ url: string; manifest: ParsedManifest | null }> => ({
        url: a.url,
        manifest: await fetchManifest(a.url),
      }),
    ),
  );

  const rows: CatalogRow[] = [];
  for (const { url, manifest } of manifests) {
    if (!manifest) continue;
    for (const cat of manifest.catalogs) {
      rows.push({
        key: `${manifest.id}_${cat.type}_${cat.id}`,
        addonId: manifest.id,
        addonName: manifest.name,
        addonUrl: url,
        catalogType: cat.type,
        catalogId: cat.id,
        catalogName: cat.name,
      });
    }
  }

  const layoutSettings =
    (snapshot.features.layout_settings as Record<string, unknown> | undefined) ?? {};
  const orderKeys = parseKeyList(layoutSettings.home_catalog_order_keys);
  const disabledKeys = parseKeyList(layoutSettings.disabled_home_catalog_keys);
  const heroKeys = parseKeyList(layoutSettings.hero_catalog_keys);

  const knownKeys = new Set(rows.map((r) => r.key));
  const unknownOrderedKeys = orderKeys.filter((k) => !knownKeys.has(k));

  return {
    rows,
    unknownOrderedKeys,
    layoutSettings,
    orderKeys,
    disabledKeys,
    heroKeys,
    expectedUpdatedAt: snapshot.updatedAt,
  };
}
