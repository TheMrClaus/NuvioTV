"use client";

import { useMemo, useState } from "react";
import { X } from "lucide-react";
import {
  TMDB_CATALOG_TYPES,
  TMDB_PROVIDER_JOIN_MODES,
  TMDB_SORT_OPTIONS,
  TMDB_WATCH_PROVIDERS,
  TMDB_WATCH_REGIONS,
  type TmdbGenre,
  type TmdbProvider,
} from "@/lib/aiometadata-constants";
import {
  defaultFormStateForType,
  envelopeToFormState,
  genresFor,
  readDiscoverEnvelope,
  type DiscoverFormState,
} from "@/lib/aiometadata-discover";
import { Select, TextField, ToggleRow } from "./AioMetadataFormElements";

interface Props {
  initial: Record<string, unknown> | null; // null = new catalog
  onCancel: () => void;
  onSave: (formState: DiscoverFormState) => void;
}

function initialFormState(catalog: Record<string, unknown> | null): DiscoverFormState {
  if (!catalog) return defaultFormStateForType("movie");
  const env = readDiscoverEnvelope(catalog);
  if (!env) {
    // Built-in catalog (no discover envelope). Pre-fill from top-level
    // name/type and let the user convert it into a custom catalog by saving.
    const type =
      String(catalog.type ?? "movie") === "series" ? "series" : "movie";
    return {
      ...defaultFormStateForType(type as "movie" | "series"),
      catalogName: String(catalog.name ?? ""),
    };
  }
  return envelopeToFormState(env);
}

