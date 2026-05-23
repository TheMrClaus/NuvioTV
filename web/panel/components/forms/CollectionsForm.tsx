"use client";

import { useMemo, useState, useTransition } from "react";
import { Pin, Plus, Trash2 } from "lucide-react";
import { useRouter } from "next/navigation";
import { saveCollections } from "@/lib/actions/relational";
import type {
  Collection,
  CollectionCatalogSource,
  CollectionFolder,
} from "@/lib/data/collections";
import { TILE_SHAPES } from "@/lib/data/collections";
import { SaveBar, type SaveState } from "./SaveBar";
import { SortableList } from "./SortableList";
import { useUnsavedWarning } from "./useUnsavedWarning";

// Flat catalog choice produced from each installed addon's manifest. Same
// fields the TV's CollectionCatalogSource uses, plus display names so the
// picker UI can show something readable.
export interface CatalogChoice {
  addonId: string;
  addonName: string;
  type: string;
  catalogId: string;
  catalogName: string;
}

interface Props {
  profileId: number;
  initial: Collection[];
  expectedUpdatedAt: string | null;
  availableCatalogs: CatalogChoice[];
}

function newId(): string {
  if (typeof crypto !== "undefined" && "randomUUID" in crypto) {
    return crypto.randomUUID();
  }
  return `tmp-${Math.random().toString(36).slice(2)}`;
}

function newFolder(): CollectionFolder {
  return {
    id: newId(),
    title: "New folder",
    tileShape: "SQUARE",
    catalogSources: [],
  };
}

function newCollection(): Collection {
  return {
    id: newId(),
    title: "New collection",
    pinToTop: false,
    focusGlowEnabled: true,
    showAllTab: true,
    folders: [],
  };
}

