# orbit-supergrok-oauth

**Sketch and integration notes only.** This is not a working, tested, drop-in client for Orbit Assistant or any other app.

It contains a Java sketch of the SuperGrok / xAI device-code OAuth flow (RFC 8628) that other tools use, plus documentation on the risks and the work required to integrate it into a fork of Orbit Assistant.

## Important warnings

- **Terms / ban risk**: The client ID `b1a00492-073a-47ea-816f-4c329264a828` is the official Grok CLI client. xAI has no public third-party client registration and no documented permission to reuse it. Doing so means impersonating the official CLI. xAI's AUP and ToS allow account suspension. This is fine at most for personal experimentation. The sanctioned route is an API key from console.x.ai.
- **API surface unproven**: No official xAI documentation confirms that a SuperGrok subscription token works as a Bearer token on `https://api.x.ai/v1/chat/completions`. Some tools use the Responses API; others use proxy endpoints. Orbit currently only calls `/v1/chat/completions`. A real successful call with a token from this flow must be proven before any production use.
- **403 by tier**: Login can succeed while subsequent requests or refreshes return 403 depending on the SuperGrok tier. Re-login does not help. Treat 403 as "no entitlement" and fall back to an API key.
- **Not Orbit-compatible as-is**: Orbit Assistant is Java-only (no Kotlin, no OkHttp, no kotlinx.serialization, no coroutines). It uses `HttpURLConnection` and `org.json`. This sketch is written in Java with `HttpURLConnection` to match that style, but it is still not wired into Orbit's provider system.

## Contents

- `src/main/java/com/example/supergrok/SuperGrokAuth.java` — Java sketch of device-code login, polling, and refresh. Uses only JDK + Android-compatible APIs. Addresses several review issues (RFC 8628 error parsing, refresh-token preservation, 403 handling, timeouts, no token leak in toString).
- `INTEGRATION.md` — Accurate notes on what a real Orbit fork would require.
- `.github/workflows/build.yml` — Minimal CI that compiles the Java sketch.

## What this does not include

- A full Android app or APK.
- Storage in Android Keystore / SecureStore.
- UI for sign-in.
- Integration with Orbit's `AiProvider`, `Prefs`, model catalog, or device-action flags.
- Proof that a token from this flow can complete a chat completion on a real SuperGrok account.
- Any permission or endorsement from xAI.

## Open-source Android assistants (fact corrections)

Several exist. Examples with sources:

- Orbit Assistant: https://github.com/lpnovi/Orbit-Assistant (MPL-2.0, Java)
- AI-Buddy: https://github.com/Weltenwanderer1/ai-buddy (MIT, Flutter)
- BetterAIChat: https://github.com/Verlintas/BetterAIChat (MIT)
- Gotcha: https://github.com/samosa-ai-com/Gotcha (AGPL-3.0)
- Ophoner: https://github.com/aaditagrawal/ophoner

Ferri was not located. Claims that the official Grok app or Grok Bot cannot be registered as Android's default digital assistant were not verified against source.

## License

MIT. Any fork of Orbit Assistant must retain Orbit's MPL-2.0 LICENSE and notices.
