"use client";

import { useMemo, useState, useTransition } from "react";
import { useRouter } from "next/navigation";
import { updateAioMetadataConfig } from "@/lib/actions/aiometadata";
import { Plus, Pencil, Trash2 } from "lucide-react";
import { SaveBar, TextField, Toggle } from "./AioMetadataFormElements";
import { SortableList } from "./SortableList";
import AioMetadataCatalogEditor from "./AioMetadataCatalogEditor";
import {
  applyEditToCatalog,
  makeNewDiscoverCatalog,
  type DiscoverFormState,
} from "@/lib/aiometadata-discover";

interface Props {
  profileId: number;
  initialCatalogs: Array<Record<string, unknown>>;
}

interface CatalogRow {
  /** Stable synthetic key for drag-drop / list keys. Upstream ids are not unique
   *  across types (e.g. "tmdb.top" exists for both movie and series). */
  id: string;
  index: number; // position in the original list
  catalogId: string;
  type: string;
  source: string;
  name: string;
  enabled: boolean;
  showInHome: boolean;
  isCustom: boolean;
  raw: Record<string, unknown>;
}

function readField<T = unknown>(o: Record<string, unknown>, key: string, fallback: T): T {
  const v = o[key];
  return v === undefined || v === null ? fallback : (v as T);
}

function buildRows(catalogs: Array<Record<string, unknown>>): CatalogRow[] {
  return catalogs.map((raw, index) => {
    const catalogId = readField<string>(raw, "id", "");
    const type = readField<string>(raw, "type", "");
    const isCustom =
      raw.metadata && typeof raw.metadata === "object" && !Array.isArray(raw.metadata)
        ? Boolean((raw.metadata as Record<string, unknown>).discover)
        : false;
    return {
      id: `${catalogId}__${type}__${index}`,
      index,
      catalogId,
      type,
      source: readField<string>(raw, "source", ""),
      name: readField<string>(raw, "name", ""),
      enabled: readField<boolean>(raw, "enabled", true),
      showInHome: readField<boolean>(raw, "showInHome", true),
      isCustom,
      raw,
    };
  });
}

function rowsToCatalogs(rows: CatalogRow[]): Array<Record<string, unknown>> {
  return rows.map((row) => ({
    ...row.raw,
    name: row.name,
    enabled: row.enabled,
    showInHome: row.showInHome,
  }));
}

const TYPE_FILTERS = [
  { value: "all", label: "All" },
  { value: "movie", label: "Movies" },
  { value: "series", label: "Series" },
  { value: "anime", label: "Anime" },
];

