import { z } from "zod";
import { createServerSupabase } from "@/lib/supabase/server";

// Mirrors Collection / CollectionFolder / CollectionCatalogSource in
// core-domain/.../model/Collection.kt. Gson default = camelCase keys; the
// sync RPC writes the raw JSON unchanged, and the TV reads it back via Gson
// with these exact field names + enum values. Drift here corrupts collections
// silently on the TV.
export interface CollectionCatalogSource {
  addonId: string;
  type: string;
  catalogId: string;
  genre?: string | null;
}

export interface CollectionFolder {
  id: string;
  title: string;
  coverImageUrl?: string | null;
  focusGifUrl?: string | null;
  focusGifEnabled?: boolean | null;
  coverEmoji?: string | null;
  tileShape?: string; // "POSTER" | "LANDSCAPE" | "SQUARE"
  hideTitle?: boolean;
  catalogSources?: CollectionCatalogSource[];
}

export interface Collection {
  id: string;
  title: string;
  backdropImageUrl?: string | null;
  pinToTop?: boolean;
  focusGlowEnabled?: boolean | null;
  viewMode?: string; // FolderViewMode enum: "TABBED_GRID" | "ROWS" | "FOLLOW_LAYOUT"
  showAllTab?: boolean;
  folders?: CollectionFolder[];
}

// Mirrors the Kotlin enum values. POSTER / LANDSCAPE / SQUARE in PosterShape.kt.
export const TILE_SHAPES = ["POSTER", "LANDSCAPE", "SQUARE"] as const;
export type TileShape = (typeof TILE_SHAPES)[number];

// Mirrors FolderViewMode.kt. The TV's fromString() also accepts "follow_home"
// as a legacy alias for FOLLOW_LAYOUT — we never emit that from the panel.
export const VIEW_MODES = ["TABBED_GRID", "ROWS", "FOLLOW_LAYOUT"] as const;
export type ViewMode = (typeof VIEW_MODES)[number];

// Runtime validation for collections JSON before push. Mirrors the Kotlin
// data classes; reject-on-unknown-key keeps schema drift loud instead of
// letting a malformed entry land in collections_json and silently corrupt
// the TV's Gson parse.
const collectionCatalogSourceSchema = z
  .object({
    addonId: z.string().min(1),
    type: z.string().min(1),
    catalogId: z.string().min(1),
    genre: z.string().nullable().optional(),
  })
  .strict();

const collectionFolderSchema = z
  .object({
    id: z.string().min(1),
    title: z.string(),
    coverImageUrl: z.string().nullable().optional(),
    focusGifUrl: z.string().nullable().optional(),
    focusGifEnabled: z.boolean().optional(),
    coverEmoji: z.string().nullable().optional(),
    tileShape: z.enum(TILE_SHAPES).optional(),
    hideTitle: z.boolean().optional(),
    catalogSources: z.array(collectionCatalogSourceSchema).optional(),
  })
  .strict();

export const collectionSchema = z
  .object({
    id: z.string().min(1),
    title: z.string(),
    backdropImageUrl: z.string().nullable().optional(),
    pinToTop: z.boolean().optional(),
    focusGlowEnabled: z.boolean().nullable().optional(),
    viewMode: z.enum(VIEW_MODES).optional(),
    showAllTab: z.boolean().optional(),
    folders: z.array(collectionFolderSchema).optional(),
  })
  .strict();

export const collectionsArraySchema = z.array(collectionSchema);

export interface CollectionsSnapshot {
  collections: Collection[];
  updatedAt: string | null;
}

export async function listCollections(profileId: number): Promise<CollectionsSnapshot> {
  const supabase = await createServerSupabase();
  const { data, error } = await supabase.rpc("sync_pull_collections", {
    p_profile_id: profileId,
  });
  if (error) throw error;
  const row = (data as Array<{ collections_json: unknown; updated_at: string }> | null)?.[0];
  if (!row) return { collections: [], updatedAt: null };
  const arr = Array.isArray(row.collections_json) ? row.collections_json : [];
  return { collections: arr as Collection[], updatedAt: row.updated_at ?? null };
}
