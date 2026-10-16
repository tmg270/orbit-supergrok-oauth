# Orbit SuperGrok OAuth

Proof-of-concept and integration guide for adding **SuperGrok / X Premium+ OAuth** (device-code flow) to Android AI assistants such as [Orbit Assistant](https://github.com/lpnovi/Orbit-Assistant).

This lets users sign in with their existing SuperGrok subscription instead of generating a separate xAI API key and buying credits.

## How it works

Uses the same public device-code (RFC 8628) flow that the official Grok CLI, Hermes Agent, OpenClaw, and other tools use:

- Client ID: `b1a00492-073a-47ea-816f-4c329264a828` (public Grok CLI client)
- Device code endpoint: `https://auth.x.ai/oauth2/device/code`
- Token endpoint: `https://auth.x.ai/oauth2/token`
- Scope: `openid profile email offline_access grok-cli:access api:access`
- API calls go to `https://api.x.ai/v1` with the access token as Bearer.

After approval the access token is refreshed in the background using the refresh token. Tokens should be stored in Android Keystore (same as Orbit already does for API keys).

## Contents

- `SuperGrokOAuth.kt` — Kotlin implementation of the device-code flow + token refresh.
- `INTEGRATION.md` — Notes on wiring this into an Orbit-style provider.

## Quick test (conceptual)

1. Call `requestDeviceCode()`.
2. Show the user the `verification_uri_complete` (or URI + user code).
3. Poll `pollForToken()` until success or timeout.
4. Store the tokens securely.
5. Use the access token for chat completions against `https://api.x.ai/v1/chat/completions` (or Responses API).

## Caveats

xAI has been observed to return HTTP 403 for some SuperGrok tiers on the OAuth API surface even when the in-app subscription is active. If that happens, fall back to an API key path. This is an unofficial integration; endpoints or client IDs can change.

## License

MIT. Orbit Assistant itself is MPL-2.0; any fork must respect that.
