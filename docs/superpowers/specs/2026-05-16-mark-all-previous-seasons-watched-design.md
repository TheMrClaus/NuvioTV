# Mark All Previous Seasons as Watched

**Status:** Approved
**Date:** 2026-05-16
**Scope:** `app-tv` only

## Summary

Add a new entry to the season long-press context menu on the series Detail screen: **"Mark all previous seasons as watched"**. When the user holds the remote SELECT (or KEYCODE_MENU) on Season N, the menu offers — in addition to the existing watched/unwatched toggle — an action that marks every episode of every season `1..N-1` as watched. Specials (season 0) are excluded. The current season is not affected (it has its own existing action). The new option is hidden when there are no previous regular seasons (i.e. when the user is holding on Season 1, or only Specials exist before).

No inverse "unwatch all previous" action is added.

## Motivation

We already support "Mark current season as watched" via the season long-press menu, and "Mark previous episodes in this season as watched" via the episode long-press menu. The natural complement — bulk-marking all earlier seasons — is missing. Users picking up a show mid-run currently have to enter each prior season tab and mark them individually.

## Non-goals

- No new repository, sync, Trakt, Supabase, or DataStore code.
- No "Mark all previous seasons as UNwatched" inverse action.
- No new long-press surfaces — reuses the existing season tab long-press flow.
- No changes to the episode-level "Mark previous episodes in this season" action.

## Design

### Trigger and menu

The long-press trigger on season tabs is already implemented in `app-tv/src/main/java/com/omnio/tv/ui/screens/detail/EpisodesSection.kt:160` (handles `KEYCODE_MENU` and long-press on SELECT). It calls `onSeasonLongPress(season)` which `MetaDetailsScreen.kt:1407` uses to set `seasonOptionsDialogSeason`, rendering `SeasonOptionsDialog` at `MetaDetailsScreen.kt:1671`.

`SeasonOptionsDialog` (`EpisodesSection.kt:927`) today contains a single button — the watched/unwatched toggle for the selected season. We add a second button below it, conditional on `hasPreviousSeasons`.

### Scope of "previous"

For a long-press on Season N:

- Mark episodes where `season in 1..(N-1)` and `episode != null`.
- Excludes season 0 (Specials).
- Excludes the current season (covered by the existing action).
- If there are gaps in season numbering (e.g. show has seasons 1, 3, 4 and user holds Season 4), all available episodes in seasons 1 and 3 are marked — the filter is on the actual `Video.season` values present in `meta.videos`, not on a numeric range alone.

### Visibility

`hasPreviousSeasons = seasons.any { it in 1 until season }` where `seasons` is the existing derived list of season numbers already in scope at the dialog render site. When false, the new button is not rendered (no greyed-out state, no toast-on-empty).

### Inverse action

Not added. Symmetric with the existing episode-level "Mark previous episodes in this season as watched" which also has no inverse.

## Implementation

### 1. `EpisodesSection.kt` — extend `SeasonOptionsDialog`

Add two parameters and a conditional button:

```kotlin
@Composable
fun SeasonOptionsDialog(
    season: Int,
    isFullyWatched: Boolean,
    hasPreviousSeasons: Boolean,                              // NEW
    onDismiss: () -> Unit,
    onMarkSeasonWatched: () -> Unit,
    onMarkSeasonUnwatched: () -> Unit,
    onMarkAllPreviousSeasonsWatched: () -> Unit               // NEW
) {
    OmnioDialog(...) {
        Button(/* existing watched/unwatched toggle */)

        if (hasPreviousSeasons) {
            Spacer(Modifier.height(8.dp))
            Button(onClick = onMarkAllPreviousSeasonsWatched) {
                Text(stringResource(R.string.episodes_mark_all_previous_seasons_watched))
            }
        }
    }
}
```

### 2. `MetaDetailsScreen.kt` — render-site wiring (~L1671)

