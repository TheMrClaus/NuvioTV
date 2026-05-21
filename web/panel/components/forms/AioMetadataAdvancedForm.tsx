"use client";

import { useMemo, useState, useTransition } from "react";
import { useRouter } from "next/navigation";
import { updateAioMetadataConfig } from "@/lib/actions/aiometadata";
import { AIO_METADATA_TVDB_SEASON_TYPES } from "@/lib/aiometadata-constants";
import {
  SaveBar,
  Select,
  ToggleRow,
  readBool,
  readNested,
  readString,
} from "./AioMetadataFormElements";

interface Props {
  profileId: number;
  initialSettings: Record<string, unknown>;
}

interface FormState {
  tvdbSeasonType: string;
  // tmdb.*
  tmdbScrapeImdb: boolean;
  tmdbForceLatinCastNames: boolean;
  // mal.*
  malSkipRecap: boolean;
  malSkipFiller: boolean;
  malAllowEpisodeMarking: boolean;
  malUseImdbIdForCatalogAndSearch: boolean;
  // catalogSetupComplete — surfaced so a user who's customised catalogs
  // outside the panel can be sure setup-mode won't snap back on next load.
  catalogSetupComplete: boolean;
}

function buildInitial(s: Record<string, unknown>): FormState {
  const tmdb = readNested(s, "tmdb");
  const mal = readNested(s, "mal");
  return {
    tvdbSeasonType: readString(s, "tvdbSeasonType", "default"),
    tmdbScrapeImdb: readBool(tmdb, "scrapeImdb", true),
    tmdbForceLatinCastNames: readBool(tmdb, "forceLatinCastNames", false),
    malSkipRecap: readBool(mal, "skipRecap", false),
    malSkipFiller: readBool(mal, "skipFiller", false),
    malAllowEpisodeMarking: readBool(mal, "allowEpisodeMarking", true),
    malUseImdbIdForCatalogAndSearch: readBool(mal, "useImdbIdForCatalogAndSearch", true),
    catalogSetupComplete: readBool(s, "catalogSetupComplete", true),
  };
}

export default function AioMetadataAdvancedForm({ profileId, initialSettings }: Props) {
  const router = useRouter();
  const baseline = useMemo(() => buildInitial(initialSettings), [initialSettings]);
  const [form, setForm] = useState<FormState>(baseline);
  const [error, setError] = useState<string | null>(null);
  const [isSaving, startSave] = useTransition();

  const dirty = JSON.stringify(form) !== JSON.stringify(baseline);

  const save = () => {
    setError(null);
    startSave(async () => {
      const patch: Record<string, unknown> = {};
      if (form.tvdbSeasonType !== baseline.tvdbSeasonType) patch.tvdbSeasonType = form.tvdbSeasonType;
      if (form.catalogSetupComplete !== baseline.catalogSetupComplete) {
        patch.catalogSetupComplete = form.catalogSetupComplete;
      }

      // tmdb / mal nested — merge with existing to preserve unknown keys.
      const tmdbChanged =
        form.tmdbScrapeImdb !== baseline.tmdbScrapeImdb ||
        form.tmdbForceLatinCastNames !== baseline.tmdbForceLatinCastNames;
      if (tmdbChanged) {
        const existing = readNested(initialSettings, "tmdb");
        patch.tmdb = {
          ...existing,
          scrapeImdb: form.tmdbScrapeImdb,
          forceLatinCastNames: form.tmdbForceLatinCastNames,
        };
      }

      const malChanged =
        form.malSkipRecap !== baseline.malSkipRecap ||
        form.malSkipFiller !== baseline.malSkipFiller ||
        form.malAllowEpisodeMarking !== baseline.malAllowEpisodeMarking ||
        form.malUseImdbIdForCatalogAndSearch !== baseline.malUseImdbIdForCatalogAndSearch;
      if (malChanged) {
        const existing = readNested(initialSettings, "mal");
        patch.mal = {
          ...existing,
          skipRecap: form.malSkipRecap,
          skipFiller: form.malSkipFiller,
          allowEpisodeMarking: form.malAllowEpisodeMarking,
          useImdbIdForCatalogAndSearch: form.malUseImdbIdForCatalogAndSearch,
        };
      }

      if (Object.keys(patch).length === 0) return;

      const result = await updateAioMetadataConfig({ profileId, settings: patch });
      if (!result.ok) setError(result.error ?? "Save failed");
      else router.refresh();
    });
  };

  return (
    <section className="rounded-2xl border border-slate-700/50 bg-slate-800/40 p-5">
      <h2 className="mb-1 text-lg font-medium">Advanced</h2>
      <p className="mb-4 text-xs text-slate-400">
        Per-provider knobs and TVDB season strategy. Leave defaults unless you
        know what you&apos;re changing.
      </p>

      <div className="grid gap-3 md:grid-cols-2">
        <Select
          label="TVDB season type"
          value={form.tvdbSeasonType}
          onChange={(v) => setForm({ ...form, tvdbSeasonType: v })}
          options={AIO_METADATA_TVDB_SEASON_TYPES}
        />
      </div>

      <h3 className="mt-6 mb-3 text-sm font-medium text-slate-200">TMDB</h3>
      <div className="grid gap-3 md:grid-cols-2">
        <ToggleRow
          title="Scrape IMDb data"
          description="Fetch additional fields from IMDb when TMDB doesn't provide them."
          checked={form.tmdbScrapeImdb}
          onChange={(v) => setForm({ ...form, tmdbScrapeImdb: v })}
        />
        <ToggleRow
          title="Force Latin cast names"
          description="Romanize cast names regardless of source language."
          checked={form.tmdbForceLatinCastNames}
          onChange={(v) => setForm({ ...form, tmdbForceLatinCastNames: v })}
        />
      </div>

      <h3 className="mt-6 mb-3 text-sm font-medium text-slate-200">MyAnimeList</h3>
      <div className="grid gap-3 md:grid-cols-2">
        <ToggleRow
          title="Skip recap episodes"
          checked={form.malSkipRecap}
          onChange={(v) => setForm({ ...form, malSkipRecap: v })}
        />
        <ToggleRow
          title="Skip filler episodes"
          checked={form.malSkipFiller}
          onChange={(v) => setForm({ ...form, malSkipFiller: v })}
        />
        <ToggleRow
          title="Allow per-episode marking"
          checked={form.malAllowEpisodeMarking}
          onChange={(v) => setForm({ ...form, malAllowEpisodeMarking: v })}
        />
        <ToggleRow
          title="Use IMDb id for anime catalog/search"
          checked={form.malUseImdbIdForCatalogAndSearch}
          onChange={(v) => setForm({ ...form, malUseImdbIdForCatalogAndSearch: v })}
        />
      </div>

      <h3 className="mt-6 mb-3 text-sm font-medium text-slate-200">Setup state</h3>
      <div className="grid gap-3 md:grid-cols-2">
        <ToggleRow
          title="Catalog setup complete"
          description="If off, upstream may prompt to re-run catalog setup on first load."
          checked={form.catalogSetupComplete}
          onChange={(v) => setForm({ ...form, catalogSetupComplete: v })}
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