export default function AioMetadataCatalogEditor({ initial, onCancel, onSave }: Props) {
  const [form, setForm] = useState<DiscoverFormState>(() => initialFormState(initial));
  const genres = useMemo(() => genresFor(form.catalogType), [form.catalogType]);

  const toggleProvider = (p: TmdbProvider) => {
    const exists = form.watchProviders.some((wp) => wp.id === p.id);
    setForm({
      ...form,
      watchProviders: exists
        ? form.watchProviders.filter((wp) => wp.id !== p.id)
        : [...form.watchProviders, p],
    });
  };

  const toggleGenre = (g: TmdbGenre) => {
    const exists = form.includeGenres.some((ig) => ig.id === g.id);
    setForm({
      ...form,
      includeGenres: exists
        ? form.includeGenres.filter((ig) => ig.id !== g.id)
        : [...form.includeGenres, g],
    });
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/70 p-4">
      <div className="relative max-h-[90vh] w-full max-w-3xl overflow-y-auto rounded-2xl border border-slate-700/60 bg-slate-900 p-6 shadow-2xl">
        <button
          type="button"
          onClick={onCancel}
          aria-label="Close"
          className="absolute right-4 top-4 rounded p-1 text-slate-500 hover:bg-slate-800 hover:text-slate-200"
        >
          <X className="h-4 w-4" />
        </button>
        <h2 className="mb-1 text-lg font-medium text-slate-100">
          {initial ? "Edit catalog" : "New custom catalog"}
        </h2>
        <p className="mb-5 text-xs text-slate-400">
          TMDB Discover params. Custom catalogs let you compose a feed using
          watch providers, genres, and sort/filter options. Built-in catalogs
          (Trakt recommendations, TMDB Top, etc.) become custom when you save
          edits here.
        </p>

        <div className="grid gap-3 md:grid-cols-2">
          <TextField
            label="Catalog name"
            value={form.catalogName}
            onChange={(v) => setForm({ ...form, catalogName: v })}
          />
          <Select
            label="Catalog type"
            value={form.catalogType}
            onChange={(v) =>
              setForm({
                ...form,
                catalogType: v === "series" ? "series" : "movie",
                // Wipe genres — they're media-type-specific.
                includeGenres: [],
              })
            }
            options={TMDB_CATALOG_TYPES.map((t) => ({ value: t.value, label: t.label }))}
          />
          <Select
            label="Sort by"
            value={form.sortBy}
            onChange={(v) => setForm({ ...form, sortBy: v })}
            options={TMDB_SORT_OPTIONS}
          />
          <TextField
            label="Minimum vote count"
            inputType="number"
            value={String(form.voteCountMin)}
            onChange={(v) =>
              setForm({ ...form, voteCountMin: Math.max(0, Number.parseInt(v, 10) || 0) })
            }
          />
        </div>

        <div className="mt-3 grid gap-3 md:grid-cols-2">
          <ToggleRow
            title="Include adult content"
            description="Lets through items flagged as adult by TMDB."
            checked={form.includeAdult}
            onChange={(v) => setForm({ ...form, includeAdult: v })}
          />
          <ToggleRow
            title="Released only"
            description="Restrict to already-released titles / live series."
            checked={form.releasedOnly}
            onChange={(v) => setForm({ ...form, releasedOnly: v })}
          />
        </div>

        <h3 className="mt-6 mb-2 text-sm font-medium text-slate-200">Watch providers</h3>
        <p className="mb-2 text-xs text-slate-500">
          Restrict the catalog to titles available on the selected providers in
          the selected region. Leave empty to skip.
        </p>
        <div className="grid gap-3 md:grid-cols-[1fr_1fr]">
          <Select
            label="Watch region"
            value={form.watchRegion}
            onChange={(v) => setForm({ ...form, watchRegion: v })}
            options={TMDB_WATCH_REGIONS}
          />
          <Select
            label="Join mode"
            value={form.providerJoinMode}
            onChange={(v) =>
              setForm({ ...form, providerJoinMode: v === "and" ? "and" : "or" })
            }
            options={TMDB_PROVIDER_JOIN_MODES}
          />
        </div>
        <ChipMultiSelect
          options={TMDB_WATCH_PROVIDERS.map((p) => ({ key: String(p.id), label: p.label }))}
          selected={form.watchProviders.map((p) => String(p.id))}
          onToggle={(key) => {
            const provider = TMDB_WATCH_PROVIDERS.find((p) => String(p.id) === key);
            if (provider) toggleProvider(provider);
          }}
        />

        <h3 className="mt-6 mb-2 text-sm font-medium text-slate-200">Genres</h3>
        <ChipMultiSelect
          options={genres.map((g) => ({ key: String(g.id), label: g.label }))}
          selected={form.includeGenres.map((g) => String(g.id))}
          onToggle={(key) => {
            const genre = genres.find((g) => String(g.id) === key);
            if (genre) toggleGenre(genre);
          }}
        />

        <h3 className="mt-6 mb-2 text-sm font-medium text-slate-200">Rating floor</h3>
        <div className="grid gap-3 md:grid-cols-[auto_1fr]">
          <ToggleRow
            title="Set rating floor"
            description="Hide titles rated below the chosen TMDB score."
            checked={!!form.voteAverageRange}
            onChange={(v) =>
              setForm({ ...form, voteAverageRange: v ? [5, 10] : undefined })
            }
          />
          {form.voteAverageRange && (
            <TextField
              label="Minimum vote average (0–10)"
              inputType="number"
              value={String(form.voteAverageRange[0])}
              onChange={(v) => {
                const min = Math.min(10, Math.max(0, Number.parseFloat(v) || 0));
                setForm({ ...form, voteAverageRange: [min, form.voteAverageRange![1]] });
              }}
            />
          )}
        </div>

        <div className="mt-6 flex items-center justify-end gap-2 border-t border-slate-800 pt-4">
          <button
            type="button"
            onClick={onCancel}
            className="rounded-lg border border-slate-700 px-3 py-1.5 text-xs text-slate-300 hover:bg-slate-800"
          >
            Cancel
          </button>
          <button
            type="button"
            onClick={() => onSave(form)}
            className="rounded-lg bg-emerald-500/90 px-3 py-1.5 text-xs font-medium text-white hover:bg-emerald-500"
          >
            {initial ? "Apply changes" : "Create catalog"}
          </button>
        </div>
      </div>
    </div>
  );
}

function ChipMultiSelect({
  options,
  selected,
  onToggle,
}: {
  options: Array<{ key: string; label: string }>;
  selected: string[];
  onToggle: (key: string) => void;
}) {
  const selectedSet = useMemo(() => new Set(selected), [selected]);
  return (
    <div className="mt-2 flex flex-wrap gap-2">
      {options.map((o) => {
        const on = selectedSet.has(o.key);
        return (
          <button
            key={o.key}
            type="button"
            onClick={() => onToggle(o.key)}
            className={`rounded-full border px-2.5 py-1 text-xs transition ${
              on
                ? "border-emerald-400 bg-emerald-500/15 text-emerald-200"
                : "border-slate-700 text-slate-300 hover:bg-slate-800"
            }`}
          >
            {o.label}
          </button>
        );
      })}
    </div>
  );
}
