Patch series for a real Orbit fork with SuperGrok sign-in.

Apply with `git am patches/orbit-supergrok-series.patch` onto lpnovi/Orbit-Assistant at 32655b8 (verified to apply cleanly).

Commits: rename applicationId; disable self-update and Orbit Local; SecureStore encrypted SuperGrok session; SuperGrokAuth device-code sign-in with single-flight refresh; XaiProvider uses the token when no API key is saved; sign-in/sign-out UI; privacy docs.

Then delete upstream release.yml, candidate.yml and badge workflows, and add fork-release.yml to .github/workflows/.

Not yet compiled or live-tested.
