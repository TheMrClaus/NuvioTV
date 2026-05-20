"use client";

import { useMemo, useState, useTransition } from "react";
import { ArrowDown, ArrowUp, ArrowUpDown, X } from "lucide-react";
import { useRouter } from "next/navigation";
import {
  updateSourceCloudConfig,
  type SourceCloudConfigSummaryResponse,
  type SourceCloudSortCriterion,
} from "@/lib/actions/sourcecloud";
import { SORT_CRITERIA, SORT_CRITERION_LABELS } from "@/lib/aiostreams-constants";

interface Props {
  profileId: number;
  summary: SourceCloudConfigSummaryResponse;
}

export default function SourceCloudSortForm({ profileId, summary }: Props) {
  const router = useRouter();
  const baseline = useMemo<SourceCloudSortCriterion[]>(
    () => (summary.sortCriteria ?? []).map((c) => ({ ...c })),
    [summary.sortCriteria],
  );
  const [criteria, setCriteria] = useState<SourceCloudSortCriterion[]>(baseline);
  const [addKey, setAddKey] = useState<string>("");
  const [error, setError] = useState<string | null>(null);
  const [isPending, startTransition] = useTransition();

  const dirty = JSON.stringify(criteria) !== JSON.stringify(baseline);
  const usedKeys = new Set(criteria.map((c) => c.key));
  const availableToAdd = SORT_CRITERIA.filter((k) => !usedKeys.has(k));

  const move = (index: number, delta: number) => {
    const next = [...criteria];
    const target = index + delta;
    if (target < 0 || target >= next.length) return;
    [next[index], next[target]] = [next[target], next[index]];
    setCriteria(next);
  };

  const toggleDirection = (index: number) => {
    const next = [...criteria];
    next[index] = {
      ...next[index],
      direction: next[index].direction === "asc" ? "desc" : "asc",
    };
    setCriteria(next);
  };

  const remove = (index: number) => {
    setCriteria(criteria.filter((_, i) => i !== index));
  };

  const add = () => {
    if (!addKey) return;
    setCriteria([...criteria, { key: addKey, direction: "desc" }]);
    setAddKey("");
  };

  const handleSave = () => {
    setError(null);
    startTransition(async () => {
      const result = await updateSourceCloudConfig({
        profileId,
        sortCriteria: criteria,
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
        <h2 className="mb-1 text-lg font-medium">Sort order</h2>
        <p className="text-sm text-slate-400">
          Connect a debrid service first — sort rules live on your AIOStreams config.
        </p>
      </section>
    );
  }

  return (
    <section className="rounded-2xl border border-slate-700/50 bg-slate-800/40 p-5">
      <h2 className="mb-1 text-lg font-medium">Sort order</h2>
      <p className="mb-4 text-xs text-slate-400">
        Streams are sorted top-to-bottom using these rules in order. First rule
        wins ties get broken by the next rule, and so on.
      </p>

      {criteria.length === 0 ? (
        <p className="rounded-xl border border-slate-700/50 bg-slate-900/40 p-4 text-sm text-slate-400">
          No sort criteria. Add one below to control stream ordering.
        </p>
      ) : (
        <ol className="space-y-2">
          {criteria.map((c, index) => (
            <li
              key={c.key}
              className="flex items-center gap-2 rounded-xl border border-slate-700/50 bg-slate-900/40 p-3"
            >
              <span className="w-5 text-center text-xs text-slate-500">{index + 1}</span>
              <span className="flex-1 truncate text-sm text-slate-100">
                {SORT_CRITERION_LABELS[c.key as keyof typeof SORT_CRITERION_LABELS] ?? c.key}
              </span>
              <button
                type="button"
                onClick={() => toggleDirection(index)}
                className="flex items-center gap-1 rounded-lg border border-slate-700 px-2 py-1 text-xs text-slate-200 hover:bg-slate-700/40"
                title={c.direction === "desc" ? "Descending (highest first)" : "Ascending (lowest first)"}
              >
                {c.direction === "desc" ? (
                  <ArrowDown className="h-3 w-3" />
                ) : (
                  <ArrowUp className="h-3 w-3" />
                )}
                {c.direction}
              </button>
              <button
                type="button"
                onClick={() => move(index, -1)}
                disabled={index === 0}
                className="flex h-7 w-7 items-center justify-center rounded-lg border border-slate-700 text-slate-400 hover:text-slate-100 disabled:opacity-30"
                title="Move up"
              >
                <ArrowUp className="h-3 w-3" />
              </button>
              <button
                type="button"
                onClick={() => move(index, 1)}
                disabled={index === criteria.length - 1}
                className="flex h-7 w-7 items-center justify-center rounded-lg border border-slate-700 text-slate-400 hover:text-slate-100 disabled:opacity-30"
                title="Move down"
              >
                <ArrowDown className="h-3 w-3" />
              </button>
              <button
                type="button"
                onClick={() => remove(index)}
                className="flex h-7 w-7 items-center justify-center rounded-lg border border-slate-700 text-rose-300 hover:bg-rose-700/20"
                title="Remove"
              >
                <X className="h-3 w-3" />
              </button>
            </li>
          ))}
        </ol>
      )}

      {availableToAdd.length > 0 && (
        <div className="mt-3 flex items-center gap-2">
          <ArrowUpDown className="h-4 w-4 text-slate-500" />
          <select
            value={addKey}
            onChange={(e) => setAddKey(e.target.value)}
            className="flex-1 rounded-lg border border-slate-700 bg-slate-900/40 px-3 py-2 text-sm text-slate-100 outline-none focus:border-primary"
          >
            <option value="">Add criterion…</option>
            {availableToAdd.map((k) => (
              <option key={k} value={k}>
                {SORT_CRITERION_LABELS[k]}
              </option>
            ))}
          </select>
          <button
            type="button"
            onClick={add}
            disabled={!addKey}
            className="rounded-lg border border-slate-600 px-3 py-1.5 text-xs text-slate-200 hover:bg-slate-700/40 disabled:opacity-50"
          >
            Add
          </button>
        </div>
      )}

      {error && <p className="mt-3 text-xs text-rose-300">{error}</p>}

      <div className="mt-4 flex items-center justify-end gap-2">
        <button
          type="button"
          onClick={() => setCriteria(baseline)}
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
