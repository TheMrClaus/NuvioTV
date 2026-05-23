"use server";

import { revalidatePath } from "next/cache";
import { createServerSupabase } from "@/lib/supabase/server";

/**
 * Server-action wrappers around the Source Cloud Edge Functions. The user's
 * Supabase session JWT is read from cookies (via createServerSupabase) and
 * threaded into the Authorization header so the Edge Functions'
 * requireAuth check passes.
 *
 * Functions return `null` on any network/auth/non-2xx failure. The pages /
 * forms render an error state when that happens.
 */

const SUPABASE_URL = process.env.NEXT_PUBLIC_SUPABASE_URL!;
const SUPABASE_ANON_KEY = process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY!;

export interface SourceCloudConfigStateDto {
  status?: string;
  label?: string;
  message?: string | null;
  advancedConfigAvailable?: boolean;
  canReset?: boolean;
}

export interface SourceCloudServiceDto {
  service: "real_debrid" | "torbox";
  connected: boolean;
  label?: string;
  message?: string | null;
}

export interface SourceCloudStatusResponse {
  config?: SourceCloudConfigStateDto | null;
  services?: SourceCloudServiceDto[] | null;
}

export interface SourceCloudPresetSummary {
  instanceId: string;
  type: string;
  name: string;
  enabled: boolean;
  timeout: number | null;
  mediaTypes: string[];
  useMultipleInstances: boolean;
}

export interface SourceCloudPresetOptionPatch {
  name?: string;
  timeout?: number;
  mediaTypes?: string[];
  useMultipleInstances?: boolean;
}

export interface SourceCloudSortCriterion {
  key: string;
  direction: "asc" | "desc";
}

export interface SourceCloudTitleMatching {
  enabled: boolean;
  mode: "exact" | "contains" | null;
  similarityThreshold: number | null;
}
export interface SourceCloudYearMatching {
  enabled: boolean;
  tolerance: number | null;
  strict: boolean;
}
export interface SourceCloudDigitalReleaseFilter {
  enabled: boolean;
  tolerance: number | null;
}
export interface SourceCloudAvailablePreset {
  type: string;
  name: string;
}

export interface SourceCloudDeduplicator {
  enabled: boolean;
  multiGroupBehaviour: "keep_all" | "aggressive" | "conservative" | null;
  keys: string[];
  cached: string | null;
  uncached: string | null;
}

export interface SourceCloudConfigSummaryResponse {
  tmdbApiKey: string | null;
  tmdbAccessToken: string | null;
  tvdbApiKey: string | null;
  rpdbApiKey: string | null;
  animeToshoEnabled: boolean;
  debridioApiKey: string | null;
  presets: SourceCloudPresetSummary[];
  excludedResolutions: string[];
  preferredResolutions: string[];
  excludedQualities: string[];
  preferredQualities: string[];
  excludedLanguages: string[];
  preferredLanguages: string[];
  sortCriteria: SourceCloudSortCriterion[];
  titleMatching: SourceCloudTitleMatching | null;
  yearMatching: SourceCloudYearMatching | null;
  digitalReleaseFilter: SourceCloudDigitalReleaseFilter | null;
  excludedKeywords: string[];
  excludedRegexPatterns: string[];
  includedRegexPatterns: string[];
  requiredRegexPatterns: string[];
  deduplicator: SourceCloudDeduplicator | null;
  availablePresets: SourceCloudAvailablePreset[];
  provisioned: boolean;
}

export interface SourceCloudAdvancedSessionResponse {
  url: string;
  expiresAtEpochMillis?: number | null;
  message?: string | null;
  configurePassword?: string | null;
  directConfigureUrl?: string | null;
}

