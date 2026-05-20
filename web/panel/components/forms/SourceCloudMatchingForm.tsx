"use client";

import { useMemo, useState, useTransition } from "react";
import { useRouter } from "next/navigation";
import {
  updateSourceCloudConfig,
  type SourceCloudConfigSummaryResponse,
} from "@/lib/actions/sourcecloud";

interface Props {
  profileId: number;
  summary: SourceCloudConfigSummaryResponse;
}

interface FormState {
  title: {
    enabled: boolean;
    mode: "exact" | "contains";
    similarityThreshold: number;
  };
  year: {
    enabled: boolean;
    tolerance: number;
    strict: boolean;
  };
  digital: {
    enabled: boolean;
    tolerance: number;
  };
}

function summaryToForm(s: SourceCloudConfigSummaryResponse): FormState {
  return {
    title: {
      enabled: s.titleMatching?.enabled ?? false,
      mode: s.titleMatching?.mode ?? "contains",
      similarityThreshold: s.titleMatching?.similarityThreshold ?? 0.8,
    },
    year: {
      enabled: s.yearMatching?.enabled ?? false,
      tolerance: s.yearMatching?.tolerance ?? 1,
      strict: s.yearMatching?.strict ?? false,
    },
    digital: {
      enabled: s.digitalReleaseFilter?.enabled ?? false,
      tolerance: s.digitalReleaseFilter?.tolerance ?? 30,
    },
  };
}

