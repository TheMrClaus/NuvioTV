"use client";

import { useState } from "react";
import Link from "next/link";
import { Copy, ExternalLink } from "lucide-react";

interface Props {
  directConfigureUrl: string | null;
  configurePassword: string | null;
  sourceCloudSettingsPath: string;
}

export default function SourceCloudTvHandoffCard({
  directConfigureUrl,
  configurePassword,
  sourceCloudSettingsPath,
}: Props) {
  const [copied, setCopied] = useState(false);
  const [copyError, setCopyError] = useState<string | null>(null);

  async function handleCopy() {
    if (!configurePassword) return;
    try {
      await navigator.clipboard.writeText(configurePassword);
      setCopyError(null);
      setCopied(true);
      setTimeout(() => setCopied(false), 1500);
    } catch {
      setCopyError("Clipboard unavailable on this device.");
    }
  }

  return (
    <section className="rounded-2xl border border-slate-700/50 bg-slate-800/40 p-5">
      <h1 className="text-2xl font-semibold">Continue Source Cloud setup</h1>
      <p className="mt-2 text-sm text-slate-400">
        You scanned the TV handoff QR. Open your private AIOStreams configure page,
        then use the password below if AIOStreams asks you to authorize saving changes.
      </p>

      <div className="mt-5 space-y-4 rounded-xl border border-slate-700/50 bg-slate-900/40 p-4">
        {directConfigureUrl ? (
          <a
            href={directConfigureUrl}
            target="_blank"
            rel="noreferrer"
            className="inline-flex items-center gap-2 rounded-lg bg-primary px-4 py-2 text-sm font-medium text-slate-900 hover:bg-primary/90"
          >
            <ExternalLink className="h-4 w-4" /> Open AIOStreams configure
          </a>
        ) : (
          <p className="text-sm text-amber-300">
            No direct configure URL is ready yet. Open the Source Cloud integration page to check provisioning state.
          </p>
        )}

        {configurePassword ? (
          <div>
            <p className="mb-1 text-xs text-slate-400">
              AIOStreams save password
            </p>
            <div className="flex flex-col gap-2 sm:flex-row sm:items-center">
              <code className="flex-1 overflow-x-auto rounded-lg border border-slate-700 bg-slate-950/70 px-3 py-2 font-mono text-xs text-slate-100">
                {configurePassword}
              </code>
              <button
                type="button"
                onClick={handleCopy}
                className="inline-flex items-center justify-center gap-1 rounded-lg border border-slate-600 px-3 py-2 text-xs text-slate-200 hover:bg-slate-700/40"
              >
                <Copy className="h-3 w-3" /> {copied ? "Copied" : "Copy password"}
              </button>
            </div>
            {copyError && <p className="mt-2 text-xs text-rose-300">{copyError}</p>}
          </div>
        ) : (
          <p className="text-xs text-slate-500">
            No save password is available yet for this Source Cloud config.
          </p>
        )}
      </div>

      <div className="mt-4 flex flex-wrap gap-3 text-sm">
        <Link href={sourceCloudSettingsPath} className="text-primary hover:underline">
          Open full Source Cloud settings
        </Link>
      </div>
    </section>
  );
}
