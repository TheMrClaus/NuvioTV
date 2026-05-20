"use client";

import { useMemo, useState, useTransition } from "react";
import { ShieldAlert, X } from "lucide-react";
import { useRouter } from "next/navigation";
import {
  updateSourceCloudConfig,
  type SourceCloudConfigSummaryResponse,
} from "@/lib/actions/sourcecloud";

interface Props {
  profileId: number;
  summary: SourceCloudConfigSummaryResponse;
}

// Seeded by clicking "Block adult content". Users can edit/remove individually.
const ADULT_KEYWORDS = [
  "XXX", "Adult", "Porn", "NSFW", "18+", "Hardcore", "Erotic", "Explicit",
];

export default function SourceCloudKeywordsForm({ profileId, summary }: Props) {
  const router = useRouter();
  const baseline = useMemo<string[]>(
    () => [...(summary.excludedKeywords ?? [])],
    [summary.excludedKeywords],
  );
  const [keywords, setKeywords] = useState<string[]>(baseline);
  const [input, setInput] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [isPending, startTransition] = useTransition();

  const dirty = JSON.stringify(keywords) !== JSON.stringify(baseline);

  const addKeyword = (raw: string) => {
    const v = raw.trim();
    if (!v) return;
    if (keywords.some((k) => k.toLowerCase() === v.toLowerCase())) return;
    setKeywords([...keywords, v]);
  };

  const removeKeyword = (k: string) => setKeywords(keywords.filter((x) => x !== k));

  const seedAdult = () => {
    const lowered = new Set(keywords.map((k) => k.toLowerCase()));
    const additions = ADULT_KEYWORDS.filter((k) => !lowered.has(k.toLowerCase()));
    if (additions.length > 0) setKeywords([...keywords, ...additions]);
  };

  const handleSave = () => {
    setError(null);
    startTransition(async () => {
      const result = await updateSourceCloudConfig({
        profileId,
        excludedKeywords: keywords,
      });
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
        <h2 className="mb-1 text-lg font-medium">Parental &amp; content filters</h2>
        <p className="text-sm text-slate-400">
          Connect a debrid service first — keyword filters live on your AIOStreams config.
        </p>
      </section>
    );
  }

  return (
    <section className="rounded-2xl border border-slate-700/50 bg-slate-800/40 p-5">
      <h2 className="mb-1 text-lg font-medium">Parental &amp; content filters</h2>
      <p className="mb-4 text-xs text-slate-400">
        Streams whose title or filename contains any of these keywords are removed
        (case-insensitive substring match).
      </p>

      <div className="mb-3 flex items-center gap-2">
        <input
          type="text"
          value={input}
          onChange={(e) => setInput(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === "Enter") {
              e.preventDefault();
              addKeyword(input);
              setInput("");
            }
          }}
          maxLength={100}
          placeholder="Add a keyword and press Enter"
          className="flex-1 rounded-lg border border-slate-700 bg-slate-900/40 px-3 py-2 text-sm text-slate-100 outline-none focus:border-primary"
        />
        <button
          type="button"
          onClick={() => {
            addKeyword(input);
            setInput("");
          }}
          disabled={!input.trim()}
          className="rounded-lg border border-slate-600 px-3 py-1.5 text-xs text-slate-200 hover:bg-slate-700/40 disabled:opacity-50"
        >
          Add
        </button>
      </div>

      <button
        type="button"
        onClick={seedAdult}
        className="mb-4 inline-flex items-center gap-1 rounded-lg border border-rose-700/50 px-3 py-1.5 text-xs text-rose-300 hover:bg-rose-700/20"
      >
        <ShieldAlert className="h-3 w-3" /> Block adult content
      </button>

      {keywords.length === 0 ? (
        <p className="rounded-xl border border-slate-700/50 bg-slate-900/40 p-4 text-sm text-slate-400">
          No keywords blocked.
        </p>
      ) : (
        <div className="flex flex-wrap gap-1.5">
          {keywords.map((k) => (
            <span
              key={k}
              className="inline-flex items-center gap-1 rounded-full border border-slate-700 bg-slate-900/40 px-3 py-1 text-xs text-slate-100"
            >
              {k}
              <button
                type="button"
                onClick={() => removeKeyword(k)}
                className="text-slate-500 hover:text-rose-300"
                aria-label={`Remove ${k}`}
              >
                <X className="h-3 w-3" />
              </button>
            </span>
          ))}
        </div>
      )}

      {error && <p className="mt-3 text-xs text-rose-300">{error}</p>}

      <div className="mt-4 flex items-center justify-end gap-2">
        <button
          type="button"
          onClick={() => setKeywords(baseline)}
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
