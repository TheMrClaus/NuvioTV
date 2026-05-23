"use client";

import { useState, useTransition } from "react";
import { Eye, EyeOff, CheckCircle2, XCircle, AlertTriangle, ExternalLink } from "lucide-react";
import { useRouter } from "next/navigation";
import {
  connectSourceCloudService,
  disconnectSourceCloudService,
  type SourceCloudServiceDto,
} from "@/lib/actions/sourcecloud";

interface ServiceMeta {
  id: "real_debrid" | "torbox";
  name: string;
  apiKeyUrl: string;
  signupUrl?: string;
  signupLabel?: string;
}

const SERVICES: ServiceMeta[] = [
  {
    id: "real_debrid",
    name: "Real-Debrid",
    apiKeyUrl: "https://real-debrid.com/apitoken",
  },
  {
    id: "torbox",
    name: "Torbox",
    apiKeyUrl: "https://torbox.app/settings",
    signupUrl:
      "https://torbox.app/subscription?referral=ef446ad8-b935-4c71-aa8f-fb62813c7a23",
    signupLabel: "Sign up (+15 days free via referral)",
  },
];

interface Props {
  profileId: number;
  services: SourceCloudServiceDto[];
}

export default function SourceCloudServicesForm({ profileId, services }: Props) {
  return (
    <section className="rounded-2xl border border-slate-700/50 bg-slate-800/40 p-5">
      <h2 className="mb-1 text-lg font-medium">Debrid services</h2>
      <p className="mb-4 text-xs text-slate-400">
        Connecting a service provisions your private AIOStreams config and applies the
        Debrid Starter template (Torrentio, Comet, AnimeTosho, Knaben, StremThru Torz).
      </p>
      <div className="space-y-4">
        {SERVICES.map((meta) => {
          const status = services.find((s) => s.service === meta.id);
          return (
            <ServiceRow
              key={meta.id}
              meta={meta}
              profileId={profileId}
              connected={status?.connected ?? false}
              statusMessage={status?.message ?? null}
            />
          );
        })}
      </div>
    </section>
  );
}

function ServiceRow({
  meta,
  profileId,
  connected,
  statusMessage,
}: {
  meta: ServiceMeta;
  profileId: number;
  connected: boolean;
  statusMessage: string | null;
}) {
  const router = useRouter();
  const [editing, setEditing] = useState(false);
  const [apiKey, setApiKey] = useState("");
  const [reveal, setReveal] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [isPending, startTransition] = useTransition();

  const handleConnect = () => {
    setError(null);
    startTransition(async () => {
      const result = await connectSourceCloudService({
        profileId,
        service: meta.id,
        apiKey,
      });
      if (result.ok) {
        setApiKey("");
        setEditing(false);
        router.refresh();
      } else {
        setError(result.error ?? "Couldn't connect");
      }
    });
  };

  const handleDisconnect = () => {
    setError(null);
    startTransition(async () => {
      const result = await disconnectSourceCloudService({
        profileId,
        service: meta.id,
      });
      if (result.ok) {
        router.refresh();
      } else {
        setError(result.error ?? "Couldn't disconnect");
      }
    });
  };

  return (
    <div className="rounded-xl border border-slate-700/50 bg-slate-900/40 p-4">
      <div className="flex items-center justify-between gap-3">
        <div>
          <h3 className="font-medium text-slate-100">{meta.name}</h3>
          <div className="mt-1 flex items-center gap-2 text-xs">
            {connected ? (
              <span className="inline-flex items-center gap-1 rounded-full bg-emerald-500/20 px-2 py-0.5 text-emerald-300">
                <CheckCircle2 className="h-3 w-3" /> Connected
              </span>
            ) : (
              <span className="inline-flex items-center gap-1 rounded-full bg-slate-700/50 px-2 py-0.5 text-slate-400">
                <XCircle className="h-3 w-3" /> Disconnected
              </span>
            )}
            {statusMessage && (
              <span className="text-amber-300 inline-flex items-center gap-1">
                <AlertTriangle className="h-3 w-3" /> {statusMessage}
              </span>
            )}
          </div>
        </div>
        <div className="flex items-center gap-2">
          {connected && !editing && (
            <button
              type="button"
              onClick={handleDisconnect}
              disabled={isPending}
              className="rounded-lg border border-rose-700/50 px-3 py-1.5 text-xs text-rose-300 hover:bg-rose-700/20 disabled:opacity-50"
            >
              {isPending ? "..." : "Disconnect"}
            </button>
          )}
          {!editing && (
            <button
              type="button"
              onClick={() => setEditing(true)}
              className="rounded-lg border border-slate-600 px-3 py-1.5 text-xs text-slate-200 hover:bg-slate-700/40"
            >
              {connected ? "Update key" : "Connect"}
            </button>
          )}
        </div>
      </div>

      {editing && (
        <div className="mt-3 space-y-3">
          <label className="block">
            <span className="mb-1 block text-xs font-medium uppercase tracking-wide text-slate-400">
              API key
            </span>
            <div className="flex gap-2">
              <input
                type={reveal ? "text" : "password"}
                value={apiKey}
                onChange={(e) => setApiKey(e.target.value)}
                placeholder={`Paste your ${meta.name} API key`}
                autoComplete="off"
                spellCheck={false}
                className="flex-1 rounded-lg border border-slate-700 bg-slate-900/40 px-3 py-2 font-mono text-sm text-slate-100 outline-none focus:border-primary"
              />
              <button
                type="button"
                onClick={() => setReveal((v) => !v)}
                className="flex h-10 w-10 items-center justify-center rounded-lg border border-slate-700 text-slate-400 hover:text-slate-100"
                aria-label={reveal ? "Hide" : "Reveal"}
              >
                {reveal ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
              </button>
            </div>
          </label>
          <div className="flex items-center justify-between text-xs">
            <div className="flex flex-col gap-1">
              <a
                href={meta.apiKeyUrl}
                target="_blank"
                rel="noreferrer"
                className="inline-flex items-center gap-1 text-primary hover:underline"
              >
                Get your {meta.name} API key <ExternalLink className="h-3 w-3" />
              </a>
              {meta.signupUrl && (
                <a
                  href={meta.signupUrl}
                  target="_blank"
                  rel="noreferrer"
                  className="inline-flex items-center gap-1 text-slate-400 hover:text-slate-200"
                >
                  {meta.signupLabel} <ExternalLink className="h-3 w-3" />
                </a>
              )}
            </div>
            <div className="flex gap-2">
              <button
                type="button"
                onClick={() => {
                  setEditing(false);
                  setApiKey("");
                  setError(null);
                }}
                className="rounded-lg px-3 py-1.5 text-xs text-slate-400 hover:text-slate-200"
                disabled={isPending}
              >
                Cancel
              </button>
              <button
                type="button"
                onClick={handleConnect}
                disabled={isPending || !apiKey.trim()}
                className="rounded-lg bg-primary px-3 py-1.5 text-xs font-medium text-slate-900 hover:bg-primary/90 disabled:opacity-50"
              >
                {isPending ? "Saving..." : "Save"}
              </button>
            </div>
          </div>
          {error && (
            <p className="text-xs text-rose-300">{error}</p>
          )}
        </div>
      )}
    </div>
  );
}