```kotlin
seasonOptionsDialogSeason?.let { season ->
    SeasonOptionsDialog(
        season = season,
        isFullyWatched = isSeasonFullyWatched(season),
        hasPreviousSeasons = seasons.any { it in 1 until season },   // NEW
        onDismiss = { seasonOptionsDialogSeason = null },
        onMarkSeasonWatched = {
            onMarkSeasonWatched(season); seasonOptionsDialogSeason = null
        },
        onMarkSeasonUnwatched = {
            onMarkSeasonUnwatched(season); seasonOptionsDialogSeason = null
        },
        onMarkAllPreviousSeasonsWatched = {                          // NEW
            onMarkAllPreviousSeasonsWatched(season)
            seasonOptionsDialogSeason = null
        }
    )
}
```

A new `onMarkAllPreviousSeasonsWatched: (Int) -> Unit` callback is threaded through the same parameter chain as the existing `onMarkSeasonWatched`. The screen's outer caller emits `MetaDetailsEvent.OnMarkAllPreviousSeasonsWatched(season)`.

### 3. `MetaDetailsUiState.kt` — new event (~L89)

```kotlin
data class OnMarkAllPreviousSeasonsWatched(val season: Int) : MetaDetailsEvent()
```

### 4. `MetaDetailsViewModel.kt` — dispatch + handler

Dispatch (next to existing season events ~L287):

```kotlin
is MetaDetailsEvent.OnMarkAllPreviousSeasonsWatched ->
    markAllPreviousSeasonsWatched(event.season)
```

Handler (mirror of `markSeasonWatched` at L1647, only the filter differs):

```kotlin
private fun markAllPreviousSeasonsWatched(currentSeason: Int) {
    val meta = _uiState.value.meta ?: return
    if (currentSeason <= 1) return
    suppressSeasonAutoSwitch = true
    viewModelScope.launch {
        val episodes = meta.videos.filter { v ->
            val s = v.season ?: return@filter false
            s in 1 until currentSeason && v.episode != null
        }
        val unwatched = episodes.filter { video ->
            val s = video.season!!; val e = video.episode!!
            val isWatched = _uiState.value.episodeProgressMap[s to e]?.isCompleted() == true
                || _uiState.value.watchedEpisodes.contains(s to e)
            !isWatched
        }
        if (unwatched.isEmpty()) {
            showMessage(context.getString(R.string.detail_all_previous_seasons_watched))
            return@launch
        }

        val pendingKeys = unwatched.map { episodePendingKey(it) }.toSet()
        _uiState.update { it.copy(
            episodeWatchedPendingKeys = it.episodeWatchedPendingKeys + pendingKeys
        ) }

        runCatching {
            val progressList = unwatched.map { buildCompletedEpisodeProgress(meta, it) }
            watchProgressRepository.markAsCompletedBatch(progressList)
        }.onFailure { error ->
            Log.w(TAG, "Failed to batch mark previous seasons < $currentSeason watched: ${error.message}")
        }

        _uiState.update { it.copy(
            episodeWatchedPendingKeys = it.episodeWatchedPendingKeys - pendingKeys
        ) }
        showMessage(context.getString(R.string.detail_marked_episodes_watched, unwatched.size))
    }
}
```

Reuses every helper already in the file: `buildCompletedEpisodeProgress`, `episodePendingKey`, `episodeWatchedPendingKeys`, `showMessage`, `suppressSeasonAutoSwitch`, and `watchProgressRepository.markAsCompletedBatch`.

### 5. String resources (`app-tv/src/main/res/values/strings.xml`)

```xml
<string name="episodes_mark_all_previous_seasons_watched">Mark all previous seasons as watched</string>
<string name="detail_all_previous_seasons_watched">All previous seasons already watched</string>
```

The success toast reuses the existing `detail_marked_episodes_watched` (formatted with the unwatched-count).

## Sync behavior

Because the handler routes through `WatchProgressRepository.markAsCompletedBatch(...)` (`core-data/src/main/kotlin/com/omnio/tv/data/repository/WatchProgressRepositoryImpl.kt:781`), the existing per-source policy applies automatically:

