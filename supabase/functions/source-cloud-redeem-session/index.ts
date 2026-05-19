import {
  createServiceClient,
  decryptAesGcm,
  errorResponse,
  handleCors,
  jsonResponse,
  parseProfileId,
  requireAuth,
  resolveOwnerId,
  sha256Hex,
} from "../_shared/source_cloud.ts";

/**
 * Redeem endpoint for the short-lived URL returned by
 * `source-cloud-advanced-session`. The intended flow is:
 *
 *   1. mobile/TV -> source-cloud-advanced-session
 *      => inserts a row into `source_cloud_advanced_sessions` and returns
 *         `https://account.omnio.tv/source-cloud/handoff/<opaque-token>`
 *   2. authenticated browser panel -> this function
 *      => looks the token hash up, validates the authenticated user owns it,
 *         and returns the AIOStreams configure artifacts as JSON.
 *
 * The function remains `verify_jwt = false` because the panel forwards the
 * user's JWT explicitly and we validate it ourselves.
 */
Deno.serve(async (request) => {
  const cors = handleCors(request);
  if (cors) return cors;

  if (request.method !== "POST") {
    return errorResponse(405, "Method not allowed");
  }

  const authResult = requireAuth(request);
  if (authResult instanceof Response) return authResult;

  const { userId } = authResult;

  let body: { token?: unknown };
  try {
    body = await request.json();
  } catch {
    return errorResponse(400, "Invalid JSON body");
  }

  const token = typeof body.token === "string" ? body.token.trim() : "";

  if (!/^[a-f0-9]{32,}$/i.test(token)) {
    return errorResponse(400, "Invalid session token");
  }

  const tokenHash = await sha256Hex(token);
  const client = createServiceClient();
  const requesterOwnerId = await resolveOwnerId(client, userId);

  const { data: session, error: sessionError } = await client
    .from("source_cloud_advanced_sessions")
    .select("user_id, profile_id, status, expires_at")
    .eq("session_token_hash", tokenHash)
    .maybeSingle();

  if (sessionError) {
    return errorResponse(500, "Failed to look up session");
  }
  if (
    !session ||
    session.status !== "active" ||
    new Date(session.expires_at as string) <= new Date() ||
    session.user_id !== requesterOwnerId
  ) {
    return errorResponse(410, "Session unavailable");
  }

  const ownerId = session.user_id as string;
  const profileId = parseProfileId(String(session.profile_id));
  if (profileId === null) {
    return errorResponse(500, "Invalid profileId on session");
  }

  const { data: configRow } = await client
    .from("source_cloud_configs")
    .select("aiostreams_config_id, aiostreams_encrypted_password, aiostreams_config_secret_ciphertext, aiostreams_config_secret_nonce")
    .eq("user_id", ownerId)
    .eq("profile_id", profileId)
    .maybeSingle();

  await client
    .from("source_cloud_advanced_sessions")
    .update({ used_at: new Date().toISOString() })
    .eq("session_token_hash", tokenHash)
    .eq("status", "active");

  const aioBaseUrl = (Deno.env.get("AIOSTREAMS_BASE_URL") ?? "").replace(/\/+$/, "");
  const configId = configRow?.aiostreams_config_id as string | null | undefined;
  const encryptedPassword = configRow?.aiostreams_encrypted_password as string | null | undefined;
  const directConfigureUrl = (aioBaseUrl && configId && encryptedPassword)
    ? `${aioBaseUrl}/stremio/${configId}/${encryptedPassword}/configure`
    : null;

  let configurePassword: string | null = null;
  if (
    typeof configRow?.aiostreams_config_secret_ciphertext === "string" &&
    typeof configRow?.aiostreams_config_secret_nonce === "string"
  ) {
    configurePassword = await decryptAesGcm(
      configRow.aiostreams_config_secret_ciphertext,
      configRow.aiostreams_config_secret_nonce,
    );
  }

  return jsonResponse(200, {
    profileId,
    directConfigureUrl,
    configurePassword,
    expiresAtEpochMillis: new Date(session.expires_at as string).getTime(),
    sourceCloudSettingsPath: `/p/${profileId}/integrations/source-cloud`,
  });
});
