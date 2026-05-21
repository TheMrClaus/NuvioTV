"use client";

import { useState, useTransition } from "react";
import { Copy, ExternalLink } from "lucide-react";
import { fetchAioMetadataStatus } from "@/lib/actions/aiometadata";

/**
 * Reveal-on-click access to the upstream cedya77/aiometadata `/configure` UI.
 *
 * Mirrors SourceCloudAdvancedForm's pattern: the button calls the status
 * server action, then renders the configure URL + the raw config password
 * the user has to paste when upstream prompts. Avoids embedding the
 * password in the initial HTML — the user must click before it appears in
 * the DOM.
 *
 * Unlike AIOStreams, the AIOMetadata upstream URL does NOT embed an
 * encrypted password in the path; upstream's web UI always prompts for the
 * raw password when you visit /stremio/<uuid>/configure, so we just surface
 * it for paste-in (same UX the TV settings screen shows).
 */
export default function AioMetadataConfigureSession({ profileId }: { profileId: number }) {
  const [session, setSession] = useState<{ url: string; password: string | null } | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [copied, setCopied] = useState(false);
  const [isFetching, startFetch] = useTransition();

  const handleFetch = () => {
    setError(null);
    setCopied(false);
    startFetch(async () => {
      const status = await fetchAioMetadataStatus(profileId);
      const cfg = status?.config;
      if (!cfg?.configureUrl) {
        setError("Couldn't load the configure URL");
        return;
      }
      setSession({
        url: cfg.configureUrl,
        password: cfg.configPassword ?? null,
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

  if (!session) {
    return (
      <div>
        <p className="mb-2 text-xs text-slate-400">
          Open the upstream AIOMetadata configure UI for any setting this panel
          doesn&apos;t expose yet. Click below to reveal the URL and the password
          upstream will prompt for.
        </p>
        <button
          type="button"
          onClick={handleFetch}
          disabled={isFetching}
          className="inline-flex items-center gap-2 rounded-lg bg-emerald-500/90 px-4 py-2 text-sm font-medium text-white hover:bg-emerald-500 disabled:opacity-50"
        >
          <ExternalLink className="h-4 w-4" />
          {isFetching ? "Loading…" : "Open AIOMetadata configure"}
        </button>
        {error && <p className="mt-2 text-xs text-rose-300">{error}</p>}
      </div>
    );
  }

  return (
    <div className="space-y-3 rounded-xl border border-slate-700/50 bg-slate-900/40 p-4">
      <a
        href={session.url}
        target="_blank"
        rel="noreferrer"
        className="inline-flex items-center gap-2 rounded-lg bg-emerald-500/90 px-4 py-2 text-sm font-medium text-white hover:bg-emerald-500"
      >
        <ExternalLink className="h-4 w-4" /> Open configure page
      </a>
      {session.password ? (
        <div>
          <p className="mb-1 text-xs text-slate-400">
            AIOMetadata will prompt for this password to authorize edits:
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
      ) : (
        <p className="text-xs text-amber-300">
          No password is recorded for this config. Save any change in this
          panel to mint one, or open AIOMetadata from the TV settings screen
          to back-fill it.
        </p>
      )}
      <p className="text-xs text-slate-500">
        This URL is stable. Save it to a password manager for one-click access
        from any device.
      </p>
      {error && <p className="text-xs text-rose-300">{error}</p>}
    </div>
  );
}
