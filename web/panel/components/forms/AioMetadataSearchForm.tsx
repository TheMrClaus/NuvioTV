"use client";

import { useMemo, useState, useTransition } from "react";
import { useRouter } from "next/navigation";
import { updateAioMetadataConfig } from "@/lib/actions/aiometadata";
import { ArrowDown, ArrowUp } from "lucide-react";
import {
  SaveBar,
  Select,
  ToggleRow,
  readBool,
  readNested,
} from "./AioMetadataFormElements";

interface Props {
  profileId: number;
  initialSettings: Record<string, unknown>;
}

// Default engine + media-type sets — matches the bundled aiometadata_default_config.json.
// Upstream may add more engines later; forms preserve unknown keys on save.
const KNOWN_ENGINES = [
  "tmdb.search",
  "tvdb.search",
  "gemini.search",
  "tvmaze.search",
  "mal.search.movie",
  "mal.search.series",
  "tmdb.people.search",
  "people_search_movie",
  "people_search_series",
  "tvdb.collections.search",
  "kitsu.search.series",
  "kitsu.search.movie",
];

const RATING_POSTER_BUCKETS = [
  "movie",
  "series",
  "gemini.search",
  "anime_series",
  "anime_movie",
];

const PROVIDER_MEDIA_TYPES = [
  { field: "movie", label: "Movies", choices: ["tmdb.search", "tvdb.search"] },
  { field: "series", label: "Series", choices: ["tvdb.search", "tmdb.search"] },
  { field: "anime_movie", label: "Anime movies", choices: ["kitsu.search.movie", "mal.search.movie"] },
  { field: "anime_series", label: "Anime series", choices: ["kitsu.search.series", "mal.search.series"] },
];

interface FormState {
  enabled: boolean;
  aiEnabled: boolean;
  providers: Record<string, string>;
  searchOrder: string[];
  engineEnabled: Record<string, boolean>;
  engineRatingPosters: Record<string, boolean>;
}

function buildInitial(settings: Record<string, unknown>): FormState {
  const search = readNested(settings, "search");
  const providers = readNested(search, "providers");
  const engineEnabled = readNested(search, "engineEnabled");
  const engineRatingPosters = readNested(search, "engineRatingPosters");
  const order = Array.isArray(search.searchOrder)
    ? (search.searchOrder as unknown[]).filter((v): v is string => typeof v === "string")
    : ["movie", "series", "gemini.search"];
  const providersOut: Record<string, string> = {};
  for (const p of PROVIDER_MEDIA_TYPES) {
    const v = providers[p.field];
    providersOut[p.field] = typeof v === "string" ? v : p.choices[0];
  }
  const engineOut: Record<string, boolean> = {};
  for (const e of KNOWN_ENGINES) {
    engineOut[e] = readBool(engineEnabled, e, e.startsWith("tmdb") || e.startsWith("tvdb") || e === "gemini.search");
  }
  const ratingOut: Record<string, boolean> = {};
  for (const k of RATING_POSTER_BUCKETS) {
    ratingOut[k] = readBool(engineRatingPosters, k, true);
  }
  return {
    enabled: readBool(search, "enabled", true),
    aiEnabled: readBool(search, "ai_enabled", true),
    providers: providersOut,
    searchOrder: order,
    engineEnabled: engineOut,
    engineRatingPosters: ratingOut,
  };
}

