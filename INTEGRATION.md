# Integration notes for a SuperGrok OAuth path in an Orbit-style fork

This is a sketch of the work required. None of it has been implemented or tested against a live SuperGrok account.

## Provider wiring (Orbit-specific)

Orbit is Java-only. Relevant areas (approximate; confirm in source):

- Provider registration: look for `AiProvider` / `AiProviders.ALL` and how ChatGPT vs API-key providers are added.
- Preferences: provider ID stored around `Prefs.java` (search for xAI or provider keys).
- Model catalog: `OrbitModelCatalog` or equivalent; query `/v1/models` rather than hard-coding version numbers.
- Settings UI: `AiProvidersActivity` (or similar) for the sign-in card.
- HTTP client: Orbit uses `HttpURLConnection`. Model the OAuth client on whatever pattern Orbit already uses for ChatGPT device-code or token refresh (the review referenced `ChatGptAuth.java` with device code around lines 99-215 and single-flight refresh around 392-569; that file name was not found via public search, so verify the actual class).
- Token storage: Android Keystore-backed encryption (Orbit already does this for API keys). Exclude from backups. No plaintext fallback.
- 401 handling: refresh then retry once. Serialize refreshes to avoid rotation races. On `invalid_grant` clear the session and force re-auth.
- 403 handling: treat as no entitlement for the OAuth surface; do not retry or loop; offer API-key fallback.
- Base URL: make the xAI endpoint configurable rather than hard-coded.

## deviceActions flag

A reviewer noted that Orbit's XaiProvider declares `deviceActions(false)` while only ChatGptProvider has `true`. If confirmed in source, this means Grok (even with a valid token) will not receive the device-action tools (alarms, calendar, etc.) that ChatGPT mode gets. Enabling it would be a separate change and should be tested carefully.

## Fork hygiene

- Change `applicationId` (do not keep `com.orbit.assistant`).
- Change app name and icon; do not use Grok/xAI trademarks without permission.
- Disable or repoint any updater or distribution code that hard-codes the upstream package name or certificate SHA.
- Use your own keystore for signing. Sideload only; do not publish to the Play Store under Orbit's identity.
- Retain Orbit's MPL-2.0 LICENSE and notices. Add SPDX headers to new files. Publish the fork source.

## Minimal scopes

Request only the scopes actually needed. Do not request `api-keys:write`, `billing:write`, or `orgs:write`. Provide a revoke path via `https://auth.x.ai/oauth2/revoke` on sign-out.

## Proof required before any app code

Before writing integration code, obtain a token via the device-code flow on a real SuperGrok account and confirm a successful call (name the exact endpoint and model). Until that is done, treat the API surface as unproven.