export interface SourceCloudRedeemedSessionResponse {
  profileId: number;
  directConfigureUrl: string | null;
  configurePassword: string | null;
  expiresAtEpochMillis: number;
  sourceCloudSettingsPath: string;
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

export async function fetchSourceCloudStatus(profileId: number): Promise<SourceCloudStatusResponse | null> {
  // status is GET with a query param, not POST. Special-case it.
  const supabase = await createServerSupabase();
  const {
    data: { session },
  } = await supabase.auth.getSession();
  if (!session) return null;
  const url = new URL(`${SUPABASE_URL}/functions/v1/source-cloud-status`);
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
  return (await res.json()) as SourceCloudStatusResponse;
}

export async function fetchSourceCloudConfigSummary(
  profileId: number,
): Promise<SourceCloudConfigSummaryResponse | null> {
  return callEdge<SourceCloudConfigSummaryResponse>(
    "source-cloud-get-config-summary",
    { profileId },
  );
}

export interface ConnectServiceResult {
  ok: boolean;
  status?: SourceCloudStatusResponse | null;
  error?: string;
}

async function refreshIntegrationsPath(profileId: number) {
  revalidatePath(`/p/${profileId}/integrations/source-cloud`);
  revalidatePath(`/p/${profileId}/integrations`);
}

export async function connectSourceCloudService(input: {
  profileId: number;
  service: "real_debrid" | "torbox";
  apiKey: string;
}): Promise<ConnectServiceResult> {
  if (!input.apiKey.trim()) return { ok: false, error: "API key is required" };
  const status = await callEdge<SourceCloudStatusResponse>(
    "source-cloud-connect-service",
    {
      profileId: input.profileId,
      service: input.service,
      apiKey: input.apiKey.trim(),
    },
  );
  if (!status) return { ok: false, error: "Couldn't reach Source Cloud" };
  const failed = status.config?.status === "provisioning_failed";
  await refreshIntegrationsPath(input.profileId);
  return {
    ok: !failed,
    status,
    error: failed ? status.config?.message ?? "Provisioning failed" : undefined,
  };
}

export async function disconnectSourceCloudService(input: {
  profileId: number;
  service: "real_debrid" | "torbox";
}): Promise<ConnectServiceResult> {
  const status = await callEdge<SourceCloudStatusResponse>(
    "source-cloud-disconnect-service",
    { profileId: input.profileId, service: input.service },
  );
  if (!status) return { ok: false, error: "Couldn't reach Source Cloud" };
  await refreshIntegrationsPath(input.profileId);
  return { ok: true, status };
}

export interface UpdateConfigInput {
  profileId: number;
  tmdbApiKey?: string | null;
  tmdbAccessToken?: string | null;
  tvdbApiKey?: string | null;
  rpdbApiKey?: string | null;
  animeToshoEnabled?: boolean;
  debridioApiKey?: string | null;
  presetToggles?: Record<string, boolean>;
  presetOptions?: Record<string, SourceCloudPresetOptionPatch>;
  excludedResolutions?: string[];
  preferredResolutions?: string[];
  excludedQualities?: string[];
  preferredQualities?: string[];
  excludedLanguages?: string[];
  preferredLanguages?: string[];
  sortCriteria?: SourceCloudSortCriterion[];
  titleMatching?: { enabled?: boolean; mode?: "exact" | "contains"; similarityThreshold?: number };
  yearMatching?: { enabled?: boolean; tolerance?: number; strict?: boolean };
  digitalReleaseFilter?: { enabled?: boolean; tolerance?: number };
  excludedKeywords?: string[];
  excludedRegexPatterns?: string[];
  includedRegexPatterns?: string[];
  requiredRegexPatterns?: string[];
  deduplicator?: {
    enabled?: boolean;
    multiGroupBehaviour?: "keep_all" | "aggressive" | "conservative";
    keys?: string[];
    cached?: string;
    uncached?: string;
  };
  addPresets?: string[];
}

export async function updateSourceCloudConfig(
  input: UpdateConfigInput,
): Promise<ConnectServiceResult> {
  const status = await callEdge<SourceCloudStatusResponse>(
    "source-cloud-update-config",
    input,
  );
  if (!status) return { ok: false, error: "Couldn't save" };
  const failed = status.config?.status === "provisioning_failed";
  await refreshIntegrationsPath(input.profileId);
  return {
    ok: !failed,
    status,
    error: failed ? status.config?.message ?? "Save failed" : undefined,
  };
}

export async function resetSourceCloudConfig(profileId: number): Promise<ConnectServiceResult> {
  const status = await callEdge<SourceCloudStatusResponse>(
    "source-cloud-reset",
    { profileId },
  );
  if (!status) return { ok: false, error: "Couldn't reach Source Cloud" };
  await refreshIntegrationsPath(profileId);
  return { ok: true, status };
}

export interface ProvisionSourceCloudResult {
  ok: boolean;
  aiostreamsConfigId?: string | null;
  reused?: boolean;
  error?: string;
}

/**
 * Provision a fresh AIOStreams config for a non-primary profile. Mirrors
 * the TV-side `sourceCloudRepository.provisionProfile` so the panel can
 * fan out provisioning after creating a profile row.
 *
 * Kids profiles always inherit Main's API keys (forced server-side); for
 * non-kids the `copyKeysFromMain` toggle is honoured.
 */
export async function provisionSourceCloudForProfile(input: {
  profileId: number;
  kids: boolean;
  copyKeysFromMain: boolean;
}): Promise<ProvisionSourceCloudResult> {
  const res = await callEdge<{
    config?: { status?: string; message?: string | null };
    aiostreamsConfigId?: string;
    reused?: boolean;
  }>("source-cloud-provision-profile", {
    profileId: input.profileId,
    kids: input.kids,
    copyKeysFromMain: input.copyKeysFromMain,
  });
  if (!res) return { ok: false, error: "Couldn't reach Source Cloud" };
  const failed = res.config?.status === "provisioning_failed";
  await refreshIntegrationsPath(input.profileId);
  return {
    ok: !failed,
    aiostreamsConfigId: res.aiostreamsConfigId ?? null,
    reused: res.reused ?? false,
    error: failed ? res.config?.message ?? "Provisioning failed" : undefined,
  };
}

export async function requestSourceCloudAdvancedSession(
  profileId: number,
): Promise<SourceCloudAdvancedSessionResponse | null> {
  return callEdge<SourceCloudAdvancedSessionResponse>(
    "source-cloud-advanced-session",
    { profileId },
  );
}

export async function redeemSourceCloudAdvancedSession(
  token: string,
): Promise<SourceCloudRedeemedSessionResponse | null> {
  if (!token.trim()) return null;
  return callEdge<SourceCloudRedeemedSessionResponse>(
    "source-cloud-redeem-session",
    { token: token.trim() },
  );
}
