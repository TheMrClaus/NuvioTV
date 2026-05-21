"use client";

import { useMemo, useState, useTransition } from "react";
import { useRouter } from "next/navigation";
import { updateAioMetadataConfig } from "@/lib/actions/aiometadata";
import { SaveBar, ToggleRow, readBool } from "./AioMetadataFormElements";

interface Props {
  profileId: number;
  initialSettings: Record<string, unknown>;
}

interface FormState {
  traktWatchTracking: boolean;
  simklWatchTracking: boolean;
  anilistWatchTracking: boolean;
  mdblistWatchTracking: boolean;
  hideWatchedTrakt: boolean;
  hideWatchedMdblist: boolean;
}

function buildInitial(s: Record<string, unknown>): FormState {
  return {
    traktWatchTracking: readBool(s, "traktWatchTracking", false),
    simklWatchTracking: readBool(s, "simklWatchTracking", true),
    anilistWatchTracking: readBool(s, "anilistWatchTracking", true),
    mdblistWatchTracking: readBool(s, "mdblistWatchTracking", true),
    hideWatchedTrakt: readBool(s, "hideWatchedTrakt", true),
    hideWatchedMdblist: readBool(s, "hideWatchedMdblist", true),
  };
}

export default function AioMetadataTrackersForm({ profileId, initialSettings }: Props) {
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
      const keys: Array<keyof FormState> = [
        "traktWatchTracking",
        "simklWatchTracking",
        "anilistWatchTracking",
        "mdblistWatchTracking",
        "hideWatchedTrakt",
        "hideWatchedMdblist",
      ];
      for (const k of keys) if (form[k] !== baseline[k]) patch[k] = form[k];
      if (Object.keys(patch).length === 0) return;

      const result = await updateAioMetadataConfig({ profileId, settings: patch });
      if (!result.ok) setError(result.error ?? "Save failed");
      else router.refresh();
    });
  };

  return (
    <section className="rounded-2xl border border-slate-700/50 bg-slate-800/40 p-5">
      <h2 className="mb-1 text-lg font-medium">Watch trackers</h2>
      <p className="mb-4 text-xs text-slate-400">
        AIOMetadata can mark items as watched from external trackers and hide
        them from catalogs. Tracker auth still happens elsewhere (Trakt OAuth
        on the TV, AniList/Simkl token in the API Keys section).
      </p>

      <div className="grid gap-3 md:grid-cols-2">
        <ToggleRow
          title="Trakt watch tracking"
          checked={form.traktWatchTracking}
          onChange={(v) => setForm({ ...form, traktWatchTracking: v })}
        />
        <ToggleRow
          title="Hide watched (Trakt)"
          checked={form.hideWatchedTrakt}
          onChange={(v) => setForm({ ...form, hideWatchedTrakt: v })}
        />
        <ToggleRow
          title="Simkl watch tracking"
          checked={form.simklWatchTracking}
          onChange={(v) => setForm({ ...form, simklWatchTracking: v })}
        />
        <ToggleRow
          title="AniList watch tracking"
          checked={form.anilistWatchTracking}
          onChange={(v) => setForm({ ...form, anilistWatchTracking: v })}
        />
        <ToggleRow
          title="MDBList watch tracking"
          checked={form.mdblistWatchTracking}
          onChange={(v) => setForm({ ...form, mdblistWatchTracking: v })}
        />
        <ToggleRow
          title="Hide watched (MDBList)"
          checked={form.hideWatchedMdblist}
          onChange={(v) => setForm({ ...form, hideWatchedMdblist: v })}
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
