"use server";

import { saveBlobFeature } from "./blob";
import type { ActionResult } from "./result";

// Re-serializes the 4 home-row keys back into the layout_settings feature blob
// and pushes via the existing partial-merge RPC. The full layout_settings is
// passed through so other layout_settings keys (poster sizes, sidebar flags,
// etc.) aren't wiped — `sync_push_profile_settings_partial` replaces the
// entire feature node, so the caller is responsible for merging.
export async function saveHomeLayout(args: {
  profileId: number;
  orderKeys: string[];
  disabledKeys: string[];
  heroKeys: string[];
  layoutSettings: Record<string, unknown>;
  expectedUpdatedAt: string | null;
  revalidatePath?: string;
}): Promise<ActionResult> {
  const merged: Record<string, unknown> = { ...args.layoutSettings };
  merged.home_catalog_order_keys = JSON.stringify(args.orderKeys);
  merged.disabled_home_catalog_keys = JSON.stringify(args.disabledKeys);
  merged.hero_catalog_keys = JSON.stringify(args.heroKeys);
  // Legacy single-key field mirrors the first hero key — matches the TV's
  // setHeroCatalogKeys() behavior in LayoutPreferenceDataStore.
  if (args.heroKeys.length > 0) {
    merged.hero_catalog_key = args.heroKeys[0];
  } else {
    delete merged.hero_catalog_key;
  }

  return saveBlobFeature({
    profileId: args.profileId,
    featureKey: "layout_settings",
    decodedValues: merged,
    expectedUpdatedAt: args.expectedUpdatedAt,
    revalidatePath: args.revalidatePath,
  });
}
