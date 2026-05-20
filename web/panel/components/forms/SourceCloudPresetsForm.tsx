"use client";

import { useMemo, useState, useTransition } from "react";
import { ChevronDown, ChevronRight } from "lucide-react";
import { useRouter } from "next/navigation";
import {
  updateSourceCloudConfig,
  type SourceCloudPresetOptionPatch,
  type SourceCloudPresetSummary,
} from "@/lib/actions/sourcecloud";
import { MEDIA_TYPES, MEDIA_TYPE_LABELS } from "@/lib/aiostreams-constants";

interface Props {
  profileId: number;
  provisioned: boolean;
  presets: SourceCloudPresetSummary[];
}

interface PresetState {
  enabled: boolean;
  name: string;
  timeout: number | null;
  mediaTypes: string[];
  useMultipleInstances: boolean;
}

type PresetStateMap = Record<string, PresetState>;

function presetsToStateMap(presets: SourceCloudPresetSummary[]): PresetStateMap {
  const map: PresetStateMap = {};
  for (const p of presets) {
    map[p.instanceId] = {
      enabled: p.enabled,
      name: p.name,
      timeout: p.timeout,
      mediaTypes: [...p.mediaTypes],
      useMultipleInstances: p.useMultipleInstances,
    };
  }
  return map;
}

function statesEqual(a: PresetState, b: PresetState): boolean {
  return (
    a.enabled === b.enabled &&
    a.name === b.name &&
    a.timeout === b.timeout &&
    a.useMultipleInstances === b.useMultipleInstances &&
    JSON.stringify(a.mediaTypes) === JSON.stringify(b.mediaTypes)
  );
}

