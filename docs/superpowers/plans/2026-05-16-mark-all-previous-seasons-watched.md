# Mark All Previous Seasons as Watched — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a new long-press menu entry on a season tab — "Mark all previous seasons as watched" — that bulk-marks every episode of every season `1..N-1` (Specials excluded) as watched, reusing the existing watch-progress pipeline.

**Architecture:** Purely additive change in the `:app-tv` module. Extracts the pure season-filter rule into a small helper for unit testing, then wires a new `MetaDetailsEvent` → ViewModel handler → `SeasonOptionsDialog` button. Reuses `WatchProgressRepository.markAsCompletedBatch(...)` which already handles Trakt/Supabase/DataStore branching.

**Tech Stack:** Kotlin, Jetpack Compose (androidx.tv.material3), Hilt, kotlinx.coroutines, JUnit 4 + MockK + kotlinx-coroutines-test. Build: `./gradlew :app-tv:testDebugUnitTest` (requires `ANDROID_HOME=$HOME/Android/Sdk`).

**Spec:** `docs/superpowers/specs/2026-05-16-mark-all-previous-seasons-watched-design.md`

---

## File Map

| File | Status | Responsibility |
|---|---|---|
| `app-tv/src/main/java/com/omnio/tv/ui/screens/detail/MarkPreviousSeasonsSelector.kt` | **Create** | Pure function: given `Meta`, current season N, and an "is watched" predicate, return the `List<Video>` of unwatched episodes in seasons `1..N-1` (excluding Specials and episodes without a season/episode number). |
| `app-tv/src/test/java/com/omnio/tv/ui/screens/detail/MarkPreviousSeasonsSelectorTest.kt` | **Create** | Unit tests for the selector covering all spec edge cases. |
| `app-tv/src/main/java/com/omnio/tv/ui/screens/detail/MetaDetailsUiState.kt` | **Modify** (line 89) | Add `OnMarkAllPreviousSeasonsWatched(season: Int)` event. |
| `app-tv/src/main/java/com/omnio/tv/ui/screens/detail/MetaDetailsViewModel.kt` | **Modify** (lines 289, ~1721) | Dispatch new event → new `markAllPreviousSeasonsWatched(season)` handler that uses the selector and calls `watchProgressRepository.markAsCompletedBatch(...)`. |
| `app-tv/src/main/java/com/omnio/tv/ui/screens/detail/MetaDetailsScreen.kt` | **Modify** (lines 521, 719, 1671) | Add `onMarkAllPreviousSeasonsWatched: (Int) -> Unit` parameter, dispatch new event, thread to `SeasonOptionsDialog`, compute `hasPreviousSeasons` from existing `seasons` list. |
| `app-tv/src/main/java/com/omnio/tv/ui/screens/detail/EpisodesSection.kt` | **Modify** (line 927) | Extend `SeasonOptionsDialog` signature with `hasPreviousSeasons: Boolean` and `onMarkAllPreviousSeasonsWatched: () -> Unit`; add a conditional `Button`. |
| `app-tv/src/main/res/values/strings.xml` | **Modify** | Add two new strings: button label + "already watched" toast. |

---

## Task 1: Extract pure season-filter helper (TDD)

**Files:**
- Create: `app-tv/src/main/java/com/omnio/tv/ui/screens/detail/MarkPreviousSeasonsSelector.kt`
- Test: `app-tv/src/test/java/com/omnio/tv/ui/screens/detail/MarkPreviousSeasonsSelectorTest.kt`

The new rule is the only piece of logic genuinely worth unit testing. Extracting it as a top-level pure function avoids building a mock harness for the 21-dependency `MetaDetailsViewModel`.

- [ ] **Step 1: Write the failing test file**

Create `app-tv/src/test/java/com/omnio/tv/ui/screens/detail/MarkPreviousSeasonsSelectorTest.kt`:

