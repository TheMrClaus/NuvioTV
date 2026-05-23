"use client";

import { useMemo, useState, useTransition } from "react";
import { useRouter } from "next/navigation";
import { Trash2 } from "lucide-react";
import { saveHomeLayout } from "@/lib/actions/homeLayout";
import type { CatalogRow } from "@/lib/data/homeLayout";
import { SaveBar, type SaveState } from "./SaveBar";
import { SortableList } from "./SortableList";
import { useUnsavedWarning } from "./useUnsavedWarning";

interface OrderedRow {
  // SortableList requires `id: string` — we use the catalog key directly.
  id: string;
  catalog: CatalogRow | null;
  enabled: boolean;
  hero: boolean;
}

interface Props {
  profileId: number;
  catalogs: CatalogRow[];
  layoutSettings: Record<string, unknown>;
  initialOrderKeys: string[];
  initialDisabledKeys: string[];
  initialHeroKeys: string[];
  expectedUpdatedAt: string | null;
}

function signature(rows: OrderedRow[]): string {
  return rows
    .map((r) => `${r.id}|${r.enabled ? "1" : "0"}|${r.hero ? "1" : "0"}`)
    .join("//");
}

export default function HomeLayoutForm({
  profileId,
  catalogs,
  layoutSettings,
  initialOrderKeys,
  initialDisabledKeys,
  initialHeroKeys,
  expectedUpdatedAt,
}: Props) {
  const router = useRouter();

  // Build initial list:
  //   1. Rows whose key appears in initialOrderKeys, in that exact order
  //   2. Remaining known catalogs that weren't in initialOrderKeys, appended
  // Stale keys (in initialOrderKeys but not in any installed addon's manifest)
  // surface with catalog=null so the user can see and remove them rather than
  // having them silently dropped.
  const initial: OrderedRow[] = useMemo(() => {
    const catalogByKey = new Map(catalogs.map((c) => [c.key, c]));
    const disabled = new Set(initialDisabledKeys);
    const hero = new Set(initialHeroKeys);
    const seen = new Set<string>();
    const rows: OrderedRow[] = [];

    for (const key of initialOrderKeys) {
      if (seen.has(key)) continue;
      seen.add(key);
      rows.push({
        id: key,
        catalog: catalogByKey.get(key) ?? null,
        enabled: !disabled.has(key),
        hero: hero.has(key),
      });
    }
    for (const cat of catalogs) {
      if (seen.has(cat.key)) continue;
      seen.add(cat.key);
      rows.push({
        id: cat.key,
        catalog: cat,
        enabled: !disabled.has(cat.key),
        hero: hero.has(cat.key),
      });
    }
    return rows;
  }, [catalogs, initialOrderKeys, initialDisabledKeys, initialHeroKeys]);

  const [items, setItems] = useState<OrderedRow[]>(initial);
  const [state, setState] = useState<SaveState>({ kind: "idle" });
  const [isPending, startTransition] = useTransition();

  const initialSig = useMemo(() => signature(initial), [initial]);
  const currentSig = useMemo(() => signature(items), [items]);
  const dirty = initialSig !== currentSig;
  useUnsavedWarning(dirty);

  const update = (id: string, patch: Partial<OrderedRow>) => {
    setItems((prev) => prev.map((r) => (r.id === id ? { ...r, ...patch } : r)));
  };

  const removeStale = (id: string) => {
    setItems((prev) => prev.filter((r) => r.id !== id));
  };

  const handleSave = () => {
    setState({ kind: "saving" });
    startTransition(async () => {
      const orderKeys = items.map((r) => r.id);
      const disabledKeys = items.filter((r) => !r.enabled).map((r) => r.id);
      const heroKeys = items.filter((r) => r.hero).map((r) => r.id);

      const result = await saveHomeLayout({
        profileId,
        orderKeys,
        disabledKeys,
        heroKeys,
        layoutSettings,
        expectedUpdatedAt,
        revalidatePath: `/p/${profileId}/home`,
      });

      if (result.ok) {
        setState({ kind: "saved", at: Date.now() });
        router.refresh();
        setTimeout(() => {
          setState((s) => (s.kind === "saved" ? { kind: "idle" } : s));
        }, 2000);
      } else if ("conflict" in result && result.conflict) {
        setState({ kind: "conflict" });
      } else {
        setState({ kind: "error", message: result.error });
      }
    });
  };

  const handleDiscard = () => {
    setItems(initial);
    setState({ kind: "idle" });
  };

  return (
    <div className="space-y-6">
      <div className="rounded-2xl border border-slate-700/50 bg-slate-800/40 p-4">
        {items.length === 0 ? (
          <div className="px-2 py-4 text-sm text-slate-400">
            No catalogs found. Install addons with catalogs to populate this list.
          </div>
        ) : (
          <SortableList
            items={items}
            onReorder={setItems}
            renderItem={(item, i, dragHandle) => (
              <div className="flex items-center gap-3 rounded-lg border border-slate-700/40 bg-slate-900/40 p-3">
                {dragHandle}
                <span className="w-6 shrink-0 text-xs text-slate-500">{i + 1}</span>
                <div className="min-w-0 flex-1">
                  {item.catalog ? (
                    <>
                      <div className="truncate text-sm font-medium text-slate-100">
                        {item.catalog.catalogName}{" "}
                        <span className="text-xs text-slate-500">
                          ({item.catalog.catalogType})
                        </span>
                      </div>
                      <div className="truncate text-xs text-slate-400">
                        {item.catalog.addonName}
                      </div>
                    </>
                  ) : (
                    <>
                      <div className="text-sm font-medium text-amber-200">
                        Unknown catalog
                      </div>
                      <div className="truncate font-mono text-xs text-slate-500">
                        {item.id}
                      </div>
                    </>
                  )}
                </div>
                <label className="flex shrink-0 cursor-pointer items-center gap-2 text-xs text-slate-300">
                  <input
                    type="checkbox"
                    checked={item.enabled}
                    onChange={(e) => update(item.id, { enabled: e.target.checked })}
                    className="h-4 w-4 rounded border-slate-600 bg-slate-900 text-primary focus:ring-primary"
                  />
                  show
                </label>
                <label className="flex shrink-0 cursor-pointer items-center gap-2 text-xs text-slate-300">
                  <input
                    type="checkbox"
                    checked={item.hero}
                    onChange={(e) => update(item.id, { hero: e.target.checked })}
                    className="h-4 w-4 rounded border-slate-600 bg-slate-900 text-primary focus:ring-primary"
                  />
                  hero
                </label>
                {!item.catalog && (
                  <button
                    type="button"
                    onClick={() => removeStale(item.id)}
                    className="flex h-8 w-8 shrink-0 items-center justify-center rounded text-slate-500 hover:text-rose-300"
                    aria-label="Remove stale catalog key"
                  >
                    <Trash2 className="h-4 w-4" />
                  </button>
                )}
              </div>
            )}
          />
        )}
      </div>

      <p className="text-xs text-slate-500">
        The four legacy raw-Gson-JSON inputs in Settings → Layout still write to
        the same keys — edits made here and there both end up in
        <code className="ml-1 font-mono">profile_settings.layout_settings</code>.
      </p>

      <SaveBar
        dirty={dirty}
        state={isPending ? { kind: "saving" } : state}
        onSave={handleSave}
        onDiscard={handleDiscard}
      />
    </div>
  );
}
