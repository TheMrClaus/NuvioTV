import "server-only";
import { createServerSupabase } from "@/lib/supabase/server";
import type { Collection, CollectionsSnapshot } from "./collections";

/**
 * Server-only data loader for collections. Lives in its own file so the
 * types + Zod schemas in `./collections.ts` stay client-safe — Next.js 15
 * rejects any client component that transitively imports `next/headers`,
 * and `createServerSupabase` does exactly that.
 */
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
