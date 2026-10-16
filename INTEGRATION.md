# Integrating SuperGrok OAuth into an Orbit-style Android assistant

## Overview

Orbit Assistant (and similar open-source Android agents) currently only supports Grok via a pasted API key. Adding SuperGrok support means:

1. Add a new provider option in Settings → AI Providers: "xAI – SuperGrok (OAuth)".
2. On selection, run the device-code flow (`SuperGrokOAuth.requestDeviceCode()`).
3. Show the user the verification URL (prefer `verification_uri_complete`) and user code. Open it in Custom Tabs or the browser.
4. Poll until the user approves.
5. Store the resulting session (access + refresh + expiry) in Android Keystore, same place Orbit stores other credentials.
6. When making chat requests, use the access token as `Authorization: Bearer ...` against `https://api.x.ai/v1`.
7. Refresh the token when it is near expiry (or on 401).

## Suggested UI flow

- Button: "Sign in with SuperGrok"
- Show a dialog/card with the code and a "Open browser" button.
- Progress indicator while polling.
- Success: mark provider as connected and list available models (you can hard-code recent Grok models or call `/v1/models`).
- Failure (including 403): offer fallback to paste an API key.

## Storage

Follow Orbit's existing pattern (Android Keystore-backed encryption). Do not put tokens in plain SharedPreferences or backups.

## Models

Common models you can offer once authenticated:
- `grok-4`
- `grok-4.5` / `grok-4.6` / `grok-4.7` (whatever the current flagship is)
- `grok-3`

Exact IDs change; prefer fetching `/v1/models` when possible.

## Error handling

- `authorization_pending` → keep polling
- `slow_down` → increase interval
- `access_denied` / expired → abort and show message
- HTTP 403 on inference → "This SuperGrok tier may not have API access via OAuth. Use an API key instead."

## Next steps to make a full fork

1. Fork https://github.com/lpnovi/Orbit-Assistant
2. Locate the provider / AI client code (search for xAI, Grok, or API key handling).
3. Add the OAuth path alongside the existing key path.
4. Wire the UI in the providers settings screen.
5. Build and sideload the APK for testing.

This repo only contains the core OAuth client so it can be copied into a fork or any similar Android agent.