export default function SourceCloudMatchingForm({ profileId, summary }: Props) {
  const router = useRouter();
  const baseline = useMemo(() => summaryToForm(summary), [summary]);
  const [form, setForm] = useState<FormState>(baseline);
  const [error, setError] = useState<string | null>(null);
  const [isPending, startTransition] = useTransition();

  const dirty = JSON.stringify(form) !== JSON.stringify(baseline);
  const hasTmdb = !!(summary.tmdbApiKey || summary.tmdbAccessToken);

  const handleSave = () => {
    setError(null);
    startTransition(async () => {
      const diff: Parameters<typeof updateSourceCloudConfig>[0] = { profileId };
      if (JSON.stringify(form.title) !== JSON.stringify(baseline.title)) {
        diff.titleMatching = {
          enabled: form.title.enabled,
          mode: form.title.mode,
          similarityThreshold: form.title.similarityThreshold,
        };
      }
      if (JSON.stringify(form.year) !== JSON.stringify(baseline.year)) {
        diff.yearMatching = {
          enabled: form.year.enabled,
          tolerance: form.year.tolerance,
          strict: form.year.strict,
        };
      }
      if (JSON.stringify(form.digital) !== JSON.stringify(baseline.digital)) {
        diff.digitalReleaseFilter = {
          enabled: form.digital.enabled,
          tolerance: form.digital.tolerance,
        };
      }
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
        <h2 className="mb-1 text-lg font-medium">Matching rules</h2>
        <p className="text-sm text-slate-400">
          Connect a debrid service first — matching rules live on your AIOStreams config.
        </p>
      </section>
    );
  }

  return (
    <section className="rounded-2xl border border-slate-700/50 bg-slate-800/40 p-5">
      <h2 className="mb-1 text-lg font-medium">Matching rules</h2>
      <p className="mb-4 text-xs text-slate-400">
        Filter streams based on how well their parsed title/year/release date
        matches the metadata. These features require a TMDB API key
        {hasTmdb ? "" : " — add one in API keys above"}.
      </p>

      <div className="space-y-5">
        <div className="rounded-xl border border-slate-700/50 bg-slate-900/40 p-4">
          <div className="flex items-start justify-between gap-4">
            <div>
              <h3 className="text-sm font-medium text-slate-100">Title matching</h3>
              <p className="mt-0.5 text-xs text-slate-500">
                Drop streams whose parsed title doesn&apos;t match the requested title.
              </p>
            </div>
            <Toggle
              checked={form.title.enabled}
              onChange={(v) => setForm({ ...form, title: { ...form.title, enabled: v } })}
            />
          </div>
          {form.title.enabled && (
            <div className="mt-3 space-y-3">
              <div>
                <div className="mb-1 text-xs font-medium uppercase tracking-wide text-slate-400">
                  Mode
                </div>
                <div className="flex gap-2">
                  {(["exact", "contains"] as const).map((m) => (
                    <button
                      key={m}
                      type="button"
                      onClick={() => setForm({ ...form, title: { ...form.title, mode: m } })}
                      className={`rounded-full border px-3 py-1 text-xs ${
                        form.title.mode === m
                          ? "border-primary bg-primary/20 text-primary"
                          : "border-slate-700 bg-slate-900/40 text-slate-300 hover:border-slate-600"
                      }`}
                    >
                      {m}
                    </button>
                  ))}
                </div>
              </div>
              <label className="block">
                <span className="mb-1 block text-xs font-medium uppercase tracking-wide text-slate-400">
                  Similarity threshold: {form.title.similarityThreshold.toFixed(2)}
                </span>
                <input
                  type="range"
                  min={0}
                  max={1}
                  step={0.05}
                  value={form.title.similarityThreshold}
                  onChange={(e) =>
                    setForm({
                      ...form,
                      title: { ...form.title, similarityThreshold: Number(e.target.value) },
                    })
                  }
                  className="w-full"
                />
                <p className="mt-1 text-xs text-slate-500">
                  Higher = stricter. 0.8 is a sensible default.
                </p>
              </label>
            </div>
          )}
        </div>

        <div className="rounded-xl border border-slate-700/50 bg-slate-900/40 p-4">
          <div className="flex items-start justify-between gap-4">
            <div>
              <h3 className="text-sm font-medium text-slate-100">Year matching</h3>
              <p className="mt-0.5 text-xs text-slate-500">
                Drop streams whose release year is too far from the metadata year.
              </p>
            </div>
            <Toggle
              checked={form.year.enabled}
              onChange={(v) => setForm({ ...form, year: { ...form.year, enabled: v } })}
            />
          </div>
          {form.year.enabled && (
            <div className="mt-3 space-y-3">
              <label className="block">
                <span className="mb-1 block text-xs font-medium uppercase tracking-wide text-slate-400">
                  Tolerance (years): {form.year.tolerance}
                </span>
                <input
                  type="range"
                  min={0}
                  max={10}
                  step={1}
                  value={form.year.tolerance}
                  onChange={(e) =>
                    setForm({
                      ...form,
                      year: { ...form.year, tolerance: Number(e.target.value) },
                    })
                  }
                  className="w-full"
                />
              </label>
              <div className="flex items-start justify-between gap-4">
                <div>
                  <h4 className="text-sm font-medium text-slate-100">Strict mode</h4>
                  <p className="mt-0.5 text-xs text-slate-500">
                    Reject streams with no detectable year instead of passing them through.
                  </p>
                </div>
                <Toggle
                  checked={form.year.strict}
                  onChange={(v) => setForm({ ...form, year: { ...form.year, strict: v } })}
                />
              </div>
            </div>
          )}
        </div>

        <div className="rounded-xl border border-slate-700/50 bg-slate-900/40 p-4">
          <div className="flex items-start justify-between gap-4">
            <div>
              <h3 className="text-sm font-medium text-slate-100">Digital release filter</h3>
              <p className="mt-0.5 text-xs text-slate-500">
                Hide movies whose digital release date is too recent (often CAM-quality).
              </p>
            </div>
            <Toggle
              checked={form.digital.enabled}
              onChange={(v) => setForm({ ...form, digital: { ...form.digital, enabled: v } })}
            />
          </div>
          {form.digital.enabled && (
            <div className="mt-3">
              <label className="block">
                <span className="mb-1 block text-xs font-medium uppercase tracking-wide text-slate-400">
                  Tolerance (days): {form.digital.tolerance}
                </span>
                <input
                  type="range"
                  min={0}
                  max={120}
                  step={5}
                  value={form.digital.tolerance}
                  onChange={(e) =>
                    setForm({
                      ...form,
                      digital: { ...form.digital, tolerance: Number(e.target.value) },
                    })
                  }
                  className="w-full"
                />
                <p className="mt-1 text-xs text-slate-500">
                  Streams before this many days past the digital release are hidden.
                </p>
              </label>
            </div>
          )}
        </div>
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

function Toggle({
  checked,
  onChange,
}: {
  checked: boolean;
  onChange: (v: boolean) => void;
}) {
  return (
    <button
      type="button"
      role="switch"
      aria-checked={checked}
      onClick={() => onChange(!checked)}
      className={`relative inline-flex h-6 w-10 shrink-0 cursor-pointer rounded-full border border-slate-700 transition-colors ${
        checked ? "bg-primary" : "bg-slate-700"
      }`}
    >
      <span
        className={`inline-block h-5 w-5 transform rounded-full bg-slate-100 transition-transform ${
          checked ? "translate-x-4" : "translate-x-0"
        }`}
      />
    </button>
  );
}
