import { assertEquals, assertMatch, assertNotEquals } from "https://deno.land/std@0.224.0/assert/mod.ts";
import {
  ageRatingFromString,
  applyKidsOverlayToCatalogs,
  applySettingsPatch,
  applyShallowPatch,
  configureUrl,
  extractSettings,
  fallbackManifestUrl,
  filtersForTier,
  generateConfigPassword,
} from "./aio_metadata.ts";

Deno.test("applyShallowPatch sets, clears, and ignores fields by value", () => {
  const target: Record<string, unknown> = { keep: "x", clearMe: "y", emptyMe: "z" };
  applyShallowPatch(target, {
    keep: "x2",
    clearMe: null,
    emptyMe: "",
    skip: undefined,
    add: "new",
  });
  assertEquals(target, { keep: "x2", add: "new" });
});

Deno.test("applyShallowPatch is a no-op when patch is undefined", () => {
  const target: Record<string, unknown> = { a: 1 };
  applyShallowPatch(target, undefined);
  assertEquals(target, { a: 1 });
});

Deno.test("applySettingsPatch writes flat keys at root (settings live root-side on the wire)", () => {
  const config: Record<string, unknown> = {
    apiKeys: { tmdb: "k" },
    providers: { movie: "tmdb" },
    catalogs: [],
    language: "en-US",
  };
  applySettingsPatch(config, { language: "es-ES", sfw: true });
  assertEquals(config.language, "es-ES");
  assertEquals(config.sfw, true);
  // Reserved root keys remain untouched.
  assertEquals(config.apiKeys, { tmdb: "k" });
  assertEquals(config.providers, { movie: "tmdb" });
});

Deno.test("extractSettings strips providers/apiKeys/catalogs and keeps the rest", () => {
  const config: Record<string, unknown> = {
    providers: { movie: "tmdb" },
    apiKeys: { tmdb: "k" },
    catalogs: [{ id: "a" }],
    language: "en-US",
    sfw: true,
    nested: { a: 1 },
  };
  const settings = extractSettings(config);
  assertEquals(settings, { language: "en-US", sfw: true, nested: { a: 1 } });
});

Deno.test("generateConfigPassword returns URL-safe base64 of 32 random bytes", () => {
  const a = generateConfigPassword();
  const b = generateConfigPassword();
  assertNotEquals(a, b);
  assertMatch(a, /^[A-Za-z0-9_-]+$/);
  // 32 bytes → base64 length 44 with padding, 43 without.
  assertEquals(a.length, 43);
});

Deno.test("fallbackManifestUrl and configureUrl return empty string when base URL missing", () => {
  // The module-level base URL is read at import time, so we can only assert
  // on the empty-uuid case here. The non-empty-base case is exercised by
  // integration with the upstream.
  assertEquals(fallbackManifestUrl(""), "");
  assertEquals(configureUrl(""), "");
});

Deno.test("ageRatingFromString maps known labels and rejects 'None'/unknown", () => {
  assertEquals(ageRatingFromString("G"), "G");
  assertEquals(ageRatingFromString("PG-13"), "PG-13");
  assertEquals(ageRatingFromString("None"), null);
  assertEquals(ageRatingFromString(""), null);
  assertEquals(ageRatingFromString(null), null);
  assertEquals(ageRatingFromString("PG14"), null);
});

Deno.test("filtersForTier G enforces strictest movie/TV clamps", () => {
  const f = filtersForTier("G");
  assertEquals(f.movieCertificationLte, "G");
  assertEquals(f.tvWithGenres, ["10751", "16", "10762"]);
});

Deno.test("filtersForTier TV-14 clamps movies to PG-13 + relaxes TV with-list", () => {
  const f = filtersForTier("TV-14");
  assertEquals(f.movieCertificationLte, "PG-13");
  assertEquals(f.tvWithGenres, null);
});

Deno.test("filtersForTier null is conservative even without an explicit tier", () => {
  const f = filtersForTier(null);
  assertEquals(f.movieCertificationLte, "PG");
  assertEquals(f.tvWithGenres, ["10751", "16", "10762"]);
});

Deno.test("applyKidsOverlayToCatalogs mutates TMDB movie params with cert + without_genres", () => {
  const input = [
    {
      id: "tmdb.discover.movie.test",
      type: "movie",
      source: "tmdb",
      metadata: {
        discover: {
          source: "tmdb",
          mediaType: "movie",
          params: { sort_by: "popularity.desc" },
          formState: { catalogType: "movie" },
        },
      },
    },
  ];
  const out = applyKidsOverlayToCatalogs(input, "G");
  const discover = (out[0].metadata as { discover: Record<string, unknown> }).discover;
  const params = discover.params as Record<string, unknown>;
  assertEquals(params.certification_country, "US");
  assertEquals(params["certification.lte"], "G");
  assertEquals(params.include_adult, false);
  // Genre exclusions joined by comma + deduped.
  assertMatch(String(params.without_genres ?? ""), /27,53,80,10752,37/);
  // formState mirrors the cert.
  const fs = discover.formState as Record<string, unknown>;
  assertEquals(fs.maxCertification, "G");
  assertEquals(fs.includeAdult, false);
});

Deno.test("applyKidsOverlayToCatalogs forces TV with_genres to family/animation/kids on G", () => {
  const input = [
    {
      id: "tmdb.discover.tv.test",
      type: "series",
      source: "tmdb",
      metadata: {
        discover: {
          source: "tmdb",
          mediaType: "tv",
          params: { with_genres: "10759" },
          formState: {},
        },
      },
    },
  ];
  const out = applyKidsOverlayToCatalogs(input, "G");
  const params = (out[0].metadata as { discover: { params: Record<string, unknown> } })
    .discover.params;
  assertEquals(params.with_genres, "10751|16|10762");
});

Deno.test("applyKidsOverlayToCatalogs leaves non-TMDB catalogs untouched", () => {
  const input = [
    {
      id: "trakt.recommendations.movies",
      type: "movie",
      source: "trakt",
      metadata: { discover: { source: "trakt", mediaType: "movie", params: {} } },
    },
  ];
  const out = applyKidsOverlayToCatalogs(input, "G");
  assertEquals(out[0], input[0]);
});

Deno.test("applyKidsOverlayToCatalogs R tier removes cert clamp", () => {
  const input = [
    {
      id: "tmdb.discover.movie.test",
      type: "movie",
      source: "tmdb",
      metadata: {
        discover: { source: "tmdb", mediaType: "movie", params: {}, formState: {} },
      },
    },
  ];
  const out = applyKidsOverlayToCatalogs(input, "R");
  const params = (out[0].metadata as { discover: { params: Record<string, unknown> } })
    .discover.params;
  // R tier: no cert.lte / certification_country set.
  assertEquals(params["certification.lte"], undefined);
  assertEquals(params.certification_country, undefined);
  // include_adult always forced to false on TMDB discovers.
  assertEquals(params.include_adult, false);
});
