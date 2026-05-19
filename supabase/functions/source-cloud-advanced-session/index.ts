import {
  ADVANCED_SESSION_TTL_MS,
  ADVANCED_SESSION_TOKEN_BYTES,
  createServiceClient,
  decryptAesGcm,
  errorResponse,
  handleCors,
  jsonResponse,
  parseProfileId,
  resolveOwnerId,
  requireAuth,
  sha256Hex,
} from "../_shared/source_cloud.ts";

Deno.serve(async (request) => {
  const cors = handleCors(request);
  if (cors) return cors;

  if (request.method !== "POST") {
    return errorResponse(405, "Method not allowed");
  }

  const authResult = requireAuth(request);
  if (authResult instanceof Response) return authResult;
  const { userId } = authResult;

  let body: { profileId?: unknown };
  try {
    body = await request.json();
  } catch {
    return errorResponse(400, "Invalid JSON body");
  }

  const rawProfileId = body.profileId;
  const profileId = parseProfileId(
    typeof rawProfileId === "number" ? String(rawProfileId) : typeof rawProfileId === "string" ? rawProfileId : null,
  );
  if (profileId === null) {
    return errorResponse(400, "Invalid profileId");
  }

  const client = createServiceClient();
  const ownerId = await resolveOwnerId(client, userId);

  await client
    .from("source_cloud_advanced_sessions")
    .update({ status: "revoked" })
    .eq("user_id", ownerId)
    .eq("profile_id", profileId)
    .eq("status", "active");

  const tokenBytes = new Uint8Array(ADVANCED_SESSION_TOKEN_BYTES);
  crypto.getRandomValues(tokenBytes);
  const opaqueToken = Array.from(tokenBytes)
    .map((b) => b.toString(16).padStart(2, "0"))
    .join("");

  const tokenHash = await sha256Hex(opaqueToken);
  const now = new Date();
  const expiresAt = new Date(now.getTime() + ADVANCED_SESSION_TTL_MS);

  const { error: insertError } = await client
    .from("source_cloud_advanced_sessions")
    .insert({
      user_id: ownerId,
      profile_id: profileId,
      session_token_hash: tokenHash,
      status: "active",
      expires_at: expiresAt.toISOString(),
    });

  if (insertError) {
    return errorResponse(500, "Failed to create session");
  }

  await client
    .from("source_cloud_configs")
    .upsert(
      {
        user_id: ownerId,
        profile_id: profileId,
        advanced_config_url_expires_at: expiresAt.toISOString(),
      },
      { onConflict: "user_id,profile_id" },
    );

  // Decrypt the AIOStreams account password so the app can surface it to
  // the user for paste-in on the configure page. The encryptedPassword in
  // the URL only authorizes view; the raw password is what AIOStreams' web
  // UI asks for to authorize save operations. Also expose the persistent
  // configure URL so the user can bookmark it in a password manager and
  // open AIOStreams directly from any device without going through the app.
  const { data: configRow } = await client
    .from("source_cloud_configs")
    .select("aiostreams_config_id, aiostreams_encrypted_password, aiostreams_config_secret_ciphertext, aiostreams_config_secret_nonce")
    .eq("user_id", ownerId)
    .eq("profile_id", profileId)
    .maybeSingle();

  let configurePassword: string | null = null;
  if (
    configRow &&
    typeof configRow.aiostreams_config_secret_ciphertext === "string" &&
    typeof configRow.aiostreams_config_secret_nonce === "string"
  ) {
    configurePassword = await decryptAesGcm(
      configRow.aiostreams_config_secret_ciphertext,
      configRow.aiostreams_config_secret_nonce,
    );
  }

  const aioBaseUrl = (Deno.env.get("AIOSTREAMS_BASE_URL") ?? "").replace(/\/+$/, "");
  const aioConfigId = configRow?.aiostreams_config_id as string | null | undefined;
  const aioEncryptedPassword = configRow?.aiostreams_encrypted_password as string | null | undefined;
  const directConfigureUrl = (aioBaseUrl && aioConfigId && aioEncryptedPassword)
    ? `${aioBaseUrl}/stremio/${aioConfigId}/${aioEncryptedPassword}/configure`
    : null;

  const baseUrl = Deno.env.get("SOURCE_CLOUD_ADVANCED_BASE_URL") ?? "https://account.omnio.tv";
  const url = `${baseUrl.replace(/\/+$/, "")}/source-cloud/handoff/${opaqueToken}`;

  return jsonResponse(200, {
    url,
    expiresAtEpochMillis: expiresAt.getTime(),
    message: "Scan to open advanced source config",
    configurePassword,
    directConfigureUrl,
  });
});
