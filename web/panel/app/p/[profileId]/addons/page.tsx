import { getAioMetadataAddonUrl, isAioManifest, listAddons } from "@/lib/data/addons";
import AddonsForm from "@/components/forms/AddonsForm";

interface Props {
  params: Promise<{ profileId: string }>;
}

export default async function AddonsPage({ params }: Props) {
  const { profileId } = await params;
  const id = Number.parseInt(profileId, 10);
  const [addons, aioBaseUrl] = await Promise.all([
    listAddons(id),
    getAioMetadataAddonUrl(id),
  ]);

  // Split AIOMetadata's row out so it doesn't render in the UI list. The form
  // re-appends it on save so sync_push_addons doesn't drop it from Supabase
  // (which would unmount AIO on the TV's next remote pull).
  const visible = [];
  let aioRow: { id: string; url: string; name: string | null; enabled: boolean; sort_order: number } | null = null;
  for (const a of addons) {
    if (isAioManifest(a.url, aioBaseUrl)) {
      aioRow = { id: a.id, url: a.url, name: a.name, enabled: a.enabled, sort_order: a.sort_order };
    } else {
      visible.push(a);
    }
  }

  // Snapshot the max updated_at across ALL addon rows for this profile (including
  // the hidden AIO row) — the concurrency check in sync_push_addons compares
  // against the whole set, not just the visible subset.
  const expectedUpdatedAt = addons.reduce<string | null>(
    (max, a) => (max === null || a.updated_at > max ? a.updated_at : max),
    null,
  );

  return (
    <div className="space-y-6">
      <header>
        <h1 className="text-2xl font-semibold">Addons</h1>
        <p className="text-sm text-slate-400">
          Stremio-compatible addons installed for this profile. Drag to reorder, paste
          a manifest URL to add one. The TV writes only URL and order — name is
          discovered from the manifest on the TV.
        </p>
      </header>

      <AddonsForm
        profileId={id}
        initial={visible.map((a) => ({
          id: a.id,
          url: a.url,
          name: a.name,
          enabled: a.enabled,
        }))}
        hiddenAioAddon={aioRow ? { url: aioRow.url, enabled: aioRow.enabled } : null}
        expectedUpdatedAt={expectedUpdatedAt}
      />
    </div>
  );
}
