"use server";

import { revalidatePath } from "next/cache";
import { createServerSupabase } from "@/lib/supabase/server";

/**
 * Server-action wrappers around the AIOMetadata Edge Functions. Mirrors the
 * patterns in ./sourcecloud.ts — the user's Supabase session JWT is read
 * from cookies (via createServerSupabase) and threaded into the
 * Authorization header so the edge functions' requireAuth check passes.
 *
 * Functions return `null` on any network/auth/non-2xx failure. The page
 * renders an error state when that happens.
 */

const SUPABASE_URL = process.env.NEXT_PUBLIC_SUPABASE_URL!;
const SUPABASE_ANON_KEY = process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY!;

export interface AioMetadataConfigState {
  enabled?: boolean;
  status?: string;
  label?: string;
  message?: string | null;
  hasConfig?: boolean;
  canReset?: boolean;
  uuid?: string | null;
  manifestUrl?: string | null;
  configureUrl?: string | null;
  configPassword?: string | null;
  lastProvisionedAt?: string | null;
  lastValidatedAt?: string | null;
}

export interface AioMetadataStatusResponse {
  config?: AioMetadataConfigState | null;
}

export interface AioMetadataInnerConfig {
  providers: Record<string, unknown>;
  apiKeys: Record<string, unknown>;
  catalogs: Array<Record<string, unknown>>;
  settings: Record<string, unknown>;
}

export interface AioMetadataConfigResponse {
  provisioned: boolean;
  uuid?: string;
  manifestUrl?: string | null;
  config?: AioMetadataInnerConfig;
}

async function callEdge<T>(fn: string, body: unknown): Promise<T | null> {
  const supabase = await createServerSupabase();
  const {
    data: { session },
  } = await supabase.auth.getSession();
  if (!session) return null;
  const res = await fetch(`${SUPABASE_URL}/functions/v1/${fn}`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${session.access_token}`,
      apikey: SUPABASE_ANON_KEY,
    },
    body: JSON.stringify(body),
    cache: "no-store",
  });
  if (!res.ok) return null;
  return (await res.json()) as T;
}

export async function fetchAioMetadataStatus(
  profileId: number,
): Promise<AioMetadataStatusResponse | null> {
  const supabase = await createServerSupabase();
  const {
    data: { session },
  } = await supabase.auth.getSession();
  if (!session) return null;
  const url = new URL(`${SUPABASE_URL}/functions/v1/aio-metadata-status`);
  url.searchParams.set("profileId", String(profileId));
  const res = await fetch(url.toString(), {
    method: "GET",
    headers: {
      Authorization: `Bearer ${session.access_token}`,
      apikey: SUPABASE_ANON_KEY,
    },
    cache: "no-store",
  });
  if (!res.ok) return null;
  return (await res.json()) as AioMetadataStatusResponse;
}

export async function fetchAioMetadataConfig(
  profileId: number,
): Promise<AioMetadataConfigResponse | null> {
  return callEdge<AioMetadataConfigResponse>("aio-metadata-get-config", { profileId });
}

export interface UpdateAioMetadataConfigInput {
  profileId: number;
  apiKeys?: Record<string, string | null>;
  providers?: Record<string, unknown>;
  settings?: Record<string, unknown>;
  catalogs?: Array<Record<string, unknown>>;
}

export interface UpdateAioMetadataConfigResult {
  ok: boolean;
  status?: AioMetadataStatusResponse | null;
  error?: string;
}

async function refreshIntegrationsPath(profileId: number) {
  revalidatePath(`/p/${profileId}/integrations/aio-metadata`);
  revalidatePath(`/p/${profileId}/integrations`);
}

export async function updateAioMetadataConfig(
  input: UpdateAioMetadataConfigInput,
): Promise<UpdateAioMetadataConfigResult> {
  const status = await callEdge<AioMetadataStatusResponse>(
    "aio-metadata-update-config",
    input,
  );
  if (!status) return { ok: false, error: "Couldn't reach AIOMetadata" };
  const failed = status.config?.status === "provisioning_failed";
  await refreshIntegrationsPath(input.profileId);
  return {
    ok: !failed,
    status,
    error: failed ? status.config?.message ?? "Save failed" : undefined,
  };
}

export async function setAioMetadataEnabled(
  profileId: number,
  enabled: boolean,
): Promise<UpdateAioMetadataConfigResult> {
  const status = await callEdge<{ enabled: boolean }>(
    "aio-metadata-set-enabled",
    { profileId, enabled },
  );
  if (!status) return { ok: false, error: "Couldn't reach AIOMetadata" };
  await refreshIntegrationsPath(profileId);
  return { ok: true };
}

export async function resetAioMetadataFromMain(
  profileId: number,
): Promise<UpdateAioMetadataConfigResult> {
  const status = await callEdge<AioMetadataStatusResponse>(
    "aio-metadata-reset-from-main",
    { profileId },
  );
  if (!status) return { ok: false, error: "Couldn't reach AIOMetadata" };
  const failed = status.config?.status === "provisioning_failed";
  await refreshIntegrationsPath(profileId);
  return {
    ok: !failed,
    status,
    error: failed ? status.config?.message ?? "Reset failed" : undefined,
  };
}
