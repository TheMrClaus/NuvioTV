import { createServerSupabase } from "@/lib/supabase/server";

export interface Addon {
  id: string;
  url: string;
  name: string | null;
  enabled: boolean;
  sort_order: number;
  profile_id: number;
  created_at: string;
  updated_at: string;
}

export interface Plugin {
  id: string;
  url: string;
  name: string | null;
  enabled: boolean;
  sort_order: number;
  profile_id: number;
  created_at: string;
  updated_at: string;
}

export async function listAddons(profileId: number): Promise<Addon[]> {
  const supabase = await createServerSupabase();
  const { data, error } = await supabase
    .from("addons")
    .select("*")
    .eq("profile_id", profileId)
    .order("sort_order", { ascending: true });
  if (error) throw error;
  return (data ?? []) as Addon[];
}

// AIOMetadata is managed only from Settings > AIOMetadata; its manifest URL
// must be hidden from the addon list UI. Returns the canonicalized base URL
// (no trailing /manifest.json) for the active profile, or null if AIO isn't
// provisioned. The TV side stores AIO's manifest in the same addons table,
// so the panel needs to know which row to mask before rendering.
export async function getAioMetadataAddonUrl(
  profileId: number,
): Promise<string | null> {
  const supabase = await createServerSupabase();
  const { data, error } = await supabase
    .from("aio_metadata_links")
    .select("manifest_url")
    .eq("profile_id", profileId)
    .maybeSingle();
  if (error || !data) return null;
  const raw = (data.manifest_url as string | null)?.trim();
  if (!raw) return null;
  return raw.replace(/\/+$/g, "").replace(/\/manifest\.json$/i, "").replace(/\/+$/g, "");
}

export function isAioManifest(addonUrl: string, aioBaseUrl: string | null): boolean {
  if (!aioBaseUrl) return false;
  const canonical = addonUrl.trim().replace(/\/+$/g, "").replace(/\/manifest\.json$/i, "").replace(/\/+$/g, "");
  return canonical.toLowerCase() === aioBaseUrl.toLowerCase();
}

export async function listPlugins(profileId: number): Promise<Plugin[]> {
  const supabase = await createServerSupabase();
  const { data, error } = await supabase
    .from("plugins")
    .select("*")
    .eq("profile_id", profileId)
    .order("sort_order", { ascending: true });
  if (error) throw error;
  return (data ?? []) as Plugin[];
}
