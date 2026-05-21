import Link from "next/link";
import { ChevronLeft } from "lucide-react";
import {
  fetchAioMetadataConfig,
  fetchAioMetadataStatus,
  type AioMetadataConfigState,
} from "@/lib/actions/aiometadata";
import AioMetadataAdminCard from "@/components/forms/AioMetadataAdminCard";
import AioMetadataConfigureSession from "@/components/forms/AioMetadataConfigureSession";
import AioMetadataProvidersForm from "@/components/forms/AioMetadataProvidersForm";
import AioMetadataApiKeysForm from "@/components/forms/AioMetadataApiKeysForm";
import AioMetadataDisplayForm from "@/components/forms/AioMetadataDisplayForm";
import AioMetadataTrackersForm from "@/components/forms/AioMetadataTrackersForm";
import AioMetadataSearchForm from "@/components/forms/AioMetadataSearchForm";
import AioMetadataAdvancedForm from "@/components/forms/AioMetadataAdvancedForm";
import AioMetadataCatalogsForm from "@/components/forms/AioMetadataCatalogsForm";

interface Props {
  params: Promise<{ profileId: string }>;
}

export default async function AioMetadataPage({ params }: Props) {
  const { profileId } = await params;
  const id = Number.parseInt(profileId, 10);

  const status = await fetchAioMetadataStatus(id);
  const config = status?.config ?? null;
  const hasConfig = !!config?.hasConfig;

  // Only hit upstream for the full inner config when there's something to
  // load. The forms use this; the page shell does not.
  const fullConfig = hasConfig ? await fetchAioMetadataConfig(id) : null;
  const inner = fullConfig?.config ?? null;

  return (
    <div className="space-y-6">
      <Link
        href={`/p/${id}/integrations`}
        className="inline-flex items-center gap-1 text-xs text-slate-400 hover:text-slate-200"
      >
        <ChevronLeft className="h-3 w-3" /> Back to integrations
      </Link>

      <header>
        <h1 className="text-2xl font-semibold">AIO Metadata</h1>
        <p className="text-sm text-slate-400">
          Metadata, catalogs and artwork from the self-hosted AIOMetadata
          addon. Anything you change here syncs everywhere the addon is
          mounted.
        </p>
      </header>

      <StatusSection config={config} />

      {config?.isKids && (
        <KidsProfileBanner maxAgeRating={config?.maxAgeRating ?? null} />
      )}

      {!hasConfig ? (
        <ProvisionEmptyState />
      ) : (
        <>
          <AioMetadataAdminCard
            profileId={id}
            enabled={config?.enabled ?? false}
            canReset={config?.canReset ?? false}
          />

          <ManifestSection
            profileId={id}
            manifestUrl={config?.manifestUrl ?? null}
            hasConfigureUrl={!!config?.configureUrl}
          />

          {inner ? (
            <>
              <AioMetadataProvidersForm
                profileId={id}
                initialProviders={inner.providers}
                initialSettings={inner.settings}
              />
              <AioMetadataApiKeysForm
                profileId={id}
                initialApiKeys={inner.apiKeys}
              />
              <AioMetadataDisplayForm
                profileId={id}
                initialSettings={inner.settings}
                isKids={config?.isKids ?? false}
              />
              <AioMetadataTrackersForm
                profileId={id}
                initialSettings={inner.settings}
              />
              <AioMetadataSearchForm
                profileId={id}
                initialSettings={inner.settings}
              />
              <AioMetadataAdvancedForm
                profileId={id}
                initialSettings={inner.settings}
              />
              <AioMetadataCatalogsForm
                profileId={id}
                initialCatalogs={inner.catalogs}
              />
            </>
          ) : (
            <UpstreamUnreachable />
          )}
        </>
      )}
    </div>
  );
}

function UpstreamUnreachable() {
  return (
    <section className="rounded-2xl border border-rose-500/40 bg-rose-500/10 p-5 text-sm text-rose-100">
      <h2 className="mb-1 font-medium">Couldn&apos;t load the live config</h2>
      <p className="text-xs text-rose-200/80">
        Status looked fine but the upstream call failed. Try refreshing in a
        moment, or open the upstream &quot;configure&quot; UI from the Endpoints section
        above to make sure your config still exists.
      </p>
    </section>
  );
}