```kotlin
package com.omnio.tv.ui.screens.detail

import com.omnio.tv.domain.model.Meta
import com.omnio.tv.domain.model.Video
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkPreviousSeasonsSelectorTest {

    private fun video(season: Int?, episode: Int?, id: String = "s${season}e${episode}") =
        Video(
            id = id,
            title = id,
            released = null,
            thumbnail = null,
            streams = emptyList(),
            season = season,
            episode = episode,
            overview = null,
            runtime = null,
            available = null
        )

    private fun meta(videos: List<Video>) = Meta(
        id = "tt0",
        type = "series",
        apiType = "series",
        name = "Test Show",
        poster = null,
        background = null,
        logo = null,
        description = null,
        releaseInfo = null,
        runtime = null,
        genres = emptyList(),
        cast = emptyList(),
        director = emptyList(),
        writer = emptyList(),
        imdbRating = null,
        videos = videos
    )

    private val alwaysUnwatched: (Int, Int) -> Boolean = { _, _ -> false }

    @Test
    fun `returns episodes from seasons 1 to N-1 only`() {
        val m = meta(listOf(
            video(1, 1), video(1, 2),
            video(2, 1), video(2, 2),
            video(3, 1), video(3, 2),
            video(4, 1)
        ))

        val result = selectPreviousSeasonsEpisodes(m, currentSeason = 3, isWatched = alwaysUnwatched)

        assertEquals(setOf(1 to 1, 1 to 2, 2 to 1, 2 to 2), result.map { it.season!! to it.episode!! }.toSet())
    }

    @Test
    fun `excludes specials season 0`() {
        val m = meta(listOf(
            video(0, 1), video(0, 2),  // specials
            video(1, 1),
            video(2, 1),
            video(3, 1)
        ))

        val result = selectPreviousSeasonsEpisodes(m, currentSeason = 3, isWatched = alwaysUnwatched)

        assertTrue("must not include season 0", result.none { it.season == 0 })
        assertEquals(setOf(1 to 1, 2 to 1), result.map { it.season!! to it.episode!! }.toSet())
    }

    @Test
    fun `season 1 returns empty list`() {
        val m = meta(listOf(video(1, 1), video(1, 2), video(2, 1)))

        val result = selectPreviousSeasonsEpisodes(m, currentSeason = 1, isWatched = alwaysUnwatched)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `season 0 (specials) returns empty list`() {
        val m = meta(listOf(video(0, 1), video(1, 1), video(2, 1)))

        val result = selectPreviousSeasonsEpisodes(m, currentSeason = 0, isWatched = alwaysUnwatched)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `filters out already watched episodes`() {
        val m = meta(listOf(video(1, 1), video(1, 2), video(2, 1), video(2, 2)))
        val watched: (Int, Int) -> Boolean = { s, e -> s == 1 && e == 1 }

        val result = selectPreviousSeasonsEpisodes(m, currentSeason = 3, isWatched = watched)

        assertEquals(setOf(1 to 2, 2 to 1, 2 to 2), result.map { it.season!! to it.episode!! }.toSet())
    }

    @Test
    fun `handles season gaps`() {
        // Show has seasons 1, 3, 4 (no season 2). User holds on Season 4.
        val m = meta(listOf(video(1, 1), video(3, 1), video(3, 2), video(4, 1)))

        val result = selectPreviousSeasonsEpisodes(m, currentSeason = 4, isWatched = alwaysUnwatched)

        assertEquals(setOf(1 to 1, 3 to 1, 3 to 2), result.map { it.season!! to it.episode!! }.toSet())
    }

    @Test
    fun `skips videos with null season or null episode`() {
        val m = meta(listOf(
            video(null, 1, id = "no-season"),
            video(1, null, id = "no-episode"),
            video(1, 1)
        ))

        val result = selectPreviousSeasonsEpisodes(m, currentSeason = 2, isWatched = alwaysUnwatched)

        assertEquals(setOf(1 to 1), result.map { it.season!! to it.episode!! }.toSet())
    }
}
```

