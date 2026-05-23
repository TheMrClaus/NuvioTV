import type { PostgrestError } from "@supabase/supabase-js";
import type { ActionResult } from "./result";

// Maps a Postgrest error to ActionResult. If the error is a tagged conflict
// raised by one of the *_optimistic_concurrency RPCs (P0001 + message contains
// the table's conflict name, e.g. "profiles_conflict"), returns the conflict
// branch so the form can refetch + reapply. Anything else falls through to
// the generic error branch.
export function mapPostgrestError(
  error: PostgrestError,
  conflictName: string,
): ActionResult {
  if (error.code === "P0001" && error.message?.includes(conflictName)) {
    return { ok: false, conflict: true };
  }
  return { ok: false, error: error.message };
}
