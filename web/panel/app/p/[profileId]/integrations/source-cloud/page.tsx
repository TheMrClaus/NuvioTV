import Link from "next/link";
import { ChevronLeft } from "lucide-react";
import {
  fetchSourceCloudConfigSummary,
  fetchSourceCloudStatus,
} from "@/lib/actions/sourcecloud";
import SourceCloudServicesForm from "@/components/forms/SourceCloudServicesForm";
import SourceCloudApiKeysForm from "@/components/forms/SourceCloudApiKeysForm";
import SourceCloudPresetsForm from "@/components/forms/SourceCloudPresetsForm";
import SourceCloudFiltersForm from "@/components/forms/SourceCloudFiltersForm";
import SourceCloudMatchingForm from "@/components/forms/SourceCloudMatchingForm";
import SourceCloudKeywordsForm from "@/components/forms/SourceCloudKeywordsForm";
import SourceCloudRegexForm from "@/components/forms/SourceCloudRegexForm";
import SourceCloudDedupForm from "@/components/forms/SourceCloudDedupForm";
import SourceCloudSortForm from "@/components/forms/SourceCloudSortForm";
import SourceCloudAdvancedForm from "@/components/forms/SourceCloudAdvancedForm";

interface Props {
  params: Promise<{ profileId: string }>;
}

export default async function SourceCloudPage({ params }: Props) {
  const { profileId } = await params;
  const id = Number.parseInt(profileId, 10);

  const [status, summary] = await Promise.all([
    fetchSourceCloudStatus(id),
    fetchSourceCloudConfigSummary(id),
  ]);

  const config = status?.config ?? null;
  const services = status?.services ?? [];
  const canReset = config?.canReset ?? false;

  return (
    <div className="space-y-6">
      <Link
        href={`/p/${id}/integrations`}
        className="inline-flex items-center gap-1 text-xs text-slate-400 hover:text-slate-200"
      >
        <ChevronLeft className="h-3 w-3" /> Back to integrations
      </Link>
      <header>
        <h1 className="text-2xl font-semibold">Omnio Source Cloud</h1>
        <p className="text-sm text-slate-400">
          Manage your private AIOStreams config. Same backend the phone and TV
          apps use — anything you change here applies everywhere.
        </p>
      </header>

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
            Couldn&apos;t load Source Cloud status. Make sure you&apos;re signed in.
          </p>
        )}
      </section>

      <SourceCloudServicesForm profileId={id} services={services} />

      {summary && <SourceCloudApiKeysForm profileId={id} summary={summary} />}

      {summary && (
        <SourceCloudPresetsForm
          profileId={id}
          provisioned={summary.provisioned}
          presets={summary.presets ?? []}
          availablePresets={summary.availablePresets ?? []}
        />
      )}

      {summary && <SourceCloudFiltersForm profileId={id} summary={summary} />}

      {summary && <SourceCloudMatchingForm profileId={id} summary={summary} />}

      {summary && <SourceCloudKeywordsForm profileId={id} summary={summary} />}

      {summary && <SourceCloudRegexForm profileId={id} summary={summary} />}

      {summary && <SourceCloudDedupForm profileId={id} summary={summary} />}

      {summary && <SourceCloudSortForm profileId={id} summary={summary} />}

      <SourceCloudAdvancedForm profileId={id} canReset={canReset} />
    </div>
  );
}

function StatusChip({ status }: { status: string }) {
  const config: Record<string, { label: string; classes: string }> = {
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
  const entry = config[status] ?? config.unknown;
  return (
    <span
      className={`inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium ${entry.classes}`}
    >
      {entry.label}
    </span>
  );
}