- [ ] **Step 2: Run the test to verify it fails to compile**

```bash
ANDROID_HOME=$HOME/Android/Sdk ./gradlew :app-tv:compileDebugUnitTestKotlin
```

Expected: compilation failure — `Unresolved reference: selectPreviousSeasonsEpisodes`.

**Note for the implementer:** Before running, open `app-tv/src/main/java/com/omnio/tv/domain/model/Meta.kt` (or `core-domain/.../Meta.kt`) and verify the `Meta` constructor parameter names used in the test (`type`, `apiType`, `name`, `poster`, `background`, `logo`, `description`, `releaseInfo`, `runtime`, `genres`, `cast`, `director`, `writer`, `imdbRating`, `videos`). If any name differs in your branch, update the test's `meta(...)` helper to match. The `Video` constructor matches `Meta.kt:99` in the spec; do not change it.

- [ ] **Step 3: Create the implementation file**

Create `app-tv/src/main/java/com/omnio/tv/ui/screens/detail/MarkPreviousSeasonsSelector.kt`:

```kotlin
package com.omnio.tv.ui.screens.detail

import com.omnio.tv.domain.model.Meta
import com.omnio.tv.domain.model.Video

/**
 * Selects unwatched episodes from all seasons strictly before [currentSeason].
 *
 * Rules:
 * - Includes seasons in `1..(currentSeason - 1)` only (Specials / season 0 excluded).
 * - Excludes videos with a null `season` or null `episode` (extras, trailers, etc.).
 * - Excludes episodes already considered watched per [isWatched].
 *
 * Returns an empty list when [currentSeason] <= 1 or when no episodes match.
 */
fun selectPreviousSeasonsEpisodes(
    meta: Meta,
    currentSeason: Int,
    isWatched: (season: Int, episode: Int) -> Boolean
): List<Video> {
    if (currentSeason <= 1) return emptyList()
    return meta.videos.filter { v ->
        val s = v.season ?: return@filter false
        val e = v.episode ?: return@filter false
        s in 1 until currentSeason && !isWatched(s, e)
    }
}
```

- [ ] **Step 4: Run the tests and verify they pass**

```bash
ANDROID_HOME=$HOME/Android/Sdk ./gradlew :app-tv:testDebugUnitTest --tests "com.omnio.tv.ui.screens.detail.MarkPreviousSeasonsSelectorTest"
```

Expected: BUILD SUCCESSFUL, 7 tests pass.

- [ ] **Step 5: Commit**

```bash
git add app-tv/src/main/java/com/omnio/tv/ui/screens/detail/MarkPreviousSeasonsSelector.kt \
        app-tv/src/test/java/com/omnio/tv/ui/screens/detail/MarkPreviousSeasonsSelectorTest.kt
git commit -m "feat(detail): add selectPreviousSeasonsEpisodes helper with tests"
```

---

## Task 2: Add string resources

**Files:**
- Modify: `app-tv/src/main/res/values/strings.xml`

- [ ] **Step 1: Add the two new strings**

Insert these two `<string>` entries near the existing `episodes_mark_season_watched` (line 124) and `detail_all_previous_watched` (line 141) for visual grouping:

```xml
<string name="episodes_mark_all_previous_seasons_watched">Mark all previous seasons as watched</string>
<string name="detail_all_previous_seasons_watched">All previous seasons already watched</string>
```

Suggested placement: put `episodes_mark_all_previous_seasons_watched` immediately after line 129 (`episodes_season_actions`), and `detail_all_previous_seasons_watched` immediately after line 141 (`detail_all_previous_watched`).

- [ ] **Step 2: Verify the resource file still parses**

```bash
ANDROID_HOME=$HOME/Android/Sdk ./gradlew :app-tv:processDebugResources
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app-tv/src/main/res/values/strings.xml
git commit -m "feat(detail): add strings for 'mark all previous seasons watched'"
```

