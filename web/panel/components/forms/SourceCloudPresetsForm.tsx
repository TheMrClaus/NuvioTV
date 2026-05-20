"use client";

import { useMemo, useState, useTransition } from "react";
import { useRouter } from "next/navigation";
import {
  updateSourceCloudConfig,
  type SourceCloudPresetSummary,
} from "@/lib/actions/sourcecloud";

interface Props {
  profileId: number;
  provisioned: boolean;
  presets: SourceCloudPresetSummary[];
}

type ToggleMap = Record<string, boolean>;

function presetsToToggleMap(presets: SourceCloudPresetSummary[]): ToggleMap {
  const map: ToggleMap = {};
  for (const p of presets) map[p.instanceId] = p.enabled;
  return map;
}

export default function SourceCloudPresetsForm({ profileId, provisioned, presets }: Props) {
  const router = useRouter();
  const baseline = useMemo(() => presetsToToggleMap(presets), [presets]);
  const [toggles, setToggles] = useState<ToggleMap>(baseline);
  const [error, setError] = useState<string | null>(null);
  const [isPending, startTransition] = useTransition();

  const dirty = useMemo(() => {
    for (const id of Object.keys(baseline)) {
      if ((toggles[id] ?? baseline[id]) !== baseline[id]) return true;
    }
    return false;
  }, [toggles, baseline]);

  const handleSave = () => {
    setError(null);
    const diff: ToggleMap = {};
    for (const id of Object.keys(baseline)) {
      const next = toggles[id] ?? baseline[id];
      if (next !== baseline[id]) diff[id] = next;
    }
    if (Object.keys(diff).length === 0) return;
    startTransition(async () => {
      const result = await updateSourceCloudConfig({ profileId, presetToggles: diff });
      if (result.ok) {
        router.refresh();
      } else {
        setError(result.error ?? "Save failed");
      }
    });
  };

  if (!provisioned) {
    return (
      <section className="rounded-2xl border border-slate-700/50 bg-slate-800/40 p-5">
        <h2 className="mb-1 text-lg font-medium">Addons (presets)</h2>
        <p className="text-sm text-slate-400">
          Connect a debrid service first — your AIOStreams presets get provisioned
          on first connect.
        </p>
      </section>
    );
  }

  return (
    <section className="rounded-2xl border border-slate-700/50 bg-slate-800/40 p-5">
      <h2 className="mb-1 text-lg font-medium">Addons (presets)</h2>
      <p className="mb-4 text-xs text-slate-400">
        Each row is one AIOStreams preset. Toggle off the ones you don&apos;t want
        contributing streams. Use Advanced → Reset to Debrid Starter to restore
        defaults or add new presets.
      </p>

      {presets.length === 0 ? (
        <p className="rounded-xl border border-slate-700/50 bg-slate-900/40 p-4 text-sm text-slate-400">
          No presets configured yet. Use Advanced → Reset to Debrid Starter to
          add the default set.
        </p>
      ) : (
        <div className="space-y-2">
          {presets.map((preset) => (
            <PresetRow
              key={preset.instanceId}
              preset={preset}
              enabled={toggles[preset.instanceId] ?? preset.enabled}
              onChange={(v) =>
                setToggles((prev) => ({ ...prev, [preset.instanceId]: v }))
              }
            />
          ))}
        </div>
      )}

      {error && <p className="mt-3 text-xs text-rose-300">{error}</p>}

      {presets.length > 0 && (
        <div className="mt-4 flex items-center justify-end gap-2">
          <button
            type="button"
            onClick={() => setToggles(baseline)}
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
      )}
    </section>
  );
}

function PresetRow({
  preset,
  enabled,
  onChange,
}: {
  preset: SourceCloudPresetSummary;
  enabled: boolean;
  onChange: (v: boolean) => void;
}) {
  return (
    <div className="flex items-center justify-between gap-4 rounded-xl border border-slate-700/50 bg-slate-900/40 p-3">
      <div className="min-w-0">
        <h3 className="truncate text-sm font-medium text-slate-100">{preset.name}</h3>
        <p className="mt-0.5 text-xs text-slate-500">{preset.type}</p>
      </div>
      <Toggle checked={enabled} onChange={onChange} />
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
