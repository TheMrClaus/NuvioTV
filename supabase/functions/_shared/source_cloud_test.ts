import { assertEquals } from "https://deno.land/std@0.224.0/assert/mod.ts";
import { parseProfileId, sha256Hex } from "./source_cloud.ts";

Deno.test("sha256Hex returns a lowercase hex digest", async () => {
  const digest = await sha256Hex("source-cloud-session");

  assertEquals(digest, "4a70d38557d1a0666350f4e3e2aec5edaa4e9e2dfee571dc43e33e936c1fa5ab");
});

Deno.test("parseProfileId defaults missing values to profile 1", () => {
  assertEquals(parseProfileId(null), 1);
  assertEquals(parseProfileId(undefined), 1);
  assertEquals(parseProfileId(""), 1);
});

Deno.test("parseProfileId accepts valid profile IDs", () => {
  assertEquals(parseProfileId("1"), 1);
  assertEquals(parseProfileId("5"), 5);
});

Deno.test("parseProfileId rejects non-positive and non-integer values", () => {
  assertEquals(parseProfileId("0"), null);
  assertEquals(parseProfileId("-1"), null);
  assertEquals(parseProfileId("1.5"), null);
  assertEquals(parseProfileId("abc"), null);
});