- **Trakt-primary user:** one batched `POST /sync/history` call via `traktProgressService.markSeasonWatchedBatch(...)` (`TraktProgressService.kt:1802`). The Trakt batch DTO already groups episodes by season, so a multi-season batch is one HTTP request. Optimistic UI per-episode, rollback on Trakt failure.
- **Nuvio/Supabase-primary user:** local DataStore write first (`WatchProgressPreferences.markAsCompletedBatch`, `WatchedItemsPreferences.markAsWatchedBatch`), then debounced `triggerRemoteSync()` and `triggerWatchedItemsSync()` push to Supabase, with an opportunistic Trakt mirror if a Trakt connection is also present.

A 5-season show where the user marks all previous on Season 5 results in: one Trakt API call (Trakt-primary), or one DataStore batch + one debounced Supabase push (Nuvio-primary). Same cost profile as the existing "mark current season".

## Edge cases

| Case | Behavior |
|---|---|
| User holds on Season 1 | New button hidden (`hasPreviousSeasons == false`). |
| User holds on Specials (season 0) | `currentSeason <= 1` short-circuits handler; button also hidden since `seasons.any { it in 1 until 0 }` is false. |
| All previous episodes already watched | Toast: "All previous seasons already watched". Repo not called. |
| Show with season gaps (e.g. 1, 3, 4), hold on Season 4 | Marks all available episodes in seasons 1 and 3. Season 2 doesn't exist so nothing to mark there. |
| Episode without a season number | Filtered out by `v.season ?: return@filter false`. Consistent with existing handlers. |
| Repo throws (e.g. Trakt network error) | Pending keys rolled back in `finally`-style update; Trakt optimistic updates rolled back by `TraktProgressService` existing logic. |

## Testing

Unit tests in `app-tv/src/test/java/com/omnio/tv/ui/screens/detail/` (location/file pattern follows existing season-related tests):

1. **`markAllPreviousSeasonsWatched_marksOnlySeasons1ToNMinus1`** — 4-season show, invoke on season 3 → assert `watchProgressRepository.markAsCompletedBatch` called once with exactly seasons 1 & 2 episodes; seasons 3 & 4 untouched.
2. **`markAllPreviousSeasonsWatched_excludesSpecials`** — show with season 0 (specials) and seasons 1-3, invoke on season 3 → repo batch contains no `season == 0` entry.
3. **`markAllPreviousSeasonsWatched_onSeason1_isNoOp`** — invoke on season 1 → repo never called.
4. **`markAllPreviousSeasonsWatched_skipsAlreadyWatched`** — pre-seed `episodeProgressMap` with some prior episodes completed → batch contains only the unwatched ones.
5. **`markAllPreviousSeasonsWatched_allWatched_showsToast`** — every prior episode already watched → repo not called, toast `detail_all_previous_seasons_watched` emitted.

If a Compose UI test file exists for `SeasonOptionsDialog`, add a test verifying the new button is hidden when `hasPreviousSeasons == false` and visible/clickable when true. Otherwise the dialog wiring is covered transitively by ViewModel tests.

## Files touched

| File | Change |
|---|---|
| `app-tv/src/main/java/com/omnio/tv/ui/screens/detail/EpisodesSection.kt` | Add 2 params + conditional button to `SeasonOptionsDialog` |
| `app-tv/src/main/java/com/omnio/tv/ui/screens/detail/MetaDetailsScreen.kt` | Compute `hasPreviousSeasons`, thread new callback, plumb new `(Int) -> Unit` param through screen signature |
| `app-tv/src/main/java/com/omnio/tv/ui/screens/detail/MetaDetailsUiState.kt` | New `OnMarkAllPreviousSeasonsWatched` event |
| `app-tv/src/main/java/com/omnio/tv/ui/screens/detail/MetaDetailsViewModel.kt` | New `markAllPreviousSeasonsWatched(...)` handler + dispatch case |
| `app-tv/src/main/res/values/strings.xml` | Two new strings |
| `app-tv/src/test/java/com/omnio/tv/ui/screens/detail/MetaDetailsViewModelTest.kt` (or new file) | 5 new unit tests |

No changes to `:core-domain`, `:core-data`, `:core-platform`, the Trakt API surface, the Supabase schema, or DataStore layout.
