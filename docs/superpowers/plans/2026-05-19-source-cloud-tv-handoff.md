# Source Cloud TV Handoff Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let OmnioTV generate a QR that lands on an authenticated Omnio account-panel handoff page, where the user can open AIOStreams configure and copy the save password without depending on OmnioMobile or patching AIOStreams.

**Architecture:** Extend the existing advanced-session payload end-to-end, repoint the session URL at a new `web/panel` handoff route, and convert `source-cloud-redeem-session` from an anonymous redirector into an authenticated JSON redemption endpoint used by the panel. Keep the TV app thin: it only requests a session and renders the QR card.

**Tech Stack:** Kotlin, Jetpack Compose TV, Supabase Edge Functions, Next.js App Router, server actions, Vitest, JUnit4.

---

## File Map

- Modify: `core-domain/src/main/kotlin/com/omnio/tv/domain/model/SourceCloud.kt`
- Modify: `core-data/src/main/kotlin/com/omnio/tv/data/remote/dto/sourcecloud/SourceCloudDtos.kt`
- Modify: `core-data/src/test/kotlin/com/omnio/tv/data/repository/SourceCloudRepositoryImplTest.kt`
- Modify: `app-tv/src/test/java/com/omnio/tv/ui/screens/settings/SourceCloudSettingsViewModelTest.kt`
- Modify: `app-tv/src/main/java/com/omnio/tv/ui/screens/settings/SourceCloudSettingsContent.kt`
- Modify: `supabase/functions/source-cloud-advanced-session/index.ts`
- Modify: `supabase/functions/source-cloud-redeem-session/index.ts`
- Modify: `web/panel/lib/actions/sourcecloud.ts`
- Create: `web/panel/app/source-cloud/handoff/[token]/page.tsx`
- Create: `web/panel/components/forms/SourceCloudTvHandoffCard.tsx`

### Task 1: Shared Advanced Session Payload

**Files:**
- Modify: `core-domain/src/main/kotlin/com/omnio/tv/domain/model/SourceCloud.kt`
- Modify: `core-data/src/main/kotlin/com/omnio/tv/data/remote/dto/sourcecloud/SourceCloudDtos.kt`
- Test: `core-data/src/test/kotlin/com/omnio/tv/data/repository/SourceCloudRepositoryImplTest.kt`

- [ ] Step 1: Write the failing repository test for the two new fields.
- [ ] Step 2: Run `ANDROID_HOME=$HOME/Android/Sdk ./gradlew :core-data:testDebugUnitTest --tests "com.omnio.tv.data.repository.SourceCloudRepositoryImplTest"` and confirm the new assertion fails for missing fields.
- [ ] Step 3: Add `configurePassword` and `directConfigureUrl` to the domain model and DTO mapping.
- [ ] Step 4: Re-run the same `:core-data` test task and confirm it passes.

### Task 2: TV ViewModel And Handoff Card

**Files:**
- Modify: `app-tv/src/test/java/com/omnio/tv/ui/screens/settings/SourceCloudSettingsViewModelTest.kt`
- Modify: `app-tv/src/main/java/com/omnio/tv/ui/screens/settings/SourceCloudSettingsContent.kt`

- [ ] Step 1: Write the failing ViewModel test showing the richer advanced session is preserved.
- [ ] Step 2: Run `ANDROID_HOME=$HOME/Android/Sdk ./gradlew :app-tv:testDebugUnitTest --tests "com.omnio.tv.ui.screens.settings.SourceCloudSettingsViewModelTest"` and confirm it fails for the new fields.
- [ ] Step 3: Update the TV QR card copy so it reads as a browser handoff card while keeping QR + regenerate behavior minimal.
- [ ] Step 4: Re-run the same `:app-tv` test task and confirm it passes.

### Task 3: Advanced Session URL Target And Regeneration Safety

**Files:**
- Modify: `supabase/functions/source-cloud-advanced-session/index.ts`

- [ ] Step 1: Add or update a failing backend test if the function test harness already exists; otherwise document this as manual verification and keep code change minimal.
- [ ] Step 2: Change the generated `url` to target `account.omnio.tv` / panel handoff route.
- [ ] Step 3: Invalidate older active sessions for the same `user_id` + `profile_id` before inserting the new session.
- [ ] Step 4: Run the relevant Supabase function verification command available in the repo, or at minimum TypeScript build/lint for the panel if the functions lack local tests.

### Task 4: Authenticated Redeem Endpoint

**Files:**
- Modify: `supabase/functions/source-cloud-redeem-session/index.ts`
- Modify: `web/panel/lib/actions/sourcecloud.ts`

- [ ] Step 1: Write the failing panel-side test if there is an existing test seam; otherwise start by shaping the action contract and verify via TypeScript.
- [ ] Step 2: Convert redeem from anonymous redirect to authenticated JSON response with owner validation.
- [ ] Step 3: Add a panel server-action helper that calls the redeem endpoint with the current Supabase session.
- [ ] Step 4: Run `npm test` or targeted verification in `web/panel` if tests are practical, plus `npm run build` or `npx tsc --noEmit` if needed.

### Task 5: Panel Handoff Route

**Files:**
- Create: `web/panel/app/source-cloud/handoff/[token]/page.tsx`
- Create: `web/panel/components/forms/SourceCloudTvHandoffCard.tsx`

- [ ] Step 1: Write a minimal failing test if there is already route/component coverage infrastructure; otherwise verify by TypeScript/build.
- [ ] Step 2: Add an authenticated page that redeems the token server-side and renders success / expired-invalid states.
- [ ] Step 3: Add a focused card component with open-configure, copy-password, and integrations-page actions.
- [ ] Step 4: Run panel verification and manually verify login redirect via `/login?next=...` still lands back on the handoff page.

### Task 6: End-To-End Verification

**Files:**
- No new files.

- [ ] Step 1: Run `ANDROID_HOME=$HOME/Android/Sdk ./gradlew :core-data:testDebugUnitTest --tests "com.omnio.tv.data.repository.SourceCloudRepositoryImplTest"`.
- [ ] Step 2: Run `ANDROID_HOME=$HOME/Android/Sdk ./gradlew :app-tv:testDebugUnitTest --tests "com.omnio.tv.ui.screens.settings.SourceCloudSettingsViewModelTest"`.
- [ ] Step 3: Run `npm test` in `web/panel` if lightweight, otherwise `npm run build`.
- [ ] Step 4: Manually verify: generate QR on TV, scan while logged out, sign in, land on handoff page, copy password, open configure page, regenerate and confirm stale link fails.
