# Cinematic Design Phase 6-8

## Context

Continue `feat/cinematic-design` from the current branch state after Phase 6a. The remaining work stays tightly scoped to the listed UI surfaces and should preserve existing behavior, navigation, persistence, and setting-value plumbing unless a phase explicitly requires minimal onboarding flow wiring.

Visual direction: match the repository's new cinematic language as closely as possible, with a Netflix-like premium TV feel as the benchmark. Prefer exact visual continuity with the new design system, allowing creative liberty only where a screen's structure demands it.

## Goals

1. Restyle remaining scoped addon and settings surfaces into the cinematic system without broadening changes into unrelated panels.
2. Add a real onboarding `Welcome` step before QR pairing and keep the onboarding experience visually coherent across subsequent first-launch steps.
3. Upgrade the profile picker and modern sidebar to the same cinematic finish.
4. Verify with the required Gradle commands, commit, push, and create a PR at the end.

## Non-Goals

1. No changes to unrelated settings sections or right-pane settings behavior beyond the scoped phases.
2. No profile-flow logic changes beyond visual treatment.
3. No new infrastructure work.
4. Do not stage `infra/aiostreams-selfhost/`.

## Recommended Approach

Use shared cinematic row primitives with screen-local assembly.

This gives the strongest visual continuity while keeping the behavior stable. Shared styling should live close to the current settings design system when the patterns are generic, while screen-unique layouts should remain local to the affected files.

Rejected alternatives:

- Per-screen restyles only: faster, but likely to drift in spacing, red accents, toggle treatment, and focus behavior.
- Broad settings design-system rewrite: too much scope and likely to leak into unrelated sections.

## Architecture

### UI primitives

Extend the current UI layer with small cinematic primitives rather than moving logic:

- `SettingsDesignSystem.kt`: reusable row chrome, section headers, value chips, and red/neutral toggle visuals for scoped settings usage.
- `AddonManagerScreen.kt`: local addon-row composition for the five-column installed-addon layout.
- `AuthQrSignInScreen.kt`: onboarding-specific hero, step indicator, and welcome/pair step shells.
- `ProfileSelectionScreen.kt`: local cinematic profile tile and dashed add tile treatment.
- `ModernSidebarBlurPanel.kt`: local sidebar item chrome update for gradient and active left-border treatment.

### Behavior boundaries

- Keep all existing view models and state producers unchanged where possible.
- Keep navigation targets and callbacks unchanged.
- Only add minimal step-state wiring in `MainActivity.kt` for onboarding progression.
- Preserve all existing settings persistence and dialog plumbing.

## Phase Design

### Phase 6b Part 1

#### Addon Manager

File: `app-tv/src/main/java/com/omnio/tv/ui/screens/addon/AddonManagerScreen.kt`

Installed addon rows become denser cinematic rows with five visual columns:

1. Identity: addon name, badge, version.
2. Description/status: short descriptive copy and optional status text.
3. Source details: base URL and source/catalog metadata.
4. Catalog/type summary: counts and supporting metadata.
5. Actions: move up, move down, remove.

The visual goal is a premium horizontal information rhythm rather than stacked cards. D-pad focus and button actions remain exactly as they work now.

Install, manage-from-phone, and catalog-order entry surfaces should remain behaviorally unchanged. Only minimal surrounding spacing adjustments are allowed if needed to keep the screen visually coherent.

#### Source Priority

Search target: source-order settings rows referenced by `Source Priority` or `Source Order` in settings screens.

Only restyle the source priority rows. Do not change the enclosing panel structure or unrelated settings content. The rows should inherit the same cinematic settings-row treatment but stay scoped to that list.

#### Toggle treatment

Scoped toggles should use:

- checked track: cinematic red
- unchecked track: neutral/dim surface tone
- knob: white

This should be centralized in the shared settings styling used by the scoped rows, without forcing unrelated settings areas to change.

### Phase 6b Part 2

#### Integrations hub

Files: `SettingsScreen.kt` integration hub area and the relevant settings design primitives.

Only the Integrations hub rows are restyled. Each hub entry should read like a cinematic destination row with stronger branding, clearer hierarchy, and premium focus treatment. Selecting an integration should keep the current section-switching behavior exactly as-is.

Do not restyle unrelated right-pane settings sections while doing this.

#### Playback

Files:

- `app-tv/src/main/java/com/omnio/tv/ui/screens/settings/PlaybackSettingsScreen.kt`
- `app-tv/src/main/java/com/omnio/tv/ui/screens/settings/PlaybackSettingsSections.kt`

Keep the existing section structure, dialog launching, and setting updates. Restyle the visible rows and section headers only:

- stronger cinematic section headers
- premium row spacing and hierarchy
- clearer active/focused red emphasis
- red/neutral/white toggle treatment

No changes to the actual setting-value plumbing.

#### About

File: `app-tv/src/main/java/com/omnio/tv/ui/screens/settings/AboutScreen.kt`

Restyle into a branded premium panel using the same cinematic row language as Integrations and Playback. Preserve the existing actions, URLs, and update-check behavior.

### Phase 7 Part 1

Files:

