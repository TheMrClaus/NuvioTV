import { listCollections } from "@/lib/data/collections.server";
import { listAddons } from "@/lib/data/addons";
import { fetchManifest } from "@/lib/data/manifest";
import CollectionsForm, {
  type CatalogChoice,
} from "@/components/forms/CollectionsForm";

interface Props {
  params: Promise<{ profileId: string }>;
}

export default async function CollectionsPage({ params }: Props) {
  const { profileId } = await params;
  const id = Number.parseInt(profileId, 10);

  const [{ collections, updatedAt }, addons] = await Promise.all([
    listCollections(id),
    listAddons(id),
  ]);

  const manifests = await Promise.all(addons.map((a) => fetchManifest(a.url)));
  const availableCatalogs: CatalogChoice[] = [];
  for (const manifest of manifests) {
    if (!manifest) continue;
    for (const cat of manifest.catalogs) {
      availableCatalogs.push({
        addonId: manifest.id,
        addonName: manifest.name,
        type: cat.type,
        catalogId: cat.id,
        catalogName: cat.name,
      });
    }
  }

  return (
    <div className="space-y-6">
      <header>
        <h1 className="text-2xl font-semibold">Collections</h1>
        <p className="text-sm text-slate-400">
          Custom row collections. Add or remove collections and folders from
          here; pick catalog sources from your installed addons.
        </p>
      </header>

      <CollectionsForm
        profileId={id}
        initial={collections}
        expectedUpdatedAt={updatedAt}
        availableCatalogs={availableCatalogs}
      />
    </div>
  );
}
