import { assertEquals, assertMatch, assertNotEquals } from "https://deno.land/std@0.224.0/assert/mod.ts";
import {
  applySettingsPatch,
  applyShallowPatch,
  configureUrl,
  extractSettings,
  fallbackManifestUrl,
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