export default function SourceCloudPresetsForm({ profileId, provisioned, presets }: Props) {
  const router = useRouter();
  const baseline = useMemo(() => presetsToStateMap(presets), [presets]);
  const [state, setState] = useState<PresetStateMap>(baseline);
  const [expanded, setExpanded] = useState<Set<string>>(new Set());
  const [error, setError] = useState<string | null>(null);
  const [isPending, startTransition] = useTransition();

  const dirty = useMemo(() => {
    for (const id of Object.keys(baseline)) {
      if (!statesEqual(state[id] ?? baseline[id], baseline[id])) return true;
    }
    return false;
  }, [state, baseline]);

  const updatePreset = (id: string, patch: Partial<PresetState>) => {
    setState((prev) => ({
      ...prev,
      [id]: { ...(prev[id] ?? baseline[id]), ...patch },
    }));
  };

  const toggleExpand = (id: string) => {
    setExpanded((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  };

  const handleSave = () => {
    setError(null);
    const presetToggles: Record<string, boolean> = {};
    const presetOptions: Record<string, SourceCloudPresetOptionPatch> = {};
    for (const id of Object.keys(baseline)) {
      const next = state[id] ?? baseline[id];
      const base = baseline[id];
      if (next.enabled !== base.enabled) presetToggles[id] = next.enabled;
      const optPatch: SourceCloudPresetOptionPatch = {};
      if (next.name !== base.name) optPatch.name = next.name;
      if (next.timeout !== base.timeout && next.timeout !== null) optPatch.timeout = next.timeout;
      if (JSON.stringify(next.mediaTypes) !== JSON.stringify(base.mediaTypes))
        optPatch.mediaTypes = next.mediaTypes;
      if (next.useMultipleInstances !== base.useMultipleInstances)
        optPatch.useMultipleInstances = next.useMultipleInstances;
      if (Object.keys(optPatch).length > 0) presetOptions[id] = optPatch;
    }
    if (
      Object.keys(presetToggles).length === 0 &&
      Object.keys(presetOptions).length === 0
    ) {
      return;
    }
    startTransition(async () => {
      const payload: Parameters<typeof updateSourceCloudConfig>[0] = { profileId };
      if (Object.keys(presetToggles).length > 0) payload.presetToggles = presetToggles;
      if (Object.keys(presetOptions).length > 0) payload.presetOptions = presetOptions;
      const result = await updateSourceCloudConfig(payload);
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
        Toggle individual presets, or click a row to edit its name, timeout,
        media types, and multi-instance behaviour. Use Advanced → Reset to Debrid
        Starter to restore defaults or add new presets.
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
              state={state[preset.instanceId] ?? baseline[preset.instanceId]}
              expanded={expanded.has(preset.instanceId)}
              onToggleExpand={() => toggleExpand(preset.instanceId)}
              onChange={(patch) => updatePreset(preset.instanceId, patch)}
            />
          ))}
        </div>
      )}

      {error && <p className="mt-3 text-xs text-rose-300">{error}</p>}

      {presets.length > 0 && (
        <div className="mt-4 flex items-center justify-end gap-2">
          <button
            type="button"
            onClick={() => setState(baseline)}
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
  state,
  expanded,
  onToggleExpand,
  onChange,
}: {
  preset: SourceCloudPresetSummary;
  state: PresetState;
  expanded: boolean;
  onToggleExpand: () => void;
  onChange: (patch: Partial<PresetState>) => void;
}) {
  return (
    <div className="rounded-xl border border-slate-700/50 bg-slate-900/40">
      <div className="flex items-center gap-2 p-3">
        <button
          type="button"
          onClick={onToggleExpand}
          className="flex h-7 w-7 items-center justify-center rounded-lg text-slate-400 hover:text-slate-100"
          aria-label={expanded ? "Collapse" : "Expand"}
        >
          {expanded ? <ChevronDown className="h-4 w-4" /> : <ChevronRight className="h-4 w-4" />}
        </button>
        <div className="min-w-0 flex-1">
          <button
            type="button"
            onClick={onToggleExpand}
            className="block w-full text-left"
          >
            <h3 className="truncate text-sm font-medium text-slate-100">{state.name || preset.type}</h3>
            <p className="mt-0.5 text-xs text-slate-500">{preset.type}</p>
          </button>
        </div>
        <Toggle
          checked={state.enabled}
          onChange={(v) => onChange({ enabled: v })}
        />
      </div>

      {expanded && (
        <div className="border-t border-slate-700/50 p-4 space-y-4">
          <label className="block">
            <span className="mb-1 block text-xs font-medium uppercase tracking-wide text-slate-400">
              Display name
            </span>
            <input
              type="text"
              value={state.name}
              onChange={(e) => onChange({ name: e.target.value })}
              maxLength={200}
              className="w-full rounded-lg border border-slate-700 bg-slate-900/40 px-3 py-2 text-sm text-slate-100 outline-none focus:border-primary"
            />
          </label>

          <label className="block">
            <span className="mb-1 block text-xs font-medium uppercase tracking-wide text-slate-400">
              Timeout (ms)
            </span>
            <input
              type="number"
              min={1000}
              max={300000}
              step={1000}
              value={state.timeout ?? ""}
              placeholder="Uses default"
              onChange={(e) => {
                const raw = e.target.value;
                onChange({ timeout: raw === "" ? null : Number(raw) });
              }}
              className="w-full rounded-lg border border-slate-700 bg-slate-900/40 px-3 py-2 text-sm text-slate-100 outline-none focus:border-primary"
            />
            <p className="mt-1 text-xs text-slate-500">
              How long to wait for this addon to respond before giving up.
            </p>
          </label>

          <div>
            <div className="mb-1 text-xs font-medium uppercase tracking-wide text-slate-400">
              Media types
            </div>
            <p className="mb-2 text-xs text-slate-500">
              Restrict this preset to specific content types. Leave empty to run
              for everything.
            </p>
            <div className="flex flex-wrap gap-1.5">
              {MEDIA_TYPES.map((mt) => {
                const active = state.mediaTypes.includes(mt);
                return (
                  <button
                    key={mt}
                    type="button"
                    onClick={() =>
                      onChange({
                        mediaTypes: active
                          ? state.mediaTypes.filter((v) => v !== mt)
                          : [...state.mediaTypes, mt],
                      })
                    }
                    className={`rounded-full border px-3 py-1 text-xs transition-colors ${
                      active
                        ? "border-primary bg-primary/20 text-primary"
                        : "border-slate-700 bg-slate-900/40 text-slate-300 hover:border-slate-600"
                    }`}
                  >
                    {MEDIA_TYPE_LABELS[mt]}
                  </button>
                );
              })}
            </div>
          </div>

          <div className="flex items-start justify-between gap-4">
            <div>
              <h4 className="text-sm font-medium text-slate-100">Use multiple instances</h4>
              <p className="mt-0.5 text-xs text-slate-500">
                Run a separate instance per debrid service instead of one shared.
              </p>
            </div>
            <Toggle
              checked={state.useMultipleInstances}
              onChange={(v) => onChange({ useMultipleInstances: v })}
            />
          </div>
        </div>
      )}
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