export default function AioMetadataCatalogsForm({ profileId, initialCatalogs }: Props) {
  const router = useRouter();
  const baseline = useMemo(() => buildRows(initialCatalogs), [initialCatalogs]);
  const [rows, setRows] = useState<CatalogRow[]>(baseline);
  const [typeFilter, setTypeFilter] = useState<string>("all");
  const [query, setQuery] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [isSaving, startSave] = useTransition();
  const [editorTarget, setEditorTarget] = useState<{ mode: "new" | "edit"; rowId?: string } | null>(null);

  const dirty = JSON.stringify(rows) !== JSON.stringify(baseline);

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase();
    return rows.filter((r) => {
      if (typeFilter !== "all" && r.type !== typeFilter) return false;
      if (q.length > 0) {
        const hay = `${r.name} ${r.catalogId} ${r.source}`.toLowerCase();
        if (!hay.includes(q)) return false;
      }
      return true;
    });
  }, [rows, typeFilter, query]);

  const onReorderFiltered = (next: CatalogRow[]) => {
    // The sortable shows the filtered list. To preserve catalogs hidden by
    // the filter, we splice the reordered subset back into the full rows
    // array at the positions the filter occupied.
    const filteredIds = new Set(filtered.map((r) => r.id));
    const remaining = rows.filter((r) => !filteredIds.has(r.id));
    // Walk the original ordering and emit either the next reordered entry
    // (when we hit a position that *was* in the filter) or the next non-filtered.
    const result: CatalogRow[] = [];
    let f = 0;
    let h = 0;
    for (const r of rows) {
      if (filteredIds.has(r.id)) {
        result.push(next[f++]);
      } else {
        result.push(remaining[h++]);
      }
    }
    setRows(result);
  };

  const patchRow = (id: string, patch: Partial<CatalogRow>) => {
    setRows((rs) => rs.map((r) => (r.id === id ? { ...r, ...patch } : r)));
  };

  const save = () => {
    setError(null);
    startSave(async () => {
      const result = await updateAioMetadataConfig({
        profileId,
        catalogs: rowsToCatalogs(rows),
      });
      if (!result.ok) setError(result.error ?? "Save failed");
      else router.refresh();
    });
  };

  const onEditorSave = (formState: DiscoverFormState) => {
    if (!editorTarget) return;
    if (editorTarget.mode === "new") {
      const fresh = makeNewDiscoverCatalog({
        name: formState.catalogName,
        catalogType: formState.catalogType,
      });
      // Apply the full form state on top of the seed so non-default fields
      // (genres, providers, sort) carry through to params.
      const withForm = applyEditToCatalog(fresh, formState);
      setRows((rs) => {
        const index = rs.length;
        return [
          ...rs,
          {
            id: `${withForm.id as string}__${withForm.type as string}__${index}`,
            index,
            catalogId: withForm.id as string,
            type: withForm.type as string,
            source: (withForm.source as string) ?? "tmdb",
            name: (withForm.name as string) ?? formState.catalogName,
            enabled: true,
            showInHome: true,
            isCustom: true,
            raw: withForm,
          },
        ];
      });
    } else if (editorTarget.rowId) {
      setRows((rs) =>
        rs.map((r) => {
          if (r.id !== editorTarget.rowId) return r;
          const edited = applyEditToCatalog(r.raw, formState);
          return {
            ...r,
            catalogId: (edited.id as string) ?? r.catalogId,
            type: (edited.type as string) ?? r.type,
            source: (edited.source as string) ?? r.source,
            name: (edited.name as string) ?? r.name,
            isCustom: true,
            raw: edited,
          };
        }),
      );
    }
    setEditorTarget(null);
  };

  const onDeleteRow = (id: string) => {
    setRows((rs) => rs.filter((r) => r.id !== id));
  };

  const editorRow =
    editorTarget?.mode === "edit" && editorTarget.rowId
      ? rows.find((r) => r.id === editorTarget.rowId) ?? null
      : null;

  return (
    <section className="rounded-2xl border border-slate-700/50 bg-slate-800/40 p-5">
      <h2 className="mb-1 text-lg font-medium">Catalogs</h2>
      <p className="mb-4 text-xs text-slate-400">
        Enable/disable, reorder, and rename catalogs. Drag the handle to
        reorder within the current filter — items hidden by the filter keep
        their original positions. {rows.length} catalogs total.
      </p>

      <div className="mb-4 grid gap-3 md:grid-cols-[1fr_auto]">
        <TextField
          label="Search"
          value={query}
          onChange={setQuery}
          placeholder="Filter by name, id, or source…"
        />
        <div className="flex items-end gap-2">
          {TYPE_FILTERS.map((f) => (
            <button
              key={f.value}
              type="button"
              onClick={() => setTypeFilter(f.value)}
              className={`rounded-lg border px-3 py-1.5 text-xs ${
                typeFilter === f.value
                  ? "border-emerald-400 bg-emerald-500/15 text-emerald-200"
                  : "border-slate-700 text-slate-300 hover:bg-slate-800"
              }`}
            >
              {f.label}
            </button>
          ))}
          <button
            type="button"
            onClick={() => setEditorTarget({ mode: "new" })}
            className="inline-flex items-center gap-1 rounded-lg border border-emerald-400/60 bg-emerald-500/10 px-3 py-1.5 text-xs font-medium text-emerald-200 hover:bg-emerald-500/20"
          >
            <Plus className="h-3.5 w-3.5" /> New
          </button>
        </div>
      </div>

      {filtered.length === 0 ? (
        <div className="rounded-xl border border-slate-700/50 bg-slate-900/30 p-4 text-xs text-slate-500">
          No catalogs match.
        </div>
      ) : (
        <SortableList
          items={filtered}
          onReorder={onReorderFiltered}
          renderItem={(row, _, dragHandle) => (
            <div className="flex items-center gap-3 rounded-xl border border-slate-700/50 bg-slate-900/40 p-3">
              {dragHandle}
              <div className="flex flex-1 flex-col gap-2 md:flex-row md:items-center">
                <div className="flex flex-1 items-center gap-2">
                  <TypeChip type={row.type} />
                  <SourceChip source={row.source} />
                  {row.isCustom && (
                    <span className="rounded-full bg-emerald-500/20 px-2 py-0.5 text-[10px] font-medium text-emerald-300">
                      custom
                    </span>
                  )}
                  <p className="font-mono text-[11px] text-slate-500">{row.catalogId}</p>
                </div>
                <input
                  value={row.name}
                  onChange={(e) => patchRow(row.id, { name: e.target.value })}
                  className="w-full rounded-lg border border-slate-700 bg-slate-900/60 px-2 py-1.5 text-sm text-slate-100 placeholder-slate-500 focus:border-emerald-400 focus:outline-none md:max-w-xs"
                />
                <div className="flex items-center gap-3">
                  <ToggleLabel
                    label="enabled"
                    checked={row.enabled}
                    onChange={(v) => patchRow(row.id, { enabled: v })}
                  />
                  <ToggleLabel
                    label="home"
                    checked={row.showInHome}
                    onChange={(v) => patchRow(row.id, { showInHome: v })}
                  />
                  <button
                    type="button"
                    onClick={() => setEditorTarget({ mode: "edit", rowId: row.id })}
                    className="rounded p-1 text-slate-400 hover:bg-slate-800 hover:text-slate-200"
                    aria-label="Edit catalog"
                    title="Edit discover params"
                  >
                    <Pencil className="h-3.5 w-3.5" />
                  </button>
                  <button
                    type="button"
                    onClick={() => onDeleteRow(row.id)}
                    className="rounded p-1 text-slate-500 hover:bg-rose-500/15 hover:text-rose-300"
                    aria-label="Delete catalog"
                    title="Remove from list"
                  >
                    <Trash2 className="h-3.5 w-3.5" />
                  </button>
                </div>
              </div>
            </div>
          )}
        />
      )}

      <SaveBar
        dirty={dirty}
        isSaving={isSaving}
        error={error}
        onSave={save}
        onReset={() => setRows(baseline)}
      />

      {editorTarget && (
        <AioMetadataCatalogEditor
          initial={editorRow?.raw ?? null}
          onCancel={() => setEditorTarget(null)}
          onSave={onEditorSave}
        />
      )}
    </section>
  );
}

function TypeChip({ type }: { type: string }) {
  if (!type) return null;
  return (
    <span className="rounded-full bg-slate-700/60 px-2 py-0.5 text-[10px] font-medium uppercase text-slate-200">
      {type}
    </span>
  );
}

function SourceChip({ source }: { source: string }) {
  if (!source) return null;
  return (
    <span className="rounded-full border border-slate-700 px-2 py-0.5 text-[10px] text-slate-400">
      {source}
    </span>
  );
}

function ToggleLabel({
  label,
  checked,
  onChange,
}: {
  label: string;
  checked: boolean;
  onChange: (v: boolean) => void;
}) {
  return (
    <span className="inline-flex items-center gap-2 text-xs text-slate-300">
      <Toggle checked={checked} onChange={onChange} />
      {label}
    </span>
  );
}
