# Source Cloud Backend Contract

The Android app calls only Omnio Source Cloud. AIOStreams remains a backend implementation detail.

## Required Endpoints

- `GET /v1/source-cloud/status`
- `POST /v1/source-cloud/search`
- `POST /v1/source-cloud/config/advanced-session`
- `POST /v1/source-cloud/config/reset`

## Status Response

`GET /v1/source-cloud/status` returns per-user/per-profile service and config state.

```json
{
  "config": {
    "status": "ready",
    "label": "Private source config ready",
    "message": "Configured for this profile",
    "advancedConfigAvailable": true,
    "canReset": true
  },
  "services": [
    {
      "service": "real_debrid",
      "connected": true,
      "label": "Real-Debrid",
      "message": null
    }
  ]
}
```

Supported config status keys are `unknown`, `not_provisioned`, `ready`, `provisioning_failed`, `unavailable`, and `invalid`.

## Advanced Config Session

`POST /v1/source-cloud/config/advanced-session` returns a short-lived Omnio-hosted URL for the authenticated user/profile.

```json
{
  "url": "https://source.omnio.tv/advanced/session/opaque-token",
  "expiresAtEpochMillis": 1770000000000,
  "message": "Scan to open advanced source config"
}
```

The URL must be treated as secret, must expire quickly, and should wrap/proxy/redirect to the user's private AIOStreams config without exposing shared or permanent config credentials to the app.

## Reset Config

`POST /v1/source-cloud/config/reset` resets or regenerates the authenticated user/profile Source Cloud implementation config and returns the same response shape as `/status`.

Backend failures should be safe for the app to ignore. Addons, plugins, and personal media providers must continue to work when these endpoints fail.
