"use client";

import { useMemo, useState, useTransition } from "react";
import { useRouter } from "next/navigation";
import {
  updateSourceCloudConfig,
  type SourceCloudConfigSummaryResponse,
} from "@/lib/actions/sourcecloud";
import { QUALITIES, RESOLUTIONS } from "@/lib/aiostreams-constants";

interface Props {
  profileId: number;
  summary: SourceCloudConfigSummaryResponse;
}

interface FormState {
  excludedResolutions: string[];
  preferredResolutions: string[];
  excludedQualities: string[];
  preferredQualities: string[];
}

function summaryToForm(s: SourceCloudConfigSummaryResponse): FormState {
  return {
    excludedResolutions: [...(s.excludedResolutions ?? [])],
    preferredResolutions: [...(s.preferredResolutions ?? [])],
    excludedQualities: [...(s.excludedQualities ?? [])],
    preferredQualities: [...(s.preferredQualities ?? [])],
  };
}

function toggleInArray(arr: string[], value: string): string[] {
  return arr.includes(value) ? arr.filter((v) => v !== value) : [...arr, value];
}

export default function SourceCloudFiltersForm({ profileId, summary }: Props) {
  const router = useRouter();
  const baseline = useMemo(() => summaryToForm(summary), [summary]);
  const [form, setForm] = useState<FormState>(baseline);
  const [error, setError] = useState<string | null>(null);
  const [isPending, startTransition] = useTransition();

  const dirty = JSON.stringify(form) !== JSON.stringify(baseline);

  const handleSave = () => {
    setError(null);
    startTransition(async () => {
      const diff: Parameters<typeof updateSourceCloudConfig>[0] = { profileId };
      if (JSON.stringify(form.excludedResolutions) !== JSON.stringify(baseline.excludedResolutions))
        diff.excludedResolutions = form.excludedResolutions;
      if (JSON.stringify(form.preferredResolutions) !== JSON.stringify(baseline.preferredResolutions))
        diff.preferredResolutions = form.preferredResolutions;
      if (JSON.stringify(form.excludedQualities) !== JSON.stringify(baseline.excludedQualities))
        diff.excludedQualities = form.excludedQualities;
      if (JSON.stringify(form.preferredQualities) !== JSON.stringify(baseline.preferredQualities))
        diff.preferredQualities = form.preferredQualities;

      const result = await updateSourceCloudConfig(diff);
      if (result.ok) {
        router.refresh();
      } else {
        setError(result.error ?? "Save failed");
      }
    });
  };

  if (!summary.provisioned) {
    return (
      <section className="rounded-2xl border border-slate-700/50 bg-slate-800/40 p-5">
        <h2 className="mb-1 text-lg font-medium">Filters</h2>
        <p className="text-sm text-slate-400">
          Connect a debrid service first — filters live on your AIOStreams config.
        </p>
      </section>
    );
  }

  return (
    <section className="rounded-2xl border border-slate-700/50 bg-slate-800/40 p-5">
      <h2 className="mb-1 text-lg font-medium">Filters</h2>
      <p className="mb-4 text-xs text-slate-400">
        Exclude removes streams entirely. Preferred ranks them higher (works with
        Quality/Resolution sort criteria).
      </p>

      <div className="space-y-5">
        <ChipGroup
          label="Excluded resolutions"
          help="These resolutions are removed from results."
          options={RESOLUTIONS}
          selected={form.excludedResolutions}
          onToggle={(v) =>
            setForm({ ...form, excludedResolutions: toggleInArray(form.excludedResolutions, v) })
          }
        />
        <ChipGroup
          label="Preferred resolutions"
          help="These resolutions are ranked higher when sorting by resolution."
          options={RESOLUTIONS}
          selected={form.preferredResolutions}
          onToggle={(v) =>
            setForm({ ...form, preferredResolutions: toggleInArray(form.preferredResolutions, v) })
          }
        />
        <ChipGroup
          label="Excluded qualities"
          help="These source qualities are removed from results."
          options={QUALITIES}
          selected={form.excludedQualities}
          onToggle={(v) =>
            setForm({ ...form, excludedQualities: toggleInArray(form.excludedQualities, v) })
          }
        />
        <ChipGroup
          label="Preferred qualities"
          help="These source qualities are ranked higher when sorting by quality."
          options={QUALITIES}
          selected={form.preferredQualities}
          onToggle={(v) =>
            setForm({ ...form, preferredQualities: toggleInArray(form.preferredQualities, v) })
          }
        />
      </div>

      {error && <p className="mt-3 text-xs text-rose-300">{error}</p>}

      <div className="mt-4 flex items-center justify-end gap-2">
        <button
          type="button"
          onClick={() => setForm(baseline)}
          disabled={!dirty || isPending}
          className="rounded-lg px-3 py-1.5 text-xs text-slate-400 hover:text-slate-200 disabled:opacity-50"
        >
          Discard changes
        </button>
        <button
          type="button"
          onClick={handleSave}
          disabled={!dirty || isPending}
          className="rounded-lg bg-primary px-4 py-1.5 text-sm font-medium text-slate-900 hover:bg-primary/90 disabled:opacity-50"
        >
          {isPending ? "Saving..." : "Save"}
        </button>
      </div>
    </section>
  );
}

function ChipGroup({
  label,
  help,
  options,
  selected,
  onToggle,
}: {
  label: string;
  help: string;
  options: readonly string[];
  selected: string[];
  onToggle: (value: string) => void;
}) {
  return (
    <div>
      <div className="mb-1 text-xs font-medium uppercase tracking-wide text-slate-400">
        {label}
      </div>
      <p className="mb-2 text-xs text-slate-500">{help}</p>
      <div className="flex flex-wrap gap-1.5">
        {options.map((opt) => {
          const active = selected.includes(opt);
          return (
            <button
              key={opt}
              type="button"
              onClick={() => onToggle(opt)}
              className={`rounded-full border px-3 py-1 text-xs transition-colors ${
                active
                  ? "border-primary bg-primary/20 text-primary"
                  : "border-slate-700 bg-slate-900/40 text-slate-300 hover:border-slate-600"
              }`}
            >
              {opt}
            </button>
          );
        })}
      </div>
    </div>
  );
}