- `app-tv/src/main/java/com/omnio/tv/ui/screens/account/AuthQrSignInScreen.kt`
- `app-tv/src/main/java/com/omnio/tv/MainActivity.kt`
- `core-data/src/main/kotlin/com/omnio/tv/data/local/AppOnboardingDataStore.kt` only if truly necessary, though the preferred design avoids new persistence
- locale string resource files

#### Onboarding structure

Add a real onboarding `Welcome` step before the QR pairing step.

Preferred state model:

- keep `AppOnboardingDataStore.hasSeenAuthQrOnFirstLaunch` as the persisted gate for whether first-launch auth onboarding is complete
- add lightweight session-local step state in `MainActivity.kt` to sequence `Welcome -> Pair -> existing post-auth/profile flow`
- avoid introducing a broader persisted onboarding state machine unless implementation proves it is required

#### Welcome step

The Welcome step should be a hero-first cinematic screen that introduces the app and leads into pairing. It should feel like the opening screen of the same experience, not a generic setup page.

#### Pair step

Reuse the current QR/account logic from `AuthQrSignInScreen.kt`, but restyle the shell into the cinematic design system.

#### Step indicator

Add a step indicator that reflects onboarding progression and is designed to conceptually continue into later first-launch steps, including profile selection and any already-wired follow-up first-launch steps.

This does not require merging all later screens into one composable. It means the indicator language and sequencing should be consistent across the onboarding and profile-selection experience.

#### Locales

Mirror any newly added strings across all existing locale files. Keep changes limited to the strings needed for the new welcome and updated onboarding chrome.

### Phase 7 Part 2

File: `app-tv/src/main/java/com/omnio/tv/ui/screens/profile/ProfileSelectionScreen.kt`

Restyle the picker into cinematic profile tiles:

- stronger tile presence and depth
- more premium focus treatment
- dashed add tile
- consistent cinematic typography and metadata treatment

Do not change:

- profile selection behavior
- long-press behavior
- PIN overlays
- create/edit/delete logic
- profile management flow

If the onboarding step indicator continues visually into profile selection, integrate it in a way that does not disturb the existing callbacks or profile gate logic.

### Phase 8

Files:

- `app-tv/src/main/java/com/omnio/tv/ModernSidebarBlurPanel.kt`
- `app-tv/src/main/java/com/omnio/tv/MainActivity.kt`

#### Sidebar

Apply the final cinematic finish:

- richer sidebar gradient
- active-item red left border
- preserve existing focus, expansion, and navigation logic

Active state should be visually clearer than the current wash-only treatment while still fitting the floating cinematic sidebar language.

#### Final integration

After implementation is complete:

1. run required verification commands
2. review changed files for scope compliance
3. ensure `infra/aiostreams-selfhost/` is not staged
4. commit and push session work
5. create the PR
6. delete external plan files if they are no longer needed and doing so is appropriate at that final stage

## Data Flow

### Onboarding

- persisted completion gate: `AppOnboardingDataStore.hasSeenAuthQrOnFirstLaunch`
- session-local sequencing: `MainActivity.kt`
- auth and QR state source: existing `AccountViewModel` / account UI state
- completion behavior: unchanged remote sync and auth completion flow

### Settings

- existing settings values and callbacks remain untouched
- existing dialog launch paths remain untouched
- focus routing may be adjusted only as needed to preserve correct D-pad behavior after the visual restyle

### Profiles

- existing `ProfileSelectionViewModel` behavior remains untouched
- PIN state, profile actions, and create/edit/delete flows remain untouched

### Sidebar

- selected route logic remains untouched
- expansion/collapse and blocked-content key handling remain untouched

## Error Handling and Risks

### Main risks

1. Scope creep in settings screens if shared primitives are applied too broadly.
2. D-pad regressions from row/tile structure changes.
3. Onboarding regressions if the new welcome step interferes with the existing auth completion gate.
4. Locale drift if new strings are not mirrored consistently.

### Mitigations

1. Limit primitive adoption to the explicitly scoped files and rows.
2. Preserve existing callbacks and focus requesters where possible.
3. Keep onboarding progression session-local and leave the persisted completion gate unchanged.
4. Update all locale files in the same change as the onboarding strings.

## Testing and Verification

### Required verification commands

Run these commands for each implementation session and again before final completion claims:

```bash
ANDROID_HOME="$HOME/Android/Sdk" ./gradlew :app-tv:assembleDebug
ANDROID_HOME="$HOME/Android/Sdk" ./gradlew :app-tv:testDebugUnitTest :core-data:testDebugUnitTest :core-platform:testDebugUnitTest
```

### Testing strategy

- For pure visual restyles, rely on build and unit-test verification unless an existing targeted test needs updating.
- If onboarding step logic introduces a small isolated behavior seam, add a focused unit/regression test first.
- Do not invent broad new test infrastructure for UI-only changes.

### Completion checklist

1. Required Gradle verification passes.
2. Changed files remain inside scoped phase boundaries.
3. `infra/aiostreams-selfhost/` remains unstaged.
4. Session work is committed and pushed.
5. PR is created at the end of the final phase session.