---

## Task 3: Add `OnMarkAllPreviousSeasonsWatched` event

**Files:**
- Modify: `app-tv/src/main/java/com/omnio/tv/ui/screens/detail/MetaDetailsUiState.kt` (line 89)

- [ ] **Step 1: Add the event declaration**

Open `app-tv/src/main/java/com/omnio/tv/ui/screens/detail/MetaDetailsUiState.kt` and insert a new event between lines 90 and 91 (between `OnMarkSeasonUnwatched` and `OnMarkPreviousEpisodesWatched`):

Existing context (line 89-91):
```kotlin
    data class OnMarkSeasonWatched(val season: Int) : MetaDetailsEvent()
    data class OnMarkSeasonUnwatched(val season: Int) : MetaDetailsEvent()
    data class OnMarkPreviousEpisodesWatched(val video: Video) : MetaDetailsEvent()
```

Becomes:
```kotlin
    data class OnMarkSeasonWatched(val season: Int) : MetaDetailsEvent()
    data class OnMarkSeasonUnwatched(val season: Int) : MetaDetailsEvent()
    data class OnMarkAllPreviousSeasonsWatched(val season: Int) : MetaDetailsEvent()
    data class OnMarkPreviousEpisodesWatched(val video: Video) : MetaDetailsEvent()
```

- [ ] **Step 2: Verify compilation**

```bash
ANDROID_HOME=$HOME/Android/Sdk ./gradlew :app-tv:compileDebugKotlin
```

Expected: compilation **fails** with a `MetaDetailsViewModel.when` exhaustiveness warning/error pointing at the `when (event)` block around line 280 — that's the next task. If it compiles cleanly, the `when` was not exhaustive to begin with, which is also fine.

- [ ] **Step 3: Do not commit yet**

We'll commit together with Task 4 so the codebase stays buildable.

---

## Task 4: ViewModel — dispatch + handler

**Files:**
- Modify: `app-tv/src/main/java/com/omnio/tv/ui/screens/detail/MetaDetailsViewModel.kt` (line 289 and ~1721)

- [ ] **Step 1: Add the dispatch case**

Open `MetaDetailsViewModel.kt`. Locate the `when (event)` block (around line 280). Between lines 288 and 289, insert one new line so the dispatch reads:

```kotlin
            is MetaDetailsEvent.OnMarkSeasonWatched -> markSeasonWatched(event.season)
            is MetaDetailsEvent.OnMarkSeasonUnwatched -> markSeasonUnwatched(event.season)
            is MetaDetailsEvent.OnMarkAllPreviousSeasonsWatched -> markAllPreviousSeasonsWatched(event.season)
            is MetaDetailsEvent.OnMarkPreviousEpisodesWatched -> markPreviousEpisodesWatched(event.video)
```

- [ ] **Step 2: Add the handler method**

Locate `markPreviousEpisodesWatched(video: Video)` (starts at line 1722). Immediately **before** that method (between line 1721 and 1722), insert the new `markAllPreviousSeasonsWatched` method:

```kotlin
    private fun markAllPreviousSeasonsWatched(currentSeason: Int) {
        val meta = _uiState.value.meta ?: return
        if (currentSeason <= 1) return
        suppressSeasonAutoSwitch = true
        viewModelScope.launch {
            val state = _uiState.value
            val unwatched = selectPreviousSeasonsEpisodes(meta, currentSeason) { s, e ->
                state.episodeProgressMap[s to e]?.isCompleted() == true
                    || state.watchedEpisodes.contains(s to e)
            }
            if (unwatched.isEmpty()) {
                showMessage(context.getString(R.string.detail_all_previous_seasons_watched))
                return@launch
            }

            val pendingKeys = unwatched.map { episodePendingKey(it) }.toSet()
            _uiState.update {
                it.copy(episodeWatchedPendingKeys = it.episodeWatchedPendingKeys + pendingKeys)
            }

            runCatching {
                val progressList = unwatched.map { buildCompletedEpisodeProgress(meta, it) }
                watchProgressRepository.markAsCompletedBatch(progressList)
            }.onFailure { error ->
                Log.w(TAG, "Failed to batch mark previous seasons < $currentSeason as watched: ${error.message}")
            }

            _uiState.update {
                it.copy(episodeWatchedPendingKeys = it.episodeWatchedPendingKeys - pendingKeys)
            }
            showMessage(context.getString(R.string.detail_marked_episodes_watched, unwatched.size))
        }
    }

```

