"use client";

import { useMemo, useState, useTransition } from "react";
import { useRouter } from "next/navigation";
import { updateAioMetadataConfig } from "@/lib/actions/aiometadata";
import {
  AIO_METADATA_AGE_TIERS,
  AIO_METADATA_LANGUAGES,
  AIO_METADATA_POSTER_PROVIDERS,
} from "@/lib/aiometadata-constants";
import {
  SaveBar,
  Select,
  TextField,
  ToggleRow,
  readBool,
  readNested,
  readNumber,
  readString,
} from "./AioMetadataFormElements";

interface Props {
  profileId: number;
  initialSettings: Record<string, unknown>;
  /** Kids profiles can't pick "No rating filter" — picking one of the tiers
   *  is required, and the edge function re-applies the Kids overlay across
   *  catalogs on save. */
  isKids?: boolean;
}

interface FormState {
  language: string;
  ageRating: string;
  sfw: boolean;
  includeAdult: boolean;
  displayAgeRating: boolean;
  hideUnreleasedDigital: boolean;
  hideUnreleasedDigitalSearch: boolean;
  blurThumbs: boolean;
  showPrefix: boolean;
  showRateMeButton: boolean;
  showMetaProviderAttribution: boolean;
  showDisabledCatalogs: boolean;
  usePosterProxy: boolean;
  enableRatingPostersForLibrary: boolean;
  posterRatingProvider: string;
  castCount: number;
  // artProviders nested object (per media type → logo/poster/background) +
  // englishArtOnly. Surfaced as a single "english art only" toggle for v1;
  // the per-media-type editors come later if anyone asks for them — most
  // users want the bundled defaults ("meta" for everything).
  englishArtOnly: boolean;
}

function buildInitial(settings: Record<string, unknown>): FormState {
  const artProviders = readNested(settings, "artProviders");
  return {
    language: readString(settings, "language", "en-US"),
    ageRating: readString(settings, "ageRating", "None"),
    sfw: readBool(settings, "sfw", true),
    includeAdult: readBool(settings, "includeAdult", false),
    displayAgeRating: readBool(settings, "displayAgeRating", false),
    hideUnreleasedDigital: readBool(settings, "hideUnreleasedDigital", true),
    hideUnreleasedDigitalSearch: readBool(settings, "hideUnreleasedDigitalSearch", false),
    blurThumbs: readBool(settings, "blurThumbs", false),
    showPrefix: readBool(settings, "showPrefix", false),
    showRateMeButton: readBool(settings, "showRateMeButton", false),
    showMetaProviderAttribution: readBool(settings, "showMetaProviderAttribution", false),
    showDisabledCatalogs: readBool(settings, "showDisabledCatalogs", false),
    usePosterProxy: readBool(settings, "usePosterProxy", true),
    enableRatingPostersForLibrary: readBool(settings, "enableRatingPostersForLibrary", true),
    posterRatingProvider: readString(settings, "posterRatingProvider", "rpdb"),
    castCount: readNumber(settings, "castCount", 10),
    englishArtOnly: readBool(artProviders, "englishArtOnly", false),
  };
}

