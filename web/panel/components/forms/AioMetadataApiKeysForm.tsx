"use client";

import { useMemo, useState, useTransition } from "react";
import { useRouter } from "next/navigation";
import { updateAioMetadataConfig } from "@/lib/actions/aiometadata";
import {
  AIO_METADATA_EXTRA_KEYS,
  AIO_METADATA_PROVIDERS,
} from "@/lib/aiometadata-constants";
import { SaveBar, SecretField } from "./AioMetadataFormElements";

interface Props {
  profileId: number;
  initialApiKeys: Record<string, unknown>;
}

function buildInitial(apiKeys: Record<string, unknown>): Record<string, string> {
  const out: Record<string, string> = {};
  for (const p of AIO_METADATA_PROVIDERS) {
    if (!p.apiKeyField) continue;
    const v = apiKeys[p.apiKeyField];
    out[p.apiKeyField] = typeof v === "string" ? v : "";
  }
  for (const e of AIO_METADATA_EXTRA_KEYS) {
    const v = apiKeys[e.field];
    out[e.field] = typeof v === "string" ? v : "";
  }
  return out;
}

const HELP_URLS: Record<string, { url: string; label: string }> = {
  tmdb: { url: "https://www.themoviedb.org/settings/api", label: "Get TMDB key" },
  tvdb: { url: "https://www.thetvdb.com/api-information", label: "Get TVDB key" },
  fanart: { url: "https://fanart.tv/get-an-api-key/", label: "Get Fanart key" },
  mal: { url: "https://myanimelist.net/apiconfig", label: "Get MAL client id" },
  anilistTokenId: { url: "https://anilist.co/settings/developer", label: "AniList tokens" },
  mdblist: { url: "https://mdblist.com/preferences", label: "Get MDBList key" },
  gemini: { url: "https://aistudio.google.com/apikey", label: "Get Gemini key" },
};

export default function AioMetadataApiKeysForm({ profileId, initialApiKeys }: Props) {
  const router = useRouter();
  const baseline = useMemo(() => buildInitial(initialApiKeys), [initialApiKeys]);
  const [form, setForm] = useState<Record<string, string>>(baseline);
  const [error, setError] = useState<string | null>(null);
  const [isSaving, startSave] = useTransition();

  const dirty = JSON.stringify(form) !== JSON.stringify(baseline);

  const save = () => {
    setError(null);
    startSave(async () => {
      const apiKeysPatch: Record<string, string | null> = {};
      for (const [field, value] of Object.entries(form)) {
        if (value === baseline[field]) continue;
        // Empty string clears the key on the server (applyShallowPatch deletes
        // the entry when value === ""). Send "" rather than null so the empty
        // input renders the same as "no value".
        apiKeysPatch[field] = value;
      }
      if (Object.keys(apiKeysPatch).length === 0) return;

      const result = await updateAioMetadataConfig({ profileId, apiKeys: apiKeysPatch });
      if (!result.ok) setError(result.error ?? "Save failed");
      else router.refresh();
    });
  };

  return (
    <section className="rounded-2xl border border-slate-700/50 bg-slate-800/40 p-5">
      <h2 className="mb-1 text-lg font-medium">API keys</h2>
      <p className="mb-4 text-xs text-slate-400">
        Keys are stored in your private AIOMetadata config. Anything that talks
        to it (TV, phone, this panel) sees the same values.
      </p>

      <div className="space-y-4">
        {AIO_METADATA_PROVIDERS.filter((p) => p.apiKeyField).map((p) => {
          const field = p.apiKeyField!;
          const help = HELP_URLS[field];
          return (
            <SecretField
              key={field}
              label={`${p.label} API key`}
              value={form[field] ?? ""}
              onChange={(v) => setForm((s) => ({ ...s, [field]: v }))}
              helpUrl={help?.url}
              helpLabel={help?.label}
            />
          );
        })}

        <div className="my-2 border-t border-slate-700/60" />

        {AIO_METADATA_EXTRA_KEYS.map((e) => {
          const help = HELP_URLS[e.field];
          return (
            <SecretField
              key={e.field}
              label={e.label}
              value={form[e.field] ?? ""}
              onChange={(v) => setForm((s) => ({ ...s, [e.field]: v }))}
              helpText={e.helpText}
              helpUrl={help?.url}
              helpLabel={help?.label}
              placeholder={e.hasManagedDefault ? "t0-free-rpdb (default)" : undefined}
            />
          );
        })}
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