export default function CollectionsForm({
  profileId,
  initial,
  expectedUpdatedAt,
  availableCatalogs,
}: Props) {
  const router = useRouter();
  const [items, setItems] = useState<Collection[]>(initial);
  const [state, setState] = useState<SaveState>({ kind: "idle" });
  const [isPending, startTransition] = useTransition();
  const [picker, setPicker] = useState<
    | { collectionId: string; folderId: string }
    | null
  >(null);

  const initialSig = useMemo(() => JSON.stringify(initial), [initial]);
  const currentSig = useMemo(() => JSON.stringify(items), [items]);
  const dirty = initialSig !== currentSig;
  useUnsavedWarning(dirty);

  // Resolve addonId+type+catalogId back to a display name from the available
  // catalogs. Falls back to the raw ids when the source's addon isn't
  // currently installed (so the row remains visible and removable instead of
  // silently disappearing).
  const catalogByKey = useMemo(() => {
    const map = new Map<string, CatalogChoice>();
    for (const c of availableCatalogs) {
      map.set(`${c.addonId}|${c.type}|${c.catalogId}`, c);
    }
    return map;
  }, [availableCatalogs]);

  const updateCollection = (id: string, patch: Partial<Collection>) => {
    setItems((prev) => prev.map((c) => (c.id === id ? { ...c, ...patch } : c)));
  };

  const removeCollection = (id: string) => {
    setItems((prev) => prev.filter((c) => c.id !== id));
  };

  const addCollection = () => {
    setItems((prev) => [...prev, newCollection()]);
  };

  const addFolder = (collectionId: string) => {
    setItems((prev) =>
      prev.map((c) =>
        c.id !== collectionId
          ? c
          : { ...c, folders: [...(c.folders ?? []), newFolder()] },
      ),
    );
  };

  const updateFolder = (
    collectionId: string,
    folderId: string,
    patch: Partial<CollectionFolder>,
  ) => {
    setItems((prev) =>
      prev.map((c) =>
        c.id !== collectionId
          ? c
          : {
              ...c,
              folders: (c.folders ?? []).map((f) =>
                f.id === folderId ? { ...f, ...patch } : f,
              ),
            },
      ),
    );
  };

  const removeFolder = (collectionId: string, folderId: string) => {
    setItems((prev) =>
      prev.map((c) =>
        c.id !== collectionId
          ? c
          : { ...c, folders: (c.folders ?? []).filter((f) => f.id !== folderId) },
      ),
    );
  };

  const reorderFolders = (collectionId: string, next: CollectionFolder[]) => {
    setItems((prev) =>
      prev.map((c) => (c.id !== collectionId ? c : { ...c, folders: next })),
    );
  };

  const addCatalogSource = (
    collectionId: string,
    folderId: string,
    source: CollectionCatalogSource,
  ) => {
    setItems((prev) =>
      prev.map((c) => {
        if (c.id !== collectionId) return c;
        return {
          ...c,
          folders: (c.folders ?? []).map((f) => {
            if (f.id !== folderId) return f;
            const existing = f.catalogSources ?? [];
            // Dedupe on (addonId, type, catalogId) — genre is allowed to vary
            // across multiple entries from the same catalog.
            const dup = existing.some(
              (s) =>
                s.addonId === source.addonId &&
                s.type === source.type &&
                s.catalogId === source.catalogId &&
                (s.genre ?? null) === (source.genre ?? null),
            );
            if (dup) return f;
            return { ...f, catalogSources: [...existing, source] };
          }),
        };
      }),
    );
  };

  const removeCatalogSource = (
    collectionId: string,
    folderId: string,
    index: number,
  ) => {
    setItems((prev) =>
      prev.map((c) => {
        if (c.id !== collectionId) return c;
        return {
          ...c,
          folders: (c.folders ?? []).map((f) => {
            if (f.id !== folderId) return f;
            return {
              ...f,
              catalogSources: (f.catalogSources ?? []).filter((_, i) => i !== index),
            };
          }),
        };
      }),
    );
  };

  const handleSave = () => {
    setState({ kind: "saving" });
    startTransition(async () => {
      const result = await saveCollections({
        profileId,
        collectionsJson: items,
        expectedUpdatedAt,
        revalidatePath: `/p/${profileId}/collections`,
      });
      if (result.ok) {
        setState({ kind: "saved", at: Date.now() });
        router.refresh();
        setTimeout(() => setState((s) => (s.kind === "saved" ? { kind: "idle" } : s)), 2000);
      } else if ("conflict" in result && result.conflict) {
        setState({ kind: "conflict" });
        router.refresh();
      } else {
        setState({ kind: "error", message: result.error });
      }
    });
  };

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <p className="text-xs text-slate-500">
          {items.length} collection{items.length === 1 ? "" : "s"}
        </p>
        <button
          type="button"
          onClick={addCollection}
          className="inline-flex items-center gap-1 rounded-lg bg-primary/15 px-3 py-1.5 text-sm font-medium text-primary transition hover:bg-primary/25"
        >
          <Plus className="h-4 w-4" /> New collection
        </button>
      </div>

      {items.length === 0 ? (
        <div className="rounded-2xl border border-slate-700/50 bg-slate-800/40 p-6 text-sm text-slate-400">
          No collections yet. Click <strong>New collection</strong> above to
          create one, then add folders and pick catalog sources from your
          installed addons.
        </div>
      ) : (
        <SortableList
          items={items}
          onReorder={(next) => setItems(next)}
          renderItem={(c, _i, dragHandle) => {
            const folders = c.folders ?? [];
            return (
              <div className="overflow-hidden rounded-2xl border border-slate-700/50 bg-slate-800/40">
                <div className="flex items-start gap-3 border-b border-slate-700/40 p-4">
                  {dragHandle}
                  <div className="flex-1 space-y-3">
                    <input
                      type="text"
                      value={c.title}
                      onChange={(e) => updateCollection(c.id, { title: e.target.value })}
                      className="w-full rounded border border-slate-700 bg-slate-900/40 px-2 py-1 text-base font-semibold text-slate-100 outline-none focus:border-primary"
                      placeholder="Collection title"
                    />
                    <div className="flex flex-wrap items-center gap-3">
                      <label className="flex cursor-pointer items-center gap-2 text-xs text-slate-300">
                        <input
                          type="checkbox"
                          checked={Boolean(c.pinToTop)}
                          onChange={(e) =>
                            updateCollection(c.id, { pinToTop: e.target.checked })
                          }
                          className="h-4 w-4 rounded border-slate-600 bg-slate-900 text-primary focus:ring-primary"
                        />
                        <Pin className="h-3 w-3" /> Pin to top
                      </label>
                      <label className="flex cursor-pointer items-center gap-2 text-xs text-slate-300">
                        <input
                          type="checkbox"
                          checked={Boolean(c.showAllTab ?? true)}
                          onChange={(e) =>
                            updateCollection(c.id, { showAllTab: e.target.checked })
                          }
                          className="h-4 w-4 rounded border-slate-600 bg-slate-900 text-primary focus:ring-primary"
                        />
                        Show &quot;All&quot; tab
                      </label>
                      <label className="flex cursor-pointer items-center gap-2 text-xs text-slate-300">
                        <input
                          type="checkbox"
                          checked={Boolean(c.focusGlowEnabled ?? true)}
                          onChange={(e) =>
                            updateCollection(c.id, { focusGlowEnabled: e.target.checked })
                          }
                          className="h-4 w-4 rounded border-slate-600 bg-slate-900 text-primary focus:ring-primary"
                        />
                        Focus glow
                      </label>
                    </div>
                    <label className="block">
                      <span className="mb-1 block text-xs uppercase tracking-wide text-slate-500">
                        Backdrop image URL
                      </span>
                      <input
                        type="url"
                        placeholder="https://…"
                        value={c.backdropImageUrl ?? ""}
                        onChange={(e) =>
                          updateCollection(c.id, {
                            backdropImageUrl: e.target.value || null,
                          })
                        }
                        className="w-full rounded border border-slate-700 bg-slate-900/40 px-2 py-1 font-mono text-xs text-slate-100 outline-none focus:border-primary"
                      />
                    </label>
                  </div>
                  <button
                    type="button"
                    onClick={() => removeCollection(c.id)}
                    className="flex h-8 w-8 items-center justify-center rounded text-slate-500 hover:text-rose-300"
                    aria-label="Remove collection"
                  >
                    <Trash2 className="h-4 w-4" />
                  </button>
                </div>

                <div className="space-y-2 p-4">
                  <div className="flex items-baseline justify-between">
                    <h3 className="text-xs uppercase tracking-wide text-slate-400">
                      Folders ({folders.length})
                    </h3>
                    <button
                      type="button"
                      onClick={() => addFolder(c.id)}
                      className="inline-flex items-center gap-1 rounded px-2 py-1 text-xs font-medium text-primary hover:bg-primary/10"
                    >
                      <Plus className="h-3 w-3" /> Add folder
                    </button>
                  </div>
                  {folders.length === 0 && (
                    <div className="rounded-lg border border-dashed border-slate-700 bg-slate-900/20 px-3 py-3 text-xs text-slate-500">
                      No folders. Add at least one folder, then pick catalog sources for it.
                    </div>
                  )}
                  {folders.length > 0 && (
                    <SortableList
                      items={folders as (CollectionFolder & { id: string })[]}
                      onReorder={(next) => reorderFolders(c.id, next)}
                      renderItem={(f, _i, fh) => {
                        const sources = f.catalogSources ?? [];
                        return (
                          <div className="rounded-lg border border-slate-700/40 bg-slate-900/40 p-3">
                            <div className="flex items-start gap-3">
                              {fh}
                              <div className="flex-1 space-y-2">
                                <div className="flex gap-2">
                                  <input
                                    type="text"
                                    value={f.coverEmoji ?? ""}
                                    onChange={(e) =>
                                      updateFolder(c.id, f.id, {
                                        coverEmoji: e.target.value || null,
                                      })
                                    }
                                    placeholder="🎬"
                                    maxLength={4}
                                    className="w-16 rounded border border-slate-700 bg-slate-950/40 px-2 py-1 text-center text-base text-slate-100 outline-none focus:border-primary"
                                  />
                                  <input
                                    type="text"
                                    value={f.title}
                                    onChange={(e) =>
                                      updateFolder(c.id, f.id, { title: e.target.value })
                                    }
                                    className="flex-1 rounded border border-slate-700 bg-slate-950/40 px-2 py-1 text-sm text-slate-100 outline-none focus:border-primary"
                                    placeholder="Folder title"
                                  />
                                </div>
                                <div className="flex flex-wrap items-center gap-3">
                                  <label className="flex items-center gap-1 text-xs text-slate-400">
                                    <span>Shape</span>
                                    <select
                                      value={f.tileShape ?? "SQUARE"}
                                      onChange={(e) =>
                                        updateFolder(c.id, f.id, {
                                          tileShape: e.target.value,
                                        })
                                      }
                                      className="rounded border border-slate-700 bg-slate-950/40 px-1 py-0.5 text-xs text-slate-100"
                                    >
                                      {TILE_SHAPES.map((s) => (
                                        <option key={s} value={s}>
                                          {s.toLowerCase()}
                                        </option>
                                      ))}
                                    </select>
                                  </label>
                                  <label className="flex cursor-pointer items-center gap-1 text-xs text-slate-300">
                                    <input
                                      type="checkbox"
                                      checked={Boolean(f.hideTitle)}
                                      onChange={(e) =>
                                        updateFolder(c.id, f.id, {
                                          hideTitle: e.target.checked,
                                        })
                                      }
                                      className="h-3.5 w-3.5 rounded border-slate-600 bg-slate-900 text-primary focus:ring-primary"
                                    />
                                    Hide title
                                  </label>
                                </div>
                                <input
                                  type="url"
                                  placeholder="cover image URL"
                                  value={f.coverImageUrl ?? ""}
                                  onChange={(e) =>
                                    updateFolder(c.id, f.id, {
                                      coverImageUrl: e.target.value || null,
                                    })
                                  }
                                  className="w-full rounded border border-slate-700 bg-slate-950/40 px-2 py-1 font-mono text-xs text-slate-100 outline-none focus:border-primary"
                                />

                                <div className="space-y-1">
                                  <div className="flex items-baseline justify-between">
                                    <span className="text-[10px] uppercase tracking-wide text-slate-500">
                                      Catalog sources ({sources.length})
                                    </span>
                                    <button
                                      type="button"
                                      onClick={() =>
                                        setPicker({ collectionId: c.id, folderId: f.id })
                                      }
                                      className="inline-flex items-center gap-1 rounded px-2 py-0.5 text-xs font-medium text-primary hover:bg-primary/10"
                                    >
                                      <Plus className="h-3 w-3" /> Add source
                                    </button>
                                  </div>
                                  {sources.length === 0 ? (
                                    <div className="rounded border border-dashed border-slate-700 bg-slate-950/30 px-2 py-2 text-[11px] text-slate-500">
                                      No catalog sources. Click <strong>Add source</strong> to
                                      pick one from your installed addons.
                                    </div>
                                  ) : (
                                    <ul className="space-y-1">
                                      {sources.map((s, idx) => {
                                        const key = `${s.addonId}|${s.type}|${s.catalogId}`;
                                        const resolved = catalogByKey.get(key);
                                        return (
                                          <li
                                            key={`${key}|${s.genre ?? ""}|${idx}`}
                                            className="flex items-center gap-2 rounded border border-slate-700/40 bg-slate-950/40 px-2 py-1 text-xs"
                                          >
                                            <span className="min-w-0 flex-1 truncate">
                                              {resolved ? (
                                                <>
                                                  <span className="text-slate-100">
                                                    {resolved.catalogName}
                                                  </span>{" "}
                                                  <span className="text-slate-500">
                                                    · {resolved.type} · {resolved.addonName}
                                                  </span>
                                                </>
                                              ) : (
                                                <span className="font-mono text-amber-200">
                                                  {s.addonId}/{s.type}/{s.catalogId}
                                                  <span className="ml-1 text-[10px] text-amber-400">
                                                    (addon not installed)
                                                  </span>
                                                </span>
                                              )}
                                              {s.genre && (
                                                <span className="ml-1 rounded bg-slate-800 px-1 text-[10px] text-slate-300">
                                                  genre: {s.genre}
                                                </span>
                                              )}
                                            </span>
                                            <button
                                              type="button"
                                              onClick={() => removeCatalogSource(c.id, f.id, idx)}
                                              className="text-slate-500 hover:text-rose-300"
                                              aria-label="Remove source"
                                            >
                                              <Trash2 className="h-3 w-3" />
                                            </button>
                                          </li>
                                        );
                                      })}
                                    </ul>
                                  )}
                                </div>
                              </div>
                              <button
                                type="button"
                                onClick={() => removeFolder(c.id, f.id)}
                                className="flex h-7 w-7 items-center justify-center rounded text-slate-500 hover:text-rose-300"
                                aria-label="Remove folder"
                              >
                                <Trash2 className="h-3.5 w-3.5" />
                              </button>
                            </div>
                          </div>
                        );
                      }}
                    />
                  )}
                </div>
              </div>
            );
          }}
        />
      )}

      <p className="text-xs text-slate-500">
        Image upload to Supabase Storage is still v3 — paste a URL for now.
      </p>

      <SaveBar
        dirty={dirty}
        state={isPending ? { kind: "saving" } : state}
        onSave={handleSave}
        onDiscard={() => {
          setItems(initial);
          setState({ kind: "idle" });
        }}
      />

      {picker && (
        <CatalogPicker
          available={availableCatalogs}
          onCancel={() => setPicker(null)}
          onPick={(source) => {
            addCatalogSource(picker.collectionId, picker.folderId, source);
            setPicker(null);
          }}
        />
      )}
    </div>
  );
}