export default function AioMetadataDisplayForm({ profileId, initialSettings, isKids = false }: Props) {
  const router = useRouter();
  const baseline = useMemo(() => buildInitial(initialSettings), [initialSettings]);
  const [form, setForm] = useState<FormState>(baseline);
  const [error, setError] = useState<string | null>(null);
  const [isSaving, startSave] = useTransition();

  // Drop the "None" choice for Kids profiles — picking it would conflict
  // with the Kids overlay (overlay applies regardless of rating). The Kids
  // banner above explains the lock.
  const ageTierOptions = isKids
    ? AIO_METADATA_AGE_TIERS.filter((t) => t.value !== "None")
    : AIO_METADATA_AGE_TIERS;

  const dirty = JSON.stringify(form) !== JSON.stringify(baseline);

  const save = () => {
    setError(null);
    startSave(async () => {
      const patch: Record<string, unknown> = {};
      const keys: Array<keyof FormState> = [
        "language",
        "ageRating",
        "sfw",
        "includeAdult",
        "displayAgeRating",
        "hideUnreleasedDigital",
        "hideUnreleasedDigitalSearch",
        "blurThumbs",
        "showPrefix",
        "showRateMeButton",
        "showMetaProviderAttribution",
        "showDisabledCatalogs",
        "usePosterProxy",
        "enableRatingPostersForLibrary",
        "posterRatingProvider",
        "castCount",
      ];
      for (const k of keys) {
        if (form[k] !== baseline[k]) patch[k] = form[k];
      }

      // englishArtOnly lives nested under artProviders — merge with existing.
      if (form.englishArtOnly !== baseline.englishArtOnly) {
        const existing = readNested(initialSettings, "artProviders");
        patch.artProviders = { ...existing, englishArtOnly: form.englishArtOnly };
      }

      if (Object.keys(patch).length === 0) return;

      const result = await updateAioMetadataConfig({ profileId, settings: patch });
      if (!result.ok) setError(result.error ?? "Save failed");
      else router.refresh();
    });
  };

  return (
    <section className="rounded-2xl border border-slate-700/50 bg-slate-800/40 p-5">
      <h2 className="mb-1 text-lg font-medium">Display</h2>
      <p className="mb-4 text-xs text-slate-400">
        Catalog and metadata presentation, posters, age ratings.
      </p>

      <div className="grid gap-3 md:grid-cols-2">
        <Select
          label="Language"
          value={form.language}
          onChange={(v) => setForm({ ...form, language: v })}
          options={AIO_METADATA_LANGUAGES}
        />
        <Select
          label="Age rating filter"
          helpText={
            isKids
              ? "Kids profile — picking a tier re-applies the catalog overlay and syncs to the TV."
              : "Hides items above this rating. 'No filter' shows everything."
          }
          value={form.ageRating}
          onChange={(v) => setForm({ ...form, ageRating: v })}
          options={ageTierOptions}
        />
        <Select
          label="Poster rating provider"
          value={form.posterRatingProvider}
          onChange={(v) => setForm({ ...form, posterRatingProvider: v })}
          options={AIO_METADATA_POSTER_PROVIDERS}
        />
        <TextField
          label="Cast count"
          helpText="How many cast members to surface per item."
          value={String(form.castCount)}
          onChange={(v) => setForm({ ...form, castCount: Math.max(0, Number.parseInt(v, 10) || 0) })}
          inputType="number"
        />
      </div>

      <h3 className="mt-6 mb-3 text-sm font-medium text-slate-200">Catalog presentation</h3>
      <div className="grid gap-3 md:grid-cols-2">
        <ToggleRow
          title="Safe for work"
          description="Filter adult catalogs."
          checked={form.sfw}
          onChange={(v) => setForm({ ...form, sfw: v })}
        />
        <ToggleRow
          title="Include adult content"
          description="TMDB include_adult — independent of SFW filter."
          checked={form.includeAdult}
          onChange={(v) => setForm({ ...form, includeAdult: v })}
        />
        <ToggleRow
          title="Show age ratings on cards"
          checked={form.displayAgeRating}
          onChange={(v) => setForm({ ...form, displayAgeRating: v })}
        />
        <ToggleRow
          title="Hide unreleased digital titles"
          description="Skip catalog items without a digital release."
          checked={form.hideUnreleasedDigital}
          onChange={(v) => setForm({ ...form, hideUnreleasedDigital: v })}
        />
        <ToggleRow
          title="Hide unreleased in search too"
          checked={form.hideUnreleasedDigitalSearch}
          onChange={(v) => setForm({ ...form, hideUnreleasedDigitalSearch: v })}
        />
        <ToggleRow
          title="Blur previews on cards"
          checked={form.blurThumbs}
          onChange={(v) => setForm({ ...form, blurThumbs: v })}
        />
        <ToggleRow
          title="Show catalog prefix"
          description="Prepend the catalog name to item titles."
          checked={form.showPrefix}
          onChange={(v) => setForm({ ...form, showPrefix: v })}
        />
        <ToggleRow
          title='Show "Rate me" button'
          checked={form.showRateMeButton}
          onChange={(v) => setForm({ ...form, showRateMeButton: v })}
        />
        <ToggleRow
          title="Show metadata provider attribution"
          checked={form.showMetaProviderAttribution}
          onChange={(v) => setForm({ ...form, showMetaProviderAttribution: v })}
        />
        <ToggleRow
          title="Show disabled catalogs in lists"
          checked={form.showDisabledCatalogs}
          onChange={(v) => setForm({ ...form, showDisabledCatalogs: v })}
        />
      </div>

      <h3 className="mt-6 mb-3 text-sm font-medium text-slate-200">Posters & art</h3>
      <div className="grid gap-3 md:grid-cols-2">
        <ToggleRow
          title="Use poster proxy"
          description="Proxy poster requests through upstream — better caching."
          checked={form.usePosterProxy}
          onChange={(v) => setForm({ ...form, usePosterProxy: v })}
        />
        <ToggleRow
          title="Rating posters in library"
          description="Apply RPDB ratings to items in your library."
          checked={form.enableRatingPostersForLibrary}
          onChange={(v) => setForm({ ...form, enableRatingPostersForLibrary: v })}
        />
        <ToggleRow
          title="English art only"
          description="Skip localized logos/posters when available."
          checked={form.englishArtOnly}
          onChange={(v) => setForm({ ...form, englishArtOnly: v })}
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
