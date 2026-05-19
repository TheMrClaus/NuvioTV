"use client";

import { useState, useTransition } from "react";
import { Copy, ExternalLink, RotateCcw } from "lucide-react";
import { useRouter } from "next/navigation";
import {
  requestSourceCloudAdvancedSession,
  resetSourceCloudConfig,
} from "@/lib/actions/sourcecloud";

interface Props {
  profileId: number;
  canReset: boolean;
}

export default function SourceCloudAdvancedForm({ profileId, canReset }: Props) {
  const router = useRouter();
  const [session, setSession] = useState<{
    url: string;
    password: string | null;
  } | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [copied, setCopied] = useState(false);
  const [isFetching, startFetch] = useTransition();
  const [isResetting, startReset] = useTransition();
  const [showResetConfirm, setShowResetConfirm] = useState(false);

  const handleFetch = () => {
    setError(null);
    setCopied(false);
    startFetch(async () => {
      const res = await requestSourceCloudAdvancedSession(profileId);
      if (!res || !res.directConfigureUrl) {
        setError("Couldn't load the configure URL");
        return;
      }
      setSession({
        url: res.directConfigureUrl,
        password: res.configurePassword ?? null,
      });
    });
  };

  const handleCopy = async (text: string) => {
    try {
      await navigator.clipboard.writeText(text);
      setCopied(true);
      setTimeout(() => setCopied(false), 1500);
    } catch {
      setError("Clipboard unavailable");
    }
  };

  const handleReset = () => {
    setError(null);
    startReset(async () => {
      const res = await resetSourceCloudConfig(profileId);
      if (res.ok) {
        setShowResetConfirm(false);
        router.refresh();
      } else {
        setError(res.error ?? "Reset failed");
      }
    });
  };

  return (
    <section className="rounded-2xl border border-slate-700/50 bg-slate-800/40 p-5">
      <h2 className="mb-1 text-lg font-medium">Advanced</h2>
      <p className="mb-4 text-xs text-slate-400">
        Open the AIOStreams configure UI directly (presets, formatter, filter
        knobs not exposed in this panel), or reset your config back to the
        Debrid Starter template defaults.
      </p>

      <div className="space-y-3">
        {!session && (
          <button
            type="button"
            onClick={handleFetch}
            disabled={isFetching}
            className="inline-flex items-center gap-2 rounded-lg bg-primary px-4 py-2 text-sm font-medium text-slate-900 hover:bg-primary/90 disabled:opacity-50"
          >
            <ExternalLink className="h-4 w-4" />
            {isFetching ? "Loading..." : "Open AIOStreams configure"}
          </button>
        )}

        {session && (
          <div className="space-y-3 rounded-xl border border-slate-700/50 bg-slate-900/40 p-4">
            <a
              href={session.url}
              target="_blank"
              rel="noreferrer"
              className="inline-flex items-center gap-2 rounded-lg bg-primary px-4 py-2 text-sm font-medium text-slate-900 hover:bg-primary/90"
            >
              <ExternalLink className="h-4 w-4" /> Open configure page
            </a>
            {session.password && (
              <div>
                <p className="mb-1 text-xs text-slate-400">
                  AIOStreams will prompt for this password to authorize saves:
                </p>
                <div className="flex items-center gap-2">
                  <code className="flex-1 truncate rounded-lg border border-slate-700 bg-slate-900/40 px-3 py-2 font-mono text-xs text-slate-100">
                    {session.password}
                  </code>
                  <button
                    type="button"
                    onClick={() => handleCopy(session.password!)}
                    className="inline-flex items-center gap-1 rounded-lg border border-slate-600 px-3 py-2 text-xs text-slate-200 hover:bg-slate-700/40"
                  >
                    <Copy className="h-3 w-3" />
                    {copied ? "Copied" : "Copy"}
                  </button>
                </div>
              </div>
            )}
            <p className="text-xs text-slate-500">
              This URL is stable. Save it to a password manager for one-click
              access from any device.
            </p>
          </div>
        )}

        <div className="border-t border-slate-700/40 pt-3">
          {!showResetConfirm ? (
            <button
              type="button"
              onClick={() => setShowResetConfirm(true)}
              disabled={!canReset || isResetting}
              className="inline-flex items-center gap-2 rounded-lg border border-rose-700/40 px-3 py-1.5 text-xs text-rose-300 hover:bg-rose-700/20 disabled:opacity-50"
            >
              <RotateCcw className="h-3 w-3" /> Reset to Debrid Starter defaults
            </button>
          ) : (
            <div className="space-y-2 rounded-xl border border-rose-700/40 bg-rose-700/10 p-3">
              <p className="text-xs text-rose-200">
                Re-apply the Debrid Starter template to your existing AIOStreams
                user. Keeps your UUID, password, debrid credentials, and
                bookmarked URL. Any custom presets / filter tweaks you made in
                the AIOStreams UI will be rolled back.
              </p>
              <div className="flex justify-end gap-2">
                <button
                  type="button"
                  onClick={() => setShowResetConfirm(false)}
                  className="rounded-lg px-3 py-1.5 text-xs text-slate-300 hover:text-slate-100"
                  disabled={isResetting}
                >
                  Cancel
                </button>
                <button
                  type="button"
                  onClick={handleReset}
                  disabled={isResetting}
                  className="rounded-lg bg-rose-500 px-3 py-1.5 text-xs font-medium text-slate-900 hover:bg-rose-400 disabled:opacity-50"
                >
                  {isResetting ? "Resetting..." : "Confirm reset"}
                </button>
              </div>
            </div>
          )}
        </div>

        {error && <p className="text-xs text-rose-300">{error}</p>}
      </div>
    </section>
  );
}