function CatalogPicker({
  available,
  onCancel,
  onPick,
}: {
  available: CatalogChoice[];
  onCancel: () => void;
  onPick: (source: CollectionCatalogSource) => void;
}) {
  const [filter, setFilter] = useState("");
  const [genre, setGenre] = useState("");
  const lowered = filter.trim().toLowerCase();
  const filtered = lowered.length === 0
    ? available
    : available.filter(
        (c) =>
          c.catalogName.toLowerCase().includes(lowered) ||
          c.addonName.toLowerCase().includes(lowered) ||
          c.type.toLowerCase().includes(lowered) ||
          c.addonId.toLowerCase().includes(lowered),
      );

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/70 p-4">
      <div className="flex max-h-[80vh] w-full max-w-xl flex-col rounded-2xl border border-slate-700/40 bg-slate-900 p-5 shadow-2xl">
        <div className="mb-3 flex items-baseline justify-between">
          <h2 className="text-lg font-semibold text-slate-100">Pick a catalog source</h2>
          <button
            type="button"
            onClick={onCancel}
            className="text-xs text-slate-400 hover:text-slate-200"
          >
            Cancel
          </button>
        </div>
        {available.length === 0 ? (
          <div className="rounded border border-amber-500/30 bg-amber-500/10 p-3 text-xs text-amber-100">
            No catalogs found in any installed addon. Install an addon with
            catalogs from the Addons page first, then come back.
          </div>
        ) : (
          <>
            <input
              type="text"
              placeholder="Filter by catalog, addon, type…"
              value={filter}
              onChange={(e) => setFilter(e.target.value)}
              className="mb-3 w-full rounded border border-slate-700 bg-slate-950/40 px-2 py-1 text-sm text-slate-100 outline-none focus:border-primary"
              autoFocus
            />
            <div className="mb-3 flex items-center gap-2 text-xs text-slate-400">
              <span>Optional genre filter</span>
              <input
                type="text"
                placeholder="(blank = no filter)"
                value={genre}
                onChange={(e) => setGenre(e.target.value)}
                className="flex-1 rounded border border-slate-700 bg-slate-950/40 px-2 py-1 text-xs text-slate-100 outline-none focus:border-primary"
              />
            </div>
            <ul className="min-h-0 flex-1 space-y-1 overflow-y-auto pr-1">
              {filtered.map((c) => {
                const k = `${c.addonId}|${c.type}|${c.catalogId}`;
                return (
                  <li key={k}>
                    <button
                      type="button"
                      onClick={() =>
                        onPick({
                          addonId: c.addonId,
                          type: c.type,
                          catalogId: c.catalogId,
                          genre: genre.trim() === "" ? null : genre.trim(),
                        })
                      }
                      className="block w-full rounded border border-slate-700/40 bg-slate-950/40 px-3 py-2 text-left text-xs hover:border-primary hover:bg-primary/10"
                    >
                      <div className="text-sm font-medium text-slate-100">
                        {c.catalogName}{" "}
                        <span className="text-xs text-slate-500">({c.type})</span>
                      </div>
                      <div className="text-[11px] text-slate-400">{c.addonName}</div>
                    </button>
                  </li>
                );
              })}
              {filtered.length === 0 && (
                <li className="rounded border border-dashed border-slate-700 px-3 py-3 text-center text-xs text-slate-500">
                  No matches.
                </li>
              )}
            </ul>
          </>
        )}
      </div>
    </div>
  );
}
