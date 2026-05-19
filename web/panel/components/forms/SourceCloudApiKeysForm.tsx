"use client";

import { useMemo, useState, useTransition } from "react";
import { Eye, EyeOff, ExternalLink } from "lucide-react";
import { useRouter } from "next/navigation";
import {
  updateSourceCloudConfig,
  type SourceCloudConfigSummaryResponse,
} from "@/lib/actions/sourcecloud";

interface Props {
  profileId: number;
  summary: SourceCloudConfigSummaryResponse;
}

interface FormState {
  tmdbApiKey: string;
  tmdbAccessToken: string;
  tvdbApiKey: string;
  rpdbApiKey: string;
  animeToshoEnabled: boolean;
  debridioApiKey: string;
}

function summaryToForm(s: SourceCloudConfigSummaryResponse): FormState {
  return {
    tmdbApiKey: s.tmdbApiKey ?? "",
    tmdbAccessToken: s.tmdbAccessToken ?? "",
    tvdbApiKey: s.tvdbApiKey ?? "",
    rpdbApiKey: s.rpdbApiKey ?? "",
    animeToshoEnabled: s.animeToshoEnabled,
    debridioApiKey: s.debridioApiKey ?? "",
  };
}

export default function SourceCloudApiKeysForm({ profileId, summary }: Props) {
  const router = useRouter();
  const baseline = useMemo(() => summaryToForm(summary), [summary]);
  const [form, setForm] = useState<FormState>(baseline);
  const [error, setError] = useState<string | null>(null);
  const [isPending, startTransition] = useTransition();

  // The "default" RPDB key — pre-fill placeholder when the user has nothing saved.
  const rpdbDisplay = form.rpdbApiKey || "";

  const tmdbKeyError =
    form.tmdbApiKey.length > 0 && form.tmdbApiKey.length !== 32
      ? "Should be exactly 32 characters"
      : null;
  const tmdbTokenError =
    form.tmdbAccessToken.length > 0 && form.tmdbAccessToken.length < 200
      ? "Should be 200+ characters"
      : null;
  const hasErrors = tmdbKeyError !== null || tmdbTokenError !== null;

  const dirty = JSON.stringify(form) !== JSON.stringify(baseline);

  const handleSave = () => {
    setError(null);
    startTransition(async () => {
      // Diff each field; send only what changed. Empty string clears (server treats "" as null).
      const diff: Parameters<typeof updateSourceCloudConfig>[0] = {
        profileId,
      };
      if (form.tmdbApiKey !== baseline.tmdbApiKey) diff.tmdbApiKey = form.tmdbApiKey;
      if (form.tmdbAccessToken !== baseline.tmdbAccessToken)
        diff.tmdbAccessToken = form.tmdbAccessToken;
      if (form.tvdbApiKey !== baseline.tvdbApiKey) diff.tvdbApiKey = form.tvdbApiKey;
      if (form.rpdbApiKey !== baseline.rpdbApiKey) diff.rpdbApiKey = form.rpdbApiKey;
      if (form.animeToshoEnabled !== baseline.animeToshoEnabled)
        diff.animeToshoEnabled = form.animeToshoEnabled;
      if (form.debridioApiKey !== baseline.debridioApiKey)
        diff.debridioApiKey = form.debridioApiKey;

      const result = await updateSourceCloudConfig(diff);
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
        <h2 className="mb-1 text-lg font-medium">API keys</h2>
        <p className="text-sm text-slate-400">
          Connect a debrid service first — API keys are stored on the AIOStreams
          user that gets provisioned when you connect.
        </p>
      </section>
    );
  }

  return (
    <section className="rounded-2xl border border-slate-700/50 bg-slate-800/40 p-5">
      <h2 className="mb-1 text-lg font-medium">API keys</h2>
      <p className="mb-4 text-xs text-slate-400">
        These keys live on your private AIOStreams config. Phone, TV, and this
        panel all see the same values.
      </p>

      <div className="space-y-4">
        <SecretField
          label="TMDB API Key"
          value={form.tmdbApiKey}
          onChange={(v) => setForm({ ...form, tmdbApiKey: v })}
          error={tmdbKeyError}
          maxLength={32}
          helpUrl="https://www.themoviedb.org/settings/api"
          helpLabel="Get TMDB key/token"
        />
        <SecretField
          label="TMDB Read Access Token"
          value={form.tmdbAccessToken}
          onChange={(v) => setForm({ ...form, tmdbAccessToken: v })}
          error={tmdbTokenError}
        />
        <SecretField
          label="TVDB API Key"
          value={form.tvdbApiKey}
          onChange={(v) => setForm({ ...form, tvdbApiKey: v })}
          helpUrl="https://www.thetvdb.com/api-information"
          helpLabel="Get TVDB key"
        />
        <SecretField
          label="RPDB API Key"
          value={rpdbDisplay}
          onChange={(v) => setForm({ ...form, rpdbApiKey: v })}
          placeholder="t0-free-rpdb"
        />

        <div className="flex items-start justify-between gap-4 rounded-xl border border-slate-700/50 bg-slate-900/40 p-3">
          <div>
            <h3 className="text-sm font-medium text-slate-100">AnimeTosho</h3>
            <p className="mt-1 text-xs text-slate-400">
              Free anime source addon; no API key required.
            </p>
          </div>
          <Toggle
            checked={form.animeToshoEnabled}
            onChange={(v) => setForm({ ...form, animeToshoEnabled: v })}
          />
        </div>

        <SecretField
          label="Debridio API Key"
          value={form.debridioApiKey}
          onChange={(v) => setForm({ ...form, debridioApiKey: v })}
          helpUrl="https://debridio.com"
          helpLabel="Sign up for Debridio"
        />
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
          disabled={!dirty || hasErrors || isPending}
          className="rounded-lg bg-primary px-4 py-1.5 text-sm font-medium text-slate-900 hover:bg-primary/90 disabled:opacity-50"
        >
          {isPending ? "Saving..." : "Save"}
        </button>
      </div>
    </section>
  );
}

function SecretField({
  label,
  value,
  onChange,
  error,
  maxLength,
  placeholder,
  helpUrl,
  helpLabel,
}: {
  label: string;
  value: string;
  onChange: (v: string) => void;
  error?: string | null;
  maxLength?: number;
  placeholder?: string;
  helpUrl?: string;
  helpLabel?: string;
}) {
  const [reveal, setReveal] = useState(false);
  return (
    <label className="block">
      <span className="mb-1 block text-xs font-medium uppercase tracking-wide text-slate-400">
        {label}
      </span>
      <div className="flex gap-2">
        <input
          type={reveal ? "text" : "password"}
          value={value}
          onChange={(e) => onChange(e.target.value)}
          placeholder={placeholder}
          maxLength={maxLength}
          autoComplete="off"
          spellCheck={false}
          className="flex-1 rounded-lg border border-slate-700 bg-slate-900/40 px-3 py-2 font-mono text-sm text-slate-100 outline-none focus:border-primary"
        />
        <button
          type="button"
          onClick={() => setReveal((v) => !v)}
          className="flex h-10 w-10 items-center justify-center rounded-lg border border-slate-700 text-slate-400 hover:text-slate-100"
          aria-label={reveal ? "Hide" : "Reveal"}
        >
          {reveal ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
        </button>
      </div>
      <div className="mt-1 flex items-center justify-between text-xs">
        {error ? (
          <span className="text-rose-300">{error}</span>
        ) : (
          <span className="text-slate-500">{value.length > 0 && `${value.length} chars`}</span>
        )}
        {helpUrl && (
          <a
            href={helpUrl}
            target="_blank"
            rel="noreferrer"
            className="inline-flex items-center gap-1 text-primary hover:underline"
          >
            {helpLabel} <ExternalLink className="h-3 w-3" />
          </a>
        )}
      </div>
    </label>
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
