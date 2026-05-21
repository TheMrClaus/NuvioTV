"use client";

import { useState, useTransition } from "react";
import { useRouter } from "next/navigation";
import { RotateCcw } from "lucide-react";
import {
  resetAioMetadataFromMain,
  setAioMetadataEnabled,
} from "@/lib/actions/aiometadata";

interface Props {
  profileId: number;
  enabled: boolean;
  canReset: boolean;
}

/**
 * Two-action admin card: enable/disable the AIOMetadata addon for this
 * profile, and (for non-primary profiles with a config) reset back to a
 * fresh copy of Main's config. Mirrors AioMetadataSettingsContent's
 * SettingsToggleRow + reset row on the TV side.
 */
export default function AioMetadataAdminCard({ profileId, enabled, canReset }: Props) {
  const router = useRouter();
  const [isToggling, startToggle] = useTransition();
  const [isResetting, startReset] = useTransition();
  const [error, setError] = useState<string | null>(null);
  const [confirmReset, setConfirmReset] = useState(false);

  const onToggle = () => {
    setError(null);
    startToggle(async () => {
      const result = await setAioMetadataEnabled(profileId, !enabled);
      if (!result.ok) setError(result.error ?? "Couldn't update");
      else router.refresh();
    });
  };

  const onReset = () => {
    setError(null);
    startReset(async () => {
      const result = await resetAioMetadataFromMain(profileId);
      if (!result.ok) setError(result.error ?? "Reset failed");
      else {
        setConfirmReset(false);
        router.refresh();
      }
    });
  };

  return (
    <section className="rounded-2xl border border-slate-700/50 bg-slate-800/40 p-5">
      <h2 className="mb-3 text-lg font-medium">Addon</h2>

      <div className="flex items-center justify-between gap-4">
        <div>
          <p className="text-sm text-slate-200">Enable for this profile</p>
          <p className="text-xs text-slate-400">
            When on, the AIOMetadata manifest is mounted in this profile&apos;s addon list.
          </p>
        </div>
        <button
          type="button"
          onClick={onToggle}
          disabled={isToggling}
          className={`relative inline-flex h-6 w-11 items-center rounded-full transition ${
            enabled ? "bg-emerald-500" : "bg-slate-600"
          } ${isToggling ? "opacity-60" : ""}`}
          aria-pressed={enabled}
        >
          <span
            className={`inline-block h-4 w-4 transform rounded-full bg-white transition ${
              enabled ? "translate-x-6" : "translate-x-1"
            }`}
          />
        </button>
      </div>

      {canReset && (
        <div className="mt-5 border-t border-slate-700/60 pt-4">
          {!confirmReset ? (
            <button
              type="button"
              onClick={() => setConfirmReset(true)}
              className="inline-flex items-center gap-2 rounded-lg border border-slate-700 bg-slate-900/40 px-3 py-1.5 text-xs text-slate-200 hover:border-slate-500 hover:bg-slate-900/70"
            >
              <RotateCcw className="h-3.5 w-3.5" />
              Reset from Main
            </button>
          ) : (
            <div className="space-y-3">
              <p className="text-xs text-amber-200">
                This re-copies Main&apos;s entire AIOMetadata config into this profile and
                mints a new UUID. The previous config on this profile is discarded. Kids
                tier overlay is <em>not</em> applied — use the TV-app reset for that.
              </p>
              <div className="flex items-center gap-2">
                <button
                  type="button"
                  onClick={onReset}
                  disabled={isResetting}
                  className="rounded-lg bg-rose-500/80 px-3 py-1.5 text-xs font-medium text-white hover:bg-rose-500 disabled:opacity-60"
                >
                  {isResetting ? "Resetting…" : "Confirm reset"}
                </button>
                <button
                  type="button"
                  onClick={() => setConfirmReset(false)}
                  className="rounded-lg border border-slate-700 px-3 py-1.5 text-xs text-slate-300 hover:bg-slate-800"
                >
                  Cancel
                </button>
              </div>
            </div>
          )}
        </div>
      )}

      {error && (
        <p className="mt-3 text-xs text-rose-300">{error}</p>
      )}
    </section>
  );
}
