import {
  ADVANCED_SESSION_TTL_MS,
  ADVANCED_SESSION_TOKEN_BYTES,
  createServiceClient,
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

  const baseUrl = Deno.env.get("SOURCE_CLOUD_ADVANCED_BASE_URL") ?? "https://source.omnio.tv";
  const url = `${baseUrl.replace(/\/+$/, "")}/advanced/session/${opaqueToken}`;

  return jsonResponse(200, {
    url,
    expiresAtEpochMillis: expiresAt.getTime(),
    message: "Scan to open advanced source config",
  });
});