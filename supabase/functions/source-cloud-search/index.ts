import {
  createServiceClient,
  ensureAioStreamsConfig,
  fetchAioStreamsSearch,
  errorResponse,
  handleCors,
  jsonResponse,
  parseProfileId,
  resolveOwnerId,
  requireAuth,
} from "../_shared/source_cloud.ts";

interface SearchBody {
  type?: string;
  videoId?: string;
  tmdbId?: string;
  season?: number;
  episode?: number;
}

Deno.serve(async (request) => {
  const cors = handleCors(request);
  if (cors) return cors;

  if (request.method !== "POST") {
    return errorResponse(405, "Method not allowed");
  }

  const authResult = requireAuth(request);
  if (authResult instanceof Response) return authResult;
  const { userId } = authResult;

  let body: Record<string, unknown>;
  try {
    body = await request.json() as Record<string, unknown>;
  } catch {
    return errorResponse(400, "Invalid JSON body");
  }

  const searchBody: SearchBody = body as SearchBody;

  const profileId = parseProfileId(
    typeof body.profileId === "number"
      ? String(body.profileId)
      : typeof body.profileId === "string"
        ? body.profileId as string
        : null,
  );

  const type = searchBody.type;
  const videoId = searchBody.videoId;
  const season = searchBody.season;
  const episode = searchBody.episode;

  if (!type || !videoId) {
    return errorResponse(400, "Missing required fields: type, videoId");
  }

  if (type !== "movie" && type !== "series") {
    return errorResponse(400, "type must be 'movie' or 'series'");
  }

  const client = createServiceClient();
  const ownerId = await resolveOwnerId(client, userId);

  const settingsResult = await client
    .from("source_cloud_settings")
    .select("enabled")
    .eq("user_id", ownerId)
    .eq("profile_id", profileId)
    .maybeSingle();

  const enabled = settingsResult.data?.enabled ?? true;
  if (!enabled) {
    return jsonResponse(200, { streams: [] });
  }

  const hasConnectedToken = await client
    .from("source_cloud_service_tokens")
    .select("service")
    .eq("user_id", ownerId)
    .eq("profile_id", profileId)
    .eq("status", "connected")
    .limit(1);

  if (!hasConnectedToken.data?.length) {
    return jsonResponse(200, { streams: [] });
  }

  const { configId, status } = await ensureAioStreamsConfig(
    client,
    ownerId,
    profileId,
  );

  if (!configId || status !== "ready") {
    return jsonResponse(200, { streams: [] });
  }

  const searchId = searchBody.tmdbId
    ? `tmdb:${searchBody.tmdbId}`
    : videoId;

  const streams = await fetchAioStreamsSearch(
    configId,
    type,
    searchId,
    season,
    episode,
  );

  return jsonResponse(200, { streams });
});