export default function AioMetadataSearchForm({ profileId, initialSettings }: Props) {
  const router = useRouter();
  const baseline = useMemo(() => buildInitial(initialSettings), [initialSettings]);
  const [form, setForm] = useState<FormState>(baseline);
  const [error, setError] = useState<string | null>(null);
  const [isSaving, startSave] = useTransition();

  const dirty = JSON.stringify(form) !== JSON.stringify(baseline);

  const save = () => {
    setError(null);
    startSave(async () => {
      // Merge with the existing upstream `search` object so unknown keys
      // (engines we don't surface yet, future fields) survive the round-trip.
      const existing = readNested(initialSettings, "search");
      const existingProviders = readNested(existing, "providers");
      const existingEnabled = readNested(existing, "engineEnabled");
      const existingRating = readNested(existing, "engineRatingPosters");

      const merged: Record<string, unknown> = {
        ...existing,
        enabled: form.enabled,
        ai_enabled: form.aiEnabled,
        providers: { ...existingProviders, ...form.providers },
        searchOrder: form.searchOrder,
        engineEnabled: { ...existingEnabled, ...form.engineEnabled },
        engineRatingPosters: { ...existingRating, ...form.engineRatingPosters },
      };

      const result = await updateAioMetadataConfig({
        profileId,
        settings: { search: merged },
      });
      if (!result.ok) setError(result.error ?? "Save failed");
      else router.refresh();
    });
  };

  const moveOrder = (index: number, dir: -1 | 1) => {
    const next = [...form.searchOrder];
    const target = index + dir;
    if (target < 0 || target >= next.length) return;
    [next[index], next[target]] = [next[target], next[index]];
    setForm({ ...form, searchOrder: next });
  };

  return (
    <section className="rounded-2xl border border-slate-700/50 bg-slate-800/40 p-5">
      <h2 className="mb-1 text-lg font-medium">Search</h2>
      <p className="mb-4 text-xs text-slate-400">
        Which engines power the in-app search and the order results are merged in.
      </p>

      <div className="grid gap-3 md:grid-cols-2">
        <ToggleRow
          title="Search enabled"
          checked={form.enabled}
          onChange={(v) => setForm({ ...form, enabled: v })}
        />
        <ToggleRow
          title="AI search (Gemini)"
          description="Augments search with semantic results from gemini.search."
          checked={form.aiEnabled}
          onChange={(v) => setForm({ ...form, aiEnabled: v })}
        />
      </div>

      <h3 className="mt-6 mb-3 text-sm font-medium text-slate-200">Default provider per media type</h3>
      <div className="grid gap-3 md:grid-cols-2">
        {PROVIDER_MEDIA_TYPES.map((p) => (
          <Select
            key={p.field}
            label={p.label}
            value={form.providers[p.field] ?? p.choices[0]}
            onChange={(v) =>
              setForm({ ...form, providers: { ...form.providers, [p.field]: v } })
            }
            options={p.choices.map((c) => ({ value: c, label: c }))}
          />
        ))}
      </div>

      <h3 className="mt-6 mb-3 text-sm font-medium text-slate-200">Search order</h3>
      <ul className="space-y-2">
        {form.searchOrder.map((entry, i) => (
          <li
            key={`${entry}-${i}`}
            className="flex items-center justify-between rounded-xl border border-slate-700/50 bg-slate-900/40 px-3 py-2 text-sm text-slate-200"
          >
            <span className="font-mono text-xs">{entry}</span>
            <span className="flex items-center gap-1">
              <button
                type="button"
                onClick={() => moveOrder(i, -1)}
                disabled={i === 0}
                className="rounded p-1 hover:bg-slate-800 disabled:opacity-30"
                aria-label="Move up"
              >
                <ArrowUp className="h-3.5 w-3.5" />
              </button>
              <button
                type="button"
                onClick={() => moveOrder(i, 1)}
                disabled={i === form.searchOrder.length - 1}
                className="rounded p-1 hover:bg-slate-800 disabled:opacity-30"
                aria-label="Move down"
              >
                <ArrowDown className="h-3.5 w-3.5" />
              </button>
            </span>
          </li>
        ))}
      </ul>

      <h3 className="mt-6 mb-3 text-sm font-medium text-slate-200">Engines</h3>
      <div className="grid gap-3 md:grid-cols-2">
        {KNOWN_ENGINES.map((e) => (
          <ToggleRow
            key={e}
            title={e}
            checked={form.engineEnabled[e] ?? false}
            onChange={(v) =>
              setForm({ ...form, engineEnabled: { ...form.engineEnabled, [e]: v } })
            }
          />
        ))}
      </div>

      <h3 className="mt-6 mb-3 text-sm font-medium text-slate-200">Rating posters per bucket</h3>
      <div className="grid gap-3 md:grid-cols-2">
        {RATING_POSTER_BUCKETS.map((b) => (
          <ToggleRow
            key={b}
            title={b}
            checked={form.engineRatingPosters[b] ?? false}
            onChange={(v) =>
              setForm({
                ...form,
                engineRatingPosters: { ...form.engineRatingPosters, [b]: v },
              })
            }
          />
        ))}
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
