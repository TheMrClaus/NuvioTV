"use client";

import { useMemo, useState, useTransition } from "react";
import { useRouter } from "next/navigation";
import { updateAioMetadataConfig } from "@/lib/actions/aiometadata";
import {
  AIO_METADATA_PROVIDERS,
  AIO_METADATA_ROUTING,
  providerEnabledSettingsKey,
} from "@/lib/aiometadata-constants";
import {
  SaveBar,
  Select,
  Toggle,
  readBool,
} from "./AioMetadataFormElements";

interface Props {
  profileId: number;
  initialProviders: Record<string, unknown>;
  initialSettings: Record<string, unknown>;
}

interface FormState {
  providerEnabled: Record<string, boolean>;
  routing: Record<string, string>;
  forceAnimeForDetectedImdb: boolean;
}

function buildInitial(
  providers: Record<string, unknown>,
  settings: Record<string, unknown>,
): FormState {
  const providerEnabled: Record<string, boolean> = {};
  for (const p of AIO_METADATA_PROVIDERS) {
    // Default on for TMDB + TVDB so the empty-state matches the bundled template.
    const dflt = p.key === "tmdb" || p.key === "tvdb";
    providerEnabled[p.key] = readBool(settings, providerEnabledSettingsKey(p.key), dflt);
  }
  const routing: Record<string, string> = {};
  for (const r of AIO_METADATA_ROUTING) {
    const v = providers[r.field];
    routing[r.field] = typeof v === "string" ? v : r.choices[0];
  }
  const force = providers.forceAnimeForDetectedImdb;
  return {
    providerEnabled,
    routing,
    forceAnimeForDetectedImdb: typeof force === "boolean" ? force : true,
  };
}

export default function AioMetadataProvidersForm({
  profileId,
  initialProviders,
  initialSettings,
}: Props) {
  const router = useRouter();
  const baseline = useMemo(
    () => buildInitial(initialProviders, initialSettings),
    [initialProviders, initialSettings],
  );
  const [form, setForm] = useState<FormState>(baseline);
  const [error, setError] = useState<string | null>(null);
  const [isSaving, startSave] = useTransition();

  const dirty = JSON.stringify(form) !== JSON.stringify(baseline);

  const save = () => {
    setError(null);
    startSave(async () => {
      const providersPatch: Record<string, unknown> = {};
      for (const r of AIO_METADATA_ROUTING) {
        if (form.routing[r.field] !== baseline.routing[r.field]) {
          providersPatch[r.field] = form.routing[r.field];
        }
      }
      if (form.forceAnimeForDetectedImdb !== baseline.forceAnimeForDetectedImdb) {
        providersPatch.forceAnimeForDetectedImdb = form.forceAnimeForDetectedImdb;
      }

      const settingsPatch: Record<string, unknown> = {};
      for (const p of AIO_METADATA_PROVIDERS) {
        if (form.providerEnabled[p.key] !== baseline.providerEnabled[p.key]) {
          settingsPatch[providerEnabledSettingsKey(p.key)] = form.providerEnabled[p.key];
        }
      }

      const result = await updateAioMetadataConfig({
        profileId,
        providers: Object.keys(providersPatch).length > 0 ? providersPatch : undefined,
        settings: Object.keys(settingsPatch).length > 0 ? settingsPatch : undefined,
      });
      if (!result.ok) setError(result.error ?? "Save failed");
      else router.refresh();
    });
  };

  return (
    <section className="rounded-2xl border border-slate-700/50 bg-slate-800/40 p-5">
      <h2 className="mb-1 text-lg font-medium">Providers</h2>
      <p className="mb-4 text-xs text-slate-400">
        Toggle individual metadata providers and pick which one routes each
        media type.
      </p>

      <div className="space-y-3">
        {AIO_METADATA_PROVIDERS.map((p) => (
          <div
            key={p.key}
            className="flex items-center justify-between rounded-xl border border-slate-700/50 bg-slate-900/40 p-3"
          >
            <div>
              <h3 className="text-sm font-medium text-slate-100">{p.label}</h3>
              <p className="mt-0.5 text-xs text-slate-500">
                {p.requiresApiKey
                  ? "API key required — set it in the API Keys section."
                  : "No API key required."}
              </p>
            </div>
            <Toggle
              checked={form.providerEnabled[p.key] ?? false}
              onChange={(v) =>
                setForm((s) => ({
                  ...s,
                  providerEnabled: { ...s.providerEnabled, [p.key]: v },
                }))
              }
            />
          </div>
        ))}
      </div>

      <h3 className="mt-6 mb-3 text-sm font-medium text-slate-200">Routing</h3>
      <div className="grid gap-3 md:grid-cols-2">
        {AIO_METADATA_ROUTING.map((r) => (
          <Select
            key={r.field}
            label={r.label}
            helpText={r.helpText}
            value={form.routing[r.field] ?? r.choices[0]}
            onChange={(v) =>
              setForm((s) => ({
                ...s,
                routing: { ...s.routing, [r.field]: v },
              }))
            }
            options={r.choices.map((c) => ({ value: c, label: c.toUpperCase() }))}
          />
        ))}
      </div>

      <div className="mt-4 flex items-start justify-between gap-4 rounded-xl border border-slate-700/50 bg-slate-900/40 p-3">
        <div>
          <h3 className="text-sm font-medium text-slate-100">
            Force anime routing for detected IMDb items
          </h3>
          <p className="mt-1 text-xs text-slate-400">
            When an item looks like anime, prefer the anime routing even if it
            has an IMDb id.
          </p>
        </div>
        <Toggle
          checked={form.forceAnimeForDetectedImdb}
          onChange={(v) => setForm((s) => ({ ...s, forceAnimeForDetectedImdb: v }))}
        />
      </div>

      <SaveBar
        dirty={dirty}
        isSaving={isSaving}
        error={error}
        onSave={save}
        onReset={() => setForm(baseline)}
      />
    </section>
  );
}
