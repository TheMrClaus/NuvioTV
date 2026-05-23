"use client";

import { useMemo, useState, useTransition } from "react";
import { useRouter } from "next/navigation";
import { Plus, Trash2, User } from "lucide-react";
import { saveProfiles } from "@/lib/actions/relational";
import { provisionAioMetadataForProfile } from "@/lib/actions/aiometadata";
import { provisionSourceCloudForProfile } from "@/lib/actions/sourcecloud";
import type { Profile, AvatarEntry } from "@/lib/data/profiles";
import { SaveBar, type SaveState } from "./SaveBar";
import { useUnsavedWarning } from "./useUnsavedWarning";

interface ProfileEdit {
  /** Stable client-side id for React keys. Pre-existing rows reuse their
   *  Supabase uuid; new-in-this-session rows get a synthesized "new-N" so
   *  the post-save fan-out can identify which profile_indexes were minted
   *  in this submission. */
  id: string;
  profile_index: number;
  name: string;
  avatar_color_hex: string;
  uses_primary_addons: boolean;
  uses_primary_plugins: boolean;
  avatar_id: string | null;
  is_kids: boolean;
  max_age_rating: string | null;
  /** Only meaningful for `isNew` rows; ignored on save for existing ones.
   *  Kids always force copy=true server-side; for non-kids new rows the
   *  user explicitly opts in. */
  copy_keys_from_main: boolean;
  isNew: boolean;
}

interface Props {
  profiles: Profile[];
  avatarCatalog: AvatarEntry[];
  onRequestDelete: (profileIndex: number, name: string) => void;
}

const COLOR_PRESETS = [
  "#1E88E5",
  "#43A047",
  "#FB8C00",
  "#E53935",
  "#8E24AA",
  "#00ACC1",
  "#FDD835",
  "#5E35B1",
];

// Mirrors core-domain AgeRatingTier enum; same labels the kids template
// and AIOMetadata server consume on settings.ageRating.
const AGE_RATING_OPTIONS = ["G", "PG", "PG-13", "TV-14", "R", "NC-17"] as const;

// Cap user-creatable profiles. The TV supports an open-ended set but most
// users stay under 10; we surface a soft cap so the UI doesn't grow
// unbounded. Bump if/when product needs it.
const MAX_PROFILES = 10;