function StatusSection({ config }: { config: AioMetadataConfigState | null }) {
  return (
    <section className="rounded-2xl border border-slate-700/50 bg-slate-800/40 p-5">
      <h2 className="mb-1 text-lg font-medium">Configuration status</h2>
      {config ? (
        <>
          <div className="mt-2 flex items-center gap-2">
            <StatusChip status={config.status ?? "unknown"} />
            <span className="text-sm text-slate-300">{config.label}</span>
          </div>
          {config.message && (
            <p className="mt-2 text-xs text-slate-400">{config.message}</p>
          )}
        </>
      ) : (
        <p className="mt-2 text-sm text-amber-300">
          Couldn&apos;t load AIOMetadata status. Make sure you&apos;re signed in.
        </p>
      )}
    </section>
  );
}

function KidsProfileBanner({ maxAgeRating }: { maxAgeRating: string | null }) {
  return (
    <section className="rounded-2xl border border-amber-500/40 bg-amber-500/10 p-4 text-sm text-amber-100">
      <h2 className="mb-1 font-medium">Kids profile</h2>
      <p className="text-xs text-amber-200/90">
        This profile is flagged as Kids in the TV profile settings
        {maxAgeRating ? (
          <>
            {" "}with a max rating of{" "}
            <span className="font-mono font-medium">{maxAgeRating}</span>
          </>
        ) : null}
        . Changing the Display form&apos;s age rating here re-applies the Kids
        catalog overlay (TMDB cert + genre clamps) and syncs back to the TV
        profile&apos;s rating chip. The TV side does the same when you change
        the chip there.
      </p>
    </section>
  );
}

function ProvisionEmptyState() {
  return (
    <section className="rounded-2xl border border-slate-700/50 bg-slate-800/40 p-5">
      <h2 className="mb-1 text-lg font-medium">Set up on a TV first</h2>
      <p className="text-sm text-slate-400">
        Open <span className="font-medium text-slate-200">Settings → AIO Metadata</span>{" "}
        on a TV that&apos;s signed into this account, set your TMDB + TVDB API keys, and
        enable the addon. That mints your private AIOMetadata config. Once that&apos;s
        done, come back here to edit catalogs, trackers, and the rest.
      </p>
    </section>
  );
}

function ManifestSection({
  profileId,
  manifestUrl,
  hasConfigureUrl,
}: {
  profileId: number;
  manifestUrl: string | null;
  hasConfigureUrl: boolean;
}) {
  if (!manifestUrl && !hasConfigureUrl) return null;
  return (
    <section className="rounded-2xl border border-slate-700/50 bg-slate-800/40 p-5">
      <h2 className="mb-3 text-lg font-medium">Endpoints</h2>
      <div className="space-y-4 text-sm">
        {manifestUrl && (
          <div>
            <p className="text-slate-300">Manifest URL</p>
            <p className="mt-1 break-all font-mono text-xs text-slate-400">
              {manifestUrl}
            </p>
          </div>
        )}
        {hasConfigureUrl && (
          <div>
            <p className="mb-2 text-slate-300">Upstream configure</p>
            <AioMetadataConfigureSession profileId={profileId} />
          </div>
        )}
      </div>
    </section>
  );
}

function StatusChip({ status }: { status: string }) {
  const table: Record<string, { label: string; classes: string }> = {
    ready: {
      label: "Ready",
      classes: "bg-emerald-500/20 text-emerald-300",
    },
    not_provisioned: {
      label: "Not provisioned",
      classes: "bg-slate-700/50 text-slate-300",
    },
    provisioning_failed: {
      label: "Provisioning failed",
      classes: "bg-rose-500/20 text-rose-300",
    },
    unavailable: {
      label: "Unavailable",
      classes: "bg-amber-500/20 text-amber-200",
    },
    invalid: {
      label: "Invalid",
      classes: "bg-rose-500/20 text-rose-300",
    },
    unknown: {
      label: "Unknown",
      classes: "bg-slate-700/50 text-slate-400",
    },
  };
  const entry = table[status] ?? table.unknown;
  return (
    <span
      className={`inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium ${entry.classes}`}
    >
      {entry.label}
    </span>
  );
}
