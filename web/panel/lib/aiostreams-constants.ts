/**
 * AIOStreams enum values + display names, mirroring
 * github.com/Viren070/AIOStreams packages/core/src/utils/constants.ts.
 *
 * Keep in sync with the allowlists in
 * supabase/functions/source-cloud-update-config/index.ts. If AIOStreams adds
 * a new enum value upstream, add it here AND to the edge function — otherwise
 * the panel may show a value the backend rejects.
 */

export const RESOLUTIONS = [
  "2160p", "1440p", "1080p", "720p", "576p", "480p", "360p", "240p", "144p", "Unknown",
] as const;

export const QUALITIES = [
  "BluRay REMUX", "BluRay", "WEB-DL", "WEBRip", "HDRip", "HC HD-Rip",
  "DVDRip", "HDTV", "CAM", "TS", "TC", "SCR", "Unknown",
] as const;

export const SORT_CRITERIA = [
  "quality", "resolution", "language", "subtitle", "visualTag", "audioTag",
  "audioChannel", "streamType", "encode", "size", "service", "seeders",
  "private", "age", "addon", "regexPatterns", "cached", "library", "keyword",
  "streamExpressionMatched", "streamExpressionScore", "regexScore", "seadex",
  "bitrate", "releaseGroup",
] as const;

export const SORT_CRITERION_LABELS: Record<(typeof SORT_CRITERIA)[number], string> = {
  quality: "Quality",
  resolution: "Resolution",
  language: "Language",
  subtitle: "Subtitle",
  visualTag: "Visual tag (HDR/DV/etc.)",
  audioTag: "Audio tag",
  audioChannel: "Audio channels",
  streamType: "Stream type",
  encode: "Encode",
  size: "Size",
  service: "Service",
  seeders: "Seeders",
  private: "Private tracker",
  age: "Age",
  addon: "Addon",
  regexPatterns: "Regex patterns",
  cached: "Cached",
  library: "Library",
  keyword: "Keyword",
  streamExpressionMatched: "Stream expression matched",
  streamExpressionScore: "Stream expression score",
  regexScore: "Regex score",
  seadex: "SeaDex",
  bitrate: "Bitrate",
  releaseGroup: "Release group",
};
