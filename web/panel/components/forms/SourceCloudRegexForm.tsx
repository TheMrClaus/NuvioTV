"use client";

import { useMemo, useState, useTransition } from "react";
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
  excluded: string;
  included: string;
  required: string;
}

function summaryToForm(s: SourceCloudConfigSummaryResponse): FormState {
  return {
    excluded: (s.excludedRegexPatterns ?? []).join("\n"),
    included: (s.includedRegexPatterns ?? []).join("\n"),
    required: (s.requiredRegexPatterns ?? []).join("\n"),
  };
}

function textToArray(s: string): string[] {
  return s
    .split("\n")
    .map((line) => line.trim())
    .filter((line) => line.length > 0);
}

function validateRegexes(patterns: string[]): string | null {
  for (const p of patterns) {
    try {
      new RegExp(p);
    } catch (e) {
      return `Invalid regex: ${p} — ${(e as Error).message}`;
    }
  }
  return null;
}

export default function SourceCloudRegexForm({ profileId, summary }: Props) {
  const router = useRouter();
  const baseline = useMemo(() => summaryToForm(summary), [summary]);
  const [form, setForm] = useState<FormState>(baseline);
  const [error, setError] = useState<string | null>(null);
  const [isPending, startTransition] = useTransition();

  const dirty = JSON.stringify(form) !== JSON.stringify(baseline);

  const handleSave = () => {
    setError(null);
    const excluded = textToArray(form.excluded);
    const included = textToArray(form.included);
    const required = textToArray(form.required);
    const invalid = validateRegexes([...excluded, ...included, ...required]);
    if (invalid) {
      setError(invalid);
      return;
    }
    startTransition(async () => {
      const diff: Parameters<typeof updateSourceCloudConfig>[0] = { profileId };
      if (form.excluded !== baseline.excluded) diff.excludedRegexPatterns = excluded;
      if (form.included !== baseline.included) diff.includedRegexPatterns = included;
      if (form.required !== baseline.required) diff.requiredRegexPatterns = required;
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
        <h2 className="mb-1 text-lg font-medium">Regex patterns</h2>
        <p className="text-sm text-slate-400">
          Connect a debrid service first — regex patterns live on your AIOStreams config.
        </p>
      </section>
    );
  }

  return (
    <section className="rounded-2xl border border-slate-700/50 bg-slate-800/40 p-5">
      <h2 className="mb-1 text-lg font-medium">Regex patterns</h2>
      <p className="mb-4 text-xs text-slate-400">
        One pattern per line. Patterns are matched against the stream title/filename
        using JavaScript regex syntax. Validated locally before saving.
      </p>

      <div className="space-y-4">
        <RegexBlock
          label="Excluded"
          help="Streams matching any of these patterns are removed."
          value={form.excluded}
          onChange={(v) => setForm({ ...form, excluded: v })}
        />
        <RegexBlock
          label="Required"
          help="Streams that don't match any of these patterns are removed (all-or-nothing filter)."
          value={form.required}
          onChange={(v) => setForm({ ...form, required: v })}
        />
        <RegexBlock
          label="Included"
          help="Streams matching any of these are kept; non-matching are removed (lenient filter)."
          value={form.included}
          onChange={(v) => setForm({ ...form, included: v })}
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
          disabled={!dirty || isPending}
          className="rounded-lg bg-primary px-4 py-1.5 text-sm font-medium text-slate-900 hover:bg-primary/90 disabled:opacity-50"
        >
          {isPending ? "Saving..." : "Save"}
        </button>
      </div>
    </section>
  );
}

function RegexBlock({
  label,
  help,
  value,
  onChange,
}: {
  label: string;
  help: string;
  value: string;
  onChange: (v: string) => void;
}) {
  const lineCount = value.split("\n").filter((l) => l.trim().length > 0).length;
  return (
    <div>
      <div className="mb-1 flex items-center justify-between">
        <span className="text-xs font-medium uppercase tracking-wide text-slate-400">
          {label}
        </span>
        <span className="text-xs text-slate-500">{lineCount} pattern{lineCount === 1 ? "" : "s"}</span>
      </div>
      <p className="mb-2 text-xs text-slate-500">{help}</p>
      <textarea
        value={value}
        onChange={(e) => onChange(e.target.value)}
        rows={4}
        spellCheck={false}
        placeholder={"^.*CAM.*$\n(?i)hdcam"}
        className="w-full rounded-lg border border-slate-700 bg-slate-900/40 px-3 py-2 font-mono text-xs text-slate-100 outline-none focus:border-primary"
      />
    </div>
  );
}