Note: `selectPreviousSeasonsEpisodes` is in the same package (`com.omnio.tv.ui.screens.detail`), so no import is needed. Verify by checking that the file's package declaration matches.

- [ ] **Step 3: Verify the module still compiles**

```bash
ANDROID_HOME=$HOME/Android/Sdk ./gradlew :app-tv:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit (with Task 3's UiState change)**

```bash
git add app-tv/src/main/java/com/omnio/tv/ui/screens/detail/MetaDetailsUiState.kt \
        app-tv/src/main/java/com/omnio/tv/ui/screens/detail/MetaDetailsViewModel.kt
git commit -m "feat(detail): handle OnMarkAllPreviousSeasonsWatched in ViewModel"
```

---

## Task 5: `SeasonOptionsDialog` — add the new button

**Files:**
- Modify: `app-tv/src/main/java/com/omnio/tv/ui/screens/detail/EpisodesSection.kt` (line 927)

- [ ] **Step 1: Verify required imports exist**

The file already imports `androidx.compose.foundation.layout.height` (line 16). The new button will reuse the existing `Spacer` pattern from `EpisodeOptionsDialog` — but inspecting lines 875–897 shows that file does **not** use `Spacer` between its buttons; consecutive `Button`s in the `OmnioDialog` column flow naturally. So no `Spacer` is needed and no new imports are required.

- [ ] **Step 2: Extend `SeasonOptionsDialog` signature**

Replace the existing function (lines 925–958) with:

```kotlin
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SeasonOptionsDialog(
    season: Int,
    isFullyWatched: Boolean,
    hasPreviousSeasons: Boolean,
    onDismiss: () -> Unit,
    onMarkSeasonWatched: () -> Unit,
    onMarkSeasonUnwatched: () -> Unit,
    onMarkAllPreviousSeasonsWatched: () -> Unit
) {
    val primaryFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        primaryFocusRequester.requestFocus()
    }

    OmnioDialog(
        onDismiss = onDismiss,
        title = if (season == 0) stringResource(R.string.episodes_specials) else stringResource(R.string.episodes_season, season),
        subtitle = stringResource(R.string.episodes_season_actions)
    ) {
        Button(
            onClick = if (isFullyWatched) onMarkSeasonUnwatched else onMarkSeasonWatched,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(primaryFocusRequester),
            colors = ButtonDefaults.colors(
                containerColor = OmnioColors.BackgroundCard,
                contentColor = OmnioColors.TextPrimary
            )
        ) {
            Text(if (isFullyWatched) stringResource(R.string.episodes_mark_season_unwatched) else stringResource(R.string.episodes_mark_season_watched))
        }

        if (hasPreviousSeasons) {
            Button(
                onClick = onMarkAllPreviousSeasonsWatched,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.colors(
                    containerColor = OmnioColors.BackgroundCard,
                    contentColor = OmnioColors.TextPrimary
                )
            ) {
                Text(stringResource(R.string.episodes_mark_all_previous_seasons_watched))
            }
        }
    }
}
```

The new button mirrors the existing button's styling (matches `EpisodeOptionsDialog`'s "Mark previous episodes" pattern at lines 886–897).

- [ ] **Step 3: Verify compilation (will fail — call site is wrong)**

```bash
ANDROID_HOME=$HOME/Android/Sdk ./gradlew :app-tv:compileDebugKotlin
```

Expected: compilation FAILS at `MetaDetailsScreen.kt:1672` — the existing `SeasonOptionsDialog(...)` call lacks the two new arguments. That's fixed in Task 6.

- [ ] **Step 4: Do not commit yet**

Commit together with Task 6.

---

## Task 6: `MetaDetailsScreen` — wire the new event end-to-end

**Files:**
- Modify: `app-tv/src/main/java/com/omnio/tv/ui/screens/detail/MetaDetailsScreen.kt` (lines 521, 719, 1671)

This screen has multiple Composable layers. The event must be added at three sites: the outer call that dispatches to the ViewModel (line 521), the screen-level parameter list (line 719), and the `SeasonOptionsDialog` render site (line 1671).

- [ ] **Step 1: Add the dispatch lambda at the outer call (line 521)**

Locate lines 521–529 in `MetaDetailsScreen.kt`. The existing block reads:

```kotlin
                    onMarkSeasonWatched = { season ->
                        viewModel.onEvent(MetaDetailsEvent.OnMarkSeasonWatched(season))
                    },
                    onMarkSeasonUnwatched = { season ->
                        viewModel.onEvent(MetaDetailsEvent.OnMarkSeasonUnwatched(season))
                    },
                    onMarkPreviousEpisodesWatched = { video ->
                        viewModel.onEvent(MetaDetailsEvent.OnMarkPreviousEpisodesWatched(video))
                    },
