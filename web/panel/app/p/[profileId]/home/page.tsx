import { getHomeLayoutSnapshot } from "@/lib/data/homeLayout";
import HomeLayoutForm from "@/components/forms/HomeLayoutForm";

interface Props {
  params: Promise<{ profileId: string }>;
}

export default async function HomeLayoutPage({ params }: Props) {
  const { profileId } = await params;
  const id = Number.parseInt(profileId, 10);
  const snapshot = await getHomeLayoutSnapshot(id);

  return (
    <div className="space-y-6">
      <header>
        <h1 className="text-2xl font-semibold">Home layout</h1>
        <p className="text-sm text-slate-400">
          Choose which catalog rows appear on the TV home screen and in what
          order. Use the hero checkbox to mark catalogs whose items feed the
          featured/hero row at the top.
        </p>
      </header>

      <HomeLayoutForm
        profileId={id}
        catalogs={snapshot.rows}
        layoutSettings={snapshot.layoutSettings}
        initialOrderKeys={snapshot.orderKeys}
        initialDisabledKeys={snapshot.disabledKeys}
        initialHeroKeys={snapshot.heroKeys}
        expectedUpdatedAt={snapshot.expectedUpdatedAt}
      />
    </div>
  );
}
