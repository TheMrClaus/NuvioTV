import {
  createServiceClient,
  errorResponse,
  handleCors,
  sha256Hex,
} from "../_shared/source_cloud.ts";

/**
 * Redeem proxy for the short-lived URL returned by
 * `source-cloud-advanced-session`. The intended flow is:
 *
 *   1. mobile/TV -> source-cloud-advanced-session
 *      => inserts a row into `source_cloud_advanced_sessions` and returns
 *         `https://source.omnio.tv/advanced/session/<opaque-token>`
 *   2. browser -> this function (reached via SOURCE_CLOUD_ADVANCED_BASE_URL
 *      pointing at it, either directly or via a CDN edge at source.omnio.tv)
 *      => looks the token hash up, ensures an AIOStreams config exists for
 *         the owner/profile, and 302-redirects to the AIOStreams configure
 *         UI.
 *
 * The route must be reachable without a Supabase JWT (the opaque token is
 * the bearer of authority), so this function is registered with
 * `verify_jwt = false` in `supabase/config.toml`.
 */
Deno.serve(async (request) => {
  const cors = handleCors(request);
  if (cors) return cors;

  if (request.method !== "GET") {
    return errorResponse(405, "Method not allowed");
  }

  const url = new URL(request.url);
  const segments = url.pathname.split("/").filter(Boolean);
  const token = segments[segments.length - 1] ?? "";

  if (!/^[a-f0-9]{32,}$/i.test(token)) {
    return errorResponse(400, "Invalid session token");
  }

  const tokenHash = await sha256Hex(token);
  const client = createServiceClient();

  const { data: session, error: sessionError } = await client
    .from("source_cloud_advanced_sessions")
    .select("user_id, profile_id, status, expires_at")
    .eq("session_token_hash", tokenHash)
    .maybeSingle();

  if (sessionError) {
    return errorResponse(500, "Failed to look up session");
  }
  if (!session) {
    return errorResponse(404, "Session not found");
  }
  if (session.status !== "active") {
    return errorResponse(410, "Session no longer active");
  }
  if (new Date(session.expires_at as string) <= new Date()) {
    return errorResponse(410, "Session expired");
  }

  const ownerId = session.user_id as string;
  const profileId = Number(session.profile_id);

  const aioBaseUrl = (Deno.env.get("AIOSTREAMS_BASE_URL") ?? "").replace(/\/+$/, "");
  if (!aioBaseUrl) {
    return errorResponse(500, "AIOSTREAMS_BASE_URL not configured");
  }

  // AIOStreams' authenticated configure URL is
  // `/stremio/<uuid>/<encryptedPassword>/configure`. Both segments are
  // required — the unauthenticated `/stremio/<uuid>/configure` route doesn't
  // exist and AIOStreams 404s it. The encryptedPassword is captured by the
  // in-app credential flow (source-cloud-connect-service) on the original
  // POST /api/v1/user response and stored in `aiostreams_encrypted_password`.
  // If we don't have one for the user yet, fall back to the public
  // `/configure` UI so they can still get to a setup page (with the caveat
  // that creating a fresh user there won't link back to this profile).
  const { data: configRow } = await client
    .from("source_cloud_configs")
    .select("aiostreams_config_id, aiostreams_encrypted_password")
    .eq("user_id", ownerId)
    .eq("profile_id", profileId)
    .maybeSingle();

  await client
    .from("source_cloud_advanced_sessions")
    .update({ used_at: new Date().toISOString() })
    .eq("session_token_hash", tokenHash)
    .is("used_at", null);

  const configId = configRow?.aiostreams_config_id as string | null | undefined;
  const encryptedPassword = configRow?.aiostreams_encrypted_password as string | null | undefined;

  const targetUrl = (configId && encryptedPassword)
    ? `${aioBaseUrl}/stremio/${configId}/${encryptedPassword}/configure`
    : `${aioBaseUrl}/configure`;

  return Response.redirect(targetUrl, 302);
});