```

Insert a new lambda between `onMarkSeasonUnwatched` and `onMarkPreviousEpisodesWatched`:

```kotlin
                    onMarkSeasonWatched = { season ->
                        viewModel.onEvent(MetaDetailsEvent.OnMarkSeasonWatched(season))
                    },
                    onMarkSeasonUnwatched = { season ->
                        viewModel.onEvent(MetaDetailsEvent.OnMarkSeasonUnwatched(season))
                    },
                    onMarkAllPreviousSeasonsWatched = { season ->
                        viewModel.onEvent(MetaDetailsEvent.OnMarkAllPreviousSeasonsWatched(season))
                    },
                    onMarkPreviousEpisodesWatched = { video ->
                        viewModel.onEvent(MetaDetailsEvent.OnMarkPreviousEpisodesWatched(video))
                    },
```

- [ ] **Step 2: Add the parameter to the screen composable signature (line 719)**

Locate the parameter list around line 719. The existing block reads:

```kotlin
    onMarkSeasonWatched: (Int) -> Unit,
    onMarkSeasonUnwatched: (Int) -> Unit,
    onMarkPreviousEpisodesWatched: (Video) -> Unit,
```

Insert a new parameter between `onMarkSeasonUnwatched` and `onMarkPreviousEpisodesWatched`:

```kotlin
    onMarkSeasonWatched: (Int) -> Unit,
    onMarkSeasonUnwatched: (Int) -> Unit,
    onMarkAllPreviousSeasonsWatched: (Int) -> Unit,
    onMarkPreviousEpisodesWatched: (Video) -> Unit,
```

- [ ] **Step 3: Wire it into the `SeasonOptionsDialog` call (line 1671)**

Locate the dialog render block at lines 1671–1685 and replace it with:

```kotlin
        seasonOptionsDialogSeason?.let { season ->
            SeasonOptionsDialog(
                season = season,
                isFullyWatched = isSeasonFullyWatched(season),
                hasPreviousSeasons = seasons.any { it in 1 until season },
                onDismiss = { seasonOptionsDialogSeason = null },
                onMarkSeasonWatched = {
                    onMarkSeasonWatched(season)
                    seasonOptionsDialogSeason = null
                },
                onMarkSeasonUnwatched = {
                    onMarkSeasonUnwatched(season)
                    seasonOptionsDialogSeason = null
                },
                onMarkAllPreviousSeasonsWatched = {
                    onMarkAllPreviousSeasonsWatched(season)
                    seasonOptionsDialogSeason = null
                }
            )
        }