export default function ProfilesForm({
  profiles,
  avatarCatalog,
  onRequestDelete,
}: Props) {
  const router = useRouter();
  const initial: ProfileEdit[] = profiles.map((p) => ({
    id: p.id,
    profile_index: p.profile_index,
    name: p.name,
    avatar_color_hex: p.avatar_color_hex,
    uses_primary_addons: p.uses_primary_addons,
    uses_primary_plugins: p.uses_primary_plugins,
    avatar_id: p.avatar_id,
    is_kids: p.is_kids,
    max_age_rating: p.max_age_rating,
    copy_keys_from_main: false,
    isNew: false,
  }));

  const [items, setItems] = useState<ProfileEdit[]>(initial);
  const [state, setState] = useState<SaveState>({ kind: "idle" });
  const [provisionWarnings, setProvisionWarnings] = useState<string[]>([]);
  const [isPending, startTransition] = useTransition();

  const initialSig = useMemo(() => JSON.stringify(initial), [initial]);
  const currentSig = useMemo(() => JSON.stringify(items), [items]);
  const dirty = initialSig !== currentSig;
  useUnsavedWarning(dirty);

  const expectedUpdatedAt = useMemo(
    () =>
      profiles.reduce<string | null>(
        (max, p) => (max === null || p.updated_at > max ? p.updated_at : max),
        null,
      ),
    [profiles],
  );

  const avatarById = useMemo(
    () => Object.fromEntries(avatarCatalog.map((a) => [a.id, a])),
    [avatarCatalog],
  );

  const update = (idx: number, patch: Partial<ProfileEdit>) => {
    setItems((prev) =>
      prev.map((p) => (p.profile_index === idx ? { ...p, ...patch } : p)),
    );
  };

  const removeNewProfile = (idx: number) => {
    setItems((prev) => prev.filter((p) => !(p.profile_index === idx && p.isNew)));
  };

  const addProfile = () => {
    setItems((prev) => {
      const nextIndex =
        prev.reduce((max, p) => (p.profile_index > max ? p.profile_index : max), 0) + 1;
      if (nextIndex > MAX_PROFILES) return prev;
      const newItem: ProfileEdit = {
        id: `new-${nextIndex}`,
        profile_index: nextIndex,
        name: "",
        avatar_color_hex: COLOR_PRESETS[(nextIndex - 1) % COLOR_PRESETS.length],
        uses_primary_addons: true,
        uses_primary_plugins: true,
        avatar_id: null,
        is_kids: false,
        max_age_rating: null,
        copy_keys_from_main: false,
        isNew: true,
      };
      return [...prev, newItem];
    });
  };

  const handleSave = () => {
    setState({ kind: "saving" });
    setProvisionWarnings([]);
    const newRows = items.filter((p) => p.isNew);

    startTransition(async () => {
      const result = await saveProfiles({
        profiles: items.map((p) => ({
          profile_index: p.profile_index,
          name: p.name,
          avatar_color_hex: p.avatar_color_hex,
          uses_primary_addons: p.uses_primary_addons,
          uses_primary_plugins: p.uses_primary_plugins,
          avatar_id: p.avatar_id,
          is_kids: p.is_kids,
          max_age_rating: p.max_age_rating,
        })),
        expectedUpdatedAt,
        revalidatePath: `/profiles`,
      });
      if (!result.ok) {
        if ("conflict" in result && result.conflict) {
          setState({ kind: "conflict" });
        } else {
          setState({ kind: "error", message: result.error });
        }
        return;
      }

      // Fan out provisioning for each newly-created profile. Kids forces
      // copyKeysFromMain on the server side; we still surface partial
      // failures so the user can retry from the profile's integration page.
      const warnings: string[] = [];
      for (const row of newRows) {
        const aio = await provisionAioMetadataForProfile({
          profileId: row.profile_index,
          kids: row.is_kids,
          copyKeysFromMain: row.copy_keys_from_main,
          maxAgeRating: row.is_kids ? row.max_age_rating : null,
        });
        if (!aio.ok) {
          warnings.push(
            `Profile ${row.profile_index} (${row.name || "unnamed"}): AIOMetadata provisioning failed — ${aio.error ?? "unknown"}.`,
          );
        }
        const sc = await provisionSourceCloudForProfile({
          profileId: row.profile_index,
          kids: row.is_kids,
          copyKeysFromMain: row.copy_keys_from_main,
        });
        if (!sc.ok) {
          warnings.push(
            `Profile ${row.profile_index} (${row.name || "unnamed"}): Source Cloud provisioning failed — ${sc.error ?? "unknown"}.`,
          );
        }
      }

      setProvisionWarnings(warnings);
      setState({ kind: "saved", at: Date.now() });
      router.refresh();
      setTimeout(
        () => setState((s) => (s.kind === "saved" ? { kind: "idle" } : s)),
        warnings.length > 0 ? 6000 : 2000,
      );
    });
  };

  const atCapacity = items.length >= MAX_PROFILES;

  return (
    <div className="space-y-6">
      {items.map((p) => {
        const isPrimary = p.profile_index === 1;
        const avatar = p.avatar_id ? avatarById[p.avatar_id] : null;
        return (
          <section
            key={p.id}
            className="rounded-2xl border border-slate-700/50 bg-slate-800/40 p-5"
          >
            <div className="mb-4 flex items-start gap-4">
              <div
                className="flex h-16 w-16 shrink-0 items-center justify-center overflow-hidden rounded-full text-2xl font-semibold text-white"
                style={{ backgroundColor: avatar?.bg_color ?? p.avatar_color_hex }}
              >
                {avatar ? (
                  <img
                    src={avatar.image_url}
                    alt={avatar.display_name}
                    className="h-full w-full object-cover"
                  />
                ) : p.name ? (
                  p.name[0]?.toUpperCase()
                ) : (
                  <User className="h-7 w-7" />
                )}
              </div>
              <div className="flex-1 space-y-3">
                <div className="flex items-baseline gap-2">
                  <h2 className="text-lg font-medium text-slate-100">
                    Profile {p.profile_index}
                  </h2>
                  {isPrimary && (
                    <span className="rounded-full bg-amber-500/20 px-2 py-0.5 text-xs text-amber-300">
                      primary
                    </span>
                  )}
                  {p.isNew && (
                    <span className="rounded-full bg-emerald-500/20 px-2 py-0.5 text-xs text-emerald-300">
                      new
                    </span>
                  )}
                </div>
                <input
                  type="text"
                  value={p.name}
                  placeholder={`Profile ${p.profile_index}`}
                  onChange={(e) => update(p.profile_index, { name: e.target.value })}
                  className="w-full rounded-lg border border-slate-700 bg-slate-900/40 px-3 py-2 text-sm text-slate-100 outline-none focus:border-primary"
                />
              </div>
              {!isPrimary && (
                <button
                  type="button"
                  onClick={() =>
                    p.isNew
                      ? removeNewProfile(p.profile_index)
                      : onRequestDelete(p.profile_index, p.name)
                  }
                  className="flex h-8 w-8 items-center justify-center rounded text-slate-500 hover:text-rose-300"
                  aria-label={p.isNew ? "Remove unsaved profile" : "Delete profile"}
                >
                  <Trash2 className="h-4 w-4" />
                </button>
              )}
            </div>

            <div className="mb-4">
              <span className="mb-2 block text-xs font-medium uppercase tracking-wide text-slate-400">
                Avatar colour
              </span>
              <div className="flex flex-wrap items-center gap-2">
                {COLOR_PRESETS.map((color) => (
                  <button
                    key={color}
                    type="button"
                    onClick={() => update(p.profile_index, { avatar_color_hex: color })}
                    className={`h-8 w-8 rounded-full border-2 transition ${
                      p.avatar_color_hex.toLowerCase() === color.toLowerCase()
                        ? "border-white"
                        : "border-transparent hover:border-slate-500"
                    }`}
                    style={{ backgroundColor: color }}
                    aria-label={`Set color ${color}`}
                  />
                ))}
                <label className="flex items-center gap-2">
                  <input
                    type="color"
                    value={p.avatar_color_hex}
                    onChange={(e) =>
                      update(p.profile_index, { avatar_color_hex: e.target.value })
                    }
                    className="h-8 w-8 cursor-pointer rounded border border-slate-700 bg-transparent"
                  />
                  <span className="font-mono text-xs text-slate-400">
                    {p.avatar_color_hex}
                  </span>
                </label>
              </div>
            </div>

            <div className="mb-4">
              <span className="mb-2 block text-xs font-medium uppercase tracking-wide text-slate-400">
                Avatar
              </span>
              <select
                value={p.avatar_id ?? ""}
                onChange={(e) =>
                  update(p.profile_index, { avatar_id: e.target.value || null })
                }
                className="rounded-lg border border-slate-700 bg-slate-900/40 px-3 py-2 text-sm text-slate-100 outline-none focus:border-primary"
              >
                <option value="">(initial letter)</option>
                {avatarCatalog.map((a) => (
                  <option key={a.id} value={a.id}>
                    {a.display_name} ({a.category})
                  </option>
                ))}
              </select>
            </div>

            {!isPrimary && (
              <div className="space-y-3">
                <label className="flex cursor-pointer items-center gap-2 text-sm text-slate-300">
                  <input
                    type="checkbox"
                    checked={p.is_kids}
                    onChange={(e) =>
                      update(p.profile_index, {
                        is_kids: e.target.checked,
                        // Clear max_age_rating when leaving Kids mode so we
                        // don't ship stale tier data on the row.
                        max_age_rating: e.target.checked ? p.max_age_rating : null,
                      })
                    }
                    className="h-4 w-4 rounded border-slate-600 bg-slate-900 text-primary focus:ring-primary"
                  />
                  Kids profile (PIN-gated exit, age-clamped catalogs)
                </label>

                {p.is_kids && (
                  <div className="ml-6 flex items-center gap-2">
                    <span className="text-xs uppercase tracking-wide text-slate-400">
                      Max age rating
                    </span>
                    <select
                      value={p.max_age_rating ?? ""}
                      onChange={(e) =>
                        update(p.profile_index, {
                          max_age_rating: e.target.value || null,
                        })
                      }
                      className="rounded-lg border border-slate-700 bg-slate-900/40 px-3 py-1.5 text-sm text-slate-100 outline-none focus:border-primary"
                    >
                      <option value="">(no limit)</option>
                      {AGE_RATING_OPTIONS.map((tier) => (
                        <option key={tier} value={tier}>
                          {tier}
                        </option>
                      ))}
                    </select>
                  </div>
                )}

                {p.isNew && !p.is_kids && (
                  <label className="flex cursor-pointer items-center gap-2 text-sm text-slate-300">
                    <input
                      type="checkbox"
                      checked={p.copy_keys_from_main}
                      onChange={(e) =>
                        update(p.profile_index, { copy_keys_from_main: e.target.checked })
                      }
                      className="h-4 w-4 rounded border-slate-600 bg-slate-900 text-primary focus:ring-primary"
                    />
                    Copy TMDB / TVDB / RPDB keys from Main on first provision
                  </label>
                )}
                {p.isNew && p.is_kids && (
                  <p className="ml-6 text-xs text-slate-500">
                    Kids profiles always inherit Main&apos;s API keys.
                  </p>
                )}

                <label className="flex cursor-pointer items-center gap-2 text-sm text-slate-300">
                  <input
                    type="checkbox"
                    checked={p.uses_primary_addons}
                    onChange={(e) =>
                      update(p.profile_index, { uses_primary_addons: e.target.checked })
                    }
                    className="h-4 w-4 rounded border-slate-600 bg-slate-900 text-primary focus:ring-primary"
                  />
                  Share addons with primary profile
                </label>
                <label className="flex cursor-pointer items-center gap-2 text-sm text-slate-300">
                  <input
                    type="checkbox"
                    checked={p.uses_primary_plugins}
                    onChange={(e) =>
                      update(p.profile_index, { uses_primary_plugins: e.target.checked })
                    }
                    className="h-4 w-4 rounded border-slate-600 bg-slate-900 text-primary focus:ring-primary"
                  />
                  Share plugins with primary profile
                </label>
              </div>
            )}
          </section>
        );
      })}

      <button
        type="button"
        onClick={addProfile}
        disabled={atCapacity}
        className="flex w-full items-center justify-center gap-2 rounded-2xl border border-dashed border-slate-600 px-4 py-3 text-sm text-slate-300 hover:border-primary hover:text-primary disabled:cursor-not-allowed disabled:opacity-40"
      >
        <Plus className="h-4 w-4" />
        {atCapacity ? `Profile limit reached (${MAX_PROFILES})` : "Add profile"}
      </button>

      {provisionWarnings.length > 0 && (
        <div className="rounded-xl border border-amber-500/40 bg-amber-500/10 p-3 text-xs text-amber-200">
          <p className="mb-1 font-medium">Profile row saved, but some provisioning failed:</p>
          <ul className="list-inside list-disc space-y-0.5">
            {provisionWarnings.map((w) => (
              <li key={w}>{w}</li>
            ))}
          </ul>
          <p className="mt-2 text-amber-300/80">
            You can retry from each profile&apos;s Integrations page.
          </p>
        </div>
      )}

      <SaveBar
        dirty={dirty}
        state={isPending ? { kind: "saving" } : state}
        onSave={handleSave}
        onDiscard={() => {
          setItems(initial);
          setState({ kind: "idle" });
          setProvisionWarnings([]);
        }}
      />
    </div>
  );
}
