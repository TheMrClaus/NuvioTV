"use server";

import { revalidatePath } from "next/cache";
import { createServerSupabase } from "@/lib/supabase/server";
import { collectionsArraySchema } from "@/lib/data/collections";
import { mapPostgrestError } from "./conflict";
import type { ActionResult } from "./result";

// Addons RPC (sync_push_addons) writes url + enabled + sort_order. The TV
// side (AddonSyncService.kt) writes the same fields; the `enabled` flag is
// the single source of truth for whether catalogs/streams from this addon
// participate in content delivery on either client.

export interface AddonInput {
  url: string;
  enabled: boolean;
}

export async function saveAddons(args: {
  profileId: number;
  addons: AddonInput[];
  expectedUpdatedAt?: string | null;
  revalidatePath?: string;
}): Promise<ActionResult> {
  const seen = new Set<string>();
  const cleaned: { url: string; enabled: boolean; sort_order: number }[] = [];
  for (const a of args.addons) {
    const url = a.url.trim();
    if (url.length === 0) continue;
    if (seen.has(url)) continue;
    seen.add(url);
    cleaned.push({ url, enabled: a.enabled, sort_order: cleaned.length });
  }

  const supabase = await createServerSupabase();
  const { data, error } = await supabase.rpc("sync_push_addons", {
    p_addons: cleaned,
    p_profile_id: args.profileId,
    p_expected_updated_at: args.expectedUpdatedAt ?? null,
  });
  if (error) return mapPostgrestError(error, "addons_conflict");

  if (args.revalidatePath) revalidatePath(args.revalidatePath);
  return { ok: true, updatedAt: (data as string | null) ?? null };
}

// Plugins RPC (sync_push_plugins) writes url + name + enabled + sort_order.
// The plugin's JS code lives on disk on the TV (plugin_code/...) and is
// intentionally not synced; only metadata flows through this RPC.

export interface PluginInput {
  url: string;
  name: string | null;
  enabled: boolean;
}

export async function savePlugins(args: {
  profileId: number;
  plugins: PluginInput[];
  expectedUpdatedAt?: string | null;
  revalidatePath?: string;
}): Promise<ActionResult> {
  const seen = new Set<string>();
  const payload: Array<{
    url: string;
    name: string;
    enabled: boolean;
    sort_order: number;
  }> = [];
  for (const p of args.plugins) {
    const url = p.url.trim();
    if (url.length === 0) continue;
    if (seen.has(url)) continue;
    seen.add(url);
    payload.push({
      url,
      name: p.name?.trim() ?? "",
      enabled: p.enabled,
      sort_order: payload.length,
    });
  }

  const supabase = await createServerSupabase();
  const { data, error } = await supabase.rpc("sync_push_plugins", {
    p_plugins: payload,
    p_profile_id: args.profileId,
    p_expected_updated_at: args.expectedUpdatedAt ?? null,
  });
  if (error) return mapPostgrestError(error, "plugins_conflict");

  if (args.revalidatePath) revalidatePath(args.revalidatePath);
  return { ok: true, updatedAt: (data as string | null) ?? null };
}

// Collections RPC (sync_push_collections) overwrites the full list as a JSONB
// array. The TV side decodes this via Gson into SerializableCollection — keys
// must remain camelCase. The panel does not validate every field; it preserves
// what it pulled and only edits the user-facing fields (title, backdrop, pin,
// folder title/cover/shape).

export async function saveCollections(args: {
  profileId: number;
  collectionsJson: unknown[];
  expectedUpdatedAt?: string | null;
  revalidatePath?: string;
}): Promise<ActionResult> {
  // Strict-shape Zod check before push. Mirrors the Kotlin data classes so the
  // panel can no longer write a malformed collection that the TV would silently
  // drop on Gson decode (CollectionsDataStore.validateCollectionsJson).
  const parsed = collectionsArraySchema.safeParse(args.collectionsJson);
  if (!parsed.success) {
    const issues = parsed.error.issues
      .map((i) => `${i.path.join(".")}: ${i.message}`)
      .join("; ");
    return { ok: false, error: `validation: ${issues}` };
  }

  const supabase = await createServerSupabase();
  const { data, error } = await supabase.rpc("sync_push_collections", {
    p_profile_id: args.profileId,
    p_collections_json: parsed.data,
    p_expected_updated_at: args.expectedUpdatedAt ?? null,
  });
  if (error) return mapPostgrestError(error, "collections_conflict");

  if (args.revalidatePath) revalidatePath(args.revalidatePath);
  return { ok: true, updatedAt: (data as string | null) ?? null };
}

// Profile push — full array overwrite via sync_push_profiles. PIN management
// uses set_profile_pin / clear_profile_pin separately and is not exposed in v2.

export interface ProfileInput {
  profile_index: number;
  name: string;
  avatar_color_hex: string;
  uses_primary_addons: boolean;
  uses_primary_plugins: boolean;
  avatar_id: string | null;
  is_kids: boolean;
  // null clears the column. Coerced to one of the six AgeRatingTier
  // labels (G/PG/PG-13/TV-14/R/NC-17) — see core-domain AgeRatingTier
  // and supabase/_shared/aio_metadata.ts::ageRatingFromString.
  max_age_rating: string | null;
}

export async function saveProfiles(args: {
  profiles: ProfileInput[];
  expectedUpdatedAt?: string | null;
  revalidatePath?: string;
}): Promise<ActionResult> {
  const supabase = await createServerSupabase();
  const { data, error } = await supabase.rpc("sync_push_profiles", {
    p_profiles: args.profiles,
    p_expected_updated_at: args.expectedUpdatedAt ?? null,
  });
  if (error) return mapPostgrestError(error, "profiles_conflict");

  if (args.revalidatePath) revalidatePath(args.revalidatePath);
  return { ok: true, updatedAt: (data as string | null) ?? null };
}

// Wipes per-profile sync data (addons, plugins, library, watch progress, watched,
// settings). For profile_index = 1 the profile row stays but its PIN is cleared;
// for any other index, the profile row itself is also deleted.

export async function deleteProfileData(args: {
  profileId: number;
  revalidatePath?: string;
}): Promise<ActionResult> {
  const supabase = await createServerSupabase();
  const { error } = await supabase.rpc("sync_delete_profile_data", {
    p_profile_id: args.profileId,
  });
  if (error) return { ok: false, error: error.message };

  if (args.revalidatePath) revalidatePath(args.revalidatePath);
  return { ok: true, updatedAt: null };
}
