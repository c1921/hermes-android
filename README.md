# Hermes for Android

[![Download latest APK and AAB](https://img.shields.io/badge/download-latest%20APK%20%2B%20AAB-0000ff?logo=android&logoColor=white)](https://github.com/luinbytes/hermes-android/actions/workflows/ci.yml?query=branch%3Amain+is%3Asuccess)

Native Android client for [Nous Research Hermes Agent](https://github.com/NousResearch/hermes-agent). The project targets first-party-quality integration with the same Dashboard backend, sessions, profiles, skills, tools, models, providers, and automations used by Hermes Desktop, CLI, and TUI.

This repository is an independent work in progress. It is not currently an official Nous Research release. Visible controls are backed by real Hermes REST or JSON-RPC/WebSocket operations; unavailable features are omitted rather than simulated.

## Project status

Last verified: 18 July 2026.

The current `main` checkout passes all unit tests, Android lint, debug APK assembly, and debug app-bundle assembly locally with JDK 17. The debug APK has also been installed and exercised on a Samsung SM-S906E running Android 16 for onboarding, light/dark theme, large-text, IME, reduced-motion, saved-session reconnect, process-restarted draft restoration, full-text session search, confirmed session deletion, confirmed live-session reset, managed workspace browsing, text and sandboxed HTML previews, and real secured upstream integration QA. The upstream smoke used an isolated Hermes home at the audited commit, temporary basic-auth credentials, and no paid provider key.

### Implemented

- [x] Dashboard username/password onboarding through `POST /auth/password-login`
- [x] Required `hermes_session_at` session-cookie extraction and validation
- [x] Authenticated `/api/status` REST validation using the Dashboard cookie
- [x] Cookie-authenticated `POST /api/auth/ws-ticket` followed by authenticated `/api/ws?ticket=` JSON-RPC WebSocket handshake
- [x] Save only after login, REST, ticket minting, and WebSocket validation all succeed
- [x] Android Keystore-backed AES-GCM session-cookie storage; passwords are never persisted
- [x] Explicit reconnect state for missing, expired, rejected, or legacy token-only credentials
- [x] Multiple saved backends with add, reconnect, select, and forget flows
- [x] HTTPS plus explicitly approved cleartext private-IP transport policy
- [x] Unified cross-profile session list, profile-scoped full-text search, resume, confirmed reset, retry, rename, archive, delete, branch, undo, compression, and steering
- [x] Backend/profile/session-scoped draft persistence across Android process restart, with debounced writes and cleanup when a backend is forgotten
- [x] Gateway-backed slash command catalogue, live completions, argument replacement, curated mobile execution, skill and quick-command dispatch, inline output, and composer prefill
- [x] Streamed assistant text, reasoning, status, and structured tool activity
- [x] Dangerous-command approval, denial, clarification, interruption, session-only YOLO, and non-persistent masked sudo/secret prompts with expiry handling
- [x] Dynamic Hermes model/provider catalogue, model selection, reasoning effort, and fast mode
- [x] Provider API-key and custom-endpoint management through Hermes-owned APIs
- [x] SAF file, image, and PDF attachments with bounded reads and server-queue cleanup
- [x] Managed workspace browsing with directory navigation, bounded text/source/image/PDF previews, network-isolated HTML rendering, streamed SAF downloads, cancellation, and MIME/path validation
- [x] Press-to-talk and lockable voice capture through Hermes `/api/audio/transcribe`, with slide-to-lock/cancel, live level feedback, bounded temporary audio, permission recovery, and audio-focus interruption handling
- [x] Per-reply spoken playback through Hermes' own `/api/audio/speak` provider chain, with pause, resume, stop, system output routing, Bluetooth support, and temporary-audio cleanup
- [x] Profile-scoped messaging-gateway catalogue, platform status, Hermes-owned credential setup/removal, enable/disable, connection tests, and explicitly confirmed gateway restart
- [x] Command Center with live Hermes subagent trees, delegation pause/resume, confirmed subagent interruption, current-session background-process output, and confirmed process stop
- [x] Profile list, create, rename, delete, selection, and profile-scoped sessions
- [x] Installed skills plus Skill Hub search, review, scan, install, update, enable/disable, and removal
- [x] Cron list, create, edit, delete, enable/disable, run-now, and recent server-side runs
- [x] Doctor and security-audit actions with bounded status polling and output redaction
- [x] Phone master/detail and expanded tablet two-pane layouts
- [x] Official Hermes site palette and artwork, Courier Prime utility typography, licensed serif fallback, rounded component geometry, and official Desktop launcher icon
- [x] Unknown protocol fields and event types fail safely instead of crashing the client

### Partial foundations

- [ ] **Partial:** reconnect uses bounded backoff and authoritative session rehydration, but exact in-flight delta replay needs a server event cursor.
- [ ] **Partial:** tool activity is structured and expandable; specialised renderers for every Hermes tool are not complete.
- [ ] **Partial:** attachment sending and managed downloads/previews work; upload progress, camera capture, large streamed uploads, share actions, and canonical generated-artifact delivery are not complete.
- [ ] **Partial:** basic semantics and adaptive layouts exist; complete TalkBack, keyboard, switch-access, reduced-motion, foldable, and multi-window audits remain.
- [ ] **Partial:** diagnostics expose versions, connection state, doctor, and security-audit results; diagnostic export, SBOM, and release provenance remain.

### Not yet implemented

- [ ] Native OAuth/OIDC sign-in
- [ ] Background push notifications and notification actions
- [ ] Pending-message queue and composer history
- [ ] Optional appearance picker matching Hermes Desktop's built-in `nous`, `midnight`, `ember`, `mono`, `cyberpunk`, and `slate` skins; Nous remains the default
- [ ] MCP and toolset configuration
- [ ] Persisted spawn-tree replay and mobile background delivery for Agents when Android is suspended
- [ ] Usage analytics, token accounting, checkpoints, diffs, and filesystem rollback
- [ ] Local Termux runtime discovery or companion integration
- [ ] Biometric lock, secure-screen option, Android share target, deep links, shortcuts, widgets, and other platform integrations
- [ ] Signed release APK, release AAB, reproducibility verification, Baseline Profile, and macrobenchmarks

The detailed, current source audit remains in [`docs/research/desktop-parity-matrix.md`](docs/research/desktop-parity-matrix.md).

## Connect to an existing Hermes install

Hermes Android connects to the existing secured Hermes Dashboard. It does not require weakened Dashboard authentication, a proxy credential bridge, a static bearer token, or changes to Caddy, host binding, or Hermes configuration.

The supplied URL must expose these standard Dashboard paths:

- `POST /auth/password-login`
- `GET /api/status`
- `POST /api/auth/ws-ticket`
- WebSocket `/api/ws`

In **Backend Link**, enter:

1. A local label for the connection.
2. The base Dashboard URL.
3. The existing Dashboard username.
4. The existing Dashboard password.

The app submits the credentials to the Dashboard login endpoint, requires its secure Hermes session cookie, validates authenticated REST, mints a fresh single-use WebSocket ticket, then validates `/api/ws?ticket=`. It saves the backend and encrypted cookie only after every step succeeds. The password exists only long enough to submit the login request and is cleared from the transient input state; it is not written to DataStore, preferences, backups, diagnostics, or logs.

### Normal HTTPS

Use the public or private HTTPS URL that already serves the Dashboard, including its port when required:

```text
https://hermes.example.com
```

HTTPS uses the Android system trust store. The app does not bypass certificate or hostname validation.

### Tailscale

Tailscale Serve or another trusted HTTPS route works with its normal MagicDNS URL:

```text
https://your-device.your-tailnet.ts.net
```

Direct cleartext access to a Tailscale IPv4 address is supported only when **Allow private-network HTTP** is explicitly enabled:

```text
http://100.79.4.2:PORT
```

### Private LAN or local development

Cleartext HTTP is disabled by default. With the private-network option enabled, it remains restricted to literal loopback, RFC1918, IPv6 ULA, or Tailscale CGNAT addresses, for example:

```text
http://192.168.1.20:PORT
http://127.0.0.1:PORT
```

Public hostnames over cleartext HTTP remain rejected. Private DNS names should use HTTPS; this prevents a DNS response from silently moving an approved connection onto a public address.

### Existing token-only records

Records created by earlier builds are not migrated or reinterpreted. They display **Reconnect** and require the Dashboard username and password. A successful reconnect replaces the old encrypted token entry with the new Dashboard session cookie.

If the Dashboard expires or rejects a saved cookie, the app removes it, disconnects the socket, and presents a reconnect-required state for that backend.

## Security model

- Dashboard session cookies are encrypted with AES-GCM using a non-exportable Android Keystore key.
- `android:allowBackup` is disabled and the secret preferences file is excluded from device transfer.
- Session-cookie string representations are redacted.
- REST authentication uses the `Cookie` header; Dashboard sessions are not converted into bearer tokens.
- WebSocket authentication mints a fresh cookie-authenticated single-use ticket and places only that short-lived ticket in the upgrade query. The session cookie is not sent on the WebSocket upgrade.
- Passwords and session cookies are not included in backend metadata, UI diagnostics, or application logs.
- Failed login, missing/malformed cookies, REST failure, or WebSocket failure leaves no newly saved backend or credential.
- Cleartext transport has no silent fallback and requires both explicit consent and a private literal address.
- Unknown server messages are tolerated without treating them as trusted commands.

See [`docs/security/threat-model.md`](docs/security/threat-model.md) for the broader pre-release threat model. Do not expose a Hermes Dashboard directly to an untrusted network over cleartext HTTP.

## Build and test

Requirements:

- JDK 17
- Android SDK Platform 35
- Android SDK Build Tools 35.0.0

JDK 26 is not supported by the current Gradle/Android Gradle Plugin toolchain and can fail before project configuration. Point `JAVA_HOME` at JDK 17 when necessary.

Run the same project gate used by CI:

```bash
./gradlew --no-daemon :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:bundleDebug
```

Run only the Dashboard-authentication contract tests:

```bash
./gradlew --no-daemon :app:testDebugUnitTest \
  --tests com.nousresearch.hermes.network.DashboardAuthClientTest \
  --tests com.nousresearch.hermes.network.HermesRestClientSessionCookieTest \
  --tests com.nousresearch.hermes.protocol.OkHttpHermesGatewaySessionCookieTest \
  --tests com.nousresearch.hermes.data.DashboardBackendConnectorTest
```

Install the debug build:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

CI publishes the successful debug APK and AAB together as the `hermes-android-debug` workflow artifact. The download badge opens the successful `main` runs with the newest run first. GitHub requires sign-in to download workflow artifacts. These are debug builds, not signed public releases.

## Test coverage

The deterministic fake Dashboard exists only under `app/src/test`; production code always uses the configured Hermes Dashboard URL and real endpoints.

Current automated coverage includes:

- Login request payload and accepted Hermes session-cookie variants
- Successful cookie extraction plus missing and malformed cookie rejection
- Cookie reuse for authenticated REST without bearer authorization
- Cookie-authenticated single-use WebSocket ticket minting without a legacy token query or WebSocket cookie
- Login → REST → ticket → WebSocket → save ordering
- REST and WebSocket validation failures without persistence
- Expired saved-session reconnect behavior
- Legacy token-only record rejection without network reinterpretation
- Password non-persistence at the connect-and-save boundary
- Transport policy, protocol fixtures, reducers, session lifecycle, management routes, provider routes, Skill Hub routes, and diagnostic redaction
- Sensitive sudo/secret request and expiry reduction plus physical Compose instrumentation for password semantics, exact submission, cancellation, and cross-request value isolation
- Hermes voice REST payloads, spoken-audio MIME/base64 validation, Desktop-equivalent speech sanitisation, and numeric/string session-history message identifiers

Automated tests do not require a paid provider key or production credentials. A physical-device smoke against an isolated secured Dashboard from the pinned upstream source has passed. Voice QA on the Samsung covered microphone permission denial/recovery, press-to-talk without the keyboard, locked recording, slide-to-lock, slide-to-cancel, transcription failure recovery, cache cleanup, real Hermes Edge TTS generation, playback pause/resume/stop/completion, and Android's system output switcher. A final smoke against any intended production deployment is still required before calling that deployment verified.

## Current issues and blockers

Open issues were last reconciled on 17 July 2026.

- [#1 — Spec: authenticate Hermes Android through Dashboard sign-in](https://github.com/luinbytes/hermes-android/issues/1): implemented by the current branch; still open for owner review and closure.
- [#2 — Connect Hermes Android to a secured Dashboard](https://github.com/luinbytes/hermes-android/issues/2): acceptance criteria are implemented and the isolated physical-device upstream smoke passed; still open for owner review and closure.

Current concrete blockers:

- **Production deployment verification:** the isolated pinned-upstream smoke passed, including login, authenticated status, two fresh WebSocket ticket mints, saved-backend reconnect, session creation, and management reads. A particular public, private, or Tailscale deployment still needs its own route and credential smoke before that deployment is declared verified.
- **Native OAuth/OIDC:** browser cookies cannot safely be imported from Custom Tabs. A general upstream native code/session exchange with PKCE is required.
- **Exact reconnect replay:** Hermes does not currently expose a universal ordered event cursor/replay contract for every in-flight stream.
- **Background mobile delivery:** approvals, clarifications, completions, failures, and cron results need an upstream device-registration, revocation, acknowledgement, and single-use action-token contract.
- **Remote artifacts:** full safe browsing and generated-artifact delivery would benefit from a canonical remote artifact descriptor rather than desktop filesystem assumptions.
- **Release readiness:** real-device phone/tablet/foldable inspection, accessibility testing, performance/battery testing, security review, release signing, AAB generation, and reproducibility evidence are incomplete.

Unimplemented items that do not depend on an upstream change remain local engineering work, not protocol blockers. No upstream pull request has been opened from this repository.

## Architecture and compatibility

The client uses Hermes REST APIs for backend-owned management data and the TUI Gateway JSON-RPC/WebSocket protocol for interactive sessions. It is not a WebView wrapper, an OpenAI-compatible chat-only client, or a messaging-platform adapter.

The source audit is pinned to Hermes Agent commit `5122ddd478143a6901bb752cf8ebcd1c5154b6da` (Hermes Agent `0.18.2`, Desktop `0.17.0`) from 18 July 2026. That exact commit is the verified source contract; version strings alone are insufficient because it is 120 commits ahead of the previous baseline without a package-version change. Older Hermes versions have not yet completed a compatibility matrix; capability and unknown-event handling are designed to degrade safely, but unsupported controls may be absent.

Upstream Hermes remains read-only from this repository. Proposed general protocol changes are documented locally for owner-led upstream review.

## Repository documents

- [`docs/research/upstream-baseline.md`](docs/research/upstream-baseline.md) — audited source baseline and protocol entry points
- [`docs/research/desktop-parity-matrix.md`](docs/research/desktop-parity-matrix.md) — detailed Desktop capability audit
- [`docs/architecture/android-client-rfc.md`](docs/architecture/android-client-rfc.md) — architecture alternatives and chosen hybrid client
- [`docs/design/mobile-product-spec.md`](docs/design/mobile-product-spec.md) — mobile information architecture, states, motion, and accessibility intent
- [`docs/security/threat-model.md`](docs/security/threat-model.md) — Android and remote-client threats and release gates
- [`docs/upstream/upstream-change-plan.md`](docs/upstream/upstream-change-plan.md) — proposed general upstream contract work

## Licence

MIT. See [`LICENSE`](LICENSE).
