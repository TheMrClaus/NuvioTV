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

const DEDUP_KEYS = ["filename", "infoHash", "smartDetect"] as const;
const DEDUP_MODES = [
  { value: "single_result", label: "Single result", help: "Keep one stream from the highest-priority service+addon." },
  { value: "per_service", label: "Per service", help: "Keep one stream per service, from the highest-priority addon." },
  { value: "per_addon", label: "Per addon", help: "Keep one stream per addon, from the highest-priority service." },
  { value: "disabled", label: "Disabled", help: "Keep all streams (no dedup for this category)." },
] as const;

const MULTI_GROUP_BEHAVIOURS = [
  { value: "keep_all", label: "Keep all", help: "Never drop duplicates that span multiple groups." },
  { value: "conservative", label: "Conservative", help: "Only drop duplicates within the same service+addon." },
  { value: "aggressive", label: "Aggressive", help: "Drop duplicates across all groups." },
] as const;

interface FormState {
  enabled: boolean;
  multiGroupBehaviour: "keep_all" | "aggressive" | "conservative";
  keys: string[];
  cached: string;
  uncached: string;
}

function summaryToForm(s: SourceCloudConfigSummaryResponse): FormState {
  const d = s.deduplicator;
  return {
    enabled: d?.enabled ?? false,
    multiGroupBehaviour: d?.multiGroupBehaviour ?? "keep_all",
    keys: [...(d?.keys ?? [])],
    cached: d?.cached ?? "single_result",
    uncached: d?.uncached ?? "single_result",
  };
}

export default function SourceCloudDedupForm({ profileId, summary }: Props) {
  const router = useRouter();
  const baseline = useMemo(() => summaryToForm(summary), [summary]);
  const [form, setForm] = useState<FormState>(baseline);
  const [error, setError] = useState<string | null>(null);
  const [isPending, startTransition] = useTransition();

  const dirty = JSON.stringify(form) !== JSON.stringify(baseline);

  const toggleKey = (k: string) => {
    setForm({
      ...form,
      keys: form.keys.includes(k) ? form.keys.filter((x) => x !== k) : [...form.keys, k],
    });
  };

  const handleSave = () => {
    setError(null);
    startTransition(async () => {
      const result = await updateSourceCloudConfig({
        profileId,
        deduplicator: {
          enabled: form.enabled,
          multiGroupBehaviour: form.multiGroupBehaviour,
          keys: form.keys,
          cached: form.cached,
          uncached: form.uncached,
        },
      });
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
        <h2 className="mb-1 text-lg font-medium">Deduplication</h2>
        <p className="text-sm text-slate-400">
          Connect a debrid service first — dedup rules live on your AIOStreams config.
        </p>
      </section>
    );
  }

  return (
    <section className="rounded-2xl border border-slate-700/50 bg-slate-800/40 p-5">
      <h2 className="mb-1 text-lg font-medium">Deduplication</h2>
      <p className="mb-4 text-xs text-slate-400">
        Removes duplicate streams across addons/services using the matching keys
        you choose.
      </p>

      <div className="space-y-5">
        <div className="flex items-start justify-between gap-4 rounded-xl border border-slate-700/50 bg-slate-900/40 p-3">
          <div>
            <h3 className="text-sm font-medium text-slate-100">Enable dedup</h3>
            <p className="mt-0.5 text-xs text-slate-500">
              When off, every result from every addon is kept.
            </p>
          </div>
          <Toggle
            checked={form.enabled}
            onChange={(v) => setForm({ ...form, enabled: v })}
          />
        </div>

        {form.enabled && (
          <>
            <div>
              <div className="mb-1 text-xs font-medium uppercase tracking-wide text-slate-400">
                Matching keys
              </div>
              <p className="mb-2 text-xs text-slate-500">
                What makes two streams &quot;the same&quot;.
              </p>
              <div className="flex flex-wrap gap-1.5">
                {DEDUP_KEYS.map((k) => {
                  const active = form.keys.includes(k);
                  return (
                    <button
                      key={k}
                      type="button"
                      onClick={() => toggleKey(k)}
                      className={`rounded-full border px-3 py-1 text-xs transition-colors ${
                        active
                          ? "border-primary bg-primary/20 text-primary"
                          : "border-slate-700 bg-slate-900/40 text-slate-300 hover:border-slate-600"
                      }`}
                    >
                      {k}
                    </button>
                  );
                })}
              </div>
            </div>

            <RadioGroup
              label="Multi-group behaviour"
              help="When duplicates span multiple addons/services, how aggressive to be."
              value={form.multiGroupBehaviour}
              options={MULTI_GROUP_BEHAVIOURS}
              onChange={(v) =>
                setForm({ ...form, multiGroupBehaviour: v as FormState["multiGroupBehaviour"] })
              }
            />

            <RadioGroup
              label="Cached streams"
              help="Dedup mode for streams cached on debrid."
              value={form.cached}
              options={DEDUP_MODES}
              onChange={(v) => setForm({ ...form, cached: v })}
            />

            <RadioGroup
              label="Uncached streams"
              help="Dedup mode for streams not yet cached."
              value={form.uncached}
              options={DEDUP_MODES}
              onChange={(v) => setForm({ ...form, uncached: v })}
            />
          </>
        )}
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

function RadioGroup({
  label,
  help,
  value,
  options,
  onChange,
}: {
  label: string;
  help: string;
  value: string;
  options: ReadonlyArray<{ value: string; label: string; help: string }>;
  onChange: (v: string) => void;
}) {
  return (
    <div>
      <div className="mb-1 text-xs font-medium uppercase tracking-wide text-slate-400">
        {label}
      </div>
      <p className="mb-2 text-xs text-slate-500">{help}</p>
      <div className="space-y-1.5">
        {options.map((opt) => (
          <label
            key={opt.value}
            className={`flex cursor-pointer items-start gap-2 rounded-lg border px-3 py-2 ${
              value === opt.value
                ? "border-primary bg-primary/10"
                : "border-slate-700 bg-slate-900/40 hover:border-slate-600"
            }`}
          >
            <input
              type="radio"
              checked={value === opt.value}
              onChange={() => onChange(opt.value)}
              className="mt-0.5 accent-primary"
            />
            <div className="min-w-0">
              <div className="text-sm text-slate-100">{opt.label}</div>
              <div className="text-xs text-slate-500">{opt.help}</div>
            </div>
          </label>
        ))}
      </div>
    </div>
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