```

The `seasons` variable is already in scope (used at line 1400 and `SeasonTabs` at line 1404).

- [ ] **Step 4: Verify the whole module compiles**

```bash
ANDROID_HOME=$HOME/Android/Sdk ./gradlew :app-tv:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Run unit tests to ensure nothing else broke**

```bash
ANDROID_HOME=$HOME/Android/Sdk ./gradlew :app-tv:testDebugUnitTest
```

Expected: BUILD SUCCESSFUL, all existing tests + the 7 new selector tests pass.

- [ ] **Step 6: Commit (with Task 5's dialog change)**

```bash
git add app-tv/src/main/java/com/omnio/tv/ui/screens/detail/EpisodesSection.kt \
        app-tv/src/main/java/com/omnio/tv/ui/screens/detail/MetaDetailsScreen.kt
git commit -m "feat(detail): add 'Mark all previous seasons as watched' option to season menu"
```

---

## Task 7: Full build verification

**Files:** none modified.

- [ ] **Step 1: Run a full debug build**

```bash
ANDROID_HOME=$HOME/Android/Sdk ./gradlew :app-tv:assembleDebug
```

Expected: BUILD SUCCESSFUL. This catches any Compose-stability or R8 issues that pure compilation skips.

- [ ] **Step 2: Run all `:app-tv` unit tests one more time**

```bash
ANDROID_HOME=$HOME/Android/Sdk ./gradlew :app-tv:testDebugUnitTest
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Manual smoke test on device (optional but recommended)**

```bash
ANDROID_HOME=$HOME/Android/Sdk ./gradlew :app-tv:installDebug
```

Then, on an Android TV device/emulator:

1. Open any series with 3+ regular seasons.
2. Navigate to a season tab > Season 1, hold SELECT (or press the MENU key). Expect the season dialog with **only** the watched/unwatched toggle (no "Mark all previous seasons" button).
3. Navigate to Season 3, hold SELECT. Expect the dialog with both buttons.
4. Click "Mark all previous seasons as watched". Expect a toast like "Episodes marked as watched: N", and seasons 1 & 2 episodes are now shown as watched (checkmark/dimmed thumbnails).
5. Repeat on Season 3 — expect toast "All previous seasons already watched" and no repo activity.
6. If Specials (season 0) is present, navigate to Specials tab, hold SELECT. Expect the dialog with **only** the watched/unwatched toggle (no "previous" button).

If on a Trakt-primary account, verify the watched state appears on trakt.tv shortly after (within ~10 seconds). If on a Nuvio/Supabase account with no Trakt link, verify watched state persists across app restart.

- [ ] **Step 4: No commit needed** — verification step only.

---

## Self-review notes

- **Spec coverage:** every spec section (trigger, scope, visibility, no-inverse, sync behavior, all 6 edge cases) is implemented across Tasks 1–6. Edge cases are tested in Task 1 (`MarkPreviousSeasonsSelectorTest`) and exercised by the manual smoke test in Task 7.
- **Type consistency:** `onMarkAllPreviousSeasonsWatched` has the signature `(Int) -> Unit` everywhere (screen param, dispatch lambda, dialog callback receives `() -> Unit` after closing over `season`). The event is `OnMarkAllPreviousSeasonsWatched(val season: Int)` in all three sites that reference it.
- **No placeholders:** every code step shows full code; every verification step shows the exact command and expected outcome.
- **Why no ViewModel test?** `MetaDetailsViewModel` has 21 injected dependencies and no existing test scaffold in this repo. Building one would be a separate refactor project. Extracting `selectPreviousSeasonsEpisodes` as a pure helper covers 100% of the new business logic in isolation — the spec's 5 originally-proposed ViewModel tests all reduce to filter-rule assertions, which the helper tests cover directly.
