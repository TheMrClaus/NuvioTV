import {
  createServiceClient,
  decryptAesGcm,
  errorResponse,
  handleCors,
  jsonResponse,
  parseProfileId,
  resolveOwnerId,
  requireAuth,
} from "../_shared/source_cloud.ts";

/**
 * Return the user-facing slice of the AIOStreams config that the in-app
 * API Keys section renders/edits. Does not expose the full config — just
 * the values our form binds to.
 *
 * Request: POST { profileId }
 * Response (200): {
 *   tmdbApiKey: string | null,
 *   tmdbAccessToken: string | null,
 *   tvdbApiKey: string | null,
 *   rpdbApiKey: string | null,
 *   animeToshoEnabled: boolean,
 *   debridioApiKey: string | null,
 *   provisioned: boolean,
 * }
 *
 * When the user has no AIOStreams config yet, returns all fields as
 * null/false with provisioned=false.
 */
Deno.serve(async (request) => {
  const cors = handleCors(request);
  if (cors) return cors;

  if (request.method !== "POST") return errorResponse(405, "Method not allowed");

  const authResult = requireAuth(request);
  if (authResult instanceof Response) return authResult;
  const { userId } = authResult;

  let body: { profileId?: unknown };
  try {
    body = await request.json();
  } catch {
    return errorResponse(400, "Invalid JSON body");
  }

  const profileId = parseProfileId(
    typeof body.profileId === "number" ? String(body.profileId)
      : typeof body.profileId === "string" ? body.profileId
      : null,
  );
  if (profileId === null) return errorResponse(400, "Invalid profileId");

  const empty = {
    tmdbApiKey: null,
    tmdbAccessToken: null,
    tvdbApiKey: null,
    rpdbApiKey: null,
    animeToshoEnabled: false,
    debridioApiKey: null,
    provisioned: false,
  };

  const baseUrl = (Deno.env.get("AIOSTREAMS_BASE_URL") ?? "").replace(/\/+$/, "");
  if (!baseUrl) return jsonResponse(200, empty);

  const client = createServiceClient();
  const ownerId = await resolveOwnerId(client, userId);

  const { data: configRow } = await client
    .from("source_cloud_configs")
    .select("aiostreams_config_id, aiostreams_config_secret_ciphertext, aiostreams_config_secret_nonce")
    .eq("user_id", ownerId)
    .eq("profile_id", profileId)
    .maybeSingle();

  const aioConfigId = configRow?.aiostreams_config_id as string | null | undefined;
  if (
    !aioConfigId ||
    typeof configRow?.aiostreams_config_secret_ciphertext !== "string" ||
    typeof configRow?.aiostreams_config_secret_nonce !== "string"
  ) {
    return jsonResponse(200, empty);
  }

  const aiostreamsPassword = await decryptAesGcm(
    configRow.aiostreams_config_secret_ciphertext,
    configRow.aiostreams_config_secret_nonce,
  );
  if (!aiostreamsPassword) return jsonResponse(200, empty);

  const url = new URL(`${baseUrl}/api/v1/user`);
  url.searchParams.set("uuid", aioConfigId);
  url.searchParams.set("password", aiostreamsPassword);
  url.searchParams.set("raw", "true");
  const fetchResponse = await fetch(url.toString(), { method: "GET" });
  if (!fetchResponse.ok) return jsonResponse(200, empty);

  const fetchJson = await fetchResponse.json() as { data?: { userData?: Record<string, unknown> } };
  const config = fetchJson.data?.userData ?? null;
  if (!config) return jsonResponse(200, empty);

  const presets = Array.isArray(config.presets) ? config.presets : [];
  const animeToshoPreset = presets.find(
    (p) => p && typeof p === "object" && (p as Record<string, unknown>).type === "animetosho",
  ) as Record<string, unknown> | undefined;
  const debridioPreset = presets.find(
    (p) => p && typeof p === "object" && (p as Record<string, unknown>).type === "debridio",
  ) as Record<string, unknown> | undefined;
  const debridioOptions = debridioPreset?.options as Record<string, unknown> | undefined;

  return jsonResponse(200, {
    tmdbApiKey: typeof config.tmdbApiKey === "string" ? config.tmdbApiKey : null,
    tmdbAccessToken: typeof config.tmdbAccessToken === "string" ? config.tmdbAccessToken : null,
    tvdbApiKey: typeof config.tvdbApiKey === "string" ? config.tvdbApiKey : null,
    rpdbApiKey: typeof config.rpdbApiKey === "string" ? config.rpdbApiKey : null,
    animeToshoEnabled: animeToshoPreset
      ? (animeToshoPreset.enabled as boolean | undefined) ?? false
      : false,
    debridioApiKey: typeof debridioOptions?.debridioApiKey === "string"
      ? debridioOptions.debridioApiKey as string
      : null,
    provisioned: true,
  });
});
