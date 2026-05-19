# Source Cloud TV Handoff Design

**Date:** 2026-05-19

## Goal

Let OmnioTV hand a user off from the TV to a browser-friendly `account.omnio.tv` flow for Source Cloud advanced configuration without requiring OmnioMobile and without modifying the AIOStreams frontend.

## Current State

- `source-cloud-advanced-session` already creates a short-lived opaque token and returns:
  - `url`
  - `expiresAtEpochMillis`
  - `message`
  - `configurePassword`
  - `directConfigureUrl`
- The TV app currently maps and renders only `url`, `expiresAtEpochMillis`, and `message`.
- `web/panel` already contains the authenticated Source Cloud integration surface, including `SourceCloudAdvancedForm`, which can show the direct configure URL and password to logged-in users.
- `source-cloud-redeem-session` currently acts as an anonymous redirector to AIOStreams. That behavior does not match the desired browser-first handoff through the Omnio account panel.

## Product Direction

### Primary Flow

1. User selects `Advanced Source Config` on TV.
2. TV requests a fresh advanced session.
3. TV renders a QR handoff card using the returned `url`.
4. The QR lands on an authenticated `account.omnio.tv` route in `web/panel`.
5. If the browser is not signed in, middleware redirects to `/login?next=<handoff-route>`.
6. After login, the handoff page redeems the opaque token server-side.
7. The handoff page shows:
   - an `Open AIOStreams configure` action using `directConfigureUrl`
   - a copyable `configurePassword`
   - explanatory text that AIOStreams asks for this password when saving changes
8. User completes the actual save flow inside AIOStreams.

### Recovery Flow

- Regenerating from TV should make older sessions unusable.
- Expired, invalid, unauthorized, or superseded tokens should all render a generic retry state telling the user to generate a new QR on TV.

### Non-Goals

- No AIOStreams frontend patching or cookie injection.
- No OmnioMobile dependency for the first version.
- No automatic completion polling back into the TV app in the first cut.

## Architecture

### TV App

- Extend the advanced-session domain/DTO model to preserve `configurePassword` and `directConfigureUrl`.
- Keep TV as a thin initiator: request session, display QR, offer regenerate.
- Default TV card remains QR-first; raw password stays off the default TV UI to avoid clutter and shoulder-surfing.

### Backend

- `source-cloud-advanced-session` remains the single session creator.
- Its returned `url` should target the panel handoff route instead of a generic source domain redirect.
- Session creation should invalidate older active sessions for the same owner/profile so the newest QR is authoritative.
- `source-cloud-redeem-session` should stop redirecting straight to AIOStreams and instead become a JSON redemption endpoint intended for authenticated panel use.

### Account Panel

- Add a top-level authenticated handoff route in `web/panel` so it works before profile context is selected.
- The handoff page redeems the token server-side after auth.
- The page renders a focused handoff UI rather than dropping the user into the general integrations screen.
- The page can link onward into the profile-specific Source Cloud integrations page as a secondary action.

## Data Model And API Shape

### TV / Shared Domain

`SourceCloudAdvancedConfigSession` should become:

```kotlin
data class SourceCloudAdvancedConfigSession(
    val url: String,
    val expiresAtEpochMillis: Long? = null,
    val message: String? = null,
    val configurePassword: String? = null,
    val directConfigureUrl: String? = null
)
```

The response DTO should mirror the same two optional fields.

### Redeem Endpoint

The panel-facing redemption response should be JSON, not a 302 redirect.

Recommended payload:

```ts
{
  profileId: number,
  directConfigureUrl: string | null,
  configurePassword: string | null,
  expiresAtEpochMillis: number,
  sourceCloudSettingsPath: string
}
```

The endpoint should only return this payload for an authenticated Omnio user who owns the session.

## Security Rules

- Opaque token remains a bearer secret and must stay random and time-limited.
- Sensitive values are revealed only after Omnio auth.
- Redeem must validate:
  - token exists
  - token is active
  - token is not expired
  - authenticated user maps to the same owner as the session
- Older sessions should be invalidated when a fresh session is generated for the same owner/profile.
- The redeem endpoint should not leak whether a token failed due to expiry, wrong user, or prior invalidation.
- Raw token, password, and direct configure URL should not be logged.

## UX Details

### TV

- Keep the existing `Advanced Source Config` row.
- Replace the current QR-only card with a handoff card that:
  - renders the returned `url`
  - explains that setup continues on a phone or browser
  - retains a regenerate action
- No manual password display on TV by default.

### Panel

- Show a compact standalone handoff card with:
  - heading for Source Cloud advanced setup
  - explanation of why the AIOStreams password is needed
  - `Open configure page` button
  - copy password button
  - fallback link to the full Source Cloud integrations page for the same profile
- If the redeem response lacks direct config data, show a clear message directing the user back to TV or the integrations page.

## Testing Strategy

### TV

- DTO mapping test for the new advanced-session fields.
- Repository test that preserves the fields.
- ViewModel test that stores the richer session.

### Backend

- Advanced-session coverage for panel-targeted URL generation.
- Redemption coverage for:
  - valid authorized token
  - expired token
  - unauthorized token
  - superseded token after regenerate

### Panel

- Route test or server-action test for redeem success and failure states where practical.
- Manual verification for login redirect continuation and copy/open actions.

## Implementation Order

1. Extend TV/shared models and tests for the richer advanced session.
2. Convert the backend URL target and redemption contract.
3. Add the panel handoff route and focused UI.
4. Update TV UI to render the revised QR handoff card.
5. Run targeted verification across `core-data`, `app-tv`, `supabase/functions`, and `web/panel`.
